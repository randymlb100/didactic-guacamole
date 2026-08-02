# Ticket snapshot write-path backup — 2026-06-18

Production project: `unhoulkujbtsypccpirc`

Before deployment:

- `get-ticket-list` production version: `33`
- Active snapshot triggers:
  - `ln_protect_ticket_owner_snapshot_trigger`
  - `preserve_terminal_ticket_state`
  - `lotterynet_broadcast_ticket_owner_touch`
- Baseline snapshot upsert statement:
  - calls: `35,884`
  - mean: `686.57 ms`
  - max: `7,985.14 ms`
  - total: `24,636,984.47 ms`
- Snapshot table:
  - live rows: `92`
  - updates: `86,392`
  - dead rows: `45`
- Largest measured payload:
  - owner: `ADM-163C38`
  - bytes: `238,973`
  - tickets: `704`
  - deleted IDs: `493`

Database rollback:

`supabase/migrations/rollback/20260618143000_optimize_ticket_snapshot_write_path.rollback.sql`

Edge rollback:

Redeploy version 33 source, replacing the RPC call with the prior direct
`lotterynet_tickets_by_owner` PostgREST upsert. No public request or response
shape changed in the optimized version.

Post-deploy verification:

- Active Edge version: `35`
- Largest-owner changed-write benchmark: `287.262 ms`
- Initial changed-write benchmark: approximately `6,350 ms`
- Reduction in benchmark latency: approximately `95.5%`
- Anonymous `updated-at`: `200`, `degraded=false`
- Anonymous upsert: `401`
- Waiting database locks after verification: `0`
- Probe rows remaining after rollback-based tests: `0`
