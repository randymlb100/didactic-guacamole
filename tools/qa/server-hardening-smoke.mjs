const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const runId = `hardening${Date.now()}`;
const fakeIsoDate = "2026-02-17";
const fakeDayKey = "17-02-2026";
const adminUsername = "podero02";
const cashierUsername = "bancae01";
const credentialFile = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("../../contraseña de prueba.txt", import.meta.url);

const checks = [];

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function headers(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

function check(condition, label, data = {}) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  console.log(`${ok ? "PASS" : "BUG"} ${label} ${JSON.stringify(data)}`);
}

async function requestJson(label, method, url, body, token = API_KEY) {
  const started = performance.now();
  const response = await fetch(url, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  console.log(`HTTP ${label} ${JSON.stringify({ status: response.status, elapsedMs, ok: response.ok, message: clean(json?.message ?? json?.error).slice(0, 180) })}`);
  return { status: response.status, ok: response.ok, elapsedMs, text, json };
}

async function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
}

function parseCredentials(text) {
  return [...text.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
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

async function fetchUsersPayload() {
  const result = await requestJson(
    "users fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return { username, ok: result.json?.ok === true && clean(result.json?.accessToken), token: result.json?.accessToken, user: result.json?.user, result };
}

function play(playType, number, amount, lotteryId, lotteryName) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName };
}

async function createNonWinnerTicket(session, cashier, admin) {
  const clientRequestId = `${runId}-plain-ticket`;
  const body = {
    clientRequestId,
    localTicketId: clientRequestId,
    adminKey: admin.id,
    adminId: admin.id,
    actorKey: cashier.user,
    actorId: cashier.id,
    actorRole: "cajero",
    cashierKey: cashier.user,
    cashierId: cashier.id,
    drawDate: fakeIsoDate,
    dayKey: fakeDayKey,
    lotteryName: "QA Hardening Dia",
    phoneTime: new Date().toISOString(),
    plays: [play("Q", "88", 5, "9988", "QA Hardening Dia")],
  };
  return { clientRequestId, result: await edge("create-ticket-v2", body, session.token) };
}

async function cleanupResults() {
  await requestJson("lottery cleanup", "DELETE", `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}`).catch(() => null);
  await requestJson("pick cleanup", "DELETE", `${SUPABASE_URL}/rest/v1/lotterynet_pick_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}`).catch(() => null);
}

async function readCredentialText() {
  const { readFile } = await import("node:fs/promises");
  return readFile(credentialFile, "utf8");
}

async function run() {
  console.log(`Inicio server hardening smoke ${runId}`);
  await cleanupResults();
  const payload = await fetchUsersPayload();
  const unauthorizedUsersWrite = await edge("lotterynet-users-state", { action: "upsert", payload });
  check(
    [401, 403].includes(unauthorizedUsersWrite.status),
    "users-state rechaza escritura anonima/public-key",
    { status: unauthorizedUsersWrite.status, message: unauthorizedUsersWrite.json?.message },
  );

  const credentials = parseCredentials(await readCredentialText());
  const admin = findAccount(payload, adminUsername);
  const cashier = findAccount(payload, cashierUsername);
  const adminCred = credentials.find((row) => lower(row.username) === adminUsername);
  const cashierCred = credentials.find((row) => lower(row.username) === cashierUsername);
  check(Boolean(admin && cashier && adminCred && cashierCred), "credenciales y cuentas de prueba disponibles", {
    admin: Boolean(admin),
    cashier: Boolean(cashier),
    adminCred: Boolean(adminCred),
    cashierCred: Boolean(cashierCred),
  });
  if (!admin || !cashier || !adminCred || !cashierCred) throw new Error("Faltan cuentas de prueba.");

  const adminSession = await login(adminCred.username, adminCred.password);
  const cashierSession = await login(cashierCred.username, cashierCred.password);
  check(adminSession.ok, "login admin para hardening valido", { status: adminSession.result.status });
  check(cashierSession.ok, "login cajero para hardening valido", { status: cashierSession.result.status });

  const authorizedUsersWrite = await edge("lotterynet-users-state", { action: "upsert", payload }, adminSession.token);
  check(
    authorizedUsersWrite.ok && authorizedUsersWrite.json?.ok !== false,
    "users-state acepta escritura con JWT admin",
    { status: authorizedUsersWrite.status, message: authorizedUsersWrite.json?.message },
  );

  const ticket = await createNonWinnerTicket(cashierSession, cashier, admin);
  check(ticket.result.json?.ok === true, "ticket simple creado para pago no ganador", {
    status: ticket.result.status,
    code: ticket.result.json?.ticketCode,
  });

  const pay = await edge("pay-ticket", {
    clientRequestId: ticket.clientRequestId,
    actorKey: cashier.user,
    adminKey: admin.id,
    cashierKey: cashier.user,
  }, cashierSession.token);
  check(
    pay.status === 409 || pay.status === 422,
    "pago sin premio confirmado devuelve rechazo controlado no 500",
    { status: pay.status, message: pay.json?.message },
  );

  const failed = checks.filter((item) => !item.ok);
  console.log(`Resumen hardening: ${checks.length - failed.length}/${checks.length} checks pasados`);
  if (failed.length) process.exit(1);
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
