import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260601183000_void_ticket_code_lookup_and_snapshot_sync.sql",
  "utf8",
);
const restoredTicketMigration = readFileSync(
  "supabase/migrations/20260601185000_protect_snapshot_deleted_ids_respects_restored_tickets.sql",
  "utf8",
);
const legacyApkVoidWindowMigration = readFileSync(
  "supabase/migrations/20260602103000_extend_cashier_void_window_for_legacy_apk.sql",
  "utf8",
);
const legacyEstadoMigration = readFileSync(
  "supabase/migrations/20260602104000_void_ticket_sets_legacy_estado.sql",
  "utf8",
);
const scopedSnapshotCleanupMigration = readFileSync(
  "supabase/migrations/20260602105000_limit_void_snapshot_cleanup_to_ticket_owners.sql",
  "utf8",
);

test("void-ticket can resolve app visible LN ticket codes", () => {
  assert.match(migration, /ticket_code = nullif\(p_body ->> 'ticketId', ''\)/);
  assert.match(migration, /ticket_code = nullif\(p_body ->> 'localTicketId', ''\)/);
  assert.match(migration, /ticket_code = nullif\(p_body ->> 'clientRequestId', ''\)/);
});

test("void-ticket syncs owner snapshots after delete or void", () => {
  assert.match(migration, /lotterynet_sync_ticket_owner_payload\(v_ticket\.id\)/);
  assert.match(migration, /ln_void_ticket_legacy\(jsonb\)/);
});

test("snapshot protection does not keep restored active tickets in deletedIds", () => {
  assert.match(restoredTicketMigration, /ln_protect_ticket_owner_snapshot/);
  assert.match(restoredTicketMigration, /tk\.deleted_at is null/);
  assert.match(restoredTicketMigration, /tk\.voided_at is null/);
  assert.match(restoredTicketMigration, /tk\.invalidated_at is null/);
  assert.match(restoredTicketMigration, /tk\.ticket_code = d\.id/);
  assert.match(restoredTicketMigration, /not exists \(/);
});

test("legacy apk cashier delete window gives delayed duplicate taps time to clean up", () => {
  assert.match(legacyApkVoidWindowMigration, /ln_void_ticket_legacy\(jsonb\)/);
  assert.match(legacyApkVoidWindowMigration, /interval ''15 minutes''/);
  assert.doesNotMatch(legacyApkVoidWindowMigration, /interval ''60 minutes''/);
});

test("legacy apk delete marks both modern status and old estado fields", () => {
  assert.match(legacyEstadoMigration, /ln_void_ticket_legacy\(jsonb\)/);
  assert.match(legacyEstadoMigration, /set status = v_next_status,\s*estado = v_next_status,/);
  assert.match(legacyEstadoMigration, /where upper\(coalesce\(status, ''\)\) in \('BORRADO', 'ANULADO', 'INVALIDADO'\)/);
});

test("void snapshot cleanup is scoped to ticket owners for fast legacy deletes", () => {
  assert.match(scopedSnapshotCleanupMigration, /ln_mark_owner_snapshots_ticket_deleted/);
  assert.match(scopedSnapshotCleanupMigration, /where s\.owner_key = any\(v_owner_keys\)/);
  assert.doesNotMatch(scopedSnapshotCleanupMigration, /or s\.owner_key = any/);
  assert.doesNotMatch(scopedSnapshotCleanupMigration, /where exists \(/);
});
