import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const logFile = new URL(`./sales-capacity-smoke-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./sales-capacity-smoke-summary-${stamp}.json`, import.meta.url);

const runId = `salescap${Date.now()}`;
const totalSales = Number(process.env.LOTTERYNET_SALES_CAPACITY_COUNT || 40);
const concurrency = Number(process.env.LOTTERYNET_SALES_CAPACITY_CONCURRENCY || 10);
const fakeDay = String(10 + (Date.now() % 15)).padStart(2, "0");
const fakeIsoDate = `2026-02-${fakeDay}`;
const fakeDayKey = `${fakeDay}-02-2026`;

const lines = [];
const timings = [];

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

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

function authHeaders(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

async function requestJson(label, method, url, body, token = API_KEY) {
  const start = performance.now();
  let response;
  let text = "";
  try {
    response = await fetch(url, {
      method,
      headers: authHeaders(token),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - start);
    return { label, ok: false, status: "NETWORK_ERROR", elapsedMs, json: null, text: clean(error?.message) };
  }
  const elapsedMs = Math.round(performance.now() - start);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { label, ok: response.ok, status: response.status, elapsedMs, json, text };
}

async function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
}

function parseCredentials(text) {
  const rows = [...text.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
  const byUser = new Map();
  for (const row of rows) byUser.set(lower(row.username), row);
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
  const role = lower(account.role);
  return role === "cashier" ? "cajero" : role || "cajero";
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
    ok: result.ok && result.json?.ok === true && clean(result.json?.accessToken),
    token: result.json?.accessToken,
    status: result.status,
  };
}

async function runPool(items, limit, worker) {
  const results = [];
  let index = 0;
  async function next() {
    while (index < items.length) {
      const current = index++;
      results[current] = await worker(items[current], current);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, next));
  return results;
}

function play(index) {
  return {
    playType: "Q",
    number: String(10 + (index % 80)).padStart(2, "0"),
    amount: 1,
    potentialPayout: 0,
    lotteryId: "9981",
    lotteryName: "QA Capacity Dia",
  };
}

async function main() {
  log("Inicio sales capacity smoke", { runId, totalSales, concurrency, fakeIsoDate });
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, "podero02");
  const cashiers = [findAccount(payload, "bancae01"), findAccount(payload, "bancae02")].filter(Boolean);
  const cashierCreds = ["bancae01", "bancae02"].map((username) => credentials.find((row) => lower(row.username) === username)).filter(Boolean);
  if (!admin || cashiers.length !== 2 || cashierCreds.length !== 2) throw new Error("Faltan cuentas base.");

  const sessions = await Promise.all(cashierCreds.map((cred) => login(cred.username, cred.password)));
  if (sessions.some((session) => !session.ok)) throw new Error("No se pudo iniciar sesion de cajeros.");

  const jobs = Array.from({ length: totalSales }, (_, index) => index);
  const started = performance.now();
  const results = await runPool(jobs, concurrency, async (index) => {
    const cashierIndex = index % cashiers.length;
    const cashier = cashiers[cashierIndex];
    const session = sessions[cashierIndex];
    const clientRequestId = `${runId}-${String(index + 1).padStart(4, "0")}`;
    const result = await edge("create-ticket-v2", {
      clientRequestId,
      localTicketId: clientRequestId,
      adminKey: admin.id,
      adminId: admin.id,
      actorKey: cashier.user,
      actorId: cashier.id,
      actorRole: roleOf(cashier),
      cashierKey: cashier.user,
      cashierId: cashier.id,
      drawDate: fakeIsoDate,
      dayKey: fakeDayKey,
      lotteryName: "QA Capacity Dia",
      phoneTime: new Date().toISOString(),
      plays: [play(index)],
    }, session.token);
    timings.push(result.elapsedMs);
    return {
      ok: result.ok && result.json?.ok === true,
      status: result.status,
      elapsedMs: result.elapsedMs,
      message: result.json?.message,
      ticketCode: result.json?.ticket?.ticket_code ?? result.json?.ticketCode,
      clientRequestId,
    };
  });
  const elapsedMs = Math.round(performance.now() - started);
  const failed = results.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    runId,
    fakeIsoDate,
    totalSales,
    concurrency,
    elapsedMs,
    throughputPerSecond: Math.round((totalSales / (elapsedMs / 1000)) * 100) / 100,
    p50Ms: percentile(timings, 0.5),
    p95Ms: percentile(timings, 0.95),
    maxMs: timings.length ? Math.max(...timings) : null,
    failed: failed.length,
    sampleFailures: failed.slice(0, 5),
    cleanupClientRequestPrefix: runId,
  };
  log(summary.ok ? "PASS ventas concurrentes" : "BUG ventas concurrentes", summary);
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (!summary.ok) process.exit(1);
}

main().catch(async (error) => {
  log("BUG sales capacity smoke interrumpida", { message: error?.message, stack: error?.stack });
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8").catch(() => null);
  process.exit(1);
});
