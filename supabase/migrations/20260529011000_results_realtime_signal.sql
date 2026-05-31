-- Emit a small realtime signal whenever normalized result rows change from cache.
-- Android listens to this key and then fetches the fresh cache payload.

create or replace function public.lotterynet_touch_results_signal(
  p_result_day_key text,
  p_source text,
  p_changed_count int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(p_result_day_key, '') = '' then
    return;
  end if;

  insert into public.lotterynet_kv(key, value, upd)
  values (
    'results_signal_by_day:' || p_result_day_key,
    jsonb_build_object(
      'date', p_result_day_key,
      'source', coalesce(nullif(p_source, ''), 'cache'),
      'changedCount', greatest(coalesce(p_changed_count, 0), 0),
      'updatedAt', now()
    )::text,
    now()
  )
  on conflict (key) do update
    set value = excluded.value,
        upd = excluded.upd;
end;
$$;

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
      perform public.lotterynet_enqueue_result_reconcile_job(p_result_day_key, row_value->>'id', draw_game);
    end if;
  end loop;

  if changed_count > 0 then
    perform public.lotterynet_touch_results_signal(p_result_day_key, p_source, changed_count);
  end if;

  return changed_count;
end;
$$;

revoke all on function public.lotterynet_touch_results_signal(text, text, int) from public, anon, authenticated;
revoke all on function public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text) from public, anon, authenticated;
