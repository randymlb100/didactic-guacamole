import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

test("ticket sync reuses a known remote stamp unless a forced refresh is requested", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`,
    "utf8",
  );
  assert.match(source, /fun\s+shouldHydrateOperationalRemote\(/s);
  assert.match(source, /if\s*\(force\)\s*return\s+true/s);
  assert.match(source, /if\s*\(remoteUpdatedAt\.isNullOrBlank\(\)\)\s*return\s+true/s);
  assert.match(
    source,
    /return\s*!remoteUpdatedAt\.equals\(lastRemoteUpdatedAt\.orEmpty\(\),\s*ignoreCase\s*=\s*true\)/s,
  );
  assert.match(source, /runCatching\s*\{\s*remoteStampStore\.fetchUpdatedAt\(normalizedOwner\)/s);
});
