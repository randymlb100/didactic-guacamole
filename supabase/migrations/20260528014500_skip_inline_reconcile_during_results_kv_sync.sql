create or replace function public.lotterynet_reconcile_tickets_for_results_day()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  ticket_row record;
begin
  if current_setting('lotterynet.skip_result_reconcile', true) = 'on' then
    return new;
  end if;

  if tg_op = 'UPDATE'
    and public.lotterynet_result_payload_fingerprint(old.payload) = public.lotterynet_result_payload_fingerprint(new.payload) then
    return new;
  end if;

  for ticket_row in
    select distinct t.id
    from public.tickets t
    where coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date) = any(public.lotterynet_ticket_date_aliases(new.result_date))
      and t.deleted_at is null
      and t.voided_at is null
      and t.invalidated_at is null
      and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
  loop
    perform public.lotterynet_reconcile_ticket_prize(ticket_row.id);
  end loop;

  return new;
end;
$$;

revoke all on function public.lotterynet_reconcile_tickets_for_results_day() from public, anon, authenticated;

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

  perform set_config('lotterynet.skip_result_reconcile', 'on', true);

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
