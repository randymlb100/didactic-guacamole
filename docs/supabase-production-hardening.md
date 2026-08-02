# Supabase production hardening

This project keeps security changes behind compatibility gates. Do not revoke
client-visible access until the native Android flow has a tested Edge Function
replacement.

## Internal test rule

- Internal server checks run through private SQL functions or Edge Functions
  protected by an admin shared secret.
- Never expose `service_role` or secret keys to the app.
- Never add broad public policies to make a test pass.
- A test helper must be read-only unless its name and payload clearly say it
  mutates production data.

## Current safe maintenance

`private.lotterynet_safe_maintenance(...)` is the only scheduled cleanup job.

It may delete:

- Tickets with `deleted_at` older than the retention window.
- Only deleted tickets without matching rows in `pagos`.
- Result cache keys older than the retention window.
- Old OTA, result health, and completed/failed reconcile logs.

It must not delete:

- Active tickets.
- Paid tickets.
- Voided tickets that are not marked with `deleted_at`.
- User state, finance rows, balances, lottery config, or master config.

## Readiness report

`private.lotterynet_server_readiness_report()` is read-only. It reports:

- Latest maintenance run.
- Lotterynet cron jobs.
- Hot table row/scan stats.
- Public policies and grants that still need review.

Use this before changing RLS, grants, cron schedules, or Edge Function auth.

## 2026-06-06 closeout progress

Implemented and applied to production:

- `results-server-refresh` now has:
  - a service-only TTL lock to prevent overlapping cron refreshes,
  - a shorter Render timeout,
  - a recent-success short-circuit,
  - stable result-signature comparisons,
  - and `sys_results_refresh_state` / `sys_results_refresh_lock` status keys.
- `private.lotterynet_cron_health_report()` reports recent cron runs by joining
  `cron.job` to `cron.job_run_details`.
- Removed unused direct public privileges:
  - `tickets`, `lotterynet_users_state`, and `lotterynet_kv` no longer expose
    direct `TRUNCATE`, `TRIGGER`, or `REFERENCES` to anon/authenticated roles.
  - `result_draws` direct anon/authenticated `SELECT` and mutation grants were
    revoked; result reads now go through Edge Functions.
  - `lotterynet_push_tokens` has no direct anon/authenticated grants; clients
    must use `register-push-token`.
- Trigger-only realtime broadcast functions no longer expose public RPC execute.
- Internal/service-only tables now have explicit deny-all policies instead of
  relying on implicit "RLS enabled, no policy" behavior.
- `lotterynet_result_draw_stable_hash(...)` has a fixed `search_path`.
- `lotterynet_users_state` direct public table access is closed:
  - anon/authenticated direct `SELECT` was revoked,
  - compatibility public read policy was dropped,
  - `lotterynet-users-state` Edge fetch still returns `200`,
  - direct REST read now returns permission denied,
  - unauthenticated Edge upsert still returns `401`.
- `lotterynet_kv` first stopped exposing known sensitive keys through public reads:
  - `lotterynet_results_cron_secret`,
  - `sys_rldly_client_secret`,
  - `sys_results_refresh_lock%`.
- `lotterynet_kv` direct public mutation access is closed:
  - anon/authenticated direct `INSERT`, `UPDATE`, and `DELETE` were revoked,
  - legacy KV write/delete policies were dropped,
  - Android ticket/result/user/config mutations use Edge Functions, so service
    paths keep working without public table writes.
- `lotterynet_kv` direct public read access is now closed:
  - anon/authenticated direct `SELECT` was revoked,
  - the compatibility read policy was dropped,
  - direct REST reads now return permission denied,
  - `get-master-config` Edge probe still returns `200`, confirming the server
    path remains available.

Verified:

- `deno check supabase/functions/results-server-refresh/index.ts`
- `node --test tools/qa/results-migration-contract.node.test.mjs tools/qa/broadcast-redis-sentry-contract.node.test.mjs tools/qa/supabase-edge-auth-contract.node.test.mjs`
- Public REST probes against `lotterynet_kv` now return `401` for direct read,
  insert, update, and delete attempts.
- `get-master-config` Edge `probe` still returns `200`.
- Latest readiness report shows `lotterynet_kv` and `tickets` removed from
  `publicGrantsToReview`; the remaining direct public read decision is
  `result_draws`.
- `tickets` direct public read access is closed:
  - anon/authenticated direct `SELECT` was revoked,
  - the public `SELECT true` policy was dropped,
  - direct REST reads now return permission denied,
  - `get-ticket-list` anonymous `updated-at` compatibility path still returns
    `200` through the Edge Function.
- `result_draws` direct public read access is closed:
  - anon/authenticated direct `SELECT` was revoked,
  - the public `result_draws_read_all` policy was dropped,
  - direct REST reads now return permission denied,
  - Android result reads use `get-results-v2` / `get-results-status`,
  - Android result live updates use private `ln:results:<day>` Realtime
    Broadcast instead of Postgres Changes fallback.
- Latest readiness report is green:
  - `releaseGate.canRevokeDirectTableAccess = true`,
  - `publicGrantsToReview = []`,
  - permissive public policy count is `0`.
- Supabase security advisor now reports only controlled warnings:
  - `pg_net` installed in public.
  - realtime authorization helper functions executable by authenticated users.
  - Auth leaked password protection disabled.

Known current issue:

- Render Web Service compatibility endpoints currently return `500`; result
  refresh no longer depends on them unless `forceLiveRefresh` is explicitly
  requested. Render Cron remains the source that writes results into Supabase.

## Permission closing gate

Direct Data API access is closed for the reviewed production tables.

Keep this gate green by verifying:

1. The Android app uses JWT Edge Functions for the same data.
2. Clean install hydration passes.
3. Cashier sale appears in admin tickets.
4. Result hydration and winner reconciliation pass.
5. Number limits/blocking update live.
6. Server logs show no unexpected 401/403/500 for 24 hours.

## First cutover candidate

`lotterynet_users_state` moved first because Android now prefers the
`lotterynet-users-state` Edge Function.

The Edge Function rule is:

- `fetch` may be called during clean install/login hydration.
- `upsert` requires an admin/master Supabase Auth JWT.
- Direct REST is closed. Rollback is in
  `supabase/migrations/20260606172500_tighten_lotterynet_users_state_access.sql`.
- Direct KV delete rollback is in
  `supabase/migrations/20260606175500_revoke_kv_public_delete.sql`.
- Direct KV write rollback is in
  `supabase/migrations/20260606180500_revoke_kv_public_writes.sql`.
- Direct KV read rollback is in
  `supabase/migrations/20260606181500_close_kv_public_reads.sql`.
- Direct ticket read rollback is in
  `supabase/migrations/20260606182500_close_tickets_public_read.sql`.
- Direct result read rollback is in
  `supabase/migrations/20260606183500_close_result_draws_public_read.sql`.
- Dynamic readiness gate rollback is the previous static definition in
  `supabase/migrations/20260606152000_server_readiness_report.sql`.

## Production-safe order

1. Add read-only reports and dry-run tools.
2. Add replacement Edge Functions.
3. Switch Android to the replacement paths.
4. Run smoke tests.
5. Only then tighten grants/RLS.
6. Keep rollback SQL ready for every permission change.
