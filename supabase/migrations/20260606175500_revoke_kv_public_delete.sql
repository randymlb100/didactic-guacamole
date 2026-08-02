-- Revoke legacy public delete access on the shared KV table.
--
-- Current Android flows do not delete lotterynet_kv rows directly through
-- PostgREST; ticket/result/user/config mutations go through Edge functions.
-- Keeping anonymous DELETE open on a shared compatibility table is not needed
-- for production.

revoke delete on table public.lotterynet_kv from anon;

drop policy if exists kv_delete_allowed on public.lotterynet_kv;

-- Rollback, if a legacy APK is proven to require direct KV deletes:
--
-- grant delete on table public.lotterynet_kv to anon;
--
-- create policy kv_delete_allowed
-- on public.lotterynet_kv
-- for delete
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
-- );
