begin;

create or replace function public.lotterynet_ticket_owner_aliases(ticket_row public.tickets)
returns text[]
language sql
stable
set search_path = public
as $$
  with seed_keys(key_value) as (
    values
      (nullif(trim(ticket_row.admin_key), '')),
      (nullif(trim(ticket_row.cashier_key), '')),
      (nullif(trim(ticket_row.admin_id::text), '')),
      (nullif(trim(ticket_row.profile_id::text), ''))
  ),
  matched_user_rows as (
    select u
    from public.lotterynet_users_state lus
    cross join lateral jsonb_array_elements(
      case
        when jsonb_typeof(lus.payload->'users') = 'array' then lus.payload->'users'
        else '[]'::jsonb
      end
    ) as users(u)
    where lus.scope = 'global'
      and (
        nullif(trim(u->>'id'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'user'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'username'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'adminId'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'adminUser'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'cashierId'), '') in (select key_value from seed_keys where key_value is not null)
        or nullif(trim(u->>'cashierUser'), '') in (select key_value from seed_keys where key_value is not null)
      )
  ),
  candidate_keys as (
    select key_value from seed_keys where key_value is not null
    union
    select nullif(trim(u->>'id'), '') from matched_user_rows
    union
    select nullif(trim(u->>'user'), '') from matched_user_rows
    union
    select nullif(trim(u->>'username'), '') from matched_user_rows
    union
    select nullif(trim(u->>'adminId'), '') from matched_user_rows
    union
    select nullif(trim(u->>'adminUser'), '') from matched_user_rows
    union
    select nullif(trim(u->>'cashierId'), '') from matched_user_rows
    union
    select nullif(trim(u->>'cashierUser'), '') from matched_user_rows
  )
  select coalesce(array_agg(distinct key_value), array[]::text[])
  from candidate_keys
  where key_value is not null;
$$;

create or replace function public.lotterynet_touch_ticket_owners_from_ticket()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_key text;
begin
  foreach owner_key in array public.lotterynet_ticket_owner_aliases(new)
  loop
    perform public.lotterynet_touch_ticket_owner(owner_key);
  end loop;

  if tg_op = 'UPDATE' then
    foreach owner_key in array public.lotterynet_ticket_owner_aliases(old)
    loop
      perform public.lotterynet_touch_ticket_owner(owner_key);
    end loop;
  end if;

  return new;
end;
$$;

create or replace function public.lotterynet_sync_winner_payload_from_ticket()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  new_status text := lower(coalesce(new.status, new.estado, ''));
  old_status text := '';
  should_sync boolean := false;
begin
  if tg_op = 'UPDATE' then
    old_status := lower(coalesce(old.status, old.estado, ''));
    should_sync := should_sync
      or coalesce(old.payout_amount, 0) > 0
      or old_status in ('ganador', 'winner', 'pending_winner', 'pagado', 'paid');
  end if;

  should_sync := should_sync
    or coalesce(new.payout_amount, 0) > 0
    or new_status in ('ganador', 'winner', 'pending_winner', 'pagado', 'paid');

  if should_sync then
    perform public.lotterynet_sync_ticket_owner_payload(new.id);
  end if;

  return new;
end;
$$;

drop trigger if exists lotterynet_sync_winner_payload_from_ticket_trigger on public.tickets;
create trigger lotterynet_sync_winner_payload_from_ticket_trigger
after insert or update of status, estado, payout_amount, paid_at
on public.tickets
for each row
execute function public.lotterynet_sync_winner_payload_from_ticket();

create or replace function public.lotterynet_sync_winner_payload_from_prize_item()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  should_sync boolean := false;
begin
  if tg_op = 'UPDATE' then
    should_sync := should_sync
      or coalesce(old.is_winner, false)
      or coalesce(old.payout_amount, 0) > 0;
  end if;

  should_sync := should_sync
    or coalesce(new.is_winner, false)
    or coalesce(new.payout_amount, 0) > 0;

  if should_sync then
    perform public.lotterynet_sync_ticket_owner_payload(new.ticket_id);
  end if;

  return new;
end;
$$;

drop trigger if exists lotterynet_sync_winner_payload_from_prize_item_trigger on public.ticket_prize_items;
create trigger lotterynet_sync_winner_payload_from_prize_item_trigger
after insert or update of is_winner, payout_amount
on public.ticket_prize_items
for each row
execute function public.lotterynet_sync_winner_payload_from_prize_item();

create or replace function public.lotterynet_heal_winner_payloads_for_day(
  p_day_key text,
  p_limit int default 100
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  ticket_row record;
  checked_count int := 0;
  synced_count int := 0;
begin
  for ticket_row in
    select distinct t.id
    from public.tickets t
    left join public.ticket_prize_items tpi on tpi.ticket_id = t.id
    where coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date) = any(public.lotterynet_ticket_date_aliases(p_day_key))
      and t.deleted_at is null
      and t.voided_at is null
      and t.invalidated_at is null
      and (
        coalesce(t.payout_amount, 0) > 0
        or lower(coalesce(t.status, t.estado, '')) in ('ganador', 'winner', 'pending_winner', 'pagado', 'paid')
        or coalesce(tpi.is_winner, false)
        or coalesce(tpi.payout_amount, 0) > 0
      )
    order by t.id
    limit greatest(coalesce(p_limit, 100), 1)
  loop
    checked_count := checked_count + 1;
    perform public.lotterynet_sync_ticket_owner_payload(ticket_row.id);
    synced_count := synced_count + 1;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'dayKey', p_day_key,
    'checked', checked_count,
    'synced', synced_count
  );
end;
$$;

revoke all on function public.lotterynet_ticket_owner_aliases(public.tickets) from public, anon, authenticated;
revoke all on function public.lotterynet_touch_ticket_owners_from_ticket() from public, anon, authenticated;
revoke all on function public.lotterynet_sync_winner_payload_from_ticket() from public, anon, authenticated;
revoke all on function public.lotterynet_sync_winner_payload_from_prize_item() from public, anon, authenticated;
revoke all on function public.lotterynet_heal_winner_payloads_for_day(text, int) from public, anon, authenticated;

grant execute on function public.lotterynet_ticket_owner_aliases(public.tickets) to service_role;
grant execute on function public.lotterynet_heal_winner_payloads_for_day(text, int) to service_role;

comment on function public.lotterynet_heal_winner_payloads_for_day(text, int)
is 'Repairs winner/paid ticket owner snapshots for a draw day after prize calculation, payment, or realtime delivery drift.';

commit;
