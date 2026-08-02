begin;

create or replace function public.lotterynet_ticket_owner_aliases(ticket_row public.tickets)
returns text[]
language sql
stable
set search_path = public
as $function$
  with seed_keys(key_value) as (
    values
      (nullif(trim(ticket_row.admin_key), '')),
      (nullif(trim(ticket_row.cashier_key), '')),
      (nullif(trim(ticket_row.admin_id::text), '')),
      (nullif(trim(ticket_row.profile_id::text), ''))
  ),
  valid_seed_keys as (
    select key_value
    from seed_keys
    where key_value is not null
      and lower(key_value) not in ('null', 'undefined', 'none', 'nil')
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
        nullif(trim(u->>'id'), '') in (select key_value from valid_seed_keys)
        or nullif(trim(u->>'user'), '') in (select key_value from valid_seed_keys)
        or nullif(trim(u->>'username'), '') in (select key_value from valid_seed_keys)
        or nullif(trim(u->>'cashierId'), '') in (select key_value from valid_seed_keys)
        or nullif(trim(u->>'cashierUser'), '') in (select key_value from valid_seed_keys)
      )
  ),
  candidate_keys as (
    select key_value from valid_seed_keys
    union select nullif(trim(u->>'id'), '') from matched_user_rows
    union select nullif(trim(u->>'user'), '') from matched_user_rows
    union select nullif(trim(u->>'username'), '') from matched_user_rows
    union select nullif(trim(u->>'adminId'), '') from matched_user_rows
    union select nullif(trim(u->>'adminUser'), '') from matched_user_rows
    union select nullif(trim(u->>'cashierId'), '') from matched_user_rows
    union select nullif(trim(u->>'cashierUser'), '') from matched_user_rows
  )
  select coalesce(array_agg(distinct key_value), array[]::text[])
  from candidate_keys
  where key_value is not null
    and lower(key_value) not in ('null', 'undefined', 'none', 'nil');
$function$;

do $block$
declare
  function_sql text;
  updated_sql text;
begin
  select pg_get_functiondef('public.lotterynet_sync_winner_payload_from_ticket()'::regprocedure)
  into function_sql;

  if position('lotterynet.skip_ticket_owner_auto_sync' in function_sql) = 0 then
    updated_sql := replace(
      function_sql,
      E'begin\n  if tg_op = ''UPDATE'' then',
      E'begin\n  if current_setting(''lotterynet.skip_ticket_owner_auto_sync'', true) = ''on'' then\n    return new;\n  end if;\n\n  if tg_op = ''UPDATE'' then'
    );
    if updated_sql = function_sql then
      raise exception 'Could not add ticket owner auto-sync guard';
    end if;
    execute updated_sql;
  end if;
end
$block$;

do $block$
declare
  function_sql text;
  updated_sql text;
begin
  select pg_get_functiondef('public.lotterynet_reconcile_ticket_prize_v2(uuid)'::regprocedure)
  into function_sql;

  if position('lotterynet.skip_ticket_owner_auto_sync' in function_sql) = 0 then
    updated_sql := replace(
      function_sql,
      E'begin\n  select lower(coalesce(status, estado, '''')) into current_status',
      E'begin\n  perform set_config(''lock_timeout'', ''1000'', true);\n  perform set_config(''lotterynet.skip_ticket_owner_auto_sync'', ''on'', true);\n\n  select lower(coalesce(status, estado, '''')) into current_status'
    );
    if updated_sql = function_sql then
      raise exception 'Could not add reconcile lock and sync guards';
    end if;
    execute updated_sql;
  end if;
end
$block$;

select cron.alter_job(
  job_id := 6,
  command := $cron$
set statement_timeout = '10s';
set lock_timeout = '1s';
select public.lotterynet_results_prize_watchdog(8, 300);
$cron$
);

commit;
