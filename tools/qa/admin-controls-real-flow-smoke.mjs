import { readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const runId = `adminctl${Date.now()}`;
const fakeDay = String((Number(runId.slice(-2)) % 28) + 1).padStart(2, "0");
const fakeIsoDate = `2026-04-${fakeDay}`;
const fakeDayKey = `${fakeDay}-04-2026`;
const payLotteryId = `99${runId.slice(-4)}`;
const limitLotteryId = `98${runId.slice(-4)}`;
const payLotteryName = `QA Premio Pago ${runId.slice(-4)}`;
const limitLotteryName = `QA Limite Cajero ${runId.slice(-4)}`;
const summaryFile = new URL(`./admin-controls-real-flow-summary-${new Date().toISOString().replace(/[:.]/g, "-")}.json`, import.meta.url);

const checks = [];
const createdTickets = [];
let originalUsersPayload = null;
let originalSystemModes = undefined;
let originalDisabledLotteries = undefined;
let originalLimits = undefined;
let usersChanged = false;
let configsChanged = false;
let adminSession = null;
let cashierSession = null;
let admin = null;
let cashier = null;

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function headers(token = API_KEY, extra = {}) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
    ...extra,
  };
}

function check(condition, label, data = {}) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  console.log(`${ok ? "PASS" : "BUG"} ${label} ${JSON.stringify(data)}`);
  return ok;
}

async function requestJson(label, method, url, body, token = API_KEY, extraHeaders = {}) {
  const started = performance.now();
  let response;
  let text = "";
  try {
    response = await fetch(url, {
      method,
      headers: headers(token, extraHeaders),
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - started);
    console.log(`HTTP ${label} ${JSON.stringify({ status: "NETWORK_ERROR", elapsedMs, ok: false, message: error?.message })}`);
    return { status: "NETWORK_ERROR", ok: false, elapsedMs, json: null, text: clean(error?.message) };
  }
  const elapsedMs = Math.round(performance.now() - started);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  console.log(`HTTP ${label} ${JSON.stringify({
    status: response.status,
    elapsedMs,
    ok: response.ok,
    message: clean(json?.message ?? json?.error).slice(0, 180),
  })}`);
  return { status: response.status, ok: response.ok, elapsedMs, json, text };
}

function edge(slug, body, token = API_KEY, extraHeaders = {}) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token, extraHeaders);
}

function parseCredentials(text) {
  const blockRows = [...text.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
  const looseRows = [...text.matchAll(/id\s+([^\s]+)\s+contrase(?:ñ|n)a\s+([^\s]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
  const byUser = new Map();
  for (const row of [...looseRows, ...blockRows]) byUser.set(lower(row.username), row);
  return [...byUser.values()];
}

function allAccounts(payload) {
  return [
    ...(Array.isArray(payload.users) ? payload.users : []),
    ...(Array.isArray(payload.admins) ? payload.admins : []),
    ...(Array.isArray(payload.supervisores) ? payload.supervisores : []),
    ...(Array.isArray(payload.supervisors) ? payload.supervisors : []),
    ...(Array.isArray(payload.cajeros) ? payload.cajeros : []),
    ...(Array.isArray(payload.cashiers) ? payload.cashiers : []),
  ];
}

function findAccount(payload, username) {
  const needle = lower(username);
  return allAccounts(payload).find((account) =>
    [account.user, account.username, account.id, account.userId].some((value) => lower(value) === needle)
  );
}

function roleOf(account) {
  const role = lower(account?.role);
  return role === "cashier" ? "cajero" : role || "cajero";
}

function sha256Hex(input) {
  return createHash("sha256").update(input).digest("hex");
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return {
    username,
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: result.json?.accessToken,
    user: result.json?.user,
    result,
  };
}

async function fetchUsersPayload() {
  const result = await requestJson(
    "users-state fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function saveUsersPayload(payload, token = adminSession?.token ?? API_KEY) {
  const direct = await requestJson(
    "users-state upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?on_conflict=scope`,
    { scope: "global", payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=minimal" },
  );
  if (direct.ok) return;
  const result = await edge("lotterynet-users-state", { action: "upsert", payload }, token);
  if (!result.ok || result.json?.ok === false) throw new Error(`No se pudo guardar usuarios: REST=${direct.text} EDGE=${result.text}`);
}

async function fetchMasterValue(key) {
  const result = await edge("get-master-config", { action: "fetch", key });
  if (!result.ok || result.json?.ok === false) throw new Error(`No se pudo leer ${key}: ${result.text}`);
  return result.json?.payload ?? null;
}

async function updateMasterValue(key, payload) {
  const result = await edge("update-master-config", { key, payload });
  if (result.ok && result.json?.ok !== false) return result;
  const direct = await requestJson(
    `master-state upsert ${key}`,
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_master_state?on_conflict=config_key`,
    { config_key: key, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=minimal" },
  );
  if (!direct.ok) throw new Error(`No se pudo guardar ${key}: EDGE=${result.text} REST=${direct.text}`);
  return result;
}

async function upsertResults(table, payload) {
  return requestJson(
    `${table} upsert`,
    "POST",
    `${SUPABASE_URL}/rest/v1/${table}?on_conflict=result_date`,
    { result_date: fakeDayKey, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

function play(playType, number, amount, lotteryId, lotteryName, extra = {}) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName, ...extra };
}

function ticketBody(label, actor, plays) {
  const clientRequestId = `${runId}-${label}`;
  return {
    clientRequestId,
    localTicketId: clientRequestId,
    adminKey: admin.id,
    adminId: admin.id,
    actorKey: actor.user,
    actorId: actor.id,
    actorRole: roleOf(actor),
    cashierKey: actor.user,
    cashierId: actor.id,
    drawDate: fakeIsoDate,
    dayKey: fakeDayKey,
    lotteryName: plays[0]?.lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
}

async function createTicket(label, session, actor, plays, keep = true) {
  const body = ticketBody(label, actor, plays);
  const result = await edge("create-ticket-v2", body, session.token);
  if (keep && result.json?.ok === true) createdTickets.push({ body, result });
  return { body, result };
}

function createSupervisorPayload(payload, assignedCashier) {
  const salt = randomUUID();
  const user = `sup${runId.slice(-6)}`;
  const password = `Sup${runId.slice(-6)}!`;
  const supervisor = {
    id: `SUP-${runId.slice(-8).toUpperCase()}`,
    user,
    username: user,
    role: "supervisor",
    nombre: "Supervisor QA Admin Controls",
    displayName: "Supervisor QA Admin Controls",
    active: true,
    activo: true,
    blocked: false,
    adminId: admin.id,
    adminUser: admin.user,
    banca: admin.banca,
    passwordSalt: salt,
    passwordHash: sha256Hex(`${salt}:${password}`),
    passwordVersion: "sha256-v1",
    assignedCashiers: [assignedCashier.user],
    updatedAtEpochMs: Date.now(),
  };
  const next = structuredClone(payload);
  next.supervisores = [...(Array.isArray(next.supervisores) ? next.supervisores : []), supervisor];
  return { payload: next, supervisor, password };
}

function setAccountBlocked(payload, username, blocked) {
  const next = structuredClone(payload);
  const arrays = ["users", "admins", "supervisores", "supervisors", "cajeros", "cashiers"];
  for (const key of arrays) {
    if (!Array.isArray(next[key])) continue;
    next[key] = next[key].map((account) => {
      if (![account.user, account.username, account.id, account.userId].some((value) => lower(value) === lower(username))) return account;
      return {
        ...account,
        active: !blocked,
        activo: !blocked,
        blocked,
        disabled: blocked,
        updatedAtEpochMs: Date.now(),
      };
    });
  }
  return next;
}

async function payTicket(ticket) {
  return edge("pay-ticket", {
    actorKey: admin.user,
    adminKey: admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.body.clientRequestId,
  }, adminSession.token);
}

async function voidTicket(ticket) {
  return edge("void-ticket", {
    actorKey: cashier.user,
    adminKey: admin.id,
    cashierKey: cashier.user,
    clientRequestId: ticket.body.clientRequestId,
    action: "delete",
    returnLimit: true,
  }, cashierSession.token);
}

async function main() {
  console.log(`Inicio flujo administrativo real ${runId}`);
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  originalUsersPayload = await fetchUsersPayload();
  admin = findAccount(originalUsersPayload, "podero02");
  cashier = findAccount(originalUsersPayload, "bancae01");
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  const cashierCred = credentials.find((entry) => lower(entry.username) === "bancae01");
  check(Boolean(admin && cashier && adminCred && cashierCred), "cuentas QA disponibles", {
    admin: admin?.id,
    cashier: cashier?.user,
  });
  if (!admin || !cashier || !adminCred || !cashierCred) throw new Error("Faltan cuentas QA.");

  adminSession = await login(adminCred.username, adminCred.password);
  cashierSession = await login(cashierCred.username, cashierCred.password);
  check(adminSession.ok && cashierSession.ok, "login admin y cajero", {
    adminStatus: adminSession.result.status,
    cashierStatus: cashierSession.result.status,
  });

  const systemKey = `system_modes:${admin.id}`;
  const disabledKey = `manual_disabled_lotteries:${admin.id}`;
  const limitKey = `cashier_limits:${admin.id}`;
  originalSystemModes = await fetchMasterValue(systemKey);
  originalDisabledLotteries = await fetchMasterValue(disabledKey);
  originalLimits = await fetchMasterValue(limitKey);
  await updateMasterValue(systemKey, {
    ...(originalSystemModes ?? {}),
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    cashierPickEnabled: true,
    blockedSalePlays: [],
    updatedAt: Date.now(),
  });
  configsChanged = true;

  const supervisorSetup = createSupervisorPayload(originalUsersPayload, cashier);
  await saveUsersPayload(supervisorSetup.payload, adminSession.token);
  usersChanged = true;
  const supervisorSession = await login(supervisorSetup.supervisor.user, supervisorSetup.password);
  check(supervisorSession.ok, "supervisor QA entra antes de bloquear", { status: supervisorSession.result.status });

  await saveUsersPayload(setAccountBlocked(supervisorSetup.payload, supervisorSetup.supervisor.user, true), adminSession.token);
  const blockedSupervisor = await edge("void-ticket", {
    actorKey: supervisorSetup.supervisor.user,
    adminKey: admin.id,
    clientRequestId: `${runId}-fake-supervisor-delete`,
    action: "delete",
  }, supervisorSession.token);
  check(blockedSupervisor.status === 403, "supervisor bloqueado no puede ejecutar accion", {
    status: blockedSupervisor.status,
    message: blockedSupervisor.json?.message,
  });

  await saveUsersPayload(setAccountBlocked(originalUsersPayload, cashier.user, true), adminSession.token);
  const blockedSnapshot = await fetchUsersPayload();
  const blockedCashierRows = allAccounts(blockedSnapshot).filter((account) =>
    [account.user, account.username, account.id, account.userId].some((value) => lower(value) === lower(cashier.user))
  );
  check(blockedCashierRows.length > 0 && blockedCashierRows.every((account) =>
    account.activo === false || account.active === false || account.blocked === true || account.disabled === true
  ), "estado bloqueado de cajero quedo guardado antes de vender", {
    rows: blockedCashierRows.map((account) => ({
      user: account.user,
      id: account.id,
      active: account.active,
      activo: account.activo,
      blocked: account.blocked,
      disabled: account.disabled,
    })),
  });
  const blockedCashier = await createTicket("blocked-cashier", cashierSession, cashier, [
    play("Q", "41", 1, "99801", "QA Cajero Bloqueado"),
  ], false);
  check(blockedCashier.result.status === 403, "cajero bloqueado no puede vender", {
    status: blockedCashier.result.status,
    message: blockedCashier.result.json?.message,
  });

  await saveUsersPayload(originalUsersPayload, adminSession.token);
  usersChanged = false;

  const blockedPlays = [
    { playType: "Q", number: "03" },
    { playType: "P", number: "0311" },
    { playType: "SP", number: "03-11" },
    { playType: "T", number: "031122" },
    { playType: "P3", number: "147" },
    { playType: "P3BOX", number: "741" },
    { playType: "P4", number: "1475" },
    { playType: "P4BOX", number: "5741" },
  ];
  await updateMasterValue(systemKey, {
    ...(originalSystemModes ?? {}),
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    cashierPickEnabled: true,
    blockedSalePlays: blockedPlays,
    updatedAt: Date.now(),
  });
  await updateMasterValue(disabledKey, { ids: ["99877"], date: "2026-05-30", permanent: true, updatedAt: Date.now() });
  await updateMasterValue(limitKey, {
    ...(originalLimits ?? {}),
    defaults: { ...(originalLimits?.defaults ?? {}), daySale: 50000, payout: 500000, q: 1000, pale: 1000, sp: 1000, t: 1000, p3: 1000, p3box: 1000, p4: 1000, p4box: 1000 },
    byUser: {
      ...(originalLimits?.byUser ?? {}),
      [cashier.user]: { daySale: 50000, payout: 500000, q: 5, pale: 1000, sp: 1000, t: 1000, p3: 1000, p3box: 1000, p4: 1000, p4box: 1000 },
    },
  });
  configsChanged = true;

  const blockedCases = [
    ["blocked-q", play("Q", "03", 1, "99802", "QA Bloqueo Jugada")],
    ["blocked-pale", play("P", "0311", 1, "99802", "QA Bloqueo Jugada")],
    ["blocked-sp", play("SP", "03/11", 1, "99802", "QA Bloqueo Jugada")],
    ["blocked-tripleta", play("T", "031122", 1, "99802", "QA Bloqueo Jugada")],
    ["blocked-p3", play("P3", "147", 1, "99803", "QA Pick Bloqueo", { pickGame: "PICK3", pickMode: "STRAIGHT" })],
    ["blocked-p3box", play("P3BOX", "741", 1, "99803", "QA Pick Bloqueo", { pickGame: "PICK3", pickMode: "BOX" })],
    ["blocked-p4", play("P4", "1475", 1, "99804", "QA Pick4 Bloqueo", { pickGame: "PICK4", pickMode: "STRAIGHT" })],
    ["blocked-p4box", play("P4BOX", "5741", 1, "99804", "QA Pick4 Bloqueo", { pickGame: "PICK4", pickMode: "BOX" })],
  ];
  for (const [label, blockedPlay] of blockedCases) {
    const result = await createTicket(label, cashierSession, cashier, [blockedPlay], false);
    check(result.result.status === 409, `bloqueo exacto aplica: ${label}`, {
      status: result.result.status,
      message: result.result.json?.message,
    });
  }

  const directAdminBlocked = await createTicket("blocked-admin-direct", adminSession, admin, [
    play("Q", "03", 1, "99802", "QA Bloqueo Jugada"),
  ], false);
  check(directAdminBlocked.result.status === 409, "bloqueo exacto tambien frena admin/API directa", {
    status: directAdminBlocked.result.status,
    message: directAdminBlocked.result.json?.message,
  });

  const allowedNear = await createTicket("allowed-near-block", cashierSession, cashier, [
    play("Q", "04", 1, "99802", "QA Bloqueo Jugada"),
  ]);
  check(allowedNear.result.json?.ok === true, "bloquear 03 no bloquea 04 ni el juego completo", {
    status: allowedNear.result.status,
    code: allowedNear.result.json?.ticket?.ticket_code,
  });

  const blockedLottery = await createTicket("blocked-lottery", cashierSession, cashier, [
    play("Q", "55", 1, "99877", "QA Loteria Cerrada"),
  ], false);
  check(blockedLottery.result.status === 409, "loteria bloqueada no permite venta", {
    status: blockedLottery.result.status,
    message: blockedLottery.result.json?.message,
  });

  const limitFirst = await createTicket("limit-first", cashierSession, cashier, [
    play("Q", "66", 3, limitLotteryId, limitLotteryName),
  ]);
  const limitSecondBlocked = await createTicket("limit-second-blocked", cashierSession, cashier, [
    play("Q", "66", 3, limitLotteryId, limitLotteryName),
  ], false);
  const limitRemainingOk = await createTicket("limit-remaining-ok", cashierSession, cashier, [
    play("Q", "66", 2, limitLotteryId, limitLotteryName),
  ]);
  check(limitFirst.result.json?.ok === true, "limite acepta primera jugada dentro del tope", {
    status: limitFirst.result.status,
  });
  check(limitSecondBlocked.result.status === 409, "limite descuenta vendido y bloquea exceso", {
    status: limitSecondBlocked.result.status,
    message: limitSecondBlocked.result.json?.message,
  });
  check(limitRemainingOk.result.json?.ok === true, "limite deja vender solo el restante exacto", {
    status: limitRemainingOk.result.status,
  });

  const ticketToVoid = await createTicket("void-target", cashierSession, cashier, [
    play("Q", "77", 1, "99889", "QA Anular Ticket"),
  ]);
  const voided = await voidTicket(ticketToVoid);
  check(voided.json?.ok === true, "anular/borrar ticket responde correcto", {
    status: voided.status,
    message: voided.json?.message,
  });

  const winnerTicket = await createTicket("pay-winner", cashierSession, cashier, [
    play("Q", "88", 1, payLotteryId, payLotteryName),
  ]);
  const resultSave = await upsertResults("lotterynet_results_by_day", [
    { id: payLotteryId, name: payLotteryName, number: "88-00-00", status: "published" },
  ]);
  check(resultSave.ok, "resultado QA se guarda para probar pago", { status: resultSave.status });
  await new Promise((resolve) => setTimeout(resolve, 1800));
  const paid = await payTicket(winnerTicket);
  check(paid.json?.ok === true, "pagar ticket ganador funciona por servidor", {
    status: paid.status,
    message: paid.json?.message,
    payout: paid.json?.payoutAmount ?? paid.json?.ticket?.payout_amount,
  });
}

async function restore() {
  if (configsChanged && admin) {
    await updateMasterValue(`system_modes:${admin.id}`, originalSystemModes ?? {});
    await updateMasterValue(`manual_disabled_lotteries:${admin.id}`, originalDisabledLotteries ?? {});
    await updateMasterValue(`cashier_limits:${admin.id}`, originalLimits ?? {});
    console.log("RESTORE configuracion administrativa original");
  }
  if (usersChanged && originalUsersPayload) {
    await saveUsersPayload(originalUsersPayload, adminSession?.token ?? API_KEY);
    console.log("RESTORE usuarios original");
  }
}

async function writeSummary() {
  const passed = checks.filter((item) => item.ok).length;
  const failed = checks.length - passed;
  await writeFile(summaryFile, JSON.stringify({
    runId,
    fakeIsoDate,
    passed,
    failed,
    checks,
    createdTickets: createdTickets.map((ticket) => ({
      clientRequestId: ticket.body.clientRequestId,
      ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
    })),
  }, null, 2), "utf8");
  console.log(`SUMMARY ${decodeURIComponent(summaryFile.pathname).replace(/^\/([A-Z]:)/, "$1")}`);
}

try {
  await main();
} catch (error) {
  check(false, "flujo administrativo interrumpido", { message: error?.message, stack: error?.stack });
} finally {
  try {
    await restore();
  } catch (error) {
    check(false, "fallo restaurando configuracion QA", { message: error?.message });
  }
  await writeSummary();
  const failed = checks.filter((item) => !item.ok);
  if (failed.length) process.exitCode = 1;
}
