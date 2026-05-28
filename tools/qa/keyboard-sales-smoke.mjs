import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const logFile = new URL(`./keyboard-sales-smoke-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./keyboard-sales-smoke-summary-${stamp}.json`, import.meta.url);

const runId = `keyboard${Date.now()}`;
const fakeDay = String(1 + (Date.now() % 20)).padStart(2, "0");
const fakeIsoDate = `2026-03-${fakeDay}`;
const fakeDayKey = `${fakeDay}-03-2026`;
const adminUsername = "podero02";
const cashierPrefix = "bancae";

const lines = [];
const checks = [];
const createdTickets = [];

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
    .slice(0, 240);
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

async function requestJson(label, method, url, body, token = API_KEY, extraHeaders = {}) {
  const started = performance.now();
  const response = await fetch(url, {
    method,
    headers: headers(token, extraHeaders),
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
    selectedLotteryNames: selections.map((lottery) => lottery.name),
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

async function createTicket(session, actor, admin, label, play) {
  const clientRequestId = `${runId}-${label}`;
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
    lotteryName: play.lotteryName,
    phoneTime: new Date().toISOString(),
    plays: [play],
  };
  const result = await edge("create-ticket-v2", body, session.token);
  const ticketId = result.json?.ticket?.id ?? result.json?.ticketId ?? null;
  if (ticketId || result.json?.ok === true) createdTickets.push({ clientRequestId, body, result, session, actor, admin });
  return { clientRequestId, body, result };
}

async function getDelta(ownerKey, token) {
  return edge("get-ticket-delta", { ownerKey, cursor: "2000-01-01T00:00:00.000Z", limit: 300 }, token);
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

async function main() {
  log("Inicio keyboard sales smoke", { runId, fakeIsoDate, fakeDayKey, credentialSource: String(CREDENTIAL_FILE) });

  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const usersPayload = await fetchUsersPayload();
  const admin = findAccount(usersPayload, adminUsername);
  const cashierEntries = credentials
    .filter((entry) => lower(entry.username).startsWith(cashierPrefix))
    .map((entry) => ({ credential: entry, account: findAccount(usersPayload, entry.username) }))
    .filter((entry) => entry.account)
    .slice(0, 4);
  const adminCredential = credentials.find((entry) => lower(entry.username) === adminUsername);

  check(Boolean(admin && adminCredential), "admin de prueba disponible", { username: adminUsername, id: admin?.id });
  check(cashierEntries.length >= 4, "hay 4 cajeros para alternar ventas", {
    cashiers: cashierEntries.map((entry) => entry.account.user),
  });
  if (!admin || !adminCredential || cashierEntries.length < 4) throw new Error("Faltan cuentas de prueba para el smoke.");

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
  const scenarios = [
    { label: "admin-q-la-primera", session: adminSession, actor: admin, lottery: { id: "1", name: "La Primera Día" }, keys: ["6", "5", "OK"], amountKeys: ["1"], modeKey: "Q" },
    { label: "cashier1-mixed-p3-straight", session: cashierSessions[0].session, actor: cashierSessions[0].account, selectedLotteries: mixedPickLotteries, keys: ["2", "5", "6", "OK"], amountKeys: ["1"], modeKey: "P3", expectedLotteryId: "19" },
    { label: "cashier2-mixed-p3-box", session: cashierSessions[1].session, actor: cashierSessions[1].account, selectedLotteries: mixedPickLotteries, keys: ["6", "5", "2", "OK"], amountKeys: ["1"], modeKey: "P3BOX", expectedLotteryId: "19" },
    { label: "cashier3-mixed-p4-straight", session: cashierSessions[2].session, actor: cashierSessions[2].account, selectedLotteries: mixedPickLotteries, keys: ["5", "4", "2", "3", "OK"], amountKeys: ["1"], modeKey: "P4", expectedLotteryId: "21" },
    { label: "cashier4-mixed-p4-box", session: cashierSessions[3].session, actor: cashierSessions[3].account, selectedLotteries: mixedPickLotteries, keys: ["3", "2", "4", "5", "OK"], amountKeys: ["1"], modeKey: "P4BOX", expectedLotteryId: "21" },
  ];

  const created = [];
  for (const scenario of scenarios) {
    const selectedLottery = scenario.lottery ?? scenario.selectedLotteries[0];
    const simulation = playFromKeyboard({
      selectedLottery,
      selectedLotteries: scenario.selectedLotteries,
      keys: scenario.keys,
      amountKeys: scenario.amountKeys,
      modeKey: scenario.modeKey,
    });
    check(
      JSON.stringify(simulation.state.selectedLotteryIds) === JSON.stringify((scenario.selectedLotteries ?? [selectedLottery]).map((lottery) => lottery.id)),
      "teclado conserva loterias seleccionadas antes de vender",
      {
      label: scenario.label,
      selectedLotteryIds: simulation.state.selectedLotteryIds,
      expectedLotteryIds: (scenario.selectedLotteries ?? [selectedLottery]).map((lottery) => lottery.id),
      number: simulation.state.number,
      },
    );
    check(simulation.play.lotteryId === (scenario.expectedLotteryId ?? selectedLottery.id), "teclado resuelve loteria compatible por cantidad de digitos", {
      label: scenario.label,
      selectedLotteryIds: simulation.state.selectedLotteryIds,
      number: simulation.state.number,
      playType: simulation.play.playType,
      resolvedLotteryId: simulation.play.lotteryId,
      expectedLotteryId: scenario.expectedLotteryId ?? selectedLottery.id,
    });
    const ticket = await createTicket(scenario.session, scenario.actor, admin, scenario.label, simulation.play);
    check(ticket.result.json?.ok === true, "venta creada por teclado", {
      label: scenario.label,
      user: scenario.actor.user,
      status: ticket.result.status,
      ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
      expectedLotteryId: simulation.play.lotteryId,
      expectedLotteryName: simulation.play.lotteryName,
    });
    created.push({ ...ticket, scenario, play: simulation.play });
  }

  const delta = await getDelta(admin.id, adminSession.token);
  const tickets = delta.json?.tickets ?? [];
  const items = delta.json?.items ?? [];
  check(delta.json?.ok === true, "delta devuelve tickets creados", { status: delta.status, count: delta.json?.count });

  for (const ticket of created) {
    const row = tickets.find((candidate) => candidate.client_request_id === ticket.clientRequestId);
    const item = row ? items.find((candidate) => clean(candidate.ticket_id) === clean(row.id) && itemMatchesPlay(candidate, ticket.play)) : null;
    check(Boolean(row), "ticket aparece en delta", {
      label: ticket.scenario.label,
      clientRequestId: ticket.clientRequestId,
      ticketCode: row?.ticket_code,
    });
    check(Boolean(item), "ticket_items conserva loteria y numero del teclado", {
      label: ticket.scenario.label,
      expected: {
        lotteryId: ticket.play.lotteryId,
        lotteryName: ticket.play.lotteryName,
        number: ticket.play.number,
        playType: ticket.play.playType,
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

try {
  await main();
} catch (error) {
  check(false, "keyboard sales smoke interrumpido", { message: error?.message, stack: error?.stack });
} finally {
  for (const ticket of createdTickets) {
    const deleted = await deleteTicket(ticket).catch((error) => ({ json: { ok: false, message: error?.message } }));
    log(deleted.json?.ok === true ? "CLEANUP ticket eliminado" : "BUG cleanup ticket no eliminado", {
      clientRequestId: ticket.clientRequestId,
      message: redact(deleted.json?.message ?? deleted.text ?? ""),
    });
  }
  const failed = checks.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    runId,
    fakeIsoDate,
    fakeDayKey,
    checks,
    created: createdTickets.map((ticket) => ({
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
