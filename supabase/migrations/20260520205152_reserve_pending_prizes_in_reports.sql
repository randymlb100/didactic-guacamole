create or replace function public.ln_legacy_report(p_body jsonb)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_actor_key text := nullif(coalesce(p_body ->> 'actorKey', p_body ->> 'adminKey', p_body ->> 'cashierKey', p_body ->> 'supervisorKey'), '');
  v_actor jsonb;
  v_role text;
  v_admin_key text := nullif(p_body ->> 'adminKey', '');
  v_cashier_key text := nullif(p_body ->> 'cashierKey', '');
  v_supervisor_key text := nullif(p_body ->> 'supervisorKey', '');
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

  with scoped as (
    select
      t.*,
      public.ln_legacy_ticket_report_day(t.legacy_day_key, t.draw_date_real, t.server_created_at) as report_day,
      public.ln_legacy_commission_rate(t.cashier_key, t.admin_key) as commission_rate
    from public.tickets t
    where (v_admin_key is null or lower(coalesce(t.admin_key, '')) = lower(v_admin_key) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'id', '')) or lower(coalesce(t.admin_key, '')) = lower(coalesce(v_actor ->> 'user', '')))
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = lower(v_cashier_key))
      and (v_supervisor_key is null or lower(coalesce(t.supervisor_key, '')) = lower(v_supervisor_key))
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
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = lower(v_cashier_key))
      and (v_supervisor_key is null or lower(coalesce(t.supervisor_key, '')) = lower(v_supervisor_key))
      and t.deleted_at is null
  )
  select coalesce(jsonb_agg(row_to_json(x)::jsonb order by x.cashier_key), '[]'::jsonb)
    into v_cashiers
  from (
    select coalesce(t.cashier_key, 'sin-cajero') as cashier_key,
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
  ) x;

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
