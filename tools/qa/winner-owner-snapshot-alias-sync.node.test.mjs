import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260531144500_refine_winner_owner_snapshot_alias_scope.sql",
  "utf8",
);
const preserveMigration = readFileSync(
  "supabase/migrations/20260531151000_server_authoritative_prize_overrides_paid_snapshot.sql",
  "utf8",
);
const hardeningMigration = readFileSync(
  "supabase/migrations/20260601170000_winner_payload_payment_hardening.sql",
  "utf8",
);
const tightenedAliasesMigration = readFileSync(
  "supabase/migrations/20260601184000_tighten_ticket_owner_aliases.sql",
  "utf8",
);
const optimizedPrizeSyncMigration = readFileSync(
  "supabase/migrations/20260602015748_optimize_prize_reconcile_owner_sync.sql",
  "utf8",
);

test("winner owner sync uses admin/cashier aliases for realtime snapshots", () => {
  assert.match(migration, /lotterynet_users_state/);
  assert.match(migration, /u->>'user'/);
  assert.match(migration, /u->>'adminUser'/);
  assert.match(migration, /u->>'cashierUser'/);
  assert.match(migration, /owner_row\.owner_key = any\(owner_keys\)/);
  assert.doesNotMatch(
    migration,
    /or nullif\(trim\(u->>'adminId'\), ''\) in \(select key_value from seed_keys/,
  );
});

test("winner owner sync patches the app fields used by admin and cashier screens", () => {
  assert.match(migration, /'status', app_status/);
  assert.match(migration, /'st', app_status/);
  assert.match(migration, /'totalPrize', prize_amount/);
  assert.match(migration, /'totalPremio', prize_amount/);
  assert.match(migration, /'winningDetails', winning_details/);
  assert.match(migration, /'serverPrizeAuthoritative', true/);
});

test("winner owner sync matches tickets by stable server and legacy identities", () => {
  assert.match(migration, /ticket_row\.legacy_ticket_id/);
  assert.match(migration, /ticket_row\.client_request_id/);
  assert.match(migration, /ticket_row\.id::text/);
  assert.match(migration, /ticket_row\.ticket_code/);
});

test("terminal snapshot protection keeps paid status but accepts server prize fixes", () => {
  assert.match(preserveMigration, /incoming_server_authoritative/);
  assert.match(preserveMigration, /serverPrizeAuthoritative/);
  assert.match(preserveMigration, /previous_status = any\(paid_statuses\)[\s\S]*incoming_server_authoritative/);
  assert.doesNotMatch(preserveMigration, /incoming_prize >= previous_prize/);
  assert.match(preserveMigration, /'status', 'paid'/);
  assert.match(preserveMigration, /'totalPrize', incoming_prize/);
});

test("winner payment hardening resyncs snapshots from prize and payment writes", () => {
  assert.match(hardeningMigration, /lotterynet_sync_winner_payload_from_ticket/);
  assert.match(hardeningMigration, /lotterynet_sync_winner_payload_from_prize_item/);
  assert.match(hardeningMigration, /after insert or update of status, estado, payout_amount, paid_at/);
  assert.match(hardeningMigration, /after insert or update of is_winner, payout_amount/);
  assert.match(hardeningMigration, /lotterynet_sync_ticket_owner_payload\(new\.id\)/);
  assert.match(hardeningMigration, /lotterynet_sync_ticket_owner_payload\(new\.ticket_id\)/);
});

test("winner payment hardening wakes all admin and cashier aliases", () => {
  assert.match(hardeningMigration, /lotterynet_ticket_owner_aliases/);
  assert.match(hardeningMigration, /u->>'adminUser'/);
  assert.match(hardeningMigration, /u->>'cashierUser'/);
  assert.match(hardeningMigration, /u->>'cashierId'/);
  assert.match(hardeningMigration, /foreach owner_key in array public\.lotterynet_ticket_owner_aliases\(new\)/);
  assert.match(hardeningMigration, /foreach owner_key in array public\.lotterynet_ticket_owner_aliases\(old\)/);
});

test("winner payment hardening has a bounded repair function for stale snapshots", () => {
  assert.match(hardeningMigration, /lotterynet_heal_winner_payloads_for_day/);
  assert.match(hardeningMigration, /limit greatest\(coalesce\(p_limit, 100\), 1\)/);
  assert.match(hardeningMigration, /lotterynet_ticket_date_aliases\(p_day_key\)/);
  assert.match(hardeningMigration, /coalesce\(tpi\.is_winner, false\)/);
});

test("ticket owner alias helper does not expand one admin ticket to every cashier", () => {
  assert.match(tightenedAliasesMigration, /lotterynet_ticket_owner_aliases/);
  assert.doesNotMatch(
    tightenedAliasesMigration,
    /or nullif\(trim\(u->>'adminId'\), ''\) in \(select key_value from seed_keys/,
  );
  assert.doesNotMatch(
    tightenedAliasesMigration,
    /or nullif\(trim\(u->>'adminUser'\), ''\) in \(select key_value from seed_keys/,
  );
  assert.match(tightenedAliasesMigration, /u->>'cashierId'/);
  assert.match(tightenedAliasesMigration, /u->>'cashierUser'/);
});

test("optimized prize sync avoids global owner snapshot scans during reconcile", () => {
  assert.match(optimizedPrizeSyncMigration, /lotterynet_sync_ticket_owner_payload/);
  assert.match(optimizedPrizeSyncMigration, /owner_keys := public\.lotterynet_ticket_owner_aliases\(ticket_row\)/);
  assert.match(optimizedPrizeSyncMigration, /owner_row\.owner_key = any\(owner_keys\)/);
  assert.doesNotMatch(
    optimizedPrizeSyncMigration,
    /where not \(owner_row\.owner_key = any\(owner_keys\)\)/,
  );
});

test("optimized prize reconcile has bounded owner repair and fast lookup indexes", () => {
  assert.match(optimizedPrizeSyncMigration, /tickets_reconcile_draw_date_real_active_idx/);
  assert.match(optimizedPrizeSyncMigration, /tickets_reconcile_legacy_day_active_idx/);
  assert.match(optimizedPrizeSyncMigration, /tickets_reconcile_draw_date_active_idx/);
  assert.match(optimizedPrizeSyncMigration, /ticket_items_reconcile_lottery_ticket_idx/);
  assert.match(optimizedPrizeSyncMigration, /ticket_items_reconcile_secondary_lottery_ticket_idx/);
  assert.match(optimizedPrizeSyncMigration, /result_reconcile_jobs_day_pending_idx/);
  assert.match(optimizedPrizeSyncMigration, /lotterynet_reconcile_owner_tickets_for_day/);
  assert.match(optimizedPrizeSyncMigration, /p_owner_key/);
});
