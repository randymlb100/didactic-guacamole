import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const logFile = new URL(`./pick-mode-results-smoke-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./pick-mode-results-smoke-summary-${stamp}.json`, import.meta.url);

const runId = `pickmode${Date.now()}`;
const fakeDay = String(13 + (Date.now() % 14)).padStart(2, "0");
const fakeIsoDate = `2026-02-${fakeDay}`;
const fakeDayKey = `${fakeDay}-02-2026`;
const adminUsername = "podero02";
const cashierUsernames = ["bancae01", "bancae02"];
const modeKey = "system_modes:ADM-C5FFB0";
const qaLotteryId = "99901";
const qaLotteryName = "QA Mode Loteria";

const lines = [];
const checks = [];
const timings = {};
let originalModePayload = undefined;

function log(label, data) {
  const line = `[${new Date().toISOString()}] ${label}${data === undefined ? "" : ` ${JSON.stringify(data)}`}`;
  lines.push(line);
  console.log(line);
}

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

function recordTiming(label, elapsedMs) {
  if (!timings[label]) timings[label] = [];
  timings[label].push(elapsedMs);
}

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

function timingSummary() {
  return Object.fromEntries(Object.entries(timings).map(([label, values]) => [
    label,
    {
      calls: values.length,
      minMs: Math.min(...values),
      p50Ms: percentile(values, 0.5),
      p95Ms: percentile(values, 0.95),
      maxMs: Math.max(...values),
    },
  ]));
}

function check(condition, label, data = {}) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  log(`${ok ? "PASS" : "BUG"} ${label}`, data);
  return ok;
}

async function requestJson(label, method, url, body, token = API_KEY, extraHeaders = {}) {
  const started = performance.now();
  const response = await fetch(url, {
    method,
    headers: headers(token, extraHeaders),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const elapsedMs = Math.round(performance.now() - started);
  recordTiming(label, elapsedMs);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  log(`HTTP ${label}`, { status: response.status, elapsedMs, ok: response.ok, message: clean(json?.message ?? json?.error).slice(0, 180) });
  return { status: response.status, ok: response.ok, elapsedMs, text, json };
}

function edge(slug, body, token = API_KEY) {
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
    "users-state fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return {
    username,
    ok: result.json?.ok === true && clean(result.json?.accessToken),
    token: result.json?.accessToken,
    user: result.json?.user,
    status: result.status,
    text: result.text,
  };
}

async function fetchMasterValue(key) {
  const result = await edge("get-master-config", { action: "fetch", key });
  if (!result.ok || result.json?.ok === false) return { ok: false, status: result.status, text: result.text, value: undefined };
  return { ok: true, status: result.status, value: result.json?.payload };
}

async function saveMasterValue(key, value) {
  return edge("update-master-config", { key, payload: value });
}

function systemModePayload(mode) {
  const lottery = mode === "lottery" || mode === "both";
  const pick = mode === "pick" || mode === "both";
  return {
    posLiteEnabled: false,
    lotteryModeEnabled: lottery,
    pickModeEnabled: pick,
    cashierPickEnabled: pick,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: lottery,
    cashierPickModeEnabled: pick,
    updatedAt: Date.now(),
  };
}

async function setAndVerifyMode(mode) {
  const save = await saveMasterValue(modeKey, systemModePayload(mode));
  const fetched = await fetchMasterValue(modeKey);
  const payload = fetched.value ?? {};
  const expected = systemModePayload(mode);
  return {
    save,
    fetched,
    ok: save.ok && save.json?.ok !== false &&
      fetched.ok &&
      payload.lotteryModeEnabled === expected.lotteryModeEnabled &&
      payload.pickModeEnabled === expected.pickModeEnabled &&
      payload.cashierLotteryModeEnabled === expected.cashierLotteryModeEnabled &&
      payload.cashierPickModeEnabled === expected.cashierPickModeEnabled,
    payload,
  };
}

function play(playType, number, amount, lotteryId, lotteryName, extra = {}) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName, ...extra };
}

async function createTicket(session, cashier, admin, label, plays, lotteryName, lotteryId) {
  const clientRequestId = `${runId}-${label}`;
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
    sorteoId: lotteryId,
    drawDate: fakeIsoDate,
    dayKey: fakeDayKey,
    lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
  const result = await edge("create-ticket-v2", body, session.token);
  return { clientRequestId, body, result };
}

async function upsertPickResult() {
  const payload = [
    { id: "19", name: "NJ Pick 3 Dia", pick3: "256", number: "2-5-6", status: "published" },
    { id: "21", name: "NJ Pick 4 Dia", pick4: "1234", number: "1-2-3-4", status: "published" },
  ];
  return requestJson(
    "lotterynet_pick_results_by_day upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_pick_results_by_day?on_conflict=result_date`,
    { result_date: fakeDayKey, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

async function upsertLotteryResult() {
  const payload = [
    { id: qaLotteryId, name: qaLotteryName, number: "11-22-32", status: "published" },
  ];
  return requestJson(
    "lotterynet_results_by_day upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?on_conflict=result_date`,
    { result_date: fakeDayKey, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

async function deleteResults(table) {
  return requestJson(
    `${table} cleanup`,
    "DELETE",
    `${SUPABASE_URL}/rest/v1/${table}?result_date=eq.${encodeURIComponent(fakeDayKey)}`,
  );
}

async function getDelta(adminId, token) {
  return edge("get-ticket-delta", { ownerKey: adminId, cursor: "2000-01-01T00:00:00.000Z", limit: 300 }, token);
}

async function waitForTicketStatus(adminId, token, clientRequestId, expectedStatus) {
  const started = performance.now();
  let last = null;
  for (let attempt = 0; attempt < 8; attempt++) {
    last = await getDelta(adminId, token);
    const ticket = (last.json?.tickets ?? []).find((item) => item.client_request_id === clientRequestId);
    if (ticket?.status === expectedStatus) {
      return { ok: true, elapsedMs: Math.round(performance.now() - started), ticket };
    }
    await new Promise((resolve) => setTimeout(resolve, 800));
  }
  return { ok: false, elapsedMs: Math.round(performance.now() - started), last };
}

function report(slug, body, token) {
  return edge(slug, { ...body, from: fakeIsoDate, to: fakeIsoDate }, token);
}

async function main() {
  log("Inicio pick/mode/results smoke", { runId, fakeIsoDate, fakeDayKey, modeKey });
  await deleteResults("lotterynet_results_by_day");
  await deleteResults("lotterynet_pick_results_by_day");

  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const usersPayload = await fetchUsersPayload();
  const admin = findAccount(usersPayload, adminUsername);
  const cashiers = cashierUsernames.map((username) => ({
    account: findAccount(usersPayload, username),
    credential: credentials.find((entry) => lower(entry.username) === username),
  }));
  check(Boolean(admin), "admin de prueba existe", { admin: admin?.user, id: admin?.id });
  check(cashiers.every((entry) => entry.account && entry.credential), "cajeros de prueba existen", {
    cashiers: cashiers.map((entry) => entry.account?.user),
  });
  if (!admin || cashiers.some((entry) => !entry.account || !entry.credential)) throw new Error("Faltan cuentas de prueba.");

  const adminCredential = credentials.find((entry) => lower(entry.username) === adminUsername);
  const adminSession = await login(adminCredential.username, adminCredential.password);
  const cashierSessions = [];
  for (const entry of cashiers) {
    cashierSessions.push({ ...entry, session: await login(entry.credential.username, entry.credential.password) });
  }
  check(adminSession.ok, "login admin valido", { status: adminSession.status });
  check(cashierSessions.every((entry) => entry.session.ok), "login cajeros valido", {
    statuses: cashierSessions.map((entry) => ({ user: entry.account.user, status: entry.session.status })),
  });

  const originalMode = await fetchMasterValue(modeKey);
  originalModePayload = originalMode.value;
  check(originalMode.ok, "modo original se puede leer", { status: originalMode.status, value: originalMode.value });

  const pickMode = await setAndVerifyMode("pick");
  check(pickMode.ok, "modo Solo Pick se guarda y se lee", { saveStatus: pickMode.save.status, payload: pickMode.payload, message: pickMode.save.json?.message });
  const pickSale = await createTicket(
    cashierSessions[0].session,
    cashierSessions[0].account,
    admin,
    "solo-pick",
    [
      play("P3", "256", 2, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
      play("P4", "1234", 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "STRAIGHT" }),
    ],
    "NJ Pick 3 Dia",
    "19",
  );
  check(pickSale.result.json?.ok === true, "venta Pick en modo Solo Pick aceptada", {
    status: pickSale.result.status,
    code: pickSale.result.json?.ticket?.ticket_code ?? pickSale.result.json?.ticketCode,
  });

  const lotteryMode = await setAndVerifyMode("lottery");
  check(lotteryMode.ok, "modo Solo Loteria se guarda y se lee", { saveStatus: lotteryMode.save.status, payload: lotteryMode.payload, message: lotteryMode.save.json?.message });
  const lotterySale = await createTicket(
    cashierSessions[1].session,
    cashierSessions[1].account,
    admin,
    "solo-lottery",
    [play("Q", "45", 4, qaLotteryId, qaLotteryName)],
    qaLotteryName,
    qaLotteryId,
  );
  check(lotterySale.result.json?.ok === true, "venta Loteria en modo Solo Loteria aceptada", {
    status: lotterySale.result.status,
    code: lotterySale.result.json?.ticket?.ticket_code ?? lotterySale.result.json?.ticketCode,
  });

  const bothMode = await setAndVerifyMode("both");
  check(bothMode.ok, "modo Loteria + Pick se guarda y se lee", { saveStatus: bothMode.save.status, payload: bothMode.payload, message: bothMode.save.json?.message });
  const bothPickSale = await createTicket(
    cashierSessions[0].session,
    cashierSessions[0].account,
    admin,
    "both-pick",
    [play("P3BOX", "652", 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "BOX" })],
    "NJ Pick 3 Dia",
    "19",
  );
  const bothLotterySale = await createTicket(
    cashierSessions[1].session,
    cashierSessions[1].account,
    admin,
    "both-lottery",
    [play("Q", "32", 3, qaLotteryId, qaLotteryName)],
    qaLotteryName,
    qaLotteryId,
  );
  check(bothPickSale.result.json?.ok === true && bothLotterySale.result.json?.ok === true, "ventas Pick y Loteria en modo combinado aceptadas", {
    pickStatus: bothPickSale.result.status,
    lotteryStatus: bothLotterySale.result.status,
  });

  const lotteryResult = await upsertLotteryResult();
  check(lotteryResult.ok, "resultado Loteria falso guardado", { status: lotteryResult.status, message: lotteryResult.text.slice(0, 180) });
  const pickResult = await upsertPickResult();
  check(pickResult.ok, "resultado Pick falso guardado", { status: pickResult.status, message: pickResult.text.slice(0, 180) });
  const winner = await waitForTicketStatus(admin.id, adminSession.token, pickSale.clientRequestId, "GANADOR");
  check(winner.ok, "resultado Pick actualiza ticket vendido a ganador", {
    elapsedMs: winner.elapsedMs,
    status: winner.ticket?.status,
    payout: winner.ticket?.payout_amount,
  });

  const adminReport = await report("get-admin-report", { actorKey: admin.id, adminKey: admin.id }, adminSession.token);
  check(adminReport.ok && Number(adminReport.json?.summary?.tickets ?? 0) >= 4, "monitor admin ve ventas Pick/Loteria", {
    status: adminReport.status,
    summary: adminReport.json?.summary,
  });
  for (const entry of cashierSessions) {
    const cashierReport = await report("get-cashier-report", { actorKey: entry.account.user, adminKey: admin.id, cashierKey: entry.account.user }, entry.session.token);
    check(cashierReport.ok && Number(cashierReport.json?.summary?.tickets ?? 0) >= 1, `monitor cajero ${entry.account.user} ve sus ventas`, {
      status: cashierReport.status,
      summary: cashierReport.json?.summary,
    });
  }
}

try {
  await main();
} catch (error) {
  check(false, "prueba interrumpida", { message: error?.message, stack: error?.stack });
} finally {
  if (originalModePayload !== undefined) {
    const restore = await saveMasterValue(modeKey, originalModePayload);
    log(restore.ok ? "CLEANUP modo sistema restaurado" : "BUG no se pudo restaurar modo sistema", { status: restore.status, message: restore.json?.message });
  }
  await deleteResults("lotterynet_results_by_day").catch(() => null);
  await deleteResults("lotterynet_pick_results_by_day").catch(() => null);
  log("CLEANUP resultados falsos borrados");
  const failed = checks.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    runId,
    fakeIsoDate,
    fakeDayKey,
    checks,
    timing: timingSummary(),
    logFile: decodeURIComponent(logFile.pathname),
  };
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (failed.length) process.exit(1);
}
