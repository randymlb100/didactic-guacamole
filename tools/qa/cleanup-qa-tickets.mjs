import { readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const QA_DIR = new URL("./", import.meta.url);
const QA_PREFIXES = [
  "podero02stress",
  "payload",
  "admctrl",
  "bugfix",
  "workday",
];
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryFile = new URL(`./cleanup-qa-tickets-summary-${stamp}.json`, import.meta.url);

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

async function requestJson(label, method, url, body, token = API_KEY) {
  const response = await fetch(url, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  console.log(`HTTP ${label} ${JSON.stringify({
    status: response.status,
    ok: response.ok,
    message: clean(json?.message ?? json?.error).slice(0, 180),
  })}`);
  return { status: response.status, ok: response.ok, json, text };
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

function accountArray(value) {
  return Array.isArray(value) ? value : [];
}

function allAccounts(payload) {
  return [
    ...accountArray(payload.users),
    ...accountArray(payload.admins),
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
    ...accountArray(payload.cajeros),
    ...accountArray(payload.cashiers),
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

async function login(credential) {
  const result = await edge("auth-legacy-login", {
    username: credential.username,
    password: credential.password,
  });
  if (result.json?.ok !== true || !clean(result.json?.accessToken)) {
    throw new Error(`No pudo entrar ${credential.username}: ${result.text}`);
  }
  return result.json.accessToken;
}

function isQaClientRequestId(value) {
  const text = lower(value);
  return QA_PREFIXES.some((prefix) => text.startsWith(prefix));
}

async function collectFromSummaries() {
  const ids = new Set();
  const files = await readdir(QA_DIR);
  for (const file of files.filter((name) => name.endsWith(".json") && /summary/i.test(name))) {
    let json = null;
    try {
      json = JSON.parse(await readFile(new URL(file, QA_DIR), "utf8"));
    } catch {
      continue;
    }
    const rows = Array.isArray(json.created) ? json.created : [];
    for (const row of rows) {
      const id = clean(row.clientRequestId ?? row.client_request_id);
      if (isQaClientRequestId(id)) ids.add(id);
    }
    const checks = Array.isArray(json.checks) ? json.checks : [];
    for (const check of checks) {
      const id = clean(check?.data?.clientRequestId ?? check?.data?.client_request_id);
      if (isQaClientRequestId(id)) ids.add(id);
    }
  }
  return ids;
}

async function collectFromServer(ownerKey, token) {
  const ids = new Map();
  const result = await edge(
    "get-ticket-delta",
    { ownerKey, cursor: "2000-01-01T00:00:00.000Z", limit: 1000 },
    token,
  );
  for (const ticket of result.json?.tickets ?? []) {
    const clientRequestId = clean(ticket.client_request_id ?? ticket.clientRequestId);
    if (isQaClientRequestId(clientRequestId)) {
      ids.set(clientRequestId, {
        ticketCode: clean(ticket.ticket_code ?? ticket.ticketCode),
        cashierKey: clean(ticket.cashier_key ?? ticket.cashierKey),
      });
    }
  }
  return ids;
}

async function main() {
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, "podero02");
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  if (!admin || !adminCred) throw new Error("No encontre podero02 para limpiar QA.");
  const token = await login(adminCred);

  const summaryIds = await collectFromSummaries();
  const serverTickets = await collectFromServer(admin.id, token);
  for (const id of summaryIds) {
    if (!serverTickets.has(id)) serverTickets.set(id, {});
  }

  const results = [];
  for (const [clientRequestId, meta] of serverTickets) {
    const deleted = await edge("void-ticket", {
      actorKey: "podero02",
      adminKey: admin.id,
      cashierKey: meta.cashierKey || "podero02",
      clientRequestId,
      action: "delete",
      returnLimit: true,
    }, token);
    results.push({
      clientRequestId,
      ticketCode: meta.ticketCode,
      status: deleted.status,
      ok: deleted.json?.ok === true,
      message: deleted.json?.message,
    });
    console.log(`CLEANUP ${clientRequestId} ${JSON.stringify(results.at(-1))}`);
  }

  const summary = {
    ok: results.every((row) => row.ok || /no existe|not found|no encontrado/i.test(clean(row.message))),
    attempted: results.length,
    deleted: results.filter((row) => row.ok).length,
    results,
  };
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  console.log(`SUMMARY_FILE ${path.normalize(decodeURIComponent(summaryFile.pathname))}`);
  if (!summary.ok) process.exit(1);
}

main().catch(async (error) => {
  const summary = { ok: false, error: error?.message ?? String(error) };
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8").catch(() => null);
  console.error(`BUG cleanup ${error?.message ?? error}`);
  console.log(`SUMMARY_FILE ${path.normalize(decodeURIComponent(summaryFile.pathname))}`);
  process.exit(1);
});
