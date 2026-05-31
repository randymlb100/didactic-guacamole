begin;

create or replace function public.lotterynet_sync_ticket_owner_payload(p_ticket_id uuid)
returns void
language plpgsql
security definer
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
    'updatedAt', updated_epoch_ms,
    'serverPrizeAuthoritative', true
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
    and owner_row.owner_key in (
      ticket_row.admin_key,
      ticket_row.cashier_key,
      ticket_row.admin_id::text,
      ticket_row.profile_id::text
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

revoke all on function public.lotterynet_sync_ticket_owner_payload(uuid) from public, anon, authenticated;
grant execute on function public.lotterynet_sync_ticket_owner_payload(uuid) to service_role;

commit;
