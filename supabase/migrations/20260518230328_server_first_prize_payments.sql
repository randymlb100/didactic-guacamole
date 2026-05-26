create or replace function public.lotterynet_safe_jsonb_numeric(source jsonb, keys text[], fallback numeric default 0)
returns numeric
language plpgsql
immutable
as $$
declare
  key text;
  raw text;
begin
  if source is null then
    return fallback;
  end if;

  foreach key in array keys loop
    raw := source->>key;
    if raw is not null and raw ~ '^-?\d+(\.\d+)?$' then
      return raw::numeric;
    end if;
  end loop;

  return fallback;
end;
$$;

create or replace function public.lotterynet_digits_only(raw text)
returns text
language sql
immutable
as $$
  select regexp_replace(coalesce(raw, ''), '\D', '', 'g')
$$;

create or replace function public.lotterynet_ticket_date_aliases(raw_date text)
returns text[]
language plpgsql
immutable
as $$
begin
  if raw_date is null or btrim(raw_date) = '' then
    return array[]::text[];
  end if;

  if raw_date ~ '^\d{4}-\d{2}-\d{2}$' then
    return array[raw_date, to_char(to_date(raw_date, 'YYYY-MM-DD'), 'DD-MM-YYYY')];
  end if;

  if raw_date ~ '^\d{2}-\d{2}-\d{4}$' then
    return array[raw_date, to_char(to_date(raw_date, 'DD-MM-YYYY'), 'YYYY-MM-DD')];
  end if;

  return array[raw_date];
end;
$$;

create or replace function public.lotterynet_pick_box_way(digits text)
returns integer
language plpgsql
immutable
as $$
declare
  d text := public.lotterynet_digits_only(digits);
  freq int[];
begin
  if length(d) not in (3, 4) then
    return 0;
  end if;

  select array_agg(c order by c desc) into freq
  from (
    select count(*)::int c
    from regexp_split_to_table(d, '') x
    group by x
  ) counts;

  if length(d) = 3 then
    if freq[1] = 2 then return 3; end if;
    if freq[1] = 1 then return 6; end if;
    return 0;
  end if;

  if freq[1] = 3 then return 4; end if;
  if array_length(freq, 1) = 2 and freq[1] = 2 and freq[2] = 2 then return 6; end if;
  if freq[1] = 2 then return 12; end if;
  if freq[1] = 1 then return 24; end if;
  return 0;
end;
$$;

create or replace function public.lotterynet_is_permutation_match(left_digits text, right_digits text)
returns boolean
language sql
immutable
as $$
  select coalesce((
    select string_agg(ch, '' order by ch)
    from regexp_split_to_table(public.lotterynet_digits_only(left_digits), '') ch
  ), '') <> ''
  and coalesce((
    select string_agg(ch, '' order by ch)
    from regexp_split_to_table(public.lotterynet_digits_only(left_digits), '') ch
  ), '') = coalesce((
    select string_agg(ch, '' order by ch)
    from regexp_split_to_table(public.lotterynet_digits_only(right_digits), '') ch
  ), '')
$$;

create or replace function public.lotterynet_ticket_payout_config(ticket jsonb)
returns jsonb
language plpgsql
stable
as $$
declare
  admin_key text;
  seller_key text;
  config jsonb;
  defaults jsonb;
  user_config jsonb;
begin
  admin_key := coalesce(nullif(ticket->>'adminId',''), nullif(ticket->>'adminUser',''), nullif(ticket->>'admin_key',''));
  seller_key := coalesce(
    nullif(ticket->>'vendedorNombre',''),
    nullif(ticket->>'sellerUser',''),
    nullif(ticket->>'cashierKey',''),
    nullif(ticket->>'cashier_key',''),
    nullif(ticket->>'cajeroNombre','')
  );

  if admin_key is not null then
    select value::jsonb into config
    from public.lotterynet_kv
    where key = 'cashier_prize_payouts:' || admin_key
    limit 1;
  end if;

  if config is null and ticket->>'adminUser' is not null then
    select value::jsonb into config
    from public.lotterynet_kv
    where key = 'cashier_prize_payouts:' || (ticket->>'adminUser')
    limit 1;
  end if;

  defaults := coalesce(config->'defaults', '{}'::jsonb);
  user_config := coalesce(config->'byUser'->seller_key, '{}'::jsonb);
  return defaults || user_config;
exception when others then
  return '{}'::jsonb;
end;
$$;

create or replace function public.lotterynet_resolve_ticket_prize(ticket jsonb)
returns jsonb
language plpgsql
stable
as $$
declare
  raw_date text;
  result_payload jsonb;
  item jsonb;
  result_row jsonb;
  secondary_result_row jsonb;
  lottery_id text;
  lottery_name text;
  secondary_lottery_id text;
  secondary_lottery_name text;
  play_type text;
  digits text;
  amount numeric;
  result_parts text[];
  secondary_parts text[];
  first_pick text;
  second_pick text;
  third_pick text;
  secondary_first_pick text;
  drawn_digits text;
  pair_a text;
  pair_b text;
  pair_c text;
  total numeric := 0;
  item_payout numeric;
  hit_position text;
  matched_count int;
  config jsonb;
  q1 numeric;
  q2 numeric;
  q3 numeric;
  pale12 numeric;
  pale13 numeric;
  pale23 numeric;
  tripleta2 numeric;
  tripleta3 numeric;
  super_pale numeric;
  pick3_straight numeric;
  pick3_box3 numeric;
  pick3_box6 numeric;
  pick4_straight numeric;
  pick4_box4 numeric;
  pick4_box6 numeric;
  pick4_box12 numeric;
  pick4_box24 numeric;
  pick3_back_pair numeric;
  pick4_back_pair numeric;
  relevant_rows_found boolean := false;
  items_result jsonb := '[]'::jsonb;
  item_index int := 0;
  box_way int;
begin
  raw_date := coalesce(nullif(ticket->>'drawDateKey',''), nullif(ticket->>'drawDate',''), nullif(ticket->>'dayKey',''), nullif(ticket->>'date',''));

  select payload into result_payload
  from public.lotterynet_results_by_day
  where result_date = any(public.lotterynet_ticket_date_aliases(raw_date))
  limit 1;

  if result_payload is null or jsonb_typeof(result_payload) <> 'array' then
    return jsonb_build_object('didValidate', false, 'totalPrize', coalesce(ticket->>'totalPrize', ticket->>'totalPremio', '0')::numeric, 'items', '[]'::jsonb);
  end if;

  config := public.lotterynet_ticket_payout_config(ticket);
  q1 := public.lotterynet_safe_jsonb_numeric(config, array['q1'], 60);
  q2 := public.lotterynet_safe_jsonb_numeric(config, array['q2'], 12);
  q3 := public.lotterynet_safe_jsonb_numeric(config, array['q3'], 4);
  pale12 := public.lotterynet_safe_jsonb_numeric(config, array['pale12','pale'], 1000);
  pale13 := public.lotterynet_safe_jsonb_numeric(config, array['pale13','pale'], 1000);
  pale23 := public.lotterynet_safe_jsonb_numeric(config, array['pale23','pale'], 1000);
  tripleta2 := public.lotterynet_safe_jsonb_numeric(config, array['tripleta2'], 1000);
  tripleta3 := public.lotterynet_safe_jsonb_numeric(config, array['tripleta3','tripleta'], 20000);
  super_pale := public.lotterynet_safe_jsonb_numeric(config, array['sp','superPale','super_pale'], 3000);
  pick3_straight := public.lotterynet_safe_jsonb_numeric(config, array['p3','pick3Straight'], 500);
  pick3_box3 := public.lotterynet_safe_jsonb_numeric(config, array['p3box3','pick3Box3'], 160);
  pick3_box6 := public.lotterynet_safe_jsonb_numeric(config, array['p3box6','pick3Box6'], 80);
  pick4_straight := public.lotterynet_safe_jsonb_numeric(config, array['p4','pick4Straight'], 5000);
  pick4_box4 := public.lotterynet_safe_jsonb_numeric(config, array['p4box4','pick4Box4'], 1200);
  pick4_box6 := public.lotterynet_safe_jsonb_numeric(config, array['p4box6','pick4Box6'], 800);
  pick4_box12 := public.lotterynet_safe_jsonb_numeric(config, array['p4box12','pick4Box12'], 400);
  pick4_box24 := public.lotterynet_safe_jsonb_numeric(config, array['p4box24','pick4Box24'], 200);
  pick3_back_pair := public.lotterynet_safe_jsonb_numeric(config, array['p3b','pick3BackPair'], 50);
  pick4_back_pair := public.lotterynet_safe_jsonb_numeric(config, array['p4b','pick4BackPair'], 50);

  if pale12 = 100000 then pale12 := 1000; end if;
  if pale13 = 100000 then pale13 := 1000; end if;
  if pale23 = 100000 then pale23 := 1000; end if;

  for item in select value from jsonb_array_elements(coalesce(ticket->'items', '[]'::jsonb)) loop
    item_index := item_index + 1;
    item_payout := 0;
    hit_position := '';
    lottery_id := coalesce(nullif(item->>'lotteryId',''), nullif(item->>'lotId',''), nullif(item->>'lottery_legacy_id',''));
    lottery_name := lower(coalesce(nullif(item->>'lotteryName',''), nullif(item->>'lotName',''), nullif(item->>'lottery_name',''), ''));
    secondary_lottery_id := coalesce(nullif(item->>'secondaryLotteryId',''), nullif(item->>'lotId2',''), nullif(item->>'secondary_lottery_legacy_id',''));
    secondary_lottery_name := lower(coalesce(nullif(item->>'secondaryLotteryName',''), nullif(item->>'lotName2',''), nullif(item->>'secondary_lottery_name',''), ''));
    play_type := upper(coalesce(nullif(item->>'localPlayType',''), nullif(item->>'playType',''), nullif(item->>'type',''), ''));
    digits := public.lotterynet_digits_only(coalesce(nullif(item->>'number',''), nullif(item->>'nums',''), nullif(item->>'play_numbers',''), ''));
    amount := public.lotterynet_safe_jsonb_numeric(item, array['amount','amt'], 0);

    result_row := null;
    select value into result_row
    from jsonb_array_elements(result_payload)
    where (lottery_id is not null and value->>'id' = lottery_id)
       or (lottery_name <> '' and lower(coalesce(value->>'name','')) = lottery_name)
    limit 1;

    if result_row is null then
      continue;
    end if;

    relevant_rows_found := true;
    result_parts := regexp_split_to_array(regexp_replace(coalesce(result_row->>'number',''), '[^0-9-]', '', 'g'), '-');
    first_pick := lpad(coalesce(result_parts[1], ''), 2, '0');
    second_pick := lpad(coalesce(result_parts[2], ''), 2, '0');
    third_pick := lpad(coalesce(result_parts[3], ''), 2, '0');
    drawn_digits := public.lotterynet_digits_only(result_row->>'number');

    if play_type in ('Q','QUINIELA') then
      if digits = first_pick then item_payout := amount * q1; hit_position := '1';
      elsif digits = second_pick then item_payout := amount * q2; hit_position := '2';
      elsif digits = third_pick then item_payout := amount * q3; hit_position := '3';
      end if;
    elsif play_type in ('P','PALE') and length(digits) = 4 then
      pair_a := substring(digits from 1 for 2);
      pair_b := substring(digits from 3 for 2);
      if pair_a <> pair_b then
        if (pair_a = first_pick and pair_b = second_pick) or (pair_a = second_pick and pair_b = first_pick) then
          item_payout := amount * pale12; hit_position := '1-2';
        elsif (pair_a = first_pick and pair_b = third_pick) or (pair_a = third_pick and pair_b = first_pick) then
          item_payout := amount * pale13; hit_position := '1-3';
        elsif (pair_a = second_pick and pair_b = third_pick) or (pair_a = third_pick and pair_b = second_pick) then
          item_payout := amount * pale23; hit_position := '2-3';
        end if;
      end if;
    elsif play_type in ('T','TRIPLETA') and length(digits) = 6 then
      pair_a := substring(digits from 1 for 2);
      pair_b := substring(digits from 3 for 2);
      pair_c := substring(digits from 5 for 2);
      matched_count :=
        (pair_a in (first_pick, second_pick, third_pick))::int +
        (pair_b in (first_pick, second_pick, third_pick))::int +
        (pair_c in (first_pick, second_pick, third_pick))::int;
      if matched_count = 3 then item_payout := amount * tripleta3; hit_position := '3';
      elsif matched_count = 2 then item_payout := amount * tripleta2; hit_position := '2';
      end if;
    elsif play_type in ('SP','SUPER_PALE','SUPERPALE') and length(digits) = 4 then
      secondary_result_row := null;
      select value into secondary_result_row
      from jsonb_array_elements(result_payload)
      where (secondary_lottery_id is not null and value->>'id' = secondary_lottery_id)
         or (secondary_lottery_name <> '' and lower(coalesce(value->>'name','')) = secondary_lottery_name)
      limit 1;
      if secondary_result_row is not null then
        secondary_parts := regexp_split_to_array(regexp_replace(coalesce(secondary_result_row->>'number',''), '[^0-9-]', '', 'g'), '-');
        secondary_first_pick := lpad(coalesce(secondary_parts[1], ''), 2, '0');
        pair_a := substring(digits from 1 for 2);
        pair_b := substring(digits from 3 for 2);
        if (pair_a = first_pick and pair_b = secondary_first_pick) or (pair_a = secondary_first_pick and pair_b = first_pick) then
          item_payout := amount * super_pale; hit_position := 'SP';
        end if;
      end if;
    elsif play_type in ('P3','PICK3_STRAIGHT') then
      if digits <> '' and digits = drawn_digits then item_payout := amount * pick3_straight; hit_position := 'straight'; end if;
    elsif play_type in ('P4','PICK4_STRAIGHT') then
      if digits <> '' and digits = drawn_digits then item_payout := amount * pick4_straight; hit_position := 'straight'; end if;
    elsif play_type in ('P3BOX','PICK3_BOX') then
      box_way := public.lotterynet_pick_box_way(digits);
      if public.lotterynet_is_permutation_match(drawn_digits, digits) then
        if box_way = 3 then item_payout := amount * pick3_box3; hit_position := 'box3';
        elsif box_way = 6 then item_payout := amount * pick3_box6; hit_position := 'box6';
        end if;
      end if;
    elsif play_type in ('P4BOX','PICK4_BOX') then
      box_way := public.lotterynet_pick_box_way(digits);
      if public.lotterynet_is_permutation_match(drawn_digits, digits) then
        if box_way = 4 then item_payout := amount * pick4_box4; hit_position := 'box4';
        elsif box_way = 6 then item_payout := amount * pick4_box6; hit_position := 'box6';
        elsif box_way = 12 then item_payout := amount * pick4_box12; hit_position := 'box12';
        elsif box_way = 24 then item_payout := amount * pick4_box24; hit_position := 'box24';
        end if;
      end if;
    elsif play_type = 'P3B' then
      if length(digits) = 2 and right(drawn_digits, 2) = digits then item_payout := amount * pick3_back_pair; hit_position := 'back'; end if;
    elsif play_type = 'P4B' then
      if length(digits) = 2 and right(drawn_digits, 2) = digits then item_payout := amount * pick4_back_pair; hit_position := 'back'; end if;
    end if;

    total := total + item_payout;
    items_result := items_result || jsonb_build_array(jsonb_build_object(
      'index', item_index,
      'itemId', item->>'itemId',
      'ticketItemId', item->>'ticketItemId',
      'number', digits,
      'playType', play_type,
      'lotteryId', lottery_id,
      'lotteryName', coalesce(item->>'lotteryName', item->>'lotName', item->>'lottery_name'),
      'resultNumber', result_row->>'number',
      'isWinner', item_payout > 0,
      'payoutAmount', item_payout,
      'hitPosition', hit_position
    ));
  end loop;

  return jsonb_build_object(
    'didValidate', relevant_rows_found,
    'totalPrize', total,
    'items', items_result
  );
end;
$$;

create or replace function public.lotterynet_classic_ticket_prize(ticket jsonb)
returns numeric
language sql
stable
as $$
  select coalesce((public.lotterynet_resolve_ticket_prize(ticket)->>'totalPrize')::numeric, 0)
$$;

create or replace function public.lotterynet_ticket_json_from_tables(p_ticket_id uuid)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id', t.id::text,
    'ticketCode', t.ticket_code,
    'clientRequestId', t.client_request_id,
    'legacyTicketId', t.legacy_ticket_id,
    'adminId', coalesce(t.admin_key, t.admin_id::text),
    'adminUser', t.admin_key,
    'cashierKey', t.cashier_key,
    'vendedorNombre', t.cashier_key,
    'drawDateKey', coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date),
    'drawDate', coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date),
    'total', coalesce(t.total_amount, t.monto, 0),
    'items', coalesce(jsonb_agg(jsonb_build_object(
      'itemId', ti.id::text,
      'ticketItemId', ti.id::text,
      'playType', ti.play_type::text,
      'number', coalesce(ti.normalized_number, ti.play_numbers),
      'amount', ti.amount,
      'lotteryId', ti.lottery_legacy_id,
      'lotteryName', ti.lottery_name,
      'secondaryLotteryId', ti.secondary_lottery_legacy_id,
      'secondaryLotteryName', ti.secondary_lottery_name
    ) order by ti.created_at, ti.id) filter (where ti.id is not null), '[]'::jsonb)
  )
  from public.tickets t
  left join public.ticket_items ti on ti.ticket_id = t.id
  where t.id = p_ticket_id
  group by t.id;
$$;

create or replace function public.lotterynet_calculate_ticket_prize(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
as $$
declare
  ticket_payload jsonb;
begin
  ticket_payload := public.lotterynet_ticket_json_from_tables(p_ticket_id);
  if ticket_payload is null then
    return jsonb_build_object('didValidate', false, 'totalPrize', 0, 'items', '[]'::jsonb, 'error', 'ticket_not_found');
  end if;

  return public.lotterynet_resolve_ticket_prize(ticket_payload);
end;
$$;

create or replace function public.lotterynet_reconcile_ticket_prize(p_ticket_id uuid)
returns jsonb
language plpgsql
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

  return calc;
end;
$$;

create or replace function public.lotterynet_pay_ticket_server_first(
  p_ticket_id uuid default null,
  p_client_request_id text default null,
  p_legacy_ticket_id text default null,
  p_actor_key text default null,
  p_admin_key text default null,
  p_cashier_key text default null,
  p_reference text default null
)
returns jsonb
language plpgsql
as $$
declare
  ticket_row public.tickets%rowtype;
  calc jsonb;
  total_prize numeric;
  payment_row public.pagos%rowtype;
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

  calc := public.lotterynet_reconcile_ticket_prize(ticket_row.id);
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
$$;

create index if not exists tickets_paid_at_idx on public.tickets (paid_at desc) where paid_at is not null;
create index if not exists tickets_cashier_draw_status_idx on public.tickets (cashier_key, draw_date_real, status);
create index if not exists tickets_supervisor_draw_status_idx on public.tickets (supervisor_key, draw_date_real, status);
create index if not exists pagos_created_status_idx on public.pagos (created_at desc, status);
create index if not exists movimientos_balance_ticket_idx on public.movimientos_balance (ticket_id) where ticket_id is not null;
