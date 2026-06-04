begin;

create index if not exists tickets_reconcile_draw_date_real_active_idx
on public.tickets (draw_date_real, id)
where deleted_at is null
  and voided_at is null
  and invalidated_at is null;

create index if not exists tickets_reconcile_legacy_day_active_idx
on public.tickets (legacy_day_key, id)
where deleted_at is null
  and voided_at is null
  and invalidated_at is null
  and legacy_day_key is not null;

create index if not exists tickets_reconcile_draw_date_active_idx
on public.tickets (draw_date, id)
where deleted_at is null
  and voided_at is null
  and invalidated_at is null
  and draw_date is not null;

create index if not exists ticket_items_reconcile_lottery_ticket_idx
on public.ticket_items (lottery_legacy_id, ticket_id);

create index if not exists ticket_items_reconcile_secondary_lottery_ticket_idx
on public.ticket_items (secondary_lottery_legacy_id, ticket_id)
where secondary_lottery_legacy_id is not null;

create index if not exists result_reconcile_jobs_day_pending_idx
on public.result_reconcile_jobs (result_day_key, created_at)
where status = 'pending';

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
  canonical_ticket jsonb;
  owner_keys text[];
begin
  select * into ticket_row from public.tickets where id = p_ticket_id;
  if ticket_row.id is null then
    return;
  end if;

  app_status := public.lotterynet_ticket_app_status(ticket_row.status, ticket_row.estado);
  prize_amount := coalesce(ticket_row.payout_amount, 0);
  updated_epoch_ms := (extract(epoch from now()) * 1000)::bigint;
  owner_keys := public.lotterynet_ticket_owner_aliases(ticket_row);

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

  select ticket into canonical_ticket
  from public.lotterynet_tickets_by_owner owner_row
  cross join lateral jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) as payload_ticket(ticket)
  where owner_row.owner_key = any(owner_keys)
    and jsonb_typeof(owner_row.payload) = 'object'
    and jsonb_typeof(owner_row.payload->'tickets') = 'array'
    and public.lotterynet_owner_payload_ticket_matches(ticket, ticket_row)
  order by owner_row.updated_at desc
  limit 1;

  canonical_ticket := coalesce(
    canonical_ticket,
    jsonb_build_object(
      'id', coalesce(nullif(ticket_row.legacy_ticket_id, ''), nullif(ticket_row.client_request_id, ''), ticket_row.id::text),
      'serial', coalesce(nullif(ticket_row.ticket_code, ''), ticket_row.id::text),
      'ticketCode', coalesce(nullif(ticket_row.ticket_code, ''), ticket_row.id::text),
      'clientRequestId', coalesce(nullif(ticket_row.client_request_id, ''), nullif(ticket_row.legacy_ticket_id, ''), ticket_row.id::text),
      'drawDateKey', coalesce(ticket_row.draw_date_real::text, nullif(ticket_row.draw_date, ''), nullif(ticket_row.legacy_day_key, '')),
      'drawDate', coalesce(ticket_row.draw_date_real::text, nullif(ticket_row.draw_date, ''), nullif(ticket_row.legacy_day_key, '')),
      'total', coalesce(ticket_row.total_amount, ticket_row.monto, 0),
      'plays', '[]'::jsonb,
      'items', '[]'::jsonb
    )
  );

  update public.lotterynet_tickets_by_owner owner_row
  set payload = jsonb_set(
        owner_row.payload,
        '{tickets}',
        (
          select coalesce(
            jsonb_agg(
              case
                when public.lotterynet_owner_payload_ticket_matches(ticket, ticket_row)
                  then ticket || ticket_patch
                else ticket
              end
              order by ord
            ),
            '[]'::jsonb
          )
          from jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb))
            with ordinality as payload_ticket(ticket, ord)
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
      where public.lotterynet_owner_payload_ticket_matches(ticket, ticket_row)
    );

  update public.lotterynet_tickets_by_owner owner_row
  set payload = jsonb_set(
        owner_row.payload,
        '{tickets}',
        coalesce(owner_row.payload->'tickets', '[]'::jsonb) || jsonb_build_array(canonical_ticket || ticket_patch),
        true
      ),
      updated_at = now()
  where owner_row.owner_key = any(owner_keys)
    and jsonb_typeof(owner_row.payload) = 'object'
    and not exists (
      select 1
      from jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) as payload_ticket(ticket)
      where public.lotterynet_owner_payload_ticket_matches(ticket, ticket_row)
    );

  insert into public.lotterynet_tickets_by_owner(owner_key, payload, updated_at)
  select key_value, jsonb_build_object('tickets', jsonb_build_array(canonical_ticket || ticket_patch)), now()
  from unnest(owner_keys) as key_value
  where not exists (
    select 1
    from public.lotterynet_tickets_by_owner owner_row
    where owner_row.owner_key = key_value
  )
  on conflict (owner_key) do nothing;
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
  job_tickets int;
  day_aliases text[];
  day_iso text;
  day_legacy text;
  day_date date;
begin
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
    order by created_at
    limit greatest(coalesce(p_job_limit, 12), 1)
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
        limit greatest(coalesce(p_ticket_limit, 500), 1)
      loop
        perform public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
        processed_tickets := processed_tickets + 1;
        job_tickets := job_tickets + 1;
      end loop;

      update public.result_reconcile_jobs
      set status = 'completed',
          completed_at = now(),
          locked_at = null,
          last_error = case
            when job_tickets >= greatest(coalesce(p_ticket_limit, 500), 1)
              then 'Completed after ticket limit; run again if more tickets are expected for this draw.'
            else null
          end
      where id = job_row.id;

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
    'processedJobs', processed_jobs,
    'processedTickets', processed_tickets
  );
end;
$$;

create or replace function public.lotterynet_reconcile_owner_tickets_for_day(
  p_owner_key text,
  p_day_key text,
  p_limit int default 500
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  ticket_row record;
  checked_count int := 0;
  winner_count int := 0;
  total_prize numeric := 0;
  calc jsonb;
  day_aliases text[];
  day_iso text;
  day_legacy text;
  day_date date;
begin
  day_aliases := public.lotterynet_ticket_date_aliases(p_day_key);
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

  for ticket_row in
    select distinct t.id
    from public.tickets t
    where (
        (day_date is not null and t.draw_date_real = day_date)
        or (day_legacy is not null and t.legacy_day_key = day_legacy)
        or (day_iso is not null and t.legacy_day_key = day_iso)
        or (day_legacy is not null and t.draw_date = day_legacy)
        or (day_iso is not null and t.draw_date = day_iso)
      )
      and (
        t.admin_key = p_owner_key
        or t.cashier_key = p_owner_key
        or t.admin_id::text = p_owner_key
        or t.profile_id::text = p_owner_key
      )
      and t.deleted_at is null
      and t.voided_at is null
      and t.invalidated_at is null
    order by t.id
    limit greatest(coalesce(p_limit, 500), 1)
  loop
    checked_count := checked_count + 1;
    calc := public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
    if coalesce((calc->>'totalPrize')::numeric, 0) > 0 then
      winner_count := winner_count + 1;
      total_prize := total_prize + coalesce((calc->>'totalPrize')::numeric, 0);
    end if;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'ownerKey', p_owner_key,
    'dayKey', p_day_key,
    'checked', checked_count,
    'winners', winner_count,
    'totalPrize', total_prize
  );
end;
$$;

revoke all on function public.lotterynet_sync_ticket_owner_payload(uuid) from public, anon, authenticated;
revoke all on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) from public, anon, authenticated;
revoke all on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int) from public, anon, authenticated;

grant execute on function public.lotterynet_sync_ticket_owner_payload(uuid) to service_role;
grant execute on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) to service_role;
grant execute on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int) to service_role;

comment on function public.lotterynet_sync_ticket_owner_payload(uuid)
is 'LotteryNet owner snapshot sync scoped to the ticket owner aliases; avoids global JSON snapshot scans during prize reconcile.';

comment on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int)
is 'Force-reconciles one admin/cashier owner for a draw day and refreshes owner snapshots.';

commit;
