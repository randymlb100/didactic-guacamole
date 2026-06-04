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

revoke all on function public.lotterynet_ticket_owner_aliases(public.tickets) from public, anon, authenticated;
grant execute on function public.lotterynet_ticket_owner_aliases(public.tickets) to service_role;

commit;
