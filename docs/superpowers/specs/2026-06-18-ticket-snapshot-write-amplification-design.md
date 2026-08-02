# Ticket Snapshot Write Amplification Design

## Objective

Reduce production writes and `get-ticket-list` upsert latency without changing the public snapshot shape or weakening paid, winner, voided, deleted, offline, and multi-device protections.

## Confirmed cause

`lotterynet_tickets_by_owner` has few rows but receives tens of thousands of updates. Each update rewrites a large JSONB document and runs two full-snapshot `BEFORE` triggers. `lotterynet_preserve_terminal_ticket_state` also performs a repeated scan of the previous ticket array for every incoming ticket, producing quadratic work on large snapshots.

## Design

1. Preserve the existing `lotterynet_tickets_by_owner` schema and API response format.
2. Replace the quadratic terminal-state trigger implementation with a set-based implementation that builds the previous-ticket lookup once.
3. Keep the independent deletion/snapshot protection trigger for this rollout. Removing or merging it is deferred until production measurements prove the optimized path is stable.
4. Add the missing partial index on `tickets.legacy_ticket_id`, used by deleted-ID protection.
5. Add a service-only RPC that serializes writes by `owner_key`, skips semantically unchanged payloads, and performs the existing upsert when a real change exists.
6. Route `get-ticket-list` writes through the RPC while retaining the existing Edge merge and compatibility behavior.
7. Keep Android snapshot uploads limited to pending offline tickets or missing deletion tombstones. Hydration remains read-only.

## Rollout and rollback

- Capture production function and trigger definitions before mutation.
- Apply database changes inside one transaction.
- Deploy the Edge Function after database verification.
- Run contract tests and read-only production probes.
- Compare `pg_stat_statements`, active locks, errors, and snapshot integrity.
- Roll back the Edge Function first and database functions/index second if correctness or latency gates fail.

## Success gates

- Existing snapshot JSON contract remains unchanged.
- Paid/winner states cannot regress.
- Deleted tickets cannot reappear.
- Anonymous `updated-at` compatibility remains available.
- Upsert mean and maximum latency decrease materially.
- Duplicate/no-op writes are skipped.
- No new Sentry or database timeout burst appears after deployment.

