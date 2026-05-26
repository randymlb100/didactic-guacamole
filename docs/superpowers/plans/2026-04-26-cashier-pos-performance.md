# Cashier POS Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the cashier profile feel light and stable on Android 8 wireless POS devices with 1 GB RAM.

**Architecture:** Keep one APK for now. Add a cashier low-memory runtime profile that avoids loading admin/master surfaces, avoids legacy WebView work for cashier, reduces bitmap/export memory spikes, and disables expensive visual work on low-RAM devices. Only consider a second APK after measurements prove one APK cannot meet the POS target.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose, existing local repositories, existing WebView bridge, JUnit unit tests, Gradle debug build.

---

## File Map

- Modify `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`: route cashier sessions directly to a lightweight cashier home instead of general shell/menu when possible.
- Modify `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`: hide/defer admin/master cards for cashier, remove unnecessary background loading, and add POS-lite mode.
- Modify `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`: make bottom navigation/menu contract minimal for cashier and prevent admin route exposure.
- Modify `app/src/main/java/com/lotterynet/pro/MainActivity.kt`: avoid initializing legacy WebView for cashier unless explicitly needed.
- Modify `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`: add bitmap dimension guards and recycle/avoid repeated large allocations during share/save.
- Modify `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`: keep result share pages bounded for low-RAM POS and avoid large offscreen captures on the main thread where possible.
- Create `app/src/main/java/com/lotterynet/pro/core/performance/RuntimePerformanceProfile.kt`: central contract for low-RAM and cashier-lite behavior.
- Test `app/src/test/java/com/lotterynet/pro/core/performance/RuntimePerformanceProfileTest.kt`: unit tests for profile decisions.
- Test existing `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`: add/keep capture size contract tests.
- Test existing `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketOfficialContractsTest.kt`: add/keep bitmap reuse contract tests.

---

## Task 1: Add Runtime Performance Profile

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/performance/RuntimePerformanceProfile.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/performance/RuntimePerformanceProfileTest.kt`

- [ ] **Step 1: Write failing tests**

Create `RuntimePerformanceProfileTest.kt`:

```kotlin
package com.lotterynet.pro.core.performance

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePerformanceProfileTest {
    @Test
    fun `cashier on one gig android 8 uses lite profile`() {
        val profile = resolveRuntimePerformanceProfile(
            role = UserRole.CASHIER,
            sdkInt = 26,
            memoryClassMb = 128,
            isLowRamDevice = true,
        )

        assertTrue(profile.cashierLite)
        assertTrue(profile.disableHeavyAnimations)
        assertTrue(profile.deferAdminSurfaces)
        assertTrue(profile.limitShareBitmaps)
        assertFalse(profile.preloadLegacyWebView)
    }

    @Test
    fun `admin keeps full profile unless device is low ram`() {
        val profile = resolveRuntimePerformanceProfile(
            role = UserRole.ADMIN,
            sdkInt = 33,
            memoryClassMb = 256,
            isLowRamDevice = false,
        )

        assertFalse(profile.cashierLite)
        assertFalse(profile.deferAdminSurfaces)
        assertTrue(profile.preloadLegacyWebView)
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.performance.RuntimePerformanceProfileTest"
```

Expected: compile failure because `resolveRuntimePerformanceProfile` does not exist.

- [ ] **Step 3: Implement profile**

Create `RuntimePerformanceProfile.kt`:

```kotlin
package com.lotterynet.pro.core.performance

import com.lotterynet.pro.core.model.UserRole

data class RuntimePerformanceProfile(
    val cashierLite: Boolean,
    val disableHeavyAnimations: Boolean,
    val deferAdminSurfaces: Boolean,
    val limitShareBitmaps: Boolean,
    val preloadLegacyWebView: Boolean,
)

fun resolveRuntimePerformanceProfile(
    role: UserRole,
    sdkInt: Int,
    memoryClassMb: Int,
    isLowRamDevice: Boolean,
): RuntimePerformanceProfile {
    val constrainedDevice = isLowRamDevice || sdkInt <= 26 || memoryClassMb <= 128
    val cashierLite = role == UserRole.CASHIER && constrainedDevice
    return RuntimePerformanceProfile(
        cashierLite = cashierLite,
        disableHeavyAnimations = constrainedDevice,
        deferAdminSurfaces = cashierLite,
        limitShareBitmaps = constrainedDevice,
        preloadLegacyWebView = !cashierLite,
    )
}
```

- [ ] **Step 4: Run tests and verify pass**

Run same test command.

Expected: PASS.

---

## Task 2: Route Cashier to a Lightweight Start Surface

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Test: add to existing shell/login contract tests if present; otherwise create `app/src/test/java/com/lotterynet/pro/ui/shell/ShellPerformanceContractsTest.kt`

- [ ] **Step 1: Write failing route contract**

Create or update a test:

```kotlin
package com.lotterynet.pro.ui.shell

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShellPerformanceContractsTest {
    @Test
    fun `cashier lite shell starts on sales and excludes admin destinations`() {
        val contract = resolveShellPerformanceContract(
            role = UserRole.CASHIER,
            cashierLite = true,
        )

        assertEquals("sales", contract.startDestination)
        assertFalse("admin_dashboard" in contract.destinations)
        assertFalse("master_dashboard" in contract.destinations)
        assertFalse(contract.preloadReports)
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.shell.ShellPerformanceContractsTest"
```

Expected: compile failure for missing contract.

- [ ] **Step 3: Implement shell contract**

Add near Shell helpers:

```kotlin
internal data class ShellPerformanceContract(
    val startDestination: String,
    val destinations: List<String>,
    val preloadReports: Boolean,
)

internal fun resolveShellPerformanceContract(
    role: UserRole,
    cashierLite: Boolean,
): ShellPerformanceContract {
    return if (role == UserRole.CASHIER && cashierLite) {
        ShellPerformanceContract(
            startDestination = "sales",
            destinations = listOf("sales", "tickets", "results", "profile"),
            preloadReports = false,
        )
    } else {
        ShellPerformanceContract(
            startDestination = "menu",
            destinations = listOf("sales", "tickets", "results", "finance", "admin_dashboard", "master_dashboard", "profile"),
            preloadReports = true,
        )
    }
}
```

- [ ] **Step 4: Wire ShellActivity to use the contract**

In `ShellActivity`, use the active session role plus `RuntimePerformanceProfile` to:

```kotlin
val profile = resolveRuntimePerformanceProfile(
    role = session.role,
    sdkInt = android.os.Build.VERSION.SDK_INT,
    memoryClassMb = (getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 128),
    isLowRamDevice = getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice ?: false,
)
val shellContract = resolveShellPerformanceContract(session.role, profile.cashierLite)
```

Then only build menu sections from `shellContract.destinations`.

- [ ] **Step 5: Run shell tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.shell.ShellPerformanceContractsTest"
```

Expected: PASS.

---

## Task 3: Avoid Legacy WebView Load for Cashier Lite

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/MainActivityContractsTest.kt` or a new `LegacyWebViewPerformanceContractsTest.kt`

- [ ] **Step 1: Write failing WebView contract**

```kotlin
package com.lotterynet.pro

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWebViewPerformanceContractsTest {
    @Test
    fun `cashier lite does not preload legacy webview`() {
        assertFalse(shouldPreloadLegacyWebView(UserRole.CASHIER, cashierLite = true))
    }

    @Test
    fun `admin can preload legacy webview`() {
        assertTrue(shouldPreloadLegacyWebView(UserRole.ADMIN, cashierLite = false))
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.LegacyWebViewPerformanceContractsTest"
```

Expected: missing `shouldPreloadLegacyWebView`.

- [ ] **Step 3: Implement decision helper**

Add:

```kotlin
internal fun shouldPreloadLegacyWebView(role: UserRole, cashierLite: Boolean): Boolean {
    return !(role == UserRole.CASHIER && cashierLite)
}
```

- [ ] **Step 4: Guard `initWebView()`**

Before `initWebView()` in `MainActivity.onCreate`, resolve the active session and profile. If `shouldPreloadLegacyWebView(...)` is false, route to `ShellActivity` or native cashier entry and call `finish()` before constructing WebView.

- [ ] **Step 5: Run tests and smoke build**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.LegacyWebViewPerformanceContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected: PASS and build success.

---

## Task 4: Disable Heavy Animations for Low-RAM POS

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify specific screens only where animation/search finds `animate`, `rememberInfiniteTransition`, or heavy visual effects.
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/PerformanceVisualContractsTest.kt`

- [ ] **Step 1: Write failing visual contract**

```kotlin
package com.lotterynet.pro.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceVisualContractsTest {
    @Test
    fun `low ram profile disables decorative animations`() {
        assertFalse(shouldEnableDecorativeAnimations(disableHeavyAnimations = true))
        assertTrue(shouldEnableDecorativeAnimations(disableHeavyAnimations = false))
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.common.PerformanceVisualContractsTest"
```

- [ ] **Step 3: Implement helper**

```kotlin
internal fun shouldEnableDecorativeAnimations(disableHeavyAnimations: Boolean): Boolean {
    return !disableHeavyAnimations
}
```

- [ ] **Step 4: Gate decorative animations**

For each animation used only for visual polish, wrap it:

```kotlin
if (shouldEnableDecorativeAnimations(profile.disableHeavyAnimations)) {
    // existing animation
} else {
    // static color/position
}
```

Do not remove functional loading indicators unless replaced with a static progress message.

- [ ] **Step 5: Run tests and assemble**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.common.PerformanceVisualContractsTest"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

---

## Task 5: Bound Bitmap Export Memory

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Test: existing `ResultsActivityContractsTest.kt` and a new export contract if needed.

- [ ] **Step 1: Add tests for bitmap limits**

In `ResultsActivityContractsTest.kt`, keep:

```kotlin
assertEquals(1600, resultsWhatsAppCaptureCanvasWidthPx(screenWidthPx = 720, density = 2f))
assertEquals(1920, resultsWhatsAppCaptureCanvasWidthPx(screenWidthPx = 1080, density = 3f))
```

Add export helper test:

```kotlin
@Test
fun `cashier lite share uses at most eight result rows per png`() {
    assertEquals(8, resultsWhatsAppCardsPerImage())
}
```

- [ ] **Step 2: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"
```

- [ ] **Step 3: Keep export pages bounded**

Ensure result sharing still chunks at 8 rows and width remains capped at 1920px.

- [ ] **Step 4: Prevent repeated ticket bitmap render**

Keep `TicketOfficialActivity` using:

```kotlin
val bitmap = remember(currentTicket, bancaName, securityCode, bancaLogoUri) {
    NativeBitmapExport.renderOfficialTicketBitmap(...)
}
```

Use that bitmap for WhatsApp/share/save callbacks.

- [ ] **Step 5: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest" --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest"
```

Expected: PASS.

---

## Task 6: Add Manual POS Performance Checklist

**Files:**
- Create: `docs/qa/pos-android8-1gb-performance-checklist.md`

- [ ] **Step 1: Create checklist**

```markdown
# POS Android 8 / 1 GB RAM Performance Checklist

Device target:
- Android 8.x
- 1 GB RAM
- 5.5 inch wireless POS
- Thermal printer paired
- WhatsApp installed

Scenarios:
1. Login as cashier.
2. Confirm first screen opens directly to cashier workflow, not admin/master menu.
3. Make a normal ticket with 1 lottery.
4. Make a ticket with multiple lotteries.
5. Print thermal ticket.
6. Share official ticket by WhatsApp.
7. Open results and share 8 available results by WhatsApp.
8. Return to sales and make another ticket.

Pass criteria:
- App does not freeze longer than 2 seconds after tapping WhatsApp.
- No crash or black screen.
- Shell/menu does not show admin/master surfaces to cashier.
- Memory pressure does not reopen login unexpectedly.
- Ticket list does not show deleted voided tickets again after sync.
```

- [ ] **Step 2: Build debug APK**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

- [ ] **Step 3: Test on actual POS**

Install debug APK on the target device and run the checklist. Record each freeze/crash with step number and whether WhatsApp was already open.

---

## Decision: One App or Two Apps?

Start with one app plus cashier-lite mode.

Choose two app variants only if, after Tasks 1-6, the Android 8 POS still freezes because the installed APK size or class loading remains too heavy. If needed, add Gradle product flavors:

- `cashierPos`: sales, tickets, results, thermal print, WhatsApp share.
- `full`: admin, master, reports, finance, monitors, all exports.

Do not split now. It adds deployment, support, and update complexity before we prove the one-app cashier-lite route is insufficient.

---

## Verification Commands

Run before finishing implementation:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug
```

Expected: all tests pass and debug APK builds.

