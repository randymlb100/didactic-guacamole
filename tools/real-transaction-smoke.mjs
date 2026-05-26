import fs from "node:fs";
import path from "node:path";

const BASE = "https://unhoulkujbtsypccpirc.supabase.co";
const KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const root = process.cwd();
const textCredentialsPath = process.env.LOTTERYNET_CREDENTIAL_FILE || path.join(root, "contraseña de prueba.txt");

function headers(token = KEY) {
  return {
    apikey: KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    Accept: "application/json",
  };
}

function cleanToken(value) {
  return String(value || "").trim().replace(/^["']|["']$/g, "");
}

function words(line) {
  return line.split(/\s+/).map(cleanToken).filter(Boolean);
}

function readLooseField(line, names) {
  const tokens = words(line);
  const index = tokens.findIndex((token) => names.some((name) => token.toLowerCase() === name.toLowerCase()));
  return index >= 0 ? tokens[index + 1] || null : null;
}

function readSentenceUsername(line) {
  const tokens = words(line);
  const passwordIndex = tokens.findIndex((token) => ["contraseña", "contrasena", "clave", "password", "pass"].includes(token.toLowerCase()));
  return passwordIndex > 0 ? tokens[passwordIndex - 1] : null;
}

function readField(line, names) {
  for (const name of names) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = line.match(new RegExp(`(?:^|[\\s,;|])${escaped}\\s*[:=]\\s*([^\\s,;|]+)`, "i"));
    if (match?.[1]) return cleanToken(match[1]);
  }
  return null;
}

function normalizeRole(value) {
  const role = String(value || "").toLowerCase();
  if (role === "administrador") return "admin";
  if (role === "cashier") return "cajero";
  if (["master", "admin", "supervisor", "cajero"].includes(role)) return role;
  return null;
}

function parseCredentialLine(line) {
  const roleMatch = line.match(/\b(master|admin|administrador|supervisor|cajero|cashier)\b/i);
  const username = readField(line, ["usuario", "user", "username", "login"]) || readSentenceUsername(line);
  const password = readField(line, ["contraseña", "contrasena", "clave", "password", "pass"]) ||
    readLooseField(line, ["contraseña", "contrasena", "clave", "password", "pass"]);
  if (!username || !password) return null;
  return {
    role: normalizeRole(roleMatch?.[1]) || "unknown",
    username,
    password,
  };
}

function readCredentials() {
  const raw = fs.readFileSync(textCredentialsPath, "utf8").trim();
  const pairedCredentials = [...raw.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => {
    const username = cleanToken(match[1]);
    return {
      role: normalizeRole(username === "podero02" ? "admin" : username.startsWith("banca") ? "cajero" : username),
      username,
      password: cleanToken(match[2]),
    };
  });
  if (pairedCredentials.length) return pairedCredentials;
  return raw.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map(parseCredentialLine).filter(Boolean);
}

function redact(value) {
  return String(value || "")
    .replace(/"(accessToken|refreshToken|access_token|refresh_token)"\s*:\s*"[^"]+"/gi, "\"$1\":\"[redacted]\"")
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[redacted-jwt]")
    .slice(0, 220);
}

async function callFunction(slug, body, token = KEY) {
  const started = Date.now();
  const res = await fetch(`${BASE}/functions/v1/${slug}`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return {
    slug,
    status: res.status,
    ms: Date.now() - started,
    ok: json?.ok,
    message: redact(json?.message || json?.error || text),
    json,
  };
}

async function login(account) {
  const res = await callFunction("auth-legacy-login", {
    username: account.username,
    password: account.password,
  });
  const accessToken = res.json?.accessToken || res.json?.session?.access_token;
  return {
    account: {
      role: res.json?.user?.role || account.role,
      username: res.json?.user?.username || account.username,
      userId: res.json?.user?.id || account.username,
      adminId: res.json?.user?.adminId || null,
      adminUser: res.json?.user?.adminUser || null,
      banca: res.json?.user?.banca || null,
    },
    accessToken,
    status: res.status,
    ok: Boolean(accessToken),
    message: accessToken ? "Login OK" : res.message,
  };
}

function ticketPayload(session, label) {
  const now = Date.now();
  const dayKey = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  const localTicketId = `real-smoke-${label}-${now}`;
  const adminKey = session.role === "admin"
    ? session.userId
    : (session.adminId || session.adminUser || session.userId);
  const cashierKey = session.role === "admin" ? session.userId : session.userId;
  return {
    clientRequestId: localTicketId,
    localTicketId,
    adminKey,
    adminId: adminKey,
    actorKey: session.username,
    actorId: session.userId,
    actorRole: session.role,
    cashierKey,
    cashierId: cashierKey,
    drawDate: dayKey,
    dayKey,
    lotteryName: "La Primera Día",
    phoneTime: new Date(now).toISOString(),
    plays: [
      { playType: "Q", number: "65", amount: 25, lotteryId: "1", lotteryName: "La Primera Día" },
      { playType: "P3BOX", number: "256", amount: 25, lotteryId: "19", lotteryName: "NJ Pick 3 Dia", pickGame: "PICK3", pickMode: "BOX" },
      { playType: "P3", number: "256", amount: 25, lotteryId: "19", lotteryName: "NJ Pick 3 Dia", pickGame: "PICK3", pickMode: "STRAIGHT" },
      { playType: "P4BOX", number: "8787", amount: 25, lotteryId: "21", lotteryName: "NJ Pick 4 Dia", pickGame: "PICK4", pickMode: "BOX" },
      { playType: "P4", number: "5423", amount: 25, lotteryId: "21", lotteryName: "NJ Pick 4 Dia", pickGame: "PICK4", pickMode: "STRAIGHT" },
    ],
  };
}

function actionPayload(session, createPayload, ticketResponse, action) {
  const ticket = ticketResponse.json?.ticket || {};
  return {
    actorKey: session.userId,
    adminKey: createPayload.adminKey,
    ownerKey: createPayload.adminKey,
    cashierKey: createPayload.cashierKey,
    ticketId: ticket.id || ticket.ticketId || undefined,
    localTicketId: createPayload.localTicketId,
    clientRequestId: createPayload.clientRequestId,
    action,
    returnLimit: true,
  };
}

async function runTicketFlow(loginResult) {
  const session = loginResult.account;
  const createPayload = ticketPayload(session, session.role);
  const created = await callFunction("create-ticket-v2", createPayload, loginResult.accessToken);
  const payBeforeDelete = created.ok
    ? await callFunction("pay-ticket", actionPayload(session, createPayload, created, "pay"), loginResult.accessToken)
    : null;
  const deleted = created.ok
    ? await callFunction("void-ticket", actionPayload(session, createPayload, created, "delete"), loginResult.accessToken)
    : null;
  const payAfterDelete = created.ok
    ? await callFunction("pay-ticket", actionPayload(session, createPayload, created, "pay"), loginResult.accessToken)
    : null;
  return {
    role: session.role,
    username: session.username,
    login: { status: loginResult.status, ok: loginResult.ok, message: loginResult.message },
    createTicket: summarizeTicketCreate(created),
    print: created.ok ? { attempted: false, message: "Impresion fisica requiere Android/impresora conectada; API creo ticket imprimible." } : null,
    payBeforeDelete: summarize(payBeforeDelete),
    deleteTicket: summarize(deleted),
    payAfterDelete: summarize(payAfterDelete),
  };
}

function summarizeTicketCreate(res) {
  if (!res) return null;
  return {
    status: res.status,
    ok: Boolean(res.ok),
    message: res.ok ? "Ticket creado" : res.message,
    ticketId: res.json?.ticket?.id || res.json?.ticketId || null,
    ticketCode: res.json?.ticket?.ticket_code || res.json?.ticketCode || null,
  };
}

function summarize(res) {
  if (!res) return null;
  return {
    status: res.status,
    ok: Boolean(res.ok),
    message: res.message,
    ticketId: res.json?.ticketId || res.json?.ticket?.id || null,
    state: res.json?.state || res.json?.status || null,
    amount: res.json?.amount || null,
  };
}

const credentials = readCredentials();
const selected = credentials.filter((account) => ["admin", "cajero"].includes(account.role));
const output = {
  ok: true,
  checkedAt: new Date().toISOString(),
  source: textCredentialsPath,
  requestedPlays: ["Q 65 RD$25", "P3 256 BOX RD$25", "P3 256 STRAIGHT RD$25", "P4 8787 BOX RD$25", "P4 5423 STRAIGHT RD$25"],
  recharge: {
    skipped: true,
    message: "Recarga real omitida: falta telefono y proveedor explicitos.",
  },
  accounts: [],
};

for (const account of selected) {
  const logged = await login(account);
  output.accounts.push(logged.ok ? await runTicketFlow(logged) : {
    role: account.role,
    username: account.username,
    login: { status: logged.status, ok: false, message: logged.message },
  });
}

console.log(JSON.stringify(output, null, 2));
