# Compose Realtime Refresh Dedup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce duplicate Realtime and ticket-refresh traffic by moving refresh ownership into a shared app-level state layer and preventing multiple Compose screens from independently re-subscribing or re-fetching the same data.

**Architecture:** Keep Compose composables side-effect light and lifecycle-aware, hoist refresh state into screen-level state holders or ViewModels, and share one logical realtime/store path per user/session rather than one per screen. Use immutable UI state, controlled `LaunchedEffect`/`DisposableEffect` boundaries, and a shared refresh governor so repeated navigation or recomposition does not multiply calls.

**Tech Stack:** Android Jetpack Compose, ViewModel, Kotlin coroutines, Supabase Realtime, existing ticket sync/store classes, JVM unit tests.

---

### Task 1: Map all realtime and ticket refresh entry points

**Files:**
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`

- [ ] Identify every `DisposableEffect`, `LaunchedEffect`, polling loop, and `NativeTicketRemoteStore` call that can trigger `get-ticket-list`, `fetchUpdatedAt`, or Realtime subscription setup.
- [ ] Record which screen owns each call and whether it is per-screen, per-session, or app-wide.
- [ ] Confirm which calls are safe to dedupe globally and which must remain screen-local.

**Validation:**
- Produce a short inventory note in the plan or PR description listing each caller and its cadence.

### Task 2: Introduce a shared app-level refresh/realtime owner

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/sync/SharedSyncOwner.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`

- [ ] Create a single shared owner for realtime subscriptions and ticket refresh governors that lives for the app/session scope instead of the composable scope.
- [ ] Expose immutable state and explicit refresh events from the shared owner.
- [ ] Replace per-screen `remember { LotterynetRealtimeClient() }` paths with the shared owner wherever the screen is only observing shared operational data.

**Validation:**
- App navigation between these screens should not create a new subscription graph each time the screen recomposes.

### Task 3: Move repeated refresh triggers behind lifecycle-aware gates

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`

- [ ] Convert “poll while visible” logic into lifecycle-scoped refresh requests that run once on resume and then wait for a real state change.
- [ ] Keep Compose side effects in `LaunchedEffect`/`DisposableEffect` only for lifecycle synchronization, not repeated business polling.
- [ ] Remove duplicate “refresh on every recomposition” patterns by keying effects on stable identifiers only.

**Validation:**
- Rotating the screen or returning to it should not multiply the same server request if the inputs did not change.

### Task 4: Add a global dedupe/governor for `get-ticket-list` and `fetchUpdatedAt`

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/TicketRefreshGovernor.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`

- [ ] Promote the current per-instance governor into a shared dedupe path keyed by owner + action + auth scope.
- [ ] Reuse one in-flight request across screens when the payload is equivalent.
- [ ] Keep a short freshness window for `updated-at` so the app can answer from cache during bursts without changing business logic.

**Validation:**
- Multiple screens asking for the same owner’s ticket stamp within the cooldown should result in one remote call, not many.

### Task 5: Guard private Realtime topics so unauthorized joins do not spiral

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`
- Inspect: `supabase/migrations/20260612104500_users_state_broadcast_signal.sql`
- Inspect: `supabase/migrations/20260604072458_realtime_broadcast_redis_sentry_foundation.sql`

- [ ] Keep private topic joins lifecycle-safe and ensure one failed authorization does not trigger immediate repeated join attempts.
- [ ] Verify that the app only subscribes to private topics when the bearer token is present and the topic is actually needed.
- [ ] Align the topic naming and policy expectations with the Supabase Realtime authorization pattern documented by Supabase.

**Validation:**
- The app should stop spamming `ln:users:global` join attempts when authorization fails.

### Task 6: Add JVM regression tests for dedupe and lifecycle behavior

**Files:**
- Add: `app/src/test/java/com/lotterynet/pro/core/sync/TicketRefreshGovernorTest.kt`
- Add: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClientTest.kt`
- Update: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt`
- Update: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt`
- Update: `app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt`

- [ ] Write a failing test for “same owner + same freshness window returns cached stamp / shared in-flight result.”
- [ ] Write a failing test for “unauthorized private Realtime topic is detected and not retried immediately.”
- [ ] Write a failing test that proves one user-state update produces one refresh decision, not a cascade.

**Validation:**
- Run the relevant JVM tests only, no Gradle release build yet.
- Suggested command: `./gradlew :app:testDebugUnitTest`

### Task 7: Verify with a small smoke pass before any release build

**Files:**
- Inspect: `tools/qa/production-readiness-timing-suite.mjs`
- Inspect: `tools/qa/real-flow-smoke.mjs`
- Inspect: `tools/qa/ticket-payload-integrity-smoke.mjs`

- [ ] Run the narrowest Node smoke test that exercises ticket refresh / realtime flow without building release artifacts.
- [ ] Confirm that the relevant calls drop when the same user/session is opened across multiple screens.
- [ ] Only after that, hand control back for the manual debug/release build the user wants to do themselves.

**Validation:**
- Evidence should show fewer repeated `get-ticket-list` and Realtime join attempts, while keeping the same visible flow.

### Task 8: Document the final architecture and rollback boundary

**Files:**
- Add: `docs/superpowers/plans/2026-06-26-compose-realtime-refresh-dedup-notes.md`
- Add: `docs/backups/compose-realtime-refresh-dedup.rollback.md` if any code touches are shipped

- [ ] Record the “before vs after” call pattern, the exact screens involved, and the rollback point.
- [ ] Note which changes are pure dedupe/caching and which are policy-bound to Supabase Realtime authorization.
- [ ] Keep the rollback boundary tight so the fix can be reverted without affecting unrelated tickets, prizes, or cash flow logic.

**Validation:**
- A maintainer should be able to explain the root cause in one paragraph and revert only the dedupe layer if needed.

## Reference docs reviewed

- [Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
- [ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Guide to app architecture](https://developer.android.com/topic/architecture)
- [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
- [Compose performance](https://developer.android.com/develop/ui/compose/performance)
- [Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Compose lifecycle](https://developer.android.com/develop/ui/compose/lifecycle)

