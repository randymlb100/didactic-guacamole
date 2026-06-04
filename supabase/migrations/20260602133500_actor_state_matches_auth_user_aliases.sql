begin;

create or replace function public.ln_actor_from_legacy_state(p_actor_key text)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_payload jsonb;
  v_actor jsonb;
  v_sources text[] := array['admins','supervisores','supervisors','cajeros','cashiers','users'];
  v_source text;
  v_key text := lower(trim(coalesce(p_actor_key, '')));
begin
  if v_key = '' then
    return null;
  end if;

  select payload into v_payload
  from public.lotterynet_users_state
  where scope = 'global'
  order by updated_at desc nulls last
  limit 1;

  if v_payload is null then
    return null;
  end if;

  foreach v_source in array v_sources loop
    select value into v_actor
    from jsonb_array_elements(coalesce(v_payload -> v_source, '[]'::jsonb)) value
    where v_key = any(array_remove(array[
      lower(trim(coalesce(value ->> 'id', ''))),
      lower(trim(coalesce(value ->> 'user', ''))),
      lower(trim(coalesce(value ->> 'username', ''))),
      lower(trim(coalesce(value ->> 'displayName', ''))),
      lower(trim(coalesce(value ->> 'authUserId', ''))),
      lower(trim(coalesce(value ->> 'auth_user_id', ''))),
      lower(trim(coalesce(value ->> 'adminId', ''))),
      lower(trim(coalesce(value ->> 'adminUser', ''))),
      lower(trim(coalesce(value ->> 'adminKey', ''))),
      lower(trim(coalesce(value ->> 'cashierId', ''))),
      lower(trim(coalesce(value ->> 'cashierUser', ''))),
      lower(trim(coalesce(value ->> 'cashierKey', ''))),
      lower(trim(coalesce(value ->> 'ownerAdminId', ''))),
      lower(trim(coalesce(value ->> 'parentAdminId', ''))),
      lower(trim(coalesce(value ->> 'supervisorId', ''))),
      lower(trim(coalesce(value ->> 'supervisorUser', ''))),
      lower(trim(coalesce(value ->> 'territory', ''))),
      lower(trim(coalesce(value ->> 'banca', '')))
    ], ''))
    limit 1;

    if v_actor is not null then
      return v_actor || jsonb_build_object('_source', v_source);
    end if;
  end loop;

  return null;
end;
$function$;

revoke all on function public.ln_actor_from_legacy_state(text) from public, anon, authenticated;
grant execute on function public.ln_actor_from_legacy_state(text) to service_role;

commit;
