begin;

create or replace function public.lotterynet_result_draw_stable_hash(
  p_result_day_key text,
  p_lottery_legacy_id text,
  p_game text,
  p_draw_name text,
  p_number_raw text,
  p_status text
)
returns text
language sql
immutable
as $$
  select md5(
    jsonb_build_object(
      'resultDayKey', coalesce(p_result_day_key, ''),
      'lotteryLegacyId', coalesce(p_lottery_legacy_id, ''),
      'game', coalesce(p_game, ''),
      'drawName', coalesce(p_draw_name, ''),
      'numberRaw', coalesce(p_number_raw, ''),
      'status', coalesce(p_status, '')
    )::text
  );
$$;

comment on function public.lotterynet_result_draw_stable_hash(text, text, text, text, text, text)
is 'Stable result identity hash. It intentionally ignores scraper metadata such as lastSeenAt, firstSeenAt, and timestamp fields so repeated sightings do not enqueue duplicate prize jobs.';

create or replace function public.lotterynet_upsert_result_draws_from_payload(
  p_result_day_key text,
  p_payload jsonb,
  p_source text default 'cache'
)
returns int
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
  draw_status text;
  stable_hash text;
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

    draw_status := coalesce(nullif(row_value->>'status', ''), case when draw_number = '' then 'pending' else 'published' end);
    stable_hash := public.lotterynet_result_draw_stable_hash(
      p_result_day_key,
      row_value->>'id',
      draw_game,
      v_draw_name,
      draw_number,
      draw_status
    );

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
      draw_status,
      row_value,
      stable_hash,
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
      perform public.lotterynet_enqueue_result_reconcile_job(p_result_day_key, row_value->>'id', draw_game);
    end if;
  end loop;

  if changed_count > 0 then
    perform public.lotterynet_touch_results_signal(p_result_day_key, p_source, changed_count);
  end if;

  return changed_count;
end;
$$;

revoke all on function public.lotterynet_result_draw_stable_hash(text, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text) from public, anon, authenticated;
grant execute on function public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text) to service_role;

do $$
begin
  alter table public.result_draws disable trigger result_draws_enqueue_reconcile_job;
exception when undefined_object then
  null;
end;
$$;

update public.result_draws
set source_hash = public.lotterynet_result_draw_stable_hash(
      result_day_key,
      lottery_legacy_id,
      game,
      draw_name,
      number_raw,
      status
    )
where result_date >= (now() at time zone 'America/Santo_Domingo')::date - interval '2 days'
  and source_hash is distinct from public.lotterynet_result_draw_stable_hash(
      result_day_key,
      lottery_legacy_id,
      game,
      draw_name,
      number_raw,
      status
    );

do $$
begin
  alter table public.result_draws enable trigger result_draws_enqueue_reconcile_job;
exception when undefined_object then
  null;
end;
$$;

create or replace function public.lotterynet_process_result_reconcile_jobs_for_day(
  p_result_day_key text,
  p_job_limit int default 12,
  p_ticket_limit int default 500
)
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
        select distinct t.id
        from public.ticket_items ti
        join public.tickets t on t.id = ti.ticket_id
        where (
            (day_date is not null and t.draw_date_real = day_date)
            or (day_legacy is not null and t.legacy_day_key = day_legacy)
            or (day_iso is not null and t.legacy_day_key = day_iso)
            or (day_legacy is not null and t.draw_date = day_legacy)
            or (day_iso is not null and t.draw_date = day_iso)
          )
          and (
            job_row.lottery_legacy_id is null
            or ti.lottery_legacy_id = job_row.lottery_legacy_id
            or ti.secondary_lottery_legacy_id = job_row.lottery_legacy_id
          )
          and t.deleted_at is null
          and t.voided_at is null
          and t.invalidated_at is null
          and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
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
$$;

revoke all on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) from public, anon, authenticated;
grant execute on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) to service_role;

select cron.unschedule('lotterynet-results-prize-watchdog')
where exists (select 1 from cron.job where jobname = 'lotterynet-results-prize-watchdog');

select cron.schedule(
  'lotterynet-results-prize-watchdog',
  '*/2 * * * *',
  $$
  select public.lotterynet_results_prize_watchdog(8, 300);
  $$
);

comment on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int)
is 'Processes active-day result prize jobs with stable result hashes, current-day priority, advisory locking, and bounded batches.';

commit;
