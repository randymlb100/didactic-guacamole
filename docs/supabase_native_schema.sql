-- LotteryNet native runtime tables.
-- Run this in Supabase SQL editor before disabling the legacy lotterynet_kv fallback.

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

alter table public.lotterynet_users_state enable row level security;
alter table public.lotterynet_master_state enable row level security;
alter table public.lotterynet_results_by_day enable row level security;

drop policy if exists "LotteryNet service access users" on public.lotterynet_users_state;
drop policy if exists "LotteryNet service access master" on public.lotterynet_master_state;
drop policy if exists "LotteryNet service access results" on public.lotterynet_results_by_day;

create policy "LotteryNet service access users"
on public.lotterynet_users_state
for all
using (true)
with check (true);

create policy "LotteryNet service access master"
on public.lotterynet_master_state
for all
using (true)
with check (true);

create policy "LotteryNet service access results"
on public.lotterynet_results_by_day
for all
using (true)
with check (true);

create index if not exists idx_lotterynet_users_state_updated_at
on public.lotterynet_users_state(updated_at desc);

create index if not exists idx_lotterynet_master_state_updated_at
on public.lotterynet_master_state(updated_at desc);

create index if not exists idx_lotterynet_results_by_day_updated_at
on public.lotterynet_results_by_day(updated_at desc);
