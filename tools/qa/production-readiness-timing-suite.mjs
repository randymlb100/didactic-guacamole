import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { constants } from "node:fs";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { performance } from "node:perf_hooks";

const execFileAsync = promisify(execFile);

const SUPABASE_URL = process.env.LOTTERYNET_SUPABASE_URL || "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = process.env.LOTTERYNET_SUPABASE_PUBLISHABLE_KEY || "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const DEFAULT_SECRET_FILE = "C:/Users/Randy Cordero/Documents/LotteryNet-Secrets/contraseña de prueba.txt";
const FALLBACK_SECRET_FILE = new URL("contraseña de prueba.txt", PROJECT_ROOT);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || DEFAULT_SECRET_FILE;
const WRITE_PROBE = process.env.LOTTERYNET_READINESS_WRITE === "1";
const SECURITY_PROBES = process.env.LOTTERYNET_SECURITY_PROBES === "1";
const APP_PROBE = process.env.LOTTERYNET_READINESS_APP !== "0";
const LOOPS = Math.max(1, Number(process.env.LOTTERYNET_READINESS_LOOPS || 3));
const TODAY_DAY_KEY = toDrDayKey(new Date());
const TODAY_ISO = toIsoDate(new Date());
const RUN_ID = `readiness${Date.now()}`;
const QA_DAY_KEY = process.env.LOTTERYNET_READINESS_DAY_KEY || "22-04-2026";
const QA_ISO = dayKeyToIso(QA_DAY_KEY);
const QA_LOTTERY_ID = process.env.LOTTERYNET_READINESS_LOTTERY_ID || `97${String(Date.now()).slice(-4)}`;
const QA_LOTTERY_NAME = process.env.LOTTERYNET_READINESS_LOTTERY_NAME || `QA Readiness ${String(Date.now()).slice(-4)}`;
const OUTPUT_DIR = new URL("./readiness-artifacts/", import.meta.url);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryFile = new URL(`production-readiness-timing-summary-${stamp}.json`, OUTPUT_DIR);
const logFile = new URL(`production-readiness-timing-${stamp}.log`, OUTPUT_DIR);

const lines = [];
const checks = [];
const warnings = [];
const timings = {};
const httpEvents = [];
const cleanup = [];

let admin = null;
let cashier1 = null;
let cashier2 = null;
let supervisor = null;
let adminSession = null;
let cashier1Session = null;
let cashier2Session = null;
let supervisorSession = null;

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

function redact(value) {
  return clean(value)
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[jwt]")
    .replace(/sb_publishable_[A-Za-z0-9_-]+/g, "[publishable-key]");
}

function addCheck(condition, label, data = {}) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  log(`${ok ? "PASS" : "BUG"} ${label}`, data);
  return ok;
}

function addWarning(condition, label, data = {}) {
  if (condition) return false;
  warnings.push({ label, data });
  log(`WARN ${label}`, data);
  return true;
}

function recordTiming(label, elapsedMs) {
  if (!timings[label]) timings[label] = [];
  timings[label].push(elapsedMs);
}

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))];
}

function timingSummary() {
  return Object.fromEntries(Object.entries(timings).map(([label, values]) => [
    label,
    {
      calls: values.length,
      minMs: Math.min(...values),
      p50Ms: percentile(values, 0.5),
      p95Ms: percentile(values, 0.95),
      maxMs: Math.max(...values),
    },
  ]));
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
  let response;
  let text = "";
  try {
    response = await fetch(url, {
      method,
      headers: headers(token, extraHeaders),
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });
    text = await response.text();
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - started);
    recordTiming(label, elapsedMs);
    const event = { label, status: "NETWORK_ERROR", ok: false, elapsedMs, message: redact(error?.message) };
    httpEvents.push(event);
    log(`HTTP ${label}`, event);
    return { ...event, json: null, text: clean(error?.message) };
  }
  const elapsedMs = Math.round(performance.now() - started);
  recordTiming(label, elapsedMs);
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  const event = {
    label,
    status: response.status,
    ok: response.ok,
    elapsedMs,
    message: redact(json?.message ?? json?.error ?? "").slice(0, 180),
  };
  httpEvents.push(event);
  log(`HTTP ${label}`, event);
  return { ...event, json, text };
}

function edge(slug, body = {}, token = API_KEY, label = slug) {
  return requestJson(label, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
}

function rest(label, table, query = "select=*&limit=1") {
  return requestJson(label, "GET", `${SUPABASE_URL}/rest/v1/${table}?${query}`, undefined, API_KEY);
}

function toDrDayKey(value) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(value).replaceAll("/", "-");
}

function toIsoDate(value) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(value);
}

function dayKeyToIso(dayKey) {
  const [day, month, year] = clean(dayKey).split("-");
  return `${year}-${month}-${day}`;
}

function parseCredentials(text) {
  const trimmed = text.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    const parsed = JSON.parse(trimmed);
    return Array.isArray(parsed) ? parsed : (Array.isArray(parsed.accounts) ? parsed.accounts : []);
  }
  const blockRows = [...trimmed.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
  const looseRows = [...trimmed.matchAll(/id\s+([^\s]+)\s+contrase(?:ñ|n)a\s+([^\s]+)/gi)].map((match) => ({
    username: clean(match[1]),
    password: clean(match[2]),
  }));
  const lineRows = trimmed
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map(parseCredentialLine)
    .filter(Boolean);
  const byUser = new Map();
  for (const row of [...blockRows, ...looseRows, ...lineRows]) {
    if (row?.username && row?.password) byUser.set(lower(row.username), row);
  }
  return [...byUser.values()];
}

function parseCredentialLine(line) {
  const username = readField(line, ["usuario", "user", "username", "login"]) || readPair(line, 0);
  const password = readField(line, ["contraseña", "contrasena", "clave", "password", "pass"]) || readPair(line, 1);
  if (!username || !password) return null;
  return { username, password };
}

function readField(line, names) {
  for (const name of names) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = line.match(new RegExp(`(?:^|[\\s,;|])${escaped}\\s*[:=]\\s*([^\\s,;|]+)`, "i"));
    if (match?.[1]) return clean(match[1]).replace(/^["']|["']$/g, "");
  }
  return null;
}

function readPair(line, index) {
  const parts = line
    .split(/\s*(?:\/|\||,|;)\s*/)
    .map((part) => part.trim().replace(/^["']|["']$/g, ""))
    .filter(Boolean);
  if (parts.length >= 2) return parts[index];
  return null;
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
    [account.user, account.username, account.id, account.userId, account.authUserId, account.auth_user_id]
      .some((value) => lower(value) === needle)
  );
}

function roleOf(account) {
  const role = lower(account?.role);
  return role === "cashier" ? "cajero" : role;
}

function accountKeys(account) {
  return [account?.id, account?.user, account?.username, account?.adminId, account?.adminUser]
    .map(clean)
    .filter(Boolean);
}

async function readableCredentialFile() {
  for (const candidate of [CREDENTIAL_FILE, FALLBACK_SECRET_FILE]) {
    try {
      const path = typeof candidate === "string" ? candidate : candidate;
      await access(path, constants.R_OK);
      return path;
    } catch {
      // Try the next configured credential path.
    }
  }
  return null;
}

async function fetchUsersPayload() {
  const result = await edge("lotterynet-users-state", { action: "fetch" }, API_KEY, "users-state edge fetch");
  if (!result.ok || result.json?.ok === false) throw new Error(`No se pudo leer usuarios por Edge: ${result.text}`);
  return result.json?.payload ?? {};
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password }, API_KEY, `login ${username}`);
  return {
    username,
    ok: result.json?.ok === true && Boolean(clean(result.json?.accessToken)),
    token: clean(result.json?.accessToken),
    refreshToken: clean(result.json?.refreshToken),
    user: result.json?.user ?? null,
    result,
  };
}

async function fetchMaster(key, token = API_KEY, label = `master fetch ${key}`) {
  const result = await edge("get-master-config", { action: "fetch", key }, token, label);
  return { ok: result.ok && result.json?.ok !== false, payload: result.json?.payload, updatedAt: result.json?.updatedAt, result };
}

async function saveMaster(key, payload, token, label = `master save ${key}`) {
  return edge("update-master-config", { key, payload }, token, label);
}

function systemModeWithBlock(original, block) {
  const base = original && typeof original === "object" && !Array.isArray(original) ? { ...original } : {};
  const rows = Array.isArray(base.blockedSalePlays) ? [...base.blockedSalePlays] : [];
  const withoutProbe = rows.filter((row) => !(lower(row?.playType) === "q" && clean(row?.number) === "03"));
  return {
    ...base,
    configured: true,
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    cashierPickEnabled: true,
    blockedSalePlays: block ? [...withoutProbe, { playType: "Q", number: "03", source: "qa-readiness", updatedAt: Date.now() }] : withoutProbe,
    updatedAt: Date.now(),
  };
}

function play(playType, number, amount, lotteryId = QA_LOTTERY_ID, lotteryName = QA_LOTTERY_NAME) {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName };
}

function ticketBody(label, actor, plays) {
  const clientRequestId = `${RUN_ID}-${label}`;
  return {
    clientRequestId,
    localTicketId: clientRequestId,
    adminKey: admin.id,
    adminId: admin.id,
    actorKey: actor.user,
    actorId: actor.id,
    actorRole: roleOf(actor),
    cashierKey: actor.user,
    cashierId: actor.id,
    drawDate: QA_ISO,
    dayKey: QA_DAY_KEY,
    lotteryName: plays[0]?.lotteryName,
    phoneTime: new Date().toISOString(),
    plays,
  };
}

async function createTicket(label, session, actor, plays) {
  const body = ticketBody(label, actor, plays);
  const result = await edge("create-ticket-v2", body, session.token, `create-ticket ${label}`);
  if (result.json?.ok === true) cleanup.push({ body, session, actor });
  return { body, result };
}

async function voidTicket(ticket, session = cashier1Session, actor = cashier1) {
  return edge("void-ticket", {
    actorKey: actor.user,
    adminKey: admin.id,
    cashierKey: ticket.body.cashierKey,
    clientRequestId: ticket.body.clientRequestId,
    action: "delete",
    returnLimit: true,
  }, session.token, `cleanup void ${ticket.body.clientRequestId}`);
}

async function pollTicketVisible(ticket, ownerKey, token, slug, bodyBase, extractor, label) {
  const deadline = performance.now() + 6000;
  let attempts = 0;
  let latest = null;
  while (performance.now() < deadline) {
    attempts += 1;
    const result = await edge(slug, bodyBase(), token, `${label} attempt ${attempts}`);
    latest = result;
    const rows = extractor(result.json);
    if (rows.some((row) => [row.client_request_id, row.clientRequestId, row.localTicketId, row.id].some((value) => clean(value) === ticket.body.clientRequestId))) {
      return { ok: true, attempts, elapsedMs: result.elapsedMs, result };
    }
    await new Promise((resolve) => setTimeout(resolve, 350));
  }
  return { ok: false, attempts, result: latest, ownerKey };
}

async function runSecurityProbes() {
  for (const table of ["lotterynet_users_state", "lotterynet_kv", "tickets", "result_draws"]) {
    if (!SECURITY_PROBES) {
      addCheck(true, `probe REST directo omitido para ${table}`, { env: "LOTTERYNET_SECURITY_PROBES=1" });
      continue;
    }
    const result = await rest(`direct REST blocked ${table}`, table, table === "lotterynet_users_state" ? "select=payload&limit=1" : "select=*&limit=1");
    addCheck([401, 403].includes(Number(result.status)), `tabla ${table} cerrada por REST publico`, { status: result.status, elapsedMs: result.elapsedMs });
  }
  const upsert = await edge("lotterynet-users-state", { action: "upsert", payload: { qa: RUN_ID } }, API_KEY, "users-state anonymous upsert blocked");
  addCheck([401, 403].includes(Number(upsert.status)) || upsert.json?.ok === false, "users-state upsert anonimo bloqueado", {
    status: upsert.status,
    message: upsert.message,
  });
}

async function runReadProbes() {
  for (let index = 0; index < LOOPS; index += 1) {
    await edge("lotterynet-users-state", { action: "fetch" }, API_KEY, "users-state edge fetch loop");
    await fetchMaster(`system_modes:${admin.id}`, API_KEY, "master system_modes fetch loop");
    await fetchMaster(`cashier_limits:${admin.id}`, API_KEY, "master cashier_limits fetch loop");
    await fetchMaster(`manual_disabled_lotteries:${admin.id}`, API_KEY, "master disabled lotteries fetch loop");
    await edge("get-results-status", { dayKey: TODAY_DAY_KEY }, adminSession.token, "results status edge loop");
    await edge("get-results-v2", { dayKey: TODAY_DAY_KEY }, adminSession.token, "results full edge loop");
    await edge("get-ticket-list", { action: "updated-at", ownerKey: admin.id }, API_KEY, "ticket updated-at anonymous loop");
    await edge("get-ticket-delta", { ownerKey: admin.id, sinceCursor: "2000-01-01T00:00:00.000Z", limit: 120, includeItems: false }, adminSession.token, "ticket delta admin loop");
    await edge("get-ticket-list", { action: "fetch", ownerKey: admin.id, fromDate: TODAY_ISO, toDate: TODAY_ISO, limit: 120, preferSnapshot: true }, adminSession.token, "ticket list admin loop");
    await edge("get-ticket-summary", { ownerKey: admin.id, dayKey: TODAY_DAY_KEY }, adminSession.token, "ticket summary admin loop");
    await edge("get-admin-report", { actorKey: admin.user, adminKey: admin.id, from: TODAY_ISO, to: TODAY_ISO }, adminSession.token, "admin report loop");
    await edge("get-cashier-report", { actorKey: cashier1.user, adminKey: admin.id, cashierKey: cashier1.user, from: TODAY_ISO, to: TODAY_ISO }, cashier1Session.token, "cashier report loop");
    if (cashier2Session) {
      await edge("get-ticket-delta", { ownerKey: cashier2.user, sinceCursor: "2000-01-01T00:00:00.000Z", limit: 60, includeItems: false }, cashier2Session.token, "ticket delta cashier2 loop");
    }
    if (supervisorSession && supervisor) {
      await edge("get-supervisor-report", { actorKey: supervisor.user, adminKey: admin.id, supervisorKey: supervisor.user, from: TODAY_ISO, to: TODAY_ISO }, supervisorSession.token, "supervisor report loop");
    }
  }
}

async function runWriteProbe() {
  if (!WRITE_PROBE) {
    addCheck(true, "prueba de escritura omitida por configuracion", { env: "LOTTERYNET_READINESS_WRITE=1" });
    return;
  }

  const systemKey = `system_modes:${admin.id}`;
  const original = await fetchMaster(systemKey, API_KEY, "master system_modes original");
  addCheck(original.ok, "configuracion system_modes se lee antes del rollback", { key: systemKey });
  const originalPayload = original.payload ?? null;

  try {
    const openConfig = systemModeWithBlock(originalPayload, false);
    const openSave = await saveMaster(systemKey, openConfig, adminSession.token, "master system_modes open for write probe");
    addCheck(openSave.ok && openSave.json?.ok !== false, "admin puede guardar modos por Edge", { status: openSave.status });

    const valid = await createTicket("valid", cashier1Session, cashier1, [play("Q", "41", 1)]);
    addCheck(valid.result.json?.ok === true, "ticket QA valido se crea por create-ticket-v2", {
      status: valid.result.status,
      code: valid.result.json?.ticket?.ticket_code ?? valid.result.json?.ticketCode,
      elapsedMs: valid.result.elapsedMs,
    });

    if (valid.result.json?.ok === true) {
      const deltaVisible = await pollTicketVisible(
        valid,
        admin.id,
        adminSession.token,
        "get-ticket-delta",
        () => ({ ownerKey: admin.id, sinceCursor: "2000-01-01T00:00:00.000Z", limit: 200, includeItems: false }),
        (json) => Array.isArray(json?.tickets) ? json.tickets : [],
        "ticket visible delta admin",
      );
      addCheck(deltaVisible.ok, "ticket aparece en delta de admin", { attempts: deltaVisible.attempts });

      const listVisible = await pollTicketVisible(
        valid,
        admin.id,
        adminSession.token,
        "get-ticket-list",
        () => ({ action: "fetch", ownerKey: admin.id, dayKey: QA_DAY_KEY, limit: 200 }),
        (json) => json?.payload?.tickets ?? json?.tickets ?? [],
        "ticket visible list admin",
      );
      addCheck(listVisible.ok, "ticket aparece en seccion Tickets por lista", { attempts: listVisible.attempts });
    }

    const blockedConfig = systemModeWithBlock(openConfig, true);
    const blockSave = await saveMaster(systemKey, blockedConfig, adminSession.token, "master system_modes block Q03");
    addCheck(blockSave.ok && blockSave.json?.ok !== false, "admin guarda bloqueo de numero Q 03", { status: blockSave.status });
    const blockFetched = await fetchMaster(systemKey, API_KEY, "master system_modes fetch after block");
    const blockedRows = Array.isArray(blockFetched.payload?.blockedSalePlays) ? blockFetched.payload.blockedSalePlays : [];
    addCheck(blockedRows.some((row) => lower(row.playType) === "q" && clean(row.number) === "03"), "servidor recuerda bloqueo Q 03 al leer limpio", {
      rows: blockedRows.length,
    });

    const blockedTicket = await createTicket("blocked-q03", cashier1Session, cashier1, [play("Q", "03", 1)]);
    addCheck(Number(blockedTicket.result.status) === 409 && blockedTicket.result.json?.ok === false, "cajero no puede vender numero bloqueado", {
      status: blockedTicket.result.status,
      message: blockedTicket.result.message,
    });

    if (cashier2Session && cashier2) {
      const cashierForbidden = await saveMaster(systemKey, blockedConfig, cashier2Session.token, "cashier forbidden system mode save");
      addCheck([401, 403].includes(Number(cashierForbidden.status)) || cashierForbidden.json?.ok === false, "cajero no puede cambiar configuracion admin", {
        status: cashierForbidden.status,
        message: cashierForbidden.message,
      });
    }
  } finally {
    if (originalPayload !== null) {
      const restored = await saveMaster(systemKey, originalPayload, adminSession.token, "master system_modes restore original");
      addCheck(restored.ok && restored.json?.ok !== false, "rollback system_modes restaurado", { status: restored.status });
    }
    for (const ticket of cleanup) {
      const deleted = await voidTicket(ticket);
      addCheck(deleted.json?.ok === true || [200, 204].includes(Number(deleted.status)), "ticket QA limpiado", {
        clientRequestId: ticket.body.clientRequestId,
        status: deleted.status,
        message: deleted.message,
      });
    }
  }
}

async function commandExists(command) {
  try {
    await execFileAsync(command, ["version"], { timeout: 8000 });
    return true;
  } catch {
    return false;
  }
}

async function resolveAdbCommand() {
  const candidates = [
    process.env.ADB,
    process.env.ANDROID_HOME ? `${process.env.ANDROID_HOME}/platform-tools/adb.exe` : "",
    process.env.ANDROID_SDK_ROOT ? `${process.env.ANDROID_SDK_ROOT}/platform-tools/adb.exe` : "",
    process.env.LOCALAPPDATA ? `${process.env.LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe` : "",
    "adb",
  ].map(clean).filter(Boolean);
  for (const candidate of candidates) {
    try {
      if (candidate !== "adb") await access(candidate, constants.X_OK).catch(() => access(candidate, constants.R_OK));
      await execFileAsync(candidate, ["version"], { timeout: 8000 });
      return candidate;
    } catch {
      // Try the next common Android SDK location.
    }
  }
  return null;
}

function parseLaunchTime(output) {
  const total = output.match(/TotalTime:\s*(\d+)/i)?.[1];
  const wait = output.match(/WaitTime:\s*(\d+)/i)?.[1];
  return {
    totalTimeMs: total ? Number(total) : null,
    waitTimeMs: wait ? Number(wait) : null,
  };
}

async function runAppProbe() {
  if (!APP_PROBE) {
    addCheck(true, "prueba ADB omitida por configuracion", { env: "LOTTERYNET_READINESS_APP=0" });
    return;
  }
  const adb = await resolveAdbCommand();
  if (!adb) {
    addWarning(false, "ADB no esta disponible; no se midio pintura real en celular");
    return;
  }
  const devices = await execFileAsync(adb, ["devices"], { timeout: 10000 }).catch((error) => ({ stdout: "", stderr: error?.message ?? "" }));
  const connected = devices.stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => /\tdevice$/.test(line));
  if (connected.length === 0) {
    addWarning(false, "no hay celular ADB conectado en estado device", { adb: devices.stdout.trim() });
    return;
  }

  const component = "com.lotterynet.pro/.ui.login.LoginActivity";
  await execFileAsync(adb, ["shell", "am", "force-stop", "com.lotterynet.pro"], { timeout: 10000 }).catch(() => null);
  const launched = await execFileAsync(adb, ["shell", "am", "start", "-W", "-n", component], { timeout: 20000 });
  const launch = parseLaunchTime(`${launched.stdout}\n${launched.stderr}`);
  if (launch.totalTimeMs !== null) recordTiming("android app launch TotalTime", launch.totalTimeMs);
  if (launch.waitTimeMs !== null) recordTiming("android app launch WaitTime", launch.waitTimeMs);
  addCheck(launch.totalTimeMs !== null, "ADB midio arranque de app", launch);

  await new Promise((resolve) => setTimeout(resolve, 1800));
  const dump = await execFileAsync(adb, ["exec-out", "uiautomator", "dump", "/dev/tty"], { timeout: 15000 }).catch((error) => ({ stdout: "", stderr: error?.message ?? "" }));
  const xml = `${dump.stdout}\n${dump.stderr}`;
  const hasPackage = xml.includes("com.lotterynet.pro");
  const hasVisibleText = /text=\"[^\"]{2,}\"/.test(xml);
  addCheck(hasPackage && hasVisibleText, "pantalla renderiza contenido visible despues del arranque", {
    packageFound: hasPackage,
    textFound: hasVisibleText,
    xmlBytes: xml.length,
  });
}

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true });
  log("Inicio production readiness timing", {
    runId: RUN_ID,
    loops: LOOPS,
    writeProbe: WRITE_PROBE,
    appProbe: APP_PROBE,
    todayDayKey: TODAY_DAY_KEY,
    qaDayKey: QA_DAY_KEY,
    node: process.version,
  });

  const credentialPath = await readableCredentialFile();
  addCheck(Boolean(credentialPath), "archivo privado de credenciales disponible", {
    path: credentialPath ? String(credentialPath).replace(/contrase.+$/i, "[redacted]") : null,
  });
  if (!credentialPath) throw new Error("Falta archivo de credenciales de prueba.");

  const credentials = parseCredentials(await readFile(credentialPath, "utf8"));
  addCheck(credentials.length > 0, "credenciales de prueba legibles", { count: credentials.length });
  const payload = await fetchUsersPayload();
  admin = findAccount(payload, "podero02");
  cashier1 = findAccount(payload, "bancae01");
  cashier2 = findAccount(payload, "bancae02");
  supervisor = allAccounts(payload).find((account) => roleOf(account) === "supervisor" && account.adminId === admin?.id);
  addCheck(Boolean(admin && cashier1), "usuarios base admin/cajero existen en servidor", {
    adminId: admin?.id,
    adminUser: admin?.user,
    cashierId: cashier1?.id,
    cashierUser: cashier1?.user,
    cashier2: Boolean(cashier2),
    supervisor: supervisor?.user ?? null,
  });
  if (!admin || !cashier1) throw new Error("Faltan podero02 o bancae01 en users-state.");

  const adminCred = credentials.find((entry) => lower(entry.username) === "podero02");
  const cashier1Cred = credentials.find((entry) => lower(entry.username) === "bancae01");
  const cashier2Cred = credentials.find((entry) => lower(entry.username) === "bancae02");
  const supervisorCred = supervisor ? credentials.find((entry) => lower(entry.username) === lower(supervisor.user)) : null;
  addCheck(Boolean(adminCred && cashier1Cred), "credenciales admin/cajero disponibles", {
    admin: Boolean(adminCred),
    cashier1: Boolean(cashier1Cred),
    cashier2: Boolean(cashier2Cred),
    supervisor: Boolean(supervisorCred),
  });
  if (!adminCred || !cashier1Cred) throw new Error("Faltan credenciales de podero02 o bancae01.");

  adminSession = await login(adminCred.username, adminCred.password);
  cashier1Session = await login(cashier1Cred.username, cashier1Cred.password);
  cashier2Session = cashier2Cred && cashier2 ? await login(cashier2Cred.username, cashier2Cred.password) : null;
  supervisorSession = supervisorCred ? await login(supervisorCred.username, supervisorCred.password) : null;
  addCheck(adminSession.ok && cashier1Session.ok, "login real admin/cajero OK", {
    adminStatus: adminSession.result.status,
    cashierStatus: cashier1Session.result.status,
    cashier2Ok: cashier2Session?.ok ?? null,
    supervisorOk: supervisorSession?.ok ?? null,
  });
  if (!adminSession.ok || !cashier1Session.ok) throw new Error("Login base fallo.");

  addCheck(accountKeys(admin).some((key) => key === admin.id) && accountKeys(cashier1).some((key) => key === cashier1.user), "IDs y aliases base son resolubles", {
    adminKeys: accountKeys(admin),
    cashierKeys: accountKeys(cashier1),
  });

  await runSecurityProbes();
  await runReadProbes();
  await runWriteProbe();
  await runAppProbe();

  const summary = timingSummary();
  const thresholds = {
    "users-state edge fetch loop": 2500,
    "master system_modes fetch loop": 1800,
    "results status edge loop": 2500,
    "results full edge loop": 4000,
    "ticket delta admin loop": 900,
    "ticket list admin loop": 1500,
    "admin report loop": 3500,
    "cashier report loop": 3500,
    "android app launch TotalTime": 3500,
  };
  for (const [label, maxP95] of Object.entries(thresholds)) {
    const actual = summary[label]?.p95Ms;
    if (actual !== null && actual !== undefined) {
      addWarning(actual <= maxP95, `p95 alto en ${label}`, { p95Ms: actual, thresholdMs: maxP95 });
    }
  }
}

try {
  await main();
} catch (error) {
  addCheck(false, "suite interrumpida", { message: redact(error?.message), stack: redact(error?.stack).slice(0, 800) });
} finally {
  const summary = {
    ok: checks.every((item) => item.ok),
    warningCount: warnings.length,
    runId: RUN_ID,
    checkedAt: new Date().toISOString(),
    supabaseUrl: SUPABASE_URL,
    loops: LOOPS,
    writeProbe: WRITE_PROBE,
    appProbe: APP_PROBE,
    todayDayKey: TODAY_DAY_KEY,
    qaDayKey: QA_DAY_KEY,
    timings: timingSummary(),
    checks,
    warnings,
    httpEvents,
    logFile: decodeURIComponent(logFile.pathname),
  };
  await mkdir(OUTPUT_DIR, { recursive: true });
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (!summary.ok) process.exit(1);
}
