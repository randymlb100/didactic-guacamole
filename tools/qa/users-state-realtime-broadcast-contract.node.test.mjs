import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");

test("users state realtime uses private broadcast invalidation instead of postgres changes", () => {
  const client = read("app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt");
  const migration = read("supabase/migrations/20260612104500_users_state_broadcast_signal.sql");

  const method = client.slice(client.indexOf("fun subscribeUsersStateSignals"), client.indexOf("fun shutdown()"));
  assert.match(method, /subscribeBroadcast\(/);
  assert.match(method, /ln:users:global/);
  assert.match(method, /isPrivate\s*=\s*true/);
  assert.match(method, /bearerTokenProvider/);
  assert.doesNotMatch(method, /postgresChangeFlow/);
  assert.match(migration, /ln:users:global/);
  assert.match(migration, /realtime\.send/);
});

test("users-state consumers provide fresh authentication to realtime", () => {
  for (const path of [
    "app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt",
    "app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt",
    "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt",
  ]) {
    const source = read(path);
    const start = source.indexOf("subscribeUsersStateSignals");
    assert.notEqual(start, -1, `${path} must subscribe to users state`);
    const fragment = source.slice(start, start + 240);
    assert.match(fragment, /bearerTokenProvider/);
  }
});
