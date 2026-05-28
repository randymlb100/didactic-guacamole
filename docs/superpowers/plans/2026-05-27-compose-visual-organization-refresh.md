# Compose Visual Organization Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mejorar la organizacion visual de LotteryNet Kotlin Compose en todas las secciones excepto Venta general, usando botones, dropdowns, badges, iconos, bottom sheets, cards, paleta y animaciones de forma consistente; Venta solo se toca para que POS Lite tenga un boton real y quepa bien en pantallas pequenas de 5.5".

**Architecture:** Mantener el flujo de servidor, permisos, ventas, tickets y sincronizacion sin cambios. La mejora se hace con contratos UI testeables, componentes compartidos en `NativeChrome.kt`, y ajustes por pantalla encima de la logica actual. Cada pantalla cambia su presentacion, no su data source ni sus llamadas remotas.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Compose Material 3 Adaptive, JUnit, Node QA smoke scripts, Android official guidance for adaptive layouts, `Scaffold`, `ModalBottomSheet`, `FlowRow`, `WindowSizeClass`, accessibility and touch target rules.

---

## Documentation Reviewed

- Android Developers: `Scaffold` debe recibir `innerPadding` y aplicarlo al contenido para que top/bottom bars no tapen la pantalla.
- Android Developers: `ModalBottomSheet` es el patron Compose Material 3 para contenido secundario, listas largas de acciones y menus moviles; debe salir de composicion cuando se oculta.
- Android Developers: `FlowRow` permite que chips, filtros y acciones pasen a otra linea cuando no caben, evitando cortes y nombres incompletos.
- Android Developers: adaptive Compose recomienda responder al tamano real de la ventana con `WindowSizeClass`/adaptive info, no asumir por modelo de dispositivo.
- Android Developers accessibility codelab: probar tamano de toque, content descriptions, click labels y TalkBack; usar minimo operacional 44-48dp.
- UI/UX Pro Max: un solo primary action por panel, badges solo para estado/conteo, dropdown para listas largas, tabs solo para vistas hermanas reales, animaciones cortas y funcionales.

## Non-Negotiables

- No cambiar validacion de jugadas, servidor, Supabase, tickets, pagos, print, permisos ni filtros de datos.
- No redisenar Venta general.
- Permitido en Venta: solo POS Lite/modo cajero para que el boton exista, haga la funcion real y el layout quepa en pantalla pequena.
- Todo cambio visual debe tener prueba de contrato antes de implementarse.
- En POS Lite se reduce texto secundario antes de reducir area de toque.
- No cards dentro de cards. No botones gigantes que parecen tabs. No badges usados como botones.

## Visual Direction

- Estilo: POS operativo moderno, denso pero respirable.
- Color: mantener base LotteryNet, con tokens semanticos: primary, success, warning, danger, neutral, surface.
- Botones: icono + texto corto para comandos principales; icon-only con contentDescription para acciones repetidas.
- Dropdowns: cajero, loteria, mes, estado, periodo, impresora.
- Bottom sheets: accesos rapidos de cajero, filtros avanzados, exportar, acciones de ticket, opciones largas.
- Cards: solo entidades repetidas o paneles principales; radio 8-10dp; padding estable.
- Animacion: 120-220ms para expandir, sheet, seleccion, estado de sync; nada decorativo.
- Pantalla pequena: `FlowRow`, `LazyVerticalGrid(GridCells.Adaptive(...))`, `BoxWithConstraints`, `WindowSizeClass`, `navigationBarsPadding`, `imePadding`.

---

## File Map

Shared UI system:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/theme/ThemeContractsTest.kt`

POS Lite only:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

Admin, supervisor, master, cajeros:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminCashierDetailActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Test: existing `Admin*ContractsTest.kt`, `MasterUiContractsTest.kt`, `UserAccountsFormattingTest.kt`

Tickets, resultados, finanzas, reportes, recargas:

- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/LotteryResultsView.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Test: matching UI contract tests already under `app/src/test/java/com/lotterynet/pro/ui/`

QA:

- Create: `docs/qa/2026-05-27-compose-visual-organization-refresh-checklist.md`
- Run: `tools/qa/*.mjs` only where behavior has risk.

---

## Task 1: Shared Visual Grammar Contract

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [ ] **Step 1: Write failing test**

Add:

```kotlin
@Test
fun `visual grammar assigns controls to the right component type`() {
    val grammar = resolveVisualGrammarContract()

    assertEquals(1, grammar.maxPrimaryActionsPerPanel)
    assertTrue(grammar.badgesOnlyForStatusOrCounts)
    assertTrue(grammar.dropdownsForLongOptionLists)
    assertTrue(grammar.bottomSheetsForSecondaryActionGroups)
    assertTrue(grammar.minTouchTargetDp >= 44)
    assertTrue(grammar.controlSpacingDp >= 8)
    assertTrue(grammar.motionDurationMs in 120..220)
}
```

- [ ] **Step 2: Run red test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest.visual grammar assigns controls to the right component type"
```

Expected: FAIL because `resolveVisualGrammarContract` does not exist.

- [ ] **Step 3: Add minimal contract**

Add to `NativeChrome.kt`:

```kotlin
internal data class VisualGrammarContract(
    val maxPrimaryActionsPerPanel: Int,
    val badgesOnlyForStatusOrCounts: Boolean,
    val dropdownsForLongOptionLists: Boolean,
    val bottomSheetsForSecondaryActionGroups: Boolean,
    val minTouchTargetDp: Int,
    val controlSpacingDp: Int,
    val motionDurationMs: Int,
)

internal fun resolveVisualGrammarContract(): VisualGrammarContract {
    return VisualGrammarContract(
        maxPrimaryActionsPerPanel = 1,
        badgesOnlyForStatusOrCounts = true,
        dropdownsForLongOptionLists = true,
        bottomSheetsForSecondaryActionGroups = true,
        minTouchTargetDp = 44,
        controlSpacingDp = 8,
        motionDurationMs = 160,
    )
}
```

- [ ] **Step 4: Run green test**

Run same test. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt
git commit -m "test: define compose visual grammar"
```

---

## Task 2: Shared Components For Buttons, Badges, Dropdowns, Sheets

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [ ] **Step 1: Write failing tests**

Add:

```kotlin
@Test
fun `adaptive action row moves extra commands into sheet on small screens`() {
    val contract = resolveAdaptiveActionGroupContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        commandCount = 5,
    )

    assertEquals(1, contract.visiblePrimaryCount)
    assertTrue(contract.overflowCount >= 3)
    assertTrue(contract.useBottomSheet)
}

@Test
fun `filter controls wrap instead of truncating on small screens`() {
    val contract = resolveFilterBandContract(LotteryNetWindowMode.POS_TIGHT)

    assertTrue(contract.useFlowRow)
    assertTrue(contract.stackLongDropdowns)
    assertTrue(contract.maxLabelLines >= 2)
}
```

- [ ] **Step 2: Run red tests**

Expected: FAIL because contracts do not exist.

- [ ] **Step 3: Implement contracts only**

Add:

```kotlin
internal data class AdaptiveActionGroupContract(
    val visiblePrimaryCount: Int,
    val visibleSecondaryCount: Int,
    val overflowCount: Int,
    val useBottomSheet: Boolean,
)

internal fun resolveAdaptiveActionGroupContract(
    windowMode: LotteryNetWindowMode,
    commandCount: Int,
): AdaptiveActionGroupContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    val visiblePrimary = if (commandCount > 0) 1 else 0
    val visibleSecondary = if (compact) 1.coerceAtMost(commandCount - visiblePrimary) else 2.coerceAtMost(commandCount - visiblePrimary)
    val overflow = (commandCount - visiblePrimary - visibleSecondary).coerceAtLeast(0)
    return AdaptiveActionGroupContract(
        visiblePrimaryCount = visiblePrimary,
        visibleSecondaryCount = visibleSecondary,
        overflowCount = overflow,
        useBottomSheet = compact && overflow > 0,
    )
}

internal data class FilterBandContract(
    val useFlowRow: Boolean,
    val stackLongDropdowns: Boolean,
    val maxLabelLines: Int,
)

internal fun resolveFilterBandContract(windowMode: LotteryNetWindowMode): FilterBandContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return FilterBandContract(
        useFlowRow = true,
        stackLongDropdowns = compact,
        maxLabelLines = if (compact) 2 else 1,
    )
}
```

- [ ] **Step 4: Run common tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest"
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt
git commit -m "feat: add adaptive compose control contracts"
```

---

## Task 3: POS Lite Sale Button And Small Screen Fit

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Write failing POS Lite behavior test**

Add:

```kotlin
@Test
fun `pos lite toggle is a real cashier sale command and keeps sale within small viewport`() {
    val contract = resolveVentaPosLiteControlContract(
        role = UserRole.CASHIER,
        viewportWidthDp = 360,
        viewportHeightDp = 640,
        posLiteEnabled = false,
    )

    assertTrue(contract.visible)
    assertTrue(contract.enabled)
    assertEquals("POS Lite", contract.label)
    assertTrue(contract.togglesPersistedMode)
    assertTrue(contract.reserveBottomSafePadding)
    assertTrue(contract.hideStatsBadges)
    assertTrue(contract.maxKeypadHeightDp <= 300)
}
```

- [ ] **Step 2: Run red test**

Expected: FAIL because `resolveVentaPosLiteControlContract` does not exist.

- [ ] **Step 3: Implement contract without changing sale server flow**

Add a contract near existing POS Lite contracts:

```kotlin
internal data class VentaPosLiteControlContract(
    val visible: Boolean,
    val enabled: Boolean,
    val label: String,
    val togglesPersistedMode: Boolean,
    val reserveBottomSafePadding: Boolean,
    val hideStatsBadges: Boolean,
    val maxKeypadHeightDp: Int,
)

internal fun resolveVentaPosLiteControlContract(
    role: UserRole,
    viewportWidthDp: Int,
    viewportHeightDp: Int,
    posLiteEnabled: Boolean,
): VentaPosLiteControlContract {
    val compact = viewportWidthDp <= 380 || viewportHeightDp <= 680 || posLiteEnabled
    return VentaPosLiteControlContract(
        visible = role == UserRole.CASHIER || role == UserRole.ADMIN,
        enabled = true,
        label = "POS Lite",
        togglesPersistedMode = true,
        reserveBottomSafePadding = true,
        hideStatsBadges = compact,
        maxKeypadHeightDp = if (compact) 300 else 360,
    )
}
```

- [ ] **Step 4: Wire button**

Use the existing `LocalPosModeRepository` path already used by shared POS mode code. The button must call the same persisted setting used by `effectiveSystemModeConfigForSession`; it must not create a separate local-only flag.

- [ ] **Step 5: Fit viewport**

In the sale scaffold content, keep `innerPadding`, add bottom safe padding for POS Lite, and cap non-critical panels before reducing key height.

- [ ] **Step 6: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"
```

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt
git commit -m "feat: make pos lite sale toggle functional"
```

---

## Task 4: Monitor, Supervisor, Admin Cajero Cards

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminUiContractsTest.kt`

- [ ] **Step 1: Write failing contract**

```kotlin
@Test
fun `cashier cards open quick action sheet instead of crowding row buttons`() {
    val contract = resolveCashierCardActionContract(LotteryNetWindowMode.POS_TIGHT)

    assertTrue(contract.cardTapOpensSheet)
    assertEquals(listOf("Tickets", "Reporte", "Cuadre", "Cobros"), contract.sheetActions)
    assertTrue(contract.filterTicketsByCashier)
    assertTrue(contract.filterReportsByCashier)
    assertTrue(contract.maxVisibleRowActions <= 1)
}
```

- [ ] **Step 2: Implement `CashierCardActionContract`**

Place pure contract logic in `AdminMonitorActivity.kt` or extract to a small UI contract file if the activity grows too much.

- [ ] **Step 3: UI implementation**

Make each cajero a modern card row:

- name/user and role on first line
- sold/profit/pending metrics inline
- status badge: Activo, Bloqueado, Sync, Sin ventas
- one visible chevron/action icon
- tap opens `ModalBottomSheet`
- sheet actions: tickets de ese cajero, reporte de ese cajero, cuadre, cobros/ganadores

- [ ] **Step 4: Preserve filters**

When tapping `Tickets` or `Reporte`, pass the selected cashier id/name using existing intent extras or existing filter functions. Do not change server query shape unless existing screen already supports it.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminUiContractsTest"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt app/src/test/java/com/lotterynet/pro/ui/admin/AdminUiContractsTest.kt
git commit -m "feat: organize monitor cashier cards"
```

---

## Task 5: Tickets And Reports Filter Bands

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Modify: matching test files

- [ ] **Step 1: Add tests for complete labels**

```kotlin
@Test
fun `period labels do not truncate on compact filters`() {
    val labels = resolveCompactPeriodLabels()

    assertEquals("Mes", labels.month)
    assertEquals("Día", labels.day)
    assertFalse(labels.month.contains("..."))
}
```

- [ ] **Step 2: Replace crowded button rows**

Use:

- segmented selector for `Día`, `Semana`, `Mes`, `Rango`
- dropdown for month/cajero/loteria
- one `Actualizar` primary action
- export/report actions in bottom sheet

- [ ] **Step 3: Wire cashier scope**

If opened from monitor card, preselect cashier and show badge `Cajero: {nombre}` with clear icon.

- [ ] **Step 4: Run focused tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*Ticket*ContractsTest" --tests "*Report*ContractsTest" --tests "*Finance*ContractsTest"
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/tickets app/src/main/java/com/lotterynet/pro/ui/report app/src/main/java/com/lotterynet/pro/ui/finance app/src/test/java/com/lotterynet/pro/ui
git commit -m "feat: reorganize ticket and report filters"
```

---

## Task 6: Results Section Refresh And Visual Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/LotteryResultsView.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`

- [ ] **Step 1: Write refresh action test**

```kotlin
@Test
fun `results refresh action is visible enabled and reports loading state`() {
    val contract = resolveResultsRefreshActionContract(isLoading = false, hasNetwork = true)

    assertTrue(contract.visible)
    assertTrue(contract.enabled)
    assertEquals("Refrescar", contract.label)
    assertTrue(contract.triggersServerRefresh)
}
```

- [ ] **Step 2: Implement real refresh contract**

The UI refresh button must call the existing results refresh path. Do not create a fake local-only reload.

- [ ] **Step 3: Visual cleanup**

- date and mode become filter band
- refresh is one visible command
- print/share/export go to overflow sheet
- rows keep logo, name, number, status; no noisy source badges unless needed

- [ ] **Step 4: Run tests and Node results smoke**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.results.*"
node tools/qa/results-stack-diagnostic.mjs
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/results app/src/test/java/com/lotterynet/pro/ui/results
git commit -m "feat: repair and polish results refresh ui"
```

---

## Task 7: Master, Admin, Cajero Dashboards

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Modify: matching tests

- [ ] **Step 1: Add dashboard organization tests**

Assert:

- max 1 primary action per panel
- repeated users use rows/cards with one status badge
- destructive actions are behind confirm sheet/dialog
- master/admin/cajero role labels use same badge tones

- [ ] **Step 2: Implement role-specific landing layout**

Master:

- bancas/admins as cards
- status + user count
- quick actions in sheet

Admin:

- cajeros as cards
- monitor/report/tickets/cuadre access from card sheet

Cajero:

- operational shortcuts only: venta, tickets, resultados, cuadre, impresora

- [ ] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*Master*Test" --tests "*Admin*Test" --tests "*UserAccounts*Test"
```

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/master app/src/main/java/com/lotterynet/pro/ui/admin app/src/main/java/com/lotterynet/pro/ui/users app/src/test/java/com/lotterynet/pro/ui
git commit -m "feat: organize role dashboards"
```

---

## Task 8: Final QA, Screens, And Release Guard

**Files:**
- Create: `docs/qa/2026-05-27-compose-visual-organization-refresh-checklist.md`

- [ ] **Step 1: Create QA checklist**

Checklist must include:

- POS 5.5 compact: no horizontal scroll, no cut labels, POS Lite button works
- Venta normal unchanged
- Monitor card sheet filters tickets/reports by selected cashier
- Results refresh button really refreshes
- Report period labels complete: `Mes`, `Día`, `Semana`, `Rango`
- Touch targets visually at least 44dp
- No card inside card
- No server flow changed

- [ ] **Step 2: Run unit tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 3: Run Node smoke scripts**

```powershell
node tools/qa/finance-monitor-smoke.mjs
node tools/qa/results-stack-diagnostic.mjs
node tools/qa/real-flow-smoke.mjs
```

- [ ] **Step 4: Build debug or release candidate**

```powershell
.\gradlew.bat assembleDebug
```

Use release build only after visual checks pass:

```powershell
.\gradlew.bat clean assembleRelease
```

- [ ] **Step 5: Commit QA doc**

```powershell
git add docs/qa/2026-05-27-compose-visual-organization-refresh-checklist.md
git commit -m "docs: add compose visual qa checklist"
```

---

## Execution Order

1. Shared grammar and contracts.
2. Shared adaptive components.
3. POS Lite sale-only fix.
4. Monitor/cajero cards and action sheet.
5. Tickets/reports/finance filters.
6. Results refresh and visual cleanup.
7. Master/admin/cajero dashboards.
8. QA and build.

This order keeps risk low: first design rules, then shared controls, then one screen group at a time. Server and sales flow stay protected.

---

## Additional Sondeo: Visual Debt Found After First Implementation Pass

This sondeo was done after the first branch implemented the shared visual contracts, POS Lite button, monitor cajero quick menu, results refresh label, and compact period labels. The remaining work below is only visual/interaction organization. Do not change server calls, ticket validation, payment logic, print logic, Supabase sync, or role permissions.

### Findings

- `RecargasActivity.kt` still has dense recharge controls, quick amount rows, confirmation dialogs, voucher share buttons, and balance panels that should be organized into one primary action plus secondary sheet actions.
- `PrinterActivity.kt` has print/test/share/save/back actions spread across grids. On small devices it needs one main print action, device/profile controls grouped clearly, and secondary actions moved to overflow.
- `ShellActivity.kt` is the real home/menu surface for roles. It still needs cleaner grouping, pinned frequent actions, and labels that do not rely on ellipsis.
- `OperationalReportActivity.kt` has crowded report action rows, filter dropdown ellipsis, tight metric rows, and actor rows that need better hierarchy.
- `AdminConfigActivity.kt`, `AdminLimitsActivity.kt`, and `AdminLotteryMonitorActivity.kt` contain long operational labels and configuration rows that should become grouped settings with sheets for dangerous/advanced actions.
- Admin limits need a business/UX separation: `Mis limites del admin` must be independent from `Limites para cajeros`. Admin has no sale cap by default; admin self-limits apply only when the admin explicitly sets them, and cashier defaults must not affect admin sales.
- `MasterDashboardActivity.kt` and `UserAccountsActivity.kt` are very large surfaces with many dialogs and row actions. They need entity cards and action sheets so admins can manage bancas, supervisores, and cajeros without cramped rows.
- `TicketOfficialActivity.kt`, `TicketLookupActivity.kt`, and `TicketDetailActivity.kt` still need a clearer ticket action system: one visible primary action, payment/duplicate/cancel in sheet, and better winner/payment status badges.
- `AdminWinnersActivity.kt`, `AdminAuditActivity.kt`, `AdminAlertsActivity.kt`, and `AdminCashierDetailActivity.kt` need the same list/card grammar: consistent empty/loading/error states, one row action, and readable compact metrics.
- Theme files were listed in the original plan but not covered in the first pass. Add semantic state tokens for success/warning/danger/info/sync and use them instead of ad hoc tones per screen.
- QA should include screenshots or emulator checks for 360x640 and 393x851, because the main visual risk is clipped text and crowded controls on POS phones.

---

## Task 9: Shell Menu Role Home Reorganization

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellUiContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/shell/ShellMenuContractsTest.kt`

- [ ] **Step 1: Add shell organization contract test**

Add to `ShellUiContractsTest.kt`:

```kotlin
@Test
fun `shell menu groups role actions without clipped labels on pos screens`() {
    val contract = resolveShellRoleHomeOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        role = UserRole.CASHIER,
        actionCount = 10,
    )

    assertTrue(contract.useGroupedSections)
    assertTrue(contract.pinFrequentActions)
    assertTrue(contract.maxPrimaryActions <= 1)
    assertTrue(contract.useOverflowForSecondaryActions)
    assertTrue(contract.labelMaxLines >= 2)
    assertTrue(contract.minTouchTargetDp >= 44)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.shell.ShellUiContractsTest.shell menu groups role actions without clipped labels on pos screens"
```

Expected: FAIL because `resolveShellRoleHomeOrganizationContract` does not exist.

- [ ] **Step 3: Implement contract**

Add near existing shell layout contracts:

```kotlin
internal data class ShellRoleHomeOrganizationContract(
    val useGroupedSections: Boolean,
    val pinFrequentActions: Boolean,
    val maxPrimaryActions: Int,
    val useOverflowForSecondaryActions: Boolean,
    val labelMaxLines: Int,
    val minTouchTargetDp: Int,
)

internal fun resolveShellRoleHomeOrganizationContract(
    windowMode: LotteryNetWindowMode,
    role: UserRole,
    actionCount: Int,
): ShellRoleHomeOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return ShellRoleHomeOrganizationContract(
        useGroupedSections = true,
        pinFrequentActions = role == UserRole.CASHIER || actionCount > 6,
        maxPrimaryActions = 1,
        useOverflowForSecondaryActions = compact && actionCount > 5,
        labelMaxLines = if (compact) 2 else 1,
        minTouchTargetDp = 44,
    )
}
```

- [ ] **Step 4: Reorganize shell UI**

Use sections:

- Cajero: `Venta`, `Tickets`, `Resultados`, `Cuadre`, then overflow for `Recargas`, `Cobros`, `Eliminar Ticket`, `Impresora`.
- Admin/Supervisor: `Monitoreo`, `Finanzas`, `Tickets`, `Reporte`, then overflow for setup/advanced tools.
- Master: `Panel master`, `Crear banca`, `Finanzas`, then overflow for audit/config.

Keep existing navigation intents and route classes unchanged.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.shell.*"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/test/java/com/lotterynet/pro/ui/shell
git commit -m "feat: organize role shell menu"
```

---

## Task 10: Recargas Visual Flow Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/recharge/RecargasUiContractsTest.kt`

- [ ] **Step 1: Add recharge action layout test**

```kotlin
@Test
fun `recharge screen keeps one primary sale action and moves sharing into sheet`() {
    val contract = resolveRechargeActionOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        hasVoucher = true,
        historyCount = 8,
    )

    assertEquals("Vender recarga", contract.primaryActionLabel)
    assertEquals(1, contract.visiblePrimaryActionCount)
    assertTrue(contract.shareActionsInSheet)
    assertEquals(listOf("WhatsApp", "Compartir", "Imprimir", "Guardar"), contract.voucherSheetActions)
    assertTrue(contract.historyUsesCompactCards)
    assertTrue(contract.quickAmountsWrap)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.recharge.RecargasUiContractsTest.recharge screen keeps one primary sale action and moves sharing into sheet"
```

Expected: FAIL because `resolveRechargeActionOrganizationContract` does not exist.

- [ ] **Step 3: Implement contract**

```kotlin
internal data class RechargeActionOrganizationContract(
    val primaryActionLabel: String,
    val visiblePrimaryActionCount: Int,
    val shareActionsInSheet: Boolean,
    val voucherSheetActions: List<String>,
    val historyUsesCompactCards: Boolean,
    val quickAmountsWrap: Boolean,
)

internal fun resolveRechargeActionOrganizationContract(
    windowMode: LotteryNetWindowMode,
    hasVoucher: Boolean,
    historyCount: Int,
): RechargeActionOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return RechargeActionOrganizationContract(
        primaryActionLabel = "Vender recarga",
        visiblePrimaryActionCount = 1,
        shareActionsInSheet = hasVoucher && compact,
        voucherSheetActions = listOf("WhatsApp", "Compartir", "Imprimir", "Guardar"),
        historyUsesCompactCards = historyCount > 0,
        quickAmountsWrap = compact,
    )
}
```

- [ ] **Step 4: Reorganize Recargas UI**

Keep provider, phone, amount, paquetico, and balance behavior unchanged. Move voucher sharing/export buttons into a bottom sheet or overflow menu. Keep one main visible button for the sale. Quick amount chips must wrap or grid on POS phones.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.recharge.RecargasUiContractsTest"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt app/src/test/java/com/lotterynet/pro/ui/recharge/RecargasUiContractsTest.kt
git commit -m "feat: organize recargas visual flow"
```

---

## Task 11: Printer Control Center Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/printer/PrinterContractsTest.kt`

- [ ] **Step 1: Add printer action contract test**

```kotlin
@Test
fun `printer screen exposes print as primary and keeps secondary actions in overflow`() {
    val contract = resolvePrinterActionOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        hasPreviewTicket = true,
        pairedPrinterCount = 2,
    )

    assertEquals("Imprimir", contract.primaryActionLabel)
    assertEquals(1, contract.visiblePrimaryActionCount)
    assertTrue(contract.secondaryActionsInSheet)
    assertEquals(listOf("Probar", "Compartir", "Guardar", "Volver al ticket"), contract.secondaryActionLabels)
    assertTrue(contract.deviceSelectorIsDropdown)
    assertTrue(contract.statusVisibleAboveActions)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.printer.PrinterContractsTest.printer screen exposes print as primary and keeps secondary actions in overflow"
```

Expected: FAIL because `resolvePrinterActionOrganizationContract` does not exist.

- [ ] **Step 3: Implement contract**

```kotlin
internal data class PrinterActionOrganizationContract(
    val primaryActionLabel: String,
    val visiblePrimaryActionCount: Int,
    val secondaryActionsInSheet: Boolean,
    val secondaryActionLabels: List<String>,
    val deviceSelectorIsDropdown: Boolean,
    val statusVisibleAboveActions: Boolean,
)

internal fun resolvePrinterActionOrganizationContract(
    windowMode: LotteryNetWindowMode,
    hasPreviewTicket: Boolean,
    pairedPrinterCount: Int,
): PrinterActionOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return PrinterActionOrganizationContract(
        primaryActionLabel = "Imprimir",
        visiblePrimaryActionCount = 1,
        secondaryActionsInSheet = compact && hasPreviewTicket,
        secondaryActionLabels = listOf("Probar", "Compartir", "Guardar", "Volver al ticket"),
        deviceSelectorIsDropdown = pairedPrinterCount > 1,
        statusVisibleAboveActions = true,
    )
}
```

- [ ] **Step 4: Reorganize printer UI**

Keep Bluetooth permission, selected printer, preview rendering, and print/share/save handlers unchanged. Put printer status and selected device before the action zone. Keep `Imprimir` as the visible primary action. Move test/share/save/back into overflow sheet on compact screens.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.printer.PrinterContractsTest"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt app/src/test/java/com/lotterynet/pro/ui/printer/PrinterContractsTest.kt
git commit -m "feat: organize printer actions"
```

---

## Task 12: Operational Report And Finance Export Sheet

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/report/OperationalReportContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/finance/FinanceUiContractsTest.kt`

- [ ] **Step 1: Add export and filter contract test**

```kotlin
@Test
fun `operational report uses filter band and export sheet on compact screens`() {
    val contract = resolveOperationalReportActionOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        filterCount = 4,
        exportActionCount = 4,
    )

    assertTrue(contract.filtersUseBand)
    assertTrue(contract.longFiltersUseDropdown)
    assertEquals("Actualizar", contract.primaryActionLabel)
    assertTrue(contract.exportActionsInSheet)
    assertEquals(listOf("WhatsApp", "Compartir", "Imprimir", "Guardar"), contract.exportLabels)
    assertTrue(contract.actorRowsUseCompactMetrics)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.report.OperationalReportContractsTest.operational report uses filter band and export sheet on compact screens"
```

Expected: FAIL because `resolveOperationalReportActionOrganizationContract` does not exist.

- [ ] **Step 3: Implement contract**

```kotlin
internal data class OperationalReportActionOrganizationContract(
    val filtersUseBand: Boolean,
    val longFiltersUseDropdown: Boolean,
    val primaryActionLabel: String,
    val exportActionsInSheet: Boolean,
    val exportLabels: List<String>,
    val actorRowsUseCompactMetrics: Boolean,
)

internal fun resolveOperationalReportActionOrganizationContract(
    windowMode: LotteryNetWindowMode,
    filterCount: Int,
    exportActionCount: Int,
): OperationalReportActionOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return OperationalReportActionOrganizationContract(
        filtersUseBand = true,
        longFiltersUseDropdown = compact && filterCount > 2,
        primaryActionLabel = "Actualizar",
        exportActionsInSheet = compact && exportActionCount > 1,
        exportLabels = listOf("WhatsApp", "Compartir", "Imprimir", "Guardar"),
        actorRowsUseCompactMetrics = true,
    )
}
```

- [ ] **Step 4: Reorganize report UI**

Do not change report repository calls. Make filters a compact band. Keep `Actualizar` as one primary command. Move export/share/print/save into one sheet. Actor rows should show name, ventas, premios, neto, and status without forcing long text into one line.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.report.OperationalReportContractsTest" --tests "com.lotterynet.pro.ui.finance.FinanceUiContractsTest"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt app/src/test/java/com/lotterynet/pro/ui/report/OperationalReportContractsTest.kt app/src/test/java/com/lotterynet/pro/ui/finance/FinanceUiContractsTest.kt
git commit -m "feat: organize operational report actions"
```

---

## Task 13: Admin Configuration, Limits, And Lottery Monitor Settings

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/CashierLimitCloudSyncCoordinator.kt`
- Modify: `supabase/migrations/<new>_separate_admin_self_limits_from_cashier_defaults.sql`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminConfigContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`
- Modify: matching server/SQL smoke test under `tools/qa/`

- [ ] **Step 1: Add admin settings organization test**

```kotlin
@Test
fun `admin settings group long operational controls and protect destructive actions`() {
    val contract = resolveAdminSettingsOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        settingCount = 9,
        destructiveActionCount = 3,
    )

    assertTrue(contract.useGroupedSettingCards)
    assertTrue(contract.longModeLabelsWrap)
    assertTrue(contract.advancedActionsInSheet)
    assertTrue(contract.destructiveActionsRequireConfirm)
    assertTrue(contract.lotteryRowsUseStatusBadges)
    assertEquals(1, contract.maxVisibleDestructiveActions)
}
```

- [ ] **Step 2: Add admin self-limit separation test**

Add to `AdminLimitsContractsTest.kt`:

```kotlin
@Test
fun `admin self limits are separate from cashier defaults`() {
    val contract = resolveAdminLimitScopeContract(
        selectedScope = AdminLimitScope.ADMIN_SELF,
        adminHasSelfLimits = false,
        cashierDefaultsEnabled = true,
    )

    assertEquals(AdminLimitScope.ADMIN_SELF, contract.selectedScope)
    assertEquals("Mis limites", contract.title)
    assertTrue(contract.adminSalesUnlimitedWhenEmpty)
    assertFalse(contract.cashierDefaultsAffectAdmin)
    assertEquals(listOf("Mis limites", "Todos los cajeros", "Por cajero"), contract.scopeLabels)
}
```

- [ ] **Step 3: Run red tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminConfigContractsTest.admin settings group long operational controls and protect destructive actions"
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminLimitsContractsTest.admin self limits are separate from cashier defaults"
```

Expected: FAIL because `resolveAdminSettingsOrganizationContract` and `resolveAdminLimitScopeContract` do not exist.

- [ ] **Step 4: Implement visual organization contracts**

```kotlin
internal data class AdminSettingsOrganizationContract(
    val useGroupedSettingCards: Boolean,
    val longModeLabelsWrap: Boolean,
    val advancedActionsInSheet: Boolean,
    val destructiveActionsRequireConfirm: Boolean,
    val lotteryRowsUseStatusBadges: Boolean,
    val maxVisibleDestructiveActions: Int,
)

internal fun resolveAdminSettingsOrganizationContract(
    windowMode: LotteryNetWindowMode,
    settingCount: Int,
    destructiveActionCount: Int,
): AdminSettingsOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return AdminSettingsOrganizationContract(
        useGroupedSettingCards = settingCount > 4,
        longModeLabelsWrap = compact,
        advancedActionsInSheet = compact,
        destructiveActionsRequireConfirm = destructiveActionCount > 0,
        lotteryRowsUseStatusBadges = true,
        maxVisibleDestructiveActions = 1,
    )
}
```

- [ ] **Step 5: Implement admin limit scope contract**

Use explicit scopes so the UI cannot mix admin self-limits with cashier defaults:

```kotlin
internal enum class AdminLimitScope {
    ADMIN_SELF,
    CASHIER_DEFAULTS,
    CASHIER_SPECIFIC,
}

internal data class AdminLimitScopeContract(
    val selectedScope: AdminLimitScope,
    val title: String,
    val adminSalesUnlimitedWhenEmpty: Boolean,
    val cashierDefaultsAffectAdmin: Boolean,
    val scopeLabels: List<String>,
)

internal fun resolveAdminLimitScopeContract(
    selectedScope: AdminLimitScope,
    adminHasSelfLimits: Boolean,
    cashierDefaultsEnabled: Boolean,
): AdminLimitScopeContract {
    val title = when (selectedScope) {
        AdminLimitScope.ADMIN_SELF -> "Mis limites"
        AdminLimitScope.CASHIER_DEFAULTS -> "Todos los cajeros"
        AdminLimitScope.CASHIER_SPECIFIC -> "Por cajero"
    }
    return AdminLimitScopeContract(
        selectedScope = selectedScope,
        title = title,
        adminSalesUnlimitedWhenEmpty = !adminHasSelfLimits,
        cashierDefaultsAffectAdmin = false,
        scopeLabels = listOf("Mis limites", "Todos los cajeros", "Por cajero"),
    )
}
```

- [ ] **Step 6: Reorganize settings UI**

Group system mode, POS Lite, cashier mode, lottery availability, manual results, and dangerous lottery operations into separate sections. Long labels must wrap or use dropdowns. Dangerous actions stay behind confirmation dialogs or sheets.

For limits, use three visible sections or tabs:

- `Mis limites`: applies only to admin sales. Empty or `0` means admin sells without cap.
- `Todos los cajeros`: default limits for cashiers only.
- `Por cajero`: override for one selected cashier only.

The copy must make the rule visible near the form:

```text
Si Mis limites esta vacio, el admin vende sin tope. Los limites de cajeros no afectan al admin.
```

- [ ] **Step 7: Implement server data separation**

Do not reuse cashier defaults for admin sales. Store or read admin self-limits under a separate object, for example:

```json
{
  "adminSelf": {
    "sp": 0,
    "p3": 0,
    "p4": 0,
    "p3box": 0,
    "p4box": 0
  },
  "defaults": {
    "sp": 100,
    "p3": 10,
    "p4": 10,
    "p3box": 10,
    "p4box": 10
  },
  "byUser": {
    "cajero1": {
      "sp": 200
    }
  }
}
```

Server rule:

- If seller role is admin/master and `adminSelf` is empty or all relevant values are `0`, return no limit.
- If seller role is admin/master and `adminSelf` has a positive limit for the play type, enforce only that admin self-limit.
- If seller role is cashier, use `defaults` plus `byUser[cashier]`.
- Never let `defaults` apply to admin/master sales.

- [ ] **Step 8: Add server smoke test**

Create or extend a Node QA script with these checks:

```js
check(adminSuperPale200WithoutSelfLimit.ok === true, "admin puede vender Super Pale 200 sin limites propios");
check(cashierSuperPale200WithDefault100.ok === false, "cajero respeta limite default Super Pale 100");
check(adminSuperPale200WithSelfLimit75.ok === false, "admin respeta Mis limites cuando se impone SP 75");
check(cashierSpecificSuperPale200Override250.ok === true, "limite por cajero no cambia Mis limites del admin");
```

- [ ] **Step 9: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminConfigContractsTest" --tests "com.lotterynet.pro.ui.admin.AdminLimitsContractsTest"
node tools/qa/admin-self-limits-smoke.mjs
```

- [ ] **Step 10: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt app/src/main/java/com/lotterynet/pro/core/sync/CashierLimitCloudSyncCoordinator.kt app/src/test/java/com/lotterynet/pro/ui/admin supabase/migrations tools/qa
git commit -m "feat: separate admin self limits from cashier limits"
```

---

## Task 14: Master And User Administration Cards

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/users/UserAccountsFormattingTest.kt`

- [ ] **Step 1: Add master/user admin card action tests**

```kotlin
@Test
fun `master and user admin rows expose quick action sheets instead of crowded row buttons`() {
    val contract = resolveAdminEntityCardActionContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        actionCount = 6,
        hasDangerAction = true,
    )

    assertTrue(contract.entityTapOpensSheet)
    assertEquals(1, contract.maxInlineActions)
    assertTrue(contract.dangerActionsRequireConfirm)
    assertTrue(contract.credentialsUseRevealOrCopyActions)
    assertTrue(contract.roleBadgeVisible)
    assertTrue(contract.metricsUseTwoColumnGrid)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterUiContractsTest.master and user admin rows expose quick action sheets instead of crowded row buttons"
```

Expected: FAIL because `resolveAdminEntityCardActionContract` does not exist.

- [ ] **Step 3: Implement shared contract**

Place the contract in `NativeChrome.kt` if both Master and Users need it, or in the screen file first if only one screen uses it:

```kotlin
internal data class AdminEntityCardActionContract(
    val entityTapOpensSheet: Boolean,
    val maxInlineActions: Int,
    val dangerActionsRequireConfirm: Boolean,
    val credentialsUseRevealOrCopyActions: Boolean,
    val roleBadgeVisible: Boolean,
    val metricsUseTwoColumnGrid: Boolean,
)

internal fun resolveAdminEntityCardActionContract(
    windowMode: LotteryNetWindowMode,
    actionCount: Int,
    hasDangerAction: Boolean,
): AdminEntityCardActionContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return AdminEntityCardActionContract(
        entityTapOpensSheet = compact && actionCount > 2,
        maxInlineActions = if (compact) 1 else 2,
        dangerActionsRequireConfirm = hasDangerAction,
        credentialsUseRevealOrCopyActions = true,
        roleBadgeVisible = true,
        metricsUseTwoColumnGrid = true,
    )
}
```

- [ ] **Step 4: Reorganize Master/User screens**

Use entity cards for bancas, supervisores, and cajeros. Show display name, username, role/status badge, and two or four compact metrics. Move reset password, activate/deactivate, assign, delete, and mode operations into sheet actions. Keep existing repository calls and permission rules unchanged.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterUiContractsTest" --tests "com.lotterynet.pro.ui.users.UserAccountsFormattingTest"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt app/src/test/java/com/lotterynet/pro/ui/users/UserAccountsFormattingTest.kt
git commit -m "feat: organize admin entity cards"
```

---

## Task 15: Ticket Official, Lookup, And Detail Action System

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketDetailActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketOfficialContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketLookupContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketCollectionContractsTest.kt`

- [ ] **Step 1: Add ticket action organization test**

```kotlin
@Test
fun `ticket screens keep one visible primary action and move secondary ticket actions into sheet`() {
    val contract = resolveTicketActionOrganizationContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        actionMode = "pagar",
        ticketIsWinner = true,
        canCancel = true,
    )

    assertEquals(1, contract.visiblePrimaryActionCount)
    assertEquals("Pagar", contract.primaryActionLabel)
    assertTrue(contract.secondaryActionsInSheet)
    assertEquals(listOf("Abrir", "Duplicar", "Imprimir", "WhatsApp", "Anular"), contract.secondaryActionLabels)
    assertTrue(contract.winnerStatusBadgeVisible)
    assertTrue(contract.searchAndQrStayVisible)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.TicketOfficialContractsTest.ticket screens keep one visible primary action and move secondary ticket actions into sheet"
```

Expected: FAIL because `resolveTicketActionOrganizationContract` does not exist.

- [ ] **Step 3: Implement contract**

```kotlin
internal data class TicketActionOrganizationContract(
    val visiblePrimaryActionCount: Int,
    val primaryActionLabel: String,
    val secondaryActionsInSheet: Boolean,
    val secondaryActionLabels: List<String>,
    val winnerStatusBadgeVisible: Boolean,
    val searchAndQrStayVisible: Boolean,
)

internal fun resolveTicketActionOrganizationContract(
    windowMode: LotteryNetWindowMode,
    actionMode: String,
    ticketIsWinner: Boolean,
    canCancel: Boolean,
): TicketActionOrganizationContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    val primary = when (actionMode.lowercase()) {
        "pagar" -> "Pagar"
        "duplicar" -> "Duplicar"
        "anular" -> "Anular"
        else -> "Abrir"
    }
    val secondary = buildList {
        add("Abrir")
        add("Duplicar")
        add("Imprimir")
        add("WhatsApp")
        if (canCancel) add("Anular")
    }.distinct().filterNot { it == primary }
    return TicketActionOrganizationContract(
        visiblePrimaryActionCount = 1,
        primaryActionLabel = primary,
        secondaryActionsInSheet = compact && secondary.isNotEmpty(),
        secondaryActionLabels = secondary,
        winnerStatusBadgeVisible = ticketIsWinner,
        searchAndQrStayVisible = true,
    )
}
```

- [ ] **Step 4: Reorganize ticket screens**

Keep ticket lookup, official ticket render, payment, cancellation, duplication, QR scan, and print flows unchanged. Use one visible primary action per row/screen. Move secondary actions into a bottom sheet. Keep search and QR visible in lookup because they are entry methods, not secondary actions.

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.tickets.*"
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/tickets app/src/test/java/com/lotterynet/pro/ui/tickets
git commit -m "feat: organize ticket action surfaces"
```

---

## Task 16: Secondary Admin List Screens And QA Evidence

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminAuditActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminAlertsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminCashierDetailActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminWinnersContractsTest.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminUiContractsTest.kt`
- Create: `docs/qa/2026-05-27-compose-visual-secondary-screens-checklist.md`

- [ ] **Step 1: Add secondary list contract test**

```kotlin
@Test
fun `secondary admin lists use consistent rows empty states and one row action`() {
    val contract = resolveSecondaryAdminListVisualContract(
        windowMode = LotteryNetWindowMode.POS_TIGHT,
        itemCount = 12,
        rowActionCount = 3,
    )

    assertTrue(contract.useCompactRecordRows)
    assertTrue(contract.emptyStateHasAction)
    assertTrue(contract.loadingStateUsesCompactSpinner)
    assertEquals(1, contract.maxVisibleRowActions)
    assertTrue(contract.extraRowActionsInSheet)
    assertTrue(contract.metricLabelsWrap)
}
```

- [ ] **Step 2: Run red test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminUiContractsTest.secondary admin lists use consistent rows empty states and one row action"
```

Expected: FAIL because `resolveSecondaryAdminListVisualContract` does not exist.

- [ ] **Step 3: Implement contract**

```kotlin
internal data class SecondaryAdminListVisualContract(
    val useCompactRecordRows: Boolean,
    val emptyStateHasAction: Boolean,
    val loadingStateUsesCompactSpinner: Boolean,
    val maxVisibleRowActions: Int,
    val extraRowActionsInSheet: Boolean,
    val metricLabelsWrap: Boolean,
)

internal fun resolveSecondaryAdminListVisualContract(
    windowMode: LotteryNetWindowMode,
    itemCount: Int,
    rowActionCount: Int,
): SecondaryAdminListVisualContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return SecondaryAdminListVisualContract(
        useCompactRecordRows = itemCount >= 0,
        emptyStateHasAction = true,
        loadingStateUsesCompactSpinner = true,
        maxVisibleRowActions = 1,
        extraRowActionsInSheet = compact && rowActionCount > 1,
        metricLabelsWrap = compact,
    )
}
```

- [ ] **Step 4: Reorganize secondary list screens**

Use the same compact row/card grammar for winners, audit, alerts, and cashier detail. Empty states must explain the current filter or day. Loading states must not occupy the whole screen unless it is the first load.

- [ ] **Step 5: Create QA checklist**

Create `docs/qa/2026-05-27-compose-visual-secondary-screens-checklist.md`:

```markdown
# Compose Visual Secondary Screens QA

- [ ] 360x640: Shell menu labels wrap and no route button is cut.
- [ ] 360x640: Recargas has one primary sale button and voucher actions in sheet.
- [ ] 360x640: Printer has one visible Imprimir button and secondary actions in sheet.
- [ ] 360x640: Operational report filters do not ellipsize selected period/cajero.
- [ ] 360x640: Ticket lookup keeps search and QR visible.
- [ ] 360x640: Ticket official shows winner/payment badge without covering ticket content.
- [ ] 393x851: Admin config long POS mode labels wrap or use dropdown.
- [ ] 393x851: Master/User cards show one inline action and open sheet for the rest.
- [ ] 393x851: Winners/audit/alerts show readable empty/loading states.
- [ ] Server flow, print flow, payment flow, cancellation flow, and ticket validation unchanged.
```

- [ ] **Step 6: Run tests and build**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.*"
.\gradlew.bat assembleDebug
```

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/lotterynet/pro/ui/admin app/src/test/java/com/lotterynet/pro/ui/admin docs/qa/2026-05-27-compose-visual-secondary-screens-checklist.md
git commit -m "feat: organize secondary admin visual lists"
```

---

## Expanded Execution Order

1. Keep Tasks 1-6 as already started/completed in the first visual branch.
2. Finish Task 5 fully for Ticket Summary, Finance Reports, and Operational Report filter bands.
3. Finish Task 7 for role dashboards and user administration.
4. Run Task 9 so every role lands in a cleaner home menu.
5. Run Task 10 for Recargas.
6. Run Task 11 for Printer.
7. Run Task 12 for Operational Report and export sheets.
8. Run Task 13 for Admin config, limits, and lottery monitor settings.
9. Run Task 14 for Master/User entity cards.
10. Run Task 15 for Ticket official, lookup, and detail actions.
11. Run Task 16 for secondary admin lists and QA evidence.
12. Finish Task 8 after all expanded tasks pass.

The safest order is role home and operational surfaces first, then admin settings and secondary lists. Venta remains protected except the POS Lite-only work already defined.
