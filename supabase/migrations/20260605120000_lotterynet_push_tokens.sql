create table if not exists public.lotterynet_push_tokens (
  token_hash text primary key,
  token text not null,
  auth_user_id uuid not null,
  role text not null,
  owner_key_hash text not null,
  platform text not null default 'android',
  enabled boolean not null default true,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

alter table public.lotterynet_push_tokens enable row level security;

drop policy if exists "lotterynet_push_tokens_service_only" on public.lotterynet_push_tokens;
create policy "lotterynet_push_tokens_service_only"
on public.lotterynet_push_tokens
for all
using (false)
with check (false);

create index if not exists lotterynet_push_tokens_owner_seen_idx
  on public.lotterynet_push_tokens (owner_key_hash, enabled, last_seen_at desc);
