import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const hydration = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt",
  "utf8",
);
const monitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);
const detail = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminCashierDetailActivity.kt",
  "utf8",
);
const cloudSync = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt",
  "utf8",
);
const monotonicReconcilerPath =
  "app/src/main/java/com/lotterynet/pro/core/sync/MonotonicTicketReconciler.kt";

test("admin day hydration uses one bounded official fetch and merges without replacing cache", () => {
  const start = hydration.indexOf("fun hydrateOperationalTicketDay");
  const method = hydration.slice(start, hydration.indexOf("\n}", start) + 2);

  assert.ok(start >= 0, "shared bounded day hydration must exist");
  assert.match(method, /fromDate = dayKey/);
  assert.match(method, /toDate = dayKey/);
  assert.match(method, /limit = OPERATIONAL_DAY_TICKET_FETCH_LIMIT/);
  assert.match(method, /persistMonotonicTicketReconciliation/);
  assert.doesNotMatch(method, /replaceScopedImportedTickets/);
});

test("monitor and cashier detail use bounded day hydration instead of full owner hydration", () => {
  assert.match(monitor, /hydrateOperationalTicketDay\(/);
  assert.match(detail, /hydrateOperationalTicketDay\(/);

  const monitorSync = monitor.slice(
    monitor.indexOf("private fun syncMonitor"),
    monitor.indexOf("private fun refreshMonitorUsers"),
  );
  const detailSync = detail.slice(
    detail.indexOf("private fun syncCashierDetail"),
    detail.indexOf("private fun runForegroundCatchUp"),
  );
  assert.doesNotMatch(monitorSync, /syncTicketsForSession\(/);
  assert.doesNotMatch(detailSync, /syncTicketsForSession\(/);
});

test("partial remote hydration is monotonic and never replaces the local owner scope", () => {
  const reconciler = readFileSync(monotonicReconcilerPath, "utf8");

  assert.match(reconciler, /fun reconcileMonotonicTickets\(/);
  assert.match(reconciler, /completeScope: Boolean/);
  assert.match(hydration, /reconcileMonotonicTickets\(/);
  assert.match(hydration, /completeScope = false/);
  assert.match(cloudSync, /reconcileMonotonicTickets\(/);
  assert.doesNotMatch(hydration, /replaceScopedImportedTickets/);
  assert.doesNotMatch(cloudSync, /replaceScopedImportedTickets/);
});
