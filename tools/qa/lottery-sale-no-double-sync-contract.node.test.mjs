import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

test("normal/Pick sale does not sync the whole session after ticket flush", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`,
    "utf8",
  );
  const start = source.indexOf("val result = nativeOperationalSyncCoordinator.flushTicket(");
  assert.notEqual(start, -1, "sale flush path must remain present");
  const end = source.indexOf("stagedRows.clear()", start);
  assert.notEqual(end, -1, "sale completion boundary must remain present");
  const completion = source.slice(start, end);
  assert.doesNotMatch(
    completion,
    /syncTicketsForSession\(\s*session\s*=.*?force\s*=\s*true/s,
    "flush completion must not trigger a second forced session sync",
  );
  assert.match(completion, /result\.remoteUpdatedAt\?\.let\(liveTicketRemoteStamp::set\)/);
});
