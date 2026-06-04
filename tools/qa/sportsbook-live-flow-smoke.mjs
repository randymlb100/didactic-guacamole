import { readFile, writeFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const RUN_ID = `sportsqa${Date.now()}`;
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryFile = new URL(`./sportsbook-live-flow-summary-${stamp}.json`, import.meta.url);

const checks = [];
const calls = [];

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
  return ok;
}

async function requestJson(label, method, url, body, token = API_KEY) {
  const started = performance.now();
  const response = await fetch(url, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(25000),
  });
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  calls.push({ label, status: response.status, elapsedMs, ok: response.ok });
  console.log(`HTTP ${label} ${JSON.stringify({
    status: response.status,
    elapsedMs,
    ok: response.ok,
    message: clean(json?.message ?? json?.error).slice(0, 180),
  })}`);
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
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: result.json?.accessToken,
    result,
  };
}

function sportsPayload(sessionAccount, admin, oddsIds) {
  return {
    actorRole: "cashier",
    actorKey: sessionAccount.user,
    ownerKey: admin.id,
    adminKey: admin.id,
    cashierKey: sessionAccount.user,
    sellerUsername: sessionAccount.user,
    bancaName: sessionAccount.name || sessionAccount.bancaName || "Banca QA",
    clientRequestId: RUN_ID,
    stake: 25,
    selections: oddsIds.map((oddsId) => ({ oddsId })),
  };
}

try {
  console.log(`Inicio sportsbook live flow ${RUN_ID}`);
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const usersPayload = await fetchUsersPayload();
  const admin = findAccount(usersPayload, "podero02");
  const cashier = findAccount(usersPayload, "bancae01");
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  const cashierCred = credentials.find((entry) => lower(entry.username) === "bancae01");
  check(Boolean(admin && cashier && adminCred && cashierCred), "cuentas podero02/bancae01 disponibles", {
    admin: admin?.id,
    cashier: cashier?.user,
  });
  if (!admin || !cashier || !adminCred || !cashierCred) throw new Error("Faltan cuentas QA.");

  const adminSession = await login(adminCred.username, adminCred.password);
  const cashierSession = await login(cashierCred.username, cashierCred.password);
  check(adminSession.ok && cashierSession.ok, "login admin y cajero deportivo", {
    adminStatus: adminSession.result.status,
    cashierStatus: cashierSession.result.status,
  });

  const seed = await edge("sports-qa-seed", {
    actorRole: "admin",
    actorKey: admin.id,
    ownerKey: admin.id,
    adminKey: admin.id,
    cashierKey: cashier.user,
    runId: RUN_ID,
  }, adminSession.token);
  check(seed.json?.ok === true && seed.json?.oddsIds?.length === 2, "cuotas QA listas para parlay", {
    odds: seed.json?.oddsIds?.length ?? 0,
    elapsedMs: seed.elapsedMs,
  });
  if (seed.json?.ok !== true) throw new Error(seed.json?.message ?? "No se pudo preparar QA deportivo.");

  const board = await edge("sports-get-board", { league: "qa" });
  const games = board.json?.payload?.games ?? board.json?.games ?? [];
  const qaGames = games.filter((game) => clean(game?.event?.leagueTitle ?? game?.leagueTitle) === "QA League");
  check(board.json?.ok === true && qaGames.length >= 2, "tablero lee juegos deportivos cacheados", {
    totalGames: games.length,
    qaGames: qaGames.length,
    elapsedMs: board.elapsedMs,
  });

  const saleBody = sportsPayload(cashier, admin, seed.json.oddsIds);
  const sale = await edge("create-sports-ticket", saleBody, cashierSession.token);
  const ticket = sale.json?.ticket ?? {};
  check(sale.json?.ok === true && clean(ticket.id), "venta deportiva real creada en servidor", {
    status: sale.status,
    ticketCode: ticket.ticket_code,
    stake: ticket.stake,
    payout: ticket.potential_payout,
    elapsedMs: sale.elapsedMs,
  });

  const pendingPay = await edge("pay-sports-ticket", {
    actorRole: "cashier",
    actorKey: cashier.user,
    ownerKey: admin.id,
    adminKey: admin.id,
    cashierKey: cashier.user,
    ticketId: ticket.id,
  }, cashierSession.token);
  check(pendingPay.status === 409, "servidor bloquea pagar ticket pendiente", {
    status: pendingPay.status,
    message: pendingPay.json?.message,
  });

  const adminListBefore = await edge("get-sports-tickets", {
    actorRole: "admin",
    actorKey: admin.id,
    ownerKey: admin.id,
    adminKey: admin.id,
    status: "pending",
    limit: 25,
  }, adminSession.token);
  const beforeTickets = adminListBefore.json?.tickets ?? [];
  check(beforeTickets.some((row) => clean(row.id) === clean(ticket.id)), "admin ve ticket deportivo pendiente", {
    rows: beforeTickets.length,
    elapsedMs: adminListBefore.elapsedMs,
  });

  const settled = await edge("settle-sports-ticket", {
    actorRole: "admin",
    actorKey: admin.id,
    ownerKey: admin.id,
    adminKey: admin.id,
    ticketId: ticket.id,
    nextStatus: "won",
    reason: "QA flujo deportivo",
  }, adminSession.token);
  check(settled.json?.ok === true && settled.json?.ticket?.status === "won", "admin liquida ticket como ganador", {
    status: settled.status,
    ticketStatus: settled.json?.ticket?.status,
    elapsedMs: settled.elapsedMs,
  });

  const paid = await edge("pay-sports-ticket", {
    actorRole: "cashier",
    actorKey: cashier.user,
    ownerKey: admin.id,
    adminKey: admin.id,
    cashierKey: cashier.user,
    ticketId: ticket.id,
  }, cashierSession.token);
  check(paid.json?.ok === true && paid.json?.ticket?.status === "paid", "cajero paga cobro deportivo ganador", {
    status: paid.status,
    ticketStatus: paid.json?.ticket?.status,
    elapsedMs: paid.elapsedMs,
  });

  const adminListAfter = await edge("get-sports-tickets", {
    actorRole: "admin",
    actorKey: admin.id,
    ownerKey: admin.id,
    adminKey: admin.id,
    status: "paid",
    limit: 25,
  }, adminSession.token);
  const paidTicket = (adminListAfter.json?.tickets ?? []).find((row) => clean(row.id) === clean(ticket.id));
  check(Boolean(paidTicket), "ticket pagado aparece en cobros/finanza deportiva", {
    paidRows: adminListAfter.json?.tickets?.length ?? 0,
    summary: adminListAfter.json?.summary,
  });

  const duplicate = await edge("create-sports-ticket", saleBody, cashierSession.token);
  check(duplicate.json?.ok === true && duplicate.json?.duplicate === true, "duplicado clientRequestId no crea otro ticket", {
    status: duplicate.status,
    duplicate: duplicate.json?.duplicate,
  });

  const slowCalls = calls.filter((call) => call.elapsedMs > 4500);
  const edgeCalls = calls.filter((call) => !call.label.startsWith("db:"));
  const operationalCalls = edgeCalls.filter((call) =>
    !["users-state fetch", "auth-legacy-login", "sports-qa-seed"].includes(call.label) &&
    !(call.label === "create-sports-ticket" && call.elapsedMs < 700)
  );
  check(slowCalls.length === 0, "ninguna llamada paso de 4.5 segundos", {
    slowCalls,
  });
  check(operationalCalls.length <= 7, "flujo operativo usa llamadas controladas al servidor", {
    operationalCalls: operationalCalls.length,
    setupCalls: edgeCalls.length - operationalCalls.length,
    calls: operationalCalls.map((call) => `${call.label}:${call.elapsedMs}ms`),
  });

  const summary = {
    runId: RUN_ID,
    ok: checks.every((item) => item.ok),
    checks,
    calls,
    ticket: {
      id: ticket.id,
      code: ticket.ticket_code,
      stake: ticket.stake,
      potentialPayout: ticket.potential_payout,
    },
  };
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  console.log(`SUMMARY ${summaryFile.pathname}`);
  if (!summary.ok) process.exitCode = 1;
} catch (error) {
  console.error(`FATAL ${error instanceof Error ? error.stack : String(error)}`);
  process.exitCode = 1;
}
