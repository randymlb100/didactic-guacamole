# Ticket Snapshot Write Amplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce ticket snapshot write amplification and timeout risk while preserving all production ticket-state contracts.

**Architecture:** Keep the public snapshot API stable, optimize the quadratic database trigger, serialize and deduplicate owner writes through a service-only RPC, and retain Android uploads only for offline/tombstone reconciliation. Every production mutation has a checked-in rollback.

**Tech Stack:** PostgreSQL/PLpgSQL, Supabase Edge Functions/Deno, Kotlin Android, Node contract tests.

---

### Task 1: Freeze production contracts and rollback

**Files:**
- Create: `docs/backups/ticket-snapshot-write-path-backup-2026-06-18.sql`
- Create: `supabase/migrations/20260618143000_optimize_ticket_snapshot_write_path.sql`
- Create: `supabase/migrations/rollback/20260618143000_optimize_ticket_snapshot_write_path.rollback.sql`

- [ ] Capture current function, trigger, and index definitions from production.
- [ ] Store executable rollback SQL locally.
- [ ] Verify rollback restores both trigger functions and removes only newly introduced objects.

### Task 2: Add failing database contract tests

**Files:**
- Create: `tools/qa/ticket-snapshot-write-amplification-contract.node.test.mjs`

- [ ] Assert the migration creates `lotterynet_upsert_ticket_owner_snapshot`.
- [ ] Assert the RPC uses a transaction advisory lock and semantic no-op predicate.
- [ ] Assert terminal-state preservation builds one previous-ticket map and contains no per-ticket `jsonb_array_elements(previous_tickets)` scan.
- [ ] Assert the missing partial `legacy_ticket_id` index is present.
- [ ] Run `node --test tools/qa/ticket-snapshot-write-amplification-contract.node.test.mjs` and confirm failure before implementation.

### Task 3: Optimize and serialize database writes

**Files:**
- Modify: `supabase/migrations/20260618143000_optimize_ticket_snapshot_write_path.sql`

- [ ] Replace the quadratic terminal-state implementation with a set-based CTE.
- [ ] Add `tickets_legacy_ticket_id_active_idx`.
- [ ] Add service-only RPC with `pg_advisory_xact_lock(hashtextextended(...))`.
- [ ] Revoke public execution and grant only `service_role`.
- [ ] Run the database contract test and confirm it passes.

### Task 4: Route Edge writes through the RPC

**Files:**
- Modify: `supabase/functions/get-ticket-list/index.ts`
- Modify: `tools/qa/ticket-snapshot-write-amplification-contract.node.test.mjs`

- [ ] Add a failing assertion that direct table upsert is absent from the action path.
- [ ] Replace direct PostgREST upsert with `admin.rpc("lotterynet_upsert_ticket_owner_snapshot", ...)`.
- [ ] Preserve the existing stable-payload early return and cache update.
- [ ] Run Edge/auth/ticket contract tests.

### Task 5: Preserve Android low-write behavior

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Test: `tools/qa/ticket-hydrate-readonly-contract.node.test.mjs`
- Test: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketSyncContractsTest.kt`

- [ ] Verify hydration never uploads.
- [ ] Verify flush uploads only pending offline tickets or missing tombstones.
- [ ] Verify snapshot size remains bounded.
- [ ] Run focused Node and Gradle tests.

### Task 6: Deploy with rollback gates

**Files:**
- Production database and `get-ticket-list` Edge Function.

- [ ] Record pre-deploy `pg_stat_statements`, snapshot sizes, trigger definitions, and active locks.
- [ ] Apply the database migration transaction.
- [ ] Verify function privileges, trigger presence, index validity, and RPC no-op behavior.
- [ ] Deploy `get-ticket-list`.
- [ ] Run anonymous stamp, authenticated fetch/upsert, paid/winner/deleted integrity, and no-op probes.
- [ ] Compare latency/errors immediately; roll back on any failed correctness gate.

