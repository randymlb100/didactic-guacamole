# Recargas Rapidas Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe Recargas Rapidas bridge for mobile recargas and paqueticos in LotteryNet.

**Architecture:** Add focused provider mapping and API-contract files under `core/recharge/recargasrapidas`, then extend the existing native recargas UI contracts to support recarga and paquetico modes. Keep current local persistence as the visible audit history and avoid live sales without explicit credentials and confirmation.

**Tech Stack:** Kotlin, Android Compose, JUnit4, existing SharedPreferences repositories, local Android assets.

---

### Task 1: Provider Catalog And Endpoints

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/recharge/recargasrapidas/RecargasRapidasModels.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/recharge/recargasrapidas/RecargasRapidasContractsTest.kt`

- [ ] Write failing tests for provider mappings, minimum amounts, logo asset names, endpoint paths, and phone sanitization.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests "*RecargasRapidasContractsTest"` and confirm the new tests fail because the model file does not exist.
- [ ] Implement minimal provider catalog and endpoint constants.
- [ ] Run the same test and confirm it passes.

### Task 2: Record Status Fields

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/model/RechargeModels.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/storage/LocalRechargeRepository.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/storage/RechargeRepositoryContractsTest.kt`

- [ ] Write failing tests that a recharge record can persist `productType`, `status`, and `providerReference`.
- [ ] Run the storage test and confirm failure on missing fields.
- [ ] Extend `RechargeRecord` with defaulted fields and JSON persistence.
- [ ] Run the storage test and confirm it passes.

### Task 3: UI Contracts

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/recharge/RecargasUiContractsTest.kt`

- [ ] Write failing tests for modes `RECARGA` and `PAQUETICO`, provider list containing Claro/Altice/Viva/Digicel/Natcom only, quick amounts respecting provider minimums, and local logo assets.
- [ ] Run the UI contract test and confirm failure.
- [ ] Add pure helpers and data needed by the UI while preserving existing layout density.
- [ ] Run the UI contract test and confirm it passes.

### Task 4: Build Verification

**Files:**
- No extra files.

- [ ] Run `.\gradlew.bat testDebugUnitTest`.
- [ ] Run `.\gradlew.bat assembleDebug`.
- [ ] Report exact pass/fail output and remaining risks.
