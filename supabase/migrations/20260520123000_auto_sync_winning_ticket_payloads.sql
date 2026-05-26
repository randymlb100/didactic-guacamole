create or replace function public.lotterynet_ticket_app_status(raw_status text, raw_estado text default null)
returns text
language sql
stable
set search_path = public
as $$
  select case lower(trim(coalesce(nullif(raw_status, ''), nullif(raw_estado, ''), 'active')))
    when 'ganador' then 'winner'
    when 'winner' then 'winner'
    when 'pagado' then 'paid'
    when 'paid' then 'paid'
    when 'cobrado' then 'paid'
    when 'premio_pagado' then 'paid'
    when 'anulado' then 'voided'
    when 'voided' then 'voided'
    when 'borrado' then 'deleted'
    when 'deleted' then 'deleted'
    else 'active'
  end
$$;

create or replace function public.lotterynet_sync_ticket_owner_payload(p_ticket_id uuid)
returns void
language plpgsql
set search_path = public
as $$
declare
  ticket_row public.tickets%rowtype;
  app_status text;
  prize_amount numeric;
  updated_epoch_ms bigint;
  ticket_patch jsonb;
begin
  select *
  into ticket_row
  from public.tickets
  where id = p_ticket_id;

  if ticket_row.id is null then
    return;
  end if;

  app_status := public.lotterynet_ticket_app_status(ticket_row.status, ticket_row.estado);
  prize_amount := coalesce(ticket_row.payout_amount, 0);
  updated_epoch_ms := (extract(epoch from now()) * 1000)::bigint;
  ticket_patch := jsonb_build_object(
    'status', app_status,
    'st', app_status,
    'totalPrize', prize_amount,
    'totalPremio', prize_amount,
    'updatedAt', updated_epoch_ms
  );

  if app_status = 'winner' and prize_amount > 0 then
    ticket_patch := ticket_patch || jsonb_build_object(
      'note',
      coalesce(nullif(ticket_row.void_reason, ''), 'Premio detectado en servidor')
    );
  end if;

  update public.lotterynet_tickets_by_owner owner_row
  set payload = jsonb_set(
        owner_row.payload,
        '{tickets}',
        (
          select coalesce(jsonb_agg(
            case
              when ticket->>'id' = coalesce(ticket_row.legacy_ticket_id, ticket_row.client_request_id, ticket_row.id::text)
                or ticket->>'clientRequestId' = ticket_row.client_request_id
                or ticket->>'legacyTicketId' = ticket_row.legacy_ticket_id
                or ticket->>'serial' = ticket_row.ticket_code
                or ticket->>'ticketCode' = ticket_row.ticket_code
              then ticket || ticket_patch
              else ticket
            end
            order by ord
          ), '[]'::jsonb)
          from jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) with ordinality as payload_ticket(ticket, ord)
        ),
        true
      ),
      updated_at = now()
  where jsonb_typeof(owner_row.payload) = 'object'
    and jsonb_typeof(owner_row.payload->'tickets') = 'array'
    and (
      owner_row.owner_key = ticket_row.admin_key
      or owner_row.owner_key = ticket_row.cashier_key
      or owner_row.owner_key = ticket_row.admin_id::text
      or owner_row.owner_key = ticket_row.profile_id::text
      or owner_row.payload::text like '%' || coalesce(ticket_row.legacy_ticket_id, ticket_row.client_request_id, ticket_row.ticket_code, ticket_row.id::text) || '%'
    )
    and exists (
      select 1
      from jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) as payload_ticket(ticket)
      where ticket->>'id' = coalesce(ticket_row.legacy_ticket_id, ticket_row.client_request_id, ticket_row.id::text)
        or ticket->>'clientRequestId' = ticket_row.client_request_id
        or ticket->>'legacyTicketId' = ticket_row.legacy_ticket_id
        or ticket->>'serial' = ticket_row.ticket_code
        or ticket->>'ticketCode' = ticket_row.ticket_code
    );
end;
$$;

create or replace function public.lotterynet_reconcile_ticket_prize(p_ticket_id uuid)
returns jsonb
language plpgsql
set search_path = public
as $$
declare
  calc jsonb;
  total_prize numeric;
  item_result jsonb;
  item_uuid uuid;
begin
  calc := public.lotterynet_calculate_ticket_prize(p_ticket_id);
  if not coalesce((calc->>'didValidate')::boolean, false) then
    return calc;
  end if;

  total_prize := coalesce((calc->>'totalPrize')::numeric, 0);

  update public.ticket_items
  set is_winner = false,
      payout_amount = 0,
      hit_position = ''
  where ticket_id = p_ticket_id;

  for item_result in select value from jsonb_array_elements(coalesce(calc->'items', '[]'::jsonb)) loop
    if nullif(item_result->>'itemId', '') is not null then
      item_uuid := (item_result->>'itemId')::uuid;
      update public.ticket_items
      set is_winner = coalesce((item_result->>'isWinner')::boolean, false),
          payout_amount = coalesce((item_result->>'payoutAmount')::numeric, 0),
          hit_position = coalesce(item_result->>'hitPosition', '')
      where id = item_uuid and ticket_id = p_ticket_id;
    end if;
  end loop;

  update public.tickets
  set payout_amount = total_prize,
      status = case
        when lower(coalesce(status, estado, '')) in ('pagado','paid','cobrado','premio_pagado') and total_prize > 0 then 'PAGADO'
        when total_prize > 0 then 'GANADOR'
        else 'PERDEDOR'
      end,
      estado = case
        when lower(coalesce(status, estado, '')) in ('pagado','paid','cobrado','premio_pagado') and total_prize > 0 then 'PAGADO'
        when total_prize > 0 then 'GANADOR'
        else 'PERDEDOR'
      end,
      server_validated_at = now(),
      updated_at = now()
  where id = p_ticket_id
    and deleted_at is null
    and voided_at is null
    and invalidated_at is null;

  perform public.lotterynet_sync_ticket_owner_payload(p_ticket_id);

  return calc;
end;
$$;

create or replace function public.lotterynet_reconcile_tickets_for_results_day()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  ticket_row record;
begin
  for ticket_row in
    select distinct t.id
    from public.tickets t
    where coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date) = any(public.lotterynet_ticket_date_aliases(new.result_date))
      and t.deleted_at is null
      and t.voided_at is null
      and t.invalidated_at is null
      and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
  loop
    perform public.lotterynet_reconcile_ticket_prize(ticket_row.id);
  end loop;

  return new;
end;
$$;

drop trigger if exists lotterynet_results_reconcile_tickets on public.lotterynet_results_by_day;
create trigger lotterynet_results_reconcile_tickets
after insert or update of payload on public.lotterynet_results_by_day
for each row
execute function public.lotterynet_reconcile_tickets_for_results_day();
