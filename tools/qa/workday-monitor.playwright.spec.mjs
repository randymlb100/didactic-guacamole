import { test, expect } from "@playwright/test";
import { readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";
import path from "node:path";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const PROJECT_ROOT = new URL("../../", import.meta.url);
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE || new URL("contraseña de prueba.txt", PROJECT_ROOT);
const runId = `pwmon${Date.now()}`;
const day = String((Date.now() % 20) + 1).padStart(2, "0");
const fakeIsoDate = `2026-03-${day}`;
const fakeDayKey = `${day}-03-2026`;
const artifactStamp = new Date().toISOString().replace(/[:.]/g, "-");
const summaryPath = path.resolve("tools", "qa", `workday-monitor-playwright-summary-${artifactStamp}.json`);
const screenshotPath = path.resolve("tools", "qa", `workday-monitor-playwright-${artifactStamp}.png`);

function clean(value) {
  return String(value ?? "").trim();
}

function lower(value) {
  return clean(value).toLowerCase();
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
  return { label, status: response.status, elapsedMs, ok: response.ok, json, text };
}

async function edge(slug, body, token = API_KEY) {
  return requestJson(slug, "POST", `${SUPABASE_URL}/functions/v1/${slug}`, body, token);
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

async function fetchUsersPayload() {
  const result = await requestJson(
    "users fetch",
    "GET",
    `${SUPABASE_URL}/rest/v1/lotterynet_users_state?scope=eq.global&select=payload`,
  );
  if (!result.ok) throw new Error(`No se pudo leer usuarios: ${result.text}`);
  return result.json?.[0]?.payload ?? {};
}

async function saveUsersPayload(payload, token) {
  const result = await edge("lotterynet-users-state", { action: "upsert", payload }, token);
  if (!result.ok || result.json?.ok === false) throw new Error(`No se pudo guardar usuarios: ${result.text}`);
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return { username, ok: result.json?.ok === true && clean(result.json?.accessToken), token: result.json?.accessToken, user: result.json?.user, result };
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

async function cleanupResults() {
  await requestJson("lottery cleanup", "DELETE", `${SUPABASE_URL}/rest/v1/lotterynet_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}`).catch(() => null);
  await requestJson("pick cleanup", "DELETE", `${SUPABASE_URL}/rest/v1/lotterynet_pick_results_by_day?result_date=eq.${encodeURIComponent(fakeDayKey)}`).catch(() => null);
}

test("monitoreo muestra jugadas y cajeros por Playwright", async ({ page }) => {
  test.setTimeout(90_000);
  let originalUsersPayload = null;
  let usersChanged = false;
  let adminSession = null;
  const httpEvidence = [];

  try {
    await cleanupResults();
    const credentials = parseCredentials(await readFile(CREDENTIAL_FILE, "utf8"));
    originalUsersPayload = await fetchUsersPayload();
    const admin = findAccount(originalUsersPayload, "podero02");
    const cashier1 = findAccount(originalUsersPayload, "bancae01");
    const cashier2 = findAccount(originalUsersPayload, "bancae02");
    const adminCred = credentials.find((row) => lower(row.username) === "podero02");
    const cashierCred = credentials.find((row) => lower(row.username) === "bancae01");
    expect(admin && cashier1 && cashier2 && adminCred && cashierCred).toBeTruthy();

    adminSession = await login(adminCred.username, adminCred.password);
    const cashierSession = await login(cashierCred.username, cashierCred.password);
    expect(adminSession.ok).toBeTruthy();
    expect(cashierSession.ok).toBeTruthy();

    const supervisorSetup = installSupervisor(originalUsersPayload, admin, [cashier1, cashier2]);
    await saveUsersPayload(supervisorSetup.payload, adminSession.token);
    usersChanged = true;
    const supervisorSession = await login(supervisorSetup.supervisor.user, supervisorSetup.password);
    expect(supervisorSession.ok).toBeTruthy();

    const ticket = await createTicket(cashierSession, cashier1, admin, "monitor-ticket", [
      play("Q", "31", 3, "9981", "QA Monitor Dia"),
      play("P3", "257", 2, "9982", "QA Monitor Pick 3", { pickGame: "PICK3", pickMode: "STRAIGHT" }),
      play("P4BOX", "5423", 2, "9983", "QA Monitor Pick 4", { pickGame: "PICK4", pickMode: "BOX" }),
    ]);
    expect(ticket.result.json?.ok).toBe(true);

    await page.setContent(`<!doctype html>
      <html>
        <head>
          <meta charset="utf-8">
          <title>LotteryNet Monitor QA</title>
          <style>
            body { font-family: Arial, sans-serif; margin: 24px; background: #f6f7f9; color: #1f2933; }
            h1 { font-size: 22px; margin: 0 0 16px; }
            .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
            .card { background: white; border: 1px solid #d6dbe1; border-radius: 8px; padding: 14px; }
            .metric { font-size: 26px; font-weight: 700; margin: 6px 0; }
            pre { white-space: pre-wrap; font-size: 12px; background: #111827; color: #e5e7eb; padding: 12px; border-radius: 6px; }
            table { border-collapse: collapse; width: 100%; font-size: 13px; }
            th, td { border-bottom: 1px solid #e5e7eb; padding: 6px; text-align: left; }
          </style>
        </head>
        <body>
          <h1>LotteryNet Monitor QA ${fakeDayKey}</h1>
          <div id="status">Cargando monitoreo...</div>
          <div id="app"></div>
        </body>
      </html>`);

    const monitor = await page.evaluate(async ({ base, apiKey, adminToken, cashierToken, supervisorToken, adminId, cashierKey, supervisorKey, from, to }) => {
      async function call(slug, body, token) {
        const started = performance.now();
        const response = await fetch(`${base}/functions/v1/${slug}`, {
          method: "POST",
          headers: {
            apikey: apiKey,
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(body),
        });
        const json = await response.json();
        return { slug, status: response.status, elapsedMs: Math.round(performance.now() - started), json };
      }
      const admin = await call("get-admin-report", { actorKey: adminId, adminKey: adminId, from, to }, adminToken);
      const cashier = await call("get-cashier-report", { actorKey: cashierKey, adminKey: adminId, cashierKey, from, to }, cashierToken);
      const supervisor = await call("get-supervisor-report", { actorKey: supervisorKey, adminKey: adminId, supervisorKey, from, to }, supervisorToken);
      const delta = await call("get-ticket-delta", { ownerKey: adminId, limit: 50 }, adminToken);
      const rows = [
        ["Admin", admin.json?.summary?.tickets ?? 0, admin.json?.summary?.totalVendido ?? 0, admin.json?.summary?.comision ?? 0],
        ["Cajero", cashier.json?.summary?.tickets ?? 0, cashier.json?.summary?.totalVendido ?? 0, cashier.json?.summary?.comision ?? 0],
        ["Supervisor", supervisor.json?.summary?.tickets ?? 0, supervisor.json?.summary?.totalVendido ?? 0, supervisor.json?.summary?.comision ?? 0],
      ];
      const tickets = (delta.json?.tickets ?? []).slice(0, 8);
      document.querySelector("#status").textContent = "Monitoreo cargado";
      document.querySelector("#app").innerHTML = `
        <div class="grid">
          ${rows.map(([label, ticketsCount, vendido, comision]) => `
            <div class="card">
              <strong>${label}</strong>
              <div class="metric">${ticketsCount} tickets</div>
              <div>Vendido: ${vendido}</div>
              <div>Comision: ${comision}</div>
            </div>
          `).join("")}
        </div>
        <div class="card" style="margin-top:12px">
          <strong>Tickets recientes</strong>
          <table><thead><tr><th>Codigo</th><th>Cajero</th><th>Estado</th><th>Total</th></tr></thead>
          <tbody>${tickets.map((ticket) => `<tr><td>${ticket.ticket_code ?? ticket.client_request_id}</td><td>${ticket.cashier_key}</td><td>${ticket.status}</td><td>${ticket.total_amount}</td></tr>`).join("")}</tbody></table>
        </div>
        <pre>${JSON.stringify({ admin, cashier, supervisor, deltaCount: delta.json?.count }, null, 2)}</pre>
      `;
      return { admin, cashier, supervisor, delta };
    }, {
      base: SUPABASE_URL,
      apiKey: API_KEY,
      adminToken: adminSession.token,
      cashierToken: cashierSession.token,
      supervisorToken: supervisorSession.token,
      adminId: admin.id,
      cashierKey: cashier1.user,
      supervisorKey: supervisorSetup.supervisor.user,
      from: fakeIsoDate,
      to: fakeIsoDate,
    });

    httpEvidence.push(monitor.admin, monitor.cashier, monitor.supervisor, { slug: "get-ticket-delta", status: monitor.delta.status, count: monitor.delta.json?.count });
    await expect(page.locator("#status")).toHaveText("Monitoreo cargado");
    expect(monitor.admin.status).toBe(200);
    expect(monitor.cashier.status).toBe(200);
    expect(monitor.supervisor.status).toBe(200);
    expect(Number(monitor.admin.json?.summary?.tickets ?? 0)).toBeGreaterThan(0);
    expect(Number(monitor.cashier.json?.summary?.tickets ?? 0)).toBeGreaterThan(0);
    expect(Number(monitor.supervisor.json?.summary?.tickets ?? 0)).toBeGreaterThan(0);
    expect((monitor.delta.json?.tickets ?? []).some((row) => row.client_request_id === ticket.clientRequestId)).toBeTruthy();

    await page.screenshot({ path: screenshotPath, fullPage: true });
    await writeFile(summaryPath, JSON.stringify({
      ok: true,
      runId,
      fakeDayKey,
      screenshotPath,
      httpEvidence,
      adminSummary: monitor.admin.json?.summary,
      cashierSummary: monitor.cashier.json?.summary,
      supervisorSummary: monitor.supervisor.json?.summary,
    }, null, 2), "utf8");
  } finally {
    if (usersChanged && originalUsersPayload) {
      await saveUsersPayload(originalUsersPayload, adminSession?.token);
    }
    await cleanupResults();
  }
});
