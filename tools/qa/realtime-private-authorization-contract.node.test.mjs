import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260725120000_restore_private_realtime_authorization.sql",
  "utf8",
);

test("Realtime authorization is migrated atomically to the private schema", () => {
  assert.match(migration, /^begin;\s/m);
  assert.match(migration, /commit;\s*$/m);
  assert.match(migration, /create or replace function private\.lotterynet_can_receive_realtime_topic/);
  assert.match(migration, /auth\.uid\(\) is null/);
  assert.match(migration, /v_topic = 'ln:users:global'/);
  assert.match(migration, /private\.lotterynet_realtime_actor_aliases\(\)/);
  assert.match(migration, /private\.lotterynet_can_receive_realtime_topic\(\(select realtime\.topic\(\)\)\)/);
});

test("Realtime authorization does not grant client execution on the public helper", () => {
  assert.match(
    migration,
    /revoke all on function public\.lotterynet_can_receive_realtime_topic\(text\)[\s\S]*from public, anon, authenticated;/,
  );
  assert.doesNotMatch(
    migration,
    /grant execute on function public\.lotterynet_can_receive_realtime_topic\(text\)[\s\S]*to authenticated/,
  );
});

test("Services-games operation ledger remains server-only under RLS", () => {
  const policy = readFileSync(
    "supabase/migrations/20260725123000_lock_services_games_operations_to_server.sql",
    "utf8",
  );
  assert.match(policy, /^begin;\s/m);
  assert.match(policy, /create policy services_games_operations_internal_only/);
  assert.match(policy, /as restrictive/);
  assert.match(policy, /for all/);
  assert.match(policy, /to anon, authenticated/);
  assert.match(policy, /using \(false\)/);
  assert.match(policy, /with check \(false\)/);
  assert.match(policy, /commit;\s*$/m);
});
