-- Non-mutating smoke checks for result/prize v2.
-- Run with a privileged SQL session against the target Supabase project.

select 'result_draws' as table_name, count(*)::bigint as rows from public.result_draws
union all select 'ticket_prize_items', count(*)::bigint from public.ticket_prize_items
union all select 'result_reconcile_jobs', count(*)::bigint from public.result_reconcile_jobs;

select status, count(*)::bigint as jobs
from public.result_reconcile_jobs
group by status
order by status;

select
  '28-05-2026' as result_day_key,
  jsonb_array_length(public.lotterynet_result_draws_payload('28-05-2026')) as payload_count,
  count(*) filter (where result_day_key = '28-05-2026')::bigint as normalized_rows
from public.result_draws;

with winners as (
  select id, ticket_code, status, payout_amount, created_at
  from public.tickets
  where deleted_at is null
    and voided_at is null
    and invalidated_at is null
    and (status in ('GANADOR','PAGADO') or payout_amount > 0)
  order by created_at desc nulls last
  limit 30
)
select
  ticket_code,
  status,
  payout_amount,
  (public.lotterynet_calculate_ticket_prize(id)->>'totalPrize')::numeric as old_prize,
  (public.lotterynet_calculate_ticket_prize_v2(id)->>'totalPrize')::numeric as v2_prize,
  public.lotterynet_calculate_ticket_prize_v2(id)->>'source' as v2_source
from winners
order by created_at desc;
