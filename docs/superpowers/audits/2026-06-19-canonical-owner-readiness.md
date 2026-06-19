# Canonical Ticket Owner Readiness

Date: 2026-06-19
Project: `unhoulkujbtsypccpirc`

## Production evidence captured before deployment

- Invalid snapshot row: `owner_key = 'null'`
- Snapshot tickets: `433`
- Snapshot SHA-256: `4ffe77aa4528ee911582626470dd1932ef5abd67ea80b1ef8380a5606094a08a`
- Official matches: `433`
- Unmatched identities: `0`
- Canonical administrator for all matched tickets: `ADM-163C38`

The invalid snapshot is therefore recoverable from authoritative normalized tickets. The prepared migration only copies it into `private.lotterynet_ticket_owner_snapshot_quarantine`; it does not delete the public row or modify `public.tickets`.

## Query-plan gate

The bounded administrator query for the Dominican day used:

- `tickets_server_created_at_idx`
- backward index scan
- estimated rows: `9`
- startup cost: `0.28`
- total cost: `7.73`

No additional index is justified by the current plan.

## Current production behavior

`get-ticket-list` version 40 is returning:

- bounded authenticated reads: HTTP 200, approximately 198–348 ms in the sampled logs;
- legacy unbounded reads and snapshot upserts: HTTP 503 in approximately 55–237 ms.

Those 503 responses are intentional fail-closed guards and are not PostgreSQL statement timeouts. The Android changes prepared in this work remove unbounded ticket hydration and full-owner snapshot writes from the new client flow.

## Local verification

- Deno owner canonicalization tests: 3 passed.
- Deno type checking for `get-ticket-list`: passed.
- Node ticket reconciliation/read/quarantine contracts: 12 passed.
- Android main-source compilation: passed.
- Android debug APK assembly: passed.
- Node ticket reconciliation/read/quarantine suite: 14 passed.
- Focused Android unit-test task is blocked by five unrelated pre-existing unresolved references in `SalesUiContractsTest.kt`.

## Release gate

Do not deploy until:

1. Android main-source compilation passes after the final change.
2. Edge function source is backed up from production.
3. Migration is applied and quarantine checksum is verified.
4. Edge function is deployed with its current JWT setting preserved.
5. One administrator and one cashier bounded request return the expected count.
6. Logs remain free of statement timeouts during the canary.
