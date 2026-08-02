create table if not exists public.services_games_operations (
  id uuid primary key default gen_random_uuid(),
  client_request_id text not null unique,
  module text not null check (module in ('services', 'video_games')),
  provider_id text not null,
  product_id text not null,
  actor_user_id uuid,
  admin_key text,
  cashier_key text,
  amount numeric(14,2) not null default 0,
  provider_cost numeric(14,2) not null default 0,
  commission numeric(14,2) not null default 0,
  status text not null default 'submitted',
  provider_reference text,
  provider_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists services_games_operations_scope_idx
  on public.services_games_operations (admin_key, cashier_key, created_at desc);

create index if not exists services_games_operations_module_idx
  on public.services_games_operations (module, created_at desc);

alter table public.services_games_operations enable row level security;
revoke all on table public.services_games_operations from anon, authenticated;
grant all on table public.services_games_operations to service_role;

comment on table public.services_games_operations is
  'Ledger separado para Servicios y Videojuegos; nunca se mezcla con lotería, Pick, Recargas o Deportes.';
