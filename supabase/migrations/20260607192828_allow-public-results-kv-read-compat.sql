begin;

-- Compatibility for old web clients that still read public result caches through
-- PostgREST. This does not reopen ticket/config/user state rows from lotterynet_kv.
grant select (key, value, upd) on table public.lotterynet_kv to anon, authenticated;

drop policy if exists lotterynet_kv_public_results_cache_read on public.lotterynet_kv;
create policy lotterynet_kv_public_results_cache_read
on public.lotterynet_kv
for select
to anon, authenticated
using (
  key like 'lot_results_cache_by_day:%'
  or key like 'pick_results_cache_by_day:%'
);

comment on policy lotterynet_kv_public_results_cache_read on public.lotterynet_kv
is 'Allows legacy web clients to read only public result-cache rows; all sensitive kv rows remain blocked by RLS.';

-- PostgREST profile lookup uses direct equality on these legacy alias columns.
-- The existing indexes cover lower(...) expressions, not the direct equality path.
create index if not exists profiles_legacy_key_plain_idx
on public.profiles (legacy_key)
where legacy_key is not null;

create index if not exists profiles_legacy_admin_user_plain_idx
on public.profiles (legacy_admin_user)
where legacy_admin_user is not null;

-- Ticket list/delta calls always ignore deleted rows. Partial active indexes keep
-- the scan smaller for admin/cashier filters without changing ticket behavior.
create index if not exists tickets_admin_active_server_created_idx
on public.tickets (admin_key, server_created_at desc)
where deleted_at is null and admin_key is not null;

create index if not exists tickets_cashier_active_server_created_idx
on public.tickets (cashier_key, server_created_at desc)
where deleted_at is null and cashier_key is not null;

create index if not exists tickets_admin_active_updated_idx
on public.tickets (admin_key, updated_at desc)
where deleted_at is null and admin_key is not null;

create index if not exists tickets_cashier_active_updated_idx
on public.tickets (cashier_key, updated_at desc)
where deleted_at is null and cashier_key is not null;

create index if not exists tickets_client_request_active_created_idx
on public.tickets (client_request_id, server_created_at desc)
where deleted_at is null and client_request_id is not null;

create index if not exists tickets_ticket_code_active_created_idx
on public.tickets (ticket_code, server_created_at desc)
where deleted_at is null and ticket_code is not null;

-- Results screens filter by day + game without always constraining lottery id.
create index if not exists result_draws_day_game_idx
on public.result_draws (result_day_key, game, updated_at desc);

commit;
