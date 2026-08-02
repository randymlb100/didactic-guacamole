# Realtime Auth/Scope Dedup Adjustment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce repeated Realtime joins and repeated ticket refresh calls by narrowing scope to the canonical owner, keeping legacy global signals out of the main app path, and adding dedupe gates so the app stays stable before deploy.

**Architecture:** Keep the business flow intact and only adjust the synchronization layer. The app should continue to read the same operational data, but it should subscribe and refresh from one canonical owner context instead of fanning out through aliases or multiple lifecycle paths. Supabase private-channel authorization stays in place; we only tighten when and how often the client asks for the same data.

**Tech Stack:** Android Jetpack Compose, Kotlin coroutines, Supabase Realtime, Postgres RLS, Node smoke tests, JVM contract tests.

---

### Task 1: Freeze the current call graph and isolate the real hot path

**Files:**
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentity.kt`
- Inspect: `supabase/migrations/20260612104500_users_state_broadcast_signal.sql`
- Inspect: `supabase/migrations/20260604072458_realtime_broadcast_redis_sentry_foundation.sql`

- [ ] Trace every place where the app subscribes, resubscribes, or refreshes on resume/catch-up, and mark which ones can fire more than once for the same owner/session.
- [ ] Mark every path that still expands a session into aliases, especially `resolveOperationalOwnerKeys(...)` and `resolveTicketRealtimeSyncOwnerKeys(...)`.
- [ ] Record which path is creating the legacy `ln:users:global` noise and which paths are only reacting to it.

**Validation:**
- The inventory should clearly separate canonical-owner refreshes, alias-triggered refreshes, and the legacy global signal.

### Task 2: Narrow the main app path to a single canonical owner context

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`

- [ ] Keep aliases as lookup inputs, but stop letting them become separate subscription scopes or repeated refresh owners.
- [ ] Route the realtime refresh logic through the canonical owner key so one user/session does not re-trigger the same work under multiple labels.
- [ ] Keep the current data model and visibility rules unchanged; only collapse duplicate sync work.

**Validation:**
- Opening the same operational area through multiple screens should still show the same data, but it should not create parallel refresh bursts for the same owner.

### Task 3: Add a small dedupe gate around the hottest refresh loop

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/TicketRefreshGovernor.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicy.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`

- [ ] Reuse the existing refresh governor instead of adding a second one, and key it by canonical owner + request type + auth scope.
- [ ] Keep the current in-flight request sharing behavior, but make sure alias-driven callers land on the same key when they mean the same data.
- [ ] Preserve the fallback path for realtime loss, but avoid immediately re-running the same catch-up if the inputs have not changed.

**Validation:**
- A burst of identical refresh requests should collapse to one network call plus cache reuse, not a cascade of repeated `get-ticket-list` or `fetchUpdatedAt` traffic.

### Task 4: Keep Supabase Realtime auth strict but avoid a noisy global join path

**Files:**
- Inspect: `supabase/migrations/20260612104500_users_state_broadcast_signal.sql`
- Inspect: `supabase/migrations/20260604072458_realtime_broadcast_redis_sentry_foundation.sql`
- Inspect: `docs/supabase/2026-05-13-realtime-rollout-checklist.md`
- Inspect: `docs/supabase/2026-05-13-realtime-cutover-checklist.md`
- Inspect: `docs/supabase/realtime_number_limits_and_ticket_backend.md`

- [ ] Confirm that private Realtime channels still rely on RLS on `realtime.messages` and topic matching through `realtime.topic()`, exactly as Supabase documents.
- [ ] Treat `ln:users:global` as legacy compatibility, not the main synchronization path for the Android client.
- [ ] If that signal must remain for a transition window, keep it out of the critical refresh loop so it cannot multiply joins or refreshes.

**Validation:**
- The app should still receive real changes, but repeated unauthorized or redundant global joins should stop being part of the active flow.

### Task 5: Add regression tests that prove the duplicate-call problem stays fixed

**Files:**
- Modify: `tools/qa/realtime-refresh-dedup-contract.node.test.mjs`
- Modify: `tools/qa/broadcast-redis-sentry-contract.node.test.mjs`
- Modify: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/TicketRefreshGovernorTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/ForegroundCatchUpPolicyTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentityTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt`

- [ ] Add a Node smoke test that proves repeated equivalent refresh requests collapse into one deduped path.
- [ ] Add a JVM contract test that proves canonical owner resolution does not create extra owners from alias noise.
- [ ] Add a JVM contract test that proves a realtime loss fallback does not immediately re-fire the same request if the inputs are unchanged.

**Validation:**
- Run the narrowest tests first, not a release build.
- Suggested smoke command:

```bash
node --test tools/qa/realtime-refresh-dedup-contract.node.test.mjs tools/qa/broadcast-redis-sentry-contract.node.test.mjs
```

### Task 6: Verify the fix before any manual release build

**Files:**
- Inspect: `tools/qa/realtime-refresh-dedup-contract.node.test.mjs`
- Inspect: `tools/qa/ticket-summary-bounded-refresh-contract.node.test.mjs`
- Inspect: `tools/qa/ticket-list-snapshot-cache-contract.node.test.mjs`

- [ ] Confirm the smoke tests still pass after the scope reduction and dedupe changes.
- [ ] Confirm the logs stop showing the same owner/session asking for the same realtime work over and over.
- [ ] Stop here and hand back the result for a manual debug/release build only after the tests are green.

**Validation:**
- The change is acceptable only if the visible flow stays the same and the repeated-call pattern drops measurably.

## Reference docs reviewed

- [Realtime Authorization](https://supabase.com/docs/guides/realtime/authorization)
- [Subscribing to Database Changes](https://supabase.com/docs/guides/realtime/subscribing-to-database-changes)
- [Realtime Concepts](https://supabase.com/docs/guides/realtime/concepts)
- [Realtime Broadcast](https://supabase.com/docs/guides/realtime/broadcast)
- [Postgres Changes](https://supabase.com/docs/guides/realtime/postgres-changes)
- [Android Compose side effects](https://developer.android.com/develop/ui/compose/side-effects)
- [Compose state and state hoisting](https://developer.android.com/develop/ui/compose/state)
- [ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)

