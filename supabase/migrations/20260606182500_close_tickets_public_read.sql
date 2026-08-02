-- Close direct public reads on official ticket rows.
--
-- Current Android ticket flows use Edge Functions:
-- - create-ticket-v2
-- - get-ticket-list
-- - get-ticket-delta
-- - void-ticket
-- - pay-ticket
--
-- get-ticket-list keeps its legacy anonymous updated-at compatibility path,
-- but that path reads with the server service role and does not require public
-- Data API SELECT on tickets.

revoke select on table public.tickets from anon;
revoke select on table public.tickets from authenticated;

drop policy if exists "Enable read access for all users" on public.tickets;

-- Rollback, if a legacy APK is proven to require direct ticket reads:
--
-- grant select on table public.tickets to anon, authenticated;
--
-- create policy "Enable read access for all users"
-- on public.tickets
-- for select
-- to public
-- using (true);
