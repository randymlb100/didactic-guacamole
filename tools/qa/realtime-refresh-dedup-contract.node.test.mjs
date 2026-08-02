import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

function read(relativePath) {
  return readFileSync(`${root}/${relativePath}`, "utf8");
}

test("realtime broadcast subscriptions are shared across Compose screens", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt");
  const subscribeSection = source.slice(source.indexOf("fun subscribe("), source.indexOf("fun subscribeBroadcast("));
  const broadcastSection = source.slice(
    source.indexOf("fun subscribeBroadcast("),
    source.indexOf("private suspend fun applyRealtimeAuth"),
  );

  assert.match(source, /private val sharedBroadcastSubscriptionLock = Any\(\)/);
  assert.match(source, /private val sharedBroadcastSubscriptions = mutableMapOf<String, SharedBroadcastSubscription>\(\)/);
  assert.match(source, /private val sharedBroadcastScope = CoroutineScope\(SupervisorJob\(\) \+ Dispatchers\.IO\)/);
  assert.match(source, /val job = sharedBroadcastScope\.launch\(start = CoroutineStart\.LAZY\)/);
  assert.match(source, /ensurePrivateRealtimeAuthRefreshLoop/);
  assert.match(source, /privateRealtimeAuthRefreshJob/);
  assert.match(source, /private var realtimeChannelHealthy: Boolean = false/);
  assert.match(source, /fun isHealthy\(\): Boolean = isConfigured\(\) && realtimeChannelHealthy/);
  assert.match(source, /fun shouldUsePollingFallback\(\): Boolean = !isConfigured\(\) \|\| !realtimeChannelHealthy/);
  assert.match(source, /channel\.subscribe\(blockUntilSubscribed = true\)\s*\n\s*realtimeChannelHealthy = true/);
  assert.match(source, /realtimeChannelHealthy = false/);
  assert.match(source, /while \(isActive\)/);
  assert.doesNotMatch(subscribeSection, /while\s*\(\s*true\s*\)/);
  assert.doesNotMatch(broadcastSection, /while\s*\(\s*true\s*\)/);
  assert.match(source, /synchronized\(sharedBroadcastSubscriptionLock\)/);
  assert.doesNotMatch(source, /private val broadcastSubscriptionLock = Any\(\)/);
});

test("ticket refresh governor is shared so repeated screens reuse the same cool down window", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt");

  assert.match(source, /companion object \{\s*private val sharedRefreshGovernor = TicketRefreshGovernor\(\)/s);
  assert.match(source, /private val refreshGovernor = sharedRefreshGovernor/);
});

test("polling is gated by the realtime health result instead of configuration alone", () => {
  const recharge = read("app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt");
  const sales = read("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt");
  const results = read("app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt");

  assert.match(recharge, /if \(realtimeClient\.shouldUsePollingFallback\(\)\) \{/);
  assert.match(sales, /if \(!realtimeClient\.shouldUsePollingFallback\(\)\) continue/);
  assert.match(results, /realtimeEnabled = realtimeClient\.shouldUsePollingFallback\(\)/);
  assert.doesNotMatch(sales, /if \(realtimeEnabled\) return@LaunchedEffect/);
});

test("admin and ticket screens keep a dormant fallback scheduler instead of trusting a subscription handle", () => {
  const files = [
    "AdminDashboardActivity.kt",
    "AdminCashierDetailActivity.kt",
    "AdminWinnersActivity.kt",
    "AdminMonitorActivity.kt",
    "AdminLotteryMonitorActivity.kt",
  ];
  for (const name of files) {
    const source = read(`app/src/main/java/com/lotterynet/pro/ui/admin/${name}`);
    assert.match(source, /realtimeClient\.shouldUsePollingFallback\(\)/);
    assert.doesNotMatch(source, /if \(realtimeSubscriptions\.isNotEmpty\(\)\) return/);
  }
  const ticketSummary = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt");
  assert.match(ticketSummary, /realtimeClient\.shouldUsePollingFallback\(\)/);
  assert.match(ticketSummary, /realtimeClient\.isConfigured\(\) \|\| shouldStartTicketSummaryFallbackPoll/);
});
