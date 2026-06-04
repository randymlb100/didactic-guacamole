begin;

create or replace function public.lotterynet_preserve_terminal_ticket_state()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  previous_payload jsonb;
  normalized_payload jsonb;
  incoming_tickets jsonb;
  previous_tickets jsonb;
  merged_tickets jsonb := '[]'::jsonb;
  incoming_ticket jsonb;
  previous_ticket jsonb;
  incoming_id text;
  incoming_status text;
  previous_status text;
  incoming_prize numeric;
  previous_prize numeric;
  calculated_prize numeric;
  incoming_server_authoritative boolean;
  paid_statuses text[] := array['paid','pagado','paid_out','payout','cobrado','premio_pagado'];
  void_statuses text[] := array['voided','void','nulled','anulado','annulled','cancelled','canceled','cancelado','invalid','invalido','inválido','deleted','borrado','removed'];
begin
  if new.payload is null then
    return new;
  end if;

  if tg_op = 'UPDATE' then
    previous_payload := old.payload;
  else
    select payload into previous_payload
    from public.lotterynet_tickets_by_owner
    where owner_key = new.owner_key;
  end if;

  normalized_payload := case
    when jsonb_typeof(new.payload) = 'array' then jsonb_build_object('schemaVersion', 2, 'tickets', new.payload, 'deletedIds', '[]'::jsonb)
    else new.payload
  end;

  incoming_tickets := coalesce(normalized_payload->'tickets', '[]'::jsonb);
  previous_tickets := case
    when previous_payload is null then '[]'::jsonb
    when jsonb_typeof(previous_payload) = 'array' then previous_payload
    else coalesce(previous_payload->'tickets', '[]'::jsonb)
  end;

  for incoming_ticket in select value from jsonb_array_elements(incoming_tickets)
  loop
    incoming_id := incoming_ticket->>'id';
    previous_ticket := null;
    incoming_status := lower(coalesce(incoming_ticket->>'status', incoming_ticket->>'st', ''));
    incoming_server_authoritative := coalesce((incoming_ticket->>'serverPrizeAuthoritative')::boolean, false);
    incoming_prize := coalesce(
      nullif(incoming_ticket->>'totalPrize','')::numeric,
      nullif(incoming_ticket->>'totalPremio','')::numeric,
      0
    );

    if incoming_id is not null and incoming_id <> '' then
      select value into previous_ticket
      from jsonb_array_elements(previous_tickets)
      where value->>'id' = incoming_id
      limit 1;
    end if;

    if incoming_status = any(paid_statuses) or incoming_status = 'winner' or incoming_prize > 0 then
      if incoming_server_authoritative then
        calculated_prize := incoming_prize;
      else
        calculated_prize := public.lotterynet_classic_ticket_prize(incoming_ticket);
      end if;

      if calculated_prize is not null then
        incoming_ticket := incoming_ticket || jsonb_build_object(
          'totalPrize', calculated_prize,
          'totalPremio', calculated_prize
        );
        incoming_prize := calculated_prize;

        if calculated_prize > 0 and incoming_status = any(paid_statuses) then
          incoming_ticket := incoming_ticket || jsonb_build_object('status', 'paid', 'st', 'paid');
        elsif calculated_prize > 0 then
          incoming_ticket := incoming_ticket || jsonb_build_object('status', 'winner', 'st', 'winner');
        elsif incoming_status = 'winner' then
          incoming_ticket := incoming_ticket || jsonb_build_object('status', 'active', 'st', 'active');
        end if;
      end if;
    end if;

    if previous_ticket is not null then
      previous_status := lower(coalesce(previous_ticket->>'status', previous_ticket->>'st', ''));
      previous_prize := coalesce(
        nullif(previous_ticket->>'totalPrize','')::numeric,
        nullif(previous_ticket->>'totalPremio','')::numeric,
        0
      );

      if previous_status = any(paid_statuses)
         and incoming_server_authoritative
         and incoming_prize >= previous_prize
         and incoming_prize > 0 then
        incoming_ticket := incoming_ticket || jsonb_build_object(
          'status', 'paid',
          'st', 'paid',
          'totalPrize', incoming_prize,
          'totalPremio', incoming_prize
        );
      elsif previous_status = any(paid_statuses) and not (incoming_status = any(paid_statuses)) then
        incoming_ticket := previous_ticket;
      elsif previous_prize > incoming_prize and previous_status <> '' and incoming_status <> all(void_statuses) then
        incoming_ticket := incoming_ticket || jsonb_build_object(
          'status', previous_ticket->>'status',
          'st', previous_ticket->>'st',
          'totalPrize', previous_prize,
          'totalPremio', previous_prize
        );
      end if;
    end if;

    merged_tickets := merged_tickets || jsonb_build_array(incoming_ticket);
  end loop;

  normalized_payload := jsonb_set(normalized_payload, '{tickets}', merged_tickets, true);
  normalized_payload := jsonb_set(
    normalized_payload,
    '{deletedIds}',
    coalesce((
      select jsonb_agg(id_value)
      from jsonb_array_elements_text(coalesce(normalized_payload->'deletedIds','[]'::jsonb)) as ids(id_value)
      where not exists (
        select 1
        from jsonb_array_elements(merged_tickets) ticket
        where ticket->>'id' = id_value
          and lower(coalesce(ticket->>'status', ticket->>'st', '')) = any(paid_statuses)
      )
    ), '[]'::jsonb),
    true
  );

  new.payload := normalized_payload;
  return new;
end;
$$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves terminal ticket states while allowing server-authoritative prize amounts to fix stale paid/winner snapshots.';

commit;
