import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migrationPath =
  "supabase/migrations/20260618213300_emergency_isolate_ticket_create_from_snapshot_touch.sql";
const rollbackPath =
  "supabase/migrations/rollback/20260618213300_emergency_isolate_ticket_create_from_snapshot_touch.rollback.sql";

test("emergency sale isolation disables only the ticket owner snapshot touch trigger", () => {
  const migration = readFileSync(migrationPath, "utf8");

  assert.match(
    migration,
    /alter table public\.tickets\s+disable trigger lotterynet_ticket_owner_realtime_touch/i,
  );
  assert.doesNotMatch(migration, /disable trigger all/i);
  assert.doesNotMatch(migration, /trg_ln_ticket_cashier_day_sale_limit/i);
  assert.doesNotMatch(migration, /trg_ln_ticket_item_sale_limit/i);
});

test("emergency sale isolation has a direct rollback", () => {
  const rollback = readFileSync(rollbackPath, "utf8");

  assert.match(
    rollback,
    /alter table public\.tickets\s+enable trigger lotterynet_ticket_owner_realtime_touch/i,
  );
});
