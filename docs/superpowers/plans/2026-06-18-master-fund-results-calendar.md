# MASTER Fund and Results Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Supabase authoritative for MASTER-managed recharge funds, confirm the exact persisted amount, roll back optimistic UI on failure, propagate updates to other phones, hide fund amounts from cashiers, and add a Material 3 modal DatePicker for historical results.

**Architecture:** A focused fund-update coordinator will perform an optimistic local update while retaining the previous account snapshot, invoke an authenticated Edge Function that upserts and reads back the canonical row, and accept success only when the returned amount equals the requested amount. Realtime signals will trigger a remote users snapshot refresh on other devices. Results keeps its existing date cache and loading behavior; only its current date sheet is upgraded with a Material 3 `DatePicker`.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, ViewModel/state holders, Supabase Edge Functions (Deno/TypeScript), Postgres/RLS, Supabase Realtime, JUnit, Gradle.

---

## File structure

- `app/src/main/java/com/lotterynet/pro/core/master/MasterRechargeFundUpdate.kt`: result model, exact-value comparison, optimistic update/rollback orchestration.
- `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`: authenticated fund update call and parsing of the canonical server response.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`: async save state, confirmation/error feedback, and remote refresh after realtime events.
- `app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt`: stable topic/subscription identity for MASTER fund changes.
- `supabase/functions/update-master-config/index.ts`: dedicated fund action that authorizes MASTER, persists, reads back, and returns the canonical amount.
- `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`: modal Material 3 DatePicker integrated with the current date selector.
- `app/src/test/java/com/lotterynet/pro/core/master/MasterRechargeFundUpdateTest.kt`: exact confirmation and rollback contracts.
- `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`: role visibility and status copy contracts.
- `app/src/test/java/com/lotterynet/pro/ui/results/ResultsActivityContractsTest.kt`: date conversion, future-date blocking, and picker selection contracts.
- `tools/qa/master-fund-server-first-contract.node.test.mjs`: Edge Function authorization/read-back/realtime contract.

### Task 1: Exact server confirmation and rollback

- [ ] Add failing tests proving `4_037.0` succeeds only when the server returns `4_037.0`, a mismatched response fails, and a network/server failure restores the complete previous `UserAccount`.
- [ ] Run `.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.master.MasterRechargeFundUpdateTest"` and verify the new tests fail because the coordinator does not exist.
- [ ] Implement `MasterRechargeFundUpdateCoordinator` with injected local writer and remote writer, retaining the previous account snapshot before optimistic mutation.
- [ ] Return a typed `Confirmed`, `Rejected`, or `RolledBack` result; compare money after normalization to integer cents to avoid floating-point mismatches.
- [ ] Re-run the focused test and verify it passes.

### Task 2: Server-authoritative fund endpoint

- [ ] Add a failing Node contract requiring an authenticated MASTER-only `update-recharge-fund` action, a response containing `requestedAmount`, `persistedAmount`, `confirmed`, `updatedAt`, and rejection of cashier actors.
- [ ] Run `node --test tools/qa/master-fund-server-first-contract.node.test.mjs` and verify it fails against the current generic endpoint.
- [ ] Extend `update-master-config` to validate the admin/bank identifier and non-negative finite amount, update the authoritative users payload/state, read it back in the same request, and return the canonical persisted amount.
- [ ] Ensure authorization uses the server-resolved actor role and never client-editable metadata.
- [ ] Emit the existing global users realtime signal after successful persistence so subscribed phones refresh from Supabase.
- [ ] Re-run the Node contract and verify it passes.

### Task 3: MASTER UI server-first behavior

- [ ] Add failing UI contract tests for “Guardando…”, “Servidor confirmó $4,037”, mismatch/error feedback, disabled duplicate submission, and cashier fund-amount visibility returning false.
- [ ] Run the focused `MasterUiContractsTest` and verify the tests fail.
- [ ] Replace the current local-save-plus-background-general-sync path for “Guardar fondo” with the coordinator.
- [ ] Keep the optimistic display while saving, disable the bank’s save control during the request, show exact server confirmation on success, and restore the previous account plus draft value on failure.
- [ ] Refresh the remote users snapshot when the global users realtime signal arrives or when the app resumes, preserving the existing foreground fallback.
- [ ] Re-run focused MASTER tests.

### Task 4: Cashier privacy boundary

- [ ] Add tests proving cashier-facing composables/contracts receive no assigned or available fund amount while MASTER/ADMIN management views retain it.
- [ ] Audit shell, recharge, report, and user-card presentation paths for direct `rechargesBalance` or `rechargesAssignedBalance` rendering.
- [ ] Route all fund labels through a role-aware presenter and replace cashier-visible amounts with operational wording such as “Recargas disponibles” or limit-only information.
- [ ] Keep server payloads scoped to operational needs; rely on authenticated endpoint authorization and RLS/Edge Function filtering rather than UI hiding alone.
- [ ] Run MASTER, recharge, shell, and report contract tests.

### Task 5: Material 3 historical DatePicker

- [ ] Add failing tests for converting `dd-MM-yyyy` result keys to/from UTC picker milliseconds, rejecting dates after today in `America/Santo_Domingo`, and accepting historical dates.
- [ ] Run focused results tests and verify the conversion helpers are missing.
- [ ] Add a modal `DatePickerDialog` launched from the existing “Cambiar” action, retain quick choices for Hoy/Ayer/Anteayer, and remove reliance on repeated previous-day tapping for long history.
- [ ] Set `selectableDates` so future dates cannot be chosen; on confirm, convert the selected picker value to the existing result date key and reuse the current cache/remote loading flow.
- [ ] Preserve compact/phone/POS layouts, 48dp interactive targets, Material typography/colors, and date accessibility descriptions.
- [ ] Run focused results tests.

### Task 6: Verification and production readiness

- [ ] Run `node --test tools/qa/master-fund-server-first-contract.node.test.mjs`.
- [ ] Run `.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.master.*" --tests "com.lotterynet.pro.ui.master.*" --tests "com.lotterynet.pro.ui.results.*" --tests "com.lotterynet.pro.ui.recharge.*" --tests "com.lotterynet.pro.ui.shell.*"`.
- [ ] Run `.\gradlew.bat :app:assembleDebug`.
- [ ] Inspect the final diff to ensure unrelated dirty-worktree changes were not overwritten.
- [ ] Verify the acceptance checklist: exact `4,037` read-back, rollback on failure, cross-device refresh, cashier amount hidden, historical DatePicker, and Supabase as source of truth.
