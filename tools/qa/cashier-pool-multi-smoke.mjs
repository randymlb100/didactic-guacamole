import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const url = "https://unhoulkujbtsypccpirc.supabase.co";
const key = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const run = `poolmulti${Date.now()}`;
const lottery = `QA-${run}`;
const otherLottery = `QA-OTHER-${run}`;
const credsText = await readFile("C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt", "utf8");
const creds = new Map([...credsText.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((m) => [m[1].trim().toLowerCase(), { user: m[1].trim(), pass: m[2].trim() }]));
const headers = (token = key) => ({ apikey: key, Authorization: `Bearer ${token}`, "Content-Type": "application/json" });
async function edge(name, body, token = key) { const r = await fetch(`${url}/functions/v1/${name}`, { method: "POST", headers: headers(token), body: JSON.stringify(body) }); return { status: r.status, json: await r.json() }; }
async function login(name) { const c = creds.get(name); assert.ok(c, `credencial ${name}`); const r = await edge("auth-legacy-login", { username: c.user, password: c.pass }); assert.ok(r.json.accessToken, `login ${name}`); return r.json; }
function play(amount, lot = lottery) { return { playType: "Q", number: "77", amount, potentialPayout: 0, lotteryId: lot, lotteryName: lot }; }
function body(label, actor, admin) { return { clientRequestId: `${run}-${label}`, localTicketId: `${run}-${label}`, adminKey: admin.id, adminId: admin.id, actorKey: actor.user, actorId: actor.id, actorRole: "cajero", cashierKey: actor.user, cashierId: actor.id, drawDate: "2026-04-15", dayKey: "15-04-2026", lotteryName: lottery, phoneTime: new Date().toISOString(), plays: [play(label === "other" ? 2 : label === "a" ? 3 : 2, label === "other" ? otherLottery : lottery)] }; }
const adminSession = await login("podero02");
const users = (await edge("lotterynet-users-state", { action: "fetch" })).json.payload;
const admin = [...(users.admins ?? []), ...(users.users ?? [])].find((x) => String(x.user ?? x.username).toLowerCase() === "podero02");
const cashiers = users.cajeros ?? users.cashiers ?? [];
const a = cashiers.find((x) => String(x.user ?? x.username).toLowerCase() === "bancae01");
const b = cashiers.find((x) => String(x.user ?? x.username).toLowerCase() === "bancae02");
assert.ok(admin && a && b, "admin y dos cajeros QA disponibles");
const aSession = await login("bancae01"); const bSession = await login("bancae02");
const limitKey = `cashier_limits:${admin.id}`;
const old = (await edge("get-master-config", { action: "fetch", key: limitKey }, adminSession.accessToken)).json.payload;
const created = [];
try {
  const config = { defaults: { q: 0 }, pool: { q: 5 }, byUser: { [a.user]: { q: 4 }, [b.user]: { q: 4 } } };
  const saved = await edge("update-master-config", { key: limitKey, payload: config }, adminSession.accessToken); assert.equal(saved.status, 200);
  const create = async (label, session, actor) => { const r = await edge("create-ticket-v2", body(label, actor, admin), session.accessToken); if (r.json.ok) created.push({ ...body(label, actor, admin), token: session.accessToken }); return r; };
  const firstA = await create("a", aSession, a); assert.equal(firstA.status, 200, "A vende 3");
  const firstB = await create("b", bSession, b); assert.equal(firstB.status, 200, "B vende 2 del pool restante");
  const poolBlocked = await create("b2", bSession, b); assert.equal(poolBlocked.status, 409, "pool bloquea exceso compartido"); assert.match(String(poolBlocked.json.message), /pool|limite/i);
  const other = await create("other", bSession, b); assert.equal(other.status, 200, "otra loteria queda independiente");
  console.log(JSON.stringify({ pass: true, admin: admin.user, cashiers: [a.user, b.user], pool: 5, soldA: 3, soldB: 2, poolExcessStatus: poolBlocked.status, otherLotteryStatus: other.status }, null, 2));
} finally {
  for (const ticket of created) await edge("void-ticket", { actorKey: ticket.cashierKey, adminKey: admin.id, cashierKey: ticket.cashierKey, clientRequestId: ticket.clientRequestId, action: "delete", returnLimit: true }, ticket.token);
  await edge("update-master-config", { key: limitKey, payload: old ?? {} }, adminSession.accessToken);
}
