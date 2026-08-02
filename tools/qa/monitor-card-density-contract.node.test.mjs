import { readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";

const adminMonitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);

test("cashier monitor cards are readable on phones and adapt to wide screens", () => {
  assert.match(adminMonitor, /singleLineIdentity = false/);
  assert.match(adminMonitor, /minTouchTargetDp = 64/);
  assert.match(adminMonitor, /rowPaddingVerticalDp = 10/);
  assert.match(adminMonitor, /style = MaterialTheme\.typography\.titleMedium/);
  assert.match(adminMonitor, /modifier = modifier\.heightIn\(min = 56\.dp\)/);
  assert.match(
    adminMonitor,
    /val wideLayout = visual\.windowMode in setOf\(LotteryNetWindowMode\.TABLET, LotteryNetWindowMode\.WIDE\)/,
    "cashier cards should branch on tablet and wide layouts",
  );
  assert.match(
    adminMonitor,
    /MonitorGlassCard\(/,
    "wide cashier cards should use the new softer glass card surface",
  );
  assert.match(
    adminMonitor,
    /if \(wideLayout\) \{[\s\S]*CompactInlineMetric\(label = "Venta"/,
    "wide cashier cards should surface metrics in a wider layout",
  );
  for (const metric of ["Venta", "Caja", "Pendiente"]) {
    assert.match(adminMonitor, new RegExp(`CompactInlineMetric\\(label = "${metric}"`));
  }
  assert.match(
    adminMonitor,
    /remainingLimitAmount/,
    "cashier cards should surface the remaining limit directly in the card body",
  );
  assert.match(
    adminMonitor,
    /limitScopeLabel/,
    "cashier cards should carry the scope that explains which limit is being shown",
  );
  assert.match(
    adminMonitor,
    /CompactToggleSwitch\(/,
    "cashier cards should keep the single compact toggle and avoid extra status chrome",
  );
  assert.doesNotMatch(adminMonitor, /CompactStatusBadge\(label = .*Bloqueado|CompactStatusBadge\(label = .*Activo|CompactStatusBadge\(label = .*Sin movimiento/);
  assert.doesNotMatch(
    adminMonitor,
    /Icons\.AutoMirrored\.Rounded\.ArrowForward[\s\S]{0,120}CashierMonitorDenseCard/,
    "cashier cards should stop spending space on a redundant chevron when the card already opens and has a switch",
  );
});

console.log("Monitor card density contract passed");
