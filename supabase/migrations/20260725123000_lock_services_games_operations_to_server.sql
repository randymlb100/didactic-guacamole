begin;

-- This table is an internal operation ledger. Android reaches it only through
-- the services-games Edge Function, which uses service_role.
drop policy if exists services_games_operations_internal_only on public.services_games_operations;
create policy services_games_operations_internal_only
on public.services_games_operations
as restrictive
for all
to anon, authenticated
using (false)
with check (false);

commit;
