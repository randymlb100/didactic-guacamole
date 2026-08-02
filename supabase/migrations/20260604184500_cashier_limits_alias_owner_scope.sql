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

create or replace function public.ln_cashier_limit_payload_for_admin(p_admin_key text)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_key text;
  v_payload jsonb;
  v_raw jsonb;
begin
  foreach v_key in array public.ln_limit_self_keys(p_admin_key) loop
    select payload into v_payload
    from public.lotterynet_master_state
    where config_key = 'cashier_limits:' || v_key
    order by updated_at desc nulls last
    limit 1;

    if v_payload is not null and v_payload <> '{}'::jsonb then
      return v_payload;
    end if;

    select value into v_raw
    from public.lotterynet_kv
    where key = 'cashier_limits:' || v_key
    limit 1;

    if jsonb_typeof(v_raw) = 'object' then
      return v_raw;
    end if;

    if v_raw is not null then
      begin
        v_payload := trim(both '"' from v_raw::text)::jsonb;
        if v_payload <> '{}'::jsonb then
          return v_payload;
        end if;
      exception when others then
        null;
      end;
    end if;
  end loop;

  return '{}'::jsonb;
end;
$function$;

create or replace function public.ln_ticket_sale_is_admin_actor(p_admin_key text, p_cashier_key text)
returns boolean
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_admin_lkeys text[];
  v_cashier_lkeys text[];
  v_actor jsonb;
  v_role text;
begin
  if nullif(trim(coalesce(p_cashier_key, '')), '') is null then
    return false;
  end if;

  v_actor := public.ln_actor_from_legacy_state(p_cashier_key);
  v_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source', ''));
  if v_role in ('admin', 'admins', 'master', 'masters') then
    return true;
  end if;

  select coalesce(array_agg(distinct lower(k)), array[]::text[])
  into v_admin_lkeys
  from unnest(public.ln_limit_self_keys(p_admin_key)) k;

  select coalesce(array_agg(distinct lower(k)), array[]::text[])
  into v_cashier_lkeys
  from unnest(public.ln_limit_self_keys(p_cashier_key)) k;

  return exists (
    select 1
    from unnest(v_cashier_lkeys) cashier_key
    where cashier_key = any(v_admin_lkeys)
  );
end;
$function$;

create or replace function public.ln_cashier_limit_config(p_admin_key text, p_cashier_key text)
returns jsonb
language plpgsql
set search_path to 'public'
as $function$
declare
  v_root jsonb := public.ln_cashier_limit_payload_for_admin(p_admin_key);
  v_defaults jsonb := coalesce(v_root -> 'defaults', '{}'::jsonb);
  v_admin_self jsonb := coalesce(v_root -> 'adminSelf', '{}'::jsonb);
  v_by_user jsonb := coalesce(v_root -> 'byUser', '{}'::jsonb);
  v_actor jsonb;
  v_role text;
  v_user_lkeys text[];
  v_row jsonb := null;
begin
  v_actor := public.ln_actor_from_legacy_state(p_cashier_key);
  v_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source', ''));

  select coalesce(array_agg(distinct lower(k)), array[]::text[])
  into v_user_lkeys
  from unnest(public.ln_limit_self_keys(p_cashier_key)) k;

  if jsonb_typeof(v_by_user) = 'object' then
    select value into v_row
    from jsonb_each(v_by_user)
    where lower(key) = any(v_user_lkeys)
    limit 1;
  end if;

  if v_role in ('admin', 'admins', 'master', 'masters')
    or public.ln_ticket_sale_is_admin_actor(p_admin_key, p_cashier_key) then
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

create or replace function public.ln_enforce_ticket_item_sale_limit()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  v_ticket public.tickets%rowtype;
  v_config jsonb;
  v_limit numeric;
  v_bucket text;
  v_day date;
  v_sold numeric;
  v_is_admin_sale boolean := false;
  v_admin_lkeys text[];
begin
  if new.play_type is null then
    return new;
  end if;

  select * into v_ticket
  from public.tickets
  where id = new.ticket_id;

  if not found then
    return new;
  end if;

  v_config := public.ln_cashier_limit_config(v_ticket.admin_key, v_ticket.cashier_key);
  v_limit := public.ln_cashier_play_sale_limit(v_config, new.play_type);
  if v_limit <= 0 then
    return new;
  end if;

  v_is_admin_sale := public.ln_ticket_sale_is_admin_actor(v_ticket.admin_key, v_ticket.cashier_key);
  select coalesce(array_agg(distinct lower(k)), array[]::text[])
  into v_admin_lkeys
  from unnest(public.ln_limit_self_keys(v_ticket.admin_key)) k;

  v_bucket := public.ln_sale_limit_bucket(new.play_type, coalesce(new.normalized_number, new.play_numbers));
  v_day := coalesce(v_ticket.draw_date_real, public.ln_day_key_to_iso(v_ticket.legacy_day_key)::date, current_date);

  select coalesce(sum(ti.amount), 0) into v_sold
  from public.ticket_items ti
  join public.tickets t on t.id = ti.ticket_id
  where lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys)
    and (
      (new.lottery_id is not null and ti.lottery_id = new.lottery_id)
      or (
        new.lottery_id is null
        and nullif(trim(coalesce(new.lottery_legacy_id, '')), '') is not null
        and lower(coalesce(ti.lottery_legacy_id, '')) = lower(coalesce(new.lottery_legacy_id, ''))
      )
    )
    and coalesce(t.draw_date_real, public.ln_day_key_to_iso(t.legacy_day_key)::date, current_date) = v_day
    and t.deleted_at is null
    and upper(coalesce(t.status, t.estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID')
    and ti.play_type = new.play_type
    and public.ln_sale_limit_bucket(ti.play_type, coalesce(ti.normalized_number, ti.play_numbers)) = v_bucket
    and (
      (
        v_is_admin_sale
        and lower(coalesce(t.cashier_key, '')) = any(v_admin_lkeys)
      )
      or (
        not v_is_admin_sale
        and nullif(trim(coalesce(t.cashier_key, '')), '') is not null
        and lower(coalesce(t.cashier_key, '')) <> all(v_admin_lkeys)
      )
    );

  if v_sold + coalesce(new.amount, 0) > v_limit then
    raise exception 'Limite agotado para esta jugada';
  end if;

  return new;
end;
$function$;

revoke all on function public.ln_limit_self_keys(text) from public, anon, authenticated;
revoke all on function public.ln_cashier_limit_payload_for_admin(text) from public, anon, authenticated;
revoke all on function public.ln_ticket_sale_is_admin_actor(text, text) from public, anon, authenticated;
grant execute on function public.ln_limit_self_keys(text) to service_role;
grant execute on function public.ln_cashier_limit_payload_for_admin(text) to service_role;
grant execute on function public.ln_ticket_sale_is_admin_actor(text, text) to service_role;

commit;
