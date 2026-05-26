# Non-Sales Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Rediseñar visualmente todas las secciones nativas excepto el flujo visual de Venta, respetando el estado actual del producto y usando la organización compacta aplicada en Monitoreo como patrón base. POS Lite sí incluye Venta porque en el dispositivo POS el flujo principal es vender.

**Architecture:** Mantener Jetpack Compose nativo y la estructura actual de Activities. El rediseño se concentra en contratos visuales testeables, componentes compartidos en `ui/common/NativeChrome.kt`, y cambios de layout por pantalla sin alterar lógica de negocio, datos, permisos, navegación ni la pantalla de venta. `SalesActivity.kt` queda fuera de alcance.

**Tech Stack:** Kotlin, Android Activities, Jetpack Compose Material 3, JUnit, Gradle, `ui-ux-pro-max`, componentes compartidos `CompactPanel`, `MetricStrip`, `ScreenHeaderPanel`, `CompactActionButton`, `CompactStatusBadge`, `CompactToggleSwitch`.

---

## Safety Backup

A local backup was created before executing this plan:

- Folder: `backups/non_sales_visual_redesign_20260523-023538`
- Zip: `backups/non_sales_visual_redesign_20260523-023538.zip`
- Files backed up: 24 Kotlin/doc files in the non-sales redesign scope.

If the redesign is rejected, restore only the affected files from this backup. Do not restore the whole project blindly because other unrelated work may happen after this backup.

## Documentation And Design Rules Reviewed

- `C:\Users\Randy Cordero\.codex\skills\ui-ux-pro-max\SKILL.md`
- `C:\Users\Randy Cordero\.codex\skills\lotterynet-pro\SKILL.md`
- `docs/superpowers/plans/2026-04-18-lotterynet-native-ui-polish.md`
- `docs/superpowers/plans/2026-04-20-mobile-layout-ticket-preview.md`
- `docs/qa/native-local-pos-checklist.md`
- Android Developers: Jetpack Compose adaptive apps, window size classes, and `currentWindowAdaptiveInfo()`
- Android Developers: supporting different display sizes in Compose without duplicating layout files
- Q2i/Q2-style POS reference: 5.5 inch Android handheld POS, 1280x720/HD-class screen, 2GB RAM, 58mm thermal printer
- UI Pro Max query: mobile POS dashboard dense operational layout touch hierarchy
- UI Pro Max query: Jetpack Compose mobile dashboard state animation dense list
- UI Pro Max query: admin panel compact metrics table mobile

## Global Visual Direction

Use the Monitoreo redesign as the standard:

- One operational row per repeated entity instead of stacked cards.
- Inline metrics for scan speed: `Ventas · Beneficio/Pérdida · Pendiente`, adapted per screen.
- One clear status indicator per row. Avoid duplicate status badge + large color block + switch unless each has a separate job.
- Use subtle state backgrounds, not full alert-colored cards.
- Use icon-only affordances for repeated actions when the meaning is standard: arrow for open, sync for refresh, print for print, share for share.
- Keep touch targets at least `44.dp`; preserve at least `8.dp` spacing between adjacent actions.
- Use `animateColorAsState` / `AnimatedVisibility` only for useful state changes: selected, expanded, active, warning, sync.
- No generic dashboard hero sections. POS screens must feel operational, dense, and readable.
- Preserve the current LotteryNet visual language: compact panels, blue action tone, green gain, red loss, amber warning, neutral surface.
- POS Lite must adapt globally through shared viewport/window contracts instead of per-screen manual hacks.
- The Q2i-style target should be treated as a narrow 5.5 inch operational device: high pixel count does not mean there is room for tablet-style cards.
- Prefer single-column dense rows on POS Lite; use two-column grids only when each cell remains readable and touch-safe.

## Explicit Exclusions

Do not edit these files for the non-sales visual redesign except when adding POS Lite selling layout contracts or fixing ticket thermal output:

- `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

Do not change:

- sale flow behavior
- keypad layout
- staged plays
- ticket creation
- limit validation
- print logic from sale

Allowed follow-up changes requested on 2026-05-23:

- POS Lite may explicitly include Venta layout contracts.
- Thermal ticket text may be fixed so printed ticket amounts fit 58mm output and do not expose decimal cents in styled ticket lines.

---

## File Map

Shared design system:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

POS Lite/adaptive viewport:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/PosMode.kt` if the file exists when implementing
- Modify: shared callers only when they consume the new viewport contract
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/PosModeContractsTest.kt`

Login and shell:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/login/LoginUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellMenuContractsTest.kt`

Admin, master, users, monitor:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminCashierDetailActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminConfigContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/users/UserAccountsFormattingTest.kt`

Tickets, results, reports, finance, recargas:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketListSupport.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketDetailActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/LotteryResultsView.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Test: matching existing contracts under `app/src/test/java/com/lotterynet/pro/ui/tickets`, `results`, `recharge`, `report`, and `finance`.

Secondary operational screens:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminAuditActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminAlertsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/printer/PrinterContractsTest.kt`
- Add focused tests to `AdminUiContractsTest.kt` only when no existing audit/alerts test exists.

QA docs:

- Create: `docs/qa/2026-05-23-non-sales-visual-redesign-checklist.md`

---

## Task 1: Add A Shared Non-Sales Visual Contract

**Files:**

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [x] **Step 1: Write the failing visual contract test**

Add to `NativeChromeContractsTest.kt`:

```kotlin
@Test
fun `non sales visual redesign uses dense operational rows and keeps sale excluded`() {
    val contract = resolveNonSalesVisualRedesignContract()

    assertTrue(contract.excludeSales)
    assertTrue(contract.useDenseOperationalRows)
    assertTrue(contract.inlineMetrics)
    assertTrue(contract.singleStatusIndicator)
    assertTrue(contract.minTouchTargetDp >= 44)
    assertTrue(contract.actionSpacingDp >= 8)
    assertTrue(contract.animationDurationMs in 120..220)
    assertFalse(contract.useHeroCards)
}
```

- [x] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.non sales visual redesign uses dense operational rows and keeps sale excluded"
```

Expected: FAIL because `resolveNonSalesVisualRedesignContract` is not defined.

- [x] **Step 3: Add the shared contract**

Add to `NativeChrome.kt` near other visual contracts:

```kotlin
internal data class NonSalesVisualRedesignContract(
    val excludeSales: Boolean,
    val useDenseOperationalRows: Boolean,
    val inlineMetrics: Boolean,
    val singleStatusIndicator: Boolean,
    val minTouchTargetDp: Int,
    val actionSpacingDp: Int,
    val animationDurationMs: Int,
    val useHeroCards: Boolean,
)

internal fun resolveNonSalesVisualRedesignContract(): NonSalesVisualRedesignContract {
    return NonSalesVisualRedesignContract(
        excludeSales = true,
        useDenseOperationalRows = true,
        inlineMetrics = true,
        singleStatusIndicator = true,
        minTouchTargetDp = 44,
        actionSpacingDp = 8,
        animationDurationMs = 160,
        useHeroCards = false,
    )
}
```

- [x] **Step 4: Run the test again**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.non sales visual redesign uses dense operational rows and keeps sale excluded"
```

Expected: PASS.

---

## Task 2: Create Shared Dense Row Components

**Files:**

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [x] **Step 1: Write the failing component contract test**

Add to `NativeChromeContractsTest.kt`:

```kotlin
@Test
fun `dense operational row contract keeps repeated lists compact and readable`() {
    val contract = resolveDenseOperationalRowContract()

    assertEquals(6, contract.verticalPaddingDp)
    assertEquals(8, contract.horizontalPaddingDp)
    assertEquals(44, contract.minHeightDp)
    assertEquals(3, contract.maxInlineMetricCount)
    assertTrue(contract.usesChevronOpenAffordance)
    assertTrue(contract.supportsAnimatedStatusBackground)
}
```

- [x] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.dense operational row contract keeps repeated lists compact and readable"
```

Expected: FAIL because `resolveDenseOperationalRowContract` is not defined.

- [x] **Step 3: Add row contract and reusable composables**

Add to `NativeChrome.kt`:

```kotlin
internal data class DenseOperationalRowContract(
    val verticalPaddingDp: Int,
    val horizontalPaddingDp: Int,
    val minHeightDp: Int,
    val maxInlineMetricCount: Int,
    val usesChevronOpenAffordance: Boolean,
    val supportsAnimatedStatusBackground: Boolean,
)

internal fun resolveDenseOperationalRowContract(): DenseOperationalRowContract {
    return DenseOperationalRowContract(
        verticalPaddingDp = 6,
        horizontalPaddingDp = 8,
        minHeightDp = 44,
        maxInlineMetricCount = 3,
        usesChevronOpenAffordance = true,
        supportsAnimatedStatusBackground = true,
    )
}
```

Then add composables patterned after the current `CashierMonitorDenseCard`: `DenseOperationalMetric`, `DenseOperationalStatusLine`, and `DenseOpenIcon`. Keep them generic and visually aligned with `CompactPanel`.

- [x] **Step 4: Run shared chrome tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest"
```

Expected: PASS.

---

## Task 3: Preserve Venta With A Guard Test

**Files:**

- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [x] **Step 1: Add guard test**

Add:

```kotlin
@Test
fun `non sales redesign scope never includes sales activity`() {
    val excluded = nonSalesVisualRedesignExcludedFiles()

    assertTrue(excluded.contains("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt"))
    assertTrue(excluded.contains("app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt"))
}
```

- [x] **Step 2: Add helper in `NativeChrome.kt`**

```kotlin
internal fun nonSalesVisualRedesignExcludedFiles(): Set<String> {
    return setOf(
        "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt",
        "app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt",
    )
}
```

- [x] **Step 3: Run guard test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.non sales redesign scope never includes sales activity"
```

Expected: PASS.

---

## Task 4: Add POS Lite Adaptive Viewport Contract

**Files:**

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/PosModeContractsTest.kt`

- [x] **Step 1: Write the failing POS Lite viewport test**

Add to `NativeChromeContractsTest.kt`:

```kotlin
@Test
fun `pos lite viewport adapts globally for q2i class handheld screens`() {
    val contract = resolvePosLiteViewportContract(
        widthDp = 360,
        heightDp = 640,
        forcedPosLite = true,
    )

    assertEquals(LotteryNetWindowMode.POS_TIGHT, contract.windowMode)
    assertTrue(contract.singleColumn)
    assertTrue(contract.hideSecondaryCopy)
    assertTrue(contract.useDenseRows)
    assertTrue(contract.collapseSecondaryActions)
    assertTrue(contract.minTouchTargetDp >= 44)
    assertTrue(contract.contentHorizontalPaddingDp <= 10)
}
```

- [x] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.pos lite viewport adapts globally for q2i class handheld screens"
```

Expected: FAIL because `resolvePosLiteViewportContract` is not defined.

- [x] **Step 3: Add the POS Lite viewport contract**

Add to `NativeChrome.kt` near viewport/window contracts:

```kotlin
internal data class PosLiteViewportContract(
    val windowMode: LotteryNetWindowMode,
    val singleColumn: Boolean,
    val hideSecondaryCopy: Boolean,
    val useDenseRows: Boolean,
    val collapseSecondaryActions: Boolean,
    val minTouchTargetDp: Int,
    val contentHorizontalPaddingDp: Int,
)

internal fun resolvePosLiteViewportContract(
    widthDp: Int,
    heightDp: Int,
    forcedPosLite: Boolean,
): PosLiteViewportContract {
    val tight = forcedPosLite || widthDp <= 380 || heightDp <= 680
    return PosLiteViewportContract(
        windowMode = if (tight) LotteryNetWindowMode.POS_TIGHT else LotteryNetWindowMode.POS,
        singleColumn = tight,
        hideSecondaryCopy = tight,
        useDenseRows = true,
        collapseSecondaryActions = tight,
        minTouchTargetDp = 44,
        contentHorizontalPaddingDp = if (tight) 8 else 12,
    )
}
```

- [x] **Step 4: Wire POS Lite through shared visual profile**

Use the shared viewport contract inside the existing `rememberLotteryNetVisualSpec()` / size profile path, not inside every individual screen. The implementation must preserve existing `LotteryNetWindowMode.POS_TIGHT` behavior and make POS Lite force that compact profile even when the device reports enough pixels.

Do not hardcode Q2i by model name. Use viewport and POS Lite mode so similar 5.5 inch Android POS devices benefit.

- [x] **Step 5: Run common UI tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.*"
```

Expected: PASS.

---

## Task 5: Redesign Shell, Login, And Role Menus

**Files:**

- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/login/LoginUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellMenuContractsTest.kt`

- [x] **Step 1: Add tests for compact operational entrance**

Add tests that require:

```kotlin
assertTrue(resolveLoginLayoutContract(LotteryNetWindowMode.POS).compactPanel)
assertTrue(resolveShellMenuLayout(LotteryNetWindowMode.POS).useDenseRows)
assertFalse(resolveShellMenuLayout(LotteryNetWindowMode.POS).showLargeCards)
```

- [x] **Step 2: Implement visual changes**

Use:

- compact login panel
- shorter helper copy
- one primary login action
- dense grouped shell rows by workflow: Operar, Administrar, Revisar, Sistema
- no oversized menu cards
- icons from existing Material icons

- [x] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.login.LoginUiContractsTest" --tests "com.lotterynet.pro.ui.shell.*"
```

Expected: PASS.

---

## Task 6: Redesign Admin, Master, Users, Config, Limits, And Monitor Family

**Files:**

- Modify: `AdminDashboardActivity.kt`
- Modify: `AdminMonitorActivity.kt`
- Modify: `AdminCashierDetailActivity.kt`
- Modify: `AdminConfigActivity.kt`
- Modify: `AdminLimitsActivity.kt`
- Modify: `MasterDashboardActivity.kt`
- Modify: `UserAccountsActivity.kt`
- Test: admin, master, users contract tests listed in the file map.

- [x] **Step 1: Add contracts for each family**

Required checks:

```kotlin
assertTrue(layout.compactSummary)
assertTrue(layout.useCompactRows)
assertFalse(layout.showLargeCards)
```

For screens without these properties, add focused contracts similar to `CashierMonitorCardVisualContract`.

- [x] **Step 2: Apply the Monitoreo pattern**

Use:

- dense rows for cajeros, supervisores, banks, limits, lottery config rows
- inline metrics for totals
- a single status badge
- subtle animated status background
- one clear open affordance
- bulk action bar only when selection exists

- [x] **Step 3: Keep business behavior unchanged**

Do not change:

- user creation
- supervisor assignment
- blocking rules
- limits persistence
- monitor filters
- ticket ownership
- server sync

- [x] **Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.*" --tests "com.lotterynet.pro.ui.master.*" --tests "com.lotterynet.pro.ui.users.*"
```

Expected: PASS.

---

## Task 7: Redesign Tickets And Results Screens

**Files:**

- Modify: `TicketListSupport.kt`
- Modify: `TicketDetailActivity.kt`
- Modify: `TicketLookupActivity.kt`
- Modify: `TicketSummaryActivity.kt`
- Modify: `TicketOfficialActivity.kt`
- Modify: `ResultsActivity.kt`
- Modify: `LotteryResultsView.kt`

- [x] **Step 1: Add tests for dense scan rows**

Add/extend contracts to require:

```kotlin
assertTrue(layout.useCompactRows)
assertTrue(layout.inlinePrimaryNumbers)
assertTrue(layout.minTouchTargetDp >= 44)
```

- [x] **Step 2: Redesign list rows**

Use:

- ticket row: serial, cajero, total, status, open icon
- result row: lottery, draw, numbers, status/timestamp, action icon
- official ticket remains printable/readable; do not reduce printed canvas fidelity

- [x] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.*" --tests "com.lotterynet.pro.ui.results.*"
```

Expected: PASS.

---

## Task 8: Redesign Recargas, Finance, And Operational Reports

**Files:**

- Modify: `RecargasActivity.kt`
- Modify: `FinanceReportsActivity.kt`
- Modify: `OperationalReportActivity.kt`

- [x] **Step 1: Add compact report contracts**

Required behavior:

```kotlin
assertTrue(layout.compactHeader)
assertTrue(layout.useDenseRows)
assertTrue(layout.inlineTotals)
```

- [x] **Step 2: Apply visual organization**

Recargas:

- provider selector as compact segmented/grid control
- form grouped in one clear panel
- history rows dense and scannable

Finance:

- totals top strip
- date/filter controls collapsed when possible
- rows with amount/status/action

Reports:

- summary first
- details in dense rows
- export/share actions in one compact toolbar

- [x] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.recharge.*" --tests "com.lotterynet.pro.ui.finance.*" --tests "com.lotterynet.pro.ui.report.*"
```

Expected: PASS.

---

## Task 9: Redesign Alerts, Audit, Printer, And Secondary Operational Screens

**Files:**

- Modify: `AdminAuditActivity.kt`
- Modify: `AdminAlertsActivity.kt`
- Modify: `PrinterActivity.kt`

- [x] **Step 1: Add/extend contracts**

For printer:

```kotlin
assertTrue(resolvePrinterLayoutContract(LotteryNetWindowMode.POS).compactControls)
```

For alerts/audit, add helper contracts if none exist:

```kotlin
assertTrue(resolveAuditVisualContract().useDenseRows)
assertTrue(resolveAlertsVisualContract().singleStatusIndicator)
```

- [x] **Step 2: Apply visual organization**

Use:

- dense log rows
- status chips
- one compact action row
- no large repeated empty panels
- printer connection state as one clear status line

- [x] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.printer.*" --tests "com.lotterynet.pro.ui.admin.AdminUiContractsTest"
```

Expected: PASS.

---

## Task 10: Add Manual QA Checklist

**Files:**

- Create: `docs/qa/2026-05-23-non-sales-visual-redesign-checklist.md`

- [x] **Step 1: Add checklist**

Create this content:

```markdown
# Non-Sales Visual Redesign QA Checklist

Device target: Android POS around 5.5 inches first, then normal phone/tablet.

Do not test Venta as part of this redesign except to confirm it still opens unchanged.

## Shared Checks

- No horizontal scroll.
- All repeated lists use compact rows, not tall repeated cards.
- Touch targets remain at least 44dp.
- Adjacent actions have visible spacing.
- Status is shown once per row.
- Metrics are readable and not wrapped awkwardly.
- Animations are short and tied to state changes.
- Empty states look intentional, not broken.

## Screens

- Login
- Shell/menu
- Admin dashboard
- Master dashboard
- User accounts
- Admin monitor
- Cashier detail
- Config
- Limits
- Tickets summary
- Ticket lookup
- Ticket detail
- Official ticket
- Results
- Recargas
- Finance reports
- Operational report
- Alerts
- Audit
- Printer

## Final Commands

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
```

- [x] **Step 2: Run final verification**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: all commands return `BUILD SUCCESSFUL`.

---

## Implementation Order

1. Shared contracts/components first.
2. POS Lite adaptive viewport second.
3. Shell/login/menu third.
4. Admin/master/users/config/monitor fourth.
5. Tickets/results fifth.
6. Recargas/finance/reports sixth.
7. Alerts/audit/printer last.
8. Final QA checklist and full build.

## Acceptance Criteria

- `SalesActivity.kt` remains untouched for the non-sales visual redesign; POS Lite Venta contract and thermal ticket formatting were updated by later user request.
- Backup exists before implementation: `backups/non_sales_visual_redesign_20260523-023538.zip`.
- POS Lite uses one shared adaptive viewport/window contract instead of one-off per-screen manual sizing.
- Q2i-style 5.5 inch handheld POS screens resolve to compact, single-column, dense operational layouts.
- No visual section outside Venta keeps tall repeated cards where a dense row is clearer.
- Every redesigned list has a contract test.
- Every screen keeps existing business behavior.
- POS 5.5-inch layout is first-class.
- `compileDebugKotlin`, relevant unit tests, and `assembleDebug` pass before completion.
