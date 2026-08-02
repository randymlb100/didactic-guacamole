begin;

-- Defaults belong to the cashier configuration. A pool must be explicit.
create or replace function public.ln_cashier_pool_limit_config(p_admin_key text)
returns jsonb language plpgsql stable security definer set search_path to 'public'
as $function$
declare v_payload jsonb;
begin
  v_payload := public.ln_cashier_limit_payload_for_admin(p_admin_key);
  return case when jsonb_typeof(v_payload -> 'pool') = 'object'
    then v_payload -> 'pool' else '{}'::jsonb end;
end;
$function$;

create or replace function public.ln_enforce_ticket_item_sale_limit()
returns trigger language plpgsql set search_path to 'public'
as $function$
declare
  v_ticket public.tickets%rowtype;
  v_personal_config jsonb; v_pool_config jsonb;
  v_personal_limit numeric; v_pool_limit numeric;
  v_bucket text; v_day date; v_sold numeric;
  v_is_admin_sale boolean := false; v_admin_lkeys text[]; v_lottery_keys text[];
  v_amount numeric := coalesce(new.amount, 0);
begin
  if new.play_type is null then return new; end if;
  select * into v_ticket from public.tickets where id = new.ticket_id;
  if not found then return new; end if;
  if current_setting('lotterynet.admin_limit_override', true) = 'true' then return new; end if;

  v_is_admin_sale := public.ln_ticket_sale_is_admin_actor(v_ticket.admin_key, v_ticket.cashier_key);
  v_personal_config := public.ln_cashier_limit_config(v_ticket.admin_key, v_ticket.cashier_key);
  v_personal_limit := public.ln_cashier_play_sale_limit(v_personal_config, new.play_type);
  v_pool_config := public.ln_cashier_pool_limit_config(v_ticket.admin_key);
  v_pool_limit := public.ln_cashier_play_sale_limit(v_pool_config, new.play_type);
  select coalesce(array_agg(distinct lower(k)), array[]::text[]) into v_admin_lkeys
    from unnest(public.ln_limit_self_keys(v_ticket.admin_key)) k;
  v_bucket := public.ln_sale_limit_bucket(new.play_type, coalesce(new.normalized_number, new.play_numbers));
  v_day := coalesce(v_ticket.draw_date_real, public.ln_day_key_to_iso(v_ticket.legacy_day_key)::date, current_date);
  v_lottery_keys := array_remove(array[lower(nullif(trim(new.lottery_id::text), '')), lower(nullif(trim(coalesce(new.lottery_legacy_id, '')), ''))], null);

  -- Admin sales retain their existing admin/self limit and do not consume cashier pool.
  if v_is_admin_sale then
    if v_personal_limit <= 0 then return new; end if;
    select coalesce(sum(ti.amount), 0) into v_sold from public.ticket_items ti join public.tickets t on t.id = ti.ticket_id
    where lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys)
      and array_length(v_lottery_keys, 1) is not null and (lower(coalesce(ti.lottery_id::text, '')) = any(v_lottery_keys) or lower(coalesce(ti.lottery_legacy_id, '')) = any(v_lottery_keys))
      and coalesce(t.draw_date_real, public.ln_day_key_to_iso(t.legacy_day_key)::date, current_date) = v_day and t.deleted_at is null
      and upper(coalesce(t.status, t.estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID') and ti.play_type = new.play_type
      and public.ln_sale_limit_bucket(ti.play_type, coalesce(ti.normalized_number, ti.play_numbers)) = v_bucket
      and lower(coalesce(t.cashier_key, '')) = any(v_admin_lkeys);
    if v_sold + v_amount > v_personal_limit then raise exception 'Limite administrativo agotado para esta jugada'; end if;
    return new;
  end if;

  -- Personal limit: only the current cashier, scoped to lottery/play/number.
  if v_personal_limit > 0 then
    select coalesce(sum(ti.amount), 0) into v_sold from public.ticket_items ti join public.tickets t on t.id = ti.ticket_id
    where lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys) and lower(coalesce(t.cashier_key, '')) = lower(coalesce(v_ticket.cashier_key, ''))
      and array_length(v_lottery_keys, 1) is not null and (lower(coalesce(ti.lottery_id::text, '')) = any(v_lottery_keys) or lower(coalesce(ti.lottery_legacy_id, '')) = any(v_lottery_keys))
      and coalesce(t.draw_date_real, public.ln_day_key_to_iso(t.legacy_day_key)::date, current_date) = v_day and t.deleted_at is null
      and upper(coalesce(t.status, t.estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID') and ti.play_type = new.play_type
      and public.ln_sale_limit_bucket(ti.play_type, coalesce(ti.normalized_number, ti.play_numbers)) = v_bucket;
    if v_sold + v_amount > v_personal_limit then raise exception 'Limite personal agotado para esta jugada'; end if;
  end if;

  -- Pool limit: all non-admin cashiers share the same lottery/play/number bucket.
  if v_pool_limit > 0 then
    select coalesce(sum(ti.amount), 0) into v_sold from public.ticket_items ti join public.tickets t on t.id = ti.ticket_id
    where lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys) and nullif(trim(coalesce(t.cashier_key, '')), '') is not null
      and lower(coalesce(t.cashier_key, '')) <> all(v_admin_lkeys) and array_length(v_lottery_keys, 1) is not null
      and (lower(coalesce(ti.lottery_id::text, '')) = any(v_lottery_keys) or lower(coalesce(ti.lottery_legacy_id, '')) = any(v_lottery_keys))
      and coalesce(t.draw_date_real, public.ln_day_key_to_iso(t.legacy_day_key)::date, current_date) = v_day and t.deleted_at is null
      and upper(coalesce(t.status, t.estado, '')) not in ('BORRADO','ANULADO','INVALIDADO','VOIDED','NULLED','INVALID') and ti.play_type = new.play_type
      and public.ln_sale_limit_bucket(ti.play_type, coalesce(ti.normalized_number, ti.play_numbers)) = v_bucket;
    if v_sold + v_amount > v_pool_limit then raise exception 'Pool agotado para esta jugada'; end if;
  end if;
  return new;
end;
$function$;

revoke all on function public.ln_cashier_pool_limit_config(text) from public, anon, authenticated;
grant execute on function public.ln_cashier_pool_limit_config(text) to service_role;
commit;
