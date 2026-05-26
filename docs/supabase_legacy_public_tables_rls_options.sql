-- LotteryNet legacy public table RLS options.
--
-- Why Supabase Security Advisor still reports errors:
-- The production compatibility script protects the native tables:
--   lotterynet_kv, lotterynet_users_state, lotterynet_master_state,
--   lotterynet_results_by_day, lotterynet_tickets_by_owner,
--   lotterynet_recharges_by_owner.
-- The Advisor screenshot reports older public tables:
--   recargas, usuarios, premios, ventas, ticket_items.
--
-- Use OPTION A if the current Android app no longer reads/writes those
-- legacy tables directly. This is the safest production option.
--
-- Use OPTION B only as a temporary no-break bridge if an old web/client
-- still depends on those tables through the anon key. OPTION B clears the
-- Advisor RLS-disabled error but is not a real per-user security boundary.

begin;

-- ============================================================
-- OPTION A: RECOMMENDED LOCKDOWN FOR UNUSED LEGACY TABLES
-- ============================================================
-- Uncomment this block if legacy tables are old leftovers.
-- It enables RLS and removes anon/authenticated access.

/*
alter table if exists public.recargas enable row level security;
alter table if exists public.usuarios enable row level security;
alter table if exists public.premios enable row level security;
alter table if exists public.ventas enable row level security;
alter table if exists public.ticket_items enable row level security;

revoke all on table
  public.recargas,
  public.usuarios,
  public.premios,
  public.ventas,
  public.ticket_items
from anon, authenticated;

drop policy if exists "legacy compatibility recargas" on public.recargas;
drop policy if exists "legacy compatibility usuarios" on public.usuarios;
drop policy if exists "legacy compatibility premios" on public.premios;
drop policy if exists "legacy compatibility ventas" on public.ventas;
drop policy if exists "legacy compatibility ticket_items" on public.ticket_items;
*/

-- ============================================================
-- OPTION B: TEMPORARY COMPATIBILITY IF OLD CLIENT STILL USES THEM
-- ============================================================
-- Uncomment this block only if the app/old web still needs these tables.
-- This keeps reads/writes working with anon/authenticated.

/*
alter table if exists public.recargas enable row level security;
alter table if exists public.usuarios enable row level security;
alter table if exists public.premios enable row level security;
alter table if exists public.ventas enable row level security;
alter table if exists public.ticket_items enable row level security;

drop policy if exists "legacy compatibility recargas" on public.recargas;
drop policy if exists "legacy compatibility usuarios" on public.usuarios;
drop policy if exists "legacy compatibility premios" on public.premios;
drop policy if exists "legacy compatibility ventas" on public.ventas;
drop policy if exists "legacy compatibility ticket_items" on public.ticket_items;

create policy "legacy compatibility recargas"
on public.recargas
for all
to anon, authenticated
using (true)
with check (true);

create policy "legacy compatibility usuarios"
on public.usuarios
for all
to anon, authenticated
using (true)
with check (true);

create policy "legacy compatibility premios"
on public.premios
for all
to anon, authenticated
using (true)
with check (true);

create policy "legacy compatibility ventas"
on public.ventas
for all
to anon, authenticated
using (true)
with check (true);

create policy "legacy compatibility ticket_items"
on public.ticket_items
for all
to anon, authenticated
using (true)
with check (true);

grant select, insert, update, delete on
  public.recargas,
  public.usuarios,
  public.premios,
  public.ventas,
  public.ticket_items
to anon, authenticated;
*/

commit;
