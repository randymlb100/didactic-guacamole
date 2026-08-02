-- Stop public clients from mutating result-cache KV rows.
--
-- Result caches are now server-maintained by Render Cron / Supabase Edge using
-- service_role. Legacy anon KV writes are left only for the older system/admin
-- compatibility prefixes until those prefixes are fully moved behind Edge/RPC.

revoke insert, update, delete
on table public.lotterynet_kv
from authenticated;

drop policy if exists kv_insert_combined
on public.lotterynet_kv;

drop policy if exists kv_update_combined
on public.lotterynet_kv;

create policy kv_insert_legacy_anon_only
on public.lotterynet_kv
for insert
to anon
with check (
  key = any (array[
    'sys_users_v4',
    'sys_audit_v4',
    'sys_alerts_v4',
    'sys_presence_v1'
  ])
  or key like 'admin:%'
  or key like 'admin_sync:%'
  or key like 'ticket_deleted:%'
);

create policy kv_update_legacy_anon_only
on public.lotterynet_kv
for update
to anon
using (
  key = any (array[
    'sys_users_v4',
    'sys_audit_v4',
    'sys_alerts_v4',
    'sys_presence_v1'
  ])
  or key like 'admin:%'
  or key like 'admin_sync:%'
  or key like 'ticket_deleted:%'
)
with check (
  key = any (array[
    'sys_users_v4',
    'sys_audit_v4',
    'sys_alerts_v4',
    'sys_presence_v1'
  ])
  or key like 'admin:%'
  or key like 'admin_sync:%'
  or key like 'ticket_deleted:%'
);

-- Rollback, if a legacy scraper unexpectedly needs public result-cache writes:
--
-- grant insert, update, delete on table public.lotterynet_kv to authenticated;
--
-- drop policy if exists kv_insert_legacy_anon_only on public.lotterynet_kv;
-- drop policy if exists kv_update_legacy_anon_only on public.lotterynet_kv;
--
-- create policy kv_insert_combined
-- on public.lotterynet_kv
-- for insert
-- to public
-- with check (
--   ((((select auth.role()) = 'anon') and (
--     key = any (array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
--     or key like 'admin:%'
--     or key like 'admin_sync:%'
--     or key like 'ticket_deleted:%'
--   ))
--   or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$')
-- );
--
-- create policy kv_update_combined
-- on public.lotterynet_kv
-- for update
-- to public
-- using (
--   ((((select auth.role()) = 'anon') and (
--     key = any (array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
--     or key like 'admin:%'
--     or key like 'admin_sync:%'
--     or key like 'ticket_deleted:%'
--   ))
--   or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$')
-- )
-- with check (
--   ((((select auth.role()) = 'anon') and (
--     key = any (array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
--     or key like 'admin:%'
--     or key like 'admin_sync:%'
--     or key like 'ticket_deleted:%'
--   ))
--   or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$')
-- );
