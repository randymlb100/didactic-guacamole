import { readFile, writeFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const STATE_FILE = new URL("./payment-tolerance-state.json", import.meta.url);

const fakeIsoDate = "2026-01-17";
const fakeDayKey = "17-01-2026";

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
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

function headers(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

async function requestJson(label, method, url, body, token = API_KEY) {
  const start = performance.now();
  const response = await fetch(url, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const elapsedMs = Math.round(performance.now() - start);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  console.log(`${label}: HTTP ${response.status} (${elapsedMs}ms)`);
  return { status: response.status, ok: response.ok, json, text, elapsedMs };
}

async function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
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
  if (!result.ok || result.json?.ok !== true || !clean(result.json?.accessToken)) {
    throw new Error(`Login fallo para ${username}: ${result.text}`);
  }
  return result.json.accessToken;
}

async function create() {
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, "podero02");
  const cashier = findAccount(payload, "bancae01");
  const cashierCredential = credentials.find((entry) => lower(entry.username) === "bancae01");
  if (!admin || !cashier || !cashierCredential) throw new Error("Faltan cuentas de prueba podero02/bancae01.");
  const token = await login(cashierCredential.username, cashierCredential.password);
  const runId = `paytol${Date.now()}`;
  const clientRequestId = `${runId}-winner`;
  const body = {
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
    lotteryName: "La Primera Dia",
    phoneTime: new Date().toISOString(),
    plays: [
      {
        playType: "Q",
        number: "32",
        amount: 2,
        potentialPayout: 0,
        lotteryId: "1",
        lotteryName: "La Primera Dia",
      },
    ],
  };
  const result = await edge("create-ticket-v2", body, token);
  if (result.json?.ok !== true) throw new Error(`Venta QA fallo: ${result.text}`);
  const state = { runId, clientRequestId, fakeIsoDate, fakeDayKey, admin, cashier };
  await writeFile(STATE_FILE, JSON.stringify(state, null, 2), "utf8");
  console.log(`CREATED ${clientRequestId}`);
  console.log(`STATE ${STATE_FILE.pathname}`);
}

async function publishWinningResult() {
  const response = await requestJson(
    "lotterynet_results_by_day upsert",
    "POST",
    `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?on_conflict=result_date`,
    {
      result_date: fakeDayKey,
      payload: [
        { id: "1", name: "La Primera Dia", number: "32", status: "published" },
        { id: "1", name: "La Primera Día", number: "32", status: "published" },
      ],
      updated_at: new Date().toISOString(),
    },
  );
  if (!response.ok) throw new Error(`No se pudo publicar resultado QA: ${response.text}`);
}

async function pay() {
  const state = JSON.parse(await readFile(STATE_FILE, "utf8"));
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const adminCredential = credentials.find((entry) => lower(entry.username) === "podero02");
  if (!adminCredential) throw new Error("Falta credencial podero02.");
  const token = await login(adminCredential.username, adminCredential.password);
  await publishWinningResult();
  await new Promise((resolve) => setTimeout(resolve, 1200));
  const result = await edge("pay-ticket", {
    actorKey: "podero02",
    adminKey: "podero02",
    cashierKey: state.cashier.user,
    clientRequestId: state.clientRequestId,
  }, token);
  if (result.json?.ok !== true) throw new Error(`Pago QA fallo: ${result.text}`);
  console.log(`PAID ${state.clientRequestId} amount=${result.json.amount}`);
}

const mode = process.argv[2] ?? "create";
if (mode === "create") await create();
else if (mode === "pay") await pay();
else throw new Error("Uso: node tools/qa/payment-tolerance-smoke.mjs create|pay");
