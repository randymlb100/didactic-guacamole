import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

const timeZonesByState = {
  AR: "America/Chicago",
  AZ: "America/Phoenix",
  CA: "America/Los_Angeles",
  CO: "America/Denver",
  CT: "America/New_York",
  DC: "America/New_York",
  DE: "America/New_York",
  FL: "America/New_York",
  GA: "America/New_York",
  IA: "America/Chicago",
  ID: "America/Boise",
  IL: "America/Chicago",
  IN: "America/New_York",
  KS: "America/Chicago",
  KY: "America/New_York",
  LA: "America/Chicago",
  MA: "America/New_York",
  MD: "America/New_York",
  ME: "America/New_York",
  MI: "America/New_York",
  MN: "America/Chicago",
  MO: "America/Chicago",
  MS: "America/Chicago",
  NC: "America/New_York",
  NE: "America/Chicago",
  NH: "America/New_York",
  NJ: "America/New_York",
  NM: "America/Denver",
  NY: "America/New_York",
  OH: "America/New_York",
  OK: "America/Chicago",
  OR: "America/Los_Angeles",
  PA: "America/New_York",
  RI: "America/New_York",
  SC: "America/New_York",
  TN: "America/Chicago",
  TX: "America/Chicago",
  VA: "America/New_York",
  VT: "America/New_York",
  WA: "America/Los_Angeles",
  WI: "America/Chicago",
  WV: "America/New_York",
};

const drawTimesByStatePeriod = {
  "AR:MIDDAY": "12:59 PM", "AR:EVENING": "6:59 PM",
  "AZ:DRAW": "7:00 PM",
  "CA:DAY": "1:00 PM", "CA:MIDDAY": "1:00 PM", "CA:EVENING": "6:30 PM",
  "CO:MIDDAY": "1:30 PM", "CO:EVENING": "7:30 PM",
  "CT:DAY": "1:57 PM", "CT:MIDDAY": "1:57 PM", "CT:NIGHT": "10:29 PM", "CT:EVENING": "10:29 PM",
  "DC:MIDDAY": "1:50 PM", "DC:EVENING": "7:50 PM", "DC:NIGHT": "11:30 PM",
  "DE:DAY": "1:58 PM", "DE:MIDDAY": "1:58 PM", "DE:NIGHT": "7:57 PM", "DE:EVENING": "7:57 PM",
  "FL:MIDDAY": "1:30 PM", "FL:EVENING": "9:45 PM",
  "GA:MIDDAY": "12:29 PM", "GA:EVENING": "6:59 PM", "GA:NIGHT": "11:34 PM",
  "IA:MIDDAY": "12:20 PM", "IA:EVENING": "10:00 PM",
  "ID:DAY": "1:59 PM", "ID:MIDDAY": "1:59 PM", "ID:NIGHT": "7:59 PM",
  "IL:MIDDAY": "12:40 PM", "IL:MORNING": "12:40 PM", "IL:EVENING": "9:22 PM",
  "IN:MIDDAY": "1:20 PM", "IN:EVENING": "11:00 PM",
  "KS:MIDDAY": "1:10 PM", "KS:EVENING": "9:10 PM",
  "KY:MIDDAY": "1:20 PM", "KY:EVENING": "11:00 PM",
  "LA:DAY": "9:59 PM",
  "MA:MIDDAY": "2:00 PM", "MA:EVENING": "9:00 PM",
  "MD:MIDDAY": "12:27 PM", "MD:EVENING": "7:56 PM",
  "ME:MIDDAY": "1:10 PM", "ME:EVENING": "6:50 PM",
  "MI:MIDDAY": "12:59 PM", "MI:EVENING": "7:29 PM",
  "MN:DAY": "6:17 PM",
  "MO:DAY": "12:45 PM", "MO:MIDDAY": "12:45 PM", "MO:EVENING": "8:59 PM",
  "MS:MIDDAY": "2:30 PM", "MS:EVENING": "9:30 PM",
  "NC:MIDDAY": "3:00 PM", "NC:EVENING": "11:22 PM",
  "NE:DAY": "10:00 PM",
  "NH:MIDDAY": "1:10 PM", "NH:EVENING": "6:55 PM",
  "NJ:MIDDAY": "12:59 PM", "NJ:EVENING": "10:57 PM",
  "NM:MIDDAY": "1:00 PM", "NM:EVENING": "9:30 PM",
  "NY:MIDDAY": "2:30 PM", "NY:EVENING": "10:30 PM",
  "OH:MIDDAY": "12:29 PM", "OH:EVENING": "7:29 PM",
  "OK:DAY": "9:00 PM",
  "OR:EVENING": "7:00 PM",
  "PA:DAY": "1:35 PM", "PA:MIDDAY": "1:35 PM", "PA:EVENING": "6:59 PM",
  "RI:MIDDAY": "1:20 PM", "RI:EVENING": "6:59 PM",
  "SC:MIDDAY": "12:59 PM", "SC:EVENING": "6:59 PM",
  "TN:MORNING": "9:28 AM", "TN:DAY": "12:28 PM", "TN:MIDDAY": "12:28 PM", "TN:EVENING": "6:28 PM",
  "TX:MORNING": "10:00 AM", "TX:DAY": "12:27 PM", "TX:EVENING": "6:00 PM", "TX:NIGHT": "10:12 PM",
  "VA:DAY": "1:59 PM", "VA:MIDDAY": "1:59 PM", "VA:NIGHT": "11:00 PM", "VA:EVENING": "11:00 PM",
  "VT:MIDDAY": "1:10 PM", "VT:EVENING": "6:59 PM",
  "WA:DAY": "8:00 PM",
  "WI:MIDDAY": "1:30 PM", "WI:EVENING": "9:00 PM",
  "WV:DAY": "6:59 PM", "WV:EVENING": "9:00 PM",
};

const tests = [];
const test = (name, fn) => tests.push({ name, fn });

function parseDateKey(dateKey) {
  const [day, month, year] = dateKey.split("-").map(Number);
  return { year, month, day };
}

function parseClock(clock) {
  const match = /^(\d{1,2}):(\d{2})\s*(AM|PM)$/i.exec(clock);
  assert.ok(match, `Invalid clock: ${clock}`);
  let hour = Number(match[1]) % 12;
  if (match[3].toUpperCase() === "PM") hour += 12;
  return { hour, minute: Number(match[2]) };
}

function zonedTimeToUtc({ year, month, day, hour, minute, timeZone }) {
  let utcMs = Date.UTC(year, month - 1, day, hour, minute);
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
  for (let i = 0; i < 4; i += 1) {
    const parts = Object.fromEntries(formatter.formatToParts(new Date(utcMs)).map((part) => [part.type, part.value]));
    const localMs = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour), Number(parts.minute));
    const targetMs = Date.UTC(year, month - 1, day, hour, minute);
    const deltaMs = targetMs - localMs;
    if (deltaMs === 0) return new Date(utcMs);
    utcMs += deltaMs;
  }
  return new Date(utcMs);
}

function resolvePeriod(row) {
  const text = `${row.id ?? ""} ${row.name ?? ""} ${row.draw ?? ""}`.toUpperCase();
  if (text.includes("MORNING")) return "MORNING";
  if (text.includes("MIDDAY") || text.includes("DIA")) return "MIDDAY";
  if (text.includes("EVENING") || text.includes("TARDE")) return "EVENING";
  if (text.includes("NIGHT") || text.includes("NOCHE")) return "NIGHT";
  if (text.split(/[-_ :/.,()]+/).includes("DAY")) return "DAY";
  if (text.includes("DRAW")) return "DRAW";
  return null;
}

function drawUtc(row, dateKey) {
  const stateCode = row.stateCode;
  const period = resolvePeriod(row);
  const drawTime = drawTimesByStatePeriod[`${stateCode}:${period}`] ?? (period === "DAY" ? drawTimesByStatePeriod[`${stateCode}:MIDDAY`] : null);
  assert.ok(drawTime, `No draw time for ${row.id} (${stateCode}:${period})`);
  const { year, month, day } = parseDateKey(dateKey);
  const { hour, minute } = parseClock(drawTime);
  return zonedTimeToUtc({
    year,
    month,
    day,
    hour,
    minute,
    timeZone: timeZonesByState[stateCode] ?? "America/New_York",
  });
}

function suppressEarly(row, dateKey, nowUtc) {
  const releaseUtc = drawUtc(row, dateKey);
  if (nowUtc >= releaseUtc || !row.number) return { ...row };
  return {
    ...row,
    number: "",
    pick3: "",
    pick4: "",
    status: "pending",
    source: "early-result-suppressed",
    suppressedUntil: releaseUtc.toISOString().replace(".000Z", "Z"),
  };
}

function blocksSale(row, dateKey, nowUtc) {
  const visibleRow = suppressEarly(row, dateKey, nowUtc);
  return Boolean(visibleRow.number || visibleRow.pick3 || visibleRow.pick4) && nowUtc >= drawUtc(row, dateKey);
}

function assertSuppressed(name, row, dateKey, nowUtcIso) {
  const result = suppressEarly(row, dateKey, new Date(nowUtcIso));
  assert.equal(result.status, "pending", name);
  assert.equal(result.number, "", name);
  assert.equal(result.source, "early-result-suppressed", name);
  assert.equal(blocksSale(row, dateKey, new Date(nowUtcIso)), false, `${name} sale must stay open`);
}

function assertPublished(name, row, dateKey, nowUtcIso) {
  const result = suppressEarly(row, dateKey, new Date(nowUtcIso));
  assert.equal(result.number, row.number, name);
  assert.notEqual(result.source, "early-result-suppressed", name);
  assert.equal(blocksSale(row, dateKey, new Date(nowUtcIso)), true, `${name} sale must be blocked`);
}

test("NJ Pick 3 dia no bloquea 12:58 ET y bloquea 12:59 ET", () => {
  const row = { id: "US-P3-NJ-PICK-3-MIDDAY", stateCode: "NJ", draw: "Midday Draw", number: "8-1-4", pick3: "8-1-4" };
  assert.equal(drawUtc(row, "10-05-2026").toISOString(), "2026-05-10T16:59:00.000Z");
  assertSuppressed("NJ dia before", row, "10-05-2026", "2026-05-10T16:58:00Z");
  assertPublished("NJ dia at draw", row, "10-05-2026", "2026-05-10T16:59:00Z");
});

test("NJ Pick 3 noche no bloquea 10:56 ET y bloquea 10:57 ET", () => {
  const row = { id: "US-P3-NJ-PICK-3-EVENING", stateCode: "NJ", draw: "Evening Draw", number: "5-1-4", pick3: "5-1-4" };
  assert.equal(drawUtc(row, "10-05-2026").toISOString(), "2026-05-11T02:57:00.000Z");
  assertSuppressed("NJ noche before", row, "10-05-2026", "2026-05-11T02:56:00Z");
  assertPublished("NJ noche at draw", row, "10-05-2026", "2026-05-11T02:57:00Z");
});

test("NJ dia publicado no bloquea NJ noche", () => {
  const dia = { id: "US-P3-NJ-PICK-3-MIDDAY", stateCode: "NJ", draw: "Midday Draw", number: "1-2-3", pick3: "1-2-3" };
  const noche = { id: "US-P3-NJ-PICK-3-EVENING", stateCode: "NJ", draw: "Evening Draw", number: "9-9-9", pick3: "9-9-9" };
  assert.equal(blocksSale(dia, "10-05-2026", new Date("2026-05-10T20:00:00Z")), true);
  assert.equal(blocksSale(noche, "10-05-2026", new Date("2026-05-10T20:00:00Z")), false);
});

test("Maine DAY usa hora MIDDAY: pendiente 1:09 ET y publicado 1:10 ET", () => {
  const row = { id: "US-P3-ME-PICK-3-DAY", stateCode: "ME", draw: "Day Draw", number: "1-2-3", pick3: "1-2-3" };
  assert.equal(drawUtc(row, "10-05-2026").toISOString(), "2026-05-10T17:10:00.000Z");
  assertSuppressed("Maine day before", row, "10-05-2026", "2026-05-10T17:09:00Z");
  assertPublished("Maine day at draw", row, "10-05-2026", "2026-05-10T17:10:00Z");
});

test("despues de la hora sin numero sigue pendiente y no inventa resultado", () => {
  const row = { id: "US-P3-NY-PICK-3-MIDDAY", stateCode: "NY", draw: "Midday Draw", number: "", pick3: "", status: "pending" };
  const result = suppressEarly(row, "10-05-2026", new Date("2026-05-10T19:30:00Z"));
  assert.equal(result.number, "");
  assert.equal(result.status, "pending");
  assert.equal(blocksSale(row, "10-05-2026", new Date("2026-05-10T19:30:00Z")), false);
});

test("si el numero llega tarde despues de la hora se publica y bloquea", () => {
  const row = { id: "US-P3-NY-PICK-3-MIDDAY", stateCode: "NY", draw: "Midday Draw", number: "4-5-6", pick3: "4-5-6" };
  assertPublished("NY late result", row, "10-05-2026", "2026-05-10T19:30:00Z");
});

test("DST se respeta: NJ 12:59 ET cambia UTC entre verano e invierno", () => {
  const row = { id: "US-P3-NJ-PICK-3-MIDDAY", stateCode: "NJ", draw: "Midday Draw", number: "8-1-4", pick3: "8-1-4" };
  assert.equal(drawUtc(row, "10-05-2026").toISOString(), "2026-05-10T16:59:00.000Z");
  assert.equal(drawUtc(row, "10-01-2026").toISOString(), "2026-01-10T17:59:00.000Z");
});

test("zonas central, mountain y pacific no usan hora RD fija", () => {
  const tx = { id: "US-P3-TX-PICK-3-NIGHT", stateCode: "TX", draw: "Night Draw", number: "7-7-7", pick3: "7-7-7" };
  const nm = { id: "US-P3-NM-PICK-3-EVENING", stateCode: "NM", draw: "Evening Draw", number: "6-6-6", pick3: "6-6-6" };
  const ca = { id: "US-P4-CA-DAILY-4-EVENING", stateCode: "CA", draw: "Evening Draw", number: "1-2-3-4", pick4: "1-2-3-4" };
  assert.equal(drawUtc(tx, "10-05-2026").toISOString(), "2026-05-11T03:12:00.000Z");
  assert.equal(drawUtc(nm, "10-05-2026").toISOString(), "2026-05-11T03:30:00.000Z");
  assert.equal(drawUtc(ca, "10-05-2026").toISOString(), "2026-05-11T01:30:00.000Z");
});

test("Android y backend conservan horarios criticos iguales en codigo fuente", () => {
  const androidSchedule = fs.readFileSync(path.join(root, "app/src/main/java/com/lotterynet/pro/core/catalog/UsPickScheduleResolver.kt"), "utf8");
  const backendSchedule = fs.readFileSync(path.join(root, "../didactic-guacamole-render/scraper/scrape_and_save.py"), "utf8");
  for (const expected of [
    '("NJ" to "MIDDAY") to "12:59 PM"',
    '("NJ" to "EVENING") to "10:57 PM"',
    '("ME" to "MIDDAY") to "1:10 PM"',
    '("NY" to "MIDDAY") to "2:30 PM"',
    '("FL" to "EVENING") to "9:45 PM"',
    '"NJ" to "America/New_York"',
    '"ME" to "America/New_York"',
    '"TX" to "America/Chicago"',
  ]) {
    assert.ok(androidSchedule.includes(expected), `Android missing ${expected}`);
  }
  for (const expected of [
    '("NJ", "MIDDAY"): "12:59 PM"',
    '("NJ", "EVENING"): "10:57 PM"',
    '("ME", "MIDDAY"): "1:10 PM"',
    '("NY", "MIDDAY"): "2:30 PM"',
    '("FL", "EVENING"): "9:45 PM"',
    '"NJ": "America/New_York"',
    '"ME": "America/New_York"',
    '"TX": "America/Chicago"',
  ]) {
    assert.ok(backendSchedule.includes(expected), `Backend missing ${expected}`);
  }
});

let failures = 0;
for (const item of tests) {
  try {
    item.fn();
    console.log(`PASS ${item.name}`);
  } catch (error) {
    failures += 1;
    console.error(`FAIL ${item.name}`);
    console.error(error?.stack ?? error);
  }
}

if (failures > 0) {
  console.error(`${failures}/${tests.length} Node Pick gate checks failed.`);
  process.exit(1);
}

console.log(`${tests.length}/${tests.length} Node Pick gate checks passed.`);
