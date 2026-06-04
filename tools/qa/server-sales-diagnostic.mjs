import { readFile, writeFile } from "node:fs/promises";
import { monitorEventLoopDelay, performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const REQUEST_TIMEOUT_MS = Number(process.env.LOTTERYNET_DIAGNOSTIC_TIMEOUT_MS || 15000);
const CONCURRENT_DELTA_READS = Number(process.env.LOTTERYNET_DIAGNOSTIC_DELTA_READS || 12);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const runId = `diag${Date.now()}`;
const keepQaTickets = process.env.LOTTERYNET_KEEP_QA_TICKETS === "1";
const fakeDay = String(1 + (Date.now() % 20)).padStart(2, "0");
const fakeIsoDate = `2026-03-${fakeDay}`;
const fakeDayKey = `${fakeDay}-03-2026`;
const logFile = new URL(`./server-sales-diagnostic-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./server-sales-diagnostic-summary-${stamp}.json`, import.meta.url);

const lines = [];
const checks = [];
const metrics = [];
const createdTickets = new Map();
const loopDelay = monitorEventLoopDelay({ resolution: 20 });
loopDelay.enable();

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
    return { status: 0, ok: false, elapsedMs, text: "", json: null, error };
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
    message: redact(json?.message ?? json?.error ?? ""),
  });
  return { status: response.status, ok: response.ok, elapsedMs, text, json };
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

async function fetchUsersPayload() {
  const result = await requestJson(
    "users-state fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${redact(result.text)}`);
  return result.json?.[0]?.payload ?? {};
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return {
    username,
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: result.json?.accessToken,
    user: result.json?.user,
    status: result.status,
    message: result.json?.message,
  };
}

function roleOf(account) {
  const role = lower(account.role);
  if (role === "cashier") return "cajero";
  return role || "cajero";
}

function pickCompatibleLottery(selectedLotteries, number) {
  const requiredType = number.length === 3 ? "Pick3" : number.length === 4 ? "Pick4" : null;
  if (!requiredType) return selectedLotteries[0];
  return selectedLotteries.find((lottery) => lottery.type === requiredType) ?? selectedLotteries[0];
}

function playFromKeyboard({ selectedLottery, selectedLotteries, keys, amountKeys, modeKey }) {
  const selections = selectedLotteries?.length ? selectedLotteries : [selectedLottery];
  const state = {
    selectedLotteryIds: selections.map((lottery) => lottery.id),
    number: "",
    amount: "",
    target: "number",
  };
  for (const key of keys) {
    if (/^\d$/.test(key)) state.number += key;
    if (key === "OK") state.target = "amount";
  }
  for (const key of amountKeys) {
    if (/^\d$/.test(key) || key === ".") state.amount += key;
  }
  const playType = modeKey ?? (state.number.length === 2 ? "Q" : state.number.length === 3 ? "P3" : "P4");
  const resolvedLottery = pickCompatibleLottery(selections, state.number);
  return {
    state,
    play: {
      playType,
      number: state.number,
      amount: Number(state.amount || 1),
      potentialPayout: 0,
      lotteryId: resolvedLottery.id,
      lotteryName: resolvedLottery.name,
      ...(playType === "P3" ? { pickGame: "PICK3", pickMode: "STRAIGHT" } : {}),
      ...(playType === "P3BOX" ? { pickGame: "PICK3", pickMode: "BOX" } : {}),
      ...(playType === "P4" ? { pickGame: "PICK4", pickMode: "STRAIGHT" } : {}),
      ...(playType === "P4BOX" ? { pickGame: "PICK4", pickMode: "BOX" } : {}),
    },
  };
}

function ticketBody({ clientRequestId, actor, admin, plays, lotteryName }) {
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
    lotteryName: lotteryName ?? plays[0]?.lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
}

async function createTicket(session, actor, admin, label, plays, options = {}) {
  const clientRequestId = options.clientRequestId ?? `${runId}-${label}`;
  const body = ticketBody({ clientRequestId, actor, admin, plays, lotteryName: options.lotteryName });
  const result = await edge("create-ticket-v2", body, session.token);
  const ticketId = result.json?.ticket?.id ?? result.json?.ticketId ?? null;
  if ((ticketId || result.json?.ok === true) && !createdTickets.has(clientRequestId)) {
    createdTickets.set(clientRequestId, { clientRequestId, body, result, session, actor, admin });
  }
  return { clientRequestId, body, result };
}

async function getDelta(ownerKey, token, label = "get-ticket-delta") {
  return requestJson(
    label,
    "POST",
    `${SUPABASE_URL}/functions/v1/get-ticket-delta`,
    { ownerKey, cursor: "2000-01-01T00:00:00.000Z", limit: 500 },
    token,
  );
}

async function deleteTicket(ticket) {
  return edge("void-ticket", {
    actorKey: ticket.actor.user,
    adminKey: ticket.admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.clientRequestId,
    action: "delete",
    returnLimit: true,
  }, ticket.session.token);
}

function itemMatchesPlay(item, play) {
  return clean(item.lottery_legacy_id ?? item.lottery_id) === clean(play.lotteryId) &&
    clean(item.lottery_name) === clean(play.lotteryName) &&
    clean(item.play_numbers ?? item.normalized_number) === clean(play.number);
}

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
}

function metricsSummary() {
  const elapsed = metrics.map((item) => item.elapsedMs).filter(Number.isFinite);
  return {
    requests: metrics.length,
    failures: metrics.filter((item) => !item.ok).length,
    maxMs: Math.max(0, ...elapsed),
    p50Ms: percentile(elapsed, 50),
    p95Ms: percentile(elapsed, 95),
    eventLoopDelayMaxMs: Math.round(loopDelay.max / 1e6),
    eventLoopDelayMeanMs: Math.round(loopDelay.mean / 1e6),
  };
}

async function main() {
  log("Inicio diagnostico amplio ventas", {
    runId,
    fakeIsoDate,
    fakeDayKey,
    timeoutMs: REQUEST_TIMEOUT_MS,
    credentialSource: CREDENTIAL_FILE,
    node: process.version,
  });

  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const usersPayload = await fetchUsersPayload();
  const admin = findAccount(usersPayload, "podero02");
  const adminCredential = credentials.find((entry) => lower(entry.username) === "podero02");
  const cashierEntries = credentials
    .filter((entry) => lower(entry.username).startsWith("bancae"))
    .map((entry) => ({ credential: entry, account: findAccount(usersPayload, entry.username) }))
    .filter((entry) => entry.account)
    .slice(0, 4);

  check(Boolean(admin && adminCredential), "admin de prueba disponible", { username: "podero02", id: admin?.id });
  check(cashierEntries.length >= 4, "hay 4 cajeros de prueba", { cashiers: cashierEntries.map((entry) => entry.account.user) });
  if (!admin || !adminCredential || cashierEntries.length < 4) throw new Error("Faltan credenciales de prueba.");

  const adminSession = await login(adminCredential.username, adminCredential.password);
  const cashierSessions = [];
  for (const entry of cashierEntries) {
    cashierSessions.push({ ...entry, session: await login(entry.credential.username, entry.credential.password) });
  }
  check(adminSession.ok, "login admin valido", { status: adminSession.status, user: adminSession.user?.username });
  check(cashierSessions.every((entry) => entry.session.ok), "login cajeros valido", {
    ok: cashierSessions.filter((entry) => entry.session.ok).length,
    total: cashierSessions.length,
  });

  const mixedPickLotteries = [
    { id: "19", name: "NJ Pick 3 Dia", type: "Pick3" },
    { id: "21", name: "NJ Pick 4 Dia", type: "Pick4" },
  ];
  const keyboardScenarios = [
    { label: "normal-q-admin", session: adminSession, actor: admin, lottery: { id: "1", name: "La Primera Día" }, keys: ["6", "5", "OK"], amountKeys: ["1"], modeKey: "Q", expectedLotteryId: "1" },
    { label: "normal-q-cajero", session: cashierSessions[0].session, actor: cashierSessions[0].account, lottery: { id: "1", name: "La Primera Día" }, keys: ["1", "2", "OK"], amountKeys: ["1"], modeKey: "Q", expectedLotteryId: "1" },
    { label: "mixed-p3-straight", session: cashierSessions[0].session, actor: cashierSessions[0].account, selectedLotteries: mixedPickLotteries, keys: ["2", "5", "6", "OK"], amountKeys: ["1"], modeKey: "P3", expectedLotteryId: "19" },
    { label: "mixed-p3-box", session: cashierSessions[1].session, actor: cashierSessions[1].account, selectedLotteries: mixedPickLotteries, keys: ["6", "5", "2", "OK"], amountKeys: ["1"], modeKey: "P3BOX", expectedLotteryId: "19" },
    { label: "mixed-p4-straight", session: cashierSessions[2].session, actor: cashierSessions[2].account, selectedLotteries: mixedPickLotteries, keys: ["5", "4", "2", "3", "OK"], amountKeys: ["1"], modeKey: "P4", expectedLotteryId: "21" },
    { label: "mixed-p4-box", session: cashierSessions[3].session, actor: cashierSessions[3].account, selectedLotteries: mixedPickLotteries, keys: ["3", "2", "4", "5", "OK"], amountKeys: ["1"], modeKey: "P4BOX", expectedLotteryId: "21" },
  ];

  const created = [];
  for (const scenario of keyboardScenarios) {
    const selectedLottery = scenario.lottery ?? scenario.selectedLotteries[0];
    const simulation = playFromKeyboard({
      selectedLottery,
      selectedLotteries: scenario.selectedLotteries,
      keys: scenario.keys,
      amountKeys: scenario.amountKeys,
      modeKey: scenario.modeKey,
    });
    check(simulation.state.target === "amount", "teclado pasa a monto con OK", {
      label: scenario.label,
      number: simulation.state.number,
      selectedLotteryIds: simulation.state.selectedLotteryIds,
    });
    check(simulation.play.lotteryId === scenario.expectedLotteryId, "teclado resuelve loteria correcta", {
      label: scenario.label,
      playType: simulation.play.playType,
      number: simulation.play.number,
      resolvedLotteryId: simulation.play.lotteryId,
      expectedLotteryId: scenario.expectedLotteryId,
    });
    const ticket = await createTicket(scenario.session, scenario.actor, admin, scenario.label, [simulation.play]);
    check(ticket.result.json?.ok === true, "venta creada", {
      label: scenario.label,
      user: scenario.actor.user,
      status: ticket.result.status,
      ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
    });
    created.push({ ...ticket, scenario, plays: [simulation.play] });
  }

  const multiPlay = [
    { playType: "Q", number: "87", amount: 1, potentialPayout: 0, lotteryId: "1", lotteryName: "La Primera Día" },
    { playType: "P", number: "12/34", amount: 1, potentialPayout: 0, lotteryId: "1", lotteryName: "La Primera Día" },
    { playType: "T", number: "12/34/56", amount: 1, potentialPayout: 0, lotteryId: "1", lotteryName: "La Primera Día" },
    { playType: "P3", number: "147", amount: 1, potentialPayout: 0, lotteryId: "19", lotteryName: "NJ Pick 3 Dia", pickGame: "PICK3", pickMode: "STRAIGHT" },
    { playType: "P4BOX", number: "9876", amount: 1, potentialPayout: 0, lotteryId: "21", lotteryName: "NJ Pick 4 Dia", pickGame: "PICK4", pickMode: "BOX" },
  ];
  const multiTicket = await createTicket(adminSession, admin, admin, "multi-play-normal-y-picks", multiPlay);
  check(multiTicket.result.json?.ok === true, "ticket multi-jugada normal y picks creado", {
    status: multiTicket.result.status,
    ticketCode: multiTicket.result.json?.ticket?.ticket_code ?? multiTicket.result.json?.ticketCode,
    plays: multiPlay.length,
  });
  created.push({ ...multiTicket, scenario: { label: "multi-play-normal-y-picks" }, plays: multiPlay });

  const duplicatePlay = { playType: "Q", number: "44", amount: 1, potentialPayout: 0, lotteryId: "1", lotteryName: "La Primera Día" };
  const duplicateId = `${runId}-duplicate-sequential`;
  const duplicateFirst = await createTicket(cashierSessions[0].session, cashierSessions[0].account, admin, "duplicate-sequential-a", [duplicatePlay], { clientRequestId: duplicateId });
  const duplicateSecond = await createTicket(cashierSessions[0].session, cashierSessions[0].account, admin, "duplicate-sequential-b", [duplicatePlay], { clientRequestId: duplicateId });
  check(duplicateFirst.result.json?.ok === true, "primera venta duplicado secuencial creada", { status: duplicateFirst.result.status });
  check(duplicateSecond.result.status !== 0, "segunda venta duplicado secuencial respondio sin bloqueo de red", {
    status: duplicateSecond.result.status,
    ok: duplicateSecond.result.json?.ok,
    message: redact(duplicateSecond.result.json?.message ?? duplicateSecond.result.text),
  });

  const concurrentId = `${runId}-duplicate-concurrent`;
  const concurrentResults = await Promise.all([
    createTicket(cashierSessions[1].session, cashierSessions[1].account, admin, "duplicate-concurrent-a", [duplicatePlay], { clientRequestId: concurrentId }),
    createTicket(cashierSessions[1].session, cashierSessions[1].account, admin, "duplicate-concurrent-b", [duplicatePlay], { clientRequestId: concurrentId }),
  ]);
  check(concurrentResults.every((result) => result.result.status !== 0), "duplicado concurrente recibe respuesta del servidor", {
    statuses: concurrentResults.map((result) => result.result.status),
    oks: concurrentResults.map((result) => result.result.json?.ok),
  });

  const delta = await getDelta(admin.id, adminSession.token);
  const tickets = delta.json?.tickets ?? [];
  const items = delta.json?.items ?? [];
  check(delta.json?.ok === true, "delta principal responde", { status: delta.status, count: delta.json?.count });

  for (const ticket of created) {
    const row = tickets.find((candidate) => candidate.client_request_id === ticket.clientRequestId);
    check(Boolean(row), "ticket aparece en delta", {
      label: ticket.scenario.label,
      clientRequestId: ticket.clientRequestId,
      ticketCode: row?.ticket_code,
    });
    for (const play of ticket.plays) {
      const item = row ? items.find((candidate) => clean(candidate.ticket_id) === clean(row.id) && itemMatchesPlay(candidate, play)) : null;
      check(Boolean(item), "ticket_items conserva jugada", {
        label: ticket.scenario.label,
        expected: {
          lotteryId: play.lotteryId,
          lotteryName: play.lotteryName,
          number: play.number,
          playType: play.playType,
        },
        actual: item ? {
          lotteryId: item.lottery_legacy_id ?? item.lottery_id,
          lotteryName: item.lottery_name,
          number: item.play_numbers ?? item.normalized_number,
          playType: item.play_type,
        } : null,
      });
    }
  }

  for (const clientRequestId of [duplicateId, concurrentId]) {
    const matches = tickets.filter((candidate) => candidate.client_request_id === clientRequestId);
    check(matches.length === 1, "servidor no duplica clientRequestId", {
      clientRequestId,
      rows: matches.length,
      ticketCodes: matches.map((ticket) => ticket.ticket_code),
    });
  }

  const stressStart = performance.now();
  const stress = await Promise.all(
    Array.from({ length: CONCURRENT_DELTA_READS }, (_, index) => getDelta(admin.id, adminSession.token, `get-ticket-delta-stress-${index + 1}`)),
  );
  const stressElapsedMs = Math.round(performance.now() - stressStart);
  check(stress.every((result) => result.json?.ok === true), "stress lectura delta concurrente sin errores", {
    requests: stress.length,
    elapsedMs: stressElapsedMs,
    statuses: stress.map((result) => result.status),
    maxRequestMs: Math.max(...stress.map((result) => result.elapsedMs)),
  });

  const stats = metricsSummary();
  check(stats.failures === 0, "sin fallos HTTP inesperados en diagnostico", stats);
  check(stats.p95Ms < REQUEST_TIMEOUT_MS, "p95 HTTP debajo del timeout configurado", stats);
}

try {
  await main();
} catch (error) {
  check(false, "diagnostico interrumpido", { message: error?.message, stack: error?.stack });
} finally {
  if (keepQaTickets) {
    log("KEEP_QA_TICKETS", {
      tickets: [...createdTickets.values()].map((ticket) => ({
        clientRequestId: ticket.clientRequestId,
        ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
      })),
    });
  } else {
    for (const ticket of createdTickets.values()) {
      const deleted = await deleteTicket(ticket).catch((error) => ({ json: { ok: false, message: error?.message } }));
      log(deleted.json?.ok === true ? "CLEANUP ticket eliminado" : "BUG cleanup ticket no eliminado", {
        clientRequestId: ticket.clientRequestId,
        message: redact(deleted.json?.message ?? deleted.text ?? ""),
      });
    }
  }
  loopDelay.disable();
  const failed = checks.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    runId,
    fakeIsoDate,
    fakeDayKey,
    timeoutMs: REQUEST_TIMEOUT_MS,
    metrics: metricsSummary(),
    checks,
    created: [...createdTickets.values()].map((ticket) => ({
      clientRequestId: ticket.clientRequestId,
      ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
    })),
    logFile: decodeURIComponent(logFile.pathname),
  };
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (failed.length) process.exit(1);
}
