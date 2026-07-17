# Perfil Master Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reorganize the complete Perfil Master and make recharge funds server-authoritative without changing production business flows.

**Architecture:** Preserve `MasterDashboardActivity` as the entry point initially, extract pure fund/state contracts first, then replace the mixed card UI with responsibility-based Material 3 sections. All writes remain explicit events and server-confirmed; existing local repositories and Realtime coordination remain consumers of the same user payload fields.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing `UserAccount`, `LocalUsersRepository`, `SupabaseUsersRemoteStore`, Node.js contract tests, existing Android unit tests.

## Global Constraints

- Do not change ticket sales, ticket validation, lottery limits, prize flow, or existing Realtime behavior.
- Keep `recargasAssignedBalance` and `recargasBalance` as separate persisted fields.
- Never reset available balance on day change or UI refresh.
- Only an explicit Replace Fund action may set assigned and available to the same new amount.
- Do not edit unrelated dirty files or revert existing user changes.
- Do not add polling for the redesigned screen.

### Task 1: Protect fund semantics with pure contracts

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/master/MasterRechargeFundPolicy.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/master/MasterRechargeFundUpdate.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/master/MasterRechargeFundPolicyTest.kt`
- Test: `tools/qa/master-fund-server-first-contract.node.test.mjs`

**Interfaces:**
- Produce `MasterRechargeFundSnapshot(assigned, available, consumed)`.
- Produce `replaceFund(current, enabled, amount)` and `addBalance(current, amount)` pure transformations.
- Preserve the existing coordinator signature for compatibility.

- [ ] Add failing tests for assigned/available separation, no day reset, and replace-vs-add behavior.
- [ ] Implement the policy with money rounded to cents and non-negative values.
- [ ] Update coordinator tests so failed remote writes restore the previous snapshot.
- [ ] Run the focused Kotlin and Node contract tests and require PASS.

### Task 2: Stop the Master editor from using remaining balance as the fund draft

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`

**Interfaces:**
- The fund draft is initialized from `rechargesAssignedBalance`, not `rechargesBalance`.
- The card exposes explicit Replace Fund and Add Balance events.

- [ ] Add a contract test asserting the draft source and explicit action labels.
- [ ] Change `rememberSaveable` keys and callbacks without changing the existing remote update path.
- [ ] Add confirmation text showing old assigned, old available, and new amount.
- [ ] Run focused Master UI contract tests.

### Task 3: Separate Master profile state from visual sections

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterProfileUiState.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterProfileSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`

**Interfaces:**
- `MasterProfileSection` contains `SUMMARY`, `BANKS`, `RECHARGES`, `SECURITY`, `SERVER`, `AUDIT`.
- `MasterProfileUiState` exposes immutable metrics, selected section, busy state, and last operation result.

- [ ] Add section navigation tests for all six destinations.
- [ ] Move section selection and operation status into the screen state holder.
- [ ] Keep data loading and mutations delegated to the current repositories/coordinators.
- [ ] Run the Master UI contract suite.

### Task 4: Build the Material 3 summary and responsibility cards

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterProfileCards.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`

- [ ] Implement summary metrics using `MaterialTheme` tokens and accessible labels.
- [ ] Implement separate cards for identity, banks, recharge fund, credentials, server status, and audit.
- [ ] Keep actions out of purely informational metric cards.
- [ ] Add compact layout rules for narrow POS screens and multi-column layout for larger widths.
- [ ] Run static contract tests and inspect the affected Compose code for hard-coded colors/sizes.

### Task 5: Organize bank and administrator details

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterBankDetailsSheet.kt`

- [ ] Move secondary bank actions into a detail sheet.
- [ ] Keep cashier management, limits, credentials, and audit actions scoped to the selected bank.
- [ ] Preserve aliases and IDs in payloads while showing human-readable names first.
- [ ] Test that bank actions use the selected administrator ID and cannot target another bank.

### Task 6: Add security, server status, and audit sections

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterSecuritySection.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterServerSection.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterAuditSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`

- [ ] Show credential actions with confirmation and redacted values.
- [ ] Show last synchronization and server confirmation without adding polling.
- [ ] Show operation result, actor, timestamps, old value, and new value where available.
- [ ] Add tests for success, rejection, rollback, and offline display states.

### Task 7: Regression validation

**Files:**
- Modify only tests if a regression is found.

- [ ] Run focused Master and recharge Kotlin tests.
- [ ] Run Node.js payload and UI contract tests.
- [ ] Run existing `testDebugUnitTest` and `assembleDebug` only after focused tests pass.
- [ ] Verify no server files or unrelated flows changed in the final diff.
- [ ] Review `git diff --stat` and `git diff --check` before handoff.
