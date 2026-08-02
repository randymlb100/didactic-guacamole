begin;

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
  v_total_recargas numeric := 0;
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

  with scoped_recharges as (
    select distinct on (recharge_id)
      recharge_id,
      raw_cashier_key,
      amount,
      report_day
    from (
      select
        coalesce(
          nullif(r.client_request_id, ''),
          nullif(r.local_record ->> 'id', ''),
          nullif(r.provider_reference, ''),
          md5(concat_ws('|', r.owner_key, r.actor_user_id, r.actor_username, r.amount::text, r.created_at::text))
        ) as recharge_id,
        coalesce(
          nullif(r.local_record ->> 'userId', ''),
          nullif(r.actor_user_id, ''),
          nullif(r.local_record ->> 'userName', ''),
          nullif(r.actor_username, ''),
          'sin-cajero'
        ) as raw_cashier_key,
        coalesce(r.amount, 0) as amount,
        coalesce(
          case
            when nullif(r.local_record ->> 'createdAtEpochMs', '') ~ '^[0-9]+(\.[0-9]+)?$'
              then to_timestamp((r.local_record ->> 'createdAtEpochMs')::numeric / 1000.0) at time zone 'America/Santo_Domingo'
            when nullif(r.local_record ->> 'createdAtMs', '') ~ '^[0-9]+(\.[0-9]+)?$'
              then to_timestamp((r.local_record ->> 'createdAtMs')::numeric / 1000.0) at time zone 'America/Santo_Domingo'
            else null
          end,
          r.created_at at time zone 'America/Santo_Domingo'
        )::date as report_day,
        r.updated_at,
        r.created_at
      from public.lotterynet_recharge_requests r
      where lower(coalesce(r.status, '')) in ('completed','complete','success','ok','approved','paid')
        and coalesce(r.amount, 0) > 0
        and (
          v_admin_key is null
          or lower(coalesce(r.owner_key, '')) = lower(v_admin_key)
          or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'id', ''))
          or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'user', ''))
          or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(v_admin_key)
          or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(v_admin_key)
          or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(coalesce(v_actor ->> 'id', ''))
          or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(coalesce(v_actor ->> 'user', ''))
        )
        and (
          v_cashier_key is null
          or lower(coalesce(r.local_record ->> 'userId', r.actor_user_id, r.local_record ->> 'userName', r.actor_username, '')) = any(v_cashier_keys)
        )
        and (
          v_supervisor_key is null
          or lower(coalesce(r.local_record ->> 'userId', r.actor_user_id, r.local_record ->> 'userName', r.actor_username, '')) = any(v_supervisor_cashier_keys)
        )
    ) x
    where report_day between v_from and v_to
    order by recharge_id, updated_at desc nulls last, created_at desc
  )
  select coalesce(sum(amount), 0)
    into v_total_recargas
  from scoped_recharges;

  v_neto := v_total_vendido + v_total_recargas - v_total_anulado - v_total_invalidado - v_total_premios - v_comision - v_supervisor_comision;

  with ticket_scoped as (
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
    from ticket_scoped t
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
  ), ticket_totals as (
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
  ), raw_recharges as (
    select
      raw_cashier_key,
      count(*)::integer as recharge_count,
      coalesce(sum(amount), 0) as recargas
    from (
      select distinct on (recharge_id)
        recharge_id,
        raw_cashier_key,
        amount,
        report_day,
        updated_at,
        created_at
      from (
        select
          coalesce(
            nullif(r.client_request_id, ''),
            nullif(r.local_record ->> 'id', ''),
            nullif(r.provider_reference, ''),
            md5(concat_ws('|', r.owner_key, r.actor_user_id, r.actor_username, r.amount::text, r.created_at::text))
          ) as recharge_id,
          coalesce(
            nullif(r.local_record ->> 'userId', ''),
            nullif(r.actor_user_id, ''),
            nullif(r.local_record ->> 'userName', ''),
            nullif(r.actor_username, ''),
            'sin-cajero'
          ) as raw_cashier_key,
          coalesce(r.amount, 0) as amount,
          coalesce(
            case
              when nullif(r.local_record ->> 'createdAtEpochMs', '') ~ '^[0-9]+(\.[0-9]+)?$'
                then to_timestamp((r.local_record ->> 'createdAtEpochMs')::numeric / 1000.0) at time zone 'America/Santo_Domingo'
              when nullif(r.local_record ->> 'createdAtMs', '') ~ '^[0-9]+(\.[0-9]+)?$'
                then to_timestamp((r.local_record ->> 'createdAtMs')::numeric / 1000.0) at time zone 'America/Santo_Domingo'
              else null
            end,
            r.created_at at time zone 'America/Santo_Domingo'
          )::date as report_day,
          r.updated_at,
          r.created_at
        from public.lotterynet_recharge_requests r
        where lower(coalesce(r.status, '')) in ('completed','complete','success','ok','approved','paid')
          and coalesce(r.amount, 0) > 0
          and (
            v_admin_key is null
            or lower(coalesce(r.owner_key, '')) = lower(v_admin_key)
            or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'id', ''))
            or lower(coalesce(r.owner_key, '')) = lower(coalesce(v_actor ->> 'user', ''))
            or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(v_admin_key)
            or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(v_admin_key)
            or lower(coalesce(r.local_record ->> 'adminId', '')) = lower(coalesce(v_actor ->> 'id', ''))
            or lower(coalesce(r.local_record ->> 'adminUser', '')) = lower(coalesce(v_actor ->> 'user', ''))
          )
          and (
            v_cashier_key is null
            or lower(coalesce(r.local_record ->> 'userId', r.actor_user_id, r.local_record ->> 'userName', r.actor_username, '')) = any(v_cashier_keys)
          )
          and (
            v_supervisor_key is null
            or lower(coalesce(r.local_record ->> 'userId', r.actor_user_id, r.local_record ->> 'userName', r.actor_username, '')) = any(v_supervisor_cashier_keys)
          )
      ) rr
      where report_day between v_from and v_to
      order by recharge_id, updated_at desc nulls last, created_at desc
    ) unique_recharges
    group by raw_cashier_key
  ), resolved_recharges as (
    select
      coalesce(identity ->> 'canonicalNorm', lower(raw.raw_cashier_key)) as canonical_norm,
      coalesce(identity ->> 'canonicalKey', raw.raw_cashier_key) as canonical_key,
      coalesce(identity ->> 'displayName', raw.raw_cashier_key) as display_name,
      raw.*
    from raw_recharges raw
    cross join lateral public.ln_legacy_report_actor_identity(raw.raw_cashier_key) identity
  ), recharge_totals as (
    select
      canonical_norm,
      max(canonical_key) as cashier_key,
      max(display_name) as cashier_label,
      to_jsonb(array_agg(distinct raw_cashier_key order by raw_cashier_key)) as raw_cashier_keys,
      coalesce(sum(recargas), 0) as recargas,
      sum(recharge_count)::integer as recharge_count
    from resolved_recharges
    group by canonical_norm
  ), cashier_keys as (
    select canonical_norm from ticket_totals
    union
    select canonical_norm from recharge_totals
  ), raw_keys as (
    select canonical_norm, jsonb_agg(distinct raw_key order by raw_key) as raw_cashier_keys
    from (
      select canonical_norm, jsonb_array_elements_text(raw_cashier_keys) as raw_key from ticket_totals
      union
      select canonical_norm, jsonb_array_elements_text(raw_cashier_keys) as raw_key from recharge_totals
    ) keys
    group by canonical_norm
  ), cashier_totals as (
    select
      k.canonical_norm,
      coalesce(t.cashier_key, r.cashier_key, 'sin-cajero') as cashier_key,
      coalesce(t.cashier_label, r.cashier_label, 'Sin cajero') as cashier_label,
      coalesce(keys.raw_cashier_keys, '[]'::jsonb) as raw_cashier_keys,
      coalesce(t.tickets, 0) as tickets,
      coalesce(t.vendido, 0) as vendido,
      coalesce(r.recargas, 0) as recargas,
      coalesce(t.anulado, 0) as anulado,
      coalesce(t.pagado, 0) as pagado,
      coalesce(t.pendiente, 0) as pendiente,
      coalesce(t.premios, 0) as premios,
      coalesce(t.comision, 0) as comision,
      coalesce(r.recharge_count, 0) as recharge_count
    from cashier_keys k
    left join ticket_totals t on t.canonical_norm = k.canonical_norm
    left join recharge_totals r on r.canonical_norm = k.canonical_norm
    left join raw_keys keys on keys.canonical_norm = k.canonical_norm
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'cashier_key', x.cashier_key,
        'cashier_label', x.cashier_label,
        'raw_cashier_keys', x.raw_cashier_keys,
        'tickets', x.tickets,
        'vendido', x.vendido,
        'recargas', x.recargas,
        'rechargeCount', x.recharge_count,
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
      'totalRecargas', v_total_recargas,
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
