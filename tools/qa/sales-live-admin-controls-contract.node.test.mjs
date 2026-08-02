import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const salesActivity = readFileSync('app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt', 'utf8');
const masterStore = readFileSync('app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt', 'utf8');
const realtimeClient = readFileSync('app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt', 'utf8');

test('sales screen subscribes to live admin lottery and system mode controls', () => {
  assert.match(
    salesActivity,
    /LotterynetRealtimeSubscription\.masterKey\(manualDisabledLotteriesRemoteKey\(ownerKey\)\)/,
    'Venta must subscribe to manual lottery disable changes while the screen is open.',
  );
  assert.match(
    salesActivity,
    /LotterynetRealtimeSubscription\.masterKey\(systemModeRemoteKey\(ownerKey\)\)/,
    'Venta must subscribe to system mode changes while the screen is open.',
  );
  assert.match(
    salesActivity,
    /var liveManualClosedLotteryIds by remember\(manualClosedLotteryIds\)/,
    'Manual lottery close state must be mutable after startup.',
  );
  assert.match(
    salesActivity,
    /var liveSystemModeConfig by remember\(initialSystemModeConfig\)/,
    'System mode state must be mutable after startup.',
  );
});

test('live admin control refresh bypasses the short in-memory master cache', () => {
  assert.match(
    masterStore,
    /fun refreshValue\(key: String\): Any\? \{\s*clearMasterMemoryCache\(key\)\s*return fetchValue\(key\)\s*\}/s,
    'Realtime refresh must clear per-key master cache before fetching the new server value.',
  );
});

test('user directory realtime refresh also recomputes effective system mode', () => {
  assert.match(
    salesActivity,
    /val nextSystemModeConfig = effectiveSystemModeConfigForSession\(/,
    'Cashier mode overrides from users-state changes must update the live sale mode.',
  );
  assert.match(
    salesActivity,
    /liveSystemModeConfig = nextSystemModeConfig/,
    'The recomputed mode must be applied to the open Venta screen.',
  );
});

test('sales realtime channels receive a fresh bearer token provider', () => {
  assert.match(
    salesActivity,
    /subscribeUsersStateSignals\(\s*bearerTokenProvider = \{ sessionTokenProvider\.freshAccessToken\(\) \},/s,
    'Users broadcast channel must use a fresh token provider.',
  );
  for (const key of [
    'LotterynetRealtimeSubscription.masterKey(cashierLimitRemoteKey(ownerKey))',
    'LotterynetRealtimeSubscription.masterKey(manualDisabledLotteriesRemoteKey(ownerKey))',
    'LotterynetRealtimeSubscription.masterKey(systemModeRemoteKey(ownerKey))',
  ]) {
    const escapedKey = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    assert.match(
      salesActivity,
      new RegExp(`subscription = ${escapedKey},\\s*bearerTokenProvider = \\{ sessionTokenProvider\\.freshAccessToken\\(\\) \\}`, 's'),
      `${key} must use a fresh token provider.`,
    );
  }
});

test('realtime postgres subscriptions refresh auth periodically', () => {
  assert.match(
    realtimeClient,
    /fun subscribe\(\s*subscription: LotterynetRealtimeSubscription,\s*bearerTokenProvider: \(\(\) -> String\?\)\? = null,/s,
    'Postgres realtime subscriptions must accept bearerTokenProvider.',
  );
  assert.match(
    realtimeClient,
    /while \(isActive\) \{\s*applyRealtimeAuth\(privateRealtimeAuthProvider\)\s*delay\(PRIVATE_REALTIME_AUTH_REFRESH_INTERVAL_MS\)/s,
    'Realtime auth must be refreshed while a channel is open.',
  );
  assert.doesNotMatch(
    realtimeClient,
    /subscribe\(LotterynetRealtimeSubscription\.ticketOwner\(ownerKey\), onEvent\)/,
    'Ticket owner postgres channel must not subscribe without token refresh.',
  );
});

test('venta inline feedback stays outside the keypad composer', () => {
  assert.match(
    salesActivity,
    /private fun VentaFloatingFeedbackBanner\(/,
    'Venta must render transient feedback through a floating banner.',
  );
  assert.match(
    salesActivity,
    /VentaFloatingFeedbackBanner\(\s*feedbackMessage = liveFeedbackMessage,\s*feedbackIsError = liveFeedbackIsError,\s*numberHasError = numberAdvanceState\.showNumberError,/s,
    'The POS screen must place feedback over the staged list, away from the keypad.',
  );
  const fixedComposer = salesActivity.slice(
    salesActivity.indexOf('private fun VentaFixedComposer('),
    salesActivity.indexOf('private fun VentaFloatingFeedbackBanner('),
  );
  assert.doesNotMatch(
    fixedComposer,
    /VentaFloatingFeedbackBanner\(/,
    'The keypad composer must not reserve space for feedback.',
  );
});
