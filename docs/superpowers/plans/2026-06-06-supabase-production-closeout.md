# Supabase Production Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. For independent audit work, use `superpowers:subagent-driven-development`.

**Goal:** Close Supabase production hardening without breaking cashier, admin, tickets, results, realtime, limits, blocks, payments, or clean-install hydration.

**Architecture:** Compatibility-first hardening. First move every app dependency away from direct public PostgREST table access and into Edge Functions/RPC with JWT or server-only secrets. Then verify live flows. Only after evidence is clean, revoke public grants/policies in small migrations with rollback SQL.

**Tech Stack:** Supabase Postgres/RLS/PostgREST, Edge Functions, Realtime, pg_cron, Android Kotlin/Compose, Node/server smoke tests.

---

## Current Findings

- `private.lotterynet_safe_maintenance` is installed and scheduled as `lotterynet-safe-maintenance` at `20 4 * * *`.
- Last maintenance cleanup removed only safe candidates: old soft-deleted tickets without payments and old result-cache keys. Active tickets and paid tickets were not touched.
- `private.lotterynet_server_readiness_report()` now reports
  `releaseGate.canRevokeDirectTableAccess = true` after the direct public Data
  API grants were closed.
- `lotterynet-users-state` Edge function is deployed and verified:
  - `fetch` works without admin JWT.
  - `upsert` returns `401` without admin JWT.
  - Android store now prefers Edge first, then direct Supabase fallback, then Render.
- `results-server-refresh` had repeated slow calls around `43s-44s`. A service-only TTL lock, shorter Render timeout, recent-success short-circuit, and status KV were implemented on 2026-06-06.
- Realtime logs show unauthorized channel reads for topics like `ln:tickets:owner:adm-163c38`. This likely needs owner/topic alias normalization before closing realtime topic authorization.
- Public/security review still shows broad direct exposure:
  - `tickets` direct public `SELECT` was closed on 2026-06-06; `get-ticket-list`
    keeps the anonymous `updated-at` compatibility path through Edge.
  - `lotterynet_users_state` still has compatibility public read until the Edge-first cutover proves stable.
  - `lotterynet_kv` still has compatibility public read for non-sensitive keys,
    but direct anon/authenticated mutation grants and legacy write/delete
    policies were revoked on 2026-06-06.
  - `result_draws` direct public read and mutation grants were revoked on
    2026-06-06; result reads now go through Edge Functions and live updates use
    private Realtime Broadcast.
  - `lotterynet_push_tokens` direct anon/authenticated grants were revoked on 2026-06-06.

## Official References Checked

- Supabase API/RLS hardening: https://supabase.com/docs/guides/api/securing-your-api
- Supabase Row Level Security: https://supabase.com/docs/guides/database/postgres/row-level-security
- Supabase Edge Function auth: https://supabase.com/docs/guides/functions/auth
- Supabase Edge Function auth headers: https://supabase.com/docs/guides/functions/auth-headers
- Supabase cron/pg_cron: https://supabase.com/docs/guides/cron

## Non-Negotiable Rules

- Do not revoke direct table access until the app path has an Edge/RPC replacement and a rollback exists.
- Do not delete production tickets, payments, balances, user state, limits, blocks, config, or finance records.
- Cleanup can delete only old soft-deleted rows, old cache rows, old logs, and completed/failed old jobs, with dry-run output first.
- Do not run Gradle/Java unless explicitly requested. Use Node/server checks, Supabase MCP checks, and lightweight Kotlin diff review first.
- Every hardening migration must include a rollback section in the plan notes.

---

## Phase 1: Baseline Snapshot And Rollback Assets

- [x] Run `private.lotterynet_server_readiness_report()` and save the JSON to `docs/superpowers/audits/2026-06-06-supabase-baseline.json`.
- [ ] Capture Edge logs for:
  - `results-server-refresh`
  - `create-ticket-v2`
  - `get-ticket-delta`
  - `get-ticket-list`
  - `lotterynet-users-state`
  - `auth-legacy-login`
- [ ] Capture Realtime logs and mark every unauthorized channel topic.
- [x] Capture current grants and policies for:
  - `tickets`
  - `ticket_items`
  - `lotterynet_kv`
  - `lotterynet_users_state`
  - `result_draws`
  - `lotterynet_push_tokens`
- [x] Add rollback SQL notes for every revoke/drop-policy action implemented so far.
- [x] Update `docs/supabase-production-hardening.md` with the baseline status.

## Phase 2: Fix Results Refresh Performance First

Why first: the server already shows repeated `43s-44s` refresh calls. Closing policies while this is still slow can make hydration feel worse and hide the real bottleneck.

- [x] Inspect `supabase/functions/results-server-refresh/index.ts`.
- [x] Add a short-circuit rule:
  - If last refresh is fresh and source signature is unchanged, return quickly.
  - Target unchanged refresh time: under `2s`.
- [x] Add a DB lock or advisory lock so cron cannot overlap refresh work.
- [x] Add a timeout around upstream/result-provider calls so one slow provider cannot hold the function for `43s`.
- [x] Split heavy prize/reconcile work away from normal visible-result hydration if needed.
- [x] Keep cron frequent enough for live results; do not just slow the cron unless the function has a fast no-change path.
- [x] Add a small health row or KV state with:
  - last success time
  - last duration
  - last source signature
  - last error
- [ ] Verify for at least 30 minutes:
  - No repeated `43s-44s` calls.
  - No overlapping refresh jobs.
  - Clean install still sees today results quickly.
  - Manual result override still propagates.

## Phase 3: Close `lotterynet_users_state`

Current app status: Edge-first is implemented. Direct public table access was closed on 2026-06-06 after server-side Edge checks passed.

- [ ] Run device smoke checks:
  - clean app session fetches users state
  - cashier login
  - supervisor/admin login
  - user mode/config persists after app reinstall
  - blocked user remains blocked after app reinstall
- [x] Confirm Edge `lotterynet-users-state` fetch returns `200`.
- [x] Create migration `tighten_lotterynet_users_state_access`.
- [x] Revoke direct anon/authenticated access to `public.lotterynet_users_state`.
- [x] Drop the compatibility public read policy.
- [x] Keep `service_role` access.
- [x] Rollback note:
  - recreate `LotteryNet compatibility read users`
  - restore prior anon/authenticated grants needed for compatibility
- [x] Re-run server-side access checks:
  - direct REST read returns `401`
  - Edge fetch returns `200`
  - anonymous Edge upsert returns `401`
- [ ] Re-run clean-install and login device smoke checks.

## Phase 4: Split And Harden `lotterynet_kv`

Why: `lotterynet_kv` is doing too much. It contains cache, sys/admin state, deleted ticket markers, alerts, presence, and compatibility values. Closing it blindly can break multiple flows.

- [x] Inventory live keys by prefix:
  - `lot_results_cache_by_day`: 21 rows
  - `pick_results_cache_by_day`: 20 rows
  - `sys_results_refresh`: 2 rows
  - `sys_users`: 2 rows
  - `sys_audit`: 1 row
  - `sys_alerts`: 1 row
  - `sys_presence`: 1 row
  - `secret`: 1 row
  - `other`: 69 rows requiring owner classification
- [x] Block known sensitive keys from public read while keeping legacy reads:
  - `lotterynet_results_cron_secret`
  - `sys_rldly_client_secret`
  - `sys_results_refresh_lock%`
- [x] Revoke direct public `DELETE` on `lotterynet_kv` after confirming the
  current Android app does not delete KV rows directly through PostgREST.
- [x] Revoke direct anon/authenticated `INSERT`/`UPDATE` on `lotterynet_kv`
  after confirming the current Android app writes user/config/ticket/result
  state through Edge Functions.
- [x] Revoke direct anon/authenticated `SELECT` on `lotterynet_kv` after
  confirming the current Android app reads through Edge Functions and
  `get-master-config` probe still returns `200`.
- [ ] For each prefix, mark owner:
  - public read
  - authenticated cashier read
  - admin-only write
  - server-only cache
- [ ] Move server-only caches behind result Edge functions.
- [ ] Move admin/system writes behind admin Edge functions.
- [x] Replace deprecated public write policies by removing public write policies entirely.
- [x] Revoke anon/authenticated mutation grants after wrappers were verified in code review.
- [x] Remove public KV read entirely instead of keeping a mixed compatibility
  read policy.
- [ ] Verify:
  - clean install hydration
  - alerts
  - deleted ticket hiding
  - result cache hydration
  - admin config changes

## Phase 5: Close Ticket Direct Public Access

Why: `tickets` currently has a public `SELECT true` policy. That is convenient for compatibility but not acceptable as final production posture.

- [x] Confirm all ticket operations are available through Edge/RPC in code:
  - create sale
  - list admin tickets by day
  - cashier ticket history
  - ticket detail
  - void ticket
  - payment/paid flow
  - official ticket lookup
  - delta/snapshot sync
- [x] Audit `get-ticket-list` and any legacy anonymous `updated-at` path.
- [ ] If the app needs a lightweight sync marker, create a safe endpoint that exposes only non-sensitive update metadata.
- [x] Create migration `close_tickets_public_read`.
- [x] Drop public `SELECT true` policy after current app reads were verified as Edge/RPC.
- [x] Revoke anon/authenticated direct `SELECT`.
- [ ] Verify:
  - cashier creates sale
  - ticket appears in admin section under `2s`
  - official ticket image/receipt still renders
  - void works
  - pay/mark paid works
  - deleted/voided ticket rules still hide correctly
  - duplicate prevention still works

## Phase 6: Close Result Draw Mutation Exposure

Why: `result_draws` may be acceptable as public read, but anon/authenticated mutation grants should not remain.

- [ ] Inspect Android result store usage, especially `SupabaseResultsRemoteStore`.
- [ ] Ensure result hydration uses Edge/RPC path, not direct mutation.
- [x] Decide whether public result read is intentional:
  - Result reads are not public REST; they go behind `get-results-v2` /
    `get-results-status`.
- [x] Revoke anon/authenticated `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, and `REFERENCES` grants.
- [x] Revoke anon/authenticated direct mutation grants from `result_draws`.
- [x] Remove result Postgres Changes fallback from Android result live flow and
  use private `ln:results:<day>` Realtime Broadcast.
- [x] Revoke anon/authenticated direct `SELECT` from `result_draws` and drop
  `result_draws_read_all`.
- [x] Update readiness report gate to turn green once public grants and
  permissive public policies are gone.
- [ ] Verify:
  - lottery result display
  - manual result update
  - winner/prize calculation
  - result cache by day
  - realtime result notification

## Phase 7: Fix Realtime Owner Topic Authorization

Why: logs show unauthorized reads for `ln:tickets:owner:adm-163c38`. This can cause “server knows but UI does not visually update” problems.

- [ ] Inspect Realtime topic generation in Android:
  - `LotterynetRealtimeSubscription`
  - `LotterynetRealtimeOrchestrator`
- [ ] Inspect Supabase topic authorization function/policy.
- [ ] Normalize owner ids and aliases:
  - cashier id
  - admin id
  - supervisor id
  - branch/banca id
- [ ] Ensure Android subscribes to canonical allowed topics only.
- [ ] Add a server-side compatibility alias if existing devices can still publish old topic names.
- [ ] Verify:
  - cashier sale appears live in admin
  - admin limit/block update appears live in cashier
  - blocked number visual state updates without app restart
  - no unauthorized Realtime logs for 30 minutes

## Phase 8: Login/Auth Performance Cleanup

- [ ] Inspect `auth-legacy-login`.
- [ ] Identify whether it updates Supabase Auth user records on every login.
- [ ] Cache auth mapping and password version.
- [ ] Only update Auth when password/version actually changed.
- [ ] Verify:
  - login p95 under `2s`
  - blocked user rejected
  - password change propagates
  - no repeated unnecessary service-role updates

## Phase 9: Legacy, Temp, And QA Function Cleanup

- [ ] List all Edge functions and classify:
  - keep
  - keep but require JWT
  - deprecated
  - delete
  - replace with `410 Gone`
- [ ] Check candidates carefully before deleting:
  - old `create-ticket`
  - test/compat ticket functions
  - temporary OTA upload/publish functions
  - unused notification functions
  - old recarga functions
- [ ] Search app code before removing any function.
- [ ] Watch Edge logs for 24 hours before permanent deletion.
- [ ] Remove or disable only functions with no app calls and no production dependency.

## Phase 10: Maintenance, Logs, And Advisors

- [x] Fix the cron run-details query by joining `cron.job_run_details` with `cron.job`.
- [x] Add `private.lotterynet_cron_health_report()` if useful.
- [ ] Make maintenance retention visible/configurable:
  - deleted tickets without payments: 30 days
  - result cache by day: 21 days
  - OTA logs: 30 days
  - health logs: 14 days
  - completed/failed reconcile jobs: 14 days
- [x] Run Supabase advisors if available through MCP/dashboard:
  - security advisor
  - performance advisor
  - RLS advisor
  - index/bloat advisor
- [ ] Review high scan tables:
  - `lotterynet_users_state`
  - `tickets`
  - `ticket_items`
  - `result_reconcile_jobs`
  - `result_draws`
  - `ota_update_logs`
- [ ] Add indexes only where query evidence supports them.
- [ ] Re-run readiness report after every phase.

## Phase 11: Production Release Gates

Do not declare production closed until all gates pass.

- [ ] Server smoke tests with Node/server tooling only:
  - login
  - fetch users state
  - create sale
  - fetch ticket delta
  - admin tickets by day
  - void ticket
  - pay ticket
  - block number
  - limit update
  - result refresh
  - maintenance dry-run
- [ ] Optional Android device test only if user asks:
  - clean install
  - cashier login
  - sale
  - admin view
  - realtime limits/blocks
  - ticket official render
- [ ] Log gates:
  - no repeated slow `results-server-refresh`
  - no unexpected `401/403` for valid users
  - no repeated Realtime unauthorized topic logs
  - no Edge function crash loops
  - no Sentry server issue for tested flows
- [ ] Data gates:
  - no active ticket deleted
  - no paid ticket deleted
  - no balance mutation from maintenance
  - no finance mutation from maintenance
  - no user state lost after clean session
- [ ] Security gate:
  - `releaseGate.canRevokeDirectTableAccess = true`
  - direct public table access closed for users, tickets, KV, and result draws
  - result public read decision documented

---

## Execution Order

1. Baseline and rollback assets.
2. Fix `results-server-refresh` slow loop.
3. Close `lotterynet_users_state`.
4. Harden `lotterynet_kv`.
5. Close ticket public read.
6. Tighten `result_draws`.
7. Fix realtime owner topics.
8. Optimize login/auth.
9. Remove legacy/temp/QA functions.
10. Run advisors and final readiness gates.

This is still “cerrar todo de una vez” as one controlled production closeout, but not one blind SQL cut. Each closure has evidence, smoke tests, and rollback.
