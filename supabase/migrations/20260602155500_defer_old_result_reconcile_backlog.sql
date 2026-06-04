begin;

update public.result_reconcile_jobs
set status = 'deferred_backfill',
    completed_at = coalesce(completed_at, now()),
    locked_at = null,
    last_error = 'Deferred old backlog to protect active-day result delivery.'
where status in ('pending', 'failed')
  and coalesce(public.lotterynet_result_day_key_to_date(result_day_key), date '1900-01-01')
      < (now() at time zone 'America/Santo_Domingo')::date - interval '1 day';

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
  yesterday_day_key text := to_char((now() at time zone 'America/Santo_Domingo')::date - interval '1 day', 'DD-MM-YYYY');
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
    and locked_at < now() - interval '5 minutes'
    and result_day_key in (current_day_key, yesterday_day_key);

  update public.result_reconcile_jobs
  set status = 'pending',
      locked_at = null,
      last_error = coalesce(last_error, '') || ' | Watchdog retried failed job.'
  where status = 'failed'
    and attempts < 5
    and result_day_key in (current_day_key, yesterday_day_key);

  for day_key in
    select result_day_key
    from (
      select current_day_key as result_day_key, 0 as priority
      union all
      select yesterday_day_key as result_day_key, 1 as priority
    ) candidates
    order by priority
  loop
    begin
      process_result := public.lotterynet_process_result_reconcile_jobs_for_day(
        day_key,
        least(greatest(coalesce(p_job_limit, 5), 1), 2),
        least(greatest(coalesce(p_ticket_limit, 250), 1), 50)
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
    and result_day_key in (current_day_key, yesterday_day_key);

  select count(*)::int into running_count
  from public.result_reconcile_jobs
  where status = 'running'
    and result_day_key in (current_day_key, yesterday_day_key);

  select count(*)::int into failed_count
  from public.result_reconcile_jobs
  where status = 'failed'
    and result_day_key in (current_day_key, yesterday_day_key);

  select count(*)::int into stale_running_count
  from public.result_reconcile_jobs
  where status = 'running'
    and locked_at < now() - interval '5 minutes'
    and result_day_key in (current_day_key, yesterday_day_key);

  select coalesce(extract(epoch from max(now() - created_at))::int, 0)
  into max_pending_age
  from public.result_reconcile_jobs
  where status = 'pending'
    and result_day_key in (current_day_key, yesterday_day_key);

  select max(updated_at) into newest_result
  from public.result_draws
  where result_date >= (now() at time zone 'America/Santo_Domingo')::date - interval '1 day';

  if failed_count > 0 or stale_running_count > 0 or max_pending_age > 1200 then
    health_status := 'critical';
  elsif pending_count > 0 or running_count > 0 or max_pending_age > 300 then
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

revoke all on function public.lotterynet_results_prize_watchdog(int, int) from public, anon, authenticated;
grant execute on function public.lotterynet_results_prize_watchdog(int, int) to service_role;

comment on function public.lotterynet_results_prize_watchdog(int, int)
is 'Runs bounded current/yesterday prize reconciliation and defers old backlog so active result delivery stays responsive.';

commit;
