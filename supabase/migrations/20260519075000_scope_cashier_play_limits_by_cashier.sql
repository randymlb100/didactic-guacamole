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
    and lower(coalesce(t.cashier_key, '')) = lower(coalesce(v_ticket.cashier_key, ''))
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
