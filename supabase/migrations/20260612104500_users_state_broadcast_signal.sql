begin;

-- Keep lotterynet_users_state closed to direct clients. Clients only need an
-- invalidation signal; payload stays behind Edge Functions/service role.

create or replace function public.lotterynet_broadcast_users_state_touch()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if coalesce(new.scope, '') <> 'global' then
    return null;
  end if;

  perform realtime.send(
    jsonb_build_object(
      'schemaVersion', 1,
      'entity', 'users_state',
      'scope', new.scope,
      'updatedAt', new.updated_at
    ),
    tg_op,
    'ln:users:global',
    true
  );

  return null;
end;
$function$;

drop trigger if exists lotterynet_broadcast_users_state_touch
on public.lotterynet_users_state;

create trigger lotterynet_broadcast_users_state_touch
after insert or update of updated_at
on public.lotterynet_users_state
for each row
execute function public.lotterynet_broadcast_users_state_touch();

revoke all on function public.lotterynet_broadcast_users_state_touch()
from public, anon, authenticated;

grant execute on function public.lotterynet_broadcast_users_state_touch()
to service_role;

create or replace function public.lotterynet_can_receive_realtime_topic(p_topic text)
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
    return lower(trim(v_owner_key)) = any(public.lotterynet_realtime_actor_aliases());
  end if;

  return false;
end;
$function$;

revoke all on function public.lotterynet_can_receive_realtime_topic(text)
from public, anon, authenticated;

grant execute on function public.lotterynet_can_receive_realtime_topic(text)
to authenticated, service_role;

comment on function public.lotterynet_broadcast_users_state_touch()
is 'Broadcasts a lightweight users-state invalidation signal without exposing the users payload.';

commit;
