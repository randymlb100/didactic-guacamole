begin;

create or replace function public.ln_create_ticket_legacy_admin_limit_override(p_body jsonb)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_actor_role text := lower(trim(coalesce(p_body->>'actorRole', '')));
  v_actor_key text := trim(coalesce(p_body->>'actorKey', p_body->>'adminKey', ''));
  v_cashier_key text := trim(coalesce(p_body->>'cashierKey', ''));
  v_client_request_id text := trim(coalesce(p_body->>'clientRequestId', ''));
  v_result jsonb;
  v_ticket_id uuid;
begin
  if v_actor_role not in ('admin', 'admins', 'master', 'masters') then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Solo admin puede autorizar limite agotado.');
  end if;

  if v_actor_key = '' or v_cashier_key = '' or lower(v_actor_key) = lower(v_cashier_key) then
    return jsonb_build_object('ok', false, 'status', 400, 'message', 'Autorizacion admin invalida.');
  end if;

  perform set_config('lotterynet.admin_limit_override', 'true', true);
  v_result := public.ln_create_ticket_legacy(p_body);

  if coalesce((v_result->>'ok')::boolean, false) then
    select id into v_ticket_id
    from public.tickets
    where client_request_id = v_client_request_id
    limit 1;

    if v_ticket_id is not null then
      update public.tickets
      set anti_fraud_metadata = coalesce(anti_fraud_metadata, '{}'::jsonb)
        || jsonb_build_object(
          'adminLimitOverride', true,
          'adminLimitOverrideActorKey', v_actor_key,
          'adminLimitOverrideCashierKey', v_cashier_key,
          'adminLimitOverrideAt', now()
        )
      where id = v_ticket_id;

      insert into public.ticket_antifraud_checks(
        client_request_id,
        ticket_id,
        actor_key,
        admin_key,
        draw_date,
        decision,
        reason,
        metadata
      )
      values (
        v_client_request_id,
        v_ticket_id,
        v_actor_key,
        trim(coalesce(p_body->>'adminKey', p_body->>'adminId', '')),
        nullif(public.ln_day_key_to_iso(trim(coalesce(p_body->>'drawDate', p_body->>'dayKey', ''))), '')::date,
        'allowed',
        'Admin autorizo venta sobre limite de cajero',
        jsonb_build_object('cashierKey', v_cashier_key, 'phase', 'admin_limit_override')
      );
    end if;

    return v_result || jsonb_build_object('adminLimitOverride', true);
  end if;

  return v_result;
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

revoke all on function public.ln_create_ticket_legacy_admin_limit_override(jsonb) from public, anon, authenticated;
grant execute on function public.ln_create_ticket_legacy_admin_limit_override(jsonb) to service_role;

commit;
