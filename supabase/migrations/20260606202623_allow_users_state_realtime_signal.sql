-- Allow authenticated Realtime clients to subscribe to the users-state
-- signal without reopening the sensitive payload through REST.
--
-- The Android app only uses this postgres_changes subscription as a
-- "users changed" invalidation signal. Actual user data still comes from
-- the lotterynet-users-state Edge Function.

revoke select, insert, update, delete
on table public.lotterynet_users_state
from anon, authenticated;

revoke select (scope, payload, updated_at),
  insert (scope, payload, updated_at),
  update (scope, payload, updated_at),
  references (scope, payload, updated_at)
on table public.lotterynet_users_state
from anon, authenticated;

drop policy if exists lotterynet_users_state_internal_deny_authenticated
on public.lotterynet_users_state;

drop policy if exists lotterynet_users_state_realtime_signal_select
on public.lotterynet_users_state;

create policy lotterynet_users_state_realtime_signal_select
on public.lotterynet_users_state
for select
to authenticated
using (scope = 'global');

grant select (scope, updated_at)
on public.lotterynet_users_state
to authenticated;

drop policy if exists lotterynet_users_state_no_authenticated_insert
on public.lotterynet_users_state;

create policy lotterynet_users_state_no_authenticated_insert
on public.lotterynet_users_state
as restrictive
for insert
to authenticated
with check (false);

drop policy if exists lotterynet_users_state_no_authenticated_update
on public.lotterynet_users_state;

create policy lotterynet_users_state_no_authenticated_update
on public.lotterynet_users_state
as restrictive
for update
to authenticated
using (false)
with check (false);

drop policy if exists lotterynet_users_state_no_authenticated_delete
on public.lotterynet_users_state;

create policy lotterynet_users_state_no_authenticated_delete
on public.lotterynet_users_state
as restrictive
for delete
to authenticated
using (false);
