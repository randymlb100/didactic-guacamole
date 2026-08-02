import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);

test("cashier modal actions open distinct destinations", () => {
  assert.match(source, /OperationalReportActivity::class\.java/);
  assert.match(source, /TicketSummaryActivity::class\.java/);
  assert.match(source, /FinanceActivity::class\.java/);
  assert.match(source, /TicketLookupActivity::class\.java/);
  assert.match(source, /TicketLookupActivity\.EXTRA_MODE, "pagar"/);
});

test("tickets action keeps the selected cashier scope", () => {
  assert.match(source, /TicketSummaryActivity\.EXTRA_CASHIER_KEY, row\.userId/);
});
