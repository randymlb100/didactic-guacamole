begin;

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
  v_remaining numeric;
  v_is_admin_sale boolean := false;
  v_admin_lkeys text[];
  v_lottery_label text;
  v_play_label text;
  v_number_label text;
  v_lottery_keys text[];
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

  if current_setting('lotterynet.admin_limit_override', true) = 'true' then
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
  v_lottery_keys := array_remove(array[
    lower(nullif(trim(new.lottery_id::text), '')),
    lower(nullif(trim(coalesce(new.lottery_legacy_id, '')), ''))
  ], null);

  select coalesce(sum(ti.amount), 0) into v_sold
  from public.ticket_items ti
  join public.tickets t on t.id = ti.ticket_id
  where lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys)
    and array_length(v_lottery_keys, 1) is not null
    and (
      lower(coalesce(ti.lottery_id::text, '')) = any(v_lottery_keys)
      or lower(coalesce(ti.lottery_legacy_id, '')) = any(v_lottery_keys)
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
    v_remaining := greatest(v_limit - v_sold, 0);
    v_lottery_label := coalesce(
      nullif(trim(new.lottery_name), ''),
      nullif(trim(new.lottery_legacy_id), ''),
      nullif(trim(new.lottery_id::text), ''),
      'Loteria'
    );
    v_number_label := coalesce(nullif(trim(new.normalized_number), ''), nullif(trim(new.play_numbers), ''), v_bucket);
    v_play_label := case upper(coalesce(new.play_type::text, ''))
      when 'Q' then 'Quiniela'
      when 'P' then 'Pale'
      when 'SP' then 'Super Pale'
      when 'T' then 'Tripleta'
      when 'PICK3_STRAIGHT' then 'Pick 3'
      when 'PICK3_BOX' then 'Pick 3 Box'
      when 'PICK4_STRAIGHT' then 'Pick 4'
      when 'PICK4_BOX' then 'Pick 4 Box'
      else coalesce(new.play_type::text, 'Jugada')
    end;

    raise exception 'Numero lleno: % % en %. Disponible %, venta %',
      v_play_label,
      v_number_label,
      v_lottery_label,
      trim(to_char(v_remaining, 'FM999999999990')),
      trim(to_char(coalesce(new.amount, 0), 'FM999999999990'));
  end if;

  return new;
end;
$function$;

commit;
