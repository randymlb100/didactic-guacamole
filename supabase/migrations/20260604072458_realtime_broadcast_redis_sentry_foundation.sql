begin;

create or replace function public.lotterynet_realtime_actor_aliases()
returns text[]
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
  v_uid text := coalesce(auth.uid()::text, '');
  v_profile record;
  v_actor jsonb;
  v_aliases text[] := array[]::text[];
begin
  if v_uid = '' then
    return v_aliases;
  end if;

  select
    id::text as id,
    coalesce(admin_owner_id::text, '') as admin_owner_id,
    coalesce(banca_id::text, '') as banca_id,
    coalesce(parent_user_id::text, '') as parent_user_id,
    coalesce(created_by::text, '') as created_by
  into v_profile
  from public.profiles
  where id = auth.uid()
  limit 1;

  v_aliases := array_remove(array[
    v_uid,
    coalesce(v_profile.id, ''),
    coalesce(v_profile.admin_owner_id, ''),
    coalesce(v_profile.banca_id, ''),
    coalesce(v_profile.parent_user_id, ''),
    coalesce(v_profile.created_by, '')
  ], '');

  v_actor := public.ln_actor_from_legacy_state(v_uid);
  if v_actor is not null then
    v_aliases := v_aliases || array_remove(array[
      coalesce(v_actor ->> 'id', ''),
      coalesce(v_actor ->> 'user', ''),
      coalesce(v_actor ->> 'username', ''),
      coalesce(v_actor ->> 'authUserId', ''),
      coalesce(v_actor ->> 'auth_user_id', ''),
      coalesce(v_actor ->> 'adminId', ''),
      coalesce(v_actor ->> 'adminUser', ''),
      coalesce(v_actor ->> 'adminKey', ''),
      coalesce(v_actor ->> 'banca', ''),
      coalesce(v_actor ->> 'cashierId', ''),
      coalesce(v_actor ->> 'cashierUser', ''),
      coalesce(v_actor ->> 'cashierKey', '')
    ], '');
  end if;

  return array(
    select distinct lower(trim(value))
    from unnest(v_aliases) value
    where trim(value) <> ''
  );
end;
$function$;

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

  if v_topic like 'ln:tickets:owner:%' then
    v_owner_key := regexp_replace(v_topic, '^ln:tickets:owner:', '');
    return lower(trim(v_owner_key)) = any(public.lotterynet_realtime_actor_aliases());
  end if;

  return false;
end;
$function$;

revoke all on function public.lotterynet_realtime_actor_aliases() from public, anon, authenticated;
revoke all on function public.lotterynet_can_receive_realtime_topic(text) from public, anon, authenticated;
grant execute on function public.lotterynet_realtime_actor_aliases() to authenticated, service_role;
grant execute on function public.lotterynet_can_receive_realtime_topic(text) to authenticated, service_role;

alter table realtime.messages enable row level security;

drop policy if exists lotterynet_receive_private_broadcasts on realtime.messages;
create policy lotterynet_receive_private_broadcasts
on realtime.messages
for select
to authenticated
using (
  extension = 'broadcast'
  and public.lotterynet_can_receive_realtime_topic((select realtime.topic()))
);

drop policy if exists lotterynet_no_client_broadcast_insert on realtime.messages;
create policy lotterynet_no_client_broadcast_insert
on realtime.messages
for insert
to authenticated
with check (false);

create or replace function public.lotterynet_broadcast_ticket_owner_touch()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if new.owner_key is null or trim(new.owner_key) = '' then
    return null;
  end if;

  perform realtime.send(
    jsonb_build_object(
      'schemaVersion', 1,
      'entity', 'ticket_owner_snapshot',
      'ownerKey', new.owner_key,
      'updatedAt', new.updated_at
    ),
    tg_op,
    'ln:tickets:owner:' || lower(trim(new.owner_key)),
    true
  );

  return null;
end;
$function$;

drop trigger if exists lotterynet_broadcast_ticket_owner_touch
on public.lotterynet_tickets_by_owner;

create trigger lotterynet_broadcast_ticket_owner_touch
after insert or update of updated_at
on public.lotterynet_tickets_by_owner
for each row
execute function public.lotterynet_broadcast_ticket_owner_touch();

create or replace function public.lotterynet_broadcast_result_draw_touch()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_row public.result_draws%rowtype;
begin
  if tg_op = 'DELETE' then
    v_row := old;
  else
    v_row := new;
  end if;
  if v_row.result_day_key is null or trim(v_row.result_day_key) = '' then
    return null;
  end if;

  perform realtime.send(
    jsonb_build_object(
      'schemaVersion', 1,
      'entity', 'result_draw',
      'dayKey', v_row.result_day_key,
      'lotteryId', v_row.lottery_legacy_id,
      'game', v_row.game,
      'draw', v_row.draw_name,
      'status', v_row.status,
      'sourceHash', v_row.source_hash,
      'updatedAt', v_row.updated_at
    ),
    tg_op,
    'ln:results:' || lower(trim(v_row.result_day_key)),
    true
  );

  return null;
end;
$function$;

drop trigger if exists lotterynet_broadcast_result_draw_touch
on public.result_draws;

create trigger lotterynet_broadcast_result_draw_touch
after insert or update or delete
on public.result_draws
for each row
execute function public.lotterynet_broadcast_result_draw_touch();

comment on function public.lotterynet_broadcast_ticket_owner_touch()
is 'Broadcasts lightweight owner ticket invalidation events without ticket payload or money fields.';

comment on function public.lotterynet_broadcast_result_draw_touch()
is 'Broadcasts lightweight result invalidation events without raw result payloads.';

commit;
