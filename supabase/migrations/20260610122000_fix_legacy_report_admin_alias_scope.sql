begin;

do $$
declare
  v_sql text;
  v_decl_old text := $old$
  v_supervisor_key text := nullif(p_body ->> 'supervisorKey', '');
  v_cashier_keys text[] := array[]::text[];
  v_supervisor_cashier_keys text[] := array[]::text[];
$old$;
  v_decl_new text := $new$
  v_supervisor_key text := nullif(p_body ->> 'supervisorKey', '');
  v_admin_keys text[] := array[]::text[];
  v_cashier_keys text[] := array[]::text[];
  v_supervisor_cashier_keys text[] := array[]::text[];
$new$;
  v_anchor text := '  if v_cashier_key is not null then';
  v_admin_alias_block text := $block$
  select coalesce(array_agg(distinct alias), array[]::text[])
    into v_admin_keys
  from unnest(
    array_cat(
      array[
        lower(trim(coalesce(v_admin_key, ''))),
        lower(trim(coalesce(v_actor ->> 'adminId', ''))),
        lower(trim(coalesce(v_actor ->> 'adminUser', ''))),
        lower(trim(coalesce(v_actor ->> 'id', ''))),
        lower(trim(coalesce(v_actor ->> 'user', ''))),
        lower(trim(coalesce(v_actor ->> 'username', '')))
      ],
      case
        when v_admin_key is null then array[]::text[]
        else public.ln_legacy_actor_aliases(v_admin_key)
      end
    )
  ) as expanded(alias)
  where alias <> '';

  if cardinality(v_admin_keys) = 0 and v_admin_key is not null then
    v_admin_keys := array[lower(trim(v_admin_key))];
  end if;

$block$;
  v_ticket_old text := $old$
    where (v_admin_key is null or lower(coalesce(t.admin_key, '')) = lower(v_admin_key) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'id', '')) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'user', '')))
$old$;
  v_ticket_new text := $new$
    where (v_admin_key is null or lower(coalesce(t.admin_key, '')) = any(v_admin_keys))
$new$;
  v_recharge_old text := $old$
        and (
          v_admin_key is null
          or lower(coalesce(r.owner_key, '')) = lower(v_admin_key)
          or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'id', ''))
          or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'user', ''))
          or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(v_admin_key)
          or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(v_admin_key)
          or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(coalesce(v_actor ->> 'id', ''))
          or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(coalesce(v_actor ->> 'user', ''))
        )
$old$;
  v_recharge_new text := $new$
        and (
          v_admin_key is null
          or lower(coalesce(r.owner_key, '')) = any(v_admin_keys)
          or lower(coalesce(r.local_record ->> 'adminId', '')) = any(v_admin_keys)
          or lower(coalesce(r.local_record ->> 'adminUser', '')) = any(v_admin_keys)
        )
$new$;
begin
  select pg_get_functiondef('public.ln_legacy_report(jsonb)'::regprocedure)
    into v_sql;

  if position(v_decl_old in v_sql) = 0 then
    raise exception 'ln_legacy_report declaration anchor not found';
  end if;

  if position(v_anchor in v_sql) = 0 then
    raise exception 'ln_legacy_report cashier anchor not found';
  end if;

  if position(v_ticket_old in v_sql) = 0 then
    raise exception 'ln_legacy_report ticket admin filter pattern not found';
  end if;

  if position(v_recharge_old in v_sql) = 0 then
    raise exception 'ln_legacy_report recharge admin filter pattern not found';
  end if;

  v_sql := replace(v_sql, v_decl_old, v_decl_new);
  v_sql := replace(v_sql, v_anchor, v_admin_alias_block || v_anchor);
  v_sql := replace(v_sql, v_ticket_old, v_ticket_new);
  v_sql := replace(v_sql, v_recharge_old, v_recharge_new);

  if position('lower(coalesce(t.admin_key, '''')) = any(v_admin_keys)' in v_sql) = 0 then
    raise exception 'ln_legacy_report patched ticket admin filter not found';
  end if;

  if position('lower(coalesce(r.owner_key, '''')) = any(v_admin_keys)' in v_sql) = 0 then
    raise exception 'ln_legacy_report patched recharge admin filter not found';
  end if;

  execute v_sql;
end $$;

revoke all on function public.ln_legacy_report(jsonb) from public, anon, authenticated;
grant execute on function public.ln_legacy_report(jsonb) to service_role;

commit;
