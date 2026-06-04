begin;

create or replace function public.lotterynet_process_result_reconcile_jobs_for_day(
  p_result_day_key text,
  p_job_limit int default 12,
  p_ticket_limit int default 500
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  job_row record;
  ticket_row record;
  processed_jobs int := 0;
  processed_tickets int := 0;
  continued_jobs int := 0;
  skipped_locked boolean := false;
  job_tickets int;
  job_limit int;
  ticket_limit int;
  day_aliases text[];
  day_iso text;
  day_legacy text;
  day_date date;
  lock_key bigint;
begin
  lock_key := hashtextextended('lotterynet_result_reconcile:' || coalesce(p_result_day_key, ''), 0);
  if not pg_try_advisory_xact_lock(lock_key) then
    return jsonb_build_object(
      'ok', true,
      'dayKey', p_result_day_key,
      'skipped', true,
      'reason', 'result reconcile already running for this day',
      'processedJobs', 0,
      'processedTickets', 0,
      'continuedJobs', 0
    );
  end if;

  job_limit := least(greatest(coalesce(p_job_limit, 12), 1), 3);
  ticket_limit := least(greatest(coalesce(p_ticket_limit, 500), 1), 75);
  day_aliases := public.lotterynet_ticket_date_aliases(p_result_day_key);
  day_iso := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{4}-\d{2}-\d{2}$'
    limit 1
  );
  day_legacy := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{2}-\d{2}-\d{4}$'
    limit 1
  );
  day_date := nullif(day_iso, '')::date;

  for job_row in
    select *
    from public.result_reconcile_jobs
    where status = 'pending'
      and result_day_key = any(day_aliases)
    order by created_at
    limit job_limit
    for update skip locked
  loop
    begin
      job_tickets := 0;

      update public.result_reconcile_jobs
      set status = 'running',
          locked_at = now(),
          attempts = attempts + 1,
          last_error = null
      where id = job_row.id;

      for ticket_row in
        select distinct t.id
        from public.ticket_items ti
        join public.tickets t on t.id = ti.ticket_id
        where (
            (day_date is not null and t.draw_date_real = day_date)
            or (day_legacy is not null and t.legacy_day_key = day_legacy)
            or (day_iso is not null and t.legacy_day_key = day_iso)
            or (day_legacy is not null and t.draw_date = day_legacy)
            or (day_iso is not null and t.draw_date = day_iso)
          )
          and (
            job_row.lottery_legacy_id is null
            or ti.lottery_legacy_id = job_row.lottery_legacy_id
            or ti.secondary_lottery_legacy_id = job_row.lottery_legacy_id
          )
          and t.deleted_at is null
          and t.voided_at is null
          and t.invalidated_at is null
          and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
        order by t.id
        limit ticket_limit
      loop
        perform public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
        processed_tickets := processed_tickets + 1;
        job_tickets := job_tickets + 1;
      end loop;

      if job_tickets >= ticket_limit then
        update public.result_reconcile_jobs
        set status = 'pending',
            locked_at = null,
            last_error = 'Requeued after bounded ticket limit; continuing in next pass.'
        where id = job_row.id;
        continued_jobs := continued_jobs + 1;
      else
        update public.result_reconcile_jobs
        set status = 'completed',
            completed_at = now(),
            locked_at = null,
            last_error = null
        where id = job_row.id;
      end if;

      processed_jobs := processed_jobs + 1;
    exception when others then
      update public.result_reconcile_jobs
      set status = 'failed',
          locked_at = null,
          last_error = sqlerrm
      where id = job_row.id;
    end;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'dayKey', p_result_day_key,
    'skipped', skipped_locked,
    'processedJobs', processed_jobs,
    'processedTickets', processed_tickets,
    'continuedJobs', continued_jobs,
    'jobLimit', job_limit,
    'ticketLimit', ticket_limit
  );
end;
$$;

do $$
begin
  perform cron.unschedule('lotterynet-results-prize-watchdog');
exception when others then
  null;
end;
$$;

select cron.schedule(
  'lotterynet-results-prize-watchdog',
  '*/10 * * * *',
  $$
  select public.lotterynet_results_prize_watchdog(1, 25);
  $$
);

revoke all on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) from public, anon, authenticated;
grant execute on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) to service_role;

comment on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int)
is 'Processes result prize jobs by day with an advisory day lock and bounded batches to avoid overlapping cron pressure.';

commit;
