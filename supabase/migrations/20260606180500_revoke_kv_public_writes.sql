-- Revoke legacy public write access on the shared KV table.
--
-- The current Android app writes user/config/ticket/result state through Edge
-- Functions. Service-role Edge paths keep working after this revoke; only
-- direct anonymous PostgREST writes are closed.

revoke insert, update on table public.lotterynet_kv from anon;
revoke insert, update on table public.lotterynet_kv from authenticated;

drop policy if exists kv_insert_legacy_anon_only on public.lotterynet_kv;
drop policy if exists kv_update_legacy_anon_only on public.lotterynet_kv;

-- Rollback, if a legacy APK is proven to require direct KV writes:
--
-- grant insert, update on table public.lotterynet_kv to anon;
--
-- create policy kv_insert_legacy_anon_only
-- on public.lotterynet_kv
-- for insert
-- to anon
-- with check (
--   key = any (array[
--     'sys_users_v4',
--     'sys_audit_v4',
--     'sys_alerts_v4',
--     'sys_presence_v1'
--   ])
--   or key like 'admin:%'
--   or key like 'admin_sync:%'
--   or key like 'ticket_deleted:%'
-- );
--
-- create policy kv_update_legacy_anon_only
-- on public.lotterynet_kv
-- for update
-- to anon
-- using (
--   key = any (array[
--     'sys_users_v4',
--     'sys_audit_v4',
--     'sys_alerts_v4',
--     'sys_presence_v1'
--   ])
--   or key like 'admin:%'
--   or key like 'admin_sync:%'
--   or key like 'ticket_deleted:%'
-- )
-- with check (
--   key = any (array[
--     'sys_users_v4',
--     'sys_audit_v4',
--     'sys_alerts_v4',
--     'sys_presence_v1'
--   ])
--   or key like 'admin:%'
--   or key like 'admin_sync:%'
--   or key like 'ticket_deleted:%'
-- );
