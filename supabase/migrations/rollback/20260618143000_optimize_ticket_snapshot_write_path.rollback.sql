begin;

drop function if exists public.lotterynet_upsert_ticket_owner_snapshot(text, jsonb, timestamptz);
drop index if exists public.tickets_legacy_ticket_id_active_idx;

create or replace function public.lotterynet_preserve_terminal_ticket_state()
returns trigger
language plpgsql
set search_path = public
as $function$
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
  if current_setting('lotterynet.skip_preserve_terminal_ticket_state', true) = 'on' then
    return new;
  end if;

  if tg_op = 'UPDATE' and new.payload is not distinct from old.payload then
    return new;
  end if;

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
$function$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves paid/cancelled ticket states while treating server-authoritative prize amounts as the final amount for stale winner snapshots.';

create or replace function public.ln_protect_ticket_owner_snapshot()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  v_old_payload jsonb := '{}'::jsonb;
  v_new_payload jsonb := '{}'::jsonb;
  merged_deleted jsonb := '[]'::jsonb;
  filtered_tickets jsonb := '[]'::jsonb;
  protected_meta jsonb := '{}'::jsonb;
begin
  if current_setting('lotterynet.skip_preserve_terminal_ticket_state', true) = 'on' then
    return new;
  end if;

  if tg_op = 'UPDATE' and new.payload is not distinct from old.payload then
    return new;
  end if;

  if tg_op = 'UPDATE' then
    v_old_payload := coalesce(old.payload, '{}'::jsonb);
  end if;

  v_new_payload := coalesce(new.payload, '{}'::jsonb);
  if jsonb_typeof(v_new_payload) = 'array' then
    v_new_payload := jsonb_build_object('schemaVersion', 2, 'tickets', v_new_payload, 'deletedIds', '[]'::jsonb);
  end if;

  merged_deleted := coalesce((
    select jsonb_agg(distinct id order by id)
    from (
      select jsonb_array_elements_text(
        case when jsonb_typeof(v_old_payload->'deletedIds') = 'array' then v_old_payload->'deletedIds' else '[]'::jsonb end
      ) as id
      union
      select jsonb_array_elements_text(
        case when jsonb_typeof(v_new_payload->'deletedIds') = 'array' then v_new_payload->'deletedIds' else '[]'::jsonb end
      ) as id
      union
      select jsonb_array_elements_text(
        case when jsonb_typeof(v_new_payload->'deletedTicketIds') = 'array' then v_new_payload->'deletedTicketIds' else '[]'::jsonb end
      ) as id
      union
      select jsonb_array_elements_text(
        case when jsonb_typeof(v_new_payload->'removedIds') = 'array' then v_new_payload->'removedIds' else '[]'::jsonb end
      ) as id
    ) d
    where nullif(trim(id),'') is not null
      and not exists (
        select 1
        from public.tickets tk
        where (
            tk.client_request_id = d.id
            or tk.legacy_ticket_id = d.id
            or tk.ticket_code = d.id
            or tk.id::text = d.id
          )
          and tk.deleted_at is null
          and tk.voided_at is null
          and tk.invalidated_at is null
          and upper(coalesce(tk.status, tk.estado, '')) not in ('BORRADO','DELETED','ANULADO','VOIDED','INVALIDADO','INVALID')
      )
  ), '[]'::jsonb);

  filtered_tickets := coalesce((
    select jsonb_agg(ticket order by coalesce(
      case when coalesce(ticket->>'createdAtMs', '') ~ '^\d+$' then (ticket->>'createdAtMs')::bigint end,
      case when coalesce(ticket->>'createdAtEpochMs', '') ~ '^\d+$' then (ticket->>'createdAtEpochMs')::bigint end,
      0
    ) desc)
    from jsonb_array_elements(
      case when jsonb_typeof(v_new_payload->'tickets') = 'array' then v_new_payload->'tickets' else '[]'::jsonb end
    ) as t(ticket)
    where coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '') <> ''
      and not (
        coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '')
        in (select jsonb_array_elements_text(merged_deleted))
      )
      and lower(coalesce(ticket->>'status', ticket->>'st', ticket->>'estado', '')) not in ('deleted','borrado','removed')
  ), '[]'::jsonb);

  protected_meta := coalesce(v_new_payload->'meta', '{}'::jsonb) || jsonb_build_object(
    'snapshotProtectedAt', now(),
    'deletedIdsCount', jsonb_array_length(merged_deleted),
    'ticketsCount', jsonb_array_length(filtered_tickets)
  );

  new.payload := jsonb_set(
    jsonb_set(
      jsonb_set(
        coalesce(v_new_payload, '{}'::jsonb),
        '{schemaVersion}',
        to_jsonb(greatest(
          coalesce(
            case when coalesce(v_new_payload->>'schemaVersion', '') ~ '^\d+$' then (v_new_payload->>'schemaVersion')::int end,
            2
          ),
          3
        )),
        true
      ),
      '{deletedIds}',
      merged_deleted,
      true
    ),
    '{tickets}',
    filtered_tickets,
    true
  );

  new.payload := jsonb_set(new.payload, '{meta}', protected_meta, true);
  return new;
end;
$function$;

comment on function public.ln_protect_ticket_owner_snapshot()
is 'Protects owner ticket snapshots: keeps deletedIds for terminal tickets, removes deletedIds for active official tickets, and records compact snapshot metadata.';

commit;
