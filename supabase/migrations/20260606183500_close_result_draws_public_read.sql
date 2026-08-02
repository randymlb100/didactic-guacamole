-- Close direct public reads on normalized result rows.
--
-- Current Android result reads go through get-results-v2 / get-results-status,
-- and live invalidation uses private Realtime Broadcast on ln:results:<day>.
-- The old Postgres Changes fallback has been removed from the native client, so
-- result_draws no longer needs direct anon/authenticated SELECT.

revoke select on table public.result_draws from anon;
revoke select on table public.result_draws from authenticated;

drop policy if exists result_draws_read_all on public.result_draws;

-- Rollback, if a legacy APK is proven to require direct result_draws reads or
-- Postgres Changes fallback:
--
-- grant select on table public.result_draws to anon, authenticated;
--
-- create policy result_draws_read_all
-- on public.result_draws
-- for select
-- to anon, authenticated
-- using (true);
