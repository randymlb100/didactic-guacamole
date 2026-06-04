import { readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const LOG_FILE = new URL(`./real-flow-smoke-${new Date().toISOString().replace(/[:.]/g, "-")}.log`, import.meta.url);

const runId = `qa${Date.now()}`;
const runSeed = Number(runId.replace(/\D/g, "").slice(-8));
const fakeIsoDate = "2026-01-15";
const fakeDayKey = "15-01-2026";
const qaLotteryId = `97${runId.slice(-6)}`;
const qaLotteryName = `QA Flujo Sin Resultado ${runId.slice(-6)}`;
const qaAdminLotteryId = `96${runId.slice(-6)}`;
const qaAdminLotteryName = `QA Admin Sin Tope ${runId.slice(-6)}`;
const qaN1 = String((runSeed % 90) + 10).padStart(2, "0");
const qaN2 = String(((runSeed + 17) % 90) + 10).padStart(2, "0");
const qaN3 = String(((runSeed + 34) % 90) + 10).padStart(2, "0");
const qaQuiniela = qaN3;
const qaPale = `${qaN2}${qaN3}`;
const qaTripleta = `${qaN1}${qaN2}${qaN3}`;
const qaResultNumber = `${qaN1}-${qaN2}-${qaN3}`;
const adminUsername = "podero02";
const cashierPrefix = "bancae";
const testSupervisorUser = `sup${runId.slice(-6)}`;
const testSupervisorPassword = `Sup${runId.slice(-6)}!`;

const logLines = [];
const createdClientIds = [];
let originalUsersPayload = null;
let originalResultsPayload = null;
let originalResultsExisted = false;
let usersPayloadWasChanged = false;
let cleanupBearerToken = API_KEY;

function log(line, data) {
  const suffix = data === undefined ? "" : ` ${JSON.stringify(data)}`;
  const text = `[${new Date().toISOString()}] ${line}${suffix}`;
  logLines.push(text);
  console.log(text);
}

function ok(condition, message, data) {
  if (condition) {
    log(`PASS ${message}`, data);
    return true;
  }
  log(`BUG ${message}`, data);
  return false;
}

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function parseCredentials(text) {
  const rows = [...text.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)];
  return rows.map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
}

function roleOf(account) {
  return lower(account.role) === "cashier" ? "cajero" : lower(account.role);
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

function stableUniqueAccounts(accounts) {
  const seen = new Set();
  return accounts.filter((account) => {
    const key = `${lower(account.id)}|${lower(account.user ?? account.username)}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function sha256Hex(input) {
  return createHash("sha256").update(input).digest("hex");
}

function authHeaders(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

async function requestJson(label, method, url, body, token = API_KEY, extraHeaders = {}) {
  const start = performance.now();
  const response = await fetch(url, {
    method,
    headers: { ...authHeaders(token), ...extraHeaders },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const elapsedMs = Math.round(performance.now() - start);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  log(`HTTP ${label}`, { status: response.status, elapsedMs, ok: response.ok });
  return { status: response.status, elapsedMs, ok: response.ok, json, text };
}

async function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
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

async function saveUsersPayload(payload, token = API_KEY) {
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

  log("INFO guardado directo de usuarios bloqueado; probando fallback Edge", {
    status: direct.status,
    body: direct.text,
  });

  const legacyEdge = await edge("lotterynet-users-state", { action: "upsert", payload }, token);
  if (legacyEdge.ok && legacyEdge.json?.ok !== false) return { route: "legacy-edge" };

  log("BUG fallback Edge usuarios fallo; probando Render", {
    status: legacyEdge.status,
    body: legacyEdge.text,
  });

  const render = await requestJson(
    "render users-state upsert",
    "POST",
    "https://didactic-guacamole.onrender.com/users-state",
    { payload },
  );
  if (render.ok) return { route: "render" };

  throw new Error(`No se pudo guardar usuarios: REST=${direct.text} EDGE=${legacyEdge.text} RENDER=${render.text}`);
}

async function upsertResults(payload) {
  const row = { result_date: fakeDayKey, payload, updated_at: new Date().toISOString() };
  return requestJson(
    "results upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?on_conflict=result_date`,
    row,
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

async function fetchResultsPayload() {
  const result = await requestJson(
    "results fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer resultados QA: ${result.text}`);
  originalResultsExisted = Array.isArray(result.json) && result.json.length > 0;
  originalResultsPayload = result.json?.[0]?.payload ?? null;
}

async function deleteResults() {
  return requestJson(
    "results cleanup",
    "DELETE",
    `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}`,
    undefined,
    API_KEY,
  );
}

async function restoreResults() {
  if (!originalResultsExisted) return deleteResults();
  return upsertResults(originalResultsPayload);
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
  return {
    id: `SUP-${runId.slice(-8).toUpperCase()}`,
    user: testSupervisorUser,
    username: testSupervisorUser,
    role: "supervisor",
    nombre: "Supervisor QA Flujo",
    displayName: "Supervisor QA Flujo",
    active: true,
    activo: true,
    adminId: admin.id,
    adminUser: admin.user,
    banca: admin.banca,
    territory: admin.territory ?? "RD",
    commissionRate: 0.03,
    passwordSalt: salt,
    passwordHash: sha256Hex(`${salt}:${testSupervisorPassword}`),
    passwordVersion: "sha256-v1",
    credChangedAt: now,
    credChangedAtEpochMs: now,
    updatedAt: now,
    updatedAtEpochMs: now,
    supervisorIds: [],
    supervisorUsers: [],
    assignedCashiers: assignedCashiers.map((cashier) => cashier.user),
  };
}

function addSupervisorGroup(payload, admin, cashiers) {
  const next = structuredClone(payload);
  const assigned = cashiers.slice(0, 3);
  const supervisor = createSupervisorPayload(admin, assigned);
  next.supervisores = stableUniqueAccounts([...(Array.isArray(next.supervisores) ? next.supervisores : []), supervisor]);
  if (Array.isArray(next.users)) {
    next.users = next.users.map((account) => {
      if (!assigned.some((cashier) => lower(cashier.user) === lower(account.user))) return account;
      return {
        ...account,
        supervisorIds: [...new Set([...(account.supervisorIds ?? []), supervisor.id])],
        supervisorUsers: [...new Set([...(account.supervisorUsers ?? []), supervisor.user])],
        updatedAtEpochMs: Date.now(),
      };
    });
  }
  return { payload: next, supervisor, assigned };
}

function setUserBlocked(payload, username, blocked) {
  const next = structuredClone(payload);
  const update = (account) => lower(account.user ?? account.username) === lower(username)
    ? { ...account, blocked, isBlocked: blocked, updatedAtEpochMs: Date.now() }
    : account;
  for (const key of ["users", "admins", "supervisores", "supervisors", "cajeros", "cashiers"]) {
    if (Array.isArray(next[key])) next[key] = next[key].map(update);
  }
  return next;
}

function play(playType, number, amount, lotteryId = qaLotteryId, lotteryName = qaLotteryName) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName };
}

async function createTicket(session, actor, admin, plays, label, overrides = {}) {
  const clientRequestId = `${runId}-${label}-${Math.random().toString(36).slice(2, 8)}`;
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

async function getDelta(ownerKey, token, limit = 300) {
  return edge("get-ticket-delta", { ownerKey, limit }, token);
}

async function getSummary(ownerKey, token) {
  return edge("get-ticket-summary", { ownerKey, dayKey: fakeDayKey }, token);
}

async function payTicket(session, actor, admin, ticket) {
  return edge("pay-ticket", {
    actorKey: actor.user,
    adminKey: admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.clientRequestId,
  }, session.token);
}

async function deleteTicket(session, actor, admin, ticket) {
  return edge("void-ticket", {
    actorKey: actor.user,
    adminKey: admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.clientRequestId,
    action: "delete",
    returnLimit: true,
  }, session.token);
}

async function main() {
  log("Inicio prueba flujo real", { runId, fakeIsoDate, fakeDayKey });

  const credentialsText = await readFile(CREDENTIAL_FILE, "utf8");
  const credentials = parseCredentials(credentialsText);
  originalUsersPayload = await fetchUsersPayload();
  await fetchResultsPayload();

  const admin = findAccount(originalUsersPayload, adminUsername);
  const cashiers = credentials
    .filter((entry) => lower(entry.username).startsWith(cashierPrefix))
    .map((entry) => ({ credentials: entry, account: findAccount(originalUsersPayload, entry.username) }))
    .filter((entry) => entry.account);
  ok(Boolean(admin), "admin de prueba existe en servidor", { adminUsername, id: admin?.id });
  ok(cashiers.length === 9, "los 9 cajeros de prueba existen en servidor", { found: cashiers.map((entry) => entry.account.user) });

  const adminCredential = credentials.find((entry) => lower(entry.username) === adminUsername);
  const loginTargets = [adminCredential, ...cashiers.map((entry) => entry.credentials)].filter(Boolean);
  const sessions = new Map();
  for (const credential of loginTargets) {
    const session = await login(credential.username, credential.password);
    sessions.set(lower(credential.username), session);
    ok(session.ok, `login/JWT valido para ${credential.username}`, { status: session.status, message: session.message });
  }

  const adminSession = sessions.get(lower(adminUsername));
  if (adminSession?.token) cleanupBearerToken = adminSession.token;

  const { payload: payloadWithSupervisor, supervisor, assigned } = addSupervisorGroup(
    originalUsersPayload,
    admin,
    cashiers.map((entry) => entry.account),
  );
  await saveUsersPayload(payloadWithSupervisor, adminSession.token);
  usersPayloadWasChanged = true;
  ok(true, "supervisor y grupo guardados como lo hace Ajustes", {
    supervisor: supervisor.user,
    assigned: assigned.map((cashier) => cashier.user),
  });
  const supervisorSession = await login(testSupervisorUser, testSupervisorPassword);
  ok(supervisorSession.ok, "supervisor nuevo recibe JWT sin pedir arreglo manual", {
    status: supervisorSession.status,
    user: supervisorSession.user,
    message: supervisorSession.message,
  });

  const cashierSessions = cashiers.map((entry) => ({
    ...entry,
    session: sessions.get(lower(entry.account.user)),
  }));

  const createdTickets = [];
  for (const entry of cashierSessions) {
    const ticket = await createTicket(
      entry.session,
      entry.account,
      admin,
      [
        play("Q", qaQuiniela, 3),
        play("P", qaPale, 1),
        play("T", qaTripleta, 1),
      ],
      entry.account.user,
    );
    ok(ticket.result.json?.ok === true, `venta valida con ${entry.account.user}`, {
      status: ticket.result.status,
      message: ticket.result.json?.message,
      ticketCode: ticket.result.json?.ticket?.ticket_code,
    });
    if (ticket.result.json?.ok === true) createdTickets.push(ticket);
  }

  const adminTicket = await createTicket(
    adminSession,
    admin,
    admin,
    [play("T", qaTripleta, 76, qaAdminLotteryId, qaAdminLotteryName)],
    "admin-no-tope",
  );
  ok(adminTicket.result.json?.ok === true, "admin vende Tripleta 76 sin tope de cajero", {
    status: adminTicket.result.status,
    message: adminTicket.result.json?.message,
  });
  if (adminTicket.result.json?.ok === true) createdTickets.push(adminTicket);

  const limitTicket = await createTicket(
    cashierSessions[0].session,
    cashierSessions[0].account,
    admin,
    [play("T", qaTripleta, 76)],
    "cashier-limit-block",
  );
  ok(limitTicket.result.json?.ok === false && limitTicket.result.status === 409, "cajero queda bloqueado por tope Tripleta 75", {
    status: limitTicket.result.status,
    message: limitTicket.result.json?.message,
  });

  const summaryBefore = await getSummary(admin.id, adminSession.token);
  ok(summaryBefore.json?.ok === true && summaryBefore.json?.count >= createdTickets.length, "ticket resumido aparece por servidor", {
    elapsedMs: summaryBefore.elapsedMs,
    count: summaryBefore.json?.count,
    byStatus: summaryBefore.json?.byStatus,
  });

  const deltaBefore = await getDelta(admin.id, adminSession.token);
  const deltaCreatedIds = new Set((deltaBefore.json?.tickets ?? []).map((ticket) => ticket.client_request_id));
  ok(createdTickets.every((ticket) => deltaCreatedIds.has(ticket.clientRequestId)), "delta devuelve tickets nuevos sin esperar recarga larga", {
    elapsedMs: deltaBefore.elapsedMs,
    count: deltaBefore.json?.count,
  });

  const resultPayload = [
    { id: "1", name: "La Primera Día", number: "11-22-32", status: "published" },
    { id: "2", name: "Anguila Mañana", number: "60-93-48", status: "published" },
    { id: qaLotteryId, name: qaLotteryName, number: qaResultNumber, status: "published" },
    { id: qaAdminLotteryId, name: qaAdminLotteryName, number: qaResultNumber, status: "published" },
  ];
  const resultUpsert = await upsertResults(resultPayload);
  ok(resultUpsert.ok, "resultado falso guardado y dispara validacion automatica", {
    status: resultUpsert.status,
    elapsedMs: resultUpsert.elapsedMs,
  });

  await new Promise((resolve) => setTimeout(resolve, 1500));
  const deltaAfterResult = await getDelta(admin.id, adminSession.token);
  const createdRows = (deltaAfterResult.json?.tickets ?? []).filter((ticket) => createdClientIds.includes(ticket.client_request_id));
  const winners = createdRows.filter((ticket) => lower(ticket.status ?? ticket.estado) === "ganador" || Number(ticket.payout_amount) > 0);
  ok(winners.length >= createdTickets.length, "tickets ganadores se reflejan en servidor/delta/app", {
    elapsedMs: deltaAfterResult.elapsedMs,
    winners: winners.map((ticket) => ({ client_request_id: ticket.client_request_id, status: ticket.status, payout_amount: ticket.payout_amount })),
  });

  const blockedByPublished = await createTicket(
    cashierSessions[1].session,
    cashierSessions[1].account,
    admin,
    [play("Q", "32", 1, "1", "La Primera Día")],
    "published-result-block",
  );
  ok(blockedByPublished.result.json?.ok === false && blockedByPublished.result.status === 409, "loteria con resultado publicado bloquea venta nueva", {
    status: blockedByPublished.result.status,
    message: blockedByPublished.result.json?.message,
  });

  if (createdTickets.length > 0) {
    const payResult = await payTicket(adminSession, admin, admin, createdTickets[0]);
    ok(payResult.json?.ok === true, "pago de ticket ganador funciona", {
      status: payResult.status,
      message: payResult.json?.message,
    });
  } else {
    ok(false, "no hubo ticket creado para probar pago");
  }

  const deleteCandidate = await createTicket(
    cashierSessions[2].session,
    cashierSessions[2].account,
    admin,
    [play("Q", "44", 1, "9999", "QA Sin Resultado")],
    "delete-candidate",
  );
  if (deleteCandidate.result.json?.ok === true) {
    const deleteResult = await deleteTicket(cashierSessions[2].session, cashierSessions[2].account, admin, deleteCandidate);
    ok(deleteResult.json?.ok === true, "borrado/anulacion reciente funciona", {
      status: deleteResult.status,
      message: deleteResult.json?.message,
    });
  } else {
    ok(false, "no pude crear ticket candidato para borrar", {
      status: deleteCandidate.result.status,
      message: deleteCandidate.result.json?.message,
    });
  }

  const blockedPayload = setUserBlocked(payloadWithSupervisor, cashiers[8].account.user, true);
  await saveUsersPayload(blockedPayload, adminSession.token);
  const blockedSale = await createTicket(
    cashierSessions[8].session,
    cashierSessions[8].account,
    admin,
    [play("Q", "55", 1, "9999", "QA Sin Resultado")],
    "blocked-user-sale",
  );
  ok(blockedSale.result.json?.ok === false && blockedSale.result.status === 403, "usuario bloqueado no puede vender", {
    status: blockedSale.result.status,
    message: blockedSale.result.json?.message,
  });

  const finalSummary = await getSummary(admin.id, adminSession.token);
  ok(finalSummary.json?.ok === true, "resumen final responde despues de ventas/pago/borrado", {
    elapsedMs: finalSummary.elapsedMs,
    count: finalSummary.json?.count,
    byStatus: finalSummary.json?.byStatus,
  });
}

try {
  await main();
} catch (error) {
  log("BUG prueba interrumpida", { message: error?.message, stack: error?.stack });
} finally {
  if (originalUsersPayload && usersPayloadWasChanged) {
    try {
      await saveUsersPayload(originalUsersPayload, cleanupBearerToken);
      log("CLEANUP usuarios restaurados");
    } catch (error) {
      log("BUG no se pudo restaurar usuarios", { message: error?.message });
    }
  }
  try {
    await restoreResults();
    log(originalResultsExisted ? "CLEANUP resultados originales restaurados" : "CLEANUP resultados falsos borrados");
  } catch (error) {
    log("BUG no se pudo borrar resultado falso", { message: error?.message });
  }
  await writeFile(LOG_FILE, `${logLines.join("\n")}\n`, "utf8");
  log(`LOG_FILE ${decodeURIComponent(LOG_FILE.pathname)}`);
}
