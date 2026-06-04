import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

function read(path) {
  return readFileSync(path, "utf8");
}

test("ticket owner canonicalization does not assign ambiguous display names", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/operations/TicketOwnerCanonicalization.kt");
  const tests = read("app/src/test/java/com/lotterynet/pro/core/operations/TicketOwnerCanonicalizationTest.kt");

  assert.match(source, /resolveUniqueCashierByDisplayName/);
  assert.match(source, /matches\.singleOrNull\(\)/);
  assert.match(source, /sameActorKey\(account\.id, key\) \|\| sameActorKey\(account\.user, key\)/);
  assert.doesNotMatch(
    source,
    /sameActorKey\(account\.id, key\) \|\| sameActorKey\(account\.user, key\) \|\| sameActorKey\(account\.displayName, key\)/,
  );
  assert.match(tests, /cashier display name canonicalizes only when unique under admin/);
  assert.match(tests, /ambiguous cashier display name does not canonicalize to first cashier/);
});

test("operational scope includes cashier display names only when unique", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/operations/OperationalScope.kt");
  const tests = read("app/src/test/java/com/lotterynet/pro/core/operations/OperationalScopeContractsTest.kt");

  assert.match(source, /cashierIdentityKeys/);
  assert.match(source, /displayNameCounts/);
  assert.match(source, /displayNameCounts\[displayNameKey\] == 1/);
  assert.doesNotMatch(source, /identityKeys\(cashier\.id, cashier\.user, cashier\.displayName\)/);
  assert.match(tests, /cashier sees own winning ticket when server sends seller display name/);
  assert.match(tests, /cashier display name scope is ignored when duplicated in same admin/);
});

test("server actor lookup accepts auth uid and operational aliases for destructive ticket actions", () => {
  const migration = read("supabase/migrations/20260602133500_actor_state_matches_auth_user_aliases.sql");

  assert.match(migration, /create or replace function public\.ln_actor_from_legacy_state\(p_actor_key text\)/);
  assert.match(migration, /authUserId/);
  assert.match(migration, /auth_user_id/);
  assert.match(migration, /adminId/);
  assert.match(migration, /adminUser/);
  assert.match(migration, /cashierId/);
  assert.match(migration, /cashierUser/);
  assert.match(migration, /supervisorId/);
  assert.match(migration, /supervisorUser/);
  assert.match(migration, /revoke all on function public\.ln_actor_from_legacy_state\(text\)/);
});
