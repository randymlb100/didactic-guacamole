begin;

create or replace function public.lotterynet_try_results_refresh_lock(
  p_holder text,
  p_ttl_seconds int default 180
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_now timestamptz := now();
  v_existing text;
  v_existing_json jsonb := '{}'::jsonb;
  v_expires_at timestamptz;
  v_holder text := nullif(trim(coalesce(p_holder, '')), '');
  v_ttl_seconds int := greatest(coalesce(p_ttl_seconds, 180), 15);
begin
  if v_holder is null then
    v_holder := gen_random_uuid()::text;
  end if;

  select value
    into v_existing
  from public.lotterynet_kv
  where key = 'sys_results_refresh_lock'
  for update;

  if v_existing is not null then
    begin
      v_existing_json := v_existing::jsonb;
      v_expires_at := nullif(v_existing_json->>'expiresAt', '')::timestamptz;
    exception when others then
      v_existing_json := '{}'::jsonb;
      v_expires_at := null;
    end;

    if v_expires_at is not null and v_expires_at > v_now then
      return jsonb_build_object(
        'ok', true,
        'acquired', false,
        'holder', v_existing_json->>'holder',
        'expiresAt', v_expires_at
      );
    end if;
  end if;

  insert into public.lotterynet_kv(key, value, upd)
  values (
    'sys_results_refresh_lock',
    jsonb_build_object(
      'holder', v_holder,
      'acquiredAt', v_now,
      'expiresAt', v_now + make_interval(secs => v_ttl_seconds)
    )::text,
    v_now
  )
  on conflict (key) do update
    set value = excluded.value,
        upd = excluded.upd;

  return jsonb_build_object(
    'ok', true,
    'acquired', true,
    'holder', v_holder,
    'expiresAt', v_now + make_interval(secs => v_ttl_seconds)
  );
end;
$$;

create or replace function public.lotterynet_release_results_refresh_lock(
  p_holder text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_now timestamptz := now();
  v_existing text;
  v_existing_json jsonb := '{}'::jsonb;
  v_holder text := nullif(trim(coalesce(p_holder, '')), '');
begin
  if v_holder is null then
    return jsonb_build_object('ok', false, 'released', false, 'reason', 'missing_holder');
  end if;

  select value
    into v_existing
  from public.lotterynet_kv
  where key = 'sys_results_refresh_lock'
  for update;

  if v_existing is null then
    return jsonb_build_object('ok', true, 'released', false, 'reason', 'missing_lock');
  end if;

  begin
    v_existing_json := v_existing::jsonb;
  exception when others then
    v_existing_json := '{}'::jsonb;
  end;

  if coalesce(v_existing_json->>'holder', '') <> v_holder then
    return jsonb_build_object(
      'ok', true,
      'released', false,
      'reason', 'holder_mismatch',
      'holder', v_existing_json->>'holder'
    );
  end if;

  update public.lotterynet_kv
  set value = jsonb_build_object(
        'holder', v_holder,
        'releasedAt', v_now,
        'expiresAt', v_now
      )::text,
      upd = v_now
  where key = 'sys_results_refresh_lock';

  return jsonb_build_object('ok', true, 'released', true, 'holder', v_holder);
end;
$$;

create or replace function private.lotterynet_cron_health_report()
returns jsonb
language plpgsql
security definer
set search_path = public, cron, private, pg_temp
as $$
begin
  return (
    with recent_runs as (
      select
        d.jobid,
        j.jobname,
        d.status,
        d.return_message,
        d.start_time,
        d.end_time,
        extract(epoch from coalesce(d.end_time, now()) - d.start_time)::int as duration_seconds
      from cron.job_run_details d
      join cron.job j on j.jobid = d.jobid
      where j.jobname like 'lotterynet-%'
        and d.start_time > now() - interval '6 hours'
      order by d.start_time desc
      limit 100
    )
    select jsonb_build_object(
      'ok', true,
      'generatedAt', now(),
      'jobs', (
        select coalesce(jsonb_agg(
          jsonb_build_object(
            'jobid', jobid,
            'jobname', jobname,
            'schedule', schedule,
            'active', active
          )
          order by jobname
        ), '[]'::jsonb)
        from cron.job
        where jobname like 'lotterynet-%'
      ),
      'recentRuns', (
        select coalesce(jsonb_agg(to_jsonb(recent_runs)), '[]'::jsonb)
        from recent_runs
      )
    )
  );
end;
$$;

revoke all on function public.lotterynet_try_results_refresh_lock(text, int) from public, anon, authenticated;
revoke all on function public.lotterynet_release_results_refresh_lock(text) from public, anon, authenticated;
grant execute on function public.lotterynet_try_results_refresh_lock(text, int) to service_role;
grant execute on function public.lotterynet_release_results_refresh_lock(text) to service_role;

revoke all on function private.lotterynet_cron_health_report() from public;
revoke all on function private.lotterynet_cron_health_report() from anon;
revoke all on function private.lotterynet_cron_health_report() from authenticated;

comment on function public.lotterynet_try_results_refresh_lock(text, int)
is 'Service-only TTL lock used by results-server-refresh so cron invocations do not overlap.';

comment on function public.lotterynet_release_results_refresh_lock(text)
is 'Service-only release helper for results-server-refresh TTL lock.';

comment on function private.lotterynet_cron_health_report()
is 'Read-only server report for recent LotteryNet cron runs using cron.job joined to cron.job_run_details.';

commit;
