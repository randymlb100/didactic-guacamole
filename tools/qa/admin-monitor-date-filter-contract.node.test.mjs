import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);

test("monitor exposes an operational date without merging winner history", () => {
  assert.match(source, /Resumen operativo/);
  assert.match(source, /MonitorDaySelector\(dayKey = dayKey/);
  assert.match(source, /Fecha operativa: \$dayKey/);
  assert.match(source, /Filtra ventas, caja y cajeros de esta fecha/);
  assert.match(source, /El calendario de ganadores permanece independiente/);
});

test("changing the operational date refreshes local monitor data only", () => {
  assert.match(source, /onDaySelected = \{ selectedDay ->/);
  assert.match(source, /dayKey = selectedDay/);
  assert.match(source, /refreshMonitorData\(\)/);
});
