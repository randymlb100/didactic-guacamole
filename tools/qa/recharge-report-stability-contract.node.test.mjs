import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const rechargeSync = readFileSync('app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeCloudSyncCoordinator.kt', 'utf8');
const localRecharge = readFileSync('app/src/main/java/com/lotterynet/pro/core/storage/LocalRechargeRepository.kt', 'utf8');
const reportActivity = readFileSync('app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt', 'utf8');

test('recharge hydration does not erase local sales before merging with remote', () => {
  const hydrateBody = rechargeSync.match(/fun hydrateOwner\(ownerKey: String\): NativeRechargeCloudSyncResult \{([\s\S]*?)\n    \}/)?.[1] ?? '';

  assert.match(hydrateBody, /flushOwner\(normalizedOwner\)/);
  assert.doesNotMatch(hydrateBody, /replaceScopedImportedRecharges/);
});

test('recharge flush merges local and remote before replacing report cache', () => {
  assert.match(rechargeSync, /val localRecharges = rechargeRepository\.getRechargesForOwner\(normalizedOwner\)/);
  assert.match(rechargeSync, /val remoteRecharges = remoteStore\.fetchRecharges\(normalizedOwner\)/);
  assert.match(rechargeSync, /mergeRechargesPreferImported\(\s*existing = remoteRecharges,\s*imported = localRecharges,/s);
  assert.match(localRecharge, /replaceScopedImportedRecharges/);
});

test('operational report hydrates recargas through the merge-safe coordinator', () => {
  assert.match(reportActivity, /NativeRechargeCloudSyncCoordinator\(rechargeRepository\)\.hydrateOwner\(ownerKey\)/);
});
