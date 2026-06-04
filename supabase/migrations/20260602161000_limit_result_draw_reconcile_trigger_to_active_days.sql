begin;

create or replace function public.lotterynet_enqueue_result_draw_reconcile_job()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'DELETE' then
    return old;
  end if;

  if new.status <> 'published' or coalesce(new.number_raw, '') = '' then
    return new;
  end if;

  if new.result_date < (now() at time zone 'America/Santo_Domingo')::date - interval '1 day' then
    return new;
  end if;

  if tg_op = 'UPDATE'
    and old.source_hash is not distinct from new.source_hash
    and old.status is not distinct from new.status
    and old.number_raw is not distinct from new.number_raw then
    return new;
  end if;

  perform public.lotterynet_enqueue_result_reconcile_job(
    new.result_day_key,
    new.lottery_legacy_id,
    new.game
  );

  return new;
end;
$$;

update public.result_reconcile_jobs
set status = 'deferred_backfill',
    completed_at = coalesce(completed_at, now()),
    locked_at = null,
    last_error = 'Deferred old result_draws trigger backlog to protect active-day delivery.'
where status in ('pending', 'failed')
  and coalesce(public.lotterynet_result_day_key_to_date(result_day_key), date '1900-01-01')
      < (now() at time zone 'America/Santo_Domingo')::date - interval '1 day';

revoke all on function public.lotterynet_enqueue_result_draw_reconcile_job()
from public, anon, authenticated;
grant execute on function public.lotterynet_enqueue_result_draw_reconcile_job()
to service_role;

comment on function public.lotterynet_enqueue_result_draw_reconcile_job()
is 'Enqueues prize reconciliation for active result days only; older result updates are deferred to explicit backfill.';

commit;
