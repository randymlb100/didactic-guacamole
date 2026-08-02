begin;

create or replace function public.lotterynet_ticket_item_can_win_against_draw(
  ticket_item jsonb,
  result_draw jsonb,
  secondary_draw jsonb default null
)
returns boolean
language plpgsql
stable
set search_path = public
as $function$
declare
  play_type text := upper(coalesce(ticket_item->>'play_type', ticket_item->>'playType', ticket_item->>'type', ''));
  digits text := public.lotterynet_digits_only(coalesce(
    nullif(ticket_item->>'normalized_number', ''),
    nullif(ticket_item->>'play_numbers', ''),
    nullif(ticket_item->>'playNumbers', ''),
    nullif(ticket_item->>'number', ''),
    nullif(ticket_item->>'nums', ''),
    ''
  ));
  result_parts text[];
  secondary_parts text[];
  first_pick text;
  second_pick text;
  third_pick text;
  drawn_digits text;
  secondary_first_pick text;
  pair_a text;
  pair_b text;
  pair_c text;
  matched_count int;
  box_way int;
begin
  if result_draw is null then
    return false;
  end if;

  result_parts := regexp_split_to_array(
    regexp_replace(coalesce(result_draw->>'number_raw', result_draw->>'number_digits', result_draw->>'number', ''), '[^0-9-]', '', 'g'),
    '-'
  );
  first_pick := lpad(coalesce(result_parts[1], ''), 2, '0');
  second_pick := lpad(coalesce(result_parts[2], ''), 2, '0');
  third_pick := lpad(coalesce(result_parts[3], ''), 2, '0');
  drawn_digits := public.lotterynet_digits_only(coalesce(result_draw->>'number_raw', result_draw->>'number_digits', result_draw->>'number', ''));

  if play_type in ('Q', 'QUINIELA') then
    return digits = first_pick or digits = second_pick or digits = third_pick;
  elsif play_type in ('P', 'PALE') then
    if length(digits) <> 4 then
      return false;
    end if;
    pair_a := substring(digits from 1 for 2);
    pair_b := substring(digits from 3 for 2);
    return pair_a <> pair_b and (
      (pair_a = first_pick and pair_b = second_pick) or
      (pair_a = second_pick and pair_b = first_pick) or
      (pair_a = first_pick and pair_b = third_pick) or
      (pair_a = third_pick and pair_b = first_pick) or
      (pair_a = second_pick and pair_b = third_pick) or
      (pair_a = third_pick and pair_b = second_pick)
    );
  elsif play_type in ('T', 'TRIPLETA') then
    if length(digits) <> 6 then
      return false;
    end if;
    pair_a := substring(digits from 1 for 2);
    pair_b := substring(digits from 3 for 2);
    pair_c := substring(digits from 5 for 2);
    matched_count :=
      (pair_a in (first_pick, second_pick, third_pick))::int +
      (pair_b in (first_pick, second_pick, third_pick))::int +
      (pair_c in (first_pick, second_pick, third_pick))::int;
    return matched_count >= 2;
  elsif play_type in ('SP', 'SUPER_PALE', 'SUPERPALE') then
    if length(digits) <> 4 or secondary_draw is null then
      return false;
    end if;
    secondary_parts := regexp_split_to_array(
      regexp_replace(coalesce(secondary_draw->>'number_raw', secondary_draw->>'number_digits', secondary_draw->>'number', ''), '[^0-9-]', '', 'g'),
      '-'
    );
    secondary_first_pick := lpad(coalesce(secondary_parts[1], ''), 2, '0');
    pair_a := substring(digits from 1 for 2);
    pair_b := substring(digits from 3 for 2);
    return (
      (pair_a = first_pick and pair_b = secondary_first_pick) or
      (pair_a = secondary_first_pick and pair_b = first_pick)
    );
  elsif play_type in ('P3', 'PICK3_STRAIGHT') then
    return digits <> '' and digits = drawn_digits;
  elsif play_type in ('P4', 'PICK4_STRAIGHT') then
    return digits <> '' and digits = drawn_digits;
  elsif play_type in ('P3BOX', 'PICK3_BOX') then
    box_way := public.lotterynet_pick_box_way(digits);
    return digits <> '' and public.lotterynet_is_permutation_match(drawn_digits, digits) and box_way in (3, 6);
  elsif play_type in ('P4BOX', 'PICK4_BOX') then
    box_way := public.lotterynet_pick_box_way(digits);
    return digits <> '' and public.lotterynet_is_permutation_match(drawn_digits, digits) and box_way in (4, 6, 12, 24);
  elsif play_type = 'P3B' then
    return length(digits) = 2 and right(drawn_digits, 2) = digits;
  elsif play_type = 'P4B' then
    return length(digits) = 2 and right(drawn_digits, 2) = digits;
  end if;

  return false;
end;
$function$;

comment on function public.lotterynet_ticket_item_can_win_against_draw(jsonb, jsonb, jsonb)
is 'Returns true only when a ticket item can actually win against a specific result draw row.';

revoke all on function public.lotterynet_ticket_item_can_win_against_draw(jsonb, jsonb, jsonb)
  from public, anon, authenticated;
grant execute on function public.lotterynet_ticket_item_can_win_against_draw(jsonb, jsonb, jsonb)
  to service_role;

create or replace function public.lotterynet_process_result_reconcile_jobs_for_day(
  p_result_day_key text,
  p_job_limit int default 12,
  p_ticket_limit int default 500
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  job_row record;
  ticket_row record;
  processed_jobs int := 0;
  processed_tickets int := 0;
  continued_jobs int := 0;
  skipped_locked boolean := false;
  job_tickets int;
  job_limit int;
  ticket_limit int;
  day_aliases text[];
  day_iso text;
  day_legacy text;
  day_date date;
  lock_key bigint;
begin
  lock_key := hashtextextended('lotterynet_result_reconcile:' || coalesce(p_result_day_key, ''), 0);
  if not pg_try_advisory_xact_lock(lock_key) then
    return jsonb_build_object(
      'ok', true,
      'dayKey', p_result_day_key,
      'skipped', true,
      'reason', 'result reconcile already running for this day',
      'processedJobs', 0,
      'processedTickets', 0,
      'continuedJobs', 0
    );
  end if;

  job_limit := least(greatest(coalesce(p_job_limit, 12), 1), 12);
  ticket_limit := least(greatest(coalesce(p_ticket_limit, 500), 1), 500);
  day_aliases := public.lotterynet_ticket_date_aliases(p_result_day_key);
  day_iso := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{4}-\d{2}-\d{2}$'
    limit 1
  );
  day_legacy := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{2}-\d{2}-\d{4}$'
    limit 1
  );
  day_date := nullif(day_iso, '')::date;

  for job_row in
    select *
    from public.result_reconcile_jobs
    where status = 'pending'
      and result_day_key = any(day_aliases)
    order by
      case
        when result_day_key = to_char((now() at time zone 'America/Santo_Domingo')::date, 'DD-MM-YYYY') then 0
        when result_day_key = to_char((now() at time zone 'America/Santo_Domingo')::date - interval '1 day', 'DD-MM-YYYY') then 1
        else 2
      end,
      created_at desc
    limit job_limit
    for update skip locked
  loop
    begin
      job_tickets := 0;

      update public.result_reconcile_jobs
      set status = 'running',
          locked_at = now(),
          attempts = attempts + 1,
          last_error = null
      where id = job_row.id;

      for ticket_row in
        with candidate_draws as (
          select
            rd.result_day_key,
            rd.lottery_legacy_id,
            lower(coalesce(rd.lottery_name, '')) as lottery_name,
            rd.number_raw,
            rd.number_digits
          from public.result_draws rd
          where rd.result_day_key = any(day_aliases)
        )
        select distinct t.id
        from public.tickets t
        where (
            (day_date is not null and t.draw_date_real = day_date)
            or (day_legacy is not null and t.legacy_day_key = day_legacy)
            or (day_iso is not null and t.legacy_day_key = day_iso)
            or (day_legacy is not null and t.draw_date = day_legacy)
            or (day_iso is not null and t.draw_date = day_iso)
          )
          and t.deleted_at is null
          and t.voided_at is null
          and t.invalidated_at is null
          and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
          and exists (
            select 1
            from public.ticket_items ti
            join candidate_draws rd
              on (
                rd.lottery_legacy_id = ti.lottery_legacy_id
                or rd.lottery_name = lower(coalesce(ti.lottery_name, ''))
              )
            left join candidate_draws secondary_rd
              on (
                secondary_rd.lottery_legacy_id = ti.secondary_lottery_legacy_id
                or secondary_rd.lottery_name = lower(coalesce(ti.secondary_lottery_name, ''))
              )
            where ti.ticket_id = t.id
              and (
                job_row.lottery_legacy_id is null
                or ti.lottery_legacy_id = job_row.lottery_legacy_id
                or ti.secondary_lottery_legacy_id = job_row.lottery_legacy_id
              )
              and public.lotterynet_ticket_item_can_win_against_draw(
                to_jsonb(ti),
                to_jsonb(rd),
                case
                  when upper(coalesce(ti.play_type::text, '')) in ('SP', 'SUPER_PALE', 'SUPERPALE') then to_jsonb(secondary_rd)
                  else null
                end
              )
          )
        order by t.id
        limit ticket_limit
      loop
        perform public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
        processed_tickets := processed_tickets + 1;
        job_tickets := job_tickets + 1;
      end loop;

      if job_tickets >= ticket_limit then
        update public.result_reconcile_jobs
        set status = 'pending',
            locked_at = null,
            last_error = 'Requeued after bounded ticket limit; continuing in next pass.'
        where id = job_row.id;
        continued_jobs := continued_jobs + 1;
      else
        update public.result_reconcile_jobs
        set status = 'completed',
            completed_at = now(),
            locked_at = null,
            last_error = null
        where id = job_row.id;
      end if;

      processed_jobs := processed_jobs + 1;
    exception when others then
      update public.result_reconcile_jobs
      set status = 'failed',
          locked_at = null,
          last_error = sqlerrm
      where id = job_row.id;
    end;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'dayKey', p_result_day_key,
    'skipped', skipped_locked,
    'processedJobs', processed_jobs,
    'processedTickets', processed_tickets,
    'continuedJobs', continued_jobs,
    'jobLimit', job_limit,
    'ticketLimit', ticket_limit
  );
end;
$function$;

comment on function public.lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer)
is 'Processes only candidate tickets for a result day by matching ticket items against the winning draw rows for their lottery.';

revoke all on function public.lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer)
  from public, anon, authenticated;
grant execute on function public.lotterynet_process_result_reconcile_jobs_for_day(text, integer, integer)
  to service_role;

commit;
