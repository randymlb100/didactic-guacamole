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

drop trigger if exists result_draws_enqueue_reconcile_job
on public.result_draws;

create trigger result_draws_enqueue_reconcile_job
after insert or update of source_hash, status, number_raw
on public.result_draws
for each row
execute function public.lotterynet_enqueue_result_draw_reconcile_job();

insert into public.result_reconcile_jobs(result_day_key, lottery_legacy_id, game)
select rd.result_day_key, rd.lottery_legacy_id, rd.game
from public.result_draws rd
where rd.status = 'published'
  and coalesce(rd.number_raw, '') <> ''
  and rd.result_date >= current_date - interval '2 days'
on conflict (result_day_key, coalesce(lottery_legacy_id, ''), coalesce(game, ''))
where status in ('pending', 'running')
do nothing;

revoke all on function public.lotterynet_enqueue_result_draw_reconcile_job()
from public, anon, authenticated;
grant execute on function public.lotterynet_enqueue_result_draw_reconcile_job()
to service_role;

comment on function public.lotterynet_enqueue_result_draw_reconcile_job()
is 'Enqueues prize reconciliation whenever result_draws receives a published draw, including direct manual override writes.';

commit;
