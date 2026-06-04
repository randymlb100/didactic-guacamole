import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const rounds = Number(process.env.LOTTERYNET_STRESS_ROUNDS || 3);
const concurrency = Number(process.env.LOTTERYNET_STRESS_CONCURRENCY || 4);
const requestTimeoutMs = Number(process.env.LOTTERYNET_STRESS_TIMEOUT_MS || 20000);
const runId = `podero02stress${Date.now()}`;
const fakeIsoDate = "2026-03-31";
const fakeDayKey = "31-03-2026";
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryFile = new URL(`./podero02-ticket-stress-no-cleanup-summary-${stamp}.json`, import.meta.url);
const logFile = new URL(`./podero02-ticket-stress-no-cleanup-${stamp}.log`, import.meta.url);

const adminUsername = "podero02";
const preferredCashiers = ["bancae01", "bancae02", "bancae03", "bancae04"];
const lines = [];
const checks = [];
const metrics = [];
const created = [];

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function log(label, data) {
  const line = `[${new Date().toISOString()}] ${label}${data === undefined ? "" : ` ${JSON.stringify(data)}`}`;
  lines.push(line);
  console.log(line);
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
      signal: AbortSignal.timeout(requestTimeoutMs),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - started);
    metrics.push({ label, status: 0, ok: false, elapsedMs, timeout: error?.name === "TimeoutError" });
    log(`HTTP ${label}`, { status: 0, elapsedMs, ok: false, message: clean(error?.message) });
    return { status: 0, ok: false, elapsedMs, json: null, text: "", error };
  }
  const elapsedMs = Math.round(performance.now() - started);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  metrics.push({ label, status: response.status, ok: response.ok, elapsedMs });
  log(`HTTP ${label}`, {
    status: response.status,
    elapsedMs,
    ok: response.ok,
    message: clean(json?.message ?? json?.error).slice(0, 180),
  });
  return { status: response.status, ok: response.ok, elapsedMs, json, text };
}

function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
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

async function login(credential) {
  const result = await edge("auth-legacy-login", { username: credential.username, password: credential.password });
  return {
    username: credential.username,
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: result.json?.accessToken,
    result,
  };
}

function play(playType, number, amount, lotteryId, lotteryName, extra = {}) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName, ...extra };
}

function scenarioPlays(round, cashierIndex, variant) {
  const base = 10 + round * 10 + cashierIndex;
  const q = String((base + variant) % 100).padStart(2, "0");
  const p1 = String((base + 11 + variant) % 100).padStart(2, "0");
  const p2 = String((base + 34 + variant) % 100).padStart(2, "0");
  const p3 = `${(base + variant) % 10}${(base + 3 + variant) % 10}${(base + 6 + variant) % 10}`;
  const p4 = `${(base + variant) % 10}${(base + 2 + variant) % 10}${(base + 4 + variant) % 10}${(base + 8 + variant) % 10}`;
  return [
    play("Q", q, 1, "1", "La Primera Dia"),
    play("P", `${q}/${p1}`, 1, "1", "La Primera Dia"),
    play("T", `${q}/${p1}/${p2}`, 1, "1", "La Primera Dia"),
    play("P3", p3, 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
    play("P3BOX", p3.split("").reverse().join(""), 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "BOX" }),
    play("P4", p4, 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "STRAIGHT" }),
    play("P4BOX", p4.split("").reverse().join(""), 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "BOX" }),
  ];
}

function largeTicketPlays(round, cashierIndex) {
  const lotteries = [
    ["1", "La Primera Dia"],
    ["2", "Loteka"],
    ["3", "Anguila 9PM"],
    ["4", "King PM"],
  ];
  const rows = [];
  for (const [lotteryId, lotteryName] of lotteries) {
    for (let index = 0; index < 8; index += 1) {
      const number = String((round * 17 + cashierIndex * 9 + index * 7) % 100).padStart(2, "0");
      rows.push(play("Q", number, 1, lotteryId, lotteryName));
    }
  }
  return rows;
}

function ticketBody({ clientRequestId, actor, admin, plays }) {
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

async function createTicket(session, actor, admin, label, plays) {
  const clientRequestId = `${runId}-${label}`;
  const body = ticketBody({ clientRequestId, actor, admin, plays });
  const result = await edge("create-ticket-v2", body, session.token);
  const ticket = {
    clientRequestId,
    actor: actor.user,
    label,
    plays,
    result,
    ticketCode: result.json?.ticket?.ticket_code ?? result.json?.ticketCode,
  };
  if (result.json?.ok === true) created.push(ticket);
  return ticket;
}

async function runPool(items, limit, worker) {
  const results = [];
  let index = 0;
  async function next() {
    while (index < items.length) {
      const current = index;
      index += 1;
      results[current] = await worker(items[current], current);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, next));
  return results;
}

function itemMatchesPlay(item, play) {
  const itemNumber = clean(item.play_numbers ?? item.normalized_number ?? item.number);
  const itemLotteryId = clean(item.lottery_legacy_id ?? item.lottery_id ?? item.lotteryId);
  const itemLotteryName = clean(item.lottery_name ?? item.lotteryName);
  return itemNumber === clean(play.number) &&
    (itemLotteryId === clean(play.lotteryId) || lower(itemLotteryName) === lower(play.lotteryName));
}

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

function metricSummary() {
  const elapsed = metrics.map((metric) => metric.elapsedMs).filter(Number.isFinite);
  return {
    requests: metrics.length,
    failures: metrics.filter((metric) => !metric.ok && metric.status !== 400).length,
    maxMs: elapsed.length ? Math.max(...elapsed) : 0,
    p50Ms: percentile(elapsed, 0.5),
    p95Ms: percentile(elapsed, 0.95),
  };
}

async function main() {
  log("Inicio stress podero02 sin cleanup", { runId, fakeDayKey, rounds, concurrency });
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, adminUsername);
  const adminCred = credentials.find((entry) => lower(entry.username) === adminUsername);
  const cashiers = preferredCashiers
    .map((username) => ({ account: findAccount(payload, username), credential: credentials.find((entry) => lower(entry.username) === username) }))
    .filter((entry) => entry.account && entry.credential);

  check(Boolean(admin && adminCred), "admin podero02 disponible", { admin: admin?.id });
  check(cashiers.length === preferredCashiers.length, "cajeros podero02 QA disponibles", {
    found: cashiers.map((entry) => entry.account.user),
    expected: preferredCashiers,
  });
  if (!admin || !adminCred || cashiers.length !== preferredCashiers.length) throw new Error("Faltan credenciales podero02/cajeros.");

  const adminSession = await login(adminCred);
  const cashierSessions = await runPool(cashiers, concurrency, async (entry) => ({
    account: entry.account,
    session: await login(entry.credential),
  }));
  check(adminSession.ok, "login podero02 valido", { status: adminSession.result.status });
  check(cashierSessions.every((entry) => entry.session.ok), "login cajeros valido", {
    users: cashierSessions.map((entry) => entry.account.user),
    statuses: cashierSessions.map((entry) => entry.session.result.status),
  });

  const jobs = [];
  for (let round = 1; round <= rounds; round += 1) {
    cashierSessions.forEach((entry, cashierIndex) => {
      jobs.push({ ...entry, label: `r${round}-${entry.account.user}-mix-a`, plays: scenarioPlays(round, cashierIndex, 1) });
      jobs.push({ ...entry, label: `r${round}-${entry.account.user}-mix-b`, plays: scenarioPlays(round, cashierIndex, 4) });
      jobs.push({ ...entry, label: `r${round}-${entry.account.user}-large`, plays: largeTicketPlays(round, cashierIndex) });
    });
  }

  const createdResults = await runPool(jobs, concurrency, (job) =>
    createTicket(job.session, job.account, admin, job.label, job.plays)
  );
  const failedCreates = createdResults.filter((ticket) => ticket.result.json?.ok !== true);
  check(failedCreates.length === 0, "todas las ventas stress fueron creadas", {
    attempted: createdResults.length,
    created: created.length,
    failed: failedCreates.length,
    sampleFailures: failedCreates.slice(0, 5).map((ticket) => ({
      label: ticket.label,
      actor: ticket.actor,
      status: ticket.result.status,
      message: ticket.result.json?.message,
    })),
  });

  const duplicateTarget = createdResults.find((ticket) => ticket.result.json?.ok === true);
  if (duplicateTarget) {
    const duplicateBody = ticketBody({
      clientRequestId: duplicateTarget.clientRequestId,
      actor: cashierSessions[0].account,
      admin,
      plays: duplicateTarget.plays,
    });
    const duplicate = await edge("create-ticket-v2", duplicateBody, cashierSessions[0].session.token);
    check(duplicate.status === 200 && duplicate.json?.ok === true, "duplicado stress responde controlado", {
      status: duplicate.status,
      message: duplicate.json?.message,
      ticketCode: duplicate.json?.ticket?.ticket_code ?? duplicate.json?.ticketCode,
    });
  }

  const delta = await edge("get-ticket-delta", { ownerKey: admin.id, cursor: "2000-01-01T00:00:00.000Z", limit: 500 }, adminSession.token);
  const tickets = delta.json?.tickets ?? [];
  const items = delta.json?.items ?? [];
  check(delta.json?.ok === true, "delta responde despues del stress", { status: delta.status, count: delta.json?.count });

  for (const ticket of created) {
    const row = tickets.find((candidate) => candidate.client_request_id === ticket.clientRequestId);
    const ticketItems = row ? items.filter((item) => clean(item.ticket_id) === clean(row.id)) : [];
    check(Boolean(row), "ticket stress aparece en delta", {
      label: ticket.label,
      actor: ticket.actor,
      ticketCode: ticket.ticketCode,
    });
    check(ticketItems.length === ticket.plays.length, "ticket stress conserva cantidad de jugadas", {
      label: ticket.label,
      expected: ticket.plays.length,
      actual: ticketItems.length,
      ticketCode: ticket.ticketCode,
    });
    const missing = ticket.plays.filter((play) => !ticketItems.some((item) => itemMatchesPlay(item, play)));
    check(missing.length === 0, "ticket stress conserva numero y loteria de cada jugada", {
      label: ticket.label,
      missing: missing.slice(0, 3),
      ticketCode: ticket.ticketCode,
    });
  }

  const readStress = await runPool(
    Array.from({ length: 12 }, (_, index) => index),
    6,
    (index) => edge("get-ticket-delta", { ownerKey: admin.id, cursor: "2000-01-01T00:00:00.000Z", limit: 500 }, adminSession.token)
      .then((result) => ({ index, result })),
  );
  check(readStress.every((entry) => entry.result.json?.ok === true), "lectura delta repetida sin fallos despues de crear muchos tickets", {
    attempts: readStress.length,
    statuses: readStress.map((entry) => entry.result.status),
    maxMs: Math.max(...readStress.map((entry) => entry.result.elapsedMs)),
  });

  const summary = {
    ok: checks.every((item) => item.ok),
    runId,
    fakeIsoDate,
    fakeDayKey,
    rounds,
    createdCount: created.length,
    created: created.map((ticket) => ({
      label: ticket.label,
      actor: ticket.actor,
      clientRequestId: ticket.clientRequestId,
      ticketCode: ticket.ticketCode,
      plays: ticket.plays.length,
    })),
    metrics: metricSummary(),
    checks,
    logFile: decodeURIComponent(logFile.pathname),
  };
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (!summary.ok) process.exit(1);
}

main().catch(async (error) => {
  check(false, "stress podero02 interrumpido", { message: error?.message, stack: error?.stack });
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8").catch(() => null);
  await writeFile(summaryFile, JSON.stringify({
    ok: false,
    runId,
    fakeIsoDate,
    fakeDayKey,
    created,
    checks,
  }, null, 2), "utf8").catch(() => null);
  process.exit(1);
});
