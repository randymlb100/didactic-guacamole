-- Close direct public access to the global users state.
--
-- App path after this migration:
-- - Read:  lotterynet-users-state Edge Function, public fetch action.
-- - Write: lotterynet-users-state Edge Function, admin/master JWT required.
-- - Server functions keep service_role access.

revoke select, insert, update, delete
on table public.lotterynet_users_state
from anon, authenticated;

drop policy if exists "LotteryNet compatibility read users"
on public.lotterynet_users_state;

-- Rollback, if a legacy APK must read the table directly again:
--
-- grant select on table public.lotterynet_users_state to anon, authenticated;
--
-- create policy "LotteryNet compatibility read users"
-- on public.lotterynet_users_state
-- for select
-- to anon, authenticated
-- using (true);
