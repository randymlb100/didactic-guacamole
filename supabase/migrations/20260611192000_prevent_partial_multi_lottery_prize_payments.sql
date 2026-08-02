begin;

create or replace function public.lotterynet_pay_ticket_server_first(
  p_ticket_id uuid default null::uuid,
  p_client_request_id text default null::text,
  p_legacy_ticket_id text default null::text,
  p_actor_key text default null::text,
  p_admin_key text default null::text,
  p_cashier_key text default null::text,
  p_reference text default null::text
)
returns jsonb
language plpgsql
set search_path to 'public'
as $function$
declare
  ticket_row public.tickets%rowtype;
  calc jsonb;
  total_prize numeric;
  payment_row public.pagos%rowtype;
  draw_day date;
  missing_results jsonb := '[]'::jsonb;
  missing_names text;
begin
  select *
  into ticket_row
  from public.tickets
  where (p_ticket_id is not null and id = p_ticket_id)
     or (p_client_request_id is not null and client_request_id = p_client_request_id)
     or (p_legacy_ticket_id is not null and legacy_ticket_id = p_legacy_ticket_id)
  order by server_created_at desc nulls last, created_at desc nulls last
  limit 1
  for update;

  if ticket_row.id is null then
    raise exception 'Ticket no encontrado para pago.';
  end if;

  if ticket_row.deleted_at is not null or ticket_row.voided_at is not null or ticket_row.invalidated_at is not null then
    raise exception 'Ticket anulado, borrado o invalidado no se puede pagar.';
  end if;

  select * into payment_row
  from public.pagos
  where ticket_id = ticket_row.id
  limit 1;

  if payment_row.id is not null then
    return jsonb_build_object(
      'ok', true,
      'alreadyPaid', true,
      'ticketId', ticket_row.id,
      'amount', payment_row.amount,
      'paymentId', payment_row.id
    );
  end if;

  draw_day := ticket_row.draw_date_real;
  if draw_day is null and coalesce(ticket_row.legacy_day_key, '') ~ '^\d{4}-\d{2}-\d{2}$' then
    draw_day := ticket_row.legacy_day_key::date;
  elsif draw_day is null and coalesce(ticket_row.legacy_day_key, '') ~ '^\d{2}-\d{2}-\d{4}$' then
    draw_day := to_date(ticket_row.legacy_day_key, 'DD-MM-YYYY');
  elsif draw_day is null and coalesce(ticket_row.draw_date, '') ~ '^\d{4}-\d{2}-\d{2}$' then
    draw_day := ticket_row.draw_date::date;
  elsif draw_day is null and coalesce(ticket_row.draw_date, '') ~ '^\d{2}-\d{2}-\d{4}$' then
    draw_day := to_date(ticket_row.draw_date, 'DD-MM-YYYY');
  end if;

  if draw_day is not null then
    with required_lotteries as (
      select distinct
        nullif(ti.lottery_legacy_id, '') as lottery_id,
        coalesce(nullif(ti.lottery_name, ''), nullif(ti.lottery_legacy_id, '')) as lottery_name
      from public.ticket_items ti
      where ti.ticket_id = ticket_row.id
        and nullif(ti.lottery_legacy_id, '') is not null
      union
      select distinct
        nullif(ti.secondary_lottery_legacy_id, '') as lottery_id,
        coalesce(nullif(ti.secondary_lottery_name, ''), nullif(ti.secondary_lottery_legacy_id, '')) as lottery_name
      from public.ticket_items ti
      where ti.ticket_id = ticket_row.id
        and nullif(ti.secondary_lottery_legacy_id, '') is not null
    ),
    missing as (
      select r.lottery_id, r.lottery_name
      from required_lotteries r
      where not exists (
        select 1
        from public.result_draws rd
        where rd.result_date = draw_day
          and rd.lottery_legacy_id = r.lottery_id
          and nullif(coalesce(rd.number_digits, rd.number_raw, ''), '') is not null
      )
    )
    select
      coalesce(jsonb_agg(jsonb_build_object('lotteryId', lottery_id, 'lotteryName', lottery_name)), '[]'::jsonb),
      string_agg(coalesce(lottery_name, lottery_id), ', ' order by coalesce(lottery_name, lottery_id))
    into missing_results, missing_names
    from missing;
  end if;

  if jsonb_array_length(missing_results) > 0 then
    raise exception 'Faltan resultados confirmados para: %', missing_names;
  end if;

  calc := public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
  if not coalesce((calc->>'didValidate')::boolean, false) then
    raise exception 'No hay resultado confirmado para validar premio.';
  end if;

  total_prize := coalesce((calc->>'totalPrize')::numeric, 0);
  if total_prize <= 0 then
    raise exception 'El ticket no tiene premio confirmado.';
  end if;

  update public.tickets
  set status = 'PAGADO',
      estado = 'PAGADO',
      payout_amount = total_prize,
      paid_at = now(),
      updated_at = now(),
      admin_key = coalesce(nullif(admin_key, ''), nullif(p_admin_key, ''), ticket_row.admin_key),
      cashier_key = coalesce(nullif(cashier_key, ''), nullif(p_cashier_key, ''), ticket_row.cashier_key)
  where id = ticket_row.id
  returning * into ticket_row;

  insert into public.pagos(ticket_id, amount, status, reference)
  values (ticket_row.id, total_prize, 'completed', coalesce(nullif(p_reference, ''), 'server-first-pay-ticket'))
  returning * into payment_row;

  insert into public.movimientos_balance(
    movement_type,
    amount,
    reference,
    status,
    metadata,
    legacy_from_key,
    legacy_to_key,
    admin_key,
    cashier_key,
    supervisor_key,
    ticket_id,
    day_key
  )
  values (
    'PAYOUT'::public.ln_balance_movement_type,
    total_prize,
    coalesce(nullif(p_reference, ''), 'server-first-pay-ticket'),
    'completed',
    jsonb_build_object(
      'source', 'lotterynet_pay_ticket_server_first',
      'actorKey', p_actor_key,
      'adminKey', coalesce(p_admin_key, ticket_row.admin_key),
      'cashierKey', coalesce(p_cashier_key, ticket_row.cashier_key),
      'prize', calc
    ),
    coalesce(p_admin_key, ticket_row.admin_key),
    coalesce(p_cashier_key, ticket_row.cashier_key),
    coalesce(p_admin_key, ticket_row.admin_key),
    coalesce(p_cashier_key, ticket_row.cashier_key),
    ticket_row.supervisor_key,
    ticket_row.id,
    coalesce(ticket_row.draw_date_real::text, ticket_row.legacy_day_key, ticket_row.draw_date)
  );

  return jsonb_build_object(
    'ok', true,
    'alreadyPaid', false,
    'ticketId', ticket_row.id,
    'ticketCode', ticket_row.ticket_code,
    'clientRequestId', ticket_row.client_request_id,
    'legacyTicketId', ticket_row.legacy_ticket_id,
    'status', ticket_row.status,
    'amount', total_prize,
    'paymentId', payment_row.id,
    'prize', calc
  );
end;
$function$;

do $$
declare
  v_def text;
  v_next text;
begin
  select pg_get_functiondef('public.lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer)'::regprocedure)
  into v_def;

  if v_def is null then
    raise exception 'lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer) not found';
  end if;

  v_next := replace(
    v_def,
    '
          and lower(coalesce(t.status, t.estado, '''')) not in (''pagado'',''paid'',''cobrado'',''premio_pagado'')',
    ''
  );

  if v_next = v_def then
    raise notice 'Paid-ticket exclusion was not present in lotterynet_process_result_reconcile_jobs_for_day';
  else
    execute v_next;
  end if;
end
$$;

comment on function public.lotterynet_pay_ticket_server_first(uuid, text, text, text, text, text, text)
is 'Pays lottery prizes through v2 server reconcile and refuses partial multi-lottery payments while any selected lottery result is missing.';

comment on function public.lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer)
is 'Processes result reconcile jobs for active and already-paid tickets so later result arrivals or corrections update prize totals and owner snapshots.';

commit;
