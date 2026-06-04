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
  winning_details jsonb;
  ticket_patch jsonb;
  owner_keys text[];
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

  with seed_keys(key_value) as (
    values
      (nullif(trim(ticket_row.admin_key), '')),
      (nullif(trim(ticket_row.cashier_key), '')),
      (nullif(trim(ticket_row.admin_id::text), '')),
      (nullif(trim(ticket_row.profile_id::text), ''))
  ),
  matched_user_rows as (
    select u
    from public.lotterynet_users_state lus
    cross join lateral jsonb_array_elements(
      case
        when jsonb_typeof(lus.payload->'users') = 'array' then lus.payload->'users'
        else '[]'::jsonb
      end
    ) as users(u)
    where lus.scope = 'global'
      and (
        nullif(trim(u->>'id'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'user'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'username'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'cashierId'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'cashierUser'), '') in (select key_value from seed_keys where key_value is not null)
      )
  ),
  candidate_keys as (
    select key_value from seed_keys where key_value is not null
    union
    select nullif(trim(u->>'id'), '') from matched_user_rows
    union
    select nullif(trim(u->>'user'), '') from matched_user_rows
    union
    select nullif(trim(u->>'username'), '') from matched_user_rows
    union
    select nullif(trim(u->>'adminId'), '') from matched_user_rows
    union
    select nullif(trim(u->>'adminUser'), '') from matched_user_rows
  )
  select coalesce(array_agg(distinct key_value), array[]::text[])
  into owner_keys
  from candidate_keys
  where key_value is not null;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'lotteryName', coalesce(nullif(ti.lottery_name, ''), ''),
        'playType', coalesce(ti.play_type::text, ''),
        'playedNumber', coalesce(nullif(ti.normalized_number, ''), nullif(ti.play_numbers, ''), ''),
        'resultNumber', coalesce(nullif(tpi.result_number, ''), ''),
        'hitPosition', coalesce(nullif(ti.hit_position, ''), nullif(tpi.hit_position, ''), ''),
        'amount', coalesce(ti.amount, 0),
        'payoutAmount', greatest(coalesce(ti.payout_amount, 0), coalesce(tpi.payout_amount, 0))
      )
      order by ti.created_at, ti.id
    ),
    '[]'::jsonb
  )
  into winning_details
  from public.ticket_items ti
  left join public.ticket_prize_items tpi on tpi.ticket_item_id = ti.id
  where ti.ticket_id = p_ticket_id
    and (
      coalesce(ti.is_winner, false)
      or coalesce(ti.payout_amount, 0) > 0
      or coalesce(tpi.is_winner, false)
      or coalesce(tpi.payout_amount, 0) > 0
    );

  ticket_patch := jsonb_build_object(
    'status', app_status,
    'st', app_status,
    'totalPrize', prize_amount,
    'totalPremio', prize_amount,
    'winningDetails', winning_details,
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
                or ticket->>'id' = ticket_row.id::text
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
  where owner_row.owner_key = any(owner_keys)
    and jsonb_typeof(owner_row.payload) = 'object'
    and jsonb_typeof(owner_row.payload->'tickets') = 'array'
    and exists (
      select 1
      from jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) as payload_ticket(ticket)
      where ticket->>'id' = coalesce(ticket_row.legacy_ticket_id, ticket_row.client_request_id, ticket_row.id::text)
        or ticket->>'id' = ticket_row.id::text
        or ticket->>'clientRequestId' = ticket_row.client_request_id
        or ticket->>'legacyTicketId' = ticket_row.legacy_ticket_id
        or ticket->>'serial' = ticket_row.ticket_code
        or ticket->>'ticketCode' = ticket_row.ticket_code
    );
end;
$$;

revoke all on function public.lotterynet_sync_ticket_owner_payload(uuid) from public, anon, authenticated;
grant execute on function public.lotterynet_sync_ticket_owner_payload(uuid) to service_role;

comment on function public.lotterynet_sync_ticket_owner_payload(uuid)
is 'Synchronizes authoritative ticket prize status only into the selling admin/cashier snapshots and their username aliases.';

commit;
