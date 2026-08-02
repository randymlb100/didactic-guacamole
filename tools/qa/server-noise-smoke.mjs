import { readFile, writeFile } from "node:fs/promises";
import { monitorEventLoopDelay, performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const REQUEST_TIMEOUT_MS = Number(process.env.LOTTERYNET_NOISE_SMOKE_TIMEOUT_MS || 15000);
const CONCURRENT_DELTA_READS = Number(process.env.LOTTERYNET_NOISE_SMOKE_DELTA_READS || 8);
const LOOP_COUNT = Number(process.env.LOTTERYNET_NOISE_SMOKE_LOOPS || 3);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const runId = `noise${Date.now()}`;
const fakeDay = String(1 + (Date.now() % 20)).padStart(2, "0");
const fakeIsoDate = `2026-03-${fakeDay}`;
const fakeDayKey = `${fakeDay}-03-2026`;
const logFile = new URL(`./server-noise-smoke-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./server-noise-smoke-summary-${stamp}.json`, import.meta.url);

const lines = [];
const checks = [];
const metrics = [];
const createdTickets = [];
const loopDelay = monitorEventLoopDelay({ resolution: 20 });
loopDelay.enable();
let adminSession = null;
let cashierSessionA = null;
let cashierSessionB = null;
let admin = null;
let cashierA = null;
let cashierB = null;

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

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function redact(value) {
  return String(value ?? "")
    .replace(/"(accessToken|refreshToken|access_token|refresh_token)"\s*:\s*"[^"]+"/gi, "\"$1\":\"[redacted]\"")
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[redacted-jwt]")
    .slice(0, 260);
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
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - started);
    metrics.push({ label, status: 0, ok: false, elapsedMs, timeout: error?.name === "TimeoutError" });
    log(`HTTP ${label}`, { status: 0, elapsedMs, ok: false, message: redact(error?.message) });
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
    message: redact(json?.message ?? json?.error ?? "").slice(0, 180),
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

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

function metricsSummary() {
  const elapsed = metrics.map((item) => item.elapsedMs).filter(Number.isFinite);
  return {
    requests: metrics.length,
    failures: metrics.filter((item) => !item.ok).length,
    maxMs: Math.max(0, ...elapsed),
    p50Ms: percentile(elapsed, 0.5),
    p95Ms: percentile(elapsed, 0.95),
    eventLoopDelayMaxMs: Math.round(loopDelay.max / 1e6),
    eventLoopDelayMeanMs: Math.round(loopDelay.mean / 1e6),
  };
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return {
    username,
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: result.json?.accessToken,
    user: result.json?.user,
    status: result.status,
    refreshToken: result.json?.refreshToken || result.json?.session?.refresh_token || null,
    message: result.json?.message,
  };
}

async function refreshSession(refreshToken) {
  const started = performance.now();
  const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: {
      apikey: API_KEY,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ refresh_token: refreshToken }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  const elapsedMs = Math.round(performance.now() - started);
  const json = await response.json().catch(() => null);
  metrics.push({ label: "auth refresh token", status: response.status, ok: response.ok, elapsedMs });
  log("HTTP auth refresh token", {
    status: response.status,
    elapsedMs,
    ok: response.ok,
    message: redact(json?.message ?? json?.error_description ?? json?.error ?? "").slice(0, 180),
  });
  return {
    status: response.status,
    ok: response.ok && Boolean(json?.access_token),
    accessToken: json?.access_token,
    refreshToken: json?.refresh_token,
  };
}

async function fetchUsersPayload(token = API_KEY) {
  const edgeResult = await edge("lotterynet-users-state", { action: "fetch" }, token);
  if (edgeResult.ok && edgeResult.json?.payload) return edgeResult.json.payload;

  const result = await requestJson(
    "users-state fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
    undefined,
    token,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function getSummary(ownerKey, token) {
  return edge("get-ticket-summary", { ownerKey, dayKey: fakeDayKey }, token);
}

async function getDelta(ownerKey, token, limit = 300) {
  return edge("get-ticket-delta", { ownerKey, limit }, token);
}

async function getReport(role, actorKey, adminKey, token, extra = {}) {
  if (role === "cashier") {
    return edge("get-cashier-report", {
      actorKey,
      adminKey,
      cashierKey: actorKey,
      from: fakeIsoDate,
      to: fakeIsoDate,
      ...extra,
    }, token);
  }
  return edge("get-admin-report", {
    actorKey,
    adminKey,
    from: fakeIsoDate,
    to: fakeIsoDate,
    ...extra,
  }, token);
}

async function createTicket(session, actor, admin, amount, label, clientRequestId) {
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
    lotteryName: "La Primera Día",
    phoneTime: new Date().toISOString(),
    plays: [
      {
        playType: "Q",
        number: String(amount).padStart(2, "0"),
        amount: 1,
        potentialPayout: 0,
        lotteryId: "1",
        lotteryName: "La Primera Día",
      },
    ],
  };
  const result = await edge("create-ticket-v2", body, session.token);
  const ticket = {
    label,
    clientRequestId,
    body,
    result,
    session,
    actor,
    admin,
  };
  if (result.json?.ok === true) createdTickets.push(ticket);
  return ticket;
}

async function deleteTicket(ticket, session, actor, admin) {
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
  log("Inicio smoke de ruido inteligente", {
    runId,
    fakeIsoDate,
    fakeDayKey,
    timeoutMs: REQUEST_TIMEOUT_MS,
    credentialSource: CREDENTIAL_FILE,
  });

  const credentialsText = await readFile(CREDENTIAL_FILE, "utf8");
  const credentials = parseCredentials(credentialsText);
  const usersPayload = await fetchUsersPayload();
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  const cashierCreds = credentials.filter((entry) => lower(entry.username).startsWith("bancae")).slice(0, 2);

  check(Boolean(adminCred), "credencial admin disponible", { username: "podero02", found: Boolean(adminCred) });
  check(cashierCreds.length === 2, "hay 2 credenciales de cajero", { cashiers: cashierCreds.map((entry) => entry.username) });
  if (!adminCred || cashierCreds.length !== 2) throw new Error("Faltan credenciales de prueba.");

  admin = findAccount(usersPayload, "podero02");
  cashierA = findAccount(usersPayload, cashierCreds[0].username);
  cashierB = findAccount(usersPayload, cashierCreds[1].username);
  check(Boolean(admin && cashierA && cashierB), "admin y cajeros resueltos en servidor", {
    admin: admin?.id,
    cashierA: cashierA?.id,
    cashierB: cashierB?.id,
  });
  if (!admin || !cashierA || !cashierB) throw new Error("No se pudieron resolver cuentas de prueba.");

  adminSession = await login(adminCred.username, adminCred.password);
  cashierSessionA = await login(cashierCreds[0].username, cashierCreds[0].password);
  cashierSessionB = await login(cashierCreds[1].username, cashierCreds[1].password);
  check(adminSession.ok, "login admin valido", { status: adminSession.status, user: adminSession.user?.username });
  check(cashierSessionA.ok && cashierSessionB.ok, "login cajeros valido", {
    cashierA: cashierSessionA.status,
    cashierB: cashierSessionB.status,
  });

  if (adminSession.refreshToken) {
    const refreshed = await refreshSession(adminSession.refreshToken);
    check(refreshed.ok, "refresh token admin saludable", {
      status: refreshed.status,
      hasAccessToken: Boolean(refreshed.accessToken),
    });
  }

  const ticketA = await createTicket(adminSession, admin, admin, 11, "admin-q-1", `${runId}-admin-q-1`);
  const ticketB = await createTicket(cashierSessionA, cashierA, admin, 12, "cashier-q-1", `${runId}-cashier-q-1`);
  check(ticketA.result.json?.ok === true, "venta admin Q valida", { status: ticketA.result.status, message: ticketA.result.json?.message });
  check(ticketB.result.json?.ok === true, "venta cajero Q valida", { status: ticketB.result.status, message: ticketB.result.json?.message });

  const duplicateId = `${runId}-duplicate-q`;
  const dup1 = await createTicket(cashierSessionB, cashierB, admin, 13, "dup-a", duplicateId);
  const dup2 = await createTicket(cashierSessionB, cashierB, admin, 13, "dup-b", duplicateId);
  check(dup1.result.json?.ok === true, "primera venta duplicada valida", { status: dup1.result.status });
  check(dup2.result.ok && dup2.result.json?.message, "segunda venta duplicada responde sin romper flujo", {
    status: dup2.result.status,
    message: dup2.result.json?.message,
  });

  const summaryLoops = [];
  const deltaLoops = [];
  const reportLoops = [];
  for (let index = 0; index < LOOP_COUNT; index += 1) {
    summaryLoops.push(await getSummary(admin.id, adminSession.token));
    deltaLoops.push(await getDelta(admin.id, adminSession.token));
    reportLoops.push(await getReport("admin", admin.id, admin.id, adminSession.token));
    reportLoops.push(await getReport("cashier", cashierA.user, admin.id, cashierSessionA.token));
  }

  const summaryOk = summaryLoops.every((result) => result.ok);
  const deltaOk = deltaLoops.every((result) => result.ok);
  const reportsOk = reportLoops.every((result) => result.ok);
  check(summaryOk, "get-ticket-summary responde estable", {
    statuses: summaryLoops.map((result) => result.status),
  });
  check(deltaOk, "get-ticket-delta responde estable", {
    statuses: deltaLoops.map((result) => result.status),
  });
  check(reportsOk, "reportes admin/cajero responden estable", {
    statuses: reportLoops.map((result) => result.status),
  });

  const deltaTickets = deltaLoops.at(-1)?.json?.tickets ?? [];
  const deltaItems = deltaLoops.at(-1)?.json?.items ?? [];
  check(deltaTickets.some((ticket) => ticket.client_request_id === ticketA.clientRequestId), "ticket admin aparece en delta", {
    clientRequestId: ticketA.clientRequestId,
  });
  check(deltaTickets.some((ticket) => ticket.client_request_id === ticketB.clientRequestId), "ticket cajero aparece en delta", {
    clientRequestId: ticketB.clientRequestId,
  });
  check(deltaTickets.filter((ticket) => ticket.client_request_id === duplicateId).length === 1, "clientRequestId duplicado no se multiplica", {
    clientRequestId: duplicateId,
    rows: deltaTickets.filter((ticket) => ticket.client_request_id === duplicateId).length,
  });
  check(deltaItems.length >= 2, "ticket_items responde con datos mínimos", {
    itemCount: deltaItems.length,
  });

  const concurrentDelta = await Promise.all(
    Array.from({ length: CONCURRENT_DELTA_READS }, (_, index) =>
      getDelta(admin.id, adminSession.token, 150).then((result) => ({ index, result })),
    ),
  );
  check(concurrentDelta.every((entry) => entry.result.ok), "lectura delta concurrente sin errores", {
    statuses: concurrentDelta.map((entry) => entry.result.status),
    maxMs: Math.max(...concurrentDelta.map((entry) => entry.result.elapsedMs)),
  });

  const stats = metricsSummary();
  check(stats.failures === 0, "sin fallos HTTP inesperados", stats);
  check(stats.p95Ms < REQUEST_TIMEOUT_MS, "p95 debajo del timeout configurado", stats);

  const summary = {
    ok: checks.every((item) => item.ok),
    runId,
    fakeIsoDate,
    fakeDayKey,
    timeoutMs: REQUEST_TIMEOUT_MS,
    loops: LOOP_COUNT,
    metrics: stats,
    checks,
    created: createdTickets.map((ticket) => ({
      label: ticket.label,
      clientRequestId: ticket.clientRequestId,
      status: ticket.result.status,
      message: ticket.result.json?.message,
    })),
    logFile: decodeURIComponent(logFile.pathname),
  };

  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (!summary.ok) process.exit(1);
}

try {
  await main();
} catch (error) {
  check(false, "smoke interrumpido", { message: error?.message, stack: error?.stack });
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8").catch(() => null);
  await writeFile(summaryFile, JSON.stringify({
    ok: false,
    runId,
    fakeIsoDate,
    fakeDayKey,
    checks,
    created: createdTickets.map((ticket) => ({ label: ticket.label, clientRequestId: ticket.clientRequestId })),
  }, null, 2), "utf8").catch(() => null);
  process.exit(1);
} finally {
  for (const ticket of createdTickets) {
    try {
      const deleted = await deleteTicket(ticket, ticket.session ?? adminSession, ticket.actor ?? cashierA, ticket.admin ?? admin);
      log(deleted.ok && deleted.json?.ok !== false ? "CLEANUP ticket eliminado" : "BUG cleanup ticket no eliminado", {
        clientRequestId: ticket.clientRequestId,
        status: deleted.status,
        message: redact(deleted.json?.message ?? deleted.text ?? ""),
      });
    } catch (error) {
      log("BUG cleanup ticket no eliminado", {
        clientRequestId: ticket.clientRequestId,
        message: error?.message,
      });
    }
  }
  loopDelay.disable();
}
