# Winner Owner Snapshot Sync Backup - 2026-05-31

Context:
- Some tickets were correctly reconciled in `public.tickets` as `GANADOR` / `PAGADO` with `payout_amount > 0`.
- The Android app reads `public.lotterynet_tickets_by_owner` for realtime ticket lists.
- Investigation found owner snapshots where the same ticket stayed `active` or `totalPrize = 0`.

Backup / rollback notes:
- Before changing the sync function, capture the current definition with:

```sql
select pg_get_functiondef('public.lotterynet_sync_ticket_owner_payload(uuid)'::regprocedure);
```

- The previous implementation restricted snapshot updates to owner keys directly present on the `tickets` row. That missed username-based owner rows and stale duplicate owner rows that still contained the same ticket by legacy id or serial.

Rollback:
- Restore the function definition returned by the SQL above if this migration needs to be reverted.
