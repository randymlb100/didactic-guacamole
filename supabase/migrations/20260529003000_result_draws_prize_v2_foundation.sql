begin;

create table if not exists public.result_draws (
  id uuid primary key default gen_random_uuid(),
  source text not null,
  result_date date not null,
  result_day_key text not null,
  lottery_legacy_id text not null,
  lottery_name text not null,
  game text not null default 'normal',
  draw_name text not null default '',
  number_raw text not null,
  number_digits text not null,
  status text not null default 'published',
  source_payload jsonb not null default '{}'::jsonb,
  source_hash text not null,
  first_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint result_draws_unique unique (result_day_key, lottery_legacy_id, game, draw_name)
);

create table if not exists public.ticket_prize_items (
  ticket_item_id uuid primary key references public.ticket_items(id) on delete cascade,
  ticket_id uuid not null references public.tickets(id) on delete cascade,
  result_draw_id uuid references public.result_draws(id),
  is_winner boolean not null default false,
  payout_amount numeric not null default 0,
  hit_position text not null default '',
  result_number text not null default '',
  payout_config_snapshot jsonb not null default '{}'::jsonb,
  result_hash text not null default '',
  calculated_at timestamptz not null default now()
);

create table if not exists public.result_reconcile_jobs (
  id uuid primary key default gen_random_uuid(),
  result_day_key text not null,
  lottery_legacy_id text,
  game text,
  status text not null default 'pending',
  attempts int not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  locked_at timestamptz,
  completed_at timestamptz
);

create index if not exists result_draws_day_lottery_idx
  on public.result_draws (result_day_key, lottery_legacy_id, game);
create index if not exists result_draws_updated_idx
  on public.result_draws (updated_at desc);
create index if not exists ticket_prize_items_ticket_idx
  on public.ticket_prize_items (ticket_id);
create index if not exists ticket_prize_items_winner_idx
  on public.ticket_prize_items (ticket_id, is_winner)
  where is_winner;
create index if not exists result_reconcile_jobs_pending_idx
  on public.result_reconcile_jobs (status, created_at)
  where status in ('pending', 'running');

alter table public.result_draws enable row level security;
alter table public.ticket_prize_items enable row level security;
alter table public.result_reconcile_jobs enable row level security;

drop policy if exists result_draws_read_all on public.result_draws;
create policy result_draws_read_all
on public.result_draws
for select
to anon, authenticated
using (true);

drop policy if exists ticket_prize_items_read_all on public.ticket_prize_items;
create policy ticket_prize_items_read_all
on public.ticket_prize_items
for select
to anon, authenticated
using (true);

create or replace function public.lotterynet_result_day_key_to_date(p_day_key text)
returns date
language sql
immutable
set search_path = public
as $$
  select case
    when coalesce(p_day_key, '') ~ '^\d{2}-\d{2}-\d{4}$'
      then to_date(p_day_key, 'DD-MM-YYYY')
    when coalesce(p_day_key, '') ~ '^\d{4}-\d{2}-\d{2}$'
      then p_day_key::date
    else null
  end
$$;

create or replace function public.lotterynet_result_draw_game(p_row jsonb, p_source text)
returns text
language sql
immutable
set search_path = public
as $$
  select case
    when lower(coalesce(p_source, '')) = 'pick' then lower(coalesce(nullif(p_row->>'game', ''), case when p_row ? 'pick4' then 'pick4' else 'pick3' end))
    when upper(coalesce(p_row->>'id', '')) like 'US-P3-%' then 'pick3'
    when upper(coalesce(p_row->>'id', '')) like 'US-P4-%' then 'pick4'
    when p_row ? 'pick4' then 'pick4'
    when p_row ? 'pick3' then 'pick3'
    else 'normal'
  end
$$;

create or replace function public.lotterynet_upsert_result_draws_from_payload(
  p_result_day_key text,
  p_payload jsonb,
  p_source text default 'cache'
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  parsed_payload jsonb;
  row_value jsonb;
  draw_game text;
  v_draw_name text;
  draw_number text;
  changed_count int := 0;
begin
  parsed_payload := p_payload;
  if jsonb_typeof(parsed_payload) = 'object' and parsed_payload ? 'results' then
    parsed_payload := parsed_payload->'results';
  end if;
  if jsonb_typeof(parsed_payload) <> 'array' then
    return 0;
  end if;

  for row_value in select value from jsonb_array_elements(parsed_payload) loop
    if not row_value ? 'id' then
      continue;
    end if;

    draw_game := public.lotterynet_result_draw_game(row_value, p_source);
    v_draw_name := coalesce(nullif(row_value->>'draw', ''), '');
    draw_number := coalesce(nullif(row_value->>'number', ''), nullif(row_value->>'pick4', ''), nullif(row_value->>'pick3', ''), '');

    if draw_number = '' and lower(coalesce(row_value->>'status', '')) = 'pending' then
      draw_number := '';
    end if;

    insert into public.result_draws(
      source,
      result_date,
      result_day_key,
      lottery_legacy_id,
      lottery_name,
      game,
      draw_name,
      number_raw,
      number_digits,
      status,
      source_payload,
      source_hash,
      updated_at
    )
    values (
      coalesce(nullif(p_source, ''), 'cache'),
      coalesce(public.lotterynet_result_day_key_to_date(p_result_day_key), current_date),
      p_result_day_key,
      row_value->>'id',
      coalesce(nullif(row_value->>'name', ''), row_value->>'id'),
      draw_game,
      v_draw_name,
      draw_number,
      public.lotterynet_digits_only(draw_number),
      coalesce(nullif(row_value->>'status', ''), case when draw_number = '' then 'pending' else 'published' end),
      row_value,
      md5(row_value::text),
      now()
    )
    on conflict (result_day_key, lottery_legacy_id, game, draw_name) do update
      set source = excluded.source,
          result_date = excluded.result_date,
          lottery_name = excluded.lottery_name,
          number_raw = excluded.number_raw,
          number_digits = excluded.number_digits,
          status = excluded.status,
          source_payload = excluded.source_payload,
          source_hash = excluded.source_hash,
          updated_at = case
            when public.result_draws.source_hash is distinct from excluded.source_hash then now()
            else public.result_draws.updated_at
          end
      where public.result_draws.source_hash is distinct from excluded.source_hash
         or public.result_draws.status is distinct from excluded.status
         or public.result_draws.number_raw is distinct from excluded.number_raw;

    if found then
      changed_count := changed_count + 1;
      insert into public.result_reconcile_jobs(result_day_key, lottery_legacy_id, game)
      values (p_result_day_key, row_value->>'id', draw_game);
    end if;
  end loop;

  return changed_count;
end;
$$;

create or replace function public.lotterynet_result_draws_payload(p_raw_date text)
returns jsonb
language sql
stable
set search_path = public
as $$
  select coalesce(
    jsonb_agg(
      jsonb_strip_nulls(
        jsonb_build_object(
          'id', lottery_legacy_id,
          'name', lottery_name,
          'date', result_day_key,
          'number', number_raw,
          'pick3', case when game = 'pick3' then number_raw else null end,
          'pick4', case when game = 'pick4' then number_raw else null end,
          'game', case when game in ('pick3', 'pick4') then game else null end,
          'draw', nullif(draw_name, ''),
          'state', source_payload->>'state',
          'stateCode', source_payload->>'stateCode',
          'gameName', source_payload->>'gameName',
          'status', nullif(status, 'published')
        )
      )
      order by
        case when lottery_legacy_id ~ '^\d+$' then 0 else 1 end,
        case when lottery_legacy_id ~ '^\d+$' then lottery_legacy_id::int else 999999 end,
        lottery_legacy_id,
        draw_name
    ),
    '[]'::jsonb
  )
  from public.result_draws
  where result_day_key = any(public.lotterynet_ticket_date_aliases(p_raw_date))
    and status in ('published', 'pending')
$$;

create or replace function public.lotterynet_sync_result_draws_from_kv()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  day_key text;
  parsed_payload jsonb;
  source_name text;
begin
  if tg_op = 'DELETE' then
    return old;
  end if;
  if new.key is null or new.value is null then
    return new;
  end if;
  if new.key !~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$' then
    return new;
  end if;

  day_key := substring(new.key from ':([0-9]{2}-[0-9]{2}-[0-9]{4})$');
  source_name := case when new.key like 'pick_results_cache_by_day:%' then 'pick' else 'lottery' end;
  parsed_payload := new.value::jsonb;

  perform public.lotterynet_upsert_result_draws_from_payload(day_key, parsed_payload, source_name);
  return new;
exception when others then
  raise warning 'lotterynet_sync_result_draws_from_kv failed for key %: %', new.key, sqlerrm;
  return new;
end;
$$;

drop trigger if exists lotterynet_sync_result_draws_from_kv_trigger on public.lotterynet_kv;
create trigger lotterynet_sync_result_draws_from_kv_trigger
after insert or update of key, value, upd on public.lotterynet_kv
for each row
when (new.key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$')
execute function public.lotterynet_sync_result_draws_from_kv();

create or replace function public.lotterynet_resolve_ticket_prize_against_payload(ticket jsonb, result_payload jsonb)
returns jsonb
language plpgsql
stable
set search_path = public
as $$
declare
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
  if result_payload is null or jsonb_typeof(result_payload) <> 'array' or jsonb_array_length(result_payload) = 0 then
    return jsonb_build_object('didValidate', false, 'totalPrize', coalesce(ticket->>'totalPrize', ticket->>'totalPremio', '0')::numeric, 'items', '[]'::jsonb);
  end if;

  config := coalesce(nullif(ticket->>'payoutConfigSnapshot', '')::jsonb, public.lotterynet_ticket_payout_config(ticket));
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
    where ((lottery_id is not null and value->>'id' = lottery_id)
       or (lottery_name <> '' and lower(coalesce(value->>'name','')) = lottery_name))
      and coalesce(value->>'number', value->>'pick3', value->>'pick4', '') <> ''
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
      where ((secondary_lottery_id is not null and value->>'id' = secondary_lottery_id)
         or (secondary_lottery_name <> '' and lower(coalesce(value->>'name','')) = secondary_lottery_name))
        and coalesce(value->>'number', value->>'pick3', value->>'pick4', '') <> ''
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
      'hitPosition', hit_position,
      'resultHash', md5(result_row::text)
    ));
  end loop;

  return jsonb_build_object(
    'didValidate', relevant_rows_found,
    'totalPrize', total,
    'items', items_result,
    'source', 'result_draws'
  );
end;
$$;

create or replace function public.lotterynet_resolve_ticket_prize_v2(ticket jsonb)
returns jsonb
language plpgsql
stable
set search_path = public
as $$
declare
  raw_date text;
  normalized_payload jsonb;
  calc jsonb;
begin
  raw_date := coalesce(nullif(ticket->>'drawDateKey',''), nullif(ticket->>'drawDate',''), nullif(ticket->>'dayKey',''), nullif(ticket->>'date',''));
  normalized_payload := public.lotterynet_result_draws_payload(raw_date);
  calc := public.lotterynet_resolve_ticket_prize_against_payload(ticket, normalized_payload);
  if coalesce((calc->>'didValidate')::boolean, false) then
    return calc;
  end if;
  return public.lotterynet_resolve_ticket_prize(ticket);
end;
$$;

create or replace function public.lotterynet_calculate_ticket_prize_v2(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  ticket_payload jsonb;
begin
  ticket_payload := public.lotterynet_ticket_json_from_tables(p_ticket_id);
  if ticket_payload is null then
    return jsonb_build_object('didValidate', false, 'totalPrize', 0, 'items', '[]'::jsonb, 'error', 'ticket_not_found');
  end if;

  return public.lotterynet_resolve_ticket_prize_v2(ticket_payload);
end;
$$;

create or replace function public.lotterynet_reconcile_ticket_prize_v2(p_ticket_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  calc jsonb;
  total_prize numeric;
  item_result jsonb;
  item_uuid uuid;
  current_status text;
begin
  select lower(coalesce(status, estado, '')) into current_status
  from public.tickets
  where id = p_ticket_id
  for update;

  if current_status in ('pagado','paid','cobrado','premio_pagado') then
    return public.lotterynet_calculate_ticket_prize_v2(p_ticket_id) || jsonb_build_object('skippedPaidTicketUpdate', true);
  end if;

  calc := public.lotterynet_calculate_ticket_prize_v2(p_ticket_id);
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

      insert into public.ticket_prize_items(
        ticket_item_id,
        ticket_id,
        is_winner,
        payout_amount,
        hit_position,
        result_number,
        payout_config_snapshot,
        result_hash,
        calculated_at
      )
      values (
        item_uuid,
        p_ticket_id,
        coalesce((item_result->>'isWinner')::boolean, false),
        coalesce((item_result->>'payoutAmount')::numeric, 0),
        coalesce(item_result->>'hitPosition', ''),
        coalesce(item_result->>'resultNumber', ''),
        '{}',
        coalesce(item_result->>'resultHash', ''),
        now()
      )
      on conflict (ticket_item_id) do update
        set is_winner = excluded.is_winner,
            payout_amount = excluded.payout_amount,
            hit_position = excluded.hit_position,
            result_number = excluded.result_number,
            result_hash = excluded.result_hash,
            calculated_at = excluded.calculated_at;
    end if;
  end loop;

  update public.tickets
  set payout_amount = total_prize,
      status = case when total_prize > 0 then 'GANADOR' else 'PERDEDOR' end,
      estado = case when total_prize > 0 then 'GANADOR' else 'PERDEDOR' end,
      server_validated_at = now(),
      updated_at = now()
  where id = p_ticket_id
    and deleted_at is null
    and voided_at is null
    and invalidated_at is null
    and lower(coalesce(status, estado, '')) not in ('pagado','paid','cobrado','premio_pagado');

  perform public.lotterynet_sync_ticket_owner_payload(p_ticket_id);

  return calc;
end;
$$;

create or replace function public.lotterynet_process_result_reconcile_jobs(p_limit int default 50)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  job_row record;
  ticket_row record;
  processed_jobs int := 0;
  processed_tickets int := 0;
begin
  for job_row in
    select *
    from public.result_reconcile_jobs
    where status = 'pending'
    order by created_at
    limit greatest(coalesce(p_limit, 50), 1)
    for update skip locked
  loop
    begin
      update public.result_reconcile_jobs
      set status = 'running', locked_at = now(), attempts = attempts + 1
      where id = job_row.id;

      for ticket_row in
        select distinct t.id
        from public.tickets t
        join public.ticket_items ti on ti.ticket_id = t.id
        where coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date) = any(public.lotterynet_ticket_date_aliases(job_row.result_day_key))
          and (job_row.lottery_legacy_id is null or ti.lottery_legacy_id = job_row.lottery_legacy_id or ti.secondary_lottery_legacy_id = job_row.lottery_legacy_id)
          and t.deleted_at is null
          and t.voided_at is null
          and t.invalidated_at is null
          and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
      loop
        perform public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
        processed_tickets := processed_tickets + 1;
      end loop;

      update public.result_reconcile_jobs
      set status = 'completed', completed_at = now(), last_error = null
      where id = job_row.id;
      processed_jobs := processed_jobs + 1;
    exception when others then
      update public.result_reconcile_jobs
      set status = 'failed', last_error = sqlerrm
      where id = job_row.id;
    end;
  end loop;

  return jsonb_build_object('ok', true, 'processedJobs', processed_jobs, 'processedTickets', processed_tickets);
end;
$$;

revoke all on function public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text) from public, anon, authenticated;
revoke all on function public.lotterynet_sync_result_draws_from_kv() from public, anon, authenticated;
revoke all on function public.lotterynet_calculate_ticket_prize_v2(uuid) from public, anon, authenticated;
revoke all on function public.lotterynet_reconcile_ticket_prize_v2(uuid) from public, anon, authenticated;
revoke all on function public.lotterynet_process_result_reconcile_jobs(int) from public, anon, authenticated;

grant execute on function public.lotterynet_calculate_ticket_prize_v2(uuid) to service_role;
grant execute on function public.lotterynet_reconcile_ticket_prize_v2(uuid) to service_role;
grant execute on function public.lotterynet_process_result_reconcile_jobs(int) to service_role;

with cached as (
  select
    key,
    substring(key from ':([0-9]{2}-[0-9]{2}-[0-9]{4})$') as day_key,
    case when key like 'pick_results_cache_by_day:%' then 'pick' else 'lottery' end as source_name,
    value::jsonb as payload
  from public.lotterynet_kv
  where key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
select public.lotterynet_upsert_result_draws_from_payload(day_key, payload, source_name)
from cached;

commit;
