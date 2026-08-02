create schema if not exists private;

revoke all on schema private from public;
revoke all on schema private from anon;
revoke all on schema private from authenticated;

create table if not exists private.lotterynet_maintenance_runs (
  id uuid primary key default gen_random_uuid(),
  ran_at timestamptz not null default now(),
  dry_run boolean not null,
  result jsonb not null
);

revoke all on private.lotterynet_maintenance_runs from public;
revoke all on private.lotterynet_maintenance_runs from anon;
revoke all on private.lotterynet_maintenance_runs from authenticated;

create or replace function private.lotterynet_safe_maintenance(
  p_dry_run boolean default true,
  p_deleted_ticket_retention_days integer default 30,
  p_result_cache_retention_days integer default 21,
  p_ota_log_retention_days integer default 30,
  p_health_log_retention_days integer default 14,
  p_reconcile_job_retention_days integer default 14
)
returns jsonb
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_locked boolean;
  v_deleted_ticket_candidates integer := 0;
  v_deleted_tickets integer := 0;
  v_deleted_ticket_limit_reservations integer := 0;
  v_deleted_result_cache integer := 0;
  v_deleted_ota_logs integer := 0;
  v_deleted_health_logs integer := 0;
  v_deleted_reconcile_jobs integer := 0;
  v_result jsonb;
begin
  v_locked := pg_try_advisory_xact_lock(hashtextextended('lotterynet_safe_maintenance', 0));
  if not v_locked then
    return jsonb_build_object('ok', true, 'skipped', true, 'reason', 'maintenance_already_running');
  end if;

  p_deleted_ticket_retention_days := greatest(coalesce(p_deleted_ticket_retention_days, 30), 7);
  p_result_cache_retention_days := greatest(coalesce(p_result_cache_retention_days, 21), 7);
  p_ota_log_retention_days := greatest(coalesce(p_ota_log_retention_days, 30), 7);
  p_health_log_retention_days := greatest(coalesce(p_health_log_retention_days, 14), 7);
  p_reconcile_job_retention_days := greatest(coalesce(p_reconcile_job_retention_days, 14), 7);

  select count(*)
    into v_deleted_ticket_candidates
  from public.tickets t
  where t.deleted_at is not null
    and t.deleted_at < now() - make_interval(days => p_deleted_ticket_retention_days)
    and not exists (
      select 1
      from public.pagos p
      where p.ticket_id = t.id
    );

  if not p_dry_run then
    delete from public.ticket_limit_reservations r
    using public.tickets t
    where r.ticket_id = t.id
      and t.deleted_at is not null
      and t.deleted_at < now() - make_interval(days => p_deleted_ticket_retention_days)
      and not exists (
        select 1
        from public.pagos p
        where p.ticket_id = t.id
      );
    get diagnostics v_deleted_ticket_limit_reservations = row_count;

    delete from public.tickets t
    where t.deleted_at is not null
      and t.deleted_at < now() - make_interval(days => p_deleted_ticket_retention_days)
      and not exists (
        select 1
        from public.pagos p
        where p.ticket_id = t.id
      );
    get diagnostics v_deleted_tickets = row_count;

    delete from public.lotterynet_kv
    where (
        key like 'lot_results_cache_by_day:%'
        or key like 'pick_results_cache_by_day:%'
      )
      and upd < now() - make_interval(days => p_result_cache_retention_days);
    get diagnostics v_deleted_result_cache = row_count;

    delete from public.ota_update_logs
    where created_at < now() - make_interval(days => p_ota_log_retention_days);
    get diagnostics v_deleted_ota_logs = row_count;

    delete from public.lotterynet_results_health_log
    where checked_at < now() - make_interval(days => p_health_log_retention_days);
    get diagnostics v_deleted_health_logs = row_count;

    delete from public.result_reconcile_jobs
    where status in ('completed', 'failed')
      and coalesce(completed_at, created_at) < now() - make_interval(days => p_reconcile_job_retention_days);
    get diagnostics v_deleted_reconcile_jobs = row_count;
  end if;

  v_result := jsonb_build_object(
    'ok', true,
    'dryRun', p_dry_run,
    'retentionDays', jsonb_build_object(
      'deletedTickets', p_deleted_ticket_retention_days,
      'resultCache', p_result_cache_retention_days,
      'otaLogs', p_ota_log_retention_days,
      'healthLogs', p_health_log_retention_days,
      'reconcileJobs', p_reconcile_job_retention_days
    ),
    'candidates', jsonb_build_object(
      'deletedTicketsWithoutPayments', v_deleted_ticket_candidates
    ),
    'deleted', jsonb_build_object(
      'tickets', v_deleted_tickets,
      'ticketLimitReservations', v_deleted_ticket_limit_reservations,
      'resultCacheKeys', v_deleted_result_cache,
      'otaUpdateLogs', v_deleted_ota_logs,
      'resultHealthLogs', v_deleted_health_logs,
      'reconcileJobs', v_deleted_reconcile_jobs
    )
  );

  insert into private.lotterynet_maintenance_runs (dry_run, result)
  values (p_dry_run, v_result);

  return v_result;
end;
$$;

revoke all on function private.lotterynet_safe_maintenance(
  boolean,
  integer,
  integer,
  integer,
  integer,
  integer
) from public;
revoke all on function private.lotterynet_safe_maintenance(
  boolean,
  integer,
  integer,
  integer,
  integer,
  integer
) from anon;
revoke all on function private.lotterynet_safe_maintenance(
  boolean,
  integer,
  integer,
  integer,
  integer,
  integer
) from authenticated;

do $$
declare
  v_command text;
begin
  v_command := $job$
    select private.lotterynet_safe_maintenance(
      false,
      30,
      21,
      30,
      14,
      14
    );
  $job$;

  if exists (select 1 from cron.job where jobname = 'lotterynet-safe-maintenance') then
    update cron.job
      set schedule = '20 4 * * *',
          command = v_command,
          active = true
    where jobname = 'lotterynet-safe-maintenance';
  else
    perform cron.schedule('lotterynet-safe-maintenance', '20 4 * * *', v_command);
  end if;
end;
$$;
