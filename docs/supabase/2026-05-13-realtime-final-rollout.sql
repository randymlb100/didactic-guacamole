do $$
begin
  if exists (
    select 1
    from pg_publication
    where pubname = 'supabase_realtime'
  ) then
    if exists (
      select 1
      from information_schema.tables
      where table_schema = 'public' and table_name = 'lotterynet_users_state'
    ) and not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = 'lotterynet_users_state'
    ) then
      alter publication supabase_realtime add table public.lotterynet_users_state;
    end if;

    if exists (
      select 1
      from information_schema.tables
      where table_schema = 'public' and table_name = 'lotterynet_master_state'
    ) and not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = 'lotterynet_master_state'
    ) then
      alter publication supabase_realtime add table public.lotterynet_master_state;
    end if;

    if exists (
      select 1
      from information_schema.tables
      where table_schema = 'public' and table_name = 'lotterynet_tickets_by_owner'
    ) and not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = 'lotterynet_tickets_by_owner'
    ) then
      alter publication supabase_realtime add table public.lotterynet_tickets_by_owner;
    end if;

    if exists (
      select 1
      from information_schema.tables
      where table_schema = 'public' and table_name = 'lotterynet_kv'
    ) and not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = 'lotterynet_kv'
    ) then
      alter publication supabase_realtime add table public.lotterynet_kv;
    end if;
  else
    raise exception 'Publication supabase_realtime does not exist in this project';
  end if;
end $$;

select schemaname, tablename
from pg_publication_tables
where pubname = 'supabase_realtime'
  and schemaname = 'public'
  and tablename in (
    'lotterynet_users_state',
    'lotterynet_master_state',
    'lotterynet_tickets_by_owner',
    'lotterynet_kv'
  )
order by tablename;
