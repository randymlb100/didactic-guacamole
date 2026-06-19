import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const sourcePath = "app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt";

function read(path) {
  return readFileSync(path, "utf8");
}

function functionBody(source, name) {
  const marker = `override fun ${name}`;
  const start = source.indexOf(marker);
  assert.ok(start >= 0, `${name} must exist`);
  const next = source.indexOf("\n    override fun ", start + marker.length);
  return source.slice(start, next >= 0 ? next : source.length);
}

test("ticket hydration is read-only and does not push full owner snapshots", () => {
  const source = read(sourcePath);
  const hydrateOwner = functionBody(source, "hydrateOwner");

  assert.match(hydrateOwner, /fetchRecentAuthoritativeSnapshot\(normalizedOwner\)/);
  assert.match(hydrateOwner, /reconcileMonotonicTickets\(/);
  assert.match(hydrateOwner, /persistMonotonicTicketReconciliation\(/);
  assert.doesNotMatch(hydrateOwner, /replaceScopedImportedTickets\(/);
  assert.doesNotMatch(hydrateOwner, /flushOwner\(/);
  assert.doesNotMatch(hydrateOwner, /upsertSnapshot\(/);
});

test("ticket flush confirms pending tickets from bounded authoritative reads without snapshot writes", () => {
  const source = read(sourcePath);
  const flushOwner = functionBody(source, "flushOwner");

  assert.match(flushOwner, /fetchRecentAuthoritativeSnapshot\(normalizedOwner\)/);
  assert.match(flushOwner, /confirmedPendingTickets/);
  assert.match(flushOwner, /queueRepository\.removeByIds/);
  assert.doesNotMatch(source, /remoteStore\.upsertSnapshot\(/);
  assert.doesNotMatch(source, /fetchSnapshot\(normalizedOwner\)/);
});
