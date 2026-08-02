begin;

create or replace function public.ln_limit_self_keys(p_key text)
returns text[]
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_actor jsonb;
  v_key text := trim(coalesce(p_key, ''));
  v_keys text[] := array[]::text[];
begin
  if v_key <> '' then
    v_keys := v_keys || array[v_key];
  end if;

  v_actor := public.ln_actor_from_legacy_state(v_key);
  v_keys := v_keys || array_remove(array[
    nullif(trim(coalesce(v_actor ->> 'id', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'user', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'username', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'displayName', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'authUserId', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'auth_user_id', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'cashierId', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'cashierUser', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'cashierKey', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'supervisorId', '')), ''),
    nullif(trim(coalesce(v_actor ->> 'supervisorUser', '')), '')
  ], null);

  return coalesce((
    select array_agg(k order by first_seen)
    from (
      select trim(value) as k, min(ord) as first_seen
      from unnest(v_keys) with ordinality as u(value, ord)
      where trim(value) <> ''
      group by trim(value)
    ) keys
  ), array[]::text[]);
end;
$function$;

revoke all on function public.ln_limit_self_keys(text) from public, anon, authenticated;
grant execute on function public.ln_limit_self_keys(text) to service_role;

commit;
