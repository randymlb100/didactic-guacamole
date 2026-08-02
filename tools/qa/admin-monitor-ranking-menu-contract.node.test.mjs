import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);

test("ranking identifies its active context", () => {
  assert.match(source, /Fecha: \$dayKey/);
  assert.match(source, /Cajero:/);
  assert.match(source, /Lotería:/);
  assert.match(source, /Jugada:/);
});

test("cashier card menu can scope the ranking to that cashier", () => {
  assert.match(source, /CashierQuickActionRow\("Ver ranking"/);
  assert.match(source, /selectedCashierRowId = row\.userId/);
  assert.match(source, /activeTab = AdminMonitorRoleSegment\.MONITOR\.name/);
});
