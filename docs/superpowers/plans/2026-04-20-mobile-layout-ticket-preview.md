# Mobile Layout And Ticket Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Terminar la adaptación móvil para que login, venta y ticket no se salgan de pantalla, respeten notch/system insets, y el flujo de impresión abra una previa clara con salida térmica o WhatsApp.

**Architecture:** Se mantiene la app Compose actual y se refuerza con contratos pequeños testeables para layout compacto y orden de acciones. Los cambios visuales se concentran en `LoginActivity.kt`, `SalesActivity.kt`, `TicketOfficialActivity.kt`, `PrinterActivity.kt` y, si se detectan huecos, en `NativeChrome.kt` para insets compartidos.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle, JUnit4, Android AppCompat.

---

### Task 1: Consolidar Inset Handling En Pantallas Compose

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `tight sales keypad layout removes badges and shrinks key spacing`() {
    val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.POS_TIGHT)

    assertFalse(contract.showStatsBadges)
    assertEquals(2, contract.keySpacingDp)
    assertEquals(26, contract.keyHeightDp)
    assertTrue(contract.totalAboveKeypad)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"`
Expected: FAIL if the keypad contract or imports are missing.

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal data class VentaKeypadLayoutContract(
    val showStatsBadges: Boolean,
    val keySpacingDp: Int,
    val keyHeightDp: Int,
    val totalAboveKeypad: Boolean,
)

internal fun resolveVentaKeypadLayout(windowMode: LotteryNetWindowMode): VentaKeypadLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> VentaKeypadLayoutContract(false, 2, 26, true)
        else -> VentaKeypadLayoutContract(true, 3, 36, true)
    }
}
```

- [ ] **Step 4: Apply inset handling to each screen**

```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .background(visual.colors.background)
)
```

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
)
```

- [ ] **Step 5: Run focused verification**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"`
Expected: PASS

- [ ] **Step 6: Run compile verification**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt
git commit -m "fix: respect system insets on compose screens"
```

### Task 2: Tighten Venta Composer For Small Devices

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `regular sales keypad layout keeps badges for larger screens`() {
    val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.POS)

    assertTrue(contract.showStatsBadges)
    assertEquals(3, contract.keySpacingDp)
    assertEquals(36, contract.keyHeightDp)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"`
Expected: FAIL if the non-tight branch is wrong.

- [ ] **Step 3: Write minimal implementation**

```kotlin
if (keypadLayout.totalAboveKeypad) {
    CompactTotalBar(
        total = total,
        label = "Total jugada",
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
```

```kotlin
val label = when (key) {
    "ENTNUM" -> if (keypadLayout.showStatsBadges) "ENT\nNUM" else "JUG"
    "PRINT" -> if (keypadLayout.showStatsBadges) "PRINT" else "TKT"
    "OK" -> if (keypadLayout.showStatsBadges) "OK\nENTER" else "OK"
    else -> key
}
```

- [ ] **Step 4: Reduce dead space in composer**

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
)
```

```kotlin
.height(if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 30.dp else 42.dp)
```

- [ ] **Step 5: Run verification**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest" && .\gradlew.bat assembleDebug`
Expected: tests PASS and BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt
git commit -m "fix: compact sales composer for tight screens"
```

### Task 3: Turn Ticket Screen Into Clear Preview Hub

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketOfficialContractsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `preview actions prioritize thermal and whatsapp before secondary actions`() {
    val actions = resolveTicketPreviewActions(
        showPay = false,
        showVoid = false,
        showDuplicate = true,
    )

    assertEquals(TicketPreviewAction.THERMAL, actions[0])
    assertEquals(TicketPreviewAction.WHATSAPP, actions[1])
    assertTrue(actions.contains(TicketPreviewAction.DUPLICATE))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest"`
Expected: FAIL if action ordering helper does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal enum class TicketPreviewAction {
    THERMAL,
    WHATSAPP,
    SHARE,
    SAVE,
    DUPLICATE,
    PAY,
    VOID,
}

internal fun resolveTicketPreviewActions(
    showPay: Boolean,
    showVoid: Boolean,
    showDuplicate: Boolean,
): List<TicketPreviewAction> {
    return buildList {
        add(TicketPreviewAction.THERMAL)
        add(TicketPreviewAction.WHATSAPP)
        add(TicketPreviewAction.SHARE)
        add(TicketPreviewAction.SAVE)
        if (showDuplicate) add(TicketPreviewAction.DUPLICATE)
        if (showPay) add(TicketPreviewAction.PAY)
        if (showVoid) add(TicketPreviewAction.VOID)
    }
}
```

- [ ] **Step 4: Replace mixed quick actions with preview-first actions**

```kotlin
TicketPreviewAction.THERMAL -> TicketQuickActionSpec("Térmica", Icons.Rounded.Print, ActionTone.Warning) {
    onOpenThermal(currentTicket)
    actionMessage = "Abriendo ticket térmico"
}

TicketPreviewAction.WHATSAPP -> TicketQuickActionSpec("WhatsApp", Icons.Rounded.Whatsapp, ActionTone.Success) {
    actionMessage = onShare(currentTicket, true).message
}
```

- [ ] **Step 5: Keep ticket screen compact on small devices**

```kotlin
CompactAdaptiveGrid(
    itemCount = actionSlots.size,
    columns = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 4,
)
```

- [ ] **Step 6: Run verification**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest" && .\gradlew.bat assembleDebug`
Expected: tests PASS and BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt app/src/test/java/com/lotterynet/pro/ui/tickets/TicketOfficialContractsTest.kt
git commit -m "feat: make ticket preview lead to thermal or whatsapp"
```

### Task 4: Validate Remaining Screens That Likely Still Overflow

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Inspect screen roots for missing insets**

Run: `Select-String -Path .\app\src\main\java\com\lotterynet\pro\ui\**\*.kt -Pattern "fillMaxSize\\(|statusBarsPadding|navigationBarsPadding|verticalScroll" `
Expected: identify screens that still use full-screen roots without system inset padding.

- [ ] **Step 2: Add the smallest possible root fix per screen**

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
)
```

```kotlin
Surface(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
)
```

- [ ] **Step 3: Re-run compile verification**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt
git commit -m "fix: apply notch-safe layout to remaining mobile screens"
```

### Task 5: Device Validation And Release Candidate

**Files:**
- Modify: `docs/superpowers/plans/2026-04-20-mobile-layout-ticket-preview.md`
- Modify: `app/build/outputs/apk/debug/`

- [ ] **Step 1: Build fresh debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL and fresh APK in `app/build/outputs/apk/debug/`

- [ ] **Step 2: Validate manually on Samsung A15**

Checklist:
- Login title and fields do not hide under notch
- Venta shows `Total jugada` directly above keypad
- Keypad buttons look tighter than before
- PRINT path opens ticket preview, not direct mixed action clutter
- Ticket preview offers `Térmica` and `WhatsApp` as first actions

- [ ] **Step 3: Validate manually on POS 5.5**

Checklist:
- Bottom nav remains reachable
- Composer fits without clipping
- Keypad rows are readable and no large vertical gaps remain
- Thermal preview opens and returns cleanly

- [ ] **Step 4: If manual validation passes, build release**

Run: `.\gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL and signed APK in `app/build/outputs/apk/release/`

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-04-20-mobile-layout-ticket-preview.md
git commit -m "docs: record mobile layout validation plan"
```
