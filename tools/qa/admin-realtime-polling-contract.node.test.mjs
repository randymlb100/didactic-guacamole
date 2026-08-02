import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";
const files = [
  "AdminWinnersActivity.kt",
  "AdminCashierDetailActivity.kt",
];

test("winner and cashier detail screens poll only when Realtime is unhealthy", () => {
  for (const name of files) {
    const source = readFileSync(
      `${root}/app/src/main/java/com/lotterynet/pro/ui/admin/${name}`,
      "utf8",
    );
    assert.match(source, /realtimeClient\.shouldUsePollingFallback\(\)/);
    assert.doesNotMatch(source, /if \(realtimeSubscriptions\.isNotEmpty\(\) return/);
    assert.match(source, /syncHandler\.postDelayed\(this, resolve/);
  }
});
