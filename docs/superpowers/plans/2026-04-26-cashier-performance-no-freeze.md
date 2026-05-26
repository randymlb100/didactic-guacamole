# Cashier Performance No-Freeze Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the cashier experience stay responsive on older Android POS devices, especially Android 8 / 1 GB RAM, by preventing local storage, sync, ticket rendering, result sharing, and Bluetooth printing from blocking the UI thread.

**Architecture:** Keep the native cashier screen local-first and fast to open. Move expensive JSON reads/writes, sync flushes, bitmap rendering, export, and Bluetooth operations to explicit background workers. Add small deterministic performance contracts, cache parsed local ticket data, throttle sync, reuse render PNGs, cap bitmap memory, and add QA checks for second-share / second-print responsiveness.

**Tech Stack:** Kotlin, Android SDK 26+, Jetpack Compose, SharedPreferences/local repositories, native sync coordinators, bitmap render cache, Bluetooth thermal printing, JUnit, Gradle.

---

## File Map

Create:
- `app/src/main/java/com/lotterynet/pro/core/perf/PosPerformanceBudget.kt`
- `app/src/main/java/com/lotterynet/pro/core/perf/MainThreadWorkPolicy.kt`
- `app/src/main/java/com/lotterynet/pro/core/storage/SalesDayTicketCache.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/OperationalSyncThrottle.kt`
- `app/src/test/java/com/lotterynet/pro/core/perf/PosPerformanceBudgetTest.kt`
- `app/src/test/java/com/lotterynet/pro/core/storage/SalesDayTicketCacheTest.kt`
- `app/src/test/java/com/lotterynet/pro/core/sync/OperationalSyncThrottleTest.kt`
- `app/src/test/java/com/lotterynet/pro/ui/sales/CashierStartupContractsTest.kt`
- `docs/qa/cashier-old-android-performance-checklist.md`

Modify:
- `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- `app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt`
- `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketSyncQueueRepository.kt`
- `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`
- `app/src/main/java/com/lotterynet/pro/core/render/LocalRenderCacheRepository.kt`
- `app/src/main/java/com/lotterynet/pro/core/printing/BluetoothThermalPrinter.kt`
- `app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt`

---

## Task 1: Define POS Performance Budgets

Files:
- Create `app/src/main/java/com/lotterynet/pro/core/perf/PosPerformanceBudget.kt`
- Create `app/src/main/java/com/lotterynet/pro/core/perf/MainThreadWorkPolicy.kt`
- Create `app/src/test/java/com/lotterynet/pro/core/perf/PosPerformanceBudgetTest.kt`

- [ ] Step 1: Write failing tests.

Create `PosPerformanceBudgetTest.kt`:

```kotlin
package com.lotterynet.pro.core.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosPerformanceBudgetTest {
    @Test
    fun `cashier first frame budget is strict for old pos`() {
        assertEquals(450L, PosPerformanceBudget.CASHIER_FIRST_FRAME_MS)
        assertEquals(120L, PosPerformanceBudget.LOCAL_READ_UI_WARNING_MS)
        assertEquals(1_500L, PosPerformanceBudget.SECOND_SHARE_MAX_MS)
    }

    @Test
    fun `expensive cashier work is never allowed on main thread`() {
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.TICKET_JSON_IMPORT))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.SYNC_FLUSH))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.BITMAP_RENDER))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.BLUETOOTH_PRINT))
        assertTrue(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.UI_STATE_UPDATE))
    }
}
```

- [ ] Step 2: Run the focused test and confirm it fails.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.perf.PosPerformanceBudgetTest"
```

- [ ] Step 3: Implement `PosPerformanceBudget.kt`.

```kotlin
package com.lotterynet.pro.core.perf

object PosPerformanceBudget {
    const val CASHIER_FIRST_FRAME_MS = 450L
    const val LOCAL_READ_UI_WARNING_MS = 120L
    const val SECOND_SHARE_MAX_MS = 1_500L
    const val BLUETOOTH_CONNECT_TIMEOUT_MS = 4_000L
    const val BLUETOOTH_WRITE_TIMEOUT_MS = 6_000L
    const val SYNC_RESUME_THROTTLE_MS = 60_000L
    const val LOW_RAM_BITMAP_MAX_WIDTH_PX = 720
}
```

- [ ] Step 4: Implement `MainThreadWorkPolicy.kt`.

```kotlin
package com.lotterynet.pro.core.perf

enum class MainThreadWork {
    UI_STATE_UPDATE,
    TICKET_JSON_IMPORT,
    TICKET_JSON_EXPORT,
    SYNC_FLUSH,
    BITMAP_RENDER,
    BITMAP_EXPORT,
    BLUETOOTH_PRINT,
}

object MainThreadWorkPolicy {
    fun canRunOnMain(work: MainThreadWork): Boolean {
        return work == MainThreadWork.UI_STATE_UPDATE
    }
}
```

- [ ] Step 5: Run the test and confirm it passes.

---

## Task 2: Cache Parsed Local Ticket Days

Problem:
- `LocalSalesRepository.getAllTickets()` and `getTicketsForDay()` can parse many JSON arrays repeatedly.
- On old POS devices, repeated JSON parse/sort from the UI path can freeze the cashier screen.

Files:
- Create `app/src/main/java/com/lotterynet/pro/core/storage/SalesDayTicketCache.kt`
- Create `app/src/test/java/com/lotterynet/pro/core/storage/SalesDayTicketCacheTest.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`

- [ ] Step 1: Write failing pure unit tests for cache reuse and invalidation.

Create `SalesDayTicketCacheTest.kt`:

```kotlin
package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Test

class SalesDayTicketCacheTest {
    @Test
    fun `same raw json returns same parsed snapshot`() {
        val cache = SalesDayTicketCache()
        val raw = """[{"id":"T-1","status":"active","total":10.0}]"""

        val first = cache.getOrParse("2026-04-26", raw)
        val second = cache.getOrParse("2026-04-26", raw)

        assertSame(first, second)
        assertEquals("T-1", first.single().id)
    }

    @Test
    fun `changed raw json invalidates parsed snapshot`() {
        val cache = SalesDayTicketCache()
        val first = cache.getOrParse("2026-04-26", """[{"id":"T-1"}]""")
        val second = cache.getOrParse("2026-04-26", """[{"id":"T-2"}]""")

        assertNotSame(first, second)
        assertEquals("T-2", second.single().id)
    }

    @Test
    fun `manual invalidation clears one day only`() {
        val cache = SalesDayTicketCache()
        val raw = """[{"id":"T-1"}]"""
        val first = cache.getOrParse("2026-04-26", raw)

        cache.invalidate("2026-04-26")
        val second = cache.getOrParse("2026-04-26", raw)

        assertNotSame(first, second)
    }
}
```

- [ ] Step 2: Run and confirm compile failure.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.storage.SalesDayTicketCacheTest"
```

- [ ] Step 3: Implement `SalesDayTicketCache`.

Implementation requirements:
- Keep a small in-memory `LinkedHashMap<String, Entry>`.
- Entry fields: `rawHash: Int`, `tickets: List<TicketRecord>`.
- Use the same JSON adapter/serializer style already used by `LocalSalesRepository`.
- Return immutable list snapshots.
- Cap cache to the latest 14 day keys to avoid memory growth.
- Add `invalidate(dayKey: String)` and `clear()`.

- [ ] Step 4: Wire `LocalSalesRepository`.

Implementation requirements:
- Add a private `SalesDayTicketCache`.
- `getTicketsForDay(dayKey)` must read the raw day JSON string and call cache `getOrParse(dayKey, raw)`.
- `saveTicket()`, `saveImportedTickets()`, `replaceScopedImportedTickets()`, void/delete/paid/winner updates must invalidate affected day keys after writing.
- `getAllTickets()` must reuse day snapshots and only sort the merged list once.

- [ ] Step 5: Run storage tests.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.storage.SalesDayTicketCacheTest"
```

---

## Task 3: Keep Cashier First Screen Light

Problem:
- `SalesActivity.onCreate()` creates many repositories/coordinators and starts hydration.
- The first screen should render before any remote sync, full ticket import, or bitmap work starts.

Files:
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt`
- Create `app/src/test/java/com/lotterynet/pro/ui/sales/CashierStartupContractsTest.kt`

- [ ] Step 1: Add a pure startup contract.

Create `CashierStartupContractsTest.kt`:

```kotlin
package com.lotterynet.pro.ui.sales

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierStartupContractsTest {
    @Test
    fun `cashier first frame excludes remote and render work`() {
        val plan = resolveCashierStartupPlan()

        assertTrue(plan.firstFrameWork.contains(CashierStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(CashierStartupWork.LOAD_LOCAL_DRAFT))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_TICKETS))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.FLUSH_SYNC_QUEUE))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.RENDER_TICKET_BITMAP))
    }

    @Test
    fun `cashier heavy startup work runs after first frame`() {
        val plan = resolveCashierStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_TICKETS))
        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.FLUSH_SYNC_QUEUE))
    }
}
```

- [ ] Step 2: Implement the contract in `SalesUiContracts.kt`.

Add:

```kotlin
enum class CashierStartupWork {
    LOAD_SESSION,
    LOAD_LOCAL_DRAFT,
    LOAD_LOCAL_CATALOG,
    HYDRATE_REMOTE_TICKETS,
    FLUSH_SYNC_QUEUE,
    RENDER_TICKET_BITMAP,
}

data class CashierStartupPlan(
    val firstFrameWork: Set<CashierStartupWork>,
    val afterFirstFrameWork: Set<CashierStartupWork>,
)

fun resolveCashierStartupPlan(): CashierStartupPlan {
    return CashierStartupPlan(
        firstFrameWork = setOf(
            CashierStartupWork.LOAD_SESSION,
            CashierStartupWork.LOAD_LOCAL_DRAFT,
            CashierStartupWork.LOAD_LOCAL_CATALOG,
        ),
        afterFirstFrameWork = setOf(
            CashierStartupWork.HYDRATE_REMOTE_TICKETS,
            CashierStartupWork.FLUSH_SYNC_QUEUE,
        ),
    )
}
```

- [ ] Step 3: Update `SalesActivity`.

Implementation requirements:
- Keep login/session validation synchronous.
- Render `SalesRoute` as soon as local session, local draft, and static catalog are ready.
- Move `nativeTicketCloudSyncCoordinator.hydrateOwner(...)` to a background job launched after the first composition.
- Move queue flush to background and never block sale entry.
- Do not render ticket bitmaps in `onCreate()`.
- If using Compose, start background work from `LaunchedEffect(session.id)` with `Dispatchers.IO`, or keep the existing `thread(...)` but trigger it after content is shown and guard against duplicate starts.

- [ ] Step 4: Run the startup contract and assemble.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.CashierStartupContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

---

## Task 4: Throttle Background Sync

Problem:
- Sync should help, not freeze the cashier.
- App resume, ticket save, and manual refresh need different sync behavior.

Files:
- Create `app/src/main/java/com/lotterynet/pro/core/sync/OperationalSyncThrottle.kt`
- Create `app/src/test/java/com/lotterynet/pro/core/sync/OperationalSyncThrottleTest.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`

- [ ] Step 1: Write sync throttle tests.

Create `OperationalSyncThrottleTest.kt`:

```kotlin
package com.lotterynet.pro.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalSyncThrottleTest {
    @Test
    fun `resume sync is throttled`() {
        val throttle = OperationalSyncThrottle(minIntervalMs = 60_000L)

        assertTrue(throttle.shouldRun(nowMs = 100_000L, force = false))
        throttle.markRan(nowMs = 100_000L)
        assertFalse(throttle.shouldRun(nowMs = 120_000L, force = false))
        assertTrue(throttle.shouldRun(nowMs = 161_000L, force = false))
    }

    @Test
    fun `manual sync bypasses throttle`() {
        val throttle = OperationalSyncThrottle(minIntervalMs = 60_000L)
        throttle.markRan(nowMs = 100_000L)

        assertTrue(throttle.shouldRun(nowMs = 101_000L, force = true))
    }
}
```

- [ ] Step 2: Implement `OperationalSyncThrottle`.

Implementation requirements:
- Store only `lastRunMs`.
- `shouldRun(nowMs, force)` returns true when forced or enough time elapsed.
- `markRan(nowMs)` records successful start time.

- [ ] Step 3: Wire sync policy.

Implementation requirements:
- On cashier screen resume: run sync only when throttle allows.
- After ticket save: enqueue local ticket immediately, flush in background.
- Manual refresh/results refresh: force sync, show non-blocking progress.
- Any sync exception must be logged or surfaced as a small status, never crash or block sales input.

- [ ] Step 4: Run tests.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.OperationalSyncThrottleTest"
```

---

## Task 5: Make Ticket Save and Print Non-Blocking

Problem:
- Cashier ticket creation must immediately return control to the user.
- Bluetooth connect/write can hang on older devices if done on the UI path.

Files:
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/printing/BluetoothThermalPrinter.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt`
- Create or extend printing tests under `app/src/test/java/com/lotterynet/pro/core/printing/`

- [ ] Step 1: Add print policy tests.

Contract:
- Bluetooth connect timeout is `PosPerformanceBudget.BLUETOOTH_CONNECT_TIMEOUT_MS`.
- Bluetooth write timeout is `PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS`.
- Thermal rendering can build command bytes before connecting.
- Printer failure returns a recoverable result and does not mark the sale failed.

- [ ] Step 2: Implement non-blocking sale completion.

Implementation requirements:
- Save ticket locally first.
- Enqueue sync second.
- Start print/share work in background.
- UI shows success and next-ticket controls immediately after local save.
- Print failure should show "Ticket guardado, impresora no respondió" style status, not freeze the cashier.

- [ ] Step 3: Add Bluetooth timeout guards.

Implementation requirements:
- Wrap socket connect/write with bounded timeout.
- Close socket on timeout/failure.
- Avoid repeated reconnect loops in the same click.
- Add a short per-printer backoff after timeout.

- [ ] Step 4: Run printing tests and assemble.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.printing.*"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

---

## Task 6: Reduce Bitmap Memory for Share/Save

Problem:
- Ticket/result PNG rendering can allocate too much memory on Android 8 / 1 GB RAM.
- Second share must reuse the cached PNG, not render again.

Files:
- Modify `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`
- Modify `app/src/main/java/com/lotterynet/pro/core/render/LocalRenderCacheRepository.kt`
- Modify ticket/results share call sites if needed.
- Add tests under `app/src/test/java/com/lotterynet/pro/core/export/`

- [ ] Step 1: Add export spec tests.

Contract:
- Low-RAM export max width is `PosPerformanceBudget.LOW_RAM_BITMAP_MAX_WIDTH_PX`.
- Existing cached URI is preferred over rendering.
- Multiple result pages share URI list directly without decoding PNGs back into bitmaps.

- [ ] Step 2: Implement export spec helper.

Implementation requirements:
- `resolveBitmapExportSpec(isLowRamDevice: Boolean, requestedWidth: Int)` returns a bounded width.
- Use ActivityManager low-RAM signal where Android APIs allow it.
- Keep PNG quality at 100 for text clarity.
- Avoid holding multiple large bitmaps longer than necessary.

- [ ] Step 3: Ensure cache-first share.

Implementation requirements:
- Ticket WhatsApp/share/save checks `LocalRenderCacheRepository.getUriIfPresent(key)` first.
- Results WhatsApp/share checks all page keys first.
- Only missing pages render.
- Add `NativeBitmapExport.shareImageUris(...)` if it is not already present, so cached PNGs are shared as URIs.

- [ ] Step 4: Run ticket/results/export tests.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"
```

---

## Task 7: Stabilize Compose Recomposition in Sales

Problem:
- Old devices can freeze from repeated recomposition, list rebuilds, and recalculation while cashier types numbers/amounts quickly.

Files:
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt`
- Add or extend sales UI contract tests.

- [ ] Step 1: Add pure contracts for stable cashier input.

Contract:
- Number input updates do not trigger ticket history reload.
- Amount input updates do not trigger remote sync.
- Lottery toggle updates only the selected lottery state.
- Ticket totals are derived from the current draft, not from repository reads.

- [ ] Step 2: Refactor sales state.

Implementation requirements:
- Keep mutable draft state separate from repository state.
- Use `rememberSaveable` for current number/amount/play type.
- Use `derivedStateOf` for totals and validation.
- Use stable keys in any `LazyColumn` / `LazyVerticalGrid`.
- Avoid reading ticket history on every recomposition.

- [ ] Step 3: Keep IO out of composables.

Implementation requirements:
- Repository calls happen inside explicit background effects/events.
- Composables receive plain state and callbacks.
- No ticket JSON parsing from composable body.

- [ ] Step 4: Run sales tests and assemble.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.*"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

---

## Task 8: Add Old Android QA Checklist

Files:
- Create `docs/qa/cashier-old-android-performance-checklist.md`

- [ ] Step 1: Create checklist.

Content:

```markdown
# Cashier Old Android Performance Checklist

Target device:
- Android 8 or closest available POS device
- 1 GB RAM or low-RAM mode
- Thermal printer paired
- WhatsApp installed
- Internet can be toggled off/on

Pass requirements:
1. Login as cashier.
2. First native sales screen appears without WebView.
3. Cashier can type number and amount without visible delay.
4. Create one ticket; UI returns to ready state immediately after local save.
5. Print ticket; if printer is slow, app stays usable.
6. Reopen official ticket.
7. Share official ticket by WhatsApp twice; second share finishes under 1.5 seconds.
8. Open results and share multiple pages twice; second share reuses cached PNGs.
9. Turn internet off and create another ticket; app stays usable and ticket remains saved.
10. Turn internet on and sync; cashier screen does not freeze during flush.

Fail conditions:
- App freezes for more than 2 seconds during typing, save, print, or second share.
- Any normal cashier action blocks on remote network.
- Ticket disappears after offline save.
- Printer failure prevents continuing sales.
```

- [ ] Step 2: Build APK.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

- [ ] Step 3: Install on actual POS and run the checklist.

---

## Final Verification

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected:
- All unit tests pass.
- Debug APK builds.
- Cashier screen opens natively and quickly.
- Ticket save is local-first and non-blocking.
- Sync never blocks cashier input.
- Print timeout/failure does not freeze the app.
- Ticket/result second share uses cached PNGs.
- Old Android POS checklist passes.
