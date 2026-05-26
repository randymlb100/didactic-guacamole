-- LotteryNet production Supabase compatibility setup.
--
-- Purpose:
-- 1. Create the native tables the Kotlin app already tries to use.
-- 2. Backfill data from the legacy lotterynet_kv table when present.
-- 3. Enable RLS without breaking the current Android app, which uses the
--    publishable/anon key directly and does not yet send Supabase Auth JWTs.
--
-- Important:
-- These policies are a "no-break production compatibility" step, not a full
-- security boundary. True per-admin/per-cashier isolation requires moving
-- writes behind a backend/Edge Function or adding Supabase Auth identities.

begin;

create or replace function public.lotterynet_try_jsonb(raw text)
returns jsonb
language plpgsql
immutable
as $$
begin
  if raw is null or btrim(raw) = '' then
    return 'null'::jsonb;
  end if;

  return raw::jsonb;
exception when others then
  return to_jsonb(raw);
end;
$$;

create table if not exists public.lotterynet_kv (
  key text primary key,
  value jsonb,
  upd timestamptz not null default now()
);

create table if not exists public.lotterynet_users_state (
  scope text primary key,
  payload jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.lotterynet_master_state (
  config_key text primary key,
  payload jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.lotterynet_results_by_day (
  result_date text primary key,
  payload jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.lotterynet_tickets_by_owner (
  owner_key text primary key,
  payload jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.lotterynet_recharges_by_owner (
  owner_key text primary key,
  payload jsonb not null,
  updated_at timestamptz not null default now()
);

insert into public.lotterynet_users_state(scope, payload, updated_at)
select
  'global',
  public.lotterynet_try_jsonb(value::text),
  coalesce(upd, now())
from public.lotterynet_kv
where key = 'sys_users_v4'
on conflict (scope) do update set
  payload = excluded.payload,
  updated_at = greatest(public.lotterynet_users_state.updated_at, excluded.updated_at);

insert into public.lotterynet_results_by_day(result_date, payload, updated_at)
select
  replace(key, 'lot_results_cache_by_day:', ''),
  public.lotterynet_try_jsonb(value::text),
  coalesce(upd, now())
from public.lotterynet_kv
where key like 'lot_results_cache_by_day:%'
on conflict (result_date) do update set
  payload = excluded.payload,
  updated_at = greatest(public.lotterynet_results_by_day.updated_at, excluded.updated_at);

insert into public.lotterynet_tickets_by_owner(owner_key, payload, updated_at)
select
  replace(key, 'bv_t3_', ''),
  public.lotterynet_try_jsonb(value::text),
  coalesce(upd, now())
from public.lotterynet_kv
where key like 'bv_t3_%'
on conflict (owner_key) do update set
  payload = excluded.payload,
  updated_at = greatest(public.lotterynet_tickets_by_owner.updated_at, excluded.updated_at);

insert into public.lotterynet_recharges_by_owner(owner_key, payload, updated_at)
select
  replace(key, 'bv_r3_', ''),
  public.lotterynet_try_jsonb(value::text),
  coalesce(upd, now())
from public.lotterynet_kv
where key like 'bv_r3_%'
on conflict (owner_key) do update set
  payload = excluded.payload,
  updated_at = greatest(public.lotterynet_recharges_by_owner.updated_at, excluded.updated_at);

insert into public.lotterynet_master_state(config_key, payload, updated_at)
select
  key,
  public.lotterynet_try_jsonb(value::text),
  coalesce(upd, now())
from public.lotterynet_kv
where key not like 'bv_t3_%'
  and key not like 'bv_r3_%'
  and key not like 'lot_results_cache_by_day:%'
  and key <> 'sys_users_v4'
on conflict (config_key) do update set
  payload = excluded.payload,
  updated_at = greatest(public.lotterynet_master_state.updated_at, excluded.updated_at);

create index if not exists idx_lotterynet_kv_upd
on public.lotterynet_kv(upd desc);

create index if not exists idx_lotterynet_users_state_updated_at
on public.lotterynet_users_state(updated_at desc);

create index if not exists idx_lotterynet_master_state_updated_at
on public.lotterynet_master_state(updated_at desc);

create index if not exists idx_lotterynet_results_by_day_updated_at
on public.lotterynet_results_by_day(updated_at desc);

create index if not exists idx_lotterynet_tickets_by_owner_updated_at
on public.lotterynet_tickets_by_owner(updated_at desc);

create index if not exists idx_lotterynet_recharges_by_owner_updated_at
on public.lotterynet_recharges_by_owner(updated_at desc);

alter table public.lotterynet_kv enable row level security;
alter table public.lotterynet_users_state enable row level security;
alter table public.lotterynet_master_state enable row level security;
alter table public.lotterynet_results_by_day enable row level security;
alter table public.lotterynet_tickets_by_owner enable row level security;
alter table public.lotterynet_recharges_by_owner enable row level security;

drop policy if exists "LotteryNet compatibility read kv" on public.lotterynet_kv;
drop policy if exists "LotteryNet compatibility write kv" on public.lotterynet_kv;
drop policy if exists "LotteryNet compatibility read users" on public.lotterynet_users_state;
drop policy if exists "LotteryNet compatibility write users" on public.lotterynet_users_state;
drop policy if exists "LotteryNet compatibility read master" on public.lotterynet_master_state;
drop policy if exists "LotteryNet compatibility write master" on public.lotterynet_master_state;
drop policy if exists "LotteryNet compatibility read results" on public.lotterynet_results_by_day;
drop policy if exists "LotteryNet compatibility write results" on public.lotterynet_results_by_day;
drop policy if exists "LotteryNet compatibility read tickets" on public.lotterynet_tickets_by_owner;
drop policy if exists "LotteryNet compatibility write tickets" on public.lotterynet_tickets_by_owner;
drop policy if exists "LotteryNet compatibility read recharges" on public.lotterynet_recharges_by_owner;
drop policy if exists "LotteryNet compatibility write recharges" on public.lotterynet_recharges_by_owner;

create policy "LotteryNet compatibility read kv"
on public.lotterynet_kv
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write kv"
on public.lotterynet_kv
for all
to anon, authenticated
using (true)
with check (true);

create policy "LotteryNet compatibility read users"
on public.lotterynet_users_state
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write users"
on public.lotterynet_users_state
for all
to anon, authenticated
using (true)
with check (true);

create policy "LotteryNet compatibility read master"
on public.lotterynet_master_state
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write master"
on public.lotterynet_master_state
for all
to anon, authenticated
using (true)
with check (true);

create policy "LotteryNet compatibility read results"
on public.lotterynet_results_by_day
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write results"
on public.lotterynet_results_by_day
for all
to anon, authenticated
using (true)
with check (true);

create policy "LotteryNet compatibility read tickets"
on public.lotterynet_tickets_by_owner
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write tickets"
on public.lotterynet_tickets_by_owner
for all
to anon, authenticated
using (true)
with check (true);

create policy "LotteryNet compatibility read recharges"
on public.lotterynet_recharges_by_owner
for select
to anon, authenticated
using (true);

create policy "LotteryNet compatibility write recharges"
on public.lotterynet_recharges_by_owner
for all
to anon, authenticated
using (true)
with check (true);

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on
  public.lotterynet_kv,
  public.lotterynet_users_state,
  public.lotterynet_master_state,
  public.lotterynet_results_by_day,
  public.lotterynet_tickets_by_owner,
  public.lotterynet_recharges_by_owner
to anon, authenticated;

commit;

