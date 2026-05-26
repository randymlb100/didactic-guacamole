import { readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const runId = `bugfix${Date.now()}`;
const day = String((Date.now() % 20) + 1).padStart(2, "0");
const fakeIsoDate = `2026-02-${day}`;
const fakeDayKey = `${day}-02-2026`;
const logFile = new URL(`./bugfix-regression-smoke-${new Date().toISOString().replace(/[:.]/g, "-")}.log`, import.meta.url);
const summaryFile = new URL(`./bugfix-regression-smoke-summary-${new Date().toISOString().slice(0, 10)}.md`, import.meta.url);

const lines = [];
const checks = [];
let originalUsersPayload = null;
let originalLimitValue = null;
let usersChanged = false;
let limitsChanged = false;
let adminCleanupToken = API_KEY;

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

function check(condition, label, data) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  log(`${ok ? "PASS" : "BUG"} ${label}`, data);
  return ok;
}

function sha256Hex(input) {
  return createHash("sha256").update(input).digest("hex");
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
  log(`HTTP ${label}`, { status: response.status, elapsedMs, ok: response.ok, message: clean(json?.message ?? json?.error).slice(0, 160) });
  return { status: response.status, elapsedMs, ok: response.ok, json, text };
}

async function edge(slug, body, token = API_KEY, extraHeaders = {}) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token, extraHeaders);
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

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return { username, ok: result.json?.ok === true && clean(result.json?.accessToken), token: result.json?.accessToken, user: result.json?.user, result };
}

async function fetchUsersPayload() {
  const result = await requestJson("users fetch", "GET", `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`);
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function saveUsersPayload(payload, token = adminCleanupToken) {
  const result = await edge("lotterynet-users-state", { action: "upsert", payload }, token);
  if (!result.ok || result.json?.ok === false) throw new Error(`No se pudo guardar usuarios: ${result.text}`);
}

async function fetchMasterValue(key) {
  const result = await edge("get-master-config", { action: "fetch", key });
  return result.json?.payload ?? null;
}

async function updateMasterValue(key, payload) {
  return edge("update-master-config", { key, payload });
}

async function upsertResults(table, payload) {
  return requestJson(
    `${table} upsert`,
    "POST",
    `${SUPABASE_URL}/rest/v1/${table}?on_conflict=result_date`,
    { result_date: fakeDayKey, payload, updated_at: new Date().toISOString() },
    API_KEY,
    { Prefer: "resolution=merge-duplicates,return=representation" },
  );
}

async function deleteResults(table) {
  return requestJson(`${table} cleanup`, "DELETE", `${SUPABASE_URL}/rest/v1/${table}?result_date=eq.${encodeURIComponent(fakeDayKey)}`);
}

function createSupervisor(admin, assignedCashiers) {
  const salt = randomUUID();
  const password = `Sup${runId.slice(-6)}!`;
  const user = `sup${runId.slice(-6)}`;
  return {
    password,
    supervisor: {
      id: `SUP-${runId.slice(-8).toUpperCase()}`,
      user,
      username: user,
      role: "supervisor",
      active: true,
      activo: true,
      adminId: admin.id,
      adminUser: admin.user,
      banca: admin.banca,
      commissionRate: 0.02,
      passwordSalt: salt,
      passwordHash: sha256Hex(`${salt}:${password}`),
      passwordVersion: "sha256-v1",
      assignedCashiers: assignedCashiers.map((cashier) => cashier.user),
      updatedAtEpochMs: Date.now(),
    },
  };
}

function installSupervisor(payload, admin, assignedCashiers) {
  const next = structuredClone(payload);
  const created = createSupervisor(admin, assignedCashiers);
  next.supervisores = [...(Array.isArray(next.supervisores) ? next.supervisores : []), created.supervisor];
  const assigned = new Set(assignedCashiers.map((cashier) => lower(cashier.user)));
  if (Array.isArray(next.users)) {
    next.users = next.users.map((account) => assigned.has(lower(account.user))
      ? {
          ...account,
          supervisorIds: [...new Set([...(account.supervisorIds ?? []), created.supervisor.id])],
          supervisorUsers: [...new Set([...(account.supervisorUsers ?? []), created.supervisor.user])],
          updatedAtEpochMs: Date.now(),
        }
      : account);
  }
  return { payload: next, ...created };
}

function play(playType, number, amount, lotteryId, lotteryName, extra = {}) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName, ...extra };
}

async function createTicket(session, actor, admin, label, plays) {
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
    lotteryName: plays[0]?.lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
  return { clientRequestId, body, result: await edge("create-ticket-v2", body, session.token) };
}

async function getDelta(ownerKey, token) {
  return edge("get-ticket-delta", { ownerKey, limit: 120 }, token);
}

async function report(slug, body, token) {
  return edge(slug, { from: fakeIsoDate, to: fakeIsoDate, ...body }, token);
}

async function main() {
  log("Inicio examen bugs corregidos", { runId, fakeDayKey });
  await deleteResults("lotterynet_results_by_day");
  await deleteResults("lotterynet_pick_results_by_day");

  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  originalUsersPayload = await fetchUsersPayload();
  const admin = findAccount(originalUsersPayload, "podero02");
  const cashier1 = findAccount(originalUsersPayload, "bancae01");
  const cashier2 = findAccount(originalUsersPayload, "bancae02");
  const adminCred = credentials.find((row) => lower(row.username) === "podero02");
  const cashierCred = credentials.find((row) => lower(row.username) === "bancae01");
  check(Boolean(admin && cashier1 && cashier2 && adminCred && cashierCred), "cuentas base existen", { admin: admin?.id, cashier1: cashier1?.user, cashier2: cashier2?.user });

  const adminSession = await login(adminCred.username, adminCred.password);
  const cashierSession = await login(cashierCred.username, cashierCred.password);
  adminCleanupToken = adminSession.token || API_KEY;
  check(adminSession.ok && cashierSession.ok, "login admin y cajero funcionan", { admin: adminSession.result.status, cashier: cashierSession.result.status });

  const limitKey = `cashier_limits:${admin.id}`;
  originalLimitValue = await fetchMasterValue(limitKey);
  const limitPayload = {
    defaults: { daySale: 10000, payout: 100000, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 },
    byUser: { [cashier1.user]: { daySale: 10000, payout: 100000, q: 4, pale: 500, sp: 500, t: 75, p3: 4, p3box: 4, p4: 4, p4box: 4 } },
  };
  const limitSave = await updateMasterValue(limitKey, limitPayload);
  limitsChanged = limitSave.ok && limitSave.json?.ok !== false;
  check(limitsChanged, "bug 1: limites se guardan por Edge y no por REST bloqueado", { status: limitSave.status, message: limitSave.json?.message });
  const fetchedLimit = await fetchMasterValue(limitKey);
  check(Number(fetchedLimit?.byUser?.[cashier1.user]?.q) === 4, "bug 1: limite guardado se puede leer desde get-master-config", { fetched: fetchedLimit?.byUser?.[cashier1.user] });

  const limitBlocked = await createTicket(cashierSession, cashier1, admin, "limit-q-over", [
    play("Q", "77", 5, "99991", "QA Limite Sin Resultado"),
  ]);
  check(limitBlocked.result.status === 409 && limitBlocked.result.json?.ok === false, "bug 1: limite guardado se aplica a venta real", {
    status: limitBlocked.result.status,
    message: limitBlocked.result.json?.message,
  });

  const supervisorSetup = installSupervisor(originalUsersPayload, admin, [cashier1, cashier2]);
  await saveUsersPayload(supervisorSetup.payload, adminSession.token);
  usersChanged = true;
  const supervisorSession = await login(supervisorSetup.supervisor.user, supervisorSetup.password);
  check(supervisorSession.ok, "supervisor QA puede entrar", { status: supervisorSession.result.status, user: supervisorSession.user });

  const ticket = await createTicket(cashierSession, cashier1, admin, "pick-winner", [
    play("P3", "256", 1, "19", "NJ Pick 3 Dia", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
    play("P4", "5423", 1, "21", "NJ Pick 4 Dia", { pickGame: "PICK4", pickMode: "STRAIGHT" }),
  ]);
  check(ticket.result.json?.ok === true, "ticket Pick ganador fue creado", { status: ticket.result.status, code: ticket.result.json?.ticket?.ticket_code });

  const pickResult = await upsertResults("lotterynet_pick_results_by_day", [
    { id: "19", name: "NJ Pick 3 Dia", number: "2-5-6", pick3: "256", status: "published" },
    { id: "21", name: "NJ Pick 4 Dia", number: "5-4-2-3", pick4: "5423", status: "published" },
  ]);
  check(pickResult.ok, "bug 2: resultado falso Pick se guarda sin RLS 401", { status: pickResult.status, message: pickResult.json?.message });

  await new Promise((resolve) => setTimeout(resolve, 1600));
  const delta = await getDelta(admin.id, adminSession.token);
  const row = (delta.json?.tickets ?? []).find((candidate) => candidate.client_request_id === ticket.clientRequestId);
  check(Number(row?.payout_amount ?? 0) > 0 || lower(row?.status).includes("gan"), "bug 2: resultado Pick falso marca premio", {
    status: row?.status,
    payout_amount: row?.payout_amount,
  });

  const supervisorReport = await report("get-supervisor-report", {
    actorKey: supervisorSetup.supervisor.user,
    adminKey: admin.id,
    supervisorKey: supervisorSetup.supervisor.user,
  }, supervisorSession.token);
  check(supervisorReport.json?.ok === true && Number(supervisorReport.json?.summary?.tickets ?? 0) > 0, "bug 3: supervisor ve ventas de cajeros asignados", {
    status: supervisorReport.status,
    summary: supervisorReport.json?.summary,
    cashiers: supervisorReport.json?.cashiers,
  });
}

async function writeSummary() {
  const passed = checks.filter((item) => item.ok).length;
  const failed = checks.length - passed;
  const bugs = checks.filter((item) => !item.ok)
    .map((item, index) => `${index + 1}. ${item.label}: \`${JSON.stringify(item.data ?? {}).slice(0, 500)}\``)
    .join("\n") || "No quedan bugs en este examen.";
  const summary = `# LotteryNet bugfix regression smoke - ${new Date().toISOString().slice(0, 10)}

Run ID: \`${runId}\`
Fecha QA: \`${fakeDayKey}\`
Log completo: \`${decodeURIComponent(logFile.pathname).replace(/^\/([A-Z]:)/, "$1")}\`

## Resultado

- Checks pasados: \`${passed}\`
- Checks con BUG/riesgo: \`${failed}\`

## Bugs restantes

${bugs}
`;
  await writeFile(summaryFile, summary, "utf8");
}

try {
  await main();
} catch (error) {
  check(false, "examen interrumpido", { message: error?.message, stack: error?.stack });
} finally {
  if (usersChanged && originalUsersPayload) {
    try {
      await saveUsersPayload(originalUsersPayload);
      log("CLEANUP usuarios restaurados");
    } catch (error) {
      log("BUG cleanup usuarios fallo", { message: error?.message });
    }
  }
  if (limitsChanged) {
    try {
      await updateMasterValue("cashier_limits:ADM-C5FFB0", originalLimitValue ?? {
        defaults: { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 },
      });
      log("CLEANUP limites restaurados");
    } catch (error) {
      log("BUG cleanup limites fallo", { message: error?.message });
    }
  }
  await deleteResults("lotterynet_results_by_day").catch((error) => log("BUG cleanup resultados normal fallo", { message: error?.message }));
  await deleteResults("lotterynet_pick_results_by_day").catch((error) => log("BUG cleanup resultados Pick fallo", { message: error?.message }));
  await writeSummary();
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname).replace(/^\/([A-Z]:)/, "$1") });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname).replace(/^\/([A-Z]:)/, "$1") });
}
