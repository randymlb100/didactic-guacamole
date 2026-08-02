import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const root = process.cwd();
const source = readFileSync(join(root, "supabase/functions/lotterynet-users-state/index.ts"), "utf8");
const localMergeSource = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/core/storage/LocalUsersDeletedRepository.kt"),
  "utf8",
);

test("users-state upsert preserves existing nonzero commission rates from stale full payloads", () => {
  assert.match(source, /collectExistingCommissions/);
  assert.match(source, /preserveExistingCommissions/);
  assert.match(source, /incomingRate === null \|\| \(incomingRate <= 0 && !explicitCommissionOverride\)/);
  assert.match(source, /user\.commissionRate = existingRate/);
  assert.match(source, /readCommissionOverrideKeys\(body\.commissionOverrideKeys\)/);
  assert.match(source, /payload = preserveExistingCommissions\(payload, existing\?\.payload \?\? null, commissionOverrideKeys\)/);
});

test("users-state lets current clients intentionally set commission to zero", () => {
  assert.match(source, /const explicitCommissionOverride = userKeys\(user\)\.some/);
  assert.match(source, /commissionOverrideKeys\.has\(key\)/);
});

test("users-state commission preservation reads every legacy user bucket", () => {
  for (const key of ["admins", "supervisores", "supervisors", "cajeros"]) {
    assert.match(source, new RegExp(`"${key}"`));
  }
});

test("local user merge does not let stale zero commission override an existing nonzero commission", () => {
  assert.match(localMergeSource, /withPreservedCommission/);
  assert.match(localMergeSource, /previousRate != null && previousRate > 0\.0/);
  assert.match(localMergeSource, /currentRate == null \|\| currentRate <= 0\.0/);
  assert.match(localMergeSource, /put\("commissionRate", previousRate\)/);
});
