# Native Local No-WebView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LotteryNet fully native and local-first so cashier/admin workflows do not depend on WebView and do not repeatedly render tickets/results unless the underlying data changes.

**Architecture:** Replace the legacy WebView entry path with native Compose activities backed by local repositories. Keep sync as an explicit background/flush layer, not a UI dependency. Add native render caches for official tickets and result-share images, keyed by stable data fingerprints, so WhatsApp/print/save reuse existing bitmaps instead of regenerating on every click or recomposition.

**Tech Stack:** Kotlin, Android SDK 26+, Jetpack Compose, SharedPreferences/local repositories already present, existing Supabase/native sync stores as optional sync, JUnit tests, Gradle.

---

## Decision

This plan removes WebView from normal app operation.

Do this in phases. Do not delete legacy files in Task 1; first make native the only route used by login/shell and prove cashier/admin flows pass. Delete WebView code and web assets only after native replacement tests pass.

---

## File Map

- Modify `app/src/main/java/com/lotterynet/pro/MainActivity.kt`: stop being the main route for active users; later reduce or remove WebView.
- Modify `app/src/main/AndroidManifest.xml`: set native login/shell as launcher flow and remove WebView-only activity exposure when safe.
- Modify `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`: route all roles to native screens only.
- Modify `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`: native role-based home, no legacy target fallback.
- Modify `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`: remove WebView compatibility navigation paths.
- Modify `app/src/main/java/com/lotterynet/pro/core/legacy/*`: isolate as migration-only, then remove after verification.
- Modify `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`: source of truth for tickets and cached ticket render metadata.
- Modify `app/src/main/java/com/lotterynet/pro/core/storage/LocalResultsRepository.kt`: source of truth for results and cached result render metadata.
- Create `app/src/main/java/com/lotterynet/pro/core/render/RenderCacheKeys.kt`: stable fingerprints for ticket/result rendering.
- Create `app/src/main/java/com/lotterynet/pro/core/render/LocalRenderCacheRepository.kt`: stores cached PNG files in `cacheDir` or app files dir with metadata.
- Modify `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`: read/write cached render files and only render when cache misses.
- Modify `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`: use render cache for ticket WhatsApp/share/save preview.
- Modify `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`: after ticket creation, pre-render or cache official ticket once.
- Modify `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`: use result render cache for WhatsApp shared pages.
- Test `app/src/test/java/com/lotterynet/pro/core/render/RenderCacheKeysTest.kt`.
- Test `app/src/test/java/com/lotterynet/pro/core/render/LocalRenderCacheRepositoryContractTest.kt`.
- Test `app/src/test/java/com/lotterynet/pro/ui/navigation/NativeRoutingContractsTest.kt`.
- Update existing tests in `TicketOfficialContractsTest.kt`, `ResultsActivityContractsTest.kt`, and sync tests.

---

## Task 1: Define Native-Only Routing Contract

**Files:**
- Create: `app/src/test/java/com/lotterynet/pro/ui/navigation/NativeRoutingContractsTest.kt`
- Create or modify: `app/src/main/java/com/lotterynet/pro/ui/navigation/NativeRouting.kt`

- [ ] **Step 1: Write failing tests**

Create `NativeRoutingContractsTest.kt`:

```kotlin
package com.lotterynet.pro.ui.navigation

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeRoutingContractsTest {
    @Test
    fun `cashier route is native sales and never webview`() {
        val route = resolveNativeHomeRoute(UserRole.CASHIER)

        assertEquals(NativeHomeRoute.SALES, route)
        assertFalse(route.usesWebView)
    }

    @Test
    fun `admin and master routes are native dashboards`() {
        assertEquals(NativeHomeRoute.ADMIN_DASHBOARD, resolveNativeHomeRoute(UserRole.ADMIN))
        assertEquals(NativeHomeRoute.MASTER_DASHBOARD, resolveNativeHomeRoute(UserRole.MASTER))
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.navigation.NativeRoutingContractsTest"
```

Expected: compile failure because `NativeHomeRoute` and `resolveNativeHomeRoute` do not exist.

- [ ] **Step 3: Implement native route contract**

Create `NativeRouting.kt`:

```kotlin
package com.lotterynet.pro.ui.navigation

import com.lotterynet.pro.core.model.UserRole

enum class NativeHomeRoute(val usesWebView: Boolean) {
    SALES(false),
    ADMIN_DASHBOARD(false),
    MASTER_DASHBOARD(false),
    LOGIN(false),
}

fun resolveNativeHomeRoute(role: UserRole): NativeHomeRoute {
    return when (role) {
        UserRole.CASHIER -> NativeHomeRoute.SALES
        UserRole.ADMIN -> NativeHomeRoute.ADMIN_DASHBOARD
        UserRole.MASTER -> NativeHomeRoute.MASTER_DASHBOARD
        else -> NativeHomeRoute.LOGIN
    }
}
```

- [ ] **Step 4: Run test and verify pass**

Run same test command.

Expected: PASS.

---

## Task 2: Route Login and Launcher Away From WebView

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/lotterynet/pro/ui/navigation/NativeRoutingContractsTest.kt`

- [ ] **Step 1: Add failing intent contract**

Extend `NativeRoutingContractsTest.kt`:

```kotlin
@Test
fun `native route resolves activity class names without MainActivity webview`() {
    assertEquals(
        "com.lotterynet.pro.ui.sales.SalesActivity",
        resolveNativeHomeActivityClassName(NativeHomeRoute.SALES),
    )
    assertEquals(
        "com.lotterynet.pro.ui.admin.AdminDashboardActivity",
        resolveNativeHomeActivityClassName(NativeHomeRoute.ADMIN_DASHBOARD),
    )
    assertEquals(
        "com.lotterynet.pro.ui.master.MasterDashboardActivity",
        resolveNativeHomeActivityClassName(NativeHomeRoute.MASTER_DASHBOARD),
    )
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.navigation.NativeRoutingContractsTest"
```

Expected: missing `resolveNativeHomeActivityClassName`.

- [ ] **Step 3: Implement class-name resolver**

In `NativeRouting.kt`:

```kotlin
fun resolveNativeHomeActivityClassName(route: NativeHomeRoute): String {
    return when (route) {
        NativeHomeRoute.SALES -> "com.lotterynet.pro.ui.sales.SalesActivity"
        NativeHomeRoute.ADMIN_DASHBOARD -> "com.lotterynet.pro.ui.admin.AdminDashboardActivity"
        NativeHomeRoute.MASTER_DASHBOARD -> "com.lotterynet.pro.ui.master.MasterDashboardActivity"
        NativeHomeRoute.LOGIN -> "com.lotterynet.pro.ui.login.LoginActivity"
    }
}
```

- [ ] **Step 4: Wire LoginActivity**

In `LoginActivity`, after successful login/session, use `resolveNativeHomeRoute(session.role)` and start the matching native activity. Do not start `MainActivity` for normal roles.

- [ ] **Step 5: Turn MainActivity into migration fallback only**

In `MainActivity.onCreate`, if an active native session exists, immediately route to the native home and `finish()` before `initWebView()`.

Use:

```kotlin
val session = LocalSessionRepository(this).getActiveSession()
if (session != null) {
    val route = resolveNativeHomeRoute(session.role)
    startActivity(nativeHomeIntent(this, route))
    finish()
    return
}
```

- [ ] **Step 6: Run tests and assemble**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.navigation.NativeRoutingContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected: PASS and build success.

---

## Task 3: Add Stable Render Fingerprints

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/render/RenderCacheKeys.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/render/RenderCacheKeysTest.kt`

- [ ] **Step 1: Write failing tests**

Create `RenderCacheKeysTest.kt`:

```kotlin
package com.lotterynet.pro.core.render

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RenderCacheKeysTest {
    @Test
    fun `ticket render key is stable for same ticket content`() {
        val ticket = TicketRecord(
            id = "T-1",
            serial = "S-1",
            status = "active",
            total = 50.0,
            plays = listOf(PlayItem(number = "12", playType = "Q", amount = 50.0)),
        )

        assertEquals(
            ticketRenderCacheKey(ticket, bancaName = "Banca", logoUri = "logo"),
            ticketRenderCacheKey(ticket, bancaName = "Banca", logoUri = "logo"),
        )
    }

    @Test
    fun `ticket render key changes when status changes`() {
        val active = TicketRecord(id = "T-1", status = "active")
        val voided = TicketRecord(id = "T-1", status = "voided")

        assertNotEquals(
            ticketRenderCacheKey(active, bancaName = "Banca", logoUri = ""),
            ticketRenderCacheKey(voided, bancaName = "Banca", logoUri = ""),
        )
    }

    @Test
    fun `results render key changes when numbers change`() {
        val a = listOf(ResultShareRow("Anguila", "01", "02", "03"))
        val b = listOf(ResultShareRow("Anguila", "01", "02", "04"))

        assertNotEquals(
            resultsRenderCacheKey("25-04-2026", a, pageIndex = 0),
            resultsRenderCacheKey("25-04-2026", b, pageIndex = 0),
        )
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.render.RenderCacheKeysTest"
```

Expected: compile failure.

- [ ] **Step 3: Implement cache keys**

Create `RenderCacheKeys.kt`:

```kotlin
package com.lotterynet.pro.core.render

import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.TicketRecord
import java.security.MessageDigest

fun ticketRenderCacheKey(ticket: TicketRecord, bancaName: String, logoUri: String): String {
    val raw = buildString {
        append("ticket|")
        append(ticket.id).append('|')
        append(ticket.serial.orEmpty()).append('|')
        append(ticket.securityCode.orEmpty()).append('|')
        append(ticket.status).append('|')
        append(ticket.total).append('|')
        append(ticket.totalPrize).append('|')
        append(bancaName).append('|')
        append(logoUri).append('|')
        ticket.plays.forEach { play ->
            append(play.lotteryId.orEmpty()).append(':')
            append(play.lotteryName.orEmpty()).append(':')
            append(play.playType).append(':')
            append(play.number).append(':')
            append(play.amount).append(';')
        }
    }
    return "ticket-${sha256Short(raw)}"
}

fun resultsRenderCacheKey(date: String, rows: List<ResultShareRow>, pageIndex: Int): String {
    val raw = buildString {
        append("results|").append(date).append('|').append(pageIndex).append('|')
        rows.forEach { row ->
            append(row.displayName).append(':')
            append(row.drawTimeLabel.orEmpty()).append(':')
            append(row.logoAssetPath.orEmpty()).append(':')
            append(row.first).append(',')
            append(row.second).append(',')
            append(row.third).append(',')
            append(row.pick3.orEmpty()).append(',')
            append(row.pick4.orEmpty()).append(';')
        }
    }
    return "results-${sha256Short(raw)}"
}

private fun sha256Short(raw: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
}
```

- [ ] **Step 4: Run tests**

Run the same test command.

Expected: PASS.

---

## Task 4: Add Local Render Cache Repository

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/render/LocalRenderCacheRepository.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/render/LocalRenderCacheRepositoryContractTest.kt`

- [ ] **Step 1: Write pure path contract test**

Because unit tests do not have Android `Context`, test the path sanitizer separately:

```kotlin
package com.lotterynet.pro.core.render

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRenderCacheRepositoryContractTest {
    @Test
    fun `render cache filename keeps only safe characters`() {
        assertEquals("ticket-abc123.png", renderCacheFileName("ticket:abc/123"))
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.render.LocalRenderCacheRepositoryContractTest"
```

- [ ] **Step 3: Implement repository**

Create `LocalRenderCacheRepository.kt`:

```kotlin
package com.lotterynet.pro.core.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

fun renderCacheFileName(key: String): String {
    val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "")
    return "$safe.png"
}

class LocalRenderCacheRepository(
    private val context: Context,
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "render_cache").apply { mkdirs() }

    fun getUriIfPresent(key: String): Uri? {
        val file = File(cacheDir, renderCacheFileName(key))
        if (!file.exists() || file.length() <= 0L) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveBitmap(key: String, bitmap: Bitmap): Uri? {
        return runCatching {
            val file = File(cacheDir, renderCacheFileName(key))
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun clear(key: String) {
        File(cacheDir, renderCacheFileName(key)).delete()
    }
}
```

- [ ] **Step 4: Run test**

Run same test command.

Expected: PASS.

---

## Task 5: Cache Official Ticket Rendering

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketOfficialContractsTest.kt`

- [ ] **Step 1: Add contract test**

In `TicketOfficialContractsTest.kt`:

```kotlin
@Test
fun `official ticket share uses render cache when content is unchanged`() {
    val ticket = TicketRecord(id = "T-1", status = "active", total = 10.0)

    assertEquals(
        ticketRenderCacheKey(ticket, "Banca", ""),
        resolveOfficialTicketRenderCacheKey(ticket, "Banca", ""),
    )
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest"
```

Expected: missing `resolveOfficialTicketRenderCacheKey`.

- [ ] **Step 3: Implement helper**

In `TicketOfficialActivity.kt`:

```kotlin
internal fun resolveOfficialTicketRenderCacheKey(
    ticket: TicketRecord,
    bancaName: String,
    logoUri: String,
): String = ticketRenderCacheKey(ticket, bancaName, logoUri)
```

- [ ] **Step 4: Use cache for share/save**

In `TicketOfficialActivity`, before rendering:

```kotlin
val renderCache = remember { LocalRenderCacheRepository(localContext) }
val renderKey = resolveOfficialTicketRenderCacheKey(currentTicket, bancaName, bancaLogoUri)
```

For WhatsApp/share/save:

1. Try `renderCache.getUriIfPresent(renderKey)`.
2. If present, share that Uri with `ACTION_SEND`.
3. If missing, render bitmap once, `renderCache.saveBitmap(renderKey, bitmap)`, then share.

- [ ] **Step 5: Invalidate on status changes**

Because the key includes `ticket.status`, void/paid/winner automatically use a different cache key. Do not manually clear unless storage grows too much.

- [ ] **Step 6: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest"
```

Expected: PASS.

---

## Task 6: Cache Result Share Images

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`

- [ ] **Step 1: Add cache key test**

In `ResultsActivityContractsTest.kt`:

```kotlin
@Test
fun `result whatsapp page cache key includes page index`() {
    val rows = listOf(ResultShareRow("Anguila", "01", "02", "03"))

    assertNotEquals(
        resultsRenderCacheKey("25-04-2026", rows, pageIndex = 0),
        resultsRenderCacheKey("25-04-2026", rows, pageIndex = 1),
    )
}
```

- [ ] **Step 2: Run and verify failure if imports/helper missing**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"
```

- [ ] **Step 3: Implement cached result page sharing**

In `captureResultsListBitmaps`, replace unconditional rendering with:

1. Convert `ResultsBoardRow` to `ResultShareRow`.
2. Build key using `resultsRenderCacheKey(selectedDate, shareRows, pageIndex)`.
3. Check `LocalRenderCacheRepository.getUriIfPresent(key)` for share path.
4. If missing, render bitmap once and save.

If current `NativeBitmapExport.shareBitmaps` only accepts `Bitmap`, add a sibling method:

```kotlin
fun shareImageUris(
    context: Context,
    uris: List<Uri>,
    title: String,
    whatsappOnly: Boolean,
): ExportActionResult
```

Use that for cached result sharing to avoid decoding PNGs back into memory.

- [ ] **Step 4: Keep no-cut behavior**

Do not return visible-screen capture. Continue offscreen full-page capture per 7-8 rows.

- [ ] **Step 5: Run result tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"
```

Expected: PASS.

---

## Task 7: Remove Normal WebView Navigation

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/legacy/LegacyCompatNavigation.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/navigation/NativeRoutingContractsTest.kt`

- [ ] **Step 1: Add no-WebView contract**

In `NativeRoutingContractsTest.kt`:

```kotlin
@Test
fun `normal native tabs never resolve to legacy webview`() {
    val routes = listOf(
        NativeHomeRoute.SALES,
        NativeHomeRoute.ADMIN_DASHBOARD,
        NativeHomeRoute.MASTER_DASHBOARD,
    )

    assertTrue(routes.none { it.usesWebView })
}
```

- [ ] **Step 2: Replace `openBottomTab` WebView fallbacks**

In `NativeChrome.kt`, ensure bottom tabs resolve to native activities:

- Sales -> `SalesActivity`
- Tickets -> `TicketLookupActivity`
- Results -> `ResultsActivity`
- Finance -> `FinanceActivity`
- Menu -> `ShellActivity`

Remove normal calls that route admin/cashier to `MainActivity`.

- [ ] **Step 3: Keep MainActivity only for explicit migration**

If any legacy action still needs WebView during migration, require an explicit internal flag:

```kotlin
const val EXTRA_ALLOW_LEGACY_WEBVIEW = "allow_legacy_webview"
```

If the flag is absent, redirect to native route and finish.

- [ ] **Step 4: Run navigation tests and build**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.navigation.NativeRoutingContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

---

## Task 8: Delete Legacy Web Assets After Native Parity

**Files:**
- Delete only after Tasks 1-7 pass:
  - `app/src/main/assets/index.html`
  - `app/src/main/assets/supabase-js-v2.min.js`
  - Web-only vendor assets under `app/src/main/assets/vendor/` if unused by native screens.
- Keep:
  - `app/src/main/assets/lot-logos/`
  - `app/src/main/assets/pos-sfx/`
  - provider logos if native recharge screens still use them.

- [ ] **Step 1: Search for asset usage**

Run:

```powershell
Get-ChildItem -Path app\src\main -Recurse -File | Select-String -Pattern 'index.html|supabase-js-v2.min.js|vendor/phosphor|qrcode.min.js'
```

Expected: only legacy files reference them.

- [ ] **Step 2: Delete unused WebView assets**

Use `Remove-Item` only for confirmed web-only files.

- [ ] **Step 3: Assemble**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected: build success.

---

## Task 9: Local-First Data Rules

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/storage/LocalResultsRepository.kt`
- Modify: sync coordinators under `app/src/main/java/com/lotterynet/pro/core/sync/`
- Test: existing sync/storage tests.

- [ ] **Step 1: Enforce local read path**

All UI screens must read from local repositories first:

- tickets: `LocalSalesRepository`
- results: `LocalResultsRepository`
- users/session: local user/session repositories
- printer prefs: local printer repository

Remote sync must update local stores, not feed UI directly.

- [ ] **Step 2: Add contract tests for local-first results**

Add a test proving cached/local results are returned without network dependency. Use existing `ResultsSupabaseStoreTest` only for remote store behavior; UI contract should use local repository.

- [ ] **Step 3: Add explicit sync actions**

In cashier profile, sync should happen:

- after ticket save
- when user taps refresh results
- when app resumes, throttled

It should not block opening sales screen.

- [ ] **Step 4: Run all tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

---

## Task 10: POS QA Checklist

**Files:**
- Create: `docs/qa/native-local-pos-checklist.md`

- [ ] **Step 1: Create checklist**

```markdown
# Native Local POS Checklist

Target:
- Android 8
- 1 GB RAM
- Wireless POS
- Thermal printer paired
- WhatsApp installed

Required:
1. Launch app and login as cashier.
2. Confirm app does not open WebView screen.
3. Confirm first cashier screen is native sales.
4. Create ticket with one lottery.
5. Reopen official ticket; confirm it loads without freeze.
6. Share official ticket by WhatsApp twice; second share should be faster because cached.
7. Open results; view local cached results.
8. Share multiple results by WhatsApp twice; second share should reuse cached PNG.
9. Turn internet off and repeat ticket lookup/results view using local data.
10. Delete/anular ticket and confirm it does not return after sync.

Pass:
- No WebView visible.
- No freeze over 2 seconds on second share.
- Tickets/results remain available offline from local storage.
- No admin/master screen appears for cashier.
```

- [ ] **Step 2: Build APK**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

- [ ] **Step 3: Test on actual POS**

Install the APK and run the checklist.

---

## Final Verification

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected:

- All tests pass.
- Debug APK builds.
- Normal login routes to native screens.
- WebView is not used in cashier/admin/master normal operation.
- Ticket/result sharing uses local cached render files when data has not changed.

