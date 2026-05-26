-- Fix the three regressions found by tools/qa/bugfix-regression-smoke.mjs:
-- 1) cashier limits saved through update-master-config live in lotterynet_master_state,
--    while sale validation was reading only lotterynet_kv.
-- 2) pick result snapshots need the same narrow public write policy as lottery results.
-- 3) supervisor reports must include assigned cashiers even when old tickets have no supervisor_key.

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
  select payload into v_root
  from public.lotterynet_master_state
  where config_key = 'cashier_limits:' || trim(coalesce(p_admin_key, ''))
  limit 1;

  if v_root is null or v_root = '{}'::jsonb then
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
  end if;

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

grant execute on function public.ln_cashier_limit_config(text, text) to anon, authenticated, service_role;

drop policy if exists "pick_results_by_day_insert_public" on public.lotterynet_pick_results_by_day;
drop policy if exists "pick_results_by_day_update_public" on public.lotterynet_pick_results_by_day;

create policy "pick_results_by_day_insert_public"
on public.lotterynet_pick_results_by_day
for insert
to anon, authenticated
with check (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

create policy "pick_results_by_day_update_public"
on public.lotterynet_pick_results_by_day
for update
to anon, authenticated
using (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
with check (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

grant select, insert, update, delete on public.lotterynet_pick_results_by_day to anon, authenticated;

create or replace function public.ln_legacy_supervisor_cashier_keys(p_supervisor_key text)
returns text[]
language plpgsql
stable
set search_path to 'public'
as $function$
declare
  v_users_payload jsonb;
  v_supervisor jsonb;
  v_supervisor_aliases text[] := array[]::text[];
  v_cashier_keys text[] := array[]::text[];
  v_item jsonb;
  v_text text;
begin
  if nullif(trim(coalesce(p_supervisor_key, '')), '') is null then
    return array[]::text[];
  end if;

  select payload into v_users_payload
  from public.lotterynet_users_state
  where scope = 'global';

  if v_users_payload is null then
    return array[]::text[];
  end if;

  select item into v_supervisor
  from (
    select jsonb_array_elements(coalesce(v_users_payload->'supervisores','[]'::jsonb)) item
    union all select jsonb_array_elements(coalesce(v_users_payload->'supervisors','[]'::jsonb))
    union all select jsonb_array_elements(coalesce(v_users_payload->'users','[]'::jsonb))
  ) all_supervisors
  where lower(trim(coalesce(item->>'id',''))) = lower(trim(p_supervisor_key))
     or lower(trim(coalesce(item->>'user',''))) = lower(trim(p_supervisor_key))
     or lower(trim(coalesce(item->>'username',''))) = lower(trim(p_supervisor_key))
  limit 1;

  v_supervisor_aliases := array_remove(array[
    lower(trim(coalesce(p_supervisor_key, ''))),
    lower(trim(coalesce(v_supervisor ->> 'id', ''))),
    lower(trim(coalesce(v_supervisor ->> 'user', ''))),
    lower(trim(coalesce(v_supervisor ->> 'username', '')))
  ], '');

  if v_supervisor is not null then
    for v_item in select value from jsonb_array_elements(coalesce(v_supervisor->'assignedCashiers', '[]'::jsonb)) loop
      v_text := lower(trim(both '"' from v_item::text));
      if v_text <> '' then
        v_cashier_keys := array_append(v_cashier_keys, v_text);
      end if;
    end loop;
  end if;

  for v_item in
    select item
    from (
      select jsonb_array_elements(coalesce(v_users_payload->'users','[]'::jsonb)) item
      union all select jsonb_array_elements(coalesce(v_users_payload->'cajeros','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(v_users_payload->'cashiers','[]'::jsonb))
    ) all_cashiers
  loop
    if exists (
      select 1
      from jsonb_array_elements_text(coalesce(v_item->'supervisorUsers', '[]'::jsonb)) value
      where lower(trim(value)) = any(v_supervisor_aliases)
    ) or exists (
      select 1
      from jsonb_array_elements_text(coalesce(v_item->'supervisorIds', '[]'::jsonb)) value
      where lower(trim(value)) = any(v_supervisor_aliases)
    ) then
      v_cashier_keys := v_cashier_keys || array_remove(array[
        lower(trim(coalesce(v_item ->> 'id', ''))),
        lower(trim(coalesce(v_item ->> 'user', ''))),
        lower(trim(coalesce(v_item ->> 'username', '')))
      ], '');
    end if;
  end loop;

  return coalesce((select array_agg(distinct key) from unnest(v_cashier_keys) key where key <> ''), array[]::text[]);
end;
$function$;

grant execute on function public.ln_legacy_supervisor_cashier_keys(text) to anon, authenticated, service_role;

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
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = lower(v_cashier_key))
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
      and (v_cashier_key is null or lower(coalesce(t.cashier_key, '')) = lower(v_cashier_key))
      and (
        v_supervisor_key is null
        or lower(coalesce(t.supervisor_key, '')) = lower(v_supervisor_key)
        or lower(coalesce(t.cashier_key, '')) = any(v_supervisor_cashier_keys)
      )
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

grant execute on function public.ln_legacy_report(jsonb) to service_role;
