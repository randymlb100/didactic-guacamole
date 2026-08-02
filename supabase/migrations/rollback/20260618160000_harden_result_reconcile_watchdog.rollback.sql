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
  where key_value is not null;
$function$;

do $block$
declare
  function_sql text;
begin
  select pg_get_functiondef('public.lotterynet_sync_winner_payload_from_ticket()'::regprocedure)
  into function_sql;
  function_sql := replace(
    function_sql,
    E'  if current_setting(''lotterynet.skip_ticket_owner_auto_sync'', true) = ''on'' then\n    return new;\n  end if;\n\n',
    ''
  );
  execute function_sql;
end
$block$;

do $block$
declare
  function_sql text;
begin
  select pg_get_functiondef('public.lotterynet_reconcile_ticket_prize_v2(uuid)'::regprocedure)
  into function_sql;
  function_sql := replace(
    function_sql,
    E'  perform set_config(''lock_timeout'', ''1000'', true);\n  perform set_config(''lotterynet.skip_ticket_owner_auto_sync'', ''on'', true);\n\n',
    ''
  );
  execute function_sql;
end
$block$;

select cron.alter_job(
  job_id := 6,
  command := $cron$
select public.lotterynet_results_prize_watchdog(8, 300);
$cron$
);

commit;
