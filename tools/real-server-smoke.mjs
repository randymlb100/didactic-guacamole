import fs from "node:fs";
import path from "node:path";

const BASE = "https://unhoulkujbtsypccpirc.supabase.co";
const KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const root = process.cwd();
const credentialsPath = path.join(root, "tools", "real-test-credentials.local.json");
const textCredentialsPath = process.env.LOTTERYNET_CREDENTIAL_FILE || path.join(root, "contraseña de prueba.txt");

function headers(token = KEY) {
  return {
    apikey: KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    Accept: "application/json",
  };
}

function redactSecrets(value) {
  return String(value || "")
    .replace(/"(accessToken|refreshToken|access_token|refresh_token)"\s*:\s*"[^"]+"/gi, "\"$1\":\"[redacted]\"")
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[redacted-jwt]");
}

async function callFunction(slug, body = {}, token = KEY) {
  const started = Date.now();
  try {
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
      message: redactSecrets(json?.message || json?.error || text || "").slice(0, 180),
      json,
    };
  } catch (error) {
    return {
      slug,
      status: "NETWORK_ERROR",
      ms: Date.now() - started,
      message: redactSecrets(error?.message || error).slice(0, 180),
    };
  }
}

async function refreshSession(refreshToken) {
  const started = Date.now();
  const res = await fetch(`${BASE}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: {
      apikey: KEY,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ refresh_token: refreshToken }),
  });
  const json = await res.json().catch(() => null);
  return {
    status: res.status,
    ms: Date.now() - started,
    hasAccessToken: Boolean(json?.access_token),
    hasRefreshToken: Boolean(json?.refresh_token),
    message: String(json?.msg || json?.message || json?.error_description || json?.error || "").slice(0, 180),
  };
}

function readCredentials() {
  if (fs.existsSync(credentialsPath)) {
    const parsed = JSON.parse(fs.readFileSync(credentialsPath, "utf8"));
    return {
      source: credentialsPath,
      accounts: Array.isArray(parsed.accounts) ? parsed.accounts : [],
    };
  }

  if (fs.existsSync(textCredentialsPath)) {
    return {
      source: textCredentialsPath,
      accounts: parseTextCredentials(fs.readFileSync(textCredentialsPath, "utf8")),
    };
  }

  return null;
}

function parseTextCredentials(raw) {
  const text = raw.trim();
  if (!text) return [];

  if (text.startsWith("{") || text.startsWith("[")) {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed : (Array.isArray(parsed.accounts) ? parsed.accounts : []);
  }

  const pairedCredentials = parseUsuarioClaveBlocks(text);
  if (pairedCredentials.length) return pairedCredentials;

  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map(parseCredentialLine)
    .filter(Boolean);
}

function parseUsuarioClaveBlocks(text) {
  return [...text.matchAll(/Usuario:\s*([^\r\n]+)\s*[\r\n]+Clave:\s*([^\r\n]+)/gi)].map((match) => {
    const username = cleanToken(match[1]);
    return {
      role: inferRole(username),
      username,
      password: cleanToken(match[2]),
    };
  });
}

function parseCredentialLine(line) {
  const roleMatch = line.match(/\b(master|admin|administrador|supervisor|cajero|cashier)\b/i);
  const role = normalizeRole(roleMatch?.[1]);
  const username = readField(line, ["usuario", "user", "username", "login"]) || readSentenceUsername(line) || readPair(line, 0);
  const password = readField(line, ["contraseña", "contrasena", "clave", "password", "pass"]) ||
    readLooseField(line, ["contraseña", "contrasena", "clave", "password", "pass"]) ||
    readPair(line, 1);
  if (!username || !password) return null;
  return {
    role: role || inferRole(username),
    username,
    password,
    adminKey: readField(line, ["adminKey", "admin"]),
    cashierKey: readField(line, ["cashierKey", "cajeroKey"]),
    supervisorKey: readField(line, ["supervisorKey"]),
  };
}

function readField(line, names) {
  for (const name of names) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = line.match(new RegExp(`(?:^|[\\s,;|])${escaped}\\s*[:=]\\s*([^\\s,;|]+)`, "i"));
    if (match?.[1]) return cleanToken(match[1]);
  }
  return null;
}

function readLooseField(line, names) {
  const tokens = words(line);
  const index = tokens.findIndex((token) => names.some((name) => token.toLowerCase() === name.toLowerCase()));
  return index >= 0 ? tokens[index + 1] || null : null;
}

function readSentenceUsername(line) {
  const tokens = words(line);
  const passwordIndex = tokens.findIndex((token) => ["contraseña", "contrasena", "clave", "password", "pass"].includes(token.toLowerCase()));
  if (passwordIndex > 0) return tokens[passwordIndex - 1];
  return null;
}

function readPair(line, index) {
  const parts = line
    .split(/\s*(?:\/|\||,|;)\s*/)
    .map(cleanToken)
    .filter(Boolean);
  if (parts.length >= 2) return parts[index];

  const tokens = line
    .split(/\s+/)
    .map(cleanToken)
    .filter((token) => token && !["master", "admin", "administrador", "supervisor", "cajero", "cashier"].includes(token.toLowerCase()));
  return tokens.length >= 2 ? tokens[index] : null;
}

function words(line) {
  return line
    .split(/\s+/)
    .map(cleanToken)
    .filter(Boolean);
}

function cleanToken(value) {
  return String(value || "").trim().replace(/^["']|["']$/g, "");
}

function normalizeRole(value) {
  const role = String(value || "").toLowerCase();
  if (role === "administrador") return "admin";
  if (role === "cashier") return "cashier";
  if (["master", "admin", "supervisor", "cajero"].includes(role)) return role;
  return null;
}

function inferRole(username) {
  const name = String(username || "").toLowerCase();
  if (name.includes("master")) return "master";
  if (name.includes("super")) return "supervisor";
  if (name.includes("caj") || name.includes("cash")) return "cashier";
  if (name.includes("admin")) return "admin";
  return "unknown";
}

function reportPayloadFor(account) {
  const today = new Date().toISOString().slice(0, 10);
  const role = String(account.role || "").toLowerCase();
  if (role === "supervisor") {
    return {
      slug: "get-supervisor-report",
      body: {
        actorKey: account.username,
        adminKey: account.adminKey,
        supervisorKey: account.supervisorKey || account.username,
        from: today,
        to: today,
      },
    };
  }
  if (role === "cashier") {
    return {
      slug: "get-cashier-report",
      body: {
        actorKey: account.username,
        adminKey: account.adminKey,
        cashierKey: account.cashierKey || account.username,
        from: today,
        to: today,
      },
    };
  }
  return {
    slug: "get-admin-report",
    body: {
      actorKey: account.username,
      adminKey: account.adminKey || account.username,
      from: today,
      to: today,
    },
  };
}

async function runAccount(account) {
  const login = await callFunction("auth-legacy-login", {
    username: account.username,
    password: account.password,
  });
  const accessToken = login.json?.accessToken || login.json?.session?.access_token;
  const refreshToken = login.json?.refreshToken || login.json?.session?.refresh_token;
  const result = {
    role: account.role,
    username: account.username,
    login: {
      status: login.status,
      ok: login.json?.ok,
      hasAccessToken: Boolean(accessToken),
      hasRefreshToken: Boolean(refreshToken),
      message: accessToken ? "Login OK" : login.message,
    },
    refresh: null,
    protectedChecks: [],
  };
  if (!accessToken) return result;
  if (refreshToken) {
    result.refresh = await refreshSession(refreshToken);
  }
  result.protectedChecks.push(await callFunction("get-results-status", { date: "19-05-2026" }, accessToken));
  const report = reportPayloadFor(account);
  result.protectedChecks.push(await callFunction(report.slug, report.body, accessToken));
  return result;
}

const credentials = readCredentials();
if (!credentials) {
  console.log(JSON.stringify({
    ok: false,
    message: `Crea ${credentialsPath} copiando tools/real-test-credentials.local.example.json, o escribe usuario/contraseña en ${textCredentialsPath}.`,
    node: process.version,
    pythonBundled: "C:\\Users\\Randy Cordero\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\python\\python.exe",
  }, null, 2));
  process.exit(2);
}

const output = {
  ok: true,
  checkedAt: new Date().toISOString(),
  node: process.version,
  credentialsSource: credentials.source,
  accounts: [],
};

if (credentials.accounts.length === 0) {
  console.log(JSON.stringify({
    ...output,
    ok: false,
    message: "El archivo de contraseñas existe, pero no tiene cuentas legibles.",
  }, null, 2));
  process.exit(2);
}

for (const account of credentials.accounts) {
  output.accounts.push(await runAccount(account));
}

for (const account of output.accounts) {
  for (const check of account.protectedChecks) {
    delete check.json;
  }
}

console.log(JSON.stringify(output, null, 2));
