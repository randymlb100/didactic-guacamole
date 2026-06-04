begin;

create table if not exists public.lotterynet_results_health_log (
  id uuid primary key default gen_random_uuid(),
  checked_at timestamptz not null default now(),
  status text not null,
  pending_jobs int not null default 0,
  running_jobs int not null default 0,
  failed_jobs int not null default 0,
  stale_running_jobs int not null default 0,
  max_pending_age_seconds int not null default 0,
  newest_result_at timestamptz,
  processed jsonb not null default '[]'::jsonb,
  notes text not null default ''
);

alter table public.lotterynet_results_health_log enable row level security;

create index if not exists lotterynet_results_health_log_checked_idx
on public.lotterynet_results_health_log (checked_at desc);

create or replace function public.lotterynet_results_prize_watchdog(
  p_job_limit int default 5,
  p_ticket_limit int default 250
)
returns jsonb
language plpgsql
security definer
set search_path = public, cron
as $$
declare
  day_key text;
  current_day_key text := to_char((now() at time zone 'America/Santo_Domingo')::date, 'DD-MM-YYYY');
  processed_payload jsonb := '[]'::jsonb;
  process_result jsonb;
  pending_count int := 0;
  running_count int := 0;
  failed_count int := 0;
  stale_running_count int := 0;
  max_pending_age int := 0;
  newest_result timestamptz;
  health_status text := 'ok';
  health_notes text := '';
begin
  update public.result_reconcile_jobs
  set status = 'pending',
      locked_at = null,
      last_error = coalesce(last_error, '') || ' | Watchdog released stale running job.'
  where status = 'running'
    and locked_at < now() - interval '5 minutes';

  update public.result_reconcile_jobs
  set status = 'pending',
      locked_at = null,
      last_error = coalesce(last_error, '') || ' | Watchdog retried failed job.'
  where status = 'failed'
    and attempts < 5
    and created_at >= now() - interval '2 days';

  for day_key in
    with pending_days as (
      select result_day_key, min(created_at) as oldest_pending
      from public.result_reconcile_jobs
      where status in ('pending', 'failed')
        and created_at >= now() - interval '12 hours'
        and coalesce(result_day_key, '') <> ''
      group by result_day_key
    ), candidates as (
      select current_day_key as result_day_key, 0 as priority, now() as oldest_pending
      union all
      select result_day_key, 1 as priority, oldest_pending
      from pending_days
      where result_day_key <> current_day_key
    )
    select result_day_key
    from candidates
    order by priority, oldest_pending
    limit 2
  loop
    begin
      process_result := public.lotterynet_process_result_reconcile_jobs_for_day(
        day_key,
        greatest(coalesce(p_job_limit, 5), 1),
        greatest(coalesce(p_ticket_limit, 250), 1)
      );
      processed_payload := processed_payload || jsonb_build_array(process_result);
    exception when others then
      processed_payload := processed_payload || jsonb_build_array(
        jsonb_build_object('ok', false, 'dayKey', day_key, 'error', sqlerrm)
      );
    end;
  end loop;

  select count(*)::int into pending_count
  from public.result_reconcile_jobs
  where status = 'pending'
    and created_at >= now() - interval '12 hours';

  select count(*)::int into running_count
  from public.result_reconcile_jobs
  where status = 'running';

  select count(*)::int into failed_count
  from public.result_reconcile_jobs
  where status = 'failed';

  select count(*)::int into stale_running_count
  from public.result_reconcile_jobs
  where status = 'running'
    and locked_at < now() - interval '5 minutes';

  select coalesce(extract(epoch from max(now() - created_at))::int, 0)
  into max_pending_age
  from public.result_reconcile_jobs
  where status = 'pending'
    and created_at >= now() - interval '12 hours';

  select max(updated_at) into newest_result
  from public.result_draws
  where result_date >= (now() at time zone 'America/Santo_Domingo')::date - interval '1 day';

  if failed_count > 0 or stale_running_count > 0 or max_pending_age > 600 then
    health_status := 'critical';
  elsif pending_count > 0 or running_count > 0 or max_pending_age > 180 then
    health_status := 'warn';
  end if;

  if newest_result is null then
    health_notes := 'No recent result_draws rows found for today/yesterday.';
  end if;

  insert into public.lotterynet_results_health_log(
    status,
    pending_jobs,
    running_jobs,
    failed_jobs,
    stale_running_jobs,
    max_pending_age_seconds,
    newest_result_at,
    processed,
    notes
  )
  values (
    health_status,
    pending_count,
    running_count,
    failed_count,
    stale_running_count,
    max_pending_age,
    newest_result,
    processed_payload,
    health_notes
  );

  delete from public.lotterynet_results_health_log
  where checked_at < now() - interval '14 days';

  return jsonb_build_object(
    'ok', health_status <> 'critical',
    'status', health_status,
    'pendingJobs', pending_count,
    'runningJobs', running_count,
    'failedJobs', failed_count,
    'staleRunningJobs', stale_running_count,
    'maxPendingAgeSeconds', max_pending_age,
    'newestResultAt', newest_result,
    'processed', processed_payload,
    'notes', health_notes
  );
end;
$$;

do $$
declare
  existing_job_id bigint;
begin
  select jobid into existing_job_id
  from cron.job
  where jobname = 'lotterynet-results-server-refresh'
  limit 1;

  if existing_job_id is not null then
    perform cron.alter_job(jobid, schedule, command, database, username, true)
    from cron.job
    where jobid = existing_job_id;
  end if;

  perform cron.unschedule('lotterynet-results-prize-watchdog');
exception when others then
  null;
end;
$$;

select cron.schedule(
  'lotterynet-results-prize-watchdog',
  '*/2 * * * *',
  $$
  select public.lotterynet_results_prize_watchdog(1, 100);
  $$
);

revoke all on table public.lotterynet_results_health_log from public, anon, authenticated;
revoke all on function public.lotterynet_results_prize_watchdog(int, int) from public, anon, authenticated;
grant execute on function public.lotterynet_results_prize_watchdog(int, int) to service_role;

comment on table public.lotterynet_results_health_log
is 'Internal health log for result prize reconciliation watchdog runs.';

comment on function public.lotterynet_results_prize_watchdog(int, int)
is 'Runs bounded result prize reconciliation and records queue health so pending winner jobs do not silently stall.';

commit;
