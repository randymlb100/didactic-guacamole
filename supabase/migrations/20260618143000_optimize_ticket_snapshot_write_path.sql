begin;

create index if not exists tickets_legacy_ticket_id_active_idx
  on public.tickets using btree (legacy_ticket_id)
  where legacy_ticket_id is not null
    and deleted_at is null
    and voided_at is null
    and invalidated_at is null;

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
  previous_ticket_map jsonb := '{}'::jsonb;
  merged_tickets jsonb := '[]'::jsonb;
  paid_ticket_id_map jsonb := '{}'::jsonb;
  paid_statuses text[] := array['paid','pagado','paid_out','payout','cobrado','premio_pagado'];
  void_statuses text[] := array['voided','void','nulled','anulado','annulled','cancelled','canceled','cancelado','invalid','invalido','inválido','deleted','borrado','removed'];
begin
  if current_setting('lotterynet.skip_terminal_ticket_recalculation', true) = 'on' then
    return new;
  end if;

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
    when jsonb_typeof(new.payload) = 'array'
      then jsonb_build_object('schemaVersion', 2, 'tickets', new.payload, 'deletedIds', '[]'::jsonb)
    else new.payload
  end;

  incoming_tickets := case
    when jsonb_typeof(normalized_payload->'tickets') = 'array'
      then normalized_payload->'tickets'
    else '[]'::jsonb
  end;
  previous_tickets := case
    when previous_payload is null then '[]'::jsonb
    when jsonb_typeof(previous_payload) = 'array' then previous_payload
    when jsonb_typeof(previous_payload->'tickets') = 'array' then previous_payload->'tickets'
    else '[]'::jsonb
  end;

  select coalesce(
    jsonb_object_agg(ticket->>'id', ticket)
      filter (where nullif(ticket->>'id', '') is not null),
    '{}'::jsonb
  )
  into previous_ticket_map
  from jsonb_array_elements(previous_tickets) as previous(ticket);

  with incoming as (
    select ticket, ordinal
    from jsonb_array_elements(incoming_tickets) with ordinality as item(ticket, ordinal)
  ),
  measured as (
    select
      ticket,
      ordinal,
      lower(coalesce(ticket->>'status', ticket->>'st', '')) as incoming_status,
      coalesce((ticket->>'serverPrizeAuthoritative')::boolean, false) as server_authoritative,
      coalesce(
        nullif(ticket->>'totalPrize','')::numeric,
        nullif(ticket->>'totalPremio','')::numeric,
        0
      ) as incoming_prize,
      previous_ticket_map -> (ticket->>'id') as previous_ticket,
      lower(coalesce(
        (previous_ticket_map -> (ticket->>'id'))->>'status',
        (previous_ticket_map -> (ticket->>'id'))->>'st',
        ''
      )) as previous_status,
      coalesce(
        nullif((previous_ticket_map -> (ticket->>'id'))->>'totalPrize','')::numeric,
        nullif((previous_ticket_map -> (ticket->>'id'))->>'totalPremio','')::numeric,
        0
      ) as previous_prize
    from incoming
  ),
  calculated as (
    select
      measured.*,
      case
        when (previous_status = any(paid_statuses) or previous_status = 'winner')
          and not server_authoritative
        then null
        when incoming_status = any(paid_statuses)
          or incoming_status = 'winner'
          or incoming_prize > 0
        then case
          when server_authoritative then incoming_prize
          else public.lotterynet_classic_ticket_prize(ticket)
        end
        else null
      end as calculated_prize
    from measured
  ),
  normalized as (
    select
      calculated.*,
      case
        when calculated_prize is null then ticket
        when calculated_prize > 0 and incoming_status = any(paid_statuses)
          then ticket || jsonb_build_object(
            'totalPrize', calculated_prize,
            'totalPremio', calculated_prize,
            'status', 'paid',
            'st', 'paid'
          )
        when calculated_prize > 0
          then ticket || jsonb_build_object(
            'totalPrize', calculated_prize,
            'totalPremio', calculated_prize,
            'status', 'winner',
            'st', 'winner'
          )
        when incoming_status = 'winner'
          then ticket || jsonb_build_object(
            'totalPrize', calculated_prize,
            'totalPremio', calculated_prize,
            'status', 'active',
            'st', 'active'
          )
        else ticket || jsonb_build_object(
          'totalPrize', calculated_prize,
          'totalPremio', calculated_prize
        )
      end as normalized_ticket,
      coalesce(calculated_prize, incoming_prize) as normalized_prize
    from calculated
  ),
  preserved as (
    select
      ordinal,
      case
        when previous_ticket is null then normalized_ticket
        when previous_status = any(paid_statuses)
          and server_authoritative
          and normalized_prize > 0
        then normalized_ticket || jsonb_build_object(
          'status', 'paid',
          'st', 'paid',
          'totalPrize', normalized_prize,
          'totalPremio', normalized_prize
        )
        when (previous_status = any(paid_statuses) or previous_status = 'winner')
          and not server_authoritative
        then previous_ticket
        when previous_prize > normalized_prize
          and previous_status <> ''
          and incoming_status <> all(void_statuses)
        then normalized_ticket || jsonb_build_object(
          'status', previous_ticket->>'status',
          'st', previous_ticket->>'st',
          'totalPrize', previous_prize,
          'totalPremio', previous_prize
        )
        else normalized_ticket
      end as final_ticket
    from normalized
  )
  select coalesce(jsonb_agg(final_ticket order by ordinal), '[]'::jsonb)
  into merged_tickets
  from preserved;

  select coalesce(
    jsonb_object_agg(ticket->>'id', true)
      filter (where nullif(ticket->>'id', '') is not null),
    '{}'::jsonb
  )
  into paid_ticket_id_map
  from jsonb_array_elements(merged_tickets) ticket
  where lower(coalesce(ticket->>'status', ticket->>'st', '')) = any(paid_statuses);

  normalized_payload := jsonb_set(normalized_payload, '{tickets}', merged_tickets, true);
  normalized_payload := jsonb_set(
    normalized_payload,
    '{deletedIds}',
    coalesce((
      select jsonb_agg(id_value)
      from jsonb_array_elements_text(coalesce(normalized_payload->'deletedIds','[]'::jsonb)) as ids(id_value)
      where not (paid_ticket_id_map ? id_value)
    ), '[]'::jsonb),
    true
  );

  new.payload := normalized_payload;
  return new;
end;
$function$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves terminal ticket state using one previous-ticket lookup map instead of repeated full JSON array scans.';

create or replace function public.ln_protect_ticket_owner_snapshot()
returns trigger
language plpgsql
set search_path = public
as $function$
declare
  v_old_payload jsonb := '{}'::jsonb;
  v_new_payload jsonb := '{}'::jsonb;
  merged_deleted jsonb := '[]'::jsonb;
  filtered_tickets jsonb := '[]'::jsonb;
  protected_meta jsonb := '{}'::jsonb;
  active_ticket_identifiers jsonb := '{}'::jsonb;
  deleted_id_map jsonb := '{}'::jsonb;
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

  select coalesce(jsonb_object_agg(identifier, true), '{}'::jsonb)
  into active_ticket_identifiers
  from public.tickets tk
  cross join lateral (
    values
      (nullif(trim(tk.client_request_id), '')),
      (nullif(trim(tk.legacy_ticket_id), '')),
      (nullif(trim(tk.ticket_code), '')),
      (tk.id::text)
  ) as identifiers(identifier)
  where identifier is not null
    and tk.deleted_at is null
    and tk.voided_at is null
    and tk.invalidated_at is null
    and upper(coalesce(tk.status, tk.estado, '')) not in (
      'BORRADO','DELETED','ANULADO','VOIDED','INVALIDADO','INVALID'
    );

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
    ) candidate_deleted
    where nullif(trim(id), '') is not null
      and not (active_ticket_identifiers ? id)
  ), '[]'::jsonb);

  select coalesce(jsonb_object_agg(id, true), '{}'::jsonb)
  into deleted_id_map
  from jsonb_array_elements_text(merged_deleted) deleted(id);

  filtered_tickets := coalesce((
    select jsonb_agg(ticket order by coalesce(
      case when coalesce(ticket->>'createdAtMs', '') ~ '^\d+$' then (ticket->>'createdAtMs')::bigint end,
      case when coalesce(ticket->>'createdAtEpochMs', '') ~ '^\d+$' then (ticket->>'createdAtEpochMs')::bigint end,
      0
    ) desc)
    from jsonb_array_elements(
      case when jsonb_typeof(v_new_payload->'tickets') = 'array' then v_new_payload->'tickets' else '[]'::jsonb end
    ) as incoming(ticket)
    where coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '') <> ''
      and not (deleted_id_map ? coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', ''))
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
is 'Protects owner snapshots using one active-ticket identifier map and one deleted-id map per write.';

create or replace function public.lotterynet_upsert_ticket_owner_snapshot(
  p_owner_key text,
  p_payload jsonb,
  p_updated_at timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_owner_key text := trim(coalesce(p_owner_key, ''));
  v_current_payload jsonb;
  v_updated_at timestamptz;
begin
  if v_owner_key = '' or lower(v_owner_key) in ('null', 'undefined') then
    raise exception 'Owner requerido';
  end if;

  if p_payload is null or jsonb_typeof(p_payload) <> 'object' then
    raise exception 'Payload de tickets invalido';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('lotterynet-ticket-owner:' || lower(v_owner_key), 0));

  select payload, updated_at
    into v_current_payload, v_updated_at
  from public.lotterynet_tickets_by_owner
  where owner_key = v_owner_key
  for update;

  if found
     and (v_current_payload - 'meta') is not distinct from (p_payload - 'meta') then
    return jsonb_build_object(
      'ok', true,
      'changed', false,
      'updatedAt', v_updated_at
    );
  end if;

  perform set_config('lotterynet.skip_terminal_ticket_recalculation', 'on', true);

  if found then
    update public.lotterynet_tickets_by_owner
    set payload = p_payload,
        updated_at = coalesce(p_updated_at, now())
    where owner_key = v_owner_key
    returning updated_at into v_updated_at;
  else
    insert into public.lotterynet_tickets_by_owner(owner_key, payload, updated_at)
    values (v_owner_key, p_payload, coalesce(p_updated_at, now()))
    returning updated_at into v_updated_at;
  end if;

  return jsonb_build_object(
    'ok', true,
    'changed', true,
    'updatedAt', v_updated_at
  );
end;
$function$;

revoke all on function public.lotterynet_upsert_ticket_owner_snapshot(text, jsonb, timestamptz)
  from public, anon, authenticated;
grant execute on function public.lotterynet_upsert_ticket_owner_snapshot(text, jsonb, timestamptz)
  to service_role;

comment on function public.lotterynet_upsert_ticket_owner_snapshot(text, jsonb, timestamptz)
is 'Serializes ticket owner snapshot writes and skips semantically unchanged payloads. Service role only.';

commit;
