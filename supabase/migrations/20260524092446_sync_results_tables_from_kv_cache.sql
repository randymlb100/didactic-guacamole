create or replace function public.lotterynet_sync_results_by_day_from_kv()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  day_key text;
  parsed_payload jsonb;
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
    insert into public.lotterynet_results_by_day(result_date, payload, updated_at)
    values (day_key, parsed_payload, coalesce(new.upd, now()))
    on conflict (result_date) do update
      set payload = excluded.payload,
          updated_at = excluded.updated_at;
  elsif new.key like 'pick_results_cache_by_day:%' then
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

drop trigger if exists lotterynet_sync_results_by_day_from_kv_trigger on public.lotterynet_kv;
create trigger lotterynet_sync_results_by_day_from_kv_trigger
after insert or update of key, value, upd on public.lotterynet_kv
for each row
when (new.key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$')
execute function public.lotterynet_sync_results_by_day_from_kv();

with parsed as (
  select
    substring(key from ':([0-9]{2}-[0-9]{2}-[0-9]{4})$') as result_date,
    case
      when jsonb_typeof(value::jsonb) = 'object' and (value::jsonb) ? 'results' then (value::jsonb)->'results'
      else value::jsonb
    end as payload,
    upd
  from public.lotterynet_kv
  where key ~ '^lot_results_cache_by_day:[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
insert into public.lotterynet_results_by_day(result_date, payload, updated_at)
select result_date, payload, coalesce(upd, now())
from parsed
where jsonb_typeof(payload) = 'array'
on conflict (result_date) do update
  set payload = excluded.payload,
      updated_at = excluded.updated_at;

with parsed as (
  select
    substring(key from ':([0-9]{2}-[0-9]{2}-[0-9]{4})$') as result_date,
    case
      when jsonb_typeof(value::jsonb) = 'object' and (value::jsonb) ? 'results' then (value::jsonb)->'results'
      else value::jsonb
    end as payload,
    upd
  from public.lotterynet_kv
  where key ~ '^pick_results_cache_by_day:[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
insert into public.lotterynet_pick_results_by_day(result_date, payload, updated_at)
select result_date, payload, coalesce(upd, now())
from parsed
where jsonb_typeof(payload) = 'array'
on conflict (result_date) do update
  set payload = excluded.payload,
      updated_at = excluded.updated_at;
