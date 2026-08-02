import { readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";

const adminMonitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);
const lotteryMonitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt",
  "utf8",
);
const sectionCards = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/common/OperationalSectionCards.kt",
  "utf8",
);

test("monitor organization uses a shared dropdown scope card", () => {
  assert.match(
    sectionCards,
    /fun CurrentScopeDropdownCard\(/,
    "shared monitor scope cards should expose a dropdown-based selector",
  );
  assert.match(
    sectionCards,
    /DropdownMenu\([\s\S]*DropdownMenuItem/,
    "scope card should use a real dropdown menu for secondary choices",
  );
});

test("main monitor uses a compact segmented selector for its primary views", () => {
  assert.match(
    adminMonitor,
    /OperationalListHeader\([\s\S]*title = "Vista del panel"[\s\S]*CompactSegmentedSelector\(/,
    "admin monitor should keep cashier and ranking navigation compact",
  );
});

test("main monitor keeps dropdown scope cards for secondary number views", () => {
  assert.match(
    adminMonitor,
    /CurrentScopeDropdownCard\([\s\S]*Vista de números/,
    "admin monitor should use a dropdown card for number ranking selection",
  );
});

test("main monitor filters cashiers from a compact toolbar and action sheet", () => {
  assert.match(
    adminMonitor,
    /MonitorCashierToolbar\([\s\S]*onFilterClick = \{ showFilterSheet = true \}/,
    "cashier filtering should remain available beside the cashier list",
  );
  assert.match(
    adminMonitor,
    /OperationalModalSheet\([\s\S]*title = "Filtro de cajeros"[\s\S]*QuickFilterChips\(/,
    "cashier filter options should open in a focused mobile action sheet",
  );
});

test("lottery monitor uses a dropdown header for the main panel and ranking view", () => {
  assert.match(
    lotteryMonitor,
    /CurrentScopeDropdownCard\([\s\S]*Vista principal/,
    "lottery monitor should switch the main tab through a dropdown card",
  );
  assert.match(
    lotteryMonitor,
    /CurrentScopeDropdownCard\([\s\S]*Ranking de números/,
    "lottery monitor should use a dropdown card for number ranking view selection",
  );
});

console.log("Monitor organization contract passed");
