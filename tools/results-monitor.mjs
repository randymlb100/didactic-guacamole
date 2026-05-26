#!/usr/bin/env node

const renderBaseUrl = process.env.RESULTS_RENDER_URL || 'https://didactic-guacamole.onrender.com';
const date = process.env.RESULTS_MONITOR_DATE || toDrDate(new Date());

function toDrDate(value) {
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Santo_Domingo',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  return formatter.format(value).replaceAll('/', '-');
}

async function fetchJson(url) {
  const startedAt = Date.now();
  const response = await fetch(url, { headers: { accept: 'application/json' } });
  const body = await response.text();
  const durationMs = Date.now() - startedAt;
  let json;
  try {
    json = JSON.parse(body);
  } catch {
    throw new Error(`Invalid JSON from ${url}: ${body.slice(0, 160)}`);
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} from ${url}: ${body.slice(0, 160)}`);
  }
  return { json, durationMs };
}

function sectionCount(payload, section) {
  return Number(payload?.[section]?.count || 0);
}

function buildReport(payload, durationMs) {
  const lotteryCount = sectionCount(payload, 'lotteries');
  const pickCount = sectionCount(payload, 'picks');
  const problems = [];
  if (lotteryCount <= 0) problems.push('lottery-cache-empty');
  if (pickCount <= 0) problems.push('pick-cache-empty');
  if (payload.source === 'live-scraper') problems.push('api-used-live-scraper');
  if (durationMs > 5000) problems.push('api-slow-response');

  return {
    ok: problems.length === 0,
    date: payload.date || date,
    source: payload.source || 'unknown',
    servedFrom: payload.servedFrom || null,
    durationMs,
    lotteryCount,
    pickCount,
    problems,
    checkedAt: new Date().toISOString(),
  };
}

const url = `${renderBaseUrl.replace(/\/$/, '')}/system-results?date=${encodeURIComponent(date)}&mode=both`;
const { json, durationMs } = await fetchJson(url);
const report = buildReport(json, durationMs);

console.log(JSON.stringify(report, null, 2));
if (!report.ok) {
  process.exitCode = 1;
}
