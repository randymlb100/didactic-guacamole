import { readFile, writeFile } from "node:fs/promises";

const BASE = "https://unhoulkujbtsypccpirc.supabase.co";
const KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const runId = `normalwinner${Date.now()}`;
const fakeDayKey = "15-01-2026";
const fakeIsoDate = "2026-01-15";
const lotteryId = `97${String(Date.now()).slice(-6)}`;
const lotteryName = `QA Normal Ganador ${runId.slice(-6)}`;
const clientIds = [];
const checks = [];
let originalResults = null;
let originalResultsExisted = false;
let cleanupToken = "";
let cleanupAdminKey = "";

const clean = (v) => String(v ?? "").trim();
const lower = (v) => clean(v).toLowerCase();
const check = (ok, label, data = {}) => {
  checks.push({ ok: Boolean(ok), label, data });
  console.log(`${ok ? "PASS" : "BUG"} ${label}`, JSON.stringify(data));
};
function headers(token = KEY) {
  return { apikey: KEY, Authorization: `Bearer ${token}`, "Content-Type": "application/json", Accept: "application/json" };
}
async function request(method, url, body, token = KEY, extra = {}) {
  const response = await fetch(url, {
    method, headers: { ...headers(token), ...extra },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  console.log(`HTTP ${method} ${url.split("/functions/v1/").pop()?.split("?")[0] ?? url} ${response.status}`);
  return { ok: response.ok, status: response.status, json, text };
}
const edge = (slug, body, token = KEY) => request("POST", `${BASE}/functions/v1/${slug}`, body, token);
function parseCredentials(raw) {
  return [...raw.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)]
    .map((m) => ({ username: clean(m[1]), password: clean(m[2]) }));
}
function accounts(payload) {
  return ["users", "admins", "cajeros", "cashiers", "supervisores", "supervisors"]
    .flatMap((key) => Array.isArray(payload?.[key]) ? payload[key] : []);
}
function findAccount(payload, username) {
  return accounts(payload).find((a) => [a.user, a.username, a.id, a.userId].some((v) => lower(v) === lower(username)));
}
async function login(credential) {
  const result = await edge("auth-legacy-login", { username: credential.username, password: credential.password });
  return { ...result, token: result.json?.accessToken, user: result.json?.user };
}
async function users(token) {
  const result = await edge("lotterynet-users-state", { action: "fetch" }, token);
  if (!result.ok) throw new Error(`users-state: ${result.text}`);
  return result.json?.payload ?? {};
}
async function createTicket(session, account, admin, label) {
  const clientRequestId = `${runId}-${label}`;
  clientIds.push(clientRequestId);
  return {
    clientRequestId,
    result: await edge("create-ticket-v2", {
      clientRequestId,
      localTicketId: clientRequestId,
      adminKey: admin.id,
      adminId: admin.id,
      actorKey: account.user,
      actorId: account.id,
      actorRole: lower(account.role) === "cashier" ? "cajero" : lower(account.role),
      cashierKey: account.user,
      cashierId: account.id,
      drawDate: fakeIsoDate,
      dayKey: fakeDayKey,
      lotteryName,
      phoneTime: new Date().toISOString(),
      plays: [{ playType: "Q", number: "65", amount: 1, lotteryId, lotteryName }],
    }, session.token),
  };
}
async function cleanupExisting(token, adminKey) {
  const delta = await edge("get-ticket-delta", { ownerKey: adminKey, cursor: "2000-01-01T00:00:00.000Z", limit: 1000 }, token);
  for (const row of delta.json?.tickets ?? []) {
    const id = clean(row.client_request_id ?? row.clientRequestId);
    if (!id.startsWith("normalwinner")) continue;
    await edge("void-ticket", {
      actorKey: "podero02",
      adminKey,
      cashierKey: clean(row.cashier_key ?? row.cashierKey) || "podero02",
      clientRequestId: id,
      action: "delete",
      returnLimit: true,
    }, token);
  }
}
async function main() {
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const adminCredential = credentials.find((c) => lower(c.username) === "podero02");
  check(Boolean(adminCredential), "credencial podero02 disponible");
  if (!adminCredential) throw new Error("Falta podero02");
  const adminSession = await login(adminCredential);
  check(Boolean(adminSession.token), "login podero02 valido", { status: adminSession.status });
  const payload = await users(adminSession.token);
  const admin = findAccount(payload, "podero02");
  cleanupToken = adminSession.token;
  cleanupAdminKey = clean(admin?.id);
  if (cleanupAdminKey) await cleanupExisting(cleanupToken, cleanupAdminKey);
  const cashierCredentials = credentials.filter((c) => lower(c.username).startsWith("bancae")).slice(0, 4);
  const cashiers = cashierCredentials.map((c) => ({ credential: c, account: findAccount(payload, c.username) })).filter((x) => x.account);
  check(Boolean(admin), "admin podero02 existe");
  check(cashiers.length === 4, "cuatro cajeros del grupo disponibles", { users: cashiers.map((x) => x.account.user) });
  const sessions = [];
  for (const entry of cashiers) {
    const session = await login(entry.credential);
    sessions.push({ ...entry, session });
    check(Boolean(session.token), `login ${entry.account.user} valido`, { status: session.status });
  }
  const created = [];
  const adminTicket = await createTicket(adminSession, admin, admin, "admin");
  check(adminTicket.result.json?.ok === true, "venta normal del admin creada", { status: adminTicket.result.status });
  if (adminTicket.result.json?.ok) created.push(adminTicket);
  for (const entry of sessions) {
    const ticket = await createTicket(entry.session, entry.account, admin, entry.account.user);
    check(ticket.result.json?.ok === true, `venta normal ${entry.account.user} creada`, { status: ticket.result.status });
    if (ticket.result.json?.ok) created.push(ticket);
  }
  const original = await request("GET", `${BASE}/rest/v1/lotterynet_results_by_day?result_date=eq.${fakeDayKey}&select=payload`);
  originalResultsExisted = Array.isArray(original.json) && original.json.length > 0;
  originalResults = original.json?.[0]?.payload ?? null;
  const resultUpsert = await request("POST", `${BASE}/rest/v1/lotterynet_results_by_day?on_conflict=result_date`, {
    result_date: fakeDayKey,
    payload: [{ id: lotteryId, name: lotteryName, number: "65", status: "published" }],
    updated_at: new Date().toISOString(),
  }, KEY, { Prefer: "resolution=merge-duplicates,return=representation" });
  check(resultUpsert.ok, "resultado QA controlado guardado", { status: resultUpsert.status });
  await new Promise((resolve) => setTimeout(resolve, 1200));
  const delta = await edge("get-ticket-delta", { ownerKey: admin.id, limit: 300, processPendingPrizes: true, processPrizeDays: [fakeDayKey] }, adminSession.token);
  const rows = delta.json?.tickets ?? [];
  const qaRows = rows.filter((row) => clientIds.includes(row.client_request_id));
  const winners = qaRows.filter((row) => lower(row.status ?? row.estado) === "ganador" || Number(row.payout_amount) > 0);
  check(delta.json?.ok === true, "delta responde después del resultado", { status: delta.status });
  check(winners.length === created.length, "todos los tickets QA quedan ganadores", {
    created: created.length,
    winners: winners.length,
    prizeReconcile: delta.json?.prizeReconcile ?? null,
    qaRows: qaRows.map((row) => ({ clientRequestId: row.client_request_id, status: row.status ?? row.estado, payout: row.payout_amount })),
  });
}
try { await main(); }
catch (error) { check(false, "smoke interrumpido", { message: error?.message }); }
finally {
  for (const clientRequestId of clientIds) {
    await edge("void-ticket", { actorKey: "podero02", adminKey: cleanupAdminKey, clientRequestId, action: "delete", returnLimit: true }, cleanupToken).catch(() => null);
  }
  if (originalResultsExisted) {
    await request("POST", `${BASE}/rest/v1/lotterynet_results_by_day?on_conflict=result_date`, { result_date: fakeDayKey, payload: originalResults, updated_at: new Date().toISOString() }, KEY, { Prefer: "resolution=merge-duplicates" }).catch(() => null);
  } else {
    await request("DELETE", `${BASE}/rest/v1/lotterynet_results_by_day?result_date=eq.${fakeDayKey}`, undefined, KEY).catch(() => null);
  }
  const failed = checks.filter((x) => !x.ok).length;
  console.log(JSON.stringify({ ok: failed === 0, checks: checks.length, failed, created: clientIds.length, cleaned: clientIds.length }, null, 2));
  if (failed) process.exitCode = 1;
}
