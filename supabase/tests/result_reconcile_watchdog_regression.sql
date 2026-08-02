select
  'reject_invalid_owner_aliases' as test,
  not ('null' = any(public.lotterynet_ticket_owner_aliases(t)))
    and not ('undefined' = any(public.lotterynet_ticket_owner_aliases(t))) as passed
from public.tickets t
where id = '2c5bf478-901f-4679-af20-b0c43e6ac1aa';

select
  'single_snapshot_sync_path' as test,
  position(
    'lotterynet.skip_ticket_owner_auto_sync'
    in pg_get_functiondef('public.lotterynet_reconcile_ticket_prize_v2(uuid)'::regprocedure)
  ) > 0
  and position(
    'lotterynet.skip_ticket_owner_auto_sync'
    in pg_get_functiondef('public.lotterynet_sync_winner_payload_from_ticket()'::regprocedure)
  ) > 0 as passed;

select
  'watchdog_bounded' as test,
  command like '%statement_timeout%'
    and command like '%lock_timeout%' as passed
from cron.job
where jobid = 6;
