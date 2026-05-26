-- LotteryNet production hardening for legacy public tables.
--
-- Safe intent:
-- - Do NOT drop data.
-- - Do NOT rename tables.
-- - Enable RLS on legacy tables reported by Supabase Advisor.
-- - Remove public app access from anon/authenticated on those legacy tables.
--
-- Current Kotlin app should use the lotterynet_* runtime tables. This script
-- protects old direct tables so they stop being exposed through PostgREST.

begin;

do $$
declare
  legacy_table text;
begin
  foreach legacy_table in array array[
    'recargas',
    'usuarios',
    'premios',
    'ventas',
    'ticket_items'
  ]
  loop
    if to_regclass('public.' || legacy_table) is not null then
      execute format('alter table public.%I enable row level security', legacy_table);

      execute format('drop policy if exists "legacy compatibility %s" on public.%I', legacy_table, legacy_table);
      execute format('drop policy if exists "LotteryNet legacy read %s" on public.%I', legacy_table, legacy_table);
      execute format('drop policy if exists "LotteryNet legacy write %s" on public.%I', legacy_table, legacy_table);

      execute format('revoke all on table public.%I from anon', legacy_table);
      execute format('revoke all on table public.%I from authenticated', legacy_table);
    end if;
  end loop;
end $$;

-- Keep service_role/postgres usable for backups, migrations, and server-side jobs.
-- Android app public access must go through the lotterynet_* tables or backend functions.

commit;
