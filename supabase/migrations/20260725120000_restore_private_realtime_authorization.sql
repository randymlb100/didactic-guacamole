begin;

-- Keep the Realtime authorization helper out of the exposed public schema.
-- It is called by the RLS policy below, not by the Android client directly.
create schema if not exists private;

revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create or replace function private.lotterynet_can_receive_realtime_topic(p_topic text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
  v_topic text := lower(trim(coalesce(p_topic, '')));
  v_owner_key text;
begin
  if auth.uid() is null then
    return false;
  end if;

  if v_topic like 'ln:results:%' then
    return true;
  end if;

  if v_topic = 'ln:users:global' then
    return true;
  end if;

  if v_topic like 'ln:tickets:owner:%' then
    v_owner_key := regexp_replace(v_topic, '^ln:tickets:owner:', '');
    return lower(trim(v_owner_key)) = any(private.lotterynet_realtime_actor_aliases());
  end if;

  return false;
end;
$function$;

revoke all on function private.lotterynet_can_receive_realtime_topic(text)
from public, anon, authenticated;
grant execute on function private.lotterynet_can_receive_realtime_topic(text)
to authenticated, service_role;

-- Remove direct RPC access to the legacy public copy. The policy uses the
-- private helper above, so the client flow remains unchanged.
revoke all on function public.lotterynet_can_receive_realtime_topic(text)
from public, anon, authenticated;

drop policy if exists lotterynet_receive_private_broadcasts on realtime.messages;
create policy lotterynet_receive_private_broadcasts
on realtime.messages
for select
to authenticated
using (
  extension = 'broadcast'
  and (select private.lotterynet_can_receive_realtime_topic((select realtime.topic())))
);

commit;
