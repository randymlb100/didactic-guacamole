import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migrationPath =
  "supabase/migrations/20260619010000_quarantine_invalid_ticket_owner_snapshots.sql";
const rollbackPath =
  "supabase/migrations/rollback/20260619010000_quarantine_invalid_ticket_owner_snapshots.rollback.sql";

test("invalid owner quarantine is backup-only and blocks new placeholder owners", async () => {
  const migration = await readFile(migrationPath, "utf8");

  assert.match(migration, /lotterynet_ticket_owner_snapshot_quarantine/);
  assert.match(migration, /extensions\.digest\(owner_row\.payload::text, 'sha256'\)/);
  assert.match(migration, /on conflict \(owner_key\) do nothing/);
  assert.match(migration, /lower\(trim\(new\.owner_key\)\) in \('null', 'undefined'\)/);
  assert.doesNotMatch(migration, /delete\s+from\s+public\.lotterynet_tickets_by_owner/i);
  assert.doesNotMatch(migration, /update\s+public\.tickets/i);
  assert.doesNotMatch(migration, /delete\s+from\s+public\.tickets/i);
});

test("invalid owner quarantine has a checksum-verified rollback", async () => {
  const rollback = await readFile(rollbackPath, "utf8");

  assert.match(rollback, /insert into public\.lotterynet_tickets_by_owner/);
  assert.match(rollback, /where not exists/);
  assert.match(rollback, /Rollback checksum mismatch/);
  assert.match(rollback, /extensions\.digest\(restored\.payload::text, 'sha256'\)/);
});
