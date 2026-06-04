import { readFile, writeFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ||
  "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const SNAPSHOT_FILE = new URL("./qa-system-mode-guard.snapshot.json", import.meta.url);
const action = process.argv[2] || "open";

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
    signal: AbortSignal.timeout(20000),
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
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function fetchMasterPayload(key) {
  const result = await requestJson(
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_master_state?config_key=eq.${encodeURIComponent(key)}&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer ${key}: ${result.text}`);
  return result.json?.[0]?.payload ?? null;
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

async function updateMasterValue(key, payload, token) {
  const result = await edge("update-master-config", { key, payload }, token);
  if (result.json?.ok !== true) throw new Error(`No se pudo guardar ${key}: ${result.text}`);
}

async function main() {
  const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
  const payload = await fetchUsersPayload();
  const admin = findAccount(payload, "podero02");
  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  if (!admin || !adminCred) throw new Error("No encontre podero02.");
  const token = await login(adminCred);
  const key = `system_modes:${admin.id}`;

  if (action === "restore") {
    const snapshot = JSON.parse(await readFile(SNAPSHOT_FILE, "utf8"));
    await updateMasterValue(snapshot.key, snapshot.payload, token);
    console.log(`RESTORED ${snapshot.key}`);
    return;
  }

  const current = await fetchMasterPayload(key);
  try {
    await readFile(SNAPSHOT_FILE, "utf8");
  } catch {
    await writeFile(SNAPSHOT_FILE, JSON.stringify({ key, payload: current ?? {} }, null, 2), "utf8");
  }
  await updateMasterValue(key, {
    ...(current ?? {}),
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    blockedSalePlays: [],
  }, token);
  console.log(`OPENED ${key}`);
}

main().catch((error) => {
  console.error(error?.message ?? error);
  process.exit(1);
});
