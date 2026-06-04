import { readFile, writeFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const runId = `payload${Date.now()}`;
const keepQaTickets = process.env.LOTTERYNET_KEEP_QA_TICKETS === "1";
const fakeIsoDate = "2026-03-29";
const fakeDayKey = "29-03-2026";
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryFile = new URL(`./ticket-payload-integrity-summary-${stamp}.json`, import.meta.url);

const checks = [];
const created = [];
let cashierSession = null;
let cashier = null;
let admin = null;

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
    signal: AbortSignal.timeout(15000),
  });
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
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

function roleOf(account) {
  const role = lower(account.role);
  return role === "cashier" ? "cajero" : role;
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

function baseBody(label, plays) {
  const clientRequestId = `${runId}-${label}`;
  return {
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
    lotteryName: plays?.[0]?.lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
}

async function createTicket(label, plays) {
  const body = baseBody(label, plays);
  const result = await edge("create-ticket-v2", body, cashierSession.token);
  if (result.json?.ok === true) created.push({ body, result });
  return { body, result };
}

async function getDelta(ownerKey, token) {
  return edge("get-ticket-delta", { ownerKey, cursor: "2000-01-01T00:00:00.000Z", limit: 500 }, token);
}

async function getList(ownerKey, token) {
  return edge("get-ticket-list", { ownerKey, dayKey: fakeDayKey, action: "fetch", limit: 500 }, token);
}

async function deleteTicket(ticket) {
  return edge("void-ticket", {
    actorKey: cashier.user,
    adminKey: admin.id,
    cashierKey: cashier.user,
    clientRequestId: ticket.body.clientRequestId,
    action: "delete",
    returnLimit: true,
  }, cashierSession.token);
}

function validPlay(extra = {}) {
  return {
    playType: "Q",
    number: "25",
    amount: 2,
    potentialPayout: 0,
    lotteryId: "1",
    lotteryName: "La Primera Dia",
    ...extra,
  };
}

try {
  console.log(`Inicio ticket payload integrity ${runId}`);
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  admin = findAccount(payload, "podero02");
  cashier = findAccount(payload, "bancae01");
  const cashierCred = credentials.find((entry) => lower(entry.username) === "bancae01");
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  check(Boolean(admin && cashier && cashierCred && adminCred), "cuentas QA disponibles", {
    admin: admin?.id,
    cashier: cashier?.user,
  });
  if (!admin || !cashier || !cashierCred || !adminCred) throw new Error("Faltan cuentas QA.");

  cashierSession = await login(cashierCred.username, cashierCred.password);
  const adminSession = await login(adminCred.username, adminCred.password);
  check(cashierSession.ok && adminSession.ok, "login admin y cajero", {
    cashierStatus: cashierSession.result.status,
    adminStatus: adminSession.result.status,
  });

  const empty = await createTicket("empty-plays", []);
  check(empty.result.status === 400 && empty.result.json?.ok === false, "servidor rechaza ticket sin jugadas", {
    status: empty.result.status,
    message: empty.result.json?.message,
  });

  const incomplete = await createTicket("missing-number", [validPlay({ number: "" })]);
  check(incomplete.result.status === 400 && incomplete.result.json?.ok === false, "servidor rechaza jugada sin numero", {
    status: incomplete.result.status,
    message: incomplete.result.json?.message,
  });

  const noLottery = await createTicket("missing-lottery", [validPlay({ lotteryId: "", lotteryName: "" })]);
  check(noLottery.result.status === 400 && noLottery.result.json?.ok === false, "servidor rechaza jugada sin loteria", {
    status: noLottery.result.status,
    message: noLottery.result.json?.message,
    ok: noLottery.result.json?.ok,
  });

  const valid = await createTicket("valid-one", [validPlay()]);
  check(valid.result.json?.ok === true, "servidor acepta ticket valido", {
    status: valid.result.status,
    code: valid.result.json?.ticket?.ticket_code ?? valid.result.json?.ticketCode,
  });

  const duplicate = await edge("create-ticket-v2", valid.body, cashierSession.token);
  check(duplicate.status === 200 && duplicate.json?.ok === true, "duplicado por clientRequestId responde controlado", {
    status: duplicate.status,
    message: duplicate.json?.message,
  });

  const delta = await getDelta(admin.id, adminSession.token);
  const tickets = delta.json?.tickets ?? [];
  const items = delta.json?.items ?? [];
  const validRow = tickets.find((ticket) => ticket.client_request_id === valid.body.clientRequestId);
  const emptyRow = tickets.find((ticket) => ticket.client_request_id === empty.body.clientRequestId);
  const noLotteryRow = tickets.find((ticket) => ticket.client_request_id === noLottery.body.clientRequestId);
  const validItems = validRow ? items.filter((item) => clean(item.ticket_id) === clean(validRow.id)) : [];
  check(Boolean(validRow), "ticket valido cae en delta del admin", { ticketCode: validRow?.ticket_code });
  check(validItems.length === 1, "ticket valido tiene exactamente un item", { items: validItems.length });
  check(validItems.every((item) => clean(item.lottery_name) && clean(item.play_numbers ?? item.normalized_number)), "item valido conserva loteria y numero", {
    item: validItems[0] ? {
      lotteryName: validItems[0].lottery_name,
      number: validItems[0].play_numbers ?? validItems[0].normalized_number,
      amount: validItems[0].amount,
    } : null,
  });
  check(!emptyRow && !noLotteryRow, "payloads rechazados no aparecen como tickets fantasma", {
    emptyFound: Boolean(emptyRow),
    noLotteryFound: Boolean(noLotteryRow),
  });
  check(tickets.filter((ticket) => ticket.client_request_id === valid.body.clientRequestId).length === 1, "ticket valido no se duplica en delta", {
    rows: tickets.filter((ticket) => ticket.client_request_id === valid.body.clientRequestId).length,
  });

  const list = await getList(admin.id, adminSession.token);
  const listTickets = list.json?.payload?.tickets ?? list.json?.tickets ?? [];
  const listValid = listTickets.find((ticket) =>
    [ticket.id, ticket.clientRequestId, ticket.client_request_id].some((value) => clean(value) === valid.body.clientRequestId)
  );
  const listItems = listValid?.items ?? listValid?.plays ?? [];
  check(Boolean(listValid), "ticket valido cae en lista del owner admin", { code: listValid?.code ?? listValid?.ticketCode });
  check(
    [listValid?.vendedorNombre, listValid?.cajeroId, listValid?.vendedorId].some((value) => lower(value) === lower(cashier.user) || clean(value) === clean(cashier.id)),
    "ticket valido conserva vendedor cajero en lista",
    { vendedorNombre: listValid?.vendedorNombre, cajeroId: listValid?.cajeroId, vendedorId: listValid?.vendedorId },
  );
  check(Array.isArray(listItems) && listItems.length === 1, "lista cajero trae items del ticket valido", { items: listItems.length });

  if (keepQaTickets) {
    console.log(`KEEP_QA_TICKETS ${JSON.stringify(created.map((ticket) => ({
      clientRequestId: ticket.body.clientRequestId,
      ticketCode: ticket.result.json?.ticket?.ticket_code ?? ticket.result.json?.ticketCode,
    })))}`);
  } else {
    for (const ticket of created) {
      const deleted = await deleteTicket(ticket);
      console.log(`CLEANUP ${ticket.body.clientRequestId} ${JSON.stringify({ status: deleted.status, ok: deleted.json?.ok, message: deleted.json?.message })}`);
    }
  }

  const failed = checks.filter((item) => !item.ok);
  await writeFile(summaryFile, JSON.stringify({ ok: failed.length === 0, runId, fakeDayKey, checks }, null, 2), "utf8");
  console.log(`SUMMARY_FILE ${decodeURIComponent(summaryFile.pathname)}`);
  if (failed.length) process.exit(1);
} catch (error) {
  check(false, "smoke interrumpido", { message: error?.message });
  if (!keepQaTickets) {
    for (const ticket of created) {
      await deleteTicket(ticket).catch(() => null);
    }
  }
  await writeFile(summaryFile, JSON.stringify({ ok: false, runId, fakeDayKey, checks }, null, 2), "utf8");
  console.log(`SUMMARY_FILE ${decodeURIComponent(summaryFile.pathname)}`);
  process.exit(1);
}
