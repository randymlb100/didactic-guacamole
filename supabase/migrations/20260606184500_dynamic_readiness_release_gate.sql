-- Make the server readiness release gate reflect the actual public exposure.
--
-- Deny-all policies with `using (false)` / `with check (false)` are defensive
-- and should not keep the direct Data API gate red. Real public table exposure
-- is represented by anon/authenticated grants and permissive public policies.

create or replace function private.lotterynet_server_readiness_report()
returns jsonb
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_public_policies jsonb;
  v_public_grants jsonb;
  v_permissive_public_policy_count int := 0;
  v_cron_jobs jsonb;
  v_table_stats jsonb;
  v_maintenance jsonb;
  v_gate_open boolean;
begin
  select coalesce(jsonb_agg(
    jsonb_build_object(
      'table', tablename,
      'policy', policyname,
      'roles', roles,
      'command', cmd,
      'qual', qual,
      'withCheck', with_check
    )
    order by tablename, policyname
  ), '[]'::jsonb)
    into v_public_policies
  from pg_policies
  where schemaname = 'public'
    and (
      roles::text like '%anon%'
      or roles::text like '%public%'
      or roles::text like '%authenticated%'
    )
    and tablename in (
      'tickets',
      'ticket_items',
      'lotterynet_users_state',
      'lotterynet_kv',
      'result_draws',
      'lotterynet_push_tokens'
    );

  select count(*)
    into v_permissive_public_policy_count
  from pg_policies
  where schemaname = 'public'
    and (
      roles::text like '%anon%'
      or roles::text like '%public%'
      or roles::text like '%authenticated%'
    )
    and tablename in (
      'tickets',
      'ticket_items',
      'lotterynet_users_state',
      'lotterynet_kv',
      'result_draws',
      'lotterynet_push_tokens'
    )
    and (
      coalesce(nullif(trim(qual), ''), 'false') <> 'false'
      or coalesce(nullif(trim(with_check), ''), 'false') <> 'false'
    );

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'grantee', grantee,
      'table', table_name,
      'privilege', privilege_type
    )
    order by table_name, grantee, privilege_type
  ), '[]'::jsonb)
    into v_public_grants
  from information_schema.role_table_grants
  where table_schema = 'public'
    and grantee in ('anon', 'authenticated')
    and table_name in (
      'tickets',
      'ticket_items',
      'lotterynet_users_state',
      'lotterynet_kv',
      'result_draws',
      'lotterynet_push_tokens'
    );

  v_gate_open := jsonb_array_length(v_public_grants) = 0
    and v_permissive_public_policy_count = 0;

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'jobid', jobid,
      'jobname', jobname,
      'schedule', schedule,
      'active', active
    )
    order by jobname
  ), '[]'::jsonb)
    into v_cron_jobs
  from cron.job
  where jobname like 'lotterynet-%';

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'table', relname,
      'liveRows', n_live_tup,
      'deadRows', n_dead_tup,
      'seqScans', seq_scan,
      'indexScans', idx_scan,
      'lastAutovacuum', last_autovacuum,
      'lastAutoanalyze', last_autoanalyze
    )
    order by relname
  ), '[]'::jsonb)
    into v_table_stats
  from pg_stat_user_tables
  where schemaname = 'public'
    and relname in (
      'tickets',
      'ticket_items',
      'ticket_prize_items',
      'lotterynet_users_state',
      'lotterynet_kv',
      'result_draws',
      'result_reconcile_jobs',
      'ota_update_logs',
      'lotterynet_results_health_log'
    );

  select coalesce(result, '{}'::jsonb)
    into v_maintenance
  from private.lotterynet_maintenance_runs
  order by ran_at desc
  limit 1;

  return jsonb_build_object(
    'ok', true,
    'generatedAt', now(),
    'mode', 'read_only',
    'releaseGate', jsonb_build_object(
      'canRevokeDirectTableAccess', v_gate_open,
      'reason', case
        when v_gate_open then 'Direct public Data API table access is closed for the reviewed production tables.'
        else 'Keep legacy app compatibility until every direct Data API read is replaced by JWT Edge Functions and smoke-tested.'
      end,
      'permissivePublicPolicyCount', v_permissive_public_policy_count
    ),
    'maintenance', v_maintenance,
    'cronJobs', v_cron_jobs,
    'tableStats', v_table_stats,
    'publicPoliciesToReview', v_public_policies,
    'publicGrantsToReview', v_public_grants
  );
end;
$$;

revoke all on function private.lotterynet_server_readiness_report() from public;
revoke all on function private.lotterynet_server_readiness_report() from anon;
revoke all on function private.lotterynet_server_readiness_report() from authenticated;
