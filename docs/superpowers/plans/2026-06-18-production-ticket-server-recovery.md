# Production Ticket Server Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover production safely, remove the remaining global JSONB trigger cost, and prove the current server can support 150 simultaneous clients at 5 sales/second before deploying any new optimization.

**Architecture:** Keep `tickets` and `ticket_items` as the authoritative normalized data. Restore the previous bounded snapshot protection immediately, keep result reconciliation outside ticket reads, and migrate clients gradually from full snapshots to idempotent ticket writes plus cursor-based deltas. Production changes are reversible and load tests run only outside production.

**Tech Stack:** Supabase Postgres 17, Supabase Edge Functions, pg_cron, Kotlin Android, Node.js QA scripts, `pg_stat_statements`.

---

## Current incident evidence

- Project control plane reports `ACTIVE_HEALTHY`, but SQL connections time out.
- Cron job 3 starts every minute.
- Cron job 6 is intended to remain disabled.
- Two attempts to disable cron 3 failed with connection timeout.
- PostgreSQL recorded repeated `statement timeout` errors.
- Recent checkpoints lasted 56.863 seconds, 8.993 seconds and 7.782 seconds.
- `get-ticket-list` v36 produced requests of 6.1–6.4 seconds and errors after 11.5–15.9 seconds.
- The database migration from `20260618143000_optimize_ticket_snapshot_write_path.sql` is still active.
- Its `ln_protect_ticket_owner_snapshot()` trigger builds an identifier map from all active tickets on every snapshot write.
- There are 1,381 active tickets and owner snapshots up to 260 KB.
- Reverting only the Edge RPC did not remove the expensive database trigger.

## Non-negotiable safety rules

- Do not run load tests in production.
- Do not run `EXPLAIN ANALYZE` on a write query in production.
- Do not reactivate cron 6 during recovery.
- Do not deploy if SQL health checks cannot complete in 3 seconds.
- Do not combine trigger rollback, new indexes and Edge changes in one deployment.
- Do not increase connection limits to hide inefficient work.
- Preserve all ticket, item, payment and result data.

### Task 1: Recover database access and stop background pressure

**Files:**
- Document: `docs/superpowers/plans/2026-06-18-production-ticket-server-recovery.md`

- [ ] **Step 1: Wait for or perform one controlled Supabase restart**

Use the Supabase project controls only once. Do not repeatedly restart.

- [ ] **Step 2: Disable both ticket/result cron jobs immediately after access returns**

```sql
select cron.alter_job(job_id := 3, active := false);
select cron.alter_job(job_id := 6, active := false);
```

- [ ] **Step 3: Verify cron state**

```sql
select jobid, jobname, active
from cron.job
where jobid in (3, 6)
order by jobid;
```

Expected:

```text
3  lotterynet-results-server-refresh  false
6  lotterynet-results-prize-watchdog false
```

- [ ] **Step 4: Verify database health with one bounded query**

```sql
set statement_timeout = '3s';

select
  count(*) filter (where state = 'active' and pid <> pg_backend_pid()) as active_other,
  count(*) filter (where wait_event_type = 'Lock') as lock_waiters,
  count(*) filter (where state = 'idle in transaction') as idle_in_transaction
from pg_stat_activity;
```

Acceptance:

- query completes under 3 seconds;
- `lock_waiters = 0`;
- `idle_in_transaction = 0`.

### Task 2: Create a targeted rollback for the global snapshot scan

**Files:**
- Create: `supabase/migrations/<generated>_rollback_global_ticket_snapshot_scan.sql`
- Reference: `supabase/migrations/rollback/20260618143000_optimize_ticket_snapshot_write_path.rollback.sql`
- Test: `tools/qa/ticket-snapshot-trigger-scope-contract.node.test.mjs`

- [ ] **Step 1: Generate the migration filename through the Supabase CLI**

```powershell
npx --yes supabase@latest migration new rollback_global_ticket_snapshot_scan
```

- [ ] **Step 2: Write a failing contract test**

The test must assert that the new migration:

```js
assert.doesNotMatch(migration, /into\s+active_ticket_identifiers[\s\S]*from\s+public\.tickets/i);
assert.match(migration, /where\s+not\s+exists\s*\([\s\S]*from\s+public\.tickets/i);
assert.doesNotMatch(migration, /drop\s+index/i);
assert.doesNotMatch(migration, /drop\s+table/i);
```

- [ ] **Step 3: Run the contract and confirm failure**

```powershell
node --test tools/qa/ticket-snapshot-trigger-scope-contract.node.test.mjs
```

Expected: FAIL because the rollback migration does not exist yet.

- [ ] **Step 4: Restore only the previous bounded `ln_protect_ticket_owner_snapshot()`**

Copy the complete function definition from:

```text
supabase/migrations/rollback/20260618143000_optimize_ticket_snapshot_write_path.rollback.sql
```

The new migration must contain only:

```sql
begin;

create or replace function public.ln_protect_ticket_owner_snapshot()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
-- Exact bounded implementation from the rollback file:
-- checks only candidate deleted IDs from old/new payloads.
-- It must not build active_ticket_identifiers from every active ticket.
$function$;

commit;
```

Do not drop the useful partial index. Do not drop the unused RPC in this deployment.

- [ ] **Step 5: Run the contract**

```powershell
node --test tools/qa/ticket-snapshot-trigger-scope-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 6: Review the SQL manually**

Confirm:

- only one function is replaced;
- no data modification;
- no table or index removal;
- trigger name and function signature remain unchanged;
- rollback is another `CREATE OR REPLACE FUNCTION`.

### Task 3: Validate the trigger rollback outside production

**Files:**
- Create: `tools/qa/ticket-snapshot-trigger-benchmark.mjs`
- Test: local or Supabase development branch

- [ ] **Step 1: Seed production-shaped test data**

Use:

- 1,700 tickets;
- 1,400 active tickets;
- 92 owner snapshots;
- largest snapshot: 750 tickets and 500 deleted IDs;
- aliases including admin and cashier keys;
- reject placeholder aliases such as `"null"`.

- [ ] **Step 2: Benchmark one unchanged snapshot write**

Expected:

- zero physical update;
- p95 under 100 ms;
- WAL near zero.

- [ ] **Step 3: Benchmark one changed snapshot write**

Expected:

- p95 under 500 ms;
- WAL below 100 KB;
- no scan proportional to all rows in `tickets`.

- [ ] **Step 4: Run 30 concurrent writers against different owners**

Expected:

- zero deadlocks;
- no lock wait above 500 ms;
- zero connection timeout;
- p99 under 1.5 seconds.

- [ ] **Step 5: Run 30 concurrent writers against the same owner**

Expected behavior:

- requests fail fast or serialize for less than 500 ms;
- no unbounded advisory-lock queue;
- database remains responsive to unrelated reads.

### Task 4: Deploy the targeted trigger rollback

**Files:**
- Deploy: generated rollback migration from Task 2
- Preserve: `supabase/functions/get-ticket-list/index.ts` v36

- [ ] **Step 1: Capture pre-deploy metrics**

```sql
select now(), checkpoints_timed, checkpoints_req, checkpoint_write_time
from pg_stat_bgwriter;
```

Also capture:

- current connections;
- lock waiters;
- latest Edge latency;
- latest statement timeouts.

- [ ] **Step 2: Apply only the targeted function replacement**

Do not deploy Edge Functions in this step.

- [ ] **Step 3: Verify the active function**

```sql
select
  position(
    'active_ticket_identifiers'
    in pg_get_functiondef('public.ln_protect_ticket_owner_snapshot()'::regprocedure)
  ) = 0 as global_scan_removed;
```

Expected:

```text
global_scan_removed = true
```

- [ ] **Step 4: Observe normal production traffic**

Observe for at least 30 minutes:

- no 5xx from ticket endpoints;
- p95 `get-ticket-list` below 1 second;
- no lock waits above 1 second;
- no checkpoint above 30 seconds;
- SQL health query consistently under 3 seconds.

- [ ] **Step 5: Roll back immediately if thresholds fail**

Restore the currently saved function definition using `CREATE OR REPLACE FUNCTION`. Do not restart repeatedly.

### Task 5: Separate prize processing from ticket reads

**Files:**
- Modify: `supabase/functions/get-ticket-list/index.ts`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Test: `tools/qa/supabase-edge-auth-contract.node.test.mjs`
- Test: `tools/qa/ticket-hydrate-readonly-contract.node.test.mjs`

- [ ] **Step 1: Add failing tests**

Assertions:

```js
assert.doesNotMatch(ticketList, /processPendingPrizes\(admin,\s*body\)/);
assert.doesNotMatch(ticketList, /lotterynet_process_result_reconcile_jobs_for_day/);
assert.match(androidStore, /\.put\("processPendingPrizes", false\)/);
```

- [ ] **Step 2: Run tests and confirm failure**

```powershell
node --test tools/qa/supabase-edge-auth-contract.node.test.mjs tools/qa/ticket-hydrate-readonly-contract.node.test.mjs
```

- [ ] **Step 3: Remove prize reconciliation from `get-ticket-list`**

Delete the `processPendingPrizes` helper and its calls. Ticket reads return ticket data only.

- [ ] **Step 4: Run tests**

Expected: PASS.

### Task 6: Replace full snapshot upload with idempotent ticket batches

**Files:**
- Modify: `supabase/functions/create-ticket-v2/index.ts`
- Modify: `supabase/functions/create-ticket/index.ts`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt`
- Test: `tools/qa/ticket-hydrate-readonly-contract.node.test.mjs`

- [ ] **Step 1: Add a batch contract with maximum 20 tickets**

Required request shape:

```json
{
  "action": "upsert-ticket-batch",
  "tickets": [],
  "maxBatchSize": 20
}
```

- [ ] **Step 2: Reject batches over 20**

Return HTTP 413 without opening a write transaction.

- [ ] **Step 3: Reuse the existing idempotent ticket primitive**

Both `create-ticket` and `create-ticket-v2` must call the same internal implementation keyed by `client_request_id`.

- [ ] **Step 4: Confirm each ticket independently**

Response:

```json
{
  "ok": true,
  "accepted": ["client-id-1"],
  "duplicates": ["client-id-2"],
  "failed": []
}
```

- [ ] **Step 5: Stop uploading owner history**

The Android coordinator sends only pending tickets and missing deletion tombstones.

### Task 7: Add cursor-based delta reads

**Files:**
- Modify: `supabase/functions/get-ticket-list/index.ts`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Test: `tools/qa/ticket-delta-cursor-contract.node.test.mjs`

- [ ] **Step 1: Add cursor contract**

Cursor fields:

```json
{
  "updatedAt": "2026-06-18T20:00:00.000Z",
  "id": "uuid"
}
```

- [ ] **Step 2: Query by `(updated_at, id)`**

Maximum normal page: 150 tickets.

- [ ] **Step 3: Fetch items only for returned ticket IDs**

Use one batched `ticket_items.ticket_id IN (...)` query per page.

- [ ] **Step 4: Prove no omissions or duplicates**

Test equal timestamps, concurrent inserts and page boundaries.

### Task 8: Deduplicate Android network calls

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
- Test: Android sync contract tests

- [ ] **Step 1: Add one in-flight operation per `(owner, operation)`**

Operations:

```text
HYDRATE
FLUSH
DELTA
```

- [ ] **Step 2: Add two-second Realtime debounce**

Multiple signals for the same owner produce one delta request.

- [ ] **Step 3: Prevent hydrate/flush recursion**

Hydration never calls flush. Flush never downloads a full snapshot.

- [ ] **Step 4: Add bounded retry**

Use exponential backoff with jitter and maximum three attempts.

### Task 9: Build the 150-client load test

**Files:**
- Create: `tools/qa/ticket-sync-150-client-load.mjs`
- Create: `tools/qa/ticket-sync-load-report.md`

- [ ] **Step 1: Model target traffic**

Target:

```text
150 clients
1 sale/client/30 seconds
5 sales/second
30 minutes
```

- [ ] **Step 2: Model stress traffic**

Stress:

```text
450 clients
15 sales/second
10 minutes
```

- [ ] **Step 3: Capture mandatory metrics**

- p50, p95 and p99;
- 4xx and 5xx;
- active and waiting connections;
- lock waits and deadlocks;
- WAL bytes per sale;
- checkpoint duration;
- CPU, memory and I/O;
- duplicate or missing tickets.

- [ ] **Step 4: Enforce release thresholds**

Release fails if:

- sale p95 ≥ 750 ms;
- sale p99 ≥ 1.5 seconds;
- any 5xx under target load;
- error rate ≥ 0.1%;
- lock wait > 500 ms;
- connections ≥ 60% of limit;
- average WAL ≥ 100 KB/sale;
- checkpoint > 30 seconds;
- any lost or duplicated sale.

### Task 10: Canary deployment

**Files:**
- Modify: feature flag configuration only after Tasks 1–9 pass

- [ ] **Step 1: Enable one internal owner**

Observe 30 minutes.

- [ ] **Step 2: Increase to 5%**

Observe 30 minutes.

- [ ] **Step 3: Increase to 25%, 50% and 100%**

Observe at least 30 minutes per stage and one hour before declaring 100% stable.

- [ ] **Step 4: Keep rollback immediate**

Rollback actions:

1. disable feature flag;
2. pause new workers;
3. restore previous Edge version;
4. preserve normalized ticket data;
5. do not rebuild all snapshots.

## Definition of done

- Production remains responsive for one complete business day.
- Cron jobs cannot overlap.
- Ticket reads never process prizes.
- Ticket writes never reconstruct owner history.
- Target load of 150 clients passes all release thresholds.
- Stress load of 450 clients does not cause connection exhaustion.
- Rollback has been executed successfully in a non-production environment.
