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
const stableHashActiveDayGuardMigration = new URL("supabase/migrations/20260604164500_preserve_active_day_guard_after_stable_result_hash.sql", root);
const repeatedQuinielaMigration = new URL("supabase/migrations/20260711143000_repeat_quiniela_hits_reconcile.sql", root);
const serverOwnerPatchSkipMigration = new URL("supabase/migrations/20260602162500_skip_terminal_preserve_for_server_owner_patch.sql", root);
const deletedSnapshotGuardSkipMigration = new URL("supabase/migrations/20260602163500_skip_deleted_snapshot_guard_for_server_owner_patch.sql", root);
const forcedPreserveSkipMigration = new URL("supabase/migrations/20260602164500_force_preserve_skip_for_server_owner_patch.sql", root);
const stableResultHashMigration = new URL("supabase/migrations/20260603171000_stable_result_hash_and_reconcile_watchdog.sql", root);
const narrowResultCandidateMigration = new URL("supabase/migrations/20260619153000_narrow_result_reconcile_candidates.sql", root);
const kvSensitiveReadMigration = new URL("supabase/migrations/20260606173500_block_sensitive_kv_public_reads.sql", root);
const kvDeleteRevokeMigration = new URL("supabase/migrations/20260606175500_revoke_kv_public_delete.sql", root);
const kvWriteRevokeMigration = new URL("supabase/migrations/20260606180500_revoke_kv_public_writes.sql", root);
const kvReadRevokeMigration = new URL("supabase/migrations/20260606181500_close_kv_public_reads.sql", root);
const resultDrawsReadRevokeMigration = new URL("supabase/migrations/20260606183500_close_result_draws_public_read.sql", root);
const dynamicReadinessGateMigration = new URL("supabase/migrations/20260606184500_dynamic_readiness_release_gate.sql", root);

test("normalized results edge function exists", () => {
  assert.equal(existsSync(v2Function), true);
});

test("Android result reads use get-results-v2 instead of dead fetch-results", () => {
  const source = readFileSync(appStore, "utf8");
  const activity = readFileSync(new URL("app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt", root), "utf8");
  const salesActivity = readFileSync(new URL("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt", root), "utf8");
  const wrapper = readFileSync(new URL("app/src/main/java/com/lotterynet/pro/core/results/ResultsSupabaseStore.kt", root), "utf8");
  assert.match(source, /"get-results-v2"/);
  assert.match(source, /bearerTokenProvider\?\.invoke\(\)/);
  assert.match(activity, /bearerTokenProvider = \{ sessionTokenProvider\.freshAccessToken\(\) \}/);
  assert.match(salesActivity, /ResultsSupabaseStore\(\s*bearerTokenProvider = \{ sessionTokenProvider\.freshAccessToken\(\) \},\s*\)/s);
  assert.match(wrapper, /SupabaseResultsRemoteStore\(bearerTokenProvider = bearerTokenProvider\)/);
  assert.doesNotMatch(source, /"fetch-results"/);
  assert.doesNotMatch(source, /class SupabaseResultsCacheClient/);
  assert.doesNotMatch(source, /rest\/v1\/lotterynet_kv/);
});

test("results status reads normalized result_draws instead of kv cache", () => {
  const source = readFileSync(statusFunction, "utf8");
  assert.match(source, /result_draws/);
  assert.doesNotMatch(source, /\.from\("lotterynet_kv"\)/);
});

test("result_draws direct public read is closed behind edge and broadcast paths", () => {
  const readRevoke = readFileSync(resultDrawsReadRevokeMigration, "utf8");
  const readiness = readFileSync(dynamicReadinessGateMigration, "utf8");
  const diagnostic = readFileSync(new URL("tools/qa/results-stack-diagnostic.mjs", root), "utf8");
  const pickSmoke = readFileSync(new URL("tools/qa/pick-mode-results-smoke.mjs", root), "utf8");

  assert.match(readRevoke, /revoke select on table public\.result_draws from anon/);
  assert.match(readRevoke, /revoke select on table public\.result_draws from authenticated/);
  assert.match(readRevoke, /drop policy if exists result_draws_read_all/);
  assert.match(readRevoke, /get-results-v2 \/ get-results-status/);
  assert.match(readRevoke, /private Realtime Broadcast/);

  assert.match(readiness, /canRevokeDirectTableAccess', v_gate_open/);
  assert.match(readiness, /permissivePublicPolicyCount/);
  assert.match(readiness, /jsonb_array_length\(v_public_grants\) = 0/);

  assert.match(diagnostic, /result_draws direct blocked/);
  assert.match(diagnostic, /get-results-v2/);
  assert.doesNotMatch(diagnostic, /Supabase result_draws responde por REST/);
  assert.match(pickSmoke, /sin leer result_draws directo/);
  assert.doesNotMatch(pickSmoke, /result_draws candidate check/);
});

test("lotterynet kv direct public access is closed", () => {
  const readMigration = readFileSync(kvSensitiveReadMigration, "utf8");
  const deleteMigration = readFileSync(kvDeleteRevokeMigration, "utf8");
  const writeMigration = readFileSync(kvWriteRevokeMigration, "utf8");
  const closeReadMigration = readFileSync(kvReadRevokeMigration, "utf8");

  assert.match(readMigration, /LotteryNet compatibility read non-sensitive kv/);
  assert.match(readMigration, /lotterynet_results_cron_secret/);
  assert.match(readMigration, /sys_rldly_client_secret/);
  assert.match(readMigration, /sys_results_refresh_lock%/);

  assert.match(deleteMigration, /revoke delete on table public\.lotterynet_kv from anon/);
  assert.match(deleteMigration, /drop policy if exists kv_delete_allowed/);

  assert.match(writeMigration, /revoke insert, update on table public\.lotterynet_kv from anon/);
  assert.match(writeMigration, /revoke insert, update on table public\.lotterynet_kv from authenticated/);
  assert.match(writeMigration, /drop policy if exists kv_insert_legacy_anon_only/);
  assert.match(writeMigration, /drop policy if exists kv_update_legacy_anon_only/);

  assert.match(closeReadMigration, /revoke select on table public\.lotterynet_kv from anon/);
  assert.match(closeReadMigration, /revoke select on table public\.lotterynet_kv from authenticated/);
  assert.match(closeReadMigration, /drop policy if exists "LotteryNet compatibility read non-sensitive kv"/);
});

test("web dashboard does not subscribe to internal Supabase tables", () => {
  const dashboard = readFileSync(new URL("proyecto web nuevo/src/views/Dashboard.tsx", root), "utf8");
  const webQueries = readFileSync(new URL("proyecto web nuevo/src/utils/supabase/queries.ts", root), "utf8");

  assert.doesNotMatch(dashboard, /table:\s*['"]lotterynet_kv['"]/);
  assert.doesNotMatch(dashboard, /table:\s*['"]lotterynet_users_state['"]/);
  assert.doesNotMatch(dashboard, /postgres_changes/);
  assert.doesNotMatch(webQueries, /from\(['"]lotterynet_kv['"]\)/);
  assert.doesNotMatch(webQueries, /lot_results_cache_by_day|pick_results_cache_by_day/);
});

test("server prize v2 always resolves payout config from ticket seller instead of stale snapshot", () => {
  const cutover = readFileSync(firstCutoverMigration, "utf8");
  const migration = readFileSync(sellerPrizeMigration, "utf8");
  assert.match(cutover, /lotterynet_resolve_ticket_prize_against_payload\(ticket, normalized_payload\)/);
  assert.match(migration, /lotterynet_ticket_payout_config\(ticket\)/);
  assert.match(migration, /config := public\.lotterynet_ticket_payout_config\(ticket\);/);
  assert.match(migration, /stale payoutConfigSnapshot branch/);
});

test("quiniela reconciliation counts repeated result positions instead of stopping at the first hit", () => {
  assert.equal(existsSync(repeatedQuinielaMigration), true);
  const migration = readFileSync(repeatedQuinielaMigration, "utf8");

  assert.match(migration, /lotterynet_quiniela_hit_positions/);
  assert.match(migration, /matched_positions := public\.lotterynet_quiniela_hit_positions/);
  assert.match(migration, /item_payout :=/s);
  assert.match(migration, /digits = first_pick/);
  assert.match(migration, /digits = second_pick/);
  assert.match(migration, /digits = third_pick/);
  assert.match(migration, /array_to_string\(matched_positions, ','\)/);
  assert.match(migration, /lotterynet_resolve_ticket_prize_multi_hit/);
  assert.match(migration, /return public\.lotterynet_resolve_ticket_prize_multi_hit\(ticket, result_payload\);/);
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
  assert.equal(existsSync(stableHashActiveDayGuardMigration), true);
  const bounded = readFileSync(boundedPressureMigration, "utf8");
  const activeTrigger = readFileSync(activeDayTriggerMigration, "utf8");
  const legacyActiveUpsert = readFileSync(legacyActiveDayUpsertMigration, "utf8");
  const stableHashActiveGuard = readFileSync(stableHashActiveDayGuardMigration, "utf8");
  assert.match(bounded, /pg_try_advisory_xact_lock/);
  assert.match(bounded, /result reconcile already running for this day/);
  assert.match(bounded, /ticket_limit := least/);
  assert.ok(bounded.includes("'*/10 * * * *'"));
  assert.match(activeTrigger, /new\.result_date < \(now\(\) at time zone 'America\/Santo_Domingo'\)::date - interval '1 day'/);
  assert.match(activeTrigger, /Deferred old result_draws trigger backlog/);
  assert.match(legacyActiveUpsert, /legacy result upsert only enqueues active-day prize jobs/);
  assert.match(legacyActiveUpsert, /lotterynet_enqueue_result_reconcile_job/);
  assert.match(legacyActiveUpsert, /Deferred old legacy result upsert backlog/);
  assert.match(stableHashActiveGuard, /stable hash upsert only enqueues active-day prize jobs/);
  assert.match(stableHashActiveGuard, /Deferred old stable-hash result backlog/);
});

test("result reconciliation only evaluates tickets that can match winning draws", () => {
  assert.equal(existsSync(narrowResultCandidateMigration), true);
  const migration = readFileSync(narrowResultCandidateMigration, "utf8");

  assert.match(migration, /lotterynet_ticket_item_can_win_against_draw/);
  assert.match(migration, /candidate_draws/);
  assert.match(migration, /exists \(/);
  assert.match(migration, /lotterynet_is_permutation_match/);
  assert.match(migration, /lotterynet_pick_box_way/);
  assert.match(migration, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.doesNotMatch(migration, /select \* from public\.tickets t/);
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

test("results refresh compares stable draw signatures before writing rpc or legacy cache", () => {
  const refresh = readFileSync(refreshFunction, "utf8");
  assert.match(refresh, /stableResultRowsSignature/);
  assert.match(refresh, /RESULT_SIGNATURE_FIELDS/);
  assert.match(refresh, /stableResultRowsSignature\(currentRows\) === stableResultRowsSignature\(effectiveRows\)/);
  assert.match(refresh, /stableResultRowsSignature\(currentRows\) !== stableResultRowsSignature\(rows\)/);
  assert.doesNotMatch(refresh, /stableStringify\(currentRows\) === stableStringify\(effectiveRows\)/);
  assert.doesNotMatch(refresh, /stableStringify\(currentRows\) !== stableStringify\(rows\)/);
});

test("results refresh uses Supabase result_draws without automatic Render fallback", () => {
  const refresh = readFileSync(refreshFunction, "utf8");
  assert.match(refresh, /currentStoredResultsFor/);
  assert.match(refresh, /const stored = await currentStoredResultsFor\(date\);/);
  assert.match(refresh, /source: "supabase-result-draws"/);
  assert.match(refresh, /source: "render-fallback"/);
  assert.match(refresh, /const live = !forceLiveRefresh[\s\S]*source: "supabase-result-draws"[\s\S]*fetchLiveResultsOrEmpty\(date\)/);
  assert.match(refresh, /const split = !forceLiveRefresh \? stored : splitPayload\(live\.payload\)/);
});
