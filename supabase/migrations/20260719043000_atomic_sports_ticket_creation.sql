-- Keeps sports ticket creation atomic and isolated from lottery tables.
create or replace function public.create_sports_ticket_atomic(
    p_ticket jsonb,
    p_legs jsonb,
    p_max_event_exposure numeric default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    stored_ticket public.sports_tickets;
    existing_ticket public.sports_tickets;
    stored_legs jsonb;
    event_id uuid;
    current_exposure numeric;
begin
    if coalesce(p_max_event_exposure, 0) > 0 then
        for event_id in
            select distinct nullif(item->>'event_id', '')::uuid
            from jsonb_array_elements(coalesce(p_legs, '[]'::jsonb)) as rows(item)
            where nullif(item->>'event_id', '') is not null
        loop
            perform pg_advisory_xact_lock(
                hashtextextended(coalesce(p_ticket->>'owner_key', '') || ':' || event_id::text, 0)
            );
            select coalesce(sum(ticket.stake), 0)
            into current_exposure
            from public.sports_tickets ticket
            where ticket.owner_key = p_ticket->>'owner_key'
              and ticket.status not in ('void', 'lost')
              and exists (
                  select 1
                  from public.sports_ticket_legs leg
                  where leg.sports_ticket_id = ticket.id
                    and leg.event_id = event_id
              );
            if current_exposure + coalesce((p_ticket->>'stake')::numeric, 0) > p_max_event_exposure then
                return jsonb_build_object(
                    'rejected', true,
                    'message', 'Exposicion del evento supera el limite permitido.'
                );
            end if;
        end loop;
    end if;

    begin
        insert into public.sports_tickets (
            ticket_code, client_request_id, owner_key, admin_key, supervisor_key,
            cashier_key, seller_user_id, seller_username, banca_name, ticket_type,
            stake, decimal_odds, potential_payout, status, metadata
        )
        values (
            p_ticket->>'ticket_code',
            p_ticket->>'client_request_id',
            p_ticket->>'owner_key',
            nullif(p_ticket->>'admin_key', ''),
            nullif(p_ticket->>'supervisor_key', ''),
            nullif(p_ticket->>'cashier_key', ''),
            nullif(p_ticket->>'seller_user_id', ''),
            nullif(p_ticket->>'seller_username', ''),
            nullif(p_ticket->>'banca_name', ''),
            coalesce(nullif(p_ticket->>'ticket_type', ''), 'straight'),
            (p_ticket->>'stake')::numeric,
            (p_ticket->>'decimal_odds')::numeric,
            (p_ticket->>'potential_payout')::numeric,
            coalesce(nullif(p_ticket->>'status', ''), 'pending'),
            coalesce(p_ticket->'metadata', '{}'::jsonb)
        )
        returning * into stored_ticket;
    exception when unique_violation then
        select * into existing_ticket
        from public.sports_tickets
        where client_request_id = p_ticket->>'client_request_id';
        if existing_ticket.id is null then
            raise;
        end if;
        return jsonb_build_object(
            'duplicate', true,
            'ticket', to_jsonb(existing_ticket),
            'legs', '[]'::jsonb
        );
    end;

    insert into public.sports_ticket_legs (
        sports_ticket_id, event_id, market_id, odds_id, sport_key, league_title,
        event_label, market_key, market_title, selection_key, selection_label,
        point, decimal_odds, odds_locked_at, commence_time, status
    )
    select
        stored_ticket.id,
        nullif(leg->>'event_id', '')::uuid,
        nullif(leg->>'market_id', '')::uuid,
        nullif(leg->>'odds_id', '')::uuid,
        leg->>'sport_key',
        nullif(leg->>'league_title', ''),
        leg->>'event_label',
        leg->>'market_key',
        leg->>'market_title',
        leg->>'selection_key',
        leg->>'selection_label',
        nullif(leg->>'point', '')::numeric,
        (leg->>'decimal_odds')::numeric,
        coalesce(nullif(leg->>'odds_locked_at', '')::timestamptz, now()),
        nullif(leg->>'commence_time', '')::timestamptz,
        coalesce(nullif(leg->>'status', ''), 'pending')
    from jsonb_array_elements(coalesce(p_legs, '[]'::jsonb)) as item(leg);

    select coalesce(jsonb_agg(to_jsonb(leg) order by leg.created_at), '[]'::jsonb)
    into stored_legs
    from public.sports_ticket_legs leg
    where leg.sports_ticket_id = stored_ticket.id;

    return jsonb_build_object(
        'duplicate', false,
        'ticket', to_jsonb(stored_ticket),
        'legs', stored_legs
    );
end;
$$;

revoke all on function public.create_sports_ticket_atomic(jsonb, jsonb, numeric) from public;
grant execute on function public.create_sports_ticket_atomic(jsonb, jsonb, numeric) to service_role;

create or replace function public.pay_sports_ticket_atomic(
    p_ticket_id uuid,
    p_actor_key text,
    p_payout_amount numeric,
    p_reason text default 'Pago de cobro deportivo'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    paid_ticket public.sports_tickets;
    current_ticket public.sports_tickets;
begin
    update public.sports_tickets
    set status = 'paid', paid_at = now(), updated_at = now()
    where id = p_ticket_id and status = 'won'
    returning * into paid_ticket;

    if paid_ticket.id is null then
        select * into current_ticket from public.sports_tickets where id = p_ticket_id;
        if current_ticket.id is null then
            return jsonb_build_object('found', false);
        end if;
        return jsonb_build_object(
            'found', true,
            'already_paid', current_ticket.status = 'paid',
            'ticket', to_jsonb(current_ticket)
        );
    end if;

    insert into public.sports_settlements (
        sports_ticket_id, settlement_type, previous_status, next_status,
        payout_amount, reason, actor_key, metadata
    ) values (
        paid_ticket.id, 'manual', 'won', 'paid', coalesce(p_payout_amount, 0),
        coalesce(nullif(p_reason, ''), 'Pago de cobro deportivo'), p_actor_key,
        jsonb_build_object('action', 'pay-sports-ticket')
    );

    return jsonb_build_object('found', true, 'paid', true, 'ticket', to_jsonb(paid_ticket));
end;
$$;

revoke all on function public.pay_sports_ticket_atomic(uuid, text, numeric, text) from public;
grant execute on function public.pay_sports_ticket_atomic(uuid, text, numeric, text) to service_role;

create or replace function public.settle_sports_ticket_atomic(
    p_ticket_id uuid,
    p_next_status text,
    p_actor_key text,
    p_reason text default 'Liquidacion deportiva'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    settled_ticket public.sports_tickets;
    current_ticket public.sports_tickets;
    payout numeric := 0;
begin
    update public.sports_tickets
    set status = p_next_status, settled_at = now(), updated_at = now()
    where id = p_ticket_id and status = 'pending'
    returning * into settled_ticket;

    if settled_ticket.id is null then
        select * into current_ticket from public.sports_tickets where id = p_ticket_id;
        if current_ticket.id is null then
            return jsonb_build_object('found', false);
        end if;
        return jsonb_build_object(
            'found', true,
            'already_settled', current_ticket.status <> 'pending',
            'ticket', to_jsonb(current_ticket)
        );
    end if;

    update public.sports_ticket_legs
    set status = p_next_status,
        result_payload = jsonb_build_object('settledBy', p_actor_key, 'reason', coalesce(p_reason, ''))
    where sports_ticket_id = p_ticket_id;

    if p_next_status = 'won' then
        payout := settled_ticket.potential_payout;
    end if;
    insert into public.sports_settlements (
        sports_ticket_id, settlement_type, previous_status, next_status,
        payout_amount, reason, actor_key, metadata
    ) values (
        settled_ticket.id, 'manual', 'pending', p_next_status, payout,
        coalesce(nullif(p_reason, ''), 'Liquidacion deportiva'), p_actor_key,
        jsonb_build_object('action', 'settle-sports-ticket')
    );

    return jsonb_build_object('found', true, 'settled', true, 'ticket', to_jsonb(settled_ticket));
end;
$$;

revoke all on function public.settle_sports_ticket_atomic(uuid, text, text, text) from public;
grant execute on function public.settle_sports_ticket_atomic(uuid, text, text, text) to service_role;
