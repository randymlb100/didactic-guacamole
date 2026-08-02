begin;

create or replace function public.ln_legacy_report_actor_identity(p_actor_key text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor jsonb;
  v_raw text := trim(coalesce(p_actor_key, ''));
  v_raw_norm text := lower(trim(coalesce(p_actor_key, '')));
  v_id text;
  v_user text;
  v_username text;
  v_display text;
  v_canonical_key text;
  v_canonical_norm text;
begin
  if v_raw_norm = '' or v_raw_norm = 'sin-cajero' then
    return jsonb_build_object(
      'canonicalKey', 'sin-cajero',
      'canonicalNorm', 'sin-cajero',
      'displayName', 'Sin cajero',
      'rawKey', v_raw
    );
  end if;

  v_actor := public.ln_actor_from_legacy_state(v_raw);
  v_id := nullif(trim(coalesce(v_actor ->> 'id', '')), '');
  v_user := nullif(trim(coalesce(v_actor ->> 'user', '')), '');
  v_username := nullif(trim(coalesce(v_actor ->> 'username', '')), '');
  v_display := nullif(trim(coalesce(v_actor ->> 'displayName', '')), '');

  v_canonical_key := coalesce(v_id, v_user, v_username, v_display, v_raw);
  v_canonical_norm := lower(trim(v_canonical_key));
  v_display := coalesce(v_display, v_user, v_username, v_id, v_raw);

  return jsonb_build_object(
    'canonicalKey', v_canonical_key,
    'canonicalNorm', v_canonical_norm,
    'displayName', v_display,
    'rawKey', v_raw,
    'actorId', v_id,
    'actorUser', coalesce(v_user, v_username),
    'source', coalesce(v_actor ->> '_source', ''),
    'aliases', to_jsonb(public.ln_legacy_actor_aliases(v_raw))
  );
end;
$function$;

revoke all on function public.ln_legacy_report_actor_identity(text) from public, anon, authenticated;
grant execute on function public.ln_legacy_report_actor_identity(text) to service_role;

create or replace function public.ln_legacy_report(p_body jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor_key text := nullif(coalesce(p_body ->> 'actorKey', p_body ->> 'adminKey', p_body ->> 'cashierKey', p_body ->> 'supervisorKey'), '');
  v_actor jsonb;
  v_role text;
  v_admin_key text := nullif(p_body ->> 'adminKey', '');
  v_cashier_key text := nullif(p_body ->> 'cashierKey', '');
  v_supervisor_key text := nullif(p_body ->> 'supervisorKey', '');
  v_cashier_keys text[] := array[]::text[];
  v_supervisor_cashier_keys text[] := array[]::text[];
  v_from date := coalesce(nullif(p_body ->> 'from', '')::date, current_date);
  v_to date := coalesce(nullif(p_body ->> 'to', '')::date, current_date);
  v_total_vendido numeric := 0;
  v_total_anulado numeric := 0;
  v_total_invalidado numeric := 0;
  v_total_pagado numeric := 0;
  v_total_premios numeric := 0;
  v_comision numeric := 0;
  v_supervisor_comision numeric := 0;
  v_neto numeric := 0;
  v_ticket_count integer := 0;
  v_cashiers jsonb := '[]'::jsonb;
begin
  if v_actor_key is null then
    return jsonb_build_object('ok', false, 'status', 400, 'message', 'Usuario requerido');
  end if;

  v_actor := public.ln_actor_from_legacy_state(v_actor_key);
  if v_actor is null then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Usuario no autorizado');
  end if;

  v_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source'));
  if coalesce((v_actor ->> 'activo')::boolean, true) = false then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Usuario bloqueado');
  end if;

  if v_role in ('cashier','cajero','cajeros','cashiers') then
    v_cashier_key := coalesce(v_cashier_key, coalesce(v_actor ->> 'user', v_actor ->> 'username', v_actor ->> 'id'));
    v_admin_key := coalesce(v_admin_key, v_actor ->> 'adminUser', v_actor ->> 'adminId');
  elsif v_role in ('supervisor','supervisores','supervisors') then
    v_supervisor_key := coalesce(v_supervisor_key, coalesce(v_actor ->> 'user', v_actor ->> 'username', v_actor ->> 'id'));
    v_admin_key := coalesce(v_admin_key, v_actor ->> 'adminUser', v_actor ->> 'adminId');
  elsif v_role in ('admin','admins') then
    v_admin_key := coalesce(v_admin_key, coalesce(v_actor ->> 'id', v_actor ->> 'user', v_actor ->> 'username'));
  end if;

  if v_cashier_key is not null then
    v_cashier_keys := public.ln_legacy_actor_aliases(v_cashier_key);
    if cardinality(v_cashier_keys) = 0 then
      v_cashier_keys := array[lower(trim(v_cashier_key))];
    end if;
  end if;

  if v_supervisor_key is not null then
    v_supervisor_cashier_keys := public.ln_legacy_supervisor_cashier_keys(v_supervisor_key);
  end if;

  with scoped as (
    select
      t.*,
      public.ln_legacy_ticket_report_day(t.legacy_day_key, t.draw_date_real, t.server_created_at) as report_day,
      public.ln_legacy_commission_rate(t.cashier_key, t.admin_key) as commission_rate
    from public.tickets t
    where (v_admin_key is null or lower(coalesce(t.admin_key, '')) = lower(v_admin_key) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'id', '')) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'user', '')))
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = any(v_cashier_keys))
      and (
        v_supervisor_key is null
        or lower(coalesce(t.supervisor_key, '')) = lower(v_supervisor_key)
        or lower(coalesce(t.cashier_key, '')) = any(v_supervisor_cashier_keys)
      )
      and t.deleted_at is null
  )
  select coalesce(sum(total_amount) filter (where upper(status) in ('VALIDO','VALID','GANADOR','PERDEDOR','PAGADO')), 0),
         coalesce(sum(total_amount) filter (where upper(status) in ('ANULADO','VOID','VOIDED','BORRADO','DELETED')), 0),
         coalesce(sum(total_amount) filter (where upper(status) in ('INVALIDADO','INVALID')), 0),
         coalesce(sum(payout_amount) filter (where upper(status) in ('PAGADO','PAID')), 0),
         coalesce(sum(payout_amount) filter (where upper(status) in ('GANADOR','WINNER','PENDING_WINNER','PAGADO','PAID')), 0),
         coalesce(sum(total_amount * commission_rate) filter (where upper(status) in ('VALIDO','VALID','GANADOR','PERDEDOR','PAGADO')), 0),
         count(*)::integer
    into v_total_vendido, v_total_anulado, v_total_invalidado, v_total_pagado, v_total_premios, v_comision, v_ticket_count
  from scoped
  where report_day between v_from and v_to;

  v_neto := v_total_vendido - v_total_anulado - v_total_invalidado - v_total_premios - v_comision - v_supervisor_comision;

  with scoped as (
    select
      t.*,
      public.ln_legacy_ticket_report_day(t.legacy_day_key, t.draw_date_real, t.server_created_at) as report_day,
      public.ln_legacy_commission_rate(t.cashier_key, t.admin_key) as commission_rate
    from public.tickets t
    where (v_admin_key is null or lower(coalesce(t.admin_key, '')) = lower(v_admin_key) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'id', '')) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'user', '')))
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = any(v_cashier_keys))
      and (
        v_supervisor_key is null
        or lower(coalesce(t.supervisor_key, '')) = lower(v_supervisor_key)
        or lower(coalesce(t.cashier_key, '')) = any(v_supervisor_cashier_keys)
      )
      and t.deleted_at is null
  ), raw_cashiers as (
    select
      coalesce(t.cashier_key, 'sin-cajero') as raw_cashier_key,
      count(*)::integer as tickets,
      coalesce(sum(t.total_amount) filter (where upper(t.status) in ('VALIDO','VALID','GANADOR','PERDEDOR','PAGADO')), 0) as vendido,
      coalesce(sum(t.total_amount) filter (where upper(t.status) in ('ANULADO','VOID','VOIDED','BORRADO','DELETED')), 0) as anulado,
      coalesce(sum(t.payout_amount) filter (where upper(t.status) in ('PAGADO','PAID')), 0) as pagado,
      coalesce(sum(t.payout_amount) filter (where upper(t.status) in ('GANADOR','WINNER','PENDING_WINNER')), 0) as pendiente,
      coalesce(sum(t.payout_amount) filter (where upper(t.status) in ('GANADOR','WINNER','PENDING_WINNER','PAGADO','PAID')), 0) as premios,
      coalesce(sum(t.total_amount * t.commission_rate) filter (where upper(t.status) in ('VALIDO','VALID','GANADOR','PERDEDOR','PAGADO')), 0) as comision
    from scoped t
    where t.report_day between v_from and v_to
    group by coalesce(t.cashier_key, 'sin-cajero')
  ), resolved_cashiers as (
    select
      coalesce(identity ->> 'canonicalNorm', lower(raw.raw_cashier_key)) as canonical_norm,
      coalesce(identity ->> 'canonicalKey', raw.raw_cashier_key) as canonical_key,
      coalesce(identity ->> 'displayName', raw.raw_cashier_key) as display_name,
      raw.*
    from raw_cashiers raw
    cross join lateral public.ln_legacy_report_actor_identity(raw.raw_cashier_key) identity
  ), cashier_totals as (
    select
      canonical_norm,
      max(canonical_key) as cashier_key,
      max(display_name) as cashier_label,
      to_jsonb(array_agg(distinct raw_cashier_key order by raw_cashier_key)) as raw_cashier_keys,
      sum(tickets)::integer as tickets,
      coalesce(sum(vendido), 0) as vendido,
      coalesce(sum(anulado), 0) as anulado,
      coalesce(sum(pagado), 0) as pagado,
      coalesce(sum(pendiente), 0) as pendiente,
      coalesce(sum(premios), 0) as premios,
      coalesce(sum(comision), 0) as comision
    from resolved_cashiers
    group by canonical_norm
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'cashier_key', x.cashier_key,
        'cashier_label', x.cashier_label,
        'raw_cashier_keys', x.raw_cashier_keys,
        'tickets', x.tickets,
        'vendido', x.vendido,
        'anulado', x.anulado,
        'pagado', x.pagado,
        'pendiente', x.pendiente,
        'premios', x.premios,
        'comision', x.comision
      )
      order by lower(x.cashier_label), x.cashier_key
    ),
    '[]'::jsonb
  )
    into v_cashiers
  from cashier_totals x;

  return jsonb_build_object(
    'ok', true,
    'status', 200,
    'from', v_from,
    'to', v_to,
    'filters', jsonb_build_object('adminKey', v_admin_key, 'cashierKey', v_cashier_key, 'supervisorKey', v_supervisor_key),
    'summary', jsonb_build_object(
      'tickets', v_ticket_count,
      'totalVendido', v_total_vendido,
      'totalAnulado', v_total_anulado,
      'totalInvalidado', v_total_invalidado,
      'totalPagado', v_total_pagado,
      'totalPremios', v_total_premios,
      'totalPendiente', greatest(v_total_premios - v_total_pagado, 0),
      'comision', v_comision,
      'supervisorComision', v_supervisor_comision,
      'gananciaNeta', v_neto
    ),
    'cashiers', v_cashiers
  );
end;
$function$;

revoke all on function public.ln_legacy_report(jsonb) from public, anon, authenticated;
grant execute on function public.ln_legacy_report(jsonb) to service_role;

commit;
