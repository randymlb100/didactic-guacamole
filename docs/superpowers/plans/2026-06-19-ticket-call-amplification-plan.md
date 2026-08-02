# Ticket Call Amplification Reduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reducir de forma segura la avalancha de llamadas repetidas a `get-ticket-list` y `get-master-config`, manteniendo resultados y conciliación correctos sin romper ventas, borrados ni realtime.

**Architecture:** Vamos a atacar el problema donde realmente nace: varios bucles de refresco en Ticket, Sales, Results y Admin siguen pidiendo estado aunque ya está fresco. La corrección será por capas: primero un gobernador compartido de refresh/caché por owner/endpoint, después batching de conciliación para evitar flush por ticket, y al final una verificación con Supabase para confirmar que la carga bajó sin perder consistencia.

**Tech Stack:** Kotlin, Android Compose, kotlinx.coroutines, Supabase Edge Functions, Supabase logs / Query Performance / Realtime Reports, JUnit.

---

### Task 1: Medir y blindar el refresh compartido de tickets y master config

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/sync/TicketRefreshGovernor.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/TicketRefreshGovernorTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStoreTest.kt`

- [ ] **Step 1: Write the failing tests for request coalescing and short-window reuse**

```kotlin
@Test
fun `ticket refresh governor reuses the same request for the same owner and endpoint inside the cool down window`() {
    val governor = TicketRefreshGovernor(
        updatedAtCooldownMs = 15_000L,
        snapshotCooldownMs = 15_000L,
    )

    assertTrue(governor.shouldRun("owner-a", "get-ticket-list", nowMs = 1_000L))
    assertFalse(governor.shouldRun("owner-a", "get-ticket-list", nowMs = 5_000L))
    assertTrue(governor.shouldRun("owner-a", "get-ticket-list", nowMs = 20_000L))
}

@Test
fun `master config fetch dedupes repeated calls for the same key and token`() {
    val store = SupabaseMasterConfigRemoteStore(
        edgeClient = fakeEdgeClient,
        bearerTokenProvider = { "token-a" },
    )

    store.fetchValue("sys_mode")
    store.fetchValue("sys_mode")

    assertEquals(1, fakeEdgeClient.invocationsFor("get-master-config"))
}
```

- [ ] **Step 2: Run the tests and verify they fail before the change**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.core.sync.TicketRefreshGovernorTest --tests com.lotterynet.pro.core.sync.NativeTicketRemoteStoreTest --tests com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStoreTest
```

Expected: the new governor test fails because the class does not exist yet, and the remote-store assertions fail because repeated refreshes are still allowed too aggressively.

- [ ] **Step 3: Implement the governor and wire it into the two hottest stores**

```kotlin
class TicketRefreshGovernor(
    private val updatedAtCooldownMs: Long,
    private val snapshotCooldownMs: Long,
) {
    private val lastRun = ConcurrentHashMap<String, Long>()

    fun shouldRun(ownerKey: String, endpoint: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = "${ownerKey.trim().lowercase()}|$endpoint"
        val cooldownMs = if (endpoint == "updated-at") updatedAtCooldownMs else snapshotCooldownMs
        val previous = lastRun[key]
        if (previous != null && nowMs - previous < cooldownMs) return false
        lastRun[key] = nowMs
        return true
    }
}
```

Use it in:

- `NativeTicketRemoteStore.fetchUpdatedAtFresh(...)` so multiple screens asking for a fresh stamp inside a tiny window reuse the same network answer.
- `SupabaseMasterConfigRemoteStore.invokeMasterConfig(...)` so `get-master-config` does not get re-fired by overlapping startup screens for the same key/token.

- [ ] **Step 4: Run the tests and verify they pass**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.core.sync.TicketRefreshGovernorTest --tests com.lotterynet.pro.core.sync.NativeTicketRemoteStoreTest --tests com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStoreTest
```

Expected: the governor test passes and the remote-store tests confirm the cool-down keeps the same result without extra Supabase hits.

- [ ] **Step 5: Commit the governor/cache change**

```bash
git add app/src/main/java/com/lotterynet/pro/core/sync/TicketRefreshGovernor.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt app/src/test/java/com/lotterynet/pro/core/sync/TicketRefreshGovernorTest.kt app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt app/src/test/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStoreTest.kt
git commit -m "perf: coalesce hot ticket and master refreshes"
```

### Task 2: Batch reconciliation so one result refresh does not flush the same owner many times

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/results/TicketPrizeReconciler.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpCoordinator.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/results/TicketPrizeReconcilerTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinatorTest.kt`

- [ ] **Step 1: Write the failing test that proves reconciliation only flushes once per owner/date batch**

```kotlin
@Test
fun `reconcile tickets batches owner flushes instead of flushing once per ticket`() {
    val flushCalls = mutableListOf<String>()
    val reconciler = TicketPrizeReconciler(
        salesRepository = fakeSalesRepository,
        prizeRepository = fakePrizeRepository,
        onBatchTicketUpdated = { tickets ->
            flushCalls += tickets.mapNotNull { it.adminId }.distinct().joinToString(",")
        },
    )

    reconciler.reconcileTicketsForDate("2026-06-19", winnerResults)

    assertEquals(1, flushCalls.size)
}
```

- [ ] **Step 2: Run the test and verify it fails with the current per-ticket callback**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.core.results.TicketPrizeReconcilerTest --tests com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinatorTest
```

Expected: the test fails because the reconciler still notifies updates one ticket at a time, which in turn can fan out into repeated `get-ticket-list` calls.

- [ ] **Step 3: Refactor the reconciler to collect changed tickets and flush them in one batch**

```kotlin
val changedTickets = mutableListOf<TicketRecord>()
tickets.forEach { ticket ->
    val outcome = validationEngine.validate(ticket, results, prizeConfig)
    val normalized = outcome.ticket
    if (outcome.didValidate && normalized != ticket) {
        salesRepository.replaceTicket(normalized)
        changedTickets += normalized
        updated += 1
    }
}
onBatchTicketUpdated?.invoke(changedTickets)
```

Apply the same pattern in the callers so `flushTicket` / `flushOwner` happens once per owner snapshot, not once per reconciled row.

- [ ] **Step 4: Run the tests and verify they pass**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.core.results.TicketPrizeReconcilerTest --tests com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinatorTest
```

Expected: reconciliation still marks winners correctly, but the number of flushes drops to one batch per owner/date.

- [ ] **Step 5: Commit the reconciliation batching**

```bash
git add app/src/main/java/com/lotterynet/pro/core/results/TicketPrizeReconciler.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpCoordinator.kt app/src/test/java/com/lotterynet/pro/core/results/TicketPrizeReconcilerTest.kt app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinatorTest.kt
git commit -m "perf: batch ticket reconciliation flushes"
```

### Task 3: Make screen refresh loops realtime-aware and owner-aware

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketSummaryStartupContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`

- [ ] **Step 1: Write the failing contract tests for “realtime on means fallback off”**

```kotlin
@Test
fun `ticket summary only uses fallback polling when realtime is unavailable`() {
    assertEquals(300_000L, resolveTicketSummaryPollIntervalMs(realtimeEnabled = true, realtimeConnected = true))
    assertEquals(60_000L, resolveTicketSummaryPollIntervalMs(realtimeEnabled = false, realtimeConnected = false))
}

@Test
fun `sales does not keep polling exposure and results when realtime is already connected`() {
    assertFalse(shouldRunSalesExposureFallbackPoll(realtimeEnabled = true))
    assertFalse(shouldPollSalesResultsWinnerRefreshInBackground(realtimeEnabled = true))
}

@Test
fun `results only auto refreshes when the selected date actually needs recovery`() {
    assertFalse(shouldAutoRefreshResultsFromServer(
        selectedDateIsToday = false,
        hasWaitingResult = true,
        hasRecoverableNoDrawResult = false,
        realtimeEnabled = true,
    ))
}
```

- [ ] **Step 2: Run the tests and verify the current behavior still allows too much background work**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.ui.tickets.TicketSummaryStartupContractsTest --tests com.lotterynet.pro.ui.sales.SalesUiContractsTest --tests com.lotterynet.pro.ui.results.ResultsActivityContractsTest
```

Expected: at least one assertion fails until the screen loops are tightened.

- [ ] **Step 3: Gate every polling loop behind the realtime state and the current lifecycle**

Use these rules:

- `TicketSummaryActivity`: only start fallback polling when realtime is not configured or not connected; keep foreground catch-up throttled and do not trigger `refreshFullTicketDataInBackground()` if the current state already came from a fresh server sync.
- `SalesActivity`: keep the cashier limit pull only for the screens that truly need it, and stop the 5-minute exposure/results loops when realtime is connected and healthy.
- `ResultsActivity`: keep the 60s auto-refresh only for today and only when the board is truly waiting or recoverable; otherwise rely on realtime signals and manual refresh.
- Admin screens: reuse the shared governor so `fetchUpdatedAtFresh()` is not called repeatedly during the same foreground session.

- [ ] **Step 4: Run the tests and verify they pass**

Run:

```powershell
./gradlew test --tests com.lotterynet.pro.ui.tickets.TicketSummaryStartupContractsTest --tests com.lotterynet.pro.ui.sales.SalesUiContractsTest --tests com.lotterynet.pro.ui.results.ResultsActivityContractsTest
```

Expected: background polling is now bounded, realtime has priority, and the contracts still protect the cashier/admin flows.

- [ ] **Step 5: Commit the screen-loop tightening**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt app/src/test/java/com/lotterynet/pro/ui/tickets/TicketSummaryStartupContractsTest.kt app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt
git commit -m "perf: make refresh loops realtime-aware"
```

### Task 4: Verify the fix against Supabase logs and query performance

**Files:**
- No code changes required if the previous tasks pass; this is a verification task.

- [ ] **Step 1: Run a 5-minute live smoke on a real account and collect request counts**

Measure:

- `get-ticket-list`
- `get-master-config`
- `get-results-v2`
- `get-ticket-delta`

Expected result:

- `get-ticket-list` should stop behaving like a per-screen/per-ticket firehose.
- `get-master-config` should be called once per relevant startup scope, not on every screen bounce.
- `get-results-v2` should remain low and stable.

- [ ] **Step 2: Re-check Supabase Query Performance and Realtime Reports**

Confirm:

- lower request frequency,
- fewer concurrent reads,
- no new long-running spikes,
- realtime connection counts remain healthy.

- [ ] **Step 3: If the logs still show a spike, only then tune the remaining hot screen**

Use the logs to identify the last remaining caller before touching more code. Do not broaden the fix to unrelated flows unless the metrics prove they are still part of the spike.

