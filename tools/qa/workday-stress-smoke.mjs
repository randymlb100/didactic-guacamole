import { readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const runId = `workday${Date.now()}`;
const fakeDay = String((Date.now() % 20) + 1).padStart(2, "0");
const fakeIsoDate = `2026-01-${fakeDay}`;
const fakeDayKey = `${fakeDay}-01-2026`;
const adminUsername = "podero02";
const masterUsername = "master";
const masterPassword = "pass123";
const stressRounds = Number(process.env.LOTTERYNET_STRESS_ROUNDS || 8);
const concurrency = Number(process.env.LOTTERYNET_STRESS_CONCURRENCY || 8);
const logFile = new URL(`./workday-stress-smoke-${new Date().toISOString().replace(/[:.]/g, "-")}.log`, import.meta.url);
const summaryFile = new URL(`./workday-stress-smoke-summary-${new Date().toISOString().slice(0, 10)}.md`, import.meta.url);

const lines = [];
const checks = [];
const timings = {};
const createdClientIds = [];
let originalUsersPayload = null;
let usersPayloadWasChanged = false;
let originalLimitPayload = undefined;
let limitPayloadWasChanged = false;
let cleanupBearerToken = null;

function log(label, data) {
  const text = `[${new Date().toISOString()}] ${label}${data === undefined ? "" : ` ${JSON.stringify(data)}`}`;
  lines.push(text);
  console.log(text);
}

function check(condition, label, data) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  log(`${ok ? "PASS" : "BUG"} ${label}`, data);
  return ok;
}

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function sha256Hex(input) {
  return createHash("sha256").update(input).digest("hex");
}

function authHeaders(token = API_KEY, extra = {}) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
    ...extra,
  };
}

function redact(value) {
  return String(value ?? "")
    .replace(/"(accessToken|refreshToken|access_token|refresh_token)"\s*:\s*"[^"]+"/gi, "\"$1\":\"[redacted]\"")
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[redacted-jwt]");
}

function recordTiming(label, elapsedMs) {
  if (!timings[label]) timings[label] = [];
  timings[label].push(elapsedMs);
}

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

async function requestJson(label, method, url, body, token = API_KEY, extraHeaders = {}) {
  const start = performance.now();
  let response;
  let text = "";
  try {
    response = await fetch(url, {
      method,
      headers: authHeaders(token, extraHeaders),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - start);
    recordTiming(label, elapsedMs);
    log(`HTTP ${label}`, { status: "NETWORK_ERROR", elapsedMs, message: error?.message });
    return { status: "NETWORK_ERROR", elapsedMs, ok: false, json: null, text: clean(error?.message) };
  }
  const elapsedMs = Math.round(performance.now() - start);
  recordTiming(label, elapsedMs);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  log(`HTTP ${label}`, { status: response.status, elapsedMs, ok: response.ok, message: redact(json?.message ?? json?.error ?? "").slice(0, 180) });
  return { status: response.status, elapsedMs, ok: response.ok, json, text };
}

async function edge(slug, body, token = API_KEY, extraHeaders = {}) {
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

function accountArray(value) {
  return Array.isArray(value) ? value : [];
}

function allAccounts(payload) {
  return [
    ...accountArray(payload.users),
    ...accountArray(payload.admins),
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
    ...accountArray(payload.cajeros),
    ...accountArray(payload.cashiers),
  ];
}

function findAccount(payload, username) {
  const needle = lower(username);
  return allAccounts(payload).find((account) =>
    [account.user, account.username, account.id, account.userId].some((value) => lower(value) === needle)
  );
}

function stableUniqueAccounts(accounts) {
  const seen = new Set();
  return accounts.filter((account) => {
    const key = `${lower(account.id)}|${lower(account.user ?? account.username)}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function roleOf(account) {
  const role = lower(account.role);
  if (role === "cashier") return "cajero";
  return role || "cajero";
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

async function saveUsersPayload(payload, token) {
  const row = { scope: "global", payload, updated_at: new Date().toISOString() };
  const direct = await requestJson(
    "users-state upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?on_conflict=scope`,
    row,
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=minimal" },
  );
  if (direct.ok) return { route: "supabase-rest" };

  const legacyEdge = await edge("lotterynet-users-state", { action: "upsert", payload }, token);
  if (legacyEdge.ok && legacyEdge.json?.ok !== false) return { route: "legacy-edge" };

  const render = await requestJson(
    "render users-state upsert",
    "POST",
    "https://didactic-guacamole.onrender.com/users-state",
    { payload },
  );
  if (render.ok) return { route: "render" };
  throw new Error(`No se pudo guardar usuarios: REST=${direct.text} EDGE=${legacyEdge.text} RENDER=${render.text}`);
}

async function fetchLimitPayload(adminId) {
  const key = `cashier_limits:${adminId}`;
  const result = await edge("get-master-config", { action: "fetch", key });
  if (!result.ok || result.json?.ok === false) return { ok: false, status: result.status, text: result.text, value: undefined };
  return { ok: true, value: result.json?.payload };
}

async function saveLimitPayload(adminId, value) {
  const key = `cashier_limits:${adminId}`;
  const payload = typeof value === "string"
    ? (() => {
        try { return JSON.parse(value); } catch { return { raw: value }; }
      })()
    : value;
  return edge("update-master-config", { key, payload });
}

async function upsertResults(table, payload) {
  const resultDate = fakeDayKey;
  return requestJson(
    `${table} upsert`,
    "POST",
    `${SUPABASE_URL}/rest/v1/${table}?on_conflict=result_date`,
    { result_date: resultDate, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

async function deleteResults(table) {
  return requestJson(
    `${table} cleanup`,
    "DELETE",
    `${SUPABASE_URL}/rest/v1/${table}?result_date=eq.${encodeURIComponent(fakeDayKey)}`,
    undefined,
    API_KEY,
  );
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return {
    username,
    status: result.status,
    ok: result.ok && result.json?.ok === true && clean(result.json?.accessToken),
    token: result.json?.accessToken,
    user: result.json?.user,
    message: result.json?.message,
  };
}

function createSupervisorPayload(admin, assignedCashiers) {
  const now = Date.now();
  const salt = randomUUID();
  const user = `sup${runId.slice(-6)}`;
  const password = `Sup${runId.slice(-6)}!`;
  return {
    supervisor: {
      id: `SUP-${runId.slice(-8).toUpperCase()}`,
      user,
      username: user,
      role: "supervisor",
      nombre: "Supervisor QA Stress",
      displayName: "Supervisor QA Stress",
      active: true,
      activo: true,
      adminId: admin.id,
      adminUser: admin.user,
      banca: admin.banca,
      commissionRate: 0.02,
      passwordSalt: salt,
      passwordHash: sha256Hex(`${salt}:${password}`),
      passwordVersion: "sha256-v1",
      credChangedAt: now,
      credChangedAtEpochMs: now,
      updatedAt: now,
      updatedAtEpochMs: now,
      assignedCashiers: assignedCashiers.map((cashier) => cashier.user),
    },
    password,
  };
}

function addSupervisorAndCommissions(payload, admin, cashiers) {
  const next = structuredClone(payload);
  const assigned = cashiers.slice(0, 4);
  const { supervisor, password } = createSupervisorPayload(admin, assigned);
  next.supervisores = stableUniqueAccounts([...(Array.isArray(next.supervisores) ? next.supervisores : []), supervisor]);
  const assignedUsers = new Set(assigned.map((cashier) => lower(cashier.user)));
  const update = (account) => {
    const user = lower(account.user ?? account.username);
    if (!assignedUsers.has(user)) return account;
    return {
      ...account,
      commissionRate: user.endsWith("01") || user.endsWith("02") ? 0.08 : 0.05,
      supervisorIds: [...new Set([...(account.supervisorIds ?? []), supervisor.id])],
      supervisorUsers: [...new Set([...(account.supervisorUsers ?? []), supervisor.user])],
      updatedAtEpochMs: Date.now(),
    };
  };
  for (const key of ["users", "cajeros", "cashiers"]) {
    if (Array.isArray(next[key])) next[key] = next[key].map(update);
  }
  return { payload: next, supervisor, password, assigned };
}

function buildLimitPayload(cashiers) {
  const defaults = { daySale: 5000, payout: 100000, q: 1000, pale: 500, sp: 500, t: 75, p3: 80, p3box: 80, p4: 80, p4box: 80 };
  const byUser = {};
  for (const cashier of cashiers.slice(0, 2)) {
    byUser[cashier.user] = { ...defaults, q: 6, p3: 6, p3box: 6, p4: 6, p4box: 6 };
  }
  return { defaults, byUser };
}

function play(playType, number, amount, lotteryId = "1", lotteryName = "La Primera Día", extra = {}) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName, ...extra };
}

function stressPlays(index) {
  const q = String((index % 90) + 10).padStart(2, "0");
  const p3 = String((120 + index) % 900 + 100).padStart(3, "0");
  const p4 = String((4300 + index) % 9000 + 1000).padStart(4, "0");
  return [
    play("Q", q, 1),
    play("P3", p3, 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
    play("P3BOX", p3.split("").reverse().join(""), 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "BOX" }),
    play("P4", p4, 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "STRAIGHT" }),
    play("P4BOX", p4.split("").reverse().join(""), 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "BOX" }),
  ];
}

async function createTicket(session, actor, admin, label, plays, overrides = {}) {
  const clientRequestId = `${runId}-${label}`;
  createdClientIds.push(clientRequestId);
  const body = {
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
    lotteryName: plays[0]?.lotteryName ?? "La Primera Día",
    phoneTime: new Date().toISOString(),
    plays,
    ...overrides,
  };
  const result = await edge("create-ticket-v2", body, session.token);
  return { clientRequestId, body, result };
}

async function runPool(tasks, workerCount) {
  const results = [];
  let index = 0;
  const workers = Array.from({ length: Math.max(1, workerCount) }, async () => {
    while (index < tasks.length) {
      const taskIndex = index++;
      results[taskIndex] = await tasks[taskIndex]();
    }
  });
  await Promise.all(workers);
  return results;
}

async function getDelta(ownerKey, token, limit = 300) {
  return edge("get-ticket-delta", { ownerKey, limit }, token);
}

async function getSummary(ownerKey, token) {
  return edge("get-ticket-summary", { ownerKey, dayKey: fakeDayKey }, token);
}

async function report(slug, body, token) {
  return edge(slug, { from: fakeIsoDate, to: fakeIsoDate, ...body }, token);
}

async function payTicket(session, actor, admin, ticket) {
  return edge("pay-ticket", {
    actorKey: actor.id,
    adminKey: admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.clientRequestId,
  }, session.token);
}

async function waitForTicketsInDelta(ownerKey, token, expectedIds, maxWaitMs = 10000) {
  const start = performance.now();
  let last = null;
  while (performance.now() - start < maxWaitMs) {
    last = await getDelta(ownerKey, token);
    const ids = new Set((last.json?.tickets ?? []).map((ticket) => ticket.client_request_id));
    const found = expectedIds.filter((id) => ids.has(id));
    if (found.length === expectedIds.length) {
      return { ok: true, elapsedMs: Math.round(performance.now() - start), found, last };
    }
    await new Promise((resolve) => setTimeout(resolve, 700));
  }
  const ids = new Set((last?.json?.tickets ?? []).map((ticket) => ticket.client_request_id));
  return { ok: false, elapsedMs: Math.round(performance.now() - start), found: expectedIds.filter((id) => ids.has(id)), last };
}

async function main() {
  log("Inicio workday stress smoke", { runId, fakeIsoDate, fakeDayKey, stressRounds, concurrency });
  await deleteResults("lotterynet_results_by_day");
  await deleteResults("lotterynet_pick_results_by_day");
  log("PRECHECK resultados falsos limpiados antes de vender", { fakeDayKey });

  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  originalUsersPayload = await fetchUsersPayload();
  const admin = findAccount(originalUsersPayload, adminUsername);
  const cashiers = credentials
    .filter((entry) => lower(entry.username).startsWith("bancae"))
    .map((entry) => ({ credentials: entry, account: findAccount(originalUsersPayload, entry.username) }))
    .filter((entry) => entry.account);

  check(Boolean(admin), "admin de prueba existe en servidor", { adminUsername, id: admin?.id });
  check(cashiers.length >= 4, "hay varios cajeros para simular dia de trabajo", { found: cashiers.map((entry) => entry.account.user) });

  const adminCredential = credentials.find((entry) => lower(entry.username) === adminUsername);
  const masterSession = await login(masterUsername, masterPassword);
  const adminSession = await login(adminCredential.username, adminCredential.password);
  cleanupBearerToken = adminSession.token;
  const cashierSessions = [];
  for (const entry of cashiers) {
    cashierSessions.push({ ...entry, session: await login(entry.credentials.username, entry.credentials.password) });
  }
  check(masterSession.ok, "login master valido", { status: masterSession.status, user: masterSession.user });
  check(adminSession.ok, "login admin valido", { status: adminSession.status, user: adminSession.user });
  check(cashierSessions.every((entry) => entry.session.ok), "login de cajeros valido", {
    ok: cashierSessions.filter((entry) => entry.session.ok).length,
    total: cashierSessions.length,
  });

  const supervisorSetup = addSupervisorAndCommissions(originalUsersPayload, admin, cashiers.map((entry) => entry.account));
  const saveRoute = await saveUsersPayload(supervisorSetup.payload, adminSession.token);
  usersPayloadWasChanged = true;
  check(true, "admin crea supervisor/grupo y cambia comisiones en usuarios", {
    route: saveRoute.route,
    supervisor: supervisorSetup.supervisor.user,
    assigned: supervisorSetup.assigned.map((cashier) => cashier.user),
    commissionRates: supervisorSetup.assigned.map((cashier) => ({ user: cashier.user, rate: findAccount(supervisorSetup.payload, cashier.user)?.commissionRate })),
  });
  const supervisorSession = await login(supervisorSetup.supervisor.user, supervisorSetup.password);
  check(supervisorSession.ok, "login supervisor creado valido", { status: supervisorSession.status, user: supervisorSession.user });

  const fetchedLimits = await fetchLimitPayload(admin.id);
  originalLimitPayload = fetchedLimits.value;
  const limitSave = await saveLimitPayload(admin.id, buildLimitPayload(cashiers.map((entry) => entry.account)));
  limitPayloadWasChanged = limitSave.ok;
  check(limitSave.ok && limitSave.json?.ok !== false, "cambio de limites por servidor aceptado", {
    status: limitSave.status,
    note: limitSave.ok ? "limites QA guardados temporalmente" : "ruta bloqueada; se conserva como riesgo de permisos/API",
    message: redact(limitSave.json?.message ?? limitSave.text).slice(0, 220),
  });

  const tasks = [];
  let counter = 0;
  for (let round = 0; round < stressRounds; round++) {
    for (const entry of cashierSessions) {
      const label = `${entry.account.user}-r${round}`;
      const plays = stressPlays(counter++);
      tasks.push(async () => createTicket(entry.session, entry.account, admin, label, plays));
    }
  }
  const stressStart = performance.now();
  const stressResults = await runPool(tasks, concurrency);
  const stressElapsedMs = Math.round(performance.now() - stressStart);
  const created = stressResults.filter((ticket) => ticket.result.json?.ok === true);
  const failed = stressResults.filter((ticket) => ticket.result.json?.ok !== true);
  check(created.length >= Math.floor(stressResults.length * 0.9), "ventas masivas normal y Pick aceptadas sin romper servidor", {
    attempted: stressResults.length,
    created: created.length,
    failed: failed.length,
    stressElapsedMs,
    sampleFailures: failed.slice(0, 5).map((ticket) => ({ status: ticket.result.status, message: ticket.result.json?.message })),
  });

  const winnerTicket = await createTicket(
    cashierSessions[0].session,
    cashierSessions[0].account,
    admin,
    "winner-mixed-normal-pick",
    [
      play("Q", "32", 2),
      play("P", "1122", 1),
      play("T", "112232", 1),
      play("P3", "256", 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
      play("P3BOX", "652", 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "BOX" }),
      play("P4", "5423", 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "STRAIGHT" }),
      play("P4BOX", "3245", 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "BOX" }),
    ],
  );
  check(winnerTicket.result.json?.ok === true, "ticket ganador mixto creado antes del resultado falso", {
    status: winnerTicket.result.status,
    code: winnerTicket.result.json?.ticket?.ticket_code,
  });

  const expectedRecentIds = [...created.slice(-20).map((ticket) => ticket.clientRequestId), winnerTicket.clientRequestId].filter(Boolean);
  const deltaWait = await waitForTicketsInDelta(admin.id, adminSession.token, expectedRecentIds);
  check(deltaWait.ok, "tickets nuevos llegan a seccion Tickets por delta sin recarga larga", {
    expected: expectedRecentIds.length,
    found: deltaWait.found.length,
    elapsedMs: deltaWait.elapsedMs,
    deltaCount: deltaWait.last?.json?.count,
  });

  const summaryBefore = await getSummary(admin.id, adminSession.token);
  check(summaryBefore.json?.ok === true, "resumen de tickets responde despues de carga", {
    elapsedMs: summaryBefore.elapsedMs,
    count: summaryBefore.json?.count,
    byStatus: summaryBefore.json?.byStatus,
  });

  const limitProbe = await createTicket(
    cashierSessions[0].session,
    cashierSessions[0].account,
    admin,
    "limit-probe-tripleta",
    [play("T", "112232", 76, "9999", "QA Sin Resultado")],
  );
  check(limitProbe.result.json?.ok === false && [409, 500].includes(Number(limitProbe.result.status)), "limite de jugada evita sobreventa de Tripleta", {
    status: limitProbe.result.status,
    message: limitProbe.result.json?.message,
  });

  const lotteryResult = await upsertResults("lotterynet_results_by_day", [
    { id: "1", name: "La Primera Día", number: "11-22-32", status: "published" },
  ]);
  const pickResult = await upsertResults("lotterynet_pick_results_by_day", [
    { id: "19", name: "NJ Pick 3 Dia", number: "2-5-6", pick3: "256", status: "published" },
    { id: "21", name: "NJ Pick 4 Dia", number: "5-4-2-3", pick4: "5423", status: "published" },
  ]);
  check(lotteryResult.ok && pickResult.ok, "resultado falso normal y Pick guardado para validar premios", {
    lotteryStatus: lotteryResult.status,
    pickStatus: pickResult.status,
    lotteryMessage: redact(lotteryResult.json?.message ?? lotteryResult.text).slice(0, 160),
    pickMessage: redact(pickResult.json?.message ?? pickResult.text).slice(0, 160),
  });

  await new Promise((resolve) => setTimeout(resolve, 1800));
  const deltaAfterResults = await getDelta(admin.id, adminSession.token);
  const winnerRow = (deltaAfterResults.json?.tickets ?? []).find((ticket) => ticket.client_request_id === winnerTicket.clientRequestId);
  const winnerAmount = Number(winnerRow?.payout_amount ?? winnerRow?.total_prize ?? 0);
  check(winnerAmount > 0 || lower(winnerRow?.status).includes("gan"), "premio falso actualiza ticket ganador en servidor", {
    status: winnerRow?.status,
    payout_amount: winnerRow?.payout_amount,
    total_prize: winnerRow?.total_prize,
    elapsedMs: deltaAfterResults.elapsedMs,
  });

  const payResult = await payTicket(adminSession, admin, admin, winnerTicket);
  check(payResult.json?.ok === true, "cobro de premio falso funciona", {
    status: payResult.status,
    amount: payResult.json?.amount,
    message: payResult.json?.message,
  });

  const blockedByPublished = await createTicket(
    cashierSessions[1].session,
    cashierSessions[1].account,
    admin,
    "published-result-block",
    [play("Q", "32", 1)],
  );
  check(blockedByPublished.result.json?.ok === false && [409, 500].includes(Number(blockedByPublished.result.status)), "venta nueva bloqueada cuando ya hay resultado publicado", {
    status: blockedByPublished.result.status,
    message: blockedByPublished.result.json?.message,
  });

  const adminReport = await report("get-admin-report", { actorKey: admin.id, adminKey: admin.id }, adminSession.token);
  const cashierReport = await report("get-cashier-report", { actorKey: cashierSessions[0].account.user, adminKey: admin.id, cashierKey: cashierSessions[0].account.user }, cashierSessions[0].session.token);
  const supervisorReport = await report("get-supervisor-report", {
    actorKey: supervisorSetup.supervisor.user,
    adminKey: admin.id,
    supervisorKey: supervisorSetup.supervisor.user,
  }, supervisorSession.token);
  check(adminReport.json?.ok === true, "monitoreo/reporte admin responde con ventas y comision", {
    elapsedMs: adminReport.elapsedMs,
    summary: adminReport.json?.summary,
  });
  check(cashierReport.json?.ok === true, "monitoreo/reporte cajero responde", {
    elapsedMs: cashierReport.elapsedMs,
    summary: cashierReport.json?.summary,
  });
  check(supervisorReport.json?.ok === true, "monitoreo/reporte supervisor responde", {
    elapsedMs: supervisorReport.elapsedMs,
    summary: supervisorReport.json?.summary,
    message: supervisorReport.json?.message,
  });
  check(Number(supervisorReport.json?.summary?.tickets ?? 0) > 0, "supervisor ve las ventas de sus cajeros asignados", {
    summary: supervisorReport.json?.summary,
    assigned: supervisorSetup.assigned.map((cashier) => cashier.user),
  });
  check(Object.prototype.hasOwnProperty.call(adminReport.json?.summary ?? {}, "comision"), "reporte incluye campo comision", {
    comision: adminReport.json?.summary?.comision,
    supervisorComision: adminReport.json?.summary?.supervisorComision,
  });
}

function timingSummary() {
  return Object.entries(timings)
    .map(([label, values]) => ({
      label,
      count: values.length,
      min: Math.min(...values),
      p50: percentile(values, 0.5),
      p95: percentile(values, 0.95),
      max: Math.max(...values),
    }))
    .sort((a, b) => b.count - a.count);
}

async function writeSummary() {
  const passed = checks.filter((item) => item.ok).length;
  const failed = checks.length - passed;
  const timingRows = timingSummary().map((row) =>
    `| ${row.label} | ${row.count} | ${row.min} | ${row.p50} | ${row.p95} | ${row.max} |`
  ).join("\n");
  const bugRows = checks
    .filter((item) => !item.ok)
    .map((item, index) => `${index + 1}. ${item.label}: \`${redact(JSON.stringify(item.data ?? {})).slice(0, 500)}\``)
    .join("\n") || "No hubo checks marcados como BUG.";
  const passRows = checks
    .filter((item) => item.ok)
    .map((item) => `- ${item.label}`)
    .join("\n");
  const summary = `# LotteryNet workday stress smoke - ${new Date().toISOString().slice(0, 10)}

Run ID: \`${runId}\`
Fecha falsa usada: \`${fakeDayKey}\`
Log completo: \`${decodeURIComponent(logFile.pathname).replace(/^\/([A-Z]:)/, "$1")}\`

## Resultado

- Checks pasados: \`${passed}\`
- Checks con BUG/riesgo: \`${failed}\`
- Tickets intentados por carga: \`${stressRounds * 9}\` aproximados, segun cajeros disponibles
- Concurrencia Node: \`${concurrency}\`

## Paso

${passRows || "- Ningun check paso."}

## Bugs / riesgos

${bugRows}

## Tiempos HTTP

| Ruta | Llamadas | min ms | p50 ms | p95 ms | max ms |
| --- | ---: | ---: | ---: | ---: | ---: |
${timingRows}

## Limpieza

- Usuarios/comisiones/supervisor: ${usersPayloadWasChanged ? "se intento restaurar payload original" : "sin cambios"}
- Limites de cajero: ${limitPayloadWasChanged ? "se intento restaurar payload original" : "sin cambios guardados"}
- Resultados falsos: se intento borrar \`${fakeDayKey}\`
`;
  await writeFile(summaryFile, summary, "utf8");
}

try {
  await main();
} catch (error) {
  check(false, "prueba interrumpida", { message: error?.message, stack: error?.stack });
} finally {
  if (originalUsersPayload && usersPayloadWasChanged) {
    try {
      await saveUsersPayload(originalUsersPayload, cleanupBearerToken);
      log("CLEANUP usuarios restaurados");
    } catch (error) {
      log("BUG no se pudo restaurar usuarios", { message: error?.message });
    }
  }
  if (limitPayloadWasChanged) {
    try {
      const restoreValue = originalLimitPayload === undefined
        ? { defaults: { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 } }
        : originalLimitPayload;
      const restore = await saveLimitPayload("ADM-C5FFB0", restoreValue);
      log(restore.ok ? "CLEANUP limites restaurados" : "BUG no se pudo restaurar limites", { status: restore.status, message: restore.json?.message });
    } catch (error) {
      log("BUG no se pudo restaurar limites", { message: error?.message });
    }
  }
  try {
    await deleteResults("lotterynet_results_by_day");
    await deleteResults("lotterynet_pick_results_by_day");
    log("CLEANUP resultados falsos borrados");
  } catch (error) {
    log("BUG no se pudo borrar resultado falso", { message: error?.message });
  }
  await writeSummary();
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname).replace(/^\/([A-Z]:)/, "$1") });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname).replace(/^\/([A-Z]:)/, "$1") });
}
