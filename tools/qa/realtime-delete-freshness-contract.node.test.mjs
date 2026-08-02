import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

function read(relativePath) {
  return readFileSync(`${root}/${relativePath}`, "utf8");
}

test("delete-driven ticket refresh bypasses stale owner snapshot cache", () => {
  const remoteStore = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt");
  const cloudSync = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt");
  const opSync = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt");
  const summary = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt");

  assert.match(remoteStore, /fun fetchSnapshot\(\s*ownerKey: String,[\s\S]*forceRefresh: Boolean = false,/);
  assert.match(remoteStore, /val authScope = if \(authToken != null\) "auth" else "anon"/);
  assert.match(remoteStore, /if \(!forceRefresh && refreshGovernor\.shouldReuse\(ticketSnapshotGovernorKey\(key, authScope\)\)\)/);
  assert.match(remoteStore, /if \(forceRefresh\) \{\s*clearTicketSnapshotMemoryCache\(key\)/s);
  assert.match(remoteStore, /fun fetchUpdatedAtFresh\(ownerKey: String, forceFresh: Boolean = false\)/);
  assert.match(remoteStore, /if \(!forceFresh\) \{\s*readTicketFreshUpdatedAtCache\(key\)/s);
  assert.match(remoteStore, /val authScope = "anon"/);
  assert.match(remoteStore, /if \(!forceFresh && refreshGovernor\.shouldReuse\(ticketUpdatedAtGovernorKey\(key, authScope\)\)\)/);

  assert.match(cloudSync, /override fun flushOwnerLocalSnapshot\(ownerKey: String, banca: String\?\): NativeTicketCloudSyncResult \{/);
  assert.match(cloudSync, /fetchRecentAuthoritativeSnapshot\(normalizedOwner, forceFresh = true\)/);
  assert.match(cloudSync, /private fun fetchRecentAuthoritativeSnapshot\(ownerKey: String, forceFresh: Boolean = false\)/);
  assert.match(cloudSync, /forceRefresh = forceFresh/);

  assert.match(opSync, /fun refreshOwnerFromRealtime\(ownerKey: String, banca: String\? = null\): NativeOperationalSyncState \{/);
  assert.match(opSync, /syncTicketsForOwner\(ownerKey, banca, force = false\)/);

  assert.match(summary, /fetchUpdatedAtFresh\(primaryOwnerKey, forceFresh = force \|\| freshRemoteStamp\)/);
});
