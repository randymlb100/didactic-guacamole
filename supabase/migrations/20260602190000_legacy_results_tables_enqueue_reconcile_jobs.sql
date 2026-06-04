-- Keep legacy result table writes fast. Prize reconciliation must run through
-- result_reconcile_jobs, not inside the result insert/update transaction.

create or replace function public.lotterynet_enqueue_legacy_result_reconcile_jobs()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  source_name text;
begin
  if tg_op = 'UPDATE'
    and public.lotterynet_result_payload_fingerprint(old.payload) = public.lotterynet_result_payload_fingerprint(new.payload) then
    return new;
  end if;

  source_name := case
    when tg_table_name = 'lotterynet_pick_results_by_day' then 'pick'
    else 'lottery'
  end;

  perform public.lotterynet_upsert_result_draws_from_payload(
    new.result_date,
    coalesce(new.payload, '[]'::jsonb),
    source_name
  );

  return new;
end;
$$;

drop trigger if exists lotterynet_pick_results_reconcile_tickets
on public.lotterynet_pick_results_by_day;

drop trigger if exists lotterynet_results_reconcile_tickets
on public.lotterynet_results_by_day;

create trigger lotterynet_pick_results_reconcile_tickets
after insert or update of payload on public.lotterynet_pick_results_by_day
for each row
execute function public.lotterynet_enqueue_legacy_result_reconcile_jobs();

create trigger lotterynet_results_reconcile_tickets
after insert or update of payload on public.lotterynet_results_by_day
for each row
execute function public.lotterynet_enqueue_legacy_result_reconcile_jobs();

revoke all on function public.lotterynet_enqueue_legacy_result_reconcile_jobs() from public, anon, authenticated;
grant execute on function public.lotterynet_enqueue_legacy_result_reconcile_jobs() to service_role;

comment on function public.lotterynet_enqueue_legacy_result_reconcile_jobs()
is 'Legacy result table trigger that normalizes result rows and enqueues bounded prize jobs instead of scanning tickets inline.';
