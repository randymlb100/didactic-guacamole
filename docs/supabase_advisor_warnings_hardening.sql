-- LotteryNet Supabase Advisor warning hardening.
--
-- Safe production cleanup for the warnings shown after legacy table lockdown:
-- 1. Fix "Function Search Path Mutable" by pinning search_path.
-- 2. Fix public execution on legacy SECURITY DEFINER helper rls_auto_enable().
--
-- This script does not drop data and does not change the Android runtime tables.
-- It intentionally does not remove the current compatibility RLS policies that
-- use USING (true), because the Android app still uses the public Supabase key.
-- Tight per-admin/per-cashier policies require Supabase Auth or backend/Edge
-- Functions first.

begin;

-- Pin search_path for known functions when they exist.
do $$
declare
  fn record;
begin
  for fn in
    select
      n.nspname as schema_name,
      p.proname as function_name,
      pg_get_function_identity_arguments(p.oid) as identity_args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'lotterynet_kv_set_upd',
        'upsert_tickets_merge',
        'lotterynet_try_jsonb',
        'rls_auto_enable'
      )
  loop
    execute format(
      'alter function %I.%I(%s) set search_path = public, pg_temp',
      fn.schema_name,
      fn.function_name,
      fn.identity_args
    );
  end loop;
end $$;

-- Revoke public/client execution from SECURITY DEFINER helper if it exists.
-- The function can still be executed by privileged database roles.
do $$
declare
  fn record;
begin
  for fn in
    select
      n.nspname as schema_name,
      p.proname as function_name,
      pg_get_function_identity_arguments(p.oid) as identity_args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'rls_auto_enable'
  loop
    execute format(
      'revoke execute on function %I.%I(%s) from public, anon, authenticated',
      fn.schema_name,
      fn.function_name,
      fn.identity_args
    );
  end loop;
end $$;

commit;
