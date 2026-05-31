begin;

do $$
declare
  v_def text;
  v_next text;
begin
  select pg_get_functiondef('public.lotterynet_reconcile_ticket_prize_v2(uuid)'::regprocedure)
  into v_def;

  if v_def is null then
    raise exception 'lotterynet_reconcile_ticket_prize_v2(uuid) not found';
  end if;

  v_next := replace(
    v_def,
    '  if current_status in (''pagado'',''paid'',''cobrado'',''premio_pagado'') then
    return public.lotterynet_calculate_ticket_prize_v2(p_ticket_id) || jsonb_build_object(''skippedPaidTicketUpdate'', true);
  end if;

',
    ''
  );

  v_next := replace(
    v_next,
    '      status = case when total_prize > 0 then ''GANADOR'' else ''PERDEDOR'' end,
      estado = case when total_prize > 0 then ''GANADOR'' else ''PERDEDOR'' end,',
    '      status = case
        when current_status in (''pagado'',''paid'',''cobrado'',''premio_pagado'') and total_prize > 0 then ''PAGADO''
        when total_prize > 0 then ''GANADOR''
        else ''PERDEDOR''
      end,
      estado = case
        when current_status in (''pagado'',''paid'',''cobrado'',''premio_pagado'') and total_prize > 0 then ''PAGADO''
        when total_prize > 0 then ''GANADOR''
        else ''PERDEDOR''
      end,'
  );

  v_next := replace(
    v_next,
    '
    and lower(coalesce(status, estado, '''')) not in (''pagado'',''paid'',''cobrado'',''premio_pagado'')',
    ''
  );

  if v_next = v_def then
    raise exception 'Expected paid-ticket skip branch not found in lotterynet_reconcile_ticket_prize_v2';
  end if;

  execute v_next;
end
$$;

do $$
declare
  v_def text;
  v_next text;
begin
  select pg_get_functiondef('public.lotterynet_process_result_reconcile_jobs(int)'::regprocedure)
  into v_def;

  if v_def is null then
    raise exception 'lotterynet_process_result_reconcile_jobs(int) not found';
  end if;

  v_next := replace(
    v_def,
    '
          and lower(coalesce(t.status, t.estado, '''')) not in (''pagado'',''paid'',''cobrado'',''premio_pagado'')',
    ''
  );

  if v_next = v_def then
    raise exception 'Expected paid-ticket reconcile filter not found in lotterynet_process_result_reconcile_jobs';
  end if;

  execute v_next;
end
$$;

do $$
declare
  v_ticket record;
begin
  for v_ticket in
    select id
    from public.tickets
    where deleted_at is null
      and voided_at is null
      and invalidated_at is null
      and (
        coalesce(payout_amount, 0) > 0
        or lower(coalesce(status, estado, '')) in ('ganador','winner','pending_winner','pagado','paid','cobrado','premio_pagado')
      )
  loop
    perform public.lotterynet_reconcile_ticket_prize_v2(v_ticket.id);
  end loop;
end
$$;

comment on function public.lotterynet_reconcile_ticket_prize_v2(uuid)
is 'LotteryNet v2 prize reconcile updates winner amounts for pending and paid tickets while preserving paid status.';

commit;
