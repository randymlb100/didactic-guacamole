import { writeFile } from "node:fs/promises";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const runId = `qafin${Date.now()}`;
const fakeIsoDate = "2026-01-16";
const fakeDayKey = "16-01-2026";
const logFile = new URL(`./finance-monitor-smoke-${new Date().toISOString().replace(/[:.]/g, "-")}.log`, import.meta.url);
const lines = [];

function log(label, data) {
  const line = `[${new Date().toISOString()}] ${label}${data === undefined ? "" : ` ${JSON.stringify(data)}`}`;
  lines.push(line);
  console.log(line);
}

function pass(condition, label, data) {
  log(`${condition ? "PASS" : "BUG"} ${label}`, data);
  return condition;
}

function headers(token = API_KEY) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    Accept: "application/json",
  };
}

async function edge(slug, body, token = API_KEY) {
  const start = performance.now();
  const response = await fetch(`${SUPABASE_URL}/functions/v1/${slug}`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(body),
  });
  const elapsedMs = Math.round(performance.now() - start);
  const text = await response.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text }; }
  log(`HTTP ${slug}`, { status: response.status, elapsedMs, ok: response.ok, message: json?.message });
  return { status: response.status, elapsedMs, ok: response.ok, json, text };
}

async function login(username, password) {
  const result = await edge("auth-legacy-login", { username, password });
  return { token: result.json?.accessToken, user: result.json?.user, result };
}

function play(playType, number, amount, lotteryId = "1", lotteryName = "La Primera Día") {
  return { playType, number, amount, potentialPayout: 0, lotteryId, lotteryName };
}

async function createTicket(session, actor, admin, label, plays, expectOk = true) {
  const clientRequestId = `${runId}-${label}`;
  const body = {
    clientRequestId,
    localTicketId: clientRequestId,
    adminKey: admin.id,
    adminId: admin.id,
    actorKey: actor.user,
    actorId: actor.id,
    actorRole: actor.role,
    cashierKey: actor.user,
    cashierId: actor.id,
    drawDate: fakeIsoDate,
    dayKey: fakeDayKey,
    lotteryName: plays[0]?.lotteryName ?? "La Primera Día",
    phoneTime: new Date().toISOString(),
    plays,
  };
  const result = await edge("create-ticket-v2", body, session.token);
  pass(expectOk ? result.json?.ok === true : result.json?.ok !== true, `venta ${label} ${expectOk ? "guardada" : "bloqueada"}`, {
    status: result.status,
    message: result.json?.message,
    code: result.json?.ticket?.ticket_code,
  });
  return { clientRequestId, body, result };
}

async function report(slug, body, token) {
  return edge(slug, { from: fakeIsoDate, to: fakeIsoDate, ...body }, token);
}

async function main() {
  log("Inicio finance/monitor smoke", { runId, fakeIsoDate, fakeDayKey });
  const adminSession = await login("podero02", "c6wfdd83");
  const cashier1 = await login("bancae01", "xtpyxreb");
  const cashier2 = await login("bancae02", "knev89dw");
  pass(Boolean(adminSession.token), "JWT admin valido", { user: adminSession.user });
  pass(Boolean(cashier1.token), "JWT cajero 1 valido", { user: cashier1.user });
  pass(Boolean(cashier2.token), "JWT cajero 2 valido", { user: cashier2.user });

  const admin = { id: "ADM-C5FFB0", user: "podero02", role: "admin" };
  const bancae01 = { id: "CAJ-E4A14B", user: "bancae01", role: "cashier" };
  const bancae02 = { id: "CAJ-6855F5", user: "bancae02", role: "cashier" };

  await createTicket(cashier1, bancae01, admin, "c1-q32-10", [play("Q", "32", 10)]);
  await createTicket(cashier1, bancae01, admin, "c1-t70", [play("T", "112232", 70)]);
  await createTicket(cashier2, bancae02, admin, "c2-q33-20", [play("Q", "33", 20)]);
  await createTicket(adminSession, admin, admin, "admin-t100", [play("T", "112232", 100)]);

  const limitBlock = await createTicket(cashier1, bancae01, admin, "c1-t-over75", [play("T", "112232", 76)], false);
  pass(limitBlock.result.json?.ok === false && [409, 500].includes(limitBlock.result.status), "limite Tripleta cajero bloquea sobre 75", {
    status: limitBlock.result.status,
    message: limitBlock.result.json?.message,
  });

  const adminReport = await report("get-admin-report", { actorKey: admin.id, adminKey: admin.id }, adminSession.token);
  pass(adminReport.json?.ok === true, "endpoint finanzas admin responde", { summary: adminReport.json?.summary, cashiers: adminReport.json?.cashiers });
  pass(Number(adminReport.json?.summary?.tickets) >= 4, "finanzas admin cuenta ventas QA", { summary: adminReport.json?.summary });
  pass(Number(adminReport.json?.summary?.totalVendido) >= 200, "finanzas admin suma monto vendido QA", { summary: adminReport.json?.summary });

  const cashierReport = await report("get-cashier-report", { actorKey: bancae01.user, adminKey: admin.id, cashierKey: bancae01.user }, cashier1.token);
  pass(cashierReport.json?.ok === true, "endpoint finanzas cajero responde", { summary: cashierReport.json?.summary });
  pass(Number(cashierReport.json?.summary?.tickets) >= 2, "finanzas cajero cuenta sus ventas", { summary: cashierReport.json?.summary });

  log("Nota comision", {
    serverReportHasComision: Object.prototype.hasOwnProperty.call(adminReport.json?.summary ?? {}, "comision"),
    serverReportHasSupervisorComision: Object.prototype.hasOwnProperty.call(adminReport.json?.summary ?? {}, "supervisorComision"),
  });
}

try {
  await main();
} catch (error) {
  log("BUG prueba interrumpida", { message: error?.message, stack: error?.stack });
} finally {
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  log(`LOG_FILE ${decodeURIComponent(logFile.pathname)}`);
}
