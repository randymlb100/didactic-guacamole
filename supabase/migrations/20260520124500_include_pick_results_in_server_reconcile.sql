create or replace function public.lotterynet_resolve_ticket_prize(ticket jsonb)
returns jsonb
language plpgsql
stable
set search_path = public
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

  select coalesce(lottery_results.payload, '[]'::jsonb) || coalesce(pick_results.payload, '[]'::jsonb)
  into result_payload
  from (
    select payload
    from public.lotterynet_results_by_day
    where result_date = any(public.lotterynet_ticket_date_aliases(raw_date))
      and jsonb_typeof(payload) = 'array'
    limit 1
  ) lottery_results
  full join (
    select payload
    from public.lotterynet_pick_results_by_day
    where result_date = any(public.lotterynet_ticket_date_aliases(raw_date))
      and jsonb_typeof(payload) = 'array'
    limit 1
  ) pick_results on true;

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
    result_parts := regexp_split_to_array(regexp_replace(coalesce(result_row->>'number', result_row->>'pick3', result_row->>'pick4', ''), '[^0-9-]', '', 'g'), '-');
    first_pick := lpad(coalesce(result_parts[1], ''), 2, '0');
    second_pick := lpad(coalesce(result_parts[2], ''), 2, '0');
    third_pick := lpad(coalesce(result_parts[3], ''), 2, '0');
    drawn_digits := public.lotterynet_digits_only(coalesce(result_row->>'pick4', result_row->>'pick3', result_row->>'number'));

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
      'resultNumber', coalesce(result_row->>'number', result_row->>'pick3', result_row->>'pick4'),
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

drop trigger if exists lotterynet_pick_results_reconcile_tickets on public.lotterynet_pick_results_by_day;
create trigger lotterynet_pick_results_reconcile_tickets
after insert or update of payload on public.lotterynet_pick_results_by_day
for each row
execute function public.lotterynet_reconcile_tickets_for_results_day();
