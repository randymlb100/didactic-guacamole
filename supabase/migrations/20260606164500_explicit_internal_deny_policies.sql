begin;

do $$
declare
  table_name text;
  target regclass;
begin
  foreach table_name in array array[
    'public.lotterynet_results_health_log',
    'public.lotterynet_tickets_by_owner_backup_empty_items_20260529',
    'public.result_reconcile_jobs',
    'public.sports_audit_log',
    'public.sports_events',
    'public.sports_feature_flags',
    'public.sports_limits',
    'public.sports_markets',
    'public.sports_odds',
    'public.sports_odds_snapshots',
    'public.sports_settlements',
    'public.sports_team_assets',
    'public.sports_ticket_legs',
    'public.sports_tickets'
  ]
  loop
    target := to_regclass(table_name);
    if target is not null then
      execute format('drop policy if exists internal_service_only on %s', target);
      execute format(
        'create policy internal_service_only on %s for all to public using (false) with check (false)',
        target
      );
    end if;
  end loop;
end;
$$;

commit;

-- Rollback:
-- drop policy if exists internal_service_only on public.lotterynet_results_health_log;
-- drop policy if exists internal_service_only on public.lotterynet_tickets_by_owner_backup_empty_items_20260529;
-- drop policy if exists internal_service_only on public.result_reconcile_jobs;
-- drop policy if exists internal_service_only on public.sports_audit_log;
-- drop policy if exists internal_service_only on public.sports_events;
-- drop policy if exists internal_service_only on public.sports_feature_flags;
-- drop policy if exists internal_service_only on public.sports_limits;
-- drop policy if exists internal_service_only on public.sports_markets;
-- drop policy if exists internal_service_only on public.sports_odds;
-- drop policy if exists internal_service_only on public.sports_odds_snapshots;
-- drop policy if exists internal_service_only on public.sports_settlements;
-- drop policy if exists internal_service_only on public.sports_team_assets;
-- drop policy if exists internal_service_only on public.sports_ticket_legs;
-- drop policy if exists internal_service_only on public.sports_tickets;
