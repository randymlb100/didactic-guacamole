import { readFile, writeFile } from "node:fs/promises";
import { memoryUsage } from "node:process";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const logFile = new URL(`./production-stability-suite-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./production-stability-suite-summary-${stamp}.json`, import.meta.url);

const reportBursts = Number(process.env.LOTTERYNET_STABILITY_BURSTS || 5);
const callsPerBurst = Number(process.env.LOTTERYNET_STABILITY_CALLS || 24);
const concurrency = Number(process.env.LOTTERYNET_STABILITY_CONCURRENCY || 8);
const authLoops = Number(process.env.LOTTERYNET_STABILITY_AUTH_LOOPS || 4);
const allowWriteProbe = process.env.LOTTERYNET_STABILITY_WRITE === "1";
const fakeIsoDate = "2026-04-19";
const fakeDayKey = "19-04-2026";
const runId = `stable${Date.now()}`;

const lines = [];
const checks = [];
const timings = {};
const phaseResults = [];

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

function mb(bytes) {
  return Math.round((bytes / 1024 / 1024) * 100) / 100;
}

function sampleMemory(label) {
  if (typeof global.gc === "function") {
    global.gc();
  }
  const usage = memoryUsage();
  const sample = {
    label,
    rssMb: mb(usage.rss),
    heapTotalMb: mb(usage.heapTotal),
    heapUsedMb: mb(usage.heapUsed),
    externalMb: mb(usage.external),
    arrayBuffersMb: mb(usage.arrayBuffers ?? 0),
  };
  log("MEMORY", sample);
  return sample;
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

function headers(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

async function requestJson(label, method, url, body, token = API_KEY) {
  const started = performance.now();
  let response;
  let text = "";
  try {
    response = await fetch(url, {
      method,
      headers: headers(token),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - started);
    recordTiming(label, elapsedMs);
    return { label, status: "NETWORK_ERROR", ok: false, elapsedMs, json: null, text: clean(error?.message) };
  }
  const elapsedMs = Math.round(performance.now() - started);
  recordTiming(label, elapsedMs);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { label, status: response.status, ok: response.ok, elapsedMs, json, text };
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

function roleOf(account) {
  return lower(account.role) === "cashier" ? "cajero" : lower(account.role);
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
    result,
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

function play(playType, number, amount, lotteryId, lotteryName) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName };
}

async function createSmallWriteProbe(session, cashier, admin) {
  const clientRequestId = `${runId}-write-probe`;
  return edge("create-ticket-v2", {
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
    lotteryName: "QA Stability Dia",
    phoneTime: new Date().toISOString(),
    plays: [play("Q", "41", 1, "9971", "QA Stability Dia")],
  }, session.token);
}

async function main() {
  log("Inicio production stability suite", {
    runId,
    reportBursts,
    callsPerBurst,
    concurrency,
    authLoops,
    allowWriteProbe,
    node: process.version,
    exposeGc: typeof global.gc === "function",
  });
  const memoryStart = sampleMemory("start");
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, "podero02");
  const cashier1 = findAccount(payload, "bancae01");
  const cashier2 = findAccount(payload, "bancae02");
  const adminCred = credentials.find((row) => lower(row.username) === "podero02");
  const cashierCred1 = credentials.find((row) => lower(row.username) === "bancae01");
  const cashierCred2 = credentials.find((row) => lower(row.username) === "bancae02");
  check(Boolean(admin && cashier1 && cashier2 && adminCred && cashierCred1 && cashierCred2), "cuentas base disponibles", {
    admin: Boolean(admin),
    cashier1: Boolean(cashier1),
    cashier2: Boolean(cashier2),
  });
  if (!admin || !cashier1 || !cashier2 || !adminCred || !cashierCred1 || !cashierCred2) {
    throw new Error("Faltan credenciales/cuentas base.");
  }

  const authJobs = [];
  for (let i = 0; i < authLoops; i++) {
    authJobs.push(["admin", adminCred], ["cashier1", cashierCred1], ["cashier2", cashierCred2]);
  }
  const authStart = performance.now();
  const authResults = await runPool(authJobs, concurrency, async ([label, cred]) => {
    const session = await login(cred.username, cred.password);
    return { label, status: session.result.status, elapsedMs: session.result.elapsedMs, ok: Boolean(session.ok), token: session.token, user: session.user };
  });
  const authElapsedMs = Math.round(performance.now() - authStart);
  const authFailures = authResults.filter((item) => !item.ok);
  check(authFailures.length === 0, "login repetido admin/cajeros sin fallos", {
    attempted: authResults.length,
    failed: authFailures.length,
    elapsedMs: authElapsedMs,
    sampleFailures: authFailures.slice(0, 3),
  });
  const adminSession = await login(adminCred.username, adminCred.password);
  const cashierSession1 = await login(cashierCred1.username, cashierCred1.password);
  const cashierSession2 = await login(cashierCred2.username, cashierCred2.password);
  check(Boolean(adminSession.ok && cashierSession1.ok && cashierSession2.ok), "tokens frescos para reportes validos", {
    adminStatus: adminSession.result.status,
    cashier1Status: cashierSession1.result.status,
    cashier2Status: cashierSession2.result.status,
  });

  const anonymousSecurity = await runPool(Array.from({ length: 6 }), 3, () =>
    edge("lotterynet-users-state", { action: "upsert", payload })
  );
  check(anonymousSecurity.every((item) => [401, 403].includes(item.status)), "users-state mantiene bloqueo anonimo bajo repeticion", {
    statuses: anonymousSecurity.map((item) => item.status),
  });

  const reportBodies = [
    ["get-admin-report", { actorKey: admin.id, adminKey: admin.id, from: fakeIsoDate, to: fakeIsoDate }, adminSession.token],
    ["get-cashier-report", { actorKey: cashier1.user, adminKey: admin.id, cashierKey: cashier1.user, from: fakeIsoDate, to: fakeIsoDate }, cashierSession1.token],
    ["get-cashier-report", { actorKey: cashier2.user, adminKey: admin.id, cashierKey: cashier2.user, from: fakeIsoDate, to: fakeIsoDate }, cashierSession2.token],
    ["get-ticket-summary", { ownerKey: admin.id, from: fakeIsoDate, to: fakeIsoDate }, adminSession.token],
    ["get-ticket-delta", { ownerKey: admin.id, limit: 80 }, adminSession.token],
  ];

  const memorySamples = [sampleMemory("before-read-bursts")];
  for (let burst = 1; burst <= reportBursts; burst++) {
    const jobs = Array.from({ length: callsPerBurst }, (_, index) => reportBodies[index % reportBodies.length]);
    const started = performance.now();
    const results = await runPool(jobs, concurrency, ([slug, body, token]) => edge(slug, body, token));
    const elapsedMs = Math.round(performance.now() - started);
    const failures = results.filter((item) => !item.ok || item.json?.ok === false);
    const p95 = percentile(results.map((item) => item.elapsedMs), 0.95);
    phaseResults.push({ burst, attempted: results.length, failed: failures.length, elapsedMs, p95Ms: p95 });
    check(failures.length === 0, `rafaga lectura/reportes ${burst} sin fallos`, {
      attempted: results.length,
      failed: failures.length,
      elapsedMs,
      p95Ms: p95,
      sampleFailures: failures.slice(0, 3).map((item) => ({ label: item.label, status: item.status, message: item.json?.message })),
    });
    check(p95 <= 4500, `rafaga lectura/reportes ${burst} p95 razonable`, { p95Ms: p95, thresholdMs: 4500 });
    memorySamples.push(sampleMemory(`after-burst-${burst}`));
  }

  if (allowWriteProbe) {
    const writeProbe = await createSmallWriteProbe({ token: cashierSession1.token }, cashier1, admin);
    check(writeProbe.json?.ok === true, "write probe pequeno crea un ticket controlado", {
      status: writeProbe.status,
      message: writeProbe.json?.message,
      ticketCode: writeProbe.json?.ticketCode,
    });
  } else {
    log("SKIP write probe", { reason: "LOTTERYNET_STABILITY_WRITE no esta en 1" });
  }

  const memoryEnd = sampleMemory("end");
  const heapGrowthMb = Math.round((memoryEnd.heapUsedMb - memoryStart.heapUsedMb) * 100) / 100;
  const rssGrowthMb = Math.round((memoryEnd.rssMb - memoryStart.rssMb) * 100) / 100;
  check(heapGrowthMb <= 24, "heapUsed estable despues de rafagas Node", { startMb: memoryStart.heapUsedMb, endMb: memoryEnd.heapUsedMb, growthMb: heapGrowthMb, thresholdMb: 24 });
  check(rssGrowthMb <= 96, "RSS sin crecimiento extremo despues de rafagas Node", { startMb: memoryStart.rssMb, endMb: memoryEnd.rssMb, growthMb: rssGrowthMb, thresholdMb: 96 });

  const failed = checks.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    runId,
    node: process.version,
    exposeGc: typeof global.gc === "function",
    config: { reportBursts, callsPerBurst, concurrency, authLoops, allowWriteProbe },
    checks,
    phaseResults,
    memorySamples,
    timings: timingSummary(),
    logFile: decodeURIComponent(logFile.pathname),
  };
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (failed.length) process.exit(1);
}

main().catch(async (error) => {
  log("BUG production stability suite interrumpida", { message: error?.message, stack: error?.stack });
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8").catch(() => null);
  process.exit(1);
});
