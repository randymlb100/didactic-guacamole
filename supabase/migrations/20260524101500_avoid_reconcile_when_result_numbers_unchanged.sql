create or replace function public.lotterynet_result_payload_fingerprint(p_payload jsonb)
returns jsonb
language sql
immutable
set search_path = public
as $$
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', coalesce(elem->>'id', ''),
        'name', lower(coalesce(elem->>'name', '')),
        'number', coalesce(elem->>'number', ''),
        'pick3', coalesce(elem->>'pick3', ''),
        'pick4', coalesce(elem->>'pick4', ''),
        'status', coalesce(elem->>'status', '')
      )
      order by coalesce(elem->>'id', ''), lower(coalesce(elem->>'name', ''))
    ),
    '[]'::jsonb
  )
  from jsonb_array_elements(
    case when jsonb_typeof(p_payload) = 'array' then p_payload else '[]'::jsonb end
  ) elem;
$$;

revoke all on function public.lotterynet_result_payload_fingerprint(jsonb) from public, anon, authenticated;

create or replace function public.lotterynet_sync_results_by_day_from_kv()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  day_key text;
  parsed_payload jsonb;
  existing_payload jsonb;
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
  parsed_payload := new.value::jsonb;

  if jsonb_typeof(parsed_payload) = 'object' and parsed_payload ? 'results' then
    parsed_payload := parsed_payload->'results';
  end if;

  if jsonb_typeof(parsed_payload) <> 'array' then
    return new;
  end if;

  if new.key like 'lot_results_cache_by_day:%' then
    select payload into existing_payload
    from public.lotterynet_results_by_day
    where result_date = day_key;

    if existing_payload is not null
      and public.lotterynet_result_payload_fingerprint(existing_payload) = public.lotterynet_result_payload_fingerprint(parsed_payload) then
      update public.lotterynet_results_by_day
      set updated_at = coalesce(new.upd, now())
      where result_date = day_key;
      return new;
    end if;

    insert into public.lotterynet_results_by_day(result_date, payload, updated_at)
    values (day_key, parsed_payload, coalesce(new.upd, now()))
    on conflict (result_date) do update
      set payload = excluded.payload,
          updated_at = excluded.updated_at;
  elsif new.key like 'pick_results_cache_by_day:%' then
    select payload into existing_payload
    from public.lotterynet_pick_results_by_day
    where result_date = day_key;

    if existing_payload is not null
      and public.lotterynet_result_payload_fingerprint(existing_payload) = public.lotterynet_result_payload_fingerprint(parsed_payload) then
      update public.lotterynet_pick_results_by_day
      set updated_at = coalesce(new.upd, now())
      where result_date = day_key;
      return new;
    end if;

    insert into public.lotterynet_pick_results_by_day(result_date, payload, updated_at)
    values (day_key, parsed_payload, coalesce(new.upd, now()))
    on conflict (result_date) do update
      set payload = excluded.payload,
          updated_at = excluded.updated_at;
  end if;

  return new;
exception
  when others then
    raise warning 'lotterynet_sync_results_by_day_from_kv failed for key %: %', new.key, sqlerrm;
    return new;
end;
$$;

revoke all on function public.lotterynet_sync_results_by_day_from_kv() from public, anon, authenticated;
