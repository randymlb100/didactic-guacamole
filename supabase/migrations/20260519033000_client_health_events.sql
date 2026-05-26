create table if not exists public.client_health_events (
  id uuid primary key default gen_random_uuid(),
  received_at timestamptz not null default now(),
  app_version text,
  device_id text,
  owner_key text,
  event_type text not null,
  payload jsonb not null default '{}'::jsonb
);

create index if not exists client_health_events_received_at_idx
  on public.client_health_events (received_at desc);

create index if not exists client_health_events_owner_received_idx
  on public.client_health_events (owner_key, received_at desc)
  where owner_key is not null;

alter table public.client_health_events enable row level security;

drop policy if exists client_health_events_no_direct_client_access on public.client_health_events;

create policy client_health_events_no_direct_client_access
  on public.client_health_events
  for all
  using (false)
  with check (false);
