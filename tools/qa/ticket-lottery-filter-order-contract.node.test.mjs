import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const support = readFileSync("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketListSupport.kt", "utf8");
const summary = readFileSync("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt", "utf8");

test("ticket lottery options follow configured pick mode", () => {
  assert.match(summary, /config\.pickModeEnabled/);
  assert.match(support, /pickLabel/);
  assert.match(support, /catalog != null \|\| !pickLabel/);
});

test("ticket lottery options are ordered by draw time", () => {
  assert.match(support, /parseTicketLotteryMinutes\(it\.baseDrawTime\)/);
  assert.match(support, /sortedWith\(compareBy\(/);
});
