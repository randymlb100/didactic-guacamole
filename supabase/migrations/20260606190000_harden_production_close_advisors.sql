-- Close remaining Supabase advisor findings without reopening direct table access.
-- Public tables below are intentionally internal; Edge Functions use service role.

create schema if not exists private;

revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create or replace function private.lotterynet_realtime_actor_aliases()
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
  where id = (select auth.uid())
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

  if v_topic like 'ln:tickets:owner:%' then
    v_owner_key := regexp_replace(v_topic, '^ln:tickets:owner:', '');
    return lower(trim(v_owner_key)) = any(private.lotterynet_realtime_actor_aliases());
  end if;

  return false;
end;
$function$;

revoke all on function private.lotterynet_realtime_actor_aliases() from public, anon;
revoke all on function private.lotterynet_can_receive_realtime_topic(text) from public, anon;
grant execute on function private.lotterynet_realtime_actor_aliases() to authenticated, service_role;
grant execute on function private.lotterynet_can_receive_realtime_topic(text) to authenticated, service_role;

revoke execute on function public.lotterynet_realtime_actor_aliases() from public, anon, authenticated;
revoke execute on function public.lotterynet_can_receive_realtime_topic(text) from public, anon, authenticated;

drop policy if exists lotterynet_receive_private_broadcasts on realtime.messages;
create policy lotterynet_receive_private_broadcasts
on realtime.messages
for select
to authenticated
using (
  extension = 'broadcast'
  and (select private.lotterynet_can_receive_realtime_topic((select realtime.topic())))
);

drop policy if exists lotterynet_kv_internal_deny_anon on public.lotterynet_kv;
drop policy if exists lotterynet_kv_internal_deny_authenticated on public.lotterynet_kv;
create policy lotterynet_kv_internal_deny_anon
on public.lotterynet_kv
as restrictive
for all
to anon
using (false)
with check (false);
create policy lotterynet_kv_internal_deny_authenticated
on public.lotterynet_kv
as restrictive
for all
to authenticated
using (false)
with check (false);

drop policy if exists lotterynet_users_state_internal_deny_anon on public.lotterynet_users_state;
drop policy if exists lotterynet_users_state_internal_deny_authenticated on public.lotterynet_users_state;
create policy lotterynet_users_state_internal_deny_anon
on public.lotterynet_users_state
as restrictive
for all
to anon
using (false)
with check (false);
create policy lotterynet_users_state_internal_deny_authenticated
on public.lotterynet_users_state
as restrictive
for all
to authenticated
using (false)
with check (false);

drop policy if exists result_draws_internal_deny_anon on public.result_draws;
drop policy if exists result_draws_internal_deny_authenticated on public.result_draws;
create policy result_draws_internal_deny_anon
on public.result_draws
as restrictive
for all
to anon
using (false)
with check (false);
create policy result_draws_internal_deny_authenticated
on public.result_draws
as restrictive
for all
to authenticated
using (false)
with check (false);

drop policy if exists tickets_internal_deny_anon on public.tickets;
drop policy if exists tickets_internal_deny_authenticated on public.tickets;
create policy tickets_internal_deny_anon
on public.tickets
as restrictive
for all
to anon
using (false)
with check (false);
create policy tickets_internal_deny_authenticated
on public.tickets
as restrictive
for all
to authenticated
using (false)
with check (false);

create or replace function public.ln_current_role()
returns ln_role
language sql
stable
set search_path = public
as $function$
  select role from public.profiles where id = (select auth.uid())
$function$;

create or replace function public.ln_is_master()
returns boolean
language sql
stable
set search_path = public
as $function$
  select coalesce((select public.ln_current_role()) = 'master', false)
$function$;

create or replace function public.ln_same_admin_network(target_admin uuid)
returns boolean
language sql
stable
set search_path = public
as $function$
  select coalesce((
    select p.role = 'master'
      or (
        target_admin is not null
        and (p.id = target_admin or p.admin_owner_id = target_admin or p.parent_user_id = target_admin)
      )
    from public.profiles p
    where p.id = (select auth.uid())
    limit 1
  ), false)
$function$;

drop policy if exists audit_select_scope on public.auditoria;
create policy audit_select_scope
on public.auditoria
for select
to authenticated
using (
  (select public.ln_is_master())
  or actor_id = (select auth.uid())
);

drop policy if exists balances_select_scope on public.balances;
create policy balances_select_scope
on public.balances
for select
to authenticated
using (
  (select public.ln_is_master())
  or owner_id = (select auth.uid())
  or exists (
    select 1
    from public.profiles p
    where p.id = balances.owner_id
      and public.ln_same_admin_network(coalesce(p.admin_owner_id, p.id))
  )
);

create schema if not exists extensions;
do $$
begin
  alter extension pg_net set schema extensions;
exception
  when insufficient_privilege or feature_not_supported or object_not_in_prerequisite_state then
    raise notice 'pg_net schema relocation skipped: %', sqlerrm;
end $$;

drop index if exists public.idx_lotterynet_kv_upd;
drop index if exists public.idx_lotterynet_users_state_updated_at;
drop index if exists public.idx_lotterynet_master_state_updated_at;
