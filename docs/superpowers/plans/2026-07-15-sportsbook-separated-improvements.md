# Sportsbook Separated Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mejorar Deportes como módulo independiente, con filtros, estados, ticket, seguridad y liquidación sin mezclar lotería normal.

**Architecture:** Mantener `SportsbookActivity`, modelos deportivos y Edge Functions `sports_*` aislados de tickets de lotería. La app solo consume cartelera cacheada y crea tickets deportivos mediante Edge Functions autenticadas; el servidor valida cuota, permisos, límites e idempotencia.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Supabase Edge Functions/Deno, PostgreSQL, Node.js smoke tests y tests Kotlin contractuales.

## Global Constraints

- No modificar `TicketSummaryActivity`, `TicketOfficialActivity`, `TicketLookupActivity` ni funciones `create-ticket-v2`.
- No reutilizar tablas `lotterynet_tickets` para Deportes.
- Mantener `sports_tickets`, `sports_ticket_legs`, `sports_settlements` y `sports_audit_log` separados.
- No ejecutar Gradle salvo autorización explícita; validar primero con diff y Node smoke tests.
- Toda venta, cobro y liquidación debe permanecer server-first y autenticada.

---

### Task 1: Filtros y estados de cartelera deportiva

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivityContractsTest.kt`
- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`

**Interfaces:**
- Preserve `filterSportsbookBoardGames(games, selectedLeague, selectedStatus)` compatibility.
- Add only sports-specific filter helpers for sport, date and market availability.

- [ ] **Step 1: Add failing Kotlin contracts** for sport/date/open-market filtering and explicit unavailable states.
- [ ] **Step 2: Run only static/Node contract validation** and confirm the new contracts fail before implementation.
- [ ] **Step 3: Implement the minimum sports-only filter helpers** and expose them through the existing `SportsbookBoardFilterSheet`.
- [ ] **Step 4: Add Material 3 semantic states**: open, suspended, started and closed, with text plus color.
- [ ] **Step 5: Run the sportsbook Node smoke test and `git diff --check`.**

### Task 2: Navigation and visual hierarchy

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`

- [ ] **Step 1:** Lock role-specific tabs so Master sees only global configuration and business roles see operational sports tabs.
- [ ] **Step 2:** Replace only the sports tab presentation with Material 3 tab semantics or a documented equivalent; do not alter app-wide navigation.
- [ ] **Step 3:** Centralize sports button tones: primary sale/save, danger block/void, success enable/paid, secondary clear/cancel, warning suspended/expiring.
- [ ] **Step 4:** Add contracts proving no sports UI references lottery ticket tables or lottery sale destinations.
- [ ] **Step 5:** Run Node smoke tests and diff validation.

### Task 3: Sports ticket UX and sale safety

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/model/SportsbookModels.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookTicketRemoteStore.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivityContractsTest.kt`
- Test: `tools/qa/sportsbook-live-flow-smoke.mjs`

- [ ] **Step 1:** Add contracts for same-event market conflicts, stale odds, duplicate submit and disabled submit state.
- [ ] **Step 2:** Implement only sports ticket validation and presentation helpers.
- [ ] **Step 3:** Show individual odds, combined odds, potential payout, stake limits and server validation status.
- [ ] **Step 4:** Preserve `clientRequestId` idempotency and server-first creation.
- [ ] **Step 5:** Run sports live-flow smoke tests without touching lottery ticket flows.

### Task 4: Server authorization and atomic sale

**Files:**
- Modify: `supabase/functions/create-sports-ticket/index.ts`
- Modify: `supabase/functions/get-sports-tickets/index.ts`
- Modify: `supabase/functions/pay-sports-ticket/index.ts`
- Modify: `supabase/functions/settle-sports-ticket/index.ts`
- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`

- [ ] **Step 1:** Add contract checks requiring JWT identity, role, admin scope and cashier scope from server-resolved data.
- [ ] **Step 2:** Preserve `verify_jwt = true` and reject missing/ambiguous actor metadata for money actions.
- [ ] **Step 3:** Move ticket-plus-legs creation toward one database transaction/RPC or enforce rollback on every failed leg insert.
- [ ] **Step 4:** Keep conditional payment `status = won` and conditional settlement `status = pending`.
- [ ] **Step 5:** Add duplicate payment, cross-business access and orphan-ticket smoke cases.

### Task 5: Sports limits, settlement and operations

**Files:**
- Modify/create sports-only migrations under `supabase/migrations/`
- Modify: `supabase/functions/create-sports-ticket/index.ts`
- Modify: `supabase/functions/settle-sports-ticket/index.ts`
- Modify: `supabase/functions/pay-sports-ticket/index.ts`
- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`

- [ ] **Step 1:** Define independent limits for ticket, selection, event, market, cashier, business and potential payout.
- [ ] **Step 2:** Validate cumulative event exposure before accepting a ticket.
- [ ] **Step 3:** Prevent payment after void, duplicate payment or cross-business lookup.
- [ ] **Step 4:** Record actor, role, admin, cashier, owner, odds and settlement result in sports audit records.
- [ ] **Step 5:** Validate RLS and Edge Function authentication contracts.

### Task 6: Final verification

**Files:**
- Modify: `tools/qa/sportsbook-ui-contract.node.test.mjs`
- Add: `docs/superpowers/sportsbook-production-readiness-audit.md` updates only after verified changes

- [ ] **Step 1:** Run all sportsbook Node smoke tests.
- [ ] **Step 2:** Run `git diff --check` on sports-only files.
- [ ] **Step 3:** Confirm no changed file references lottery ticket tables or lottery sale APIs.
- [ ] **Step 4:** Report any remaining issue without claiming Gradle/build success.
