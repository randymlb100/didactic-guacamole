# Ticket Filters Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve ticket search and filter organization without changing ticket loading, remote synchronization, sales, or navigation flows.

**Architecture:** Keep date-range calculation and existing data flow intact. Hoist only filter intent into the ticket summary screen state, expose explicit filter events, and render search/date/secondary filters as focused Compose UI. Existing pure filtering and date functions remain the source of truth.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, ViewModel, StateFlow, existing ticket summary contracts and tests.

## Global Constraints

- Do not modify local/remote loading or synchronization behavior.
- Do not modify sales creation, payment, cancellation, or navigation behavior.
- Do not add dependencies.
- Preserve `America/Santo_Domingo` date semantics and the existing `fromDate/toDate` query contract.
- Validate with focused tests and static diff review; run no broad Gradle task unless explicitly requested.

### Task 1: Define filter state and pure filter contracts

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryViewModel.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketListSupport.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketSummaryStartupContractsTest.kt`

- [ ] Add a serializable-sized `TicketSummaryFilters` state containing query, period, exact date, owner scope, cashier, status, and play type.
- [ ] Add ViewModel event methods that update only filter state and a clear-filters event that preserves the existing default period.
- [ ] Keep the existing filtering and date-range functions unchanged except for small adapters needed by the UI.
- [ ] Add focused assertions for default filters and clear behavior.

### Task 2: Reorganize the Compose filter surface

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketListSupport.kt`

- [ ] Place the existing search control first.
- [ ] Add a compact date preset row with `Fecha exacta` opening the existing Material 3 date picker.
- [ ] Group cashier, owner, status, and play-type controls behind the existing filter affordance or a compact filter section.
- [ ] Show active filter chips and a clear action.
- [ ] Route all changes through the screen state/events while keeping the current query and synchronization callbacks intact.
- [ ] Keep touch targets at least 44 dp and use MaterialTheme typography/colors.

### Task 3: Verify filter behavior by diff and focused tests

**Files:**
- Modify: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketSummaryStartupContractsTest.kt`
- Inspect: `app/src/test/java/com/lotterynet/pro/ui/tickets/TicketLookupContractsTest.kt`

- [ ] Verify exact-date selection changes only the date filter/range input.
- [ ] Verify query, status, cashier, owner, and play-type filters compose without changing ticket persistence or sync contracts.
- [ ] Run only the focused test command if requested; otherwise perform static reference checks and report any pre-existing test issue separately.
- [ ] Review `git diff` and ensure no unrelated files are changed.
