# Compose UI Organization Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize LotteryNet's Kotlin Compose screens so actions, tabs, dropdowns, badges, and sections feel natural on Android POS devices. Sale general must remain unchanged; only `Modo POS Lite` may adjust sale density for compact POS devices.

**Architecture:** Keep the current native Compose app and shared `NativeChrome.kt` component system. Improve one section at a time by extracting decision contracts, reducing visible actions, replacing unnecessary tabs with compact rows/dropdowns, and adding regression tests before each UI change.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android unit tests, existing Node production smoke tools.

---

## Reference Rules Codex Must Follow

Use these rules before changing any UI:

- **Buttons:** visible buttons are for direct commands: save, sync, print, share, open, pay, delete. Primary button count should be 1 per panel; secondary commands go into overflow or rows.
- **Icon buttons:** use for compact repeated actions or top-bar actions where text would crowd the POS screen.
- **Segmented controls:** use for mutually exclusive modes with 2-4 options, such as `Lotería`, `Pick`, `Lotería + Pick`, day/week/month.
- **Dropdowns:** use when there are many choices or choices are not constantly changed, such as cashier selector, lottery selector, month selector, provider selector.
- **Tabs:** use only for true sibling views where each tab changes the whole content area. Do not use tabs just to hide a few fields.
- **Badges:** use for status/counts only: synced, pending, closed, paid, blocked, count. Do not use badges as buttons or labels for normal content.
- **Cards/panels:** no card inside card. Use panels for major groups only; repeated items can be rows.
- **POS density:** reduce visible text before reducing tap target size. Keep action height stable using shared `CompactActionButton`.

Documentation anchors:

- Android Compose menus: `DropdownMenu`, `DropdownMenuItem`, and an `IconButton` trigger.
- Android Compose tabs: primary tabs are for main destinations; secondary tabs are for another related hierarchy inside content.
- Android Compose Material components: buttons for actions, icon buttons for minor one-tap actions, segmented buttons for selecting options, badges for counts/status.

## Official Documentation Notes

Sources checked on 2026-05-23:

- Android Compose Material 3:
  - `https://developer.android.com/develop/ui/compose/designsystems/material3`
  - Compose provides Material 3 components, theming, typography, motion, and Android system consistency.
- Android Compose buttons:
  - `https://developer.android.com/jetpack/compose/components/button`
  - Filled buttons are high emphasis and should be reserved for primary actions such as save/submit. Outlined/text styles are lower emphasis.
- Android Compose menus:
  - `https://developer.android.com/develop/ui/compose/components/menu`
  - Use dropdown menus for temporary choice lists; trigger them from a compact element such as an icon button or field.
- Android Compose tabs:
  - `https://developer.android.com/develop/ui/compose/components/tabs`
  - Use tabs to organize related content groups; primary tabs are top-level content destinations, secondary tabs are for another hierarchy inside content.
- Android Compose badges:
  - `https://developer.android.com/develop/ui/compose/components/badges`
  - Badges communicate counts/status on navigation items or icons. They should not replace buttons or section labels.
- Android adaptive layout/window size classes:
  - `https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes`
  - Use compact/medium/expanded window classes instead of device-name assumptions. Compact width is the phone/POS baseline.
- Android adaptive do/don't:
  - `https://developer.android.com/develop/ui/compose/layouts/adaptive/adaptive-dos-and-donts`
  - Do adapt to app window size and avoid hard orientation/aspect assumptions.
- Android Compose animations:
  - `https://developer.android.com/develop/ui/compose/animation/composables-modifiers`
  - Use `AnimatedVisibility`, `AnimatedContent`, and `animateContentSize` for meaningful state changes. Avoid decorative motion in dense POS flows.
- Android Compose navigation:
  - `https://developer.android.com/develop/ui/compose/navigation`
  - Navigation should pass only minimal identifiers and adapt to window size. Top-level navigation should not duplicate local section controls.
- Q2I Android handheld POS reference:
  - `https://eastroyce.en.made-in-china.com/product/JmRpGDhAWvUt/China-Q2I-Android-8-1-POS-Terminal-Android-Mini-POS-System-Machine-Portable-with-58mm-Thermal-Receipt-Printer.html`
  - Reference device is a handheld POS with 5.5" 1280x720 screen and 58mm thermal printer. Treat this as the practical target for POS Lite density.

Practical interpretation for LotteryNet:

- POS screens start at compact width. Design the compact version first, then expand.
- One visible primary command per panel. Everything else becomes secondary, overflow, or a state selector.
- Use tabs only when the user is switching between real workspaces, not when changing one field group.
- Use segmented controls for 2-4 mutually exclusive modes.
- Use dropdowns for long lists: cajeros, loterías, fechas/meses, compañías, impresoras.
- Use badges only for `Sincronizado`, `Pendiente`, `Bloqueado`, `Pagado`, counts, and close/status states.
- Use animation only to show/hide panels, expand/collapse technical details, or confirm state transitions. No bouncing/large decorative motion.
- Avoid explanatory text inside the app where the control itself can be clear. Keep copy operational.

## Current Findings

### Global Component Layer

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

Problems:
- `CompactActionButton` is used for everything: true commands, filters, tabs, toggles, date shortcuts, and mode chips.
- Button style is visually heavy because every action looks equally important.
- `CompactStatusBadge` is reused widely, but status and metadata are not always separated.

Plan:
- Add a small action hierarchy contract:
  - `PrimaryCommand`: one per panel.
  - `SecondaryCommand`: lower-emphasis visible button.
  - `OverflowCommand`: menu item behind icon.
  - `ChoiceOption`: segmented/chip option, not a command.
- Keep existing function names where possible. Add wrappers instead of rewriting all screens in one pass.

### User/Cajeros

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/users/UserAccountsFormattingTest.kt`

Problems:
- Too many concepts compete: account search, cashier selector, limits, mode, prizes, supervisors, account cards.
- `CashierAdminSectionTabs` acts like tabs but the content is more like admin tasks.
- Mode assignment and global/user limits are mixed with account card editing.
- Server refresh/status is visible in several places.

Plan:
- Replace section tabs with a task list or compact segmented selector only if exactly one content area changes.
- Make four clean admin tasks:
  - `Cajeros`: select cashier and edit account.
  - `Límites`: global or selected cashier limits.
  - `Modo venta`: selected cashier mode plus `Aplicar a todos`.
  - `Premios`: payout settings.
- Move server refresh into header overflow and show one status strip only.
- Hide account cards unless user explicitly selects a cashier.

### Configuración

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminConfigContractsTest.kt`

Problems:
- `Sistema` contains manual results, system mode, and server save in one panel.
- Manual result date options are rendered as buttons, but behave like mode choices.
- `Operación`, `Caja`, `Sistema`, and `Bloqueo` are useful groups, but some copy is explanatory instead of operational.

Plan:
- Keep `Operación`, `Caja`, `Bloqueo de lotería`, `Sistema`.
- Move manual results to its own panel: `Resultados manuales`.
- Use segmented control for `Hoy/Ayer/Anteayer`, not command buttons.
- Keep `Guardar servidor` as the only command in the mode panel.
- Use badges only for sync state and blocked counts.

### Finanzas/Cuadre

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/finance/FinanceUiContractsTest.kt`

Problems:
- Header has too many date controls: quick date buttons, period buttons, manual range, month dropdown, then export actions.
- Share/print/save actions take too much horizontal space.
- `Desde/Hasta` are command-looking buttons but actually field selectors.

Plan:
- Make a single filter band:
  - Period segmented control: `Día`, `Semana`, `Mes`, `Rango`.
  - Date dropdown or calendar field based on selected period.
- Put export actions behind one `Exportar` overflow with WhatsApp, Compartir, Imprimir, Guardar.
- Keep only one primary action visible if needed: `Actualizar`.

### Resultados

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`

Problems:
- Results has useful status badges, but action rows can still crowd.
- Source badges are disabled in code, which is good; avoid reintroducing noisy status.

Plan:
- Preserve list readability first.
- Move share/print variants into overflow if more than two actions are visible.
- Keep date/mode filters as segmented/dropdown, not repeated command buttons.

### Tickets

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketListSupport.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/tickets/*ContractsTest.kt`

Problems:
- Ticket official mixes status, lottery badges, duplicate/pay/void actions, and detail content.
- Lookup rows expose multiple actions in row space.

Plan:
- Keep ticket status as badge.
- Keep one main action visible per mode:
  - Search mode: `Abrir`.
  - Duplicate mode: `Duplicar`.
  - Pay mode: `Pagar`.
  - Void mode: `Anular`.
- Move secondary actions to overflow.
- Use dropdowns for filters, not button grids.

### Recargas

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/recharge/RecargasUiContractsTest.kt`

Problems:
- Provider, paquete, resumen, historial, and tope can feel like unrelated panels.
- Some panels are operational; others are support information.

Plan:
- Order the screen by cashier workflow:
  - Number/provider entry.
  - Package selector.
  - Confirm/send.
  - Today history collapsed below.
- Use dropdown for provider if providers exceed 3.
- Keep tope as a badge/summary line, not a whole panel unless editable.

### Admin Dashboard / Monitor / Limits

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminUiContractsTest.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

Problems:
- Dashboard has multiple shortcut sections and metrics; monitor is likely too dense for POS.
- Limits repeats concepts also present in Cajeros.

Plan:
- Dashboard should show:
  - summary metrics,
  - 4 critical actions max,
  - recent tickets.
- Move secondary actions to overflow or a lower management list.
- Admin Monitor should use filters and list rows, not many always-visible controls.
- Admin Limits should become a focused advanced screen; common cashier limits should live under Cajeros.

### Master

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`

Problems:
- Master dashboard contains bank management, cloud sync, credentials, recharge, and technical status.
- Technical server controls compete with daily admin controls.

Plan:
- Split visually into:
  - `Bancas`.
  - `Servidor`.
  - `Credenciales`.
  - `Recargas`.
- Use rows with overflow actions for each bank.
- Keep technical status collapsed unless there is an error.

### Login

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/login/LoginUiContractsTest.kt`

Problems:
- Login is less risky, but should stay simple.

Plan:
- Keep one primary button.
- Password visibility remains an icon button.
- Avoid extra badges except role/session state.

### Printer

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/printer/PrinterContractsTest.kt`

Problems:
- Printer settings can become too explanatory.

Plan:
- Keep actions grouped by:
  - `Impresora`.
  - `Conexión`.
  - `Prueba`.
- Use status badges for connected/disconnected.
- Use switches for binary options, dropdowns for profiles.

## Node/Production Examination

Run these before release:

- [ ] `npm run check`
  - Expected: `Resumen: 0 fallo(s)`.
- [ ] `npm run release:check`
  - Expected: no failing pre-release checks.
- [ ] `node tools/qa/production-stability-suite.mjs`
  - Expected: JSON summary without critical failures.
- [ ] `node tools/qa/pick-mode-results-smoke.mjs`
  - Expected: Pick mode and results checks pass.
- [ ] `node tools/qa/real-flow-smoke.mjs`
  - Expected: core login/sale/sync flow passes if real credentials are configured.
- [ ] `node tools/real-server-smoke.mjs`
  - Expected: server functions respond; redact tokens in logs.

Run these Kotlin checks for UI work:

- [ ] `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.common.NativeChromeContractsTest"`
- [ ] `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.users.UserAccountsFormattingTest"`
- [ ] `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.admin.AdminConfigContractsTest"`
- [ ] `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.finance.FinanceUiContractsTest"`
- [ ] `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest"`
- [ ] `./gradlew.bat --no-daemon :app:assembleDebug`

## Execution Order

### Task 1: Shared UI Vocabulary

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/common/NativeChromeContractsTest.kt`

- [ ] Add contracts for command hierarchy.
- [ ] Add tests proving primary/secondary/overflow/choice labels are classified.
- [ ] Add wrappers without changing screen behavior.
- [ ] Run common UI tests.

### Task 2: Configuración Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminConfigContractsTest.kt`

- [ ] Write tests for separate `Resultados manuales` and `Sistema` sections.
- [ ] Move manual result editor out of `Sistema`.
- [ ] Convert date buttons to segmented choices.
- [ ] Run admin config tests.

### Task 3: Cajeros Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/users/UserAccountsFormattingTest.kt`

- [ ] Write tests for four admin tasks: Cajeros, Límites, Modo venta, Premios.
- [ ] Replace tab-looking navigation with task selector/list.
- [ ] Hide account cards unless a cashier is selected.
- [ ] Run user accounts tests.

### Task 4: Finanzas Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceReportsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/finance/FinanceUiContractsTest.kt`

- [ ] Write tests for single filter band and export overflow.
- [ ] Replace repeated export buttons with one export menu.
- [ ] Replace manual range command buttons with field selectors.
- [ ] Run finance tests.

### Task 5: Tickets and Results Cleanup

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt`
- Test: ticket/results contract tests.

- [ ] Write tests for one visible primary row action per mode.
- [ ] Move secondary actions to overflow.
- [ ] Keep status badges status-only.
- [ ] Run ticket/results tests.

### Task 6: Admin, Master, Recargas, Printer Pass

**Files:**
- Modify listed section files above.
- Test matching contract tests.

- [ ] Apply the same rules section by section.
- [ ] Keep each screen under 4 top-level panels on POS where practical.
- [ ] Collapse technical sections when no issue exists.
- [ ] Run affected Kotlin tests.

### Task 7: Production Examination

**Files:**
- No code changes unless checks reveal bugs.

- [ ] Run Node checks listed above.
- [ ] Run `:app:assembleDebug`.
- [ ] Record failures and fix only validated issues.

## Scope Guard

Do not redesign sale general. `SalesActivity.kt` may only change where `AdminSystemModeConfig.posLiteEnabled` is true or where a contract proves normal sale behavior remains unchanged.
