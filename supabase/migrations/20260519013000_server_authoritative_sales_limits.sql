create or replace function public.ln_play_type_from_text(p_value text)
returns public.ln_play_type
language plpgsql
immutable
as $function$
declare
  v text := upper(regexp_replace(translate(coalesce(p_value,''), 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou'), '[ _-]', '', 'g'));
begin
  if v in ('Q','QUINIELA') then return 'QUINIELA'::public.ln_play_type; end if;
  if v in ('P','PALE') then return 'PALE'::public.ln_play_type; end if;
  if v in ('T','TRIPLETA') then return 'TRIPLETA'::public.ln_play_type; end if;
  if v in ('SP','SUPERPALE') then return 'SUPER_PALE'::public.ln_play_type; end if;
  if v in ('P3','PICK3','PICK3STRAIGHT') then return 'PICK3_STRAIGHT'::public.ln_play_type; end if;
  if v in ('P3BOX','P3B','PICK3BOX') then return 'PICK3_BOX'::public.ln_play_type; end if;
  if v in ('P4','PICK4','PICK4STRAIGHT') then return 'PICK4_STRAIGHT'::public.ln_play_type; end if;
  if v in ('P4BOX','P4B','PICK4BOX') then return 'PICK4_BOX'::public.ln_play_type; end if;
  return null;
end;
$function$;

create or replace function public.ln_sale_limit_bucket(p_play_type public.ln_play_type, p_number_value text)
returns text
language plpgsql
immutable
as $function$
declare
  v_digits text := regexp_replace(coalesce(p_number_value, ''), '\D', '', 'g');
begin
  case p_play_type
    when 'QUINIELA'::public.ln_play_type then
      return right('00' || v_digits, 2);
    when 'PALE'::public.ln_play_type,
         'SUPER_PALE'::public.ln_play_type,
         'TRIPLETA'::public.ln_play_type then
      return v_digits;
    when 'PICK3_STRAIGHT'::public.ln_play_type,
         'PICK3_BOX'::public.ln_play_type,
         'PICK4_STRAIGHT'::public.ln_play_type,
         'PICK4_BOX'::public.ln_play_type then
      return '*';
    else
      return trim(coalesce(p_number_value, ''));
  end case;
end;
$function$;

create or replace function public.ln_jsonb_number(p_json jsonb, p_keys text[], p_default numeric default 0)
returns numeric
language plpgsql
immutable
as $function$
declare
  v_key text;
  v_value text;
begin
  foreach v_key in array p_keys loop
    v_value := nullif(trim(coalesce(p_json ->> v_key, '')), '');
    if v_value is not null then
      begin
        return greatest(v_value::numeric, 0);
      exception when others then
        null;
      end;
    end if;
  end loop;
  return greatest(coalesce(p_default, 0), 0);
end;
$function$;

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
  v_row jsonb := null;
  v_by_user jsonb := '{}'::jsonb;
  v_actor jsonb;
  v_role text;
  v_identity text;
begin
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

  v_defaults := coalesce(v_root -> 'defaults', '{}'::jsonb);
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

  if v_role in ('admin','admins','master','masters') and v_row is null then
    return '{}'::jsonb;
  end if;

  return coalesce(v_defaults, '{}'::jsonb) || coalesce(v_row, '{}'::jsonb);
end;
$function$;

create or replace function public.ln_cashier_day_sale_limit(p_config jsonb)
returns numeric
language sql
immutable
as $function$
  select public.ln_jsonb_number(coalesce(p_config, '{}'::jsonb), array['daySale','day_sale'], 0);
$function$;

create or replace function public.ln_cashier_play_sale_limit(p_config jsonb, p_play_type public.ln_play_type)
returns numeric
language plpgsql
immutable
as $function$
begin
  case p_play_type
    when 'QUINIELA'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['q','quiniela'], 0);
    when 'PALE'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['pale','p'], 0);
    when 'SUPER_PALE'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['sp','superPale','super_pale','p'], 0);
    when 'TRIPLETA'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['t','tripleta'], 0);
    when 'PICK3_STRAIGHT'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['p3','pick3Straight','pick3_straight'], 0);
    when 'PICK3_BOX'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['p3box','pick3Box','pick3_box','p3'], 0);
    when 'PICK4_STRAIGHT'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['p4','pick4Straight','pick4_straight'], 0);
    when 'PICK4_BOX'::public.ln_play_type then
      return public.ln_jsonb_number(p_config, array['p4box','pick4Box','pick4_box','p4'], 0);
    else
      return 0;
  end case;
end;
$function$;

create or replace function public.ln_enforce_ticket_cashier_day_sale_limit()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  v_config jsonb;
  v_limit numeric;
  v_sold numeric;
  v_day date;
begin
  v_config := public.ln_cashier_limit_config(new.admin_key, new.cashier_key);
  v_limit := public.ln_cashier_day_sale_limit(v_config);
  if v_limit <= 0 then
    return new;
  end if;

  v_day := coalesce(new.draw_date_real, public.ln_day_key_to_iso(new.legacy_day_key)::date, current_date);

  select coalesce(sum(coalesce(total_amount, monto, 0)), 0) into v_sold
  from public.tickets
  where lower(coalesce(admin_key, '')) = lower(coalesce(new.admin_key, ''))
    and lower(coalesce(cashier_key, '')) = lower(coalesce(new.cashier_key, ''))
    and coalesce(draw_date_real, public.ln_day_key_to_iso(legacy_day_key)::date, current_date) = v_day
    and deleted_at is null
    and upper(coalesce(status, estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID');

  if v_sold + coalesce(new.total_amount, new.monto, 0) > v_limit then
    raise exception 'Tope diario del cajero alcanzado';
  end if;

  return new;
end;
$function$;

drop trigger if exists trg_ln_ticket_cashier_day_sale_limit on public.tickets;

create trigger trg_ln_ticket_cashier_day_sale_limit
before insert on public.tickets
for each row
execute function public.ln_enforce_ticket_cashier_day_sale_limit();

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

  v_bucket := public.ln_sale_limit_bucket(new.play_type, coalesce(new.normalized_number, new.play_numbers));
  v_day := coalesce(v_ticket.draw_date_real, public.ln_day_key_to_iso(v_ticket.legacy_day_key)::date, current_date);

  select coalesce(sum(ti.amount), 0) into v_sold
  from public.ticket_items ti
  join public.tickets t on t.id = ti.ticket_id
  where lower(coalesce(t.admin_key, '')) = lower(coalesce(v_ticket.admin_key, ''))
    and coalesce(t.draw_date_real, public.ln_day_key_to_iso(t.legacy_day_key)::date, current_date) = v_day
    and t.deleted_at is null
    and upper(coalesce(t.status, t.estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID')
    and ti.play_type = new.play_type
    and public.ln_sale_limit_bucket(ti.play_type, coalesce(ti.normalized_number, ti.play_numbers)) = v_bucket;

  if v_sold + coalesce(new.amount, 0) > v_limit then
    raise exception 'Limite agotado para esta jugada';
  end if;

  return new;
end;
$function$;

drop trigger if exists trg_ln_ticket_item_sale_limit on public.ticket_items;

create trigger trg_ln_ticket_item_sale_limit
before insert on public.ticket_items
for each row
execute function public.ln_enforce_ticket_item_sale_limit();

create or replace function public.ln_reserve_number_limit(
  p_client_request_id text,
  p_admin_key text,
  p_sorteo_id uuid,
  p_draw_date date,
  p_play_type public.ln_play_type,
  p_number_value text,
  p_amount numeric
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_limit record;
  v_consumo record;
  v_existing record;
  v_remaining numeric;
  v_number_value text := public.ln_sale_limit_bucket(p_play_type, p_number_value);
begin
  if p_client_request_id is null or length(trim(p_client_request_id)) < 6 then
    return jsonb_build_object('allowed', false, 'reason', 'Intento de venta sin identificador seguro');
  end if;
  if p_amount is null or p_amount <= 0 then
    return jsonb_build_object('allowed', false, 'reason', 'Monto invalido');
  end if;

  select * into v_existing
    from public.ticket_limit_reservations
   where client_request_id = p_client_request_id
     and sorteo_id = p_sorteo_id
     and draw_date = p_draw_date
     and play_type = p_play_type
     and number_value = v_number_value
     and status <> 'released'
   limit 1;

  if found then
    select * into v_consumo
      from public.limites_numeros_consumo
     where admin_key = p_admin_key and sorteo_id = p_sorteo_id and draw_date = p_draw_date
       and play_type = p_play_type and number_value = v_number_value;
    return jsonb_build_object('allowed', true, 'duplicate', true, 'remaining', greatest(coalesce((select max_amount from public.limites_numeros where admin_key=p_admin_key and sorteo_id=p_sorteo_id and play_type=p_play_type and number_value=v_number_value and active order by updated_at desc limit 1), 999999999) - coalesce(v_consumo.sold_amount,0), 0));
  end if;

  select * into v_limit
    from public.limites_numeros
   where admin_key = p_admin_key
     and sorteo_id = p_sorteo_id
     and play_type = p_play_type
     and number_value = v_number_value
     and active = true
   order by updated_at desc
   limit 1
   for update;

  if found and coalesce(v_limit.blocked, false) = true then
    return jsonb_build_object('allowed', false, 'reason', 'Numero bloqueado');
  end if;

  insert into public.limites_numeros_consumo (admin_key, sorteo_id, draw_date, play_type, number_value, sold_amount)
  values (p_admin_key, p_sorteo_id, p_draw_date, p_play_type, v_number_value, 0)
  on conflict (admin_key, sorteo_id, draw_date, play_type, number_value) do nothing;

  select * into v_consumo
    from public.limites_numeros_consumo
   where admin_key = p_admin_key
     and sorteo_id = p_sorteo_id
     and draw_date = p_draw_date
     and play_type = p_play_type
     and number_value = v_number_value
   for update;

  if found and v_limit.max_amount is not null and (coalesce(v_consumo.sold_amount, 0) + p_amount) > v_limit.max_amount then
    v_remaining := greatest(v_limit.max_amount - coalesce(v_consumo.sold_amount, 0), 0);
    return jsonb_build_object('allowed', false, 'reason', 'Limite agotado para este numero', 'remaining', v_remaining, 'maxAmount', v_limit.max_amount, 'soldAmount', v_consumo.sold_amount);
  end if;

  update public.limites_numeros_consumo
     set sold_amount = sold_amount + p_amount,
         updated_at = now()
   where id = v_consumo.id
   returning * into v_consumo;

  insert into public.ticket_limit_reservations (client_request_id, admin_key, sorteo_id, draw_date, play_type, number_value, amount, status)
  values (p_client_request_id, p_admin_key, p_sorteo_id, p_draw_date, p_play_type, v_number_value, p_amount, 'reserved');

  if found and v_limit.max_amount is not null then
    v_remaining := greatest(v_limit.max_amount - v_consumo.sold_amount, 0);
  else
    v_remaining := null;
  end if;

  return jsonb_build_object('allowed', true, 'remaining', v_remaining, 'soldAmount', v_consumo.sold_amount, 'maxAmount', v_limit.max_amount, 'numberValue', v_number_value);
exception
  when unique_violation then
    return jsonb_build_object('allowed', true, 'duplicate', true, 'reason', 'Venta ya procesada');
end;
$function$;
