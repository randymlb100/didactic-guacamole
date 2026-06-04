import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const policyPath = "app/src/main/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicy.kt";
const policyTestPath = "app/src/test/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicyTest.kt";
const playbookPath = "docs/supabase/multibanca-engineering-playbook.md";
const planPath = "docs/superpowers/plans/2026-06-02-ticket-duplicates-and-fast-sale-feedback.md";

function read(path) {
  return readFileSync(path, "utf8");
}

test("Android foreground catch-up policy exists as reusable sync domain code", () => {
  assert.equal(existsSync(policyPath), true);

  const source = read(policyPath);
  assert.match(source, /data class ForegroundCatchUpInput/);
  assert.match(source, /data class ForegroundCatchUpDecision/);
  assert.match(source, /class ForegroundCatchUpPolicy/);
  assert.match(source, /OperationalSyncThrottle/);
});

test("foreground catch-up policy covers tickets results and realtime reconnect", () => {
  const source = read(policyPath);

  assert.match(source, /refreshTickets/);
  assert.match(source, /refreshResults/);
  assert.match(source, /reconnectRealtime/);
  assert.match(source, /hasLocalTickets/);
  assert.match(source, /hasLocalResults/);
  assert.match(source, /ticketStampChanged/);
  assert.match(source, /resultsStampChanged/);
  assert.match(source, /realtimeConnected/);
});

test("foreground catch-up policy throttles automatic refresh but allows force recovery", () => {
  const source = read(policyPath);

  assert.match(source, /throttle\.shouldRun\(input\.nowMs, input\.force\)/);
  assert.match(source, /throttle\.markRan\(input\.nowMs\)/);
  assert.match(source, /input\.force/);
});

test("foreground catch-up policy tests cover startup stale stamps realtime and throttle", () => {
  assert.equal(existsSync(policyTestPath), true);

  const testSource = read(policyTestPath);
  assert.match(testSource, /empty local state refreshes tickets results and realtime/);
  assert.match(testSource, /changed server stamps/);
  assert.match(testSource, /reconnects realtime/);
  assert.match(testSource, /throttled to avoid server spam/);
  assert.match(testSource, /bypasses throttle/);
});

test("playbook and plan require foreground catch-up after background", () => {
  const playbook = read(playbookPath);
  const plan = read(planPath);

  assert.match(playbook, /al volver debe hacer catch-up/);
  assert.match(playbook, /pedir stamps\/versiones/);
  assert.match(plan, /Android Foreground\/Background Freshness QA/);
  assert.match(plan, /foreground catch-up/);
  assert.match(plan, /reconnect realtime channels/);
});

test("ticket summary uses foreground catch-up instead of blind resume sync", () => {
  const summary = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt");

  assert.match(summary, /ForegroundCatchUpPolicy/);
  assert.match(summary, /resolveTicketSummaryForegroundCatchUpInput/);
  assert.match(summary, /remoteStampStore\.fetchUpdatedAt/);
  assert.match(summary, /subscribeRealtime\(reset = true\)/);
  assert.match(summary, /TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS/);
});

test("admin winners uses foreground catch-up so missed realtime does not hide winners", () => {
  const winners = read("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");

  assert.match(winners, /ForegroundCatchUpPolicy/);
  assert.match(winners, /resolveAdminWinnersForegroundCatchUpInput/);
  assert.match(winners, /override fun onResume\(\)/);
  assert.match(winners, /remoteStampStore\.fetchUpdatedAtFresh/);
  assert.match(winners, /subscribeRealtime\(reset = true\)/);
  assert.match(winners, /ADMIN_WINNERS_FOREGROUND_CATCH_UP_THROTTLE_MS/);
});

test("admin operational screens with remote stamps also catch up after foreground resume", () => {
  const screens = [
    {
      path: "app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt",
      helper: "resolveAdminDashboardForegroundCatchUpInput",
      throttle: "ADMIN_DASHBOARD_FOREGROUND_CATCH_UP_THROTTLE_MS",
    },
    {
      path: "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
      helper: "resolveAdminMonitorForegroundCatchUpInput",
      throttle: "ADMIN_MONITOR_FOREGROUND_CATCH_UP_THROTTLE_MS",
    },
    {
      path: "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt",
      helper: "resolveAdminLotteryMonitorForegroundCatchUpInput",
      throttle: "ADMIN_LOTTERY_MONITOR_FOREGROUND_CATCH_UP_THROTTLE_MS",
    },
    {
      path: "app/src/main/java/com/lotterynet/pro/ui/admin/AdminCashierDetailActivity.kt",
      helper: "resolveCashierDetailForegroundCatchUpInput",
      throttle: "CASHIER_DETAIL_FOREGROUND_CATCH_UP_THROTTLE_MS",
    },
  ];

  for (const screen of screens) {
    const source = read(screen.path);
    assert.match(source, /ForegroundCatchUpPolicy/, `${screen.path} should use foreground catch-up policy`);
    assert.match(source, new RegExp(screen.helper), `${screen.path} should expose catch-up input helper`);
    assert.match(source, /override fun onResume\(\)/, `${screen.path} should refresh on foreground resume`);
    assert.match(source, /remoteStampStore\.fetchUpdatedAtFresh/, `${screen.path} should fetch fresh server stamp`);
    assert.match(source, /subscribeRealtime\(reset = true\)/, `${screen.path} should reconnect realtime when needed`);
    assert.match(source, new RegExp(screen.throttle), `${screen.path} should throttle foreground catch-up`);
  }
});
