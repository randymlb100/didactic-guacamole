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
  v_actor jsonb;
  v_actor_role text;
  v_is_admin_sale boolean := false;
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

  v_actor := public.ln_actor_from_legacy_state(v_ticket.cashier_key);
  v_actor_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source', ''));
  v_is_admin_sale := v_actor_role in ('admin', 'admins', 'master', 'masters')
    or (
      trim(coalesce(v_ticket.cashier_key, '')) <> ''
      and lower(trim(coalesce(v_ticket.cashier_key, ''))) = lower(trim(coalesce(v_ticket.admin_key, '')))
    );

  v_bucket := public.ln_sale_limit_bucket(new.play_type, coalesce(new.normalized_number, new.play_numbers));
  v_day := coalesce(v_ticket.draw_date_real, public.ln_day_key_to_iso(v_ticket.legacy_day_key)::date, current_date);

  select coalesce(sum(ti.amount), 0) into v_sold
  from public.ticket_items ti
  join public.tickets t on t.id = ti.ticket_id
  where lower(coalesce(t.admin_key, '')) = lower(coalesce(v_ticket.admin_key, ''))
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
        and lower(coalesce(t.cashier_key, '')) = lower(coalesce(v_ticket.cashier_key, ''))
      )
      or (
        not v_is_admin_sale
        and nullif(trim(coalesce(t.cashier_key, '')), '') is not null
        and lower(coalesce(t.cashier_key, '')) <> lower(coalesce(t.admin_key, ''))
      )
    );

  if v_sold + coalesce(new.amount, 0) > v_limit then
    raise exception 'Limite agotado para esta jugada';
  end if;

  return new;
end;
$function$;

commit;
