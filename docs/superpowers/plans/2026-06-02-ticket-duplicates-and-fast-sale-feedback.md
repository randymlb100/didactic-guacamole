# Ticket Duplicates And Fast Sale Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent duplicate lottery tickets, keep tickets/results/prizes fresh after foreground/background changes, and make the sale screen feel instant without letting unvalidated tickets enter accounting.

**Architecture:** Keep Supabase/server as the source of truth for money, limits, ticket creation, deletion, and prizes. Android should use cache-first for reads and controlled optimistic UI for sale feedback: disable repeat actions immediately, show a pending state, then commit local cache/printing only after server confirmation. Every sale attempt must use a stable idempotency key that survives slow network, double tap, retry, and old APK behavior.

**Tech Stack:** Kotlin/Jetpack Compose, local Android ticket cache, Supabase Edge Functions, Postgres RPC, Node.js contract tests.

---

## Required Operating Playbook

Before executing this plan, read and follow:

- `docs/supabase/multibanca-engineering-playbook.md`
- `docs/supabase/production-server-first-multibanca.md`
- `docs/supabase/production-call-volume-hardening.md`

Every subagent must explain how its changes preserve:

- server-first money operations;
- cache-first reads with server catch-up;
- canonical admin/cashier identity;
- bounded queue/job processing;
- realtime recovery after foreground/background;
- stale-cache protection;
- real QA evidence.

### Task 1: Duplicate Ticket Cleanup And Admin Delete

**Files:**
- Modify: `supabase/migrations/<new>_admin_delete_duplicate_ticket_hardening.sql`
- Test: `tools/qa/void-ticket-code-lookup-contract.node.test.mjs`

- [ ] **Step 1: Add a Node contract proving current APK ticket identifiers work**

Add assertions that `ln_void_ticket_legacy(jsonb)` accepts:

```js
assert.match(migration, /ticket_code = nullif\(p_body ->> 'ticketId'/);
assert.match(migration, /ticket_code = nullif\(p_body ->> 'localTicketId'/);
assert.match(migration, /ticket_code = nullif\(p_body ->> 'clientRequestId'/);
assert.match(migration, /estado = v_next_status/);
assert.match(migration, /lotterynet_sync_ticket_owner_payload\(v_ticket.id\)/);
```

- [ ] **Step 2: Run the contract**

Run:

```powershell
node --test tools\qa\void-ticket-code-lookup-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Keep server delete admin-first**

The delete RPC must keep this rule:

```sql
elsif v_role in ('admin','admins') then
  if not lower(coalesce(v_ticket.admin_key, '')) = any(v_actor_admin_keys) then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'No puede tocar ticket de otro admin');
  end if;
```

This lets an admin delete any ticket under their own business, even when the cashier window has expired.

- [ ] **Step 4: Clean duplicate production tickets with official delete path**

Use `ln_void_ticket_legacy` with `actorKey` set to the admin username and `action=delete`; do not direct-delete rows.

```sql
select public.ln_void_ticket_legacy('{
  "actorKey": "nicola01",
  "action": "delete",
  "ticketId": "LN-E4C5DC-D9608D",
  "reason": "duplicate_keep_first_250"
}'::jsonb);
```

Expected: `ok=true`, `state=BORRADO`.

- [ ] **Step 5: Verify one active ticket remains**

```sql
select ticket_code, status, estado, deleted_at
from public.tickets
where ticket_code in ('LN-496227-244256','LN-E4C5DC-D9608D','LN-C2E43D-DC2638')
order by server_created_at;
```

Expected: only `LN-496227-244256` remains `VALIDO`; the other two are `BORRADO`.

### Task 2: Stable Sale Identity And Double-Tap Guard

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sales/SupabaseTicketBackendClient.kt`
- Modify: sale ViewModel/screen file that triggers print/sell action
- Test: `app/src/test/java/com/lotterynet/pro/core/sales/SupabaseTicketBackendClientTest.kt`

- [ ] **Step 1: Add a test for stable client request id**

The same visible sale attempt must reuse one `clientRequestId` while the request is in flight.

```kotlin
@Test
fun `same in flight sale keeps one client request id`() {
    val first = buildCreateTicketPayload(inFlightSale)
    val second = buildCreateTicketPayload(inFlightSale)
    assertEquals(first.clientRequestId, second.clientRequestId)
}
```

- [ ] **Step 2: Disable the sale/print button while validating**

The UI state must enter `Validating` immediately after the first tap.

```kotlin
if (saleState is SaleState.Validating) {
    Button(enabled = false, onClick = {}) {
        Text("Validando...")
    }
}
```

- [ ] **Step 3: Never create a second request for the same pending sale**

Guard the action handler:

```kotlin
if (saleState is SaleState.Validating) return
saleState = SaleState.Validating(clientRequestId = currentDraft.stableRequestId)
```

- [ ] **Step 4: Commit local ticket only after server confirms**

Keep this order:

```kotlin
val serverTicket = ticketBackend.createTicket(request)
localSalesRepository.saveConfirmedTicket(serverTicket)
printer.print(serverTicket)
```

Do not save a real accounting ticket before the server returns `ok=true`.

### Task 3: Cache-First Reads, Server-First Money

**Files:**
- Modify: ticket list repository/sync files
- Modify: sales screen feedback state
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/LocalSyncFreshnessContractsTest.kt`

- [ ] **Step 1: Keep ticket list cache-first**

Ticket list, reports, and snapshots should render from local cache first.

```kotlin
val local = localTicketStore.observeTickets(ownerKey)
emit(local)
refreshFromServerInBackground(ownerKey)
```

- [ ] **Step 2: Keep sale validation server-first**

Sale validation must still go to the server before printing because limits, closed lotteries, blocked plays, and duplicate prevention are money-critical.

```kotlin
val validated = remoteTicketStore.createTicket(request)
if (!validated.ok) showBlockingError(validated.message)
```

- [ ] **Step 3: Add fast feedback instead of fake success**

The screen should clear touch pressure immediately, but show an honest state:

```kotlin
SaleFeedback.Validating("Validando jugada...")
```

When server confirms:

```kotlin
SaleFeedback.Success("Ticket guardado en servidor")
```

When server fails:

```kotlin
SaleFeedback.Error("No se vendio. ${error.message}")
```

### Task 4: Node Stress Tests For Duplicate Prevention

**Files:**
- Create: `tools/qa/sale-idempotency-stress.mjs`

- [ ] **Step 1: Simulate repeated taps**

Send 3 identical create-ticket requests with the same `clientRequestId`.

```js
const responses = await Promise.all([
  createTicket(payload),
  createTicket(payload),
  createTicket(payload),
]);
```

- [ ] **Step 2: Assert one server ticket**

```js
const rows = await findTicketsByClientRequestId(payload.clientRequestId);
assert.equal(rows.length, 1);
```

- [ ] **Step 3: Assert app-compatible snapshots show one**

```js
assert.equal(countSnapshotMatches(ownerPayload, payload.clientRequestId), 1);
```

- [ ] **Step 4: Assert admin can delete old duplicate by visible code**

```js
const deleted = await voidTicket({ actorKey: "nicola01", action: "delete", ticketId: visibleTicketCode });
assert.equal(deleted.ok, true);
```

---

## Recommendation

Use cache-first for reading tickets/results/reports. Do not use pure cache-first for selling tickets. For sale, use controlled optimistic UI: instant visual feedback, one stable request id, disabled button, server validation, then local save/print after confirmation.

## Cache Strategy Decision Matrix

Official Android architecture allows different repositories to have different sources of truth. For this app, the source of truth must depend on risk:

| Area | Strategy | Why |
| --- | --- | --- |
| Ticket list | Cache-first + server refresh | Fast screen open. Server snapshot can correct stale local rows. Deleted IDs must hide removed tickets before totals are calculated. |
| Ticket official view | Cache-first for display, server verify on action | Opening a ticket should be instant. Printing, WhatsApp, delete, pay, and prize status must verify against server before changing money state. |
| Results list | Cache-first + realtime/cron refresh | Results are read-only display data after server stores them. Stale results must carry an `updatedAt/source` badge internally so reconciliation knows freshness. |
| Winning tickets | Server-authoritative + local mirror | The app can show cached winners, but only server `payout_amount/winningDetails/status` is authoritative. Local prize calculation is fallback display only, not accounting. |
| Sale create/print | Server-first with optimistic UI feedback | Money, limits, closed lottery, blocked plays, and duplicate prevention require server confirmation. UI can feel instant by locking the button and showing pending state immediately. |
| Delete/void ticket | Server-first | Deleting affects accounting and cashier totals. Old APKs must be allowed to delete by visible `LN-...` code when actor is the correct admin. |
| Pay winner | Server-first | Payment must be idempotent and match server payout. Local cache updates only after server returns success. |
| Finance/report totals | Server-first with short local cache | Finance must not be calculated from stale local-only tickets. It can display cached totals while refreshing, but must mark refresh state. |

### Why Pure Cache-First Sale Is Too Risky

Pure cache-first sale would mean the app creates the ticket locally, prints it, and uploads later. That is dangerous in this project because:

- A lottery may close while the device is offline or slow.
- A number/play may already be over the limit.
- The cashier/admin may be blocked.
- The same tap can create more than one local ticket before the server sees it.
- A stale cache can overwrite a server-corrected deleted/winner/paid ticket.

The safer design is `server-first commit` with `cache-first feeling`:

1. User taps print/sell.
2. UI immediately disables the action and shows `Validando jugada...`.
3. The sale attempt uses one stable `clientRequestId`.
4. Server validates and creates the official ticket.
5. Android saves the confirmed ticket locally and prints/shares it.
6. If the request times out, Android retries with the same `clientRequestId`, not a new ticket.

### Stale Cache Protection Rules

Add these rules to avoid old cache overwriting correct server state:

- A local active ticket must never override server `BORRADO`, `ANULADO`, `PAGADO`, `GANADOR`, or `INVALIDADO`.
- `deletedIds` from server snapshots must be applied before local totals are calculated.
- A ticket with `serverUpdatedAt` newer than local `updatedAt` wins.
- Paid/winner prize amounts from server always win over locally calculated prize amounts.
- A local pending sale can be shown in UI, but must not enter finance totals until server confirms.
- Realtime is a notification signal, not the only source of truth. Manual refresh and startup hydration must reconcile from server snapshots.

### Documentation Basis

- Android official offline-first data layer recommends a repository with a local data source and background synchronization for reads.
- Android official data layer guidance says different repositories can have different sources of truth.
- Supabase Realtime provides Postgres change notifications over WebSockets, but the app must still be able to recover by refetching snapshots.
- Supabase Edge Functions background task guidance supports responding quickly while background processing continues, but long-running work should be split into smaller safe chunks.
- Supabase large-job guidance recommends Edge Functions + Cron + database queues for reliable batch processing.

---

## Subagent Execution Plan

Use `superpowers:subagent-driven-development` to execute this section. Each subagent must start from a fresh context and must not assume production behavior from previous chat messages. Every subagent must run its assigned tests and report exact evidence.

### Required Documentation For All Subagents

Each subagent must read only the relevant current docs before changing code:

- Android offline-first data layer: `https://developer.android.com/topic/architecture/data-layer/offline-first`
- Android data layer source-of-truth guidance: `https://developer.android.com/topic/architecture/data-layer`
- Android WorkManager for reliable background sync: `https://developer.android.com/topic/libraries/architecture/workmanager`
- Supabase Realtime Postgres changes/protocol: `https://supabase.com/docs/guides/realtime`
- Supabase Edge Functions background tasks: `https://supabase.com/docs/guides/functions/background-tasks`
- Supabase large jobs with Edge Functions, Cron, and Queues: `https://supabase.com/blog/processing-large-jobs-with-edge-functions`
- Supabase Postgres indexes: `https://supabase.com/docs/guides/database/postgres/indexes`

### Subagent A: Android Sale Fast Feedback And Stable Idempotency

**Goal:** Make sale feel immediate without letting local cache create official money records before server validation.

**Files to inspect first:**
- `app/src/main/java/com/lotterynet/pro/core/sales/SupabaseTicketBackendClient.kt`
- `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Sale screen/ViewModel files found by searching `createTicket`, `print`, `Validando`, `clientRequestId`
- `app/src/test/java/com/lotterynet/pro/core/sales/SupabaseTicketBackendClientTest.kt`

**Implementation requirements:**

- [ ] Add a stable sale attempt id that is generated once per visible sale draft.
- [ ] Disable print/sell button immediately after the first tap.
- [ ] Show fast feedback: `Validando jugada...`.
- [ ] If timeout occurs, retry with the same `clientRequestId`.
- [ ] Do not save, print, or count a ticket as official until server returns success.
- [ ] If server returns duplicate/idempotent success, use the existing official ticket returned by server.
- [ ] If server fails, return UI to editable state and show clear error.

**Tests required:**

```powershell
.\gradlew testDebugUnitTest --tests "*SupabaseTicketBackendClientTest*"
```

Add or update tests proving:

- same in-flight sale reuses one `clientRequestId`;
- repeated tap does not call backend twice;
- timeout retry sends same `clientRequestId`;
- local repository does not persist official ticket before server success.

### Subagent B: Supabase Ticket Delete, Duplicate Guard, And Snapshot Protection

**Goal:** Ensure current APK/admin delete works by `LN-...` visible code, duplicates cannot remain visible, and stale snapshots cannot revive deleted tickets.

**Files to inspect first:**
- `supabase/functions/create-ticket-v2/index.ts`
- `supabase/functions/void-ticket/index.ts`
- `supabase/functions/get-ticket-list/index.ts`
- `supabase/migrations/20260519003000_enforce_ticket_delete_roles_and_two_minute_window.sql`
- `supabase/migrations/20260601183000_void_ticket_code_lookup_and_snapshot_sync.sql`
- `supabase/migrations/20260601185000_protect_snapshot_deleted_ids_respects_restored_tickets.sql`
- `supabase/migrations/20260602105000_limit_void_snapshot_cleanup_to_ticket_owners.sql`
- `tools/qa/void-ticket-code-lookup-contract.node.test.mjs`

**Implementation requirements:**

- [ ] Confirm `ln_void_ticket_legacy` can resolve tickets by `id`, `legacy_ticket_id`, `client_request_id`, and `ticket_code`.
- [ ] Confirm admin can delete any ticket under their own `admin_key`, even when cashier delete time expired.
- [ ] Confirm cashier cannot delete another cashier ticket.
- [ ] Confirm delete writes both `status` and `estado` as `BORRADO`.
- [ ] Confirm delete syncs owner snapshots only for correct owner aliases.
- [ ] Confirm snapshots keep deleted identifiers and do not let stale local uploads revive deleted tickets.
- [ ] Add a production-safe duplicate detector query that groups by admin/cashier/day/amount/items signature.

**Tests required:**

```powershell
node --test tools\qa\void-ticket-code-lookup-contract.node.test.mjs
```

Add a new Node test:

```powershell
node --test tools\qa\ticket-duplicate-delete-contract.node.test.mjs
```

This test must verify the SQL/migration contracts without exposing secrets.

### Subagent C: Real QA Flow With Stored Test Credentials

**Goal:** Run real end-to-end tests using stored QA credentials only, then clean up only the tickets created by the test.

**Credentials rule:**

- Use only QA/test credentials from `C:\Users\Randy Cordero\Documents\LotteryNet-Secrets`.
- Never print secrets in logs.
- Never use live customer/admin credentials except the explicit QA actors such as `podero02` and its test cashiers.
- Every test-created ticket must include a unique marker in `clientRequestId`, for example `qa-idem-<timestamp>-<case>`.

**Files to inspect first:**
- `tools/qa/real-flow-smoke.mjs`
- `tools/qa/real-transaction-smoke.mjs`
- `tools/qa/server-sales-diagnostic.mjs`
- `tools/qa/cleanup-qa-tickets.mjs`
- `tools/qa/ticket-payload-integrity-smoke.mjs`

**Real tests required:**

1. Login QA admin and QA cashier.
2. Create one small normal lottery ticket.
3. Create one multi-lottery ticket.
4. Try repeated tap/idempotency by sending same payload 3 times.
5. Verify only one ticket exists by `clientRequestId`.
6. Verify ticket appears in:
   - admin owner snapshot;
   - cashier owner snapshot;
   - ticket list endpoint;
   - finance/report read path.
7. Delete the QA tickets through `void-ticket`.
8. Verify deleted tickets disappear from visible lists and totals.
9. Verify deleted IDs are present in snapshots.

**Command shape:**

```powershell
node tools\qa\real-flow-smoke.mjs --profile qa --idempotency --cleanup
```

If current scripts do not support those flags, add a dedicated script:

```powershell
node tools\qa\real-sale-idempotency-and-delete-smoke.mjs
```

**Do not delete non-QA customer tickets.**

### Subagent D: Realtime Catch-Up, Read Optimization, And Job Stability

**Goal:** Reduce Supabase read volume, make prize/result jobs process in small reliable batches without timeouts, and make Android recover fresh state after app background/foreground transitions.

**Files to inspect first:**
- `supabase/functions/results-server-refresh/index.ts`
- `supabase/functions/get-ticket-list/index.ts`
- `supabase/migrations/20260602015748_optimize_prize_reconcile_owner_sync.sql`
- `docs/supabase/production-call-volume-hardening.md`
- `tools/qa/results-migration-contract.node.test.mjs`
- Android realtime/sync files found by searching `Realtime`, `subscribe`, `updatedAt`, `refreshFromServer`, `ProcessLifecycleOwner`, `LifecycleEventObserver`

**Implementation requirements:**

- [ ] Confirm ticket list reads use updated-at/stamp checks before fetching full payloads.
- [ ] Confirm empty/stable ticket stamps are cached briefly to prevent repeated polling.
- [ ] Confirm `get-ticket-list` returns scoped owner payload only, not global scans.
- [ ] Confirm result/prize jobs process bounded batches: no unbounded loops.
- [ ] Confirm job SQL uses indexed predicates, not `coalesce(column::text, ...)` scans in hot loops.
- [ ] Add foreground catch-up: when app returns to foreground, reconnect realtime channels and fetch server stamps for tickets/results/prizes.
- [ ] Add startup catch-up: when user logs in or opens app fresh, load current-day results, current owner ticket snapshot, and pending winner state before showing stale empty screens.
- [ ] Add background-safe sync: use WorkManager for periodic/lightweight catch-up, but never depend on it for instant money validation.
- [ ] Treat Realtime as a signal only. Missing realtime events must be repaired by server stamp comparison.
- [ ] Add metrics logs for:
  - number of jobs processed;
  - number of tickets reconciled;
  - elapsed milliseconds;
  - pending job count after batch.

**Tests required:**

```powershell
node --test tools\qa\results-migration-contract.node.test.mjs
```

Add assertions for:

- job limit remains bounded;
- ticket limit remains bounded;
- owner snapshot sync does not scan all owners;
- hot lookup indexes exist.

### Subagent F: Android Foreground/Background Freshness QA

**Goal:** Prove the app does not stay stuck with old results/tickets after it was left open in background.

**Why:** Supabase Realtime uses WebSockets. Android can pause/kill network work while the app is backgrounded, and Realtime delivery is not the same as durable state. The app must reconnect and run a catch-up read when it returns to foreground.

**Files to inspect first:**
- `app/src/main/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicy.kt`
- `app/src/test/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicyTest.kt`
- `app/src/main/AndroidManifest.xml`
- Application class, if present
- Main activity and navigation host files
- Realtime/sync files found by searching `Realtime`, `subscribe`, `ticket stamp`, `result`, `ProcessLifecycleOwner`, `ON_START`, `ON_RESUME`
- `app/src/test/java/com/lotterynet/pro/core/sync/LocalSyncFreshnessContractsTest.kt`

**Implementation requirements:**

- [ ] Add an app-level lifecycle observer using `ProcessLifecycleOwner`.
- [ ] Route foreground decisions through `ForegroundCatchUpPolicy`; do not duplicate catch-up rules in each screen.
- [ ] On app foreground (`ON_START`), run a lightweight catch-up:
  - current-day results stamp;
  - ticket owner snapshot stamp;
  - winner/payment stamp;
  - reconnect realtime channels if disconnected.
- [ ] On login/startup, fetch server state before showing “no tickets/no results” if local cache is empty.
- [ ] If local cache exists, show it immediately but mark refresh state until server stamp check completes.
- [ ] If server stamp changed, fetch fresh snapshot and merge using stale-cache protection rules.
- [ ] Do not run unlimited polling. Use debounce/throttle, for example at most one foreground catch-up per owner every 15-30 seconds.
- [ ] Add a Node/Kotlin contract proving `ForegroundCatchUpPolicy` remains present and covers tickets, results, realtime reconnect, throttle, and force recovery.

**Tests beyond Node.js:**

```powershell
.\gradlew testDebugUnitTest --tests "*LocalSyncFreshnessContractsTest*"
.\gradlew testDebugUnitTest --tests "*RealtimeFlowContractsTest*"
```

Add or update tests proving:

- foreground event triggers catch-up once;
- repeated foreground events are throttled;
- stale local result cache updates after server stamp changes;
- stale active ticket cannot override server deleted/winner/paid state;
- empty local cache forces server load on first app entry.

**Manual/device QA checklist:**

1. Open APK, login QA user.
2. Confirm today's results appear without pressing refresh.
3. Leave app in background for 10-15 minutes.
4. While app is backgrounded, insert/update one result or ticket snapshot from server.
5. Bring app foreground.
6. Confirm app refreshes automatically within a few seconds.
7. Confirm no repeated loading loop and no repeated Supabase spam.
8. Confirm ticket list and winner badge update without manual refresh.

### Subagent G: Full Production Readiness Test Suite

**Goal:** Add a complete test ladder so production readiness is not decided by Node alone.

**Test layers:**

- **SQL contract tests:** verify functions, indexes, owner aliases, job bounds.
- **Node real API tests:** verify Edge Functions and real Supabase behavior with QA users.
- **Kotlin unit tests:** verify repository merge, cache freshness, sale state, idempotency.
- **Android instrumentation tests:** verify UI state, foreground/background refresh, button disable, ticket list updates.
- **Manual POS checklist:** verify printer/WhatsApp/real device network transitions.
- **Supabase advisor/metrics check:** verify no new full table scan hotspot, no runaway call volume.

**Commands:**

```powershell
node --test tools\qa\void-ticket-code-lookup-contract.node.test.mjs
node --test tools\qa\results-migration-contract.node.test.mjs
node tools\qa\real-sale-idempotency-and-delete-smoke.mjs
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest
```

**Performance evidence required before release:**

- Sale validation median time.
- Ticket list first paint from local cache.
- Ticket list server catch-up time.
- Results startup catch-up time.
- Supabase calls per app open.
- Supabase calls after app foreground.
- Prize job batch duration and pending count.

### Subagent H: Server Backpressure, Durable Queue, And No-Stall Ticket Processing

**Goal:** Prevent the server from getting overwhelmed when many tickets/results/jobs accumulate, especially after hours of inactivity or app backgrounding.

**Architecture decision:** Edge Functions should handle the request boundary and quick validation. Heavy reconciliation should be split into durable queue messages and processed by bounded workers. Cron should wake workers, not process unlimited work directly in one request.

**Documentation to read first:**

- Supabase Queues / PGMQ: `https://supabase.com/docs/guides/queues`
- Supabase PGMQ extension API: `https://supabase.com/docs/guides/queues/pgmq`
- Supabase Edge Functions background tasks: `https://supabase.com/docs/guides/functions/background-tasks`
- Supabase Cron / pg_cron: `https://supabase.com/docs/guides/cron`
- Supabase pg_cron debugging/concurrency notes: `https://supabase.com/docs/guides/troubleshooting/pgcron-debugging-guide-n1KTaz`

**Files to inspect first:**

- `supabase/functions/create-ticket-v2/index.ts`
- `supabase/functions/results-server-refresh/index.ts`
- `supabase/functions/get-ticket-list/index.ts`
- `supabase/migrations/20260602015748_optimize_prize_reconcile_owner_sync.sql`
- `supabase/migrations/20260529003000_result_draws_prize_v2_foundation.sql`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- `docs/supabase/production-call-volume-hardening.md`

**Implementation requirements:**

- [ ] Create a small queue/job table or Supabase Queue for prize reconciliation tasks if current `result_reconcile_jobs` is not durable enough.
- [ ] Use one logical message per result/lottery/day, not one huge “process all today” task.
- [ ] Add dedupe key: `result_day_key + lottery_id + result_number + job_type`.
- [ ] Add status fields: `pending`, `processing`, `completed`, `failed`, `retry_after`, `attempts`, `last_error`.
- [ ] Read jobs with `FOR UPDATE SKIP LOCKED` or PGMQ visibility timeout so two workers do not process the same job.
- [ ] Process bounded chunks only:
  - max jobs per run;
  - max tickets per job;
  - max elapsed milliseconds;
  - stop cleanly before Edge/Cron timeout.
- [ ] On failure, increment attempts and retry later with backoff.
- [ ] Archive completed queue messages or mark jobs completed with timestamp.
- [ ] Keep a repair RPC for a specific owner/day/ticket for emergency support.
- [ ] Add metrics table or structured logs:
  - queue depth;
  - oldest pending age;
  - jobs processed;
  - tickets scanned;
  - tickets updated;
  - elapsed milliseconds;
  - failed jobs.

**Backpressure rules:**

- If queue depth is high, process smaller batches more often, not one huge batch.
- If Edge Function is near timeout, stop and leave remaining jobs pending.
- If Supabase calls spike, app should fetch stamps first, full payload second.
- If realtime reconnects after background, app should request only changed snapshots.
- If a result has already been processed for a ticket, skip it idempotently.

**Tests required:**

```powershell
node --test tools\qa\results-migration-contract.node.test.mjs
node --test tools\qa\winner-owner-snapshot-alias-sync.node.test.mjs
```

Add a new test:

```powershell
node --test tools\qa\queue-backpressure-contract.node.test.mjs
```

This test must assert:

- bounded job limit exists;
- bounded ticket limit exists;
- retry/backoff fields exist;
- completed jobs cannot be reprocessed;
- duplicate result jobs collapse to one job;
- worker function does not contain unbounded loops over all tickets.

**Real QA evidence:**

Run a controlled test with QA credentials:

1. Create multiple tickets under one QA admin/cashier.
2. Insert or simulate one result that makes some tickets winners.
3. Trigger the refresh worker.
4. Verify queue depth decreases.
5. Verify winner snapshots update for admin and cashier.
6. Verify app catch-up sees winners after foreground.
7. Verify no repeated Supabase flood from ticket list/results.

### AI/Subagent Usage Guide For This Large Plan

Use these roles to avoid one agent mixing concerns:

- **Architecture subagent:** validates docs and decides cache/server/queue boundaries.
- **Android lifecycle subagent:** handles Compose, lifecycle, WorkManager, foreground catch-up.
- **Android sale subagent:** handles sale UI, idempotency, button lock, retry state.
- **Supabase queue subagent:** handles jobs, PGMQ/result queue, indexes, retry/backoff.
- **Supabase ticket subagent:** handles delete, duplicates, snapshots, owner aliases.
- **QA automation subagent:** writes Node/Kotlin/instrumentation tests and real QA scripts.
- **Performance reviewer subagent:** checks call volume, full scans, job duration, logs.
- **Final safety reviewer subagent:** reviews money invariants: no duplicate sale, no stale paid/winner overwrite, no deleted ticket revival.

Each subagent must return:

- exact files changed;
- documentation used;
- tests run;
- before/after evidence;
- risks still open;
- whether build release is required.

### Subagent I: Canonical Identity, Owner Routing, And Multi-Banca Reconciliation

**Goal:** Make every ticket, prize, payment, report, and snapshot route to the correct admin/cashier/business even when visible names, usernames, legacy ids, or display labels differ.

**Problem to solve:** The app has historically mixed human names (`nicola01`, `bancay04`, display names) with internal ids (`ADM-...`, `CAJ-...`, UUIDs, legacy ticket ids). In a money system, display names must never be the primary identity. They are aliases only. The server must resolve a canonical owner graph quickly and consistently.

**Documentation to read first:**

- Supabase RLS and JWT app metadata caveats: `https://supabase.com/docs/guides/database/postgres/row-level-security`
- Supabase auth metadata warning: use `app_metadata` for authorization, never editable user metadata.
- PostgreSQL multi-tenant shared schema pattern: tenant-owned rows should carry a stable tenant/business id.
- Android data layer source-of-truth guidance: identity mapping should live in repository/domain models, not scattered UI strings.

**Canonical model:**

Use stable keys in this order:

1. `business_key` or `tenant_key`: the business/banca group boundary.
2. `admin_key`: canonical admin owner, for example `ADM-163C38`.
3. `cashier_key`: canonical cashier owner, for example `CAJ-FB545A`.
4. `supervisor_key`: optional canonical supervisor group.
5. `auth_user_id`: Supabase auth UUID, if linked.
6. Legacy aliases: username, display name, old local ids, visible banca name.

**Server routing rule:**

Every money row must store canonical keys:

- `tickets.admin_key`
- `tickets.cashier_key`
- `tickets.supervisor_key`
- `tickets.profile_id` / `admin_id` / `banca_uuid` when available
- `ticket_items.ticket_id`
- `movimientos_balance.admin_key`
- `movimientos_balance.cashier_key`
- `ticket_prize_items.ticket_id`
- `payments.ticket_id`
- `lotterynet_tickets_by_owner.owner_key`

Visible names are only presentation fields.

**Files to inspect first:**

- `supabase/functions/create-ticket-v2/index.ts`
- `supabase/functions/get-ticket-list/index.ts`
- `supabase/functions/pay-ticket/index.ts`
- `supabase/functions/void-ticket/index.ts`
- `supabase/migrations/*owner*`
- `supabase/migrations/*ticket*`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeUsersBootstrapper.kt`
- `app/src/main/java/com/lotterynet/pro/core/users/*`
- `app/src/main/java/com/lotterynet/pro/core/sales/*`
- `app/src/main/java/com/lotterynet/pro/core/finance/*`
- `app/src/test/java/com/lotterynet/pro/core/finance/LocalFinanceRepositoryContractsTest.kt`

**Implementation requirements:**

- [ ] Add/verify a canonical identity resolver RPC: input any alias, output canonical actor:
  - role;
  - admin_key;
  - cashier_key;
  - supervisor_key;
  - business_key if available;
  - active/blocked status;
  - all aliases.
- [ ] Ensure ticket creation calls the resolver once and writes canonical keys before inserting ticket/items.
- [ ] Ensure get-ticket-list scopes by canonical owner aliases but reads from canonical keys first.
- [ ] Ensure winner reconciliation updates all correct owner snapshots by alias list generated from canonical ticket row.
- [ ] Ensure finance/report queries group by canonical keys, not display names.
- [ ] Ensure pay/delete validates permissions using canonical relationships:
  - admin can act on tickets under own `admin_key`;
  - cashier only own `cashier_key`;
  - supervisor only own `supervisor_key` group;
  - master does not accidentally see/administer unrelated business money unless explicitly allowed.
- [ ] Ensure username/display name changes do not move old tickets to a different owner.
- [ ] Ensure if a cashier is renamed, historical tickets still appear under the same cashier canonical key.
- [ ] Ensure if a cashier is moved to another admin, old tickets remain under old admin unless explicit migration is performed.

**Fast reconciliation indexes:**

Add/verify indexes for:

```sql
create index if not exists tickets_admin_cashier_day_idx
on public.tickets(admin_key, cashier_key, draw_date_real, server_created_at desc)
where deleted_at is null and voided_at is null and invalidated_at is null;

create index if not exists tickets_cashier_day_idx
on public.tickets(cashier_key, draw_date_real, server_created_at desc)
where deleted_at is null and voided_at is null and invalidated_at is null;

create index if not exists tickets_admin_day_status_idx
on public.tickets(admin_key, draw_date_real, status)
where deleted_at is null and voided_at is null and invalidated_at is null;

create index if not exists ticket_items_ticket_lottery_idx
on public.ticket_items(ticket_id, lottery_legacy_id, secondary_lottery_legacy_id);

create index if not exists owner_snapshot_owner_updated_idx
on public.lotterynet_tickets_by_owner(owner_key, updated_at desc);
```

**Tests required:**

Create/extend:

```powershell
node --test tools\qa\owner-routing-contract.node.test.mjs
```

Test cases:

- admin username `nicola01` resolves to canonical `ADM-163C38`;
- cashier username `bancay04` resolves to canonical `CAJ-FB545A` and parent `ADM-163C38`;
- ticket created by cashier appears in cashier snapshot and admin snapshot;
- ticket created by admin appears in admin snapshot and not random cashier snapshot;
- changing display name does not move ticket ownership;
- winner reconciliation updates admin and cashier snapshots;
- paid ticket remains paid in both snapshots;
- deleted ticket disappears in both snapshots and remains in `deletedIds`;
- finance report for admin includes all own cashiers, not other admins;
- finance report for cashier includes only own tickets;
- master does not mix admin business money unless explicit master report endpoint is used.

**Real QA flow:**

Use QA credentials only:

1. Login admin QA.
2. Login cashier QA under that admin.
3. Create one ticket as cashier.
4. Verify DB canonical keys.
5. Verify admin snapshot contains it.
6. Verify cashier snapshot contains it.
7. Trigger result/winner reconciliation if applicable.
8. Verify both snapshots show same winner state.
9. Delete/pay as admin where allowed.
10. Verify cashier view updates by catch-up/realtime.

**Failure modes this must prevent:**

- Ticket winner visible on server but not app because snapshot only updated admin or only cashier.
- Ticket appears under wrong cajero because display name matched another user.
- Finance total wrong because query grouped by `username` instead of canonical key.
- Admin cannot delete own cashier ticket because app sends visible code/name but server expects UUID only.
- Realtime event arrives for one owner key but not the aliases currently used by app.
- Renaming a cashier breaks old tickets.
- Local cache overwrites canonical server owner fields.

**Release gate:**

No release build until owner-routing contract tests pass and one real QA ticket is proven visible in both admin and cashier paths.

### Subagent E: Final Integration Review

**Goal:** Check the whole flow as a multi-banca money system, not isolated code.

**Review checklist:**

- [ ] A stale local cache cannot revive deleted tickets.
- [ ] A stale local cache cannot lower/overwrite winner payout.
- [ ] A sale cannot be printed twice from double tap.
- [ ] A timeout retry cannot create a second ticket.
- [ ] Admin can delete duplicated tickets under their business.
- [ ] Cashier cannot delete another cashier ticket.
- [ ] Ticket list opens fast from local cache.
- [ ] Finance/report totals come from server-confirmed data.
- [ ] Realtime is useful but not required for recovery.
- [ ] Node real QA test cleans only its own tickets.

**Final commands:**

```powershell
node --test tools\qa\void-ticket-code-lookup-contract.node.test.mjs
node --test tools\qa\results-migration-contract.node.test.mjs
node tools\qa\real-sale-idempotency-and-delete-smoke.mjs
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest
```

Run a release build only after these pass.
