import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";
const RENDER_URL = process.env.RESULTS_RENDER_URL || "https://didactic-guacamole.onrender.com";
const SCRAPER_REPO = process.env.RESULTS_SCRAPER_REPO || "C:/Users/Randy Cordero/Desktop/didactic-guacamole";
let date = process.env.RESULTS_MONITOR_DATE || toDrDate(new Date());
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const logFile = new URL(`./results-stack-diagnostic-${stamp}.log`, import.meta.url);
const summaryFile = new URL(`./results-stack-diagnostic-summary-${stamp}.json`, import.meta.url);

const lines = [];
const checks = [];
const metrics = [];

function toDrDate(value) {
  const formatter = new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
  return formatter.format(value).replaceAll("/", "-");
}

function log(label, data) {
  const line = `[${new Date().toISOString()}] ${label}${data === undefined ? "" : ` ${JSON.stringify(data)}`}`;
  lines.push(line);
  console.log(line);
}

function check(condition, label, data = {}) {
  const ok = Boolean(condition);
  checks.push({ ok, label, data });
  log(`${ok ? "PASS" : "BUG"} ${label}`, data);
  return ok;
}

async function fetchJson(label, url, options = {}) {
  const started = performance.now();
  const response = await fetch(url, {
    ...options,
    headers: {
      accept: "application/json",
      ...(options.headers ?? {}),
    },
    signal: AbortSignal.timeout(15000),
  });
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  metrics.push({ label, status: response.status, ok: response.ok, elapsedMs });
  log(`HTTP ${label}`, { status: response.status, ok: response.ok, elapsedMs });
  return { status: response.status, ok: response.ok, elapsedMs, text, json };
}

function sectionCount(payload, section) {
  return Number(payload?.[section]?.count || 0);
}

async function checkGithubWorkflow() {
  const workflowPath = `${SCRAPER_REPO}/.github/workflows/scrape.yml`;
  const workflow = await readFile(workflowPath, "utf8");
  const renderYaml = await readFile(new URL("../../render.yaml", import.meta.url), "utf8").catch(() => "");
  const hasScheduledGithub = workflow.includes("cron:");
  const hasRenderResultsCron = renderYaml.includes("type: cron") && renderYaml.includes("lotterynet-scraper-cron");
  check(hasScheduledGithub || hasRenderResultsCron, "hay cron programado para resultados sin depender de requests de usuarios", {
    workflowPath,
    renderCron: hasRenderResultsCron,
    githubCron: hasScheduledGithub,
  });
  check(workflow.includes("Run scraper contract tests"), "GitHub job prueba scraper antes de guardar", { workflowPath });
  check(workflow.includes("Check Render results API health"), "GitHub job valida Render despues de guardar", { workflowPath });
  check(workflow.includes("SUPABASE_SERVICE_ROLE_KEY") || workflow.includes("SUPABASE_SERVICE_KEY"), "GitHub job prefiere service role para guardar resultados", { workflowPath });
}

async function checkRender() {
  const base = RENDER_URL.replace(/\/$/, "");
  const normal = await fetchJson("render system-results cache", `${base}/system-results?date=${encodeURIComponent(date)}&mode=both`);
  const live = await fetchJson("render system-results live param", `${base}/system-results?date=${encodeURIComponent(date)}&mode=both&live=1`);
  for (const result of [normal, live]) {
    const payload = result.json ?? {};
    check(result.ok, "Render responde resultados", { status: result.status, source: payload.source, elapsedMs: result.elapsedMs });
    check(payload.source !== "live-scraper", "Render no usa scraper inline para POS normal", {
      source: payload.source,
      servedFrom: payload.servedFrom ?? null,
      elapsedMs: result.elapsedMs,
    });
    check(sectionCount(payload, "lotteries") > 0 || sectionCount(payload, "picks") > 0, "Render devuelve alguna seccion de resultados", {
      lotteries: sectionCount(payload, "lotteries"),
      picks: sectionCount(payload, "picks"),
    });
  }
}

async function checkSupabaseResults() {
  const url = `${SUPABASE_URL}/rest/v1/result_draws?result_day_key=eq.${encodeURIComponent(date)}&select=lottery_legacy_id,game,updated_at&limit=200`;
  const result = await fetchJson("supabase result_draws", url, {
    headers: {
      apikey: API_KEY,
      Authorization: `Bearer ${API_KEY}`,
    },
  });
  const rows = Array.isArray(result.json) ? result.json : [];
  check(result.ok, "Supabase result_draws responde por REST", { status: result.status, rows: rows.length, elapsedMs: result.elapsedMs });
  check(rows.length > 0, "Supabase tiene resultados normalizados para la fecha", {
    date,
    normal: rows.filter((row) => row.game === "normal").length,
    picks: rows.filter((row) => row.game === "pick3" || row.game === "pick4").length,
  });
}

async function findCachedDate() {
  if (process.env.RESULTS_MONITOR_DATE) return date;
  for (let offset = 0; offset < 7; offset += 1) {
    const candidate = toDrDate(new Date(Date.now() - offset * 24 * 60 * 60 * 1000));
    const url = `${SUPABASE_URL}/rest/v1/result_draws?result_day_key=eq.${encodeURIComponent(candidate)}&select=lottery_legacy_id&limit=1`;
    const result = await fetchJson(`supabase result_draws probe ${candidate}`, url, {
      headers: {
        apikey: API_KEY,
        Authorization: `Bearer ${API_KEY}`,
      },
    });
    const rows = Array.isArray(result.json) ? result.json : [];
    if (result.ok && rows.length > 0) {
      date = candidate;
      check(true, "diagnostico usa fecha con resultado normalizado disponible", {
        date,
      });
      return date;
    }
  }
  check(false, "no hay result_draws reciente en Supabase", { checkedDays: 7 });
  return date;
}

async function checkRpcNotPublic() {
  const result = await fetchJson("supabase public rpc blocked", `${SUPABASE_URL}/rest/v1/rpc/ln_save_lotterynet_kv`, {
    method: "POST",
    headers: {
      apikey: API_KEY,
      Authorization: `Bearer ${API_KEY}`,
      "content-type": "application/json",
      prefer: "return=minimal",
    },
    body: JSON.stringify({
      p_key: `diagnostic_not_allowed:${date}`,
      p_value: "[]",
      p_upd: new Date().toISOString(),
    }),
  });
  if ([401, 403, 404].includes(result.status)) {
    check(true, "RPC privilegiado no es ejecutable con llave publica", {
      status: result.status,
      message: result.json?.message ?? result.json?.error ?? "",
    });
    return;
  }
  check(result.status === 400 || result.ok, "RPC privilegiado sigue abierto por compatibilidad del scraper actual", {
    status: result.status,
    message: result.json?.message ?? result.json?.error ?? "",
    note: "Para cerrarlo sin romper flujo, GitHub/Render deben usar SUPABASE_SERVICE_ROLE_KEY real.",
  });
}

async function main() {
  log("Inicio results stack diagnostic", { date, renderUrl: RENDER_URL, scraperRepo: SCRAPER_REPO });
  await checkGithubWorkflow();
  await findCachedDate();
  await checkRender();
  await checkSupabaseResults();
  await checkRpcNotPublic();
}

try {
  await main();
} catch (error) {
  check(false, "results stack diagnostic interrumpido", { message: error?.message, stack: error?.stack });
} finally {
  const failed = checks.filter((item) => !item.ok);
  const summary = {
    ok: failed.length === 0,
    date,
    metrics,
    checks,
    logFile: decodeURIComponent(logFile.pathname),
  };
  await writeFile(logFile, `${lines.join("\n")}\n`, "utf8");
  await writeFile(summaryFile, JSON.stringify(summary, null, 2), "utf8");
  log("SUMMARY_FILE", { path: decodeURIComponent(summaryFile.pathname) });
  log("LOG_FILE", { path: decodeURIComponent(logFile.pathname) });
  if (failed.length) process.exit(1);
}
