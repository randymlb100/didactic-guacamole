import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { test } from "node:test";

const root = new URL("../../", import.meta.url);
const appStore = new URL("app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt", root);
const statusFunction = new URL("supabase/functions/get-results-status/index.ts", root);
const v2Function = new URL("supabase/functions/get-results-v2/index.ts", root);
const refreshFunction = new URL("supabase/functions/results-server-refresh/index.ts", root);
const sharedAdminHelper = new URL("supabase/functions/_shared/lotterynet-admin.ts", root);
const firstCutoverMigration = new URL("supabase/migrations/20260529020000_result_draws_first_cutover.sql", root);
const sellerPrizeMigration = new URL("supabase/migrations/20260529033000_force_prize_config_from_ticket_seller.sql", root);
const paidReconcileMigration = new URL("supabase/migrations/20260529034500_reconcile_paid_ticket_prizes_with_seller_config.sql", root);
const currentDayPrizeJobsMigration = new URL("supabase/migrations/20260531015000_process_current_day_result_prize_jobs.sql", root);
const ownerSnapshotPrizeMigration = new URL("supabase/migrations/20260531021500_respect_server_authoritative_prizes_in_owner_snapshot.sql", root);
const continuedPrizeJobsMigration = new URL("supabase/migrations/20260602162000_result_reconcile_jobs_continue_after_ticket_limit.sql", root);
const legacyResultsQueueMigration = new URL("supabase/migrations/20260602190000_legacy_results_tables_enqueue_reconcile_jobs.sql", root);
const resultDrawsQueueMigration = new URL("supabase/migrations/20260602195500_result_draws_enqueue_reconcile_trigger.sql", root);
const resultsWatchdogMigration = new URL("supabase/migrations/20260602202000_results_prize_watchdog_cron.sql", root);
const boundedPressureMigration = new URL("supabase/migrations/20260602154500_reduce_result_reconcile_pressure.sql", root);
const activeDayTriggerMigration = new URL("supabase/migrations/20260602161000_limit_result_draw_reconcile_trigger_to_active_days.sql", root);
const legacyActiveDayUpsertMigration = new URL("supabase/migrations/20260602165500_limit_legacy_result_upsert_reconcile_jobs_to_active_days.sql", root);
const serverOwnerPatchSkipMigration = new URL("supabase/migrations/20260602162500_skip_terminal_preserve_for_server_owner_patch.sql", root);
const deletedSnapshotGuardSkipMigration = new URL("supabase/migrations/20260602163500_skip_deleted_snapshot_guard_for_server_owner_patch.sql", root);
const forcedPreserveSkipMigration = new URL("supabase/migrations/20260602164500_force_preserve_skip_for_server_owner_patch.sql", root);
const stableResultHashMigration = new URL("supabase/migrations/20260603171000_stable_result_hash_and_reconcile_watchdog.sql", root);

test("normalized results edge function exists", () => {
  assert.equal(existsSync(v2Function), true);
});

test("Android result reads use get-results-v2 instead of dead fetch-results", () => {
  const source = readFileSync(appStore, "utf8");
  assert.match(source, /"get-results-v2"/);
  assert.doesNotMatch(source, /"fetch-results"/);
  assert.doesNotMatch(source, /class SupabaseResultsCacheClient/);
  assert.doesNotMatch(source, /rest\/v1\/lotterynet_kv/);
});

test("results status reads normalized result_draws instead of kv cache", () => {
  const source = readFileSync(statusFunction, "utf8");
  assert.match(source, /result_draws/);
  assert.doesNotMatch(source, /\.from\("lotterynet_kv"\)/);
});

test("server prize v2 always resolves payout config from ticket seller instead of stale snapshot", () => {
  const cutover = readFileSync(firstCutoverMigration, "utf8");
  const migration = readFileSync(sellerPrizeMigration, "utf8");
  assert.match(cutover, /lotterynet_resolve_ticket_prize_against_payload\(ticket, normalized_payload\)/);
  assert.match(migration, /lotterynet_ticket_payout_config\(ticket\)/);
  assert.match(migration, /config := public\.lotterynet_ticket_payout_config\(ticket\);/);
  assert.match(migration, /stale payoutConfigSnapshot branch/);
});

test("server prize v2 reconciles paid tickets without turning them back into pending winners", () => {
  const migration = readFileSync(paidReconcileMigration, "utf8");
  assert.match(migration, /lotterynet_reconcile_ticket_prize_v2/);
  assert.match(migration, /then ''PAGADO''/);
  assert.match(migration, /lotterynet_process_result_reconcile_jobs/);
  assert.match(migration, /perform public\.lotterynet_reconcile_ticket_prize_v2\(v_ticket\.id\)/);
  assert.match(migration, /skippedPaidTicketUpdate/);
  assert.match(migration, /v_next := replace\(/);
});

test("results refresh processes prize jobs for the refreshed day", () => {
  const migration = readFileSync(currentDayPrizeJobsMigration, "utf8");
  const refresh = readFileSync(refreshFunction, "utf8");
  const sharedAdmin = readFileSync(sharedAdminHelper, "utf8");
  assert.match(migration, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.match(migration, /p_result_day_key/);
  assert.match(migration, /lotterynet_reconcile_ticket_prize_v2\(ticket_row\.id\)/);
  assert.match(refresh, /processPrizeJobsForDay/);
  assert.match(refresh, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.match(refresh, /prizeReconcile/);
  assert.match(refresh, /processOnly === true/);
  assert.match(refresh, /configuredCronSecrets/);
  assert.match(refresh, /expected\.includes\(provided\)/);
  assert.match(refresh, /p_job_limit:\s*10/);
  assert.match(refresh, /p_ticket_limit:\s*300/);
  assert.doesNotMatch(refresh, /lotteryChanged \|\| pickChanged\s*\?\s*await processPrizeJobsForDay/);
  assert.match(refresh, /const prizeReconcile = await processPrizeJobsForDay\(date\);/);
  assert.match(sharedAdmin, /SUPABASE_SERVICE_ROLE_KEY/);
  assert.match(sharedAdmin, /SUPABASE_SECRET_KEY/);
});

test("large result reconcile jobs requeue instead of completing with hidden leftovers", () => {
  const migration = readFileSync(continuedPrizeJobsMigration, "utf8");
  assert.match(migration, /ticket_limit := greatest\(coalesce\(p_ticket_limit, 500\), 1\)/);
  assert.match(migration, /if job_tickets >= ticket_limit then/);
  assert.match(migration, /set status = 'pending'/);
  assert.match(migration, /Requeued after ticket limit/);
  assert.match(migration, /continuedJobs/);
  assert.doesNotMatch(migration, /Completed after ticket limit/);
});

test("legacy results tables enqueue prize jobs instead of reconciling tickets inline", () => {
  assert.equal(existsSync(legacyResultsQueueMigration), true);
  const migration = readFileSync(legacyResultsQueueMigration, "utf8");
  assert.match(migration, /lotterynet_enqueue_legacy_result_reconcile_jobs/);
  assert.match(migration, /lotterynet_upsert_result_draws_from_payload/);
  assert.match(migration, /drop trigger if exists lotterynet_pick_results_reconcile_tickets/);
  assert.match(migration, /drop trigger if exists lotterynet_results_reconcile_tickets/);
  assert.match(migration, /after insert or update of payload/);
  assert.doesNotMatch(migration, /lotterynet_reconcile_ticket_prize\(ticket_row\.id\)/);
});

test("direct result_draws writes enqueue prize jobs for winners", () => {
  assert.equal(existsSync(resultDrawsQueueMigration), true);
  const migration = readFileSync(resultDrawsQueueMigration, "utf8");
  assert.match(migration, /lotterynet_enqueue_result_draw_reconcile_job/);
  assert.match(migration, /after insert or update of source_hash, status, number_raw/);
  assert.match(migration, /on public\.result_draws/);
  assert.match(migration, /new\.status <> 'published'/);
  assert.match(migration, /lotterynet_enqueue_result_reconcile_job/);
  assert.match(migration, /rd\.result_date >= current_date - interval '2 days'/);
});

test("results watchdog keeps prize queue observable and bounded", () => {
  assert.equal(existsSync(resultsWatchdogMigration), true);
  const migration = readFileSync(resultsWatchdogMigration, "utf8");
  assert.match(migration, /lotterynet_results_health_log/);
  assert.match(migration, /lotterynet_results_prize_watchdog/);
  assert.match(migration, /created_at >= now\(\) - interval '12 hours'/);
  assert.match(migration, /limit 2/);
  assert.match(migration, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.match(migration, /lotterynet-results-prize-watchdog/);
});

test("result reconciliation cannot overlap or re-open old backlog pressure", () => {
  assert.equal(existsSync(boundedPressureMigration), true);
  assert.equal(existsSync(activeDayTriggerMigration), true);
  assert.equal(existsSync(legacyActiveDayUpsertMigration), true);
  const bounded = readFileSync(boundedPressureMigration, "utf8");
  const activeTrigger = readFileSync(activeDayTriggerMigration, "utf8");
  const legacyActiveUpsert = readFileSync(legacyActiveDayUpsertMigration, "utf8");
  assert.match(bounded, /pg_try_advisory_xact_lock/);
  assert.match(bounded, /result reconcile already running for this day/);
  assert.match(bounded, /ticket_limit := least/);
  assert.ok(bounded.includes("'*/10 * * * *'"));
  assert.match(activeTrigger, /new\.result_date < \(now\(\) at time zone 'America\/Santo_Domingo'\)::date - interval '1 day'/);
  assert.match(activeTrigger, /Deferred old result_draws trigger backlog/);
  assert.match(legacyActiveUpsert, /legacy result upsert only enqueues active-day prize jobs/);
  assert.match(legacyActiveUpsert, /lotterynet_enqueue_result_reconcile_job/);
  assert.match(legacyActiveUpsert, /Deferred old legacy result upsert backlog/);
});

test("server prize owner patches bypass expensive app snapshot guards", () => {
  assert.equal(existsSync(serverOwnerPatchSkipMigration), true);
  assert.equal(existsSync(deletedSnapshotGuardSkipMigration), true);
  assert.equal(existsSync(forcedPreserveSkipMigration), true);
  const serverPatch = readFileSync(serverOwnerPatchSkipMigration, "utf8");
  const deletedGuard = readFileSync(deletedSnapshotGuardSkipMigration, "utf8");
  const forcedPreserve = readFileSync(forcedPreserveSkipMigration, "utf8");
  assert.match(serverPatch, /set_config\(''lotterynet\.skip_preserve_terminal_ticket_state''/);
  assert.match(serverPatch, /current_setting\(''lotterynet\.skip_preserve_terminal_ticket_state''/);
  assert.match(deletedGuard, /ln_protect_ticket_owner_snapshot/);
  assert.match(deletedGuard, /return new/);
  assert.match(forcedPreserve, /lotterynet_preserve_terminal_ticket_state/);
  assert.match(forcedPreserve, /server-authoritative owner ticket patches bypass/);
});

test("owner realtime snapshot respects server-authoritative prizes", () => {
  const syncMigration = readFileSync(ownerSnapshotPrizeMigration, "utf8");
  assert.match(syncMigration, /lotterynet_preserve_terminal_ticket_state/);
  assert.match(syncMigration, /serverPrizeAuthoritative/);
  assert.match(syncMigration, /calculated_prize := incoming_prize/);
  assert.match(syncMigration, /lotterynet_sync_ticket_owner_payload/);
});

test("result hash ignores scraper timestamps so repeated sightings do not enqueue prize jobs", () => {
  assert.equal(existsSync(stableResultHashMigration), true);
  const migration = readFileSync(stableResultHashMigration, "utf8");
  assert.match(migration, /lotterynet_result_draw_stable_hash/);
  assert.match(migration, /p_lottery_legacy_id/);
  assert.match(migration, /p_game/);
  assert.match(migration, /p_draw_name/);
  assert.match(migration, /p_number_raw/);
  assert.match(migration, /p_status/);
  assert.match(migration, /lastSeenAt/);
  assert.match(migration, /firstSeenAt/);
  assert.doesNotMatch(migration, /md5\(row_value::text\)/);
  assert.match(migration, /lotterynet_results_prize_watchdog\(8,\s*300\)/);
  assert.match(migration, /'\*\/2 \* \* \* \*'/);
});
