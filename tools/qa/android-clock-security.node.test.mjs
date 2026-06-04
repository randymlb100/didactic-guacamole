import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const RUN_ID = `clocksec${Date.now()}`;
const ADMIN_USERNAME = "podero02";
const CASHIER_USERNAME = "bancae01";

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

async function requestJson(method, url, body, token = API_KEY) {
  const response = await fetch(url, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { status: response.status, ok: response.ok, json, text };
}

function edge(slug, body, token = API_KEY) {
  return requestJson("POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
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

function dominicanDay() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return {
    iso: `${values.year}-${values.month}-${values.day}`,
    key: `${values.day}-${values.month}-${values.year}`,
  };
}

async function fetchUsersPayload() {
  const result = await requestJson(
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  assert.equal(result.ok, true, `No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  assert.equal(result.json?.ok, true, `Login fallo para ${username}: ${result.text}`);
  assert.ok(clean(result.json?.accessToken), `Login no devolvio token para ${username}`);
  return { username, token: result.json.accessToken, user: result.json.user };
}

function buildTicketBody({ admin, cashier, phoneTime, suffix }) {
  const day = dominicanDay();
  const clientRequestId = `${RUN_ID}-${suffix}`;
  return {
    clientRequestId,
    localTicketId: clientRequestId,
    adminKey: admin.id,
    adminId: admin.id,
    actorKey: cashier.user,
    actorId: cashier.id,
    actorRole: "cajero",
    cashierKey: cashier.user,
    cashierId: cashier.id,
    drawDate: day.iso,
    dayKey: day.key,
    lotteryName: "QA Clock Security",
    phoneTime,
    plays: [
      {
        playType: "Q",
        number: "73",
        amount: 1,
        potentialPayout: 0,
        lotteryId: "clock-security",
        lotteryName: "QA Clock Security",
      },
    ],
  };
}

async function voidUnexpectedTicket(body, token) {
  await edge("void-ticket", {
    clientRequestId: body.clientRequestId,
    localTicketId: body.localTicketId,
    actorKey: body.cashierKey,
    adminKey: body.adminKey,
    cashierKey: body.cashierKey,
    action: "void",
  }, token).catch(() => null);
}

function assertRejectedAsClockSecurity(result, label) {
  assert.notEqual(result.json?.ok, true, `${label}: el servidor creo ticket con hora manipulada: ${result.text}`);
  assert.ok([400, 401, 403, 409, 422].includes(result.status), `${label}: status inesperado ${result.status}: ${result.text}`);
}

test("create-ticket-v2 rechaza ventas si el celular manda hora atrasada o adelantada", async () => {
  const payload = await fetchUsersPayload();
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const admin = findAccount(payload, ADMIN_USERNAME);
  const cashier = findAccount(payload, CASHIER_USERNAME);
  const adminCred = credentials.find((row) => lower(row.username) === ADMIN_USERNAME);
  const cashierCred = credentials.find((row) => lower(row.username) === CASHIER_USERNAME);

  assert.ok(admin, "Cuenta admin de prueba no encontrada.");
  assert.ok(cashier, "Cuenta cajero de prueba no encontrada.");
  assert.ok(adminCred, "Credencial admin de prueba no encontrada.");
  assert.ok(cashierCred, "Credencial cajero de prueba no encontrada.");

  await login(adminCred.username, adminCred.password);
  const cashierSession = await login(cashierCred.username, cashierCred.password);

  for (const scenario of [
    { suffix: "past", phoneTime: "2000-01-01T00:00:00.000Z", label: "hora atrasada" },
    { suffix: "future", phoneTime: "2099-01-01T00:00:00.000Z", label: "hora adelantada" },
    { suffix: "missing", phoneTime: undefined, label: "hora ausente" },
    { suffix: "invalid", phoneTime: "no-es-fecha", label: "hora invalida" },
  ]) {
    const body = buildTicketBody({ admin, cashier, phoneTime: scenario.phoneTime, suffix: scenario.suffix });
    const result = await edge("create-ticket-v2", body, cashierSession.token);
    if (result.json?.ok === true) {
      await voidUnexpectedTicket(body, cashierSession.token);
    }
    assertRejectedAsClockSecurity(result, scenario.label);
  }
});
