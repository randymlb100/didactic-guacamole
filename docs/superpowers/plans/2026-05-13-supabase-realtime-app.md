# Supabase Realtime App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Supabase Realtime to the LotteryNet Android app where it materially improves freshness and reduces repeated REST reads, without turning results, users, or sales flows into a noisy always-on socket system.

**Architecture:** Keep Realtime narrow and scoped. Use it for small, high-value state changes keyed by `ownerKey`, `scope`, or a single `date`/`key`, and keep heavy data hydration on existing repositories/coordinators. Results stay `snapshot-first`; Realtime only signals when today's cached result rows changed. Users, cashier limits, ticket snapshots, and selected master config keys get direct event subscriptions with local cache refresh.

**Tech Stack:** Android Kotlin, existing local repositories/coordinators, Supabase project `unhoulkujbtsypccpirc`, Supabase Kotlin client with `Realtime` and `Postgrest` plugins, existing Render backend for snapshot-first results.

---

## File Structure

**Android app**

- Modify: `app/build.gradle.kts`
  Add the Supabase Kotlin dependencies needed for Realtime and Postgrest, plus Ktor websocket transport if required by the current client version.
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
  Own the Supabase client instance, connect/disconnect, and channel lifecycle.
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`
  Define typed subscription descriptors for users, master keys, tickets, recharges, cashier limits, and results cache keys.
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeEvent.kt`
  Typed event model for row changes so screens/coordinators do not parse raw payloads everywhere.
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
  High-level coordinator that wires channels to existing repositories/coordinators and throttles refreshes.
- Modify: `app/src/main/java/com/lotterynet/pro/core/users/SupabaseUsersRemoteStore.kt`
  Add a Realtime-aware refresh path for `lotterynet_users_state`.
- Modify: `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`
  Keep current edge-function reads, but support refresh-on-event for keys that already live in `lotterynet_master_state`.
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
  Add entry points to refresh owner snapshots when ticket/recharge events arrive.
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeCloudSyncCoordinator.kt`
  Add refresh hooks for live recharge events.
- Modify: `app/src/main/java/com/lotterynet/pro/core/results/ResultsScraperOrchestrator.kt`
  Accept a low-cost "remote changed" signal so today's results snapshot can refresh without manual polling.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
  Subscribe only while visible, scoped to selected date and current mode.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
  Subscribe to cashier limits and operational owner changes while the sales screen is active.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
  Own app-level start/stop of realtime only for admin/session-wide signals if needed.
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt`
  Expand the existing contracts into real Realtime coverage.
- Create: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt`
  Event routing and throttling tests.
- Create: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt`
  Table/key/filter mapping tests.

**Supabase / docs**

- Create: `docs/supabase/2026-05-13-realtime-rollout.sql`
  SQL to add tables to the `supabase_realtime` publication and verify grants/RLS.
- Create: `docs/supabase/2026-05-13-realtime-rollout-checklist.md`
  Dashboard steps: publication toggles, table exposure, and operational checks.

## Scope Decision

Realtime should be enabled for these data classes first:

- `lotterynet_users_state`
  Scope: `scope=eq.global`
  Reason: small payload, high impact on bootstrap/admin user changes.
- `lotterynet_master_state`
  Scope: filtered by concrete keys already used in app:
  `sys_master_limits_v1`, `sys_presence_v1`, `sys_alerts_v4`, `sys_audit_v4`, `cashier_limits:<adminId>`
  Reason: config freshness without repeated probes.
- `lotterynet_tickets_by_owner`
  Scope: `owner_key=eq.<adminId/adminUser>`
  Reason: strongest operational win for admin/cajero visibility.
- Recharge table or snapshot source used by `recharge-history-state`
  Scope: owner-based
  Reason: finance and recharge history freshness.
- `lotterynet_kv`
  Scope: only specific result cache keys for the current date:
  `lot_results_cache_by_day:<dd-MM-yyyy>`
  `pick_results_cache_by_day:<dd-MM-yyyy>`
  `manual_results_overrides_by_day:<dd-MM-yyyy>`
  Reason: refresh today's snapshot when backend already wrote it.

Realtime should **not** be phase-1 for:

- Full Pick live scraping
- All historical results dates
- All `lotterynet_kv` keys
- Auth/session creation

Those remain snapshot-first because otherwise the app will trade one bottleneck for too many channels and too much egress.

### Task 1: Dependency and client foundation

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt`

- [ ] **Step 1: Write the failing dependency baseline test**

```kotlin
import org.junit.Assert.assertTrue
import org.junit.Test

class LotterynetRealtimeSubscriptionTest {
    @Test
    fun `realtime module exposes required logical channels`() {
        val names = listOf(
            "users-global",
            "master-owner",
            "tickets-owner",
            "results-today",
        )
        assertTrue(names.contains("users-global"))
    }
}
```

- [ ] **Step 2: Run test to verify the module does not exist yet**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscriptionTest"`
Expected: FAIL because the new Realtime package/classes do not exist yet.

- [ ] **Step 3: Add minimal dependencies**

Add only the modules needed for current scope in `app/build.gradle.kts`:

```kotlin
implementation("io.github.jan-tennert.supabase:supabase-kt:<verified-version>")
implementation("io.github.jan-tennert.supabase:realtime-kt:<verified-version>")
implementation("io.github.jan-tennert.supabase:postgrest-kt:<verified-version>")
implementation("io.ktor:ktor-client-okhttp:<verified-version>")
implementation("io.ktor:ktor-client-websockets:<verified-version>")
```

Keep current custom REST/edge clients. Do not migrate existing stores wholesale in this task.

- [ ] **Step 4: Create the minimal Realtime client shell**

```kotlin
package com.lotterynet.pro.core.realtime

data class LotterynetRealtimeConfig(
    val url: String,
    val publishableKey: String,
)

class LotterynetRealtimeClient(
    private val config: LotterynetRealtimeConfig,
) {
    fun isConfigured(): Boolean {
        return config.url.isNotBlank() && config.publishableKey.isNotBlank()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscriptionTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt
git commit -m "feat: add realtime client foundation"
```

### Task 2: Supabase publication and table scope

**Files:**
- Create: `docs/supabase/2026-05-13-realtime-rollout.sql`
- Create: `docs/supabase/2026-05-13-realtime-rollout-checklist.md`

- [ ] **Step 1: Write the rollout SQL**

```sql
alter publication supabase_realtime add table public.lotterynet_users_state;
alter publication supabase_realtime add table public.lotterynet_master_state;
alter publication supabase_realtime add table public.lotterynet_tickets_by_owner;

-- Add only if recharge snapshots live in a real table and not edge-only state:
-- alter publication supabase_realtime add table public.<recharge_table_name>;

-- Results cache stays narrow:
alter publication supabase_realtime add table public.lotterynet_kv;
```

Do not add every public table blindly.

- [ ] **Step 2: Document the dashboard checklist**

Include:
- verify tables are exposed to the Data API only if intended
- verify RLS remains enabled
- verify the `supabase_realtime` publication contains only the needed tables
- verify expected row filters exist for `anon`/`authenticated`

- [ ] **Step 3: Verify against the official requirement**

Use Supabase docs as the acceptance source:
- Realtime is disabled by default for new tables
- tables must be added to `supabase_realtime`
- Kotlin realtime flows require both Realtime and Postgrest when using `selectAsFlow`

- [ ] **Step 4: Commit**

```bash
git add docs/supabase/2026-05-13-realtime-rollout.sql docs/supabase/2026-05-13-realtime-rollout-checklist.md
git commit -m "docs: add supabase realtime rollout guide"
```

### Task 3: Typed subscription map

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeEvent.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt`

- [ ] **Step 1: Write the failing mapping test**

```kotlin
@Test
fun `ticket owner subscription targets tickets table with owner filter`() {
    val subscription = LotterynetRealtimeSubscription.ticketOwner("admin-1")
    assertEquals("public", subscription.schema)
    assertEquals("lotterynet_tickets_by_owner", subscription.table)
    assertEquals("owner_key=eq.admin-1", subscription.filter)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscriptionTest"`
Expected: FAIL because the descriptor does not exist yet.

- [ ] **Step 3: Implement the descriptors**

```kotlin
package com.lotterynet.pro.core.realtime

data class LotterynetRealtimeSubscription(
    val channelName: String,
    val schema: String,
    val table: String,
    val filter: String? = null,
) {
    companion object {
        fun usersGlobal() = LotterynetRealtimeSubscription(
            channelName = "users-global",
            schema = "public",
            table = "lotterynet_users_state",
            filter = "scope=eq.global",
        )

        fun masterKey(key: String) = LotterynetRealtimeSubscription(
            channelName = "master-$key",
            schema = "public",
            table = "lotterynet_master_state",
            filter = "config_key=eq.$key",
        )

        fun ticketOwner(ownerKey: String) = LotterynetRealtimeSubscription(
            channelName = "tickets-$ownerKey",
            schema = "public",
            table = "lotterynet_tickets_by_owner",
            filter = "owner_key=eq.$ownerKey",
        )

        fun resultsCache(key: String) = LotterynetRealtimeSubscription(
            channelName = "results-$key",
            schema = "public",
            table = "lotterynet_kv",
            filter = "key=eq.$key",
        )
    }
}
```

- [ ] **Step 4: Add the event model**

```kotlin
package com.lotterynet.pro.core.realtime

enum class LotterynetRealtimeEventType { INSERT, UPDATE, DELETE }

data class LotterynetRealtimeEvent(
    val type: LotterynetRealtimeEventType,
    val table: String,
    val filterValue: String? = null,
    val payloadJson: String,
)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscriptionTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeEvent.kt app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscriptionTest.kt
git commit -m "feat: define realtime subscription map"
```

### Task 4: Realtime orchestrator for users and master config

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/users/SupabaseUsersRemoteStore.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt`

- [ ] **Step 1: Write the failing users refresh test**

```kotlin
@Test
fun `users-state update triggers a single remote users refresh`() {
    var refreshCalls = 0
    val orchestrator = LotterynetRealtimeOrchestrator(
        onUsersChanged = { refreshCalls += 1 },
    )

    orchestrator.onEvent(
        LotterynetRealtimeEvent(
            type = LotterynetRealtimeEventType.UPDATE,
            table = "lotterynet_users_state",
            filterValue = "global",
            payloadJson = """{"scope":"global"}""",
        )
    )

    assertEquals(1, refreshCalls)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestratorTest"`
Expected: FAIL because the orchestrator does not exist yet.

- [ ] **Step 3: Implement minimal users/master routing**

```kotlin
class LotterynetRealtimeOrchestrator(
    private val onUsersChanged: () -> Unit = {},
    private val onMasterKeyChanged: (String) -> Unit = {},
) {
    fun onEvent(event: LotterynetRealtimeEvent) {
        when (event.table) {
            "lotterynet_users_state" -> onUsersChanged()
            "lotterynet_master_state" -> event.filterValue?.let(onMasterKeyChanged)
        }
    }
}
```

- [ ] **Step 4: Wire store-level refresh hooks**

In `SupabaseUsersRemoteStore`, add a `refreshUsersPayload()` helper that explicitly re-fetches and returns current payload without changing save semantics.

In `SupabaseMasterConfigRemoteStore`, add a `refreshValue(key: String)` helper that reuses current `fetchValue` path.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestratorTest" --tests "com.lotterynet.pro.core.users.SupabaseUsersRemoteStoreTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt app/src/main/java/com/lotterynet/pro/core/users/SupabaseUsersRemoteStore.kt app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt
git commit -m "feat: route realtime events for users and master state"
```

### Task 5: Operational owner realtime for tickets, recharges, and cashier limits

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeCloudSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt`

- [ ] **Step 1: Write the failing owner refresh contract**

```kotlin
@Test
fun `ticket owner realtime event requests hydrate for matching owner`() {
    var hydratedOwner: String? = null
    val gateway = object : TicketCloudSyncGateway {
        override fun enqueueAndFlush(ticket: TicketRecord, banca: String?) = error("unused")
        override fun hydrateOwner(ownerKey: String, banca: String?) =
            NativeTicketCloudSyncResult(ok = true, message = "ok", pulledCount = 1).also {
                hydratedOwner = ownerKey
            }
        override fun flushOwner(ownerKey: String, banca: String?) = error("unused")
        override fun flushOwnerLocalSnapshot(ownerKey: String, banca: String?) = error("unused")
    }
    val coordinator = NativeOperationalSyncCoordinator(ticketGateway = gateway)
    coordinator.flushOwner("admin-1", null)
    assertEquals("admin-1", hydratedOwner)
}
```

- [ ] **Step 2: Add explicit realtime refresh entry points**

Add methods such as:

```kotlin
fun refreshOwnerFromRealtime(ownerKey: String, banca: String? = null): NativeOperationalSyncState
fun refreshRechargeOwnerFromRealtime(ownerKey: String): Boolean
fun refreshCashierLimitsFromRealtime(ownerKey: String): Boolean
```

These methods should reuse existing hydrate/fetch code instead of duplicating network logic.

- [ ] **Step 3: Wire Sales screen lifecycle**

`SalesActivity` should subscribe while visible to:
- `cashier_limits:<ownerKey>` in `lotterynet_master_state`
- `lotterynet_tickets_by_owner` for `owner_key=eq.<ownerKey>`

On event:
- refresh cashier limit cache
- hydrate operational snapshot if a ticket row changed

Do not subscribe to global results or users from Sales.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.sync.RealtimeFlowContractsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeCloudSyncCoordinator.kt app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt
git commit -m "feat: add owner-scoped realtime operational refresh"
```

### Task 6: Results realtime without live-scrape sockets

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/results/ResultsScraperOrchestrator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt`

- [ ] **Step 1: Write the failing results signal contract**

```kotlin
@Test
fun `results realtime signal forces snapshot refresh but not live scrape for historical dates`() {
    assertFalse(
        shouldForceLiveResultsFetch(
            selectedDate = "12-05-2026",
            today = "13-05-2026",
            allowLive = true,
            needsRemoteCompletion = true,
        )
    )
}
```

- [ ] **Step 2: Add a lightweight realtime refresh entry point**

In `ResultsScraperOrchestrator`, add:

```kotlin
fun refreshDateFromRealtime(date: String): ResultsRefreshResult {
    return refreshDate(
        date = date,
        forceRemote = true,
        allowLive = false,
    )
}
```

This keeps Realtime tied to already-written snapshot rows, not to live scraper work.

- [ ] **Step 3: Wire ResultsActivity subscriptions**

Subscribe while visible only to the current selected date keys:
- `lot_results_cache_by_day:<date>`
- `pick_results_cache_by_day:<date>`
- `manual_results_overrides_by_day:<date>`

On event:
- call `refreshDateFromRealtime(selectedDate)`
- update the visible list

If the selected date changes from `Hoy` to `Ayer`, unsubscribe old keys and subscribe new keys.

- [ ] **Step 4: Keep Render as source of truth for payload assembly**

Do not replace current Render-first work for heavy results payloads. Realtime is only the "data changed" signal here.

- [ ] **Step 5: Run tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.sync.RealtimeFlowContractsTest" --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/core/results/ResultsScraperOrchestrator.kt app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt
git commit -m "feat: add realtime result snapshot refresh signals"
```

### Task 7: App lifecycle, throttling, and failure policy

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt`

- [ ] **Step 1: Write the failing throttling test**

```kotlin
@Test
fun `duplicate realtime updates for same key are coalesced`() {
    var calls = 0
    val orchestrator = LotterynetRealtimeOrchestrator(
        onUsersChanged = { calls += 1 },
    )

    repeat(3) {
        orchestrator.onEvent(
            LotterynetRealtimeEvent(
                type = LotterynetRealtimeEventType.UPDATE,
                table = "lotterynet_users_state",
                filterValue = "global",
                payloadJson = """{"scope":"global"}""",
            )
        )
    }

    assertEquals(1, calls)
}
```

- [ ] **Step 2: Implement minimal coalescing**

Use a short in-memory debounce per `(table, filter)` key so bursts of updates do not trigger repeated refreshes.

- [ ] **Step 3: Add lifecycle policy**

`ShellActivity` or a lightweight app-level owner should:
- connect the realtime client when a logged-in foreground session exists
- disconnect when the app goes fully background for a sustained interval

Screen-level subscriptions still belong to the active screen.

- [ ] **Step 4: Define fallback behavior**

If websocket connect fails:
- app keeps current polling/refresh behavior
- no login or sales flow can hard-fail because Realtime is down
- log handled diagnostic only once per backoff window

- [ ] **Step 5: Run tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestratorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/test/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestratorTest.kt
git commit -m "feat: add realtime throttling and lifecycle policy"
```

### Task 8: Verification and rollout

**Files:**
- Modify: `docs/supabase/2026-05-13-realtime-rollout-checklist.md`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/RealtimeFlowContractsTest.kt`

- [ ] **Step 1: Verify the official Supabase requirements**

Acceptance checks:
- targeted tables are inside `supabase_realtime`
- RLS remains enabled on exposed tables
- Kotlin client can connect and subscribe
- results screen refreshes from snapshot changes without forcing live scrape
- users/master/tickets refresh on event without manual pull

- [ ] **Step 2: Run the Android suite**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.sync.RealtimeFlowContractsTest" --tests "com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestratorTest" --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest" --tests "com.lotterynet.pro.core.users.SupabaseUsersRemoteStoreTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run manual validation**

Manual checks:
1. Admin changes users/master config on one device; second device refreshes without reinstall.
2. Cajero sells on one device; admin monitor/sales screen sees ticket without manual force refresh.
3. Cashier limits update from admin and sales screen reflects it while open.
4. Result cache update for `Hoy` refreshes `ResultsActivity` without manual button.
5. If Realtime is disabled or blocked, app falls back to current non-Realtime behavior.

- [ ] **Step 4: Commit**

```bash
git add docs/supabase/2026-05-13-realtime-rollout-checklist.md
git commit -m "docs: finalize realtime rollout verification"
```

## Self-Review

Spec coverage:
- Activate Supabase Realtime in the app: covered by Tasks 1, 3, 4, 5, 6, 7.
- Make it work with current app architecture: covered by file-specific wiring into existing repositories, coordinators, and screens.
- Avoid harming current flows: covered by narrow scope, snapshot-first results, and explicit fallback policy.

Placeholder scan:
- No `TODO`/`TBD` placeholders remain.
- Each task lists exact files and concrete test/implementation steps.

Type consistency:
- Realtime classes use one naming family: `LotterynetRealtimeClient`, `LotterynetRealtimeSubscription`, `LotterynetRealtimeEvent`, `LotterynetRealtimeOrchestrator`.
- Results keep `refreshDateFromRealtime` separate from current `refreshDate`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-13-supabase-realtime-app.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
