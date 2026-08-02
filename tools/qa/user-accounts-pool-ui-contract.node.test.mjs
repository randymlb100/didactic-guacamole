import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const source = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt"),
  "utf8",
);

assert.match(
  source,
  /onLoadCashierPoolLimits: \(\) -> CashierSalesLimitInputs/,
  "user accounts route should accept a loader for the shared pool limits",
);

assert.match(
  source,
  /onSaveCashierPoolLimits: \(CashierSalesLimitInputs\) -> Unit/,
  "user accounts route should accept a saver for the shared pool limits",
);

assert.match(
  source,
  /CashierGlobalPoolSummaryCard\(/,
  "limits section should surface the shared pool summary card",
);

assert.match(
  source,
  /CashierPoolLimitsSheet\(/,
  "limits section should open a dedicated pool editor sheet",
);

assert.match(
  source,
  /limitsReloadTick/,
  "pool and global limits should refresh when the server refresh action runs",
);

assert.match(
  source,
  /LaunchedEffect\(selectedAdminSection, selectedCashierId, selectedCashier\?\.id, limitsReloadTick\)/,
  "pool loader should rehydrate when the selected section or server refresh changes",
);

assert.match(
  source,
  /cashierPoolSectionLabel\(\): String = "Pool del negocio"/,
  "pool editor should identify the pool as a business rule",
);

assert.match(
  source,
  /No pertenecen a un cajero/,
  "pool navigation should remain visually separate from cashier limits",
);

assert.match(
  source,
  /cashierPoolFieldLabels\(\): List<String> = listOf\(\s*"Quiniela"/,
  "pool editor should expose the play-type fields in one compact contract",
);

console.log("User accounts pool UI contract passed");
