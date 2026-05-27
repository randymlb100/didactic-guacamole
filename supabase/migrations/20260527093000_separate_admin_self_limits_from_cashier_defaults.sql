-- Keep admin self-imposed sale limits separate from cashier defaults.
-- Cashier tickets still use defaults + byUser overrides.
-- Admin/master tickets use adminSelf when present; otherwise an explicit legacy
-- byUser row for that admin can still apply, but cashier defaults never leak in.

create or replace function public.ln_limit_config_has_positive_limit(p_config jsonb)
returns boolean
language plpgsql
immutable
set search_path to 'public'
as $function$
begin
  p_config := coalesce(p_config, '{}'::jsonb);
  return public.ln_jsonb_number(p_config, array['daySale','day_sale'], 0) > 0
      or public.ln_jsonb_number(p_config, array['payout'], 0) > 0
      or public.ln_jsonb_number(p_config, array['q','quiniela'], 0) > 0
      or public.ln_jsonb_number(p_config, array['pale','p'], 0) > 0
      or public.ln_jsonb_number(p_config, array['sp','superPale','super_pale','p'], 0) > 0
      or public.ln_jsonb_number(p_config, array['t','tripleta'], 0) > 0
      or public.ln_jsonb_number(p_config, array['p3','pick3Straight','pick3_straight','p'], 0) > 0
      or public.ln_jsonb_number(p_config, array['p3box','pick3Box','pick3_box','p3','p'], 0) > 0
      or public.ln_jsonb_number(p_config, array['p4','pick4Straight','pick4_straight','p'], 0) > 0
      or public.ln_jsonb_number(p_config, array['p4box','pick4Box','pick4_box','p4','p'], 0) > 0;
end;
$function$;

grant execute on function public.ln_limit_config_has_positive_limit(jsonb) to anon, authenticated, service_role;

create or replace function public.ln_cashier_limit_config(p_admin_key text, p_cashier_key text)
returns jsonb
language plpgsql
stable
set search_path to 'public'
as $function$
declare
  v_raw text;
  v_root jsonb := '{}'::jsonb;
  v_defaults jsonb := '{}'::jsonb;
  v_admin_self jsonb := '{}'::jsonb;
  v_row jsonb := null;
  v_by_user jsonb := '{}'::jsonb;
  v_actor jsonb;
  v_role text;
  v_identity text;
begin
  select payload into v_root
  from public.lotterynet_master_state
  where config_key = 'cashier_limits:' || trim(coalesce(p_admin_key, ''))
  limit 1;

  if v_root is null or v_root = '{}'::jsonb then
    select value into v_raw
    from public.lotterynet_kv
    where key = 'cashier_limits:' || trim(coalesce(p_admin_key, ''))
    limit 1;

    if v_raw is null or trim(v_raw) = '' then
      return '{}'::jsonb;
    end if;

    begin
      v_root := v_raw::jsonb;
    exception when others then
      return '{}'::jsonb;
    end;
  end if;

  v_defaults := coalesce(v_root -> 'defaults', '{}'::jsonb);
  v_admin_self := coalesce(v_root -> 'adminSelf', '{}'::jsonb);
  v_by_user := coalesce(v_root -> 'byUser', '{}'::jsonb);
  v_actor := public.ln_actor_from_legacy_state(p_cashier_key);
  v_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source'));

  foreach v_identity in array array[
    trim(coalesce(p_cashier_key, '')),
    trim(coalesce(v_actor ->> 'user', '')),
    trim(coalesce(v_actor ->> 'username', '')),
    trim(coalesce(v_actor ->> 'id', ''))
  ] loop
    if v_identity <> '' and v_by_user ? v_identity then
      v_row := v_by_user -> v_identity;
      exit;
    end if;
  end loop;

  if v_role in ('admin','admins','master','masters') then
    if public.ln_limit_config_has_positive_limit(v_admin_self) then
      return v_admin_self;
    end if;

    if v_row is not null and public.ln_limit_config_has_positive_limit(v_row) then
      return v_row;
    end if;

    return '{}'::jsonb;
  end if;

  return coalesce(v_defaults, '{}'::jsonb) || coalesce(v_row, '{}'::jsonb);
end;
$function$;

grant execute on function public.ln_cashier_limit_config(text, text) to anon, authenticated, service_role;
