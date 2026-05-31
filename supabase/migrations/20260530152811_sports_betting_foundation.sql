-- Sportsbook foundation for LotteryNet.
-- This migration only creates isolated sports_* tables. It does not modify
-- lottery, recharge, ticket, result, finance, or user tables.

create table if not exists public.sports_feature_flags (
    scope text primary key,
    enabled boolean not null default false,
    allowed_roles text[] not null default array[]::text[],
    allowed_actor_keys text[] not null default array[]::text[],
    markets jsonb not null default '{}'::jsonb,
    limits jsonb not null default '{}'::jsonb,
    updated_by text,
    updated_at timestamptz not null default now()
);

create table if not exists public.sports_events (
    id uuid primary key default gen_random_uuid(),
    provider text not null default 'odds-api.net',
    provider_event_id text not null,
    sport_key text not null,
    sport_title text not null,
    league_key text,
    league_title text,
    home_team text not null,
    away_team text not null,
    commence_time timestamptz not null,
    status text not null default 'scheduled'
        check (status in ('scheduled', 'open', 'suspended', 'started', 'final', 'cancelled')),
    home_score numeric,
    away_score numeric,
    source_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, provider_event_id)
);

create table if not exists public.sports_markets (
    id uuid primary key default gen_random_uuid(),
    event_id uuid not null references public.sports_events(id) on delete cascade,
    market_key text not null
        check (market_key in ('moneyline', 'runline', 'spread', 'total', 'first_half', 'first_five')),
    market_title text not null,
    status text not null default 'open'
        check (status in ('open', 'suspended', 'closed', 'settled')),
    line numeric,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.sports_odds (
    id uuid primary key default gen_random_uuid(),
    market_id uuid not null references public.sports_markets(id) on delete cascade,
    provider text not null default 'odds-api.net',
    bookmaker_key text not null default 'consensus',
    selection_key text not null,
    selection_label text not null,
    decimal_odds numeric not null check (decimal_odds > 1),
    american_odds integer,
    point numeric,
    status text not null default 'open'
        check (status in ('open', 'suspended', 'closed')),
    last_updated timestamptz not null default now(),
    source_payload jsonb not null default '{}'::jsonb,
    unique (market_id, bookmaker_key, selection_key)
);

create table if not exists public.sports_odds_snapshots (
    id uuid primary key default gen_random_uuid(),
    odds_id uuid references public.sports_odds(id) on delete set null,
    event_id uuid references public.sports_events(id) on delete cascade,
    market_key text not null,
    selection_key text not null,
    decimal_odds numeric not null check (decimal_odds > 1),
    point numeric,
    captured_at timestamptz not null default now(),
    source_payload jsonb not null default '{}'::jsonb
);

create table if not exists public.sports_tickets (
    id uuid primary key default gen_random_uuid(),
    ticket_code text not null unique,
    client_request_id text not null unique,
    owner_key text not null,
    admin_key text,
    supervisor_key text,
    cashier_key text,
    seller_user_id text,
    seller_username text,
    banca_name text,
    ticket_type text not null default 'straight'
        check (ticket_type in ('straight', 'parlay')),
    stake numeric not null check (stake > 0),
    decimal_odds numeric not null check (decimal_odds > 1),
    potential_payout numeric not null check (potential_payout >= 0),
    status text not null default 'pending'
        check (status in ('pending', 'won', 'lost', 'push', 'void', 'paid')),
    sold_at timestamptz not null default now(),
    settled_at timestamptz,
    paid_at timestamptz,
    voided_at timestamptz,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.sports_ticket_legs (
    id uuid primary key default gen_random_uuid(),
    sports_ticket_id uuid not null references public.sports_tickets(id) on delete cascade,
    event_id uuid references public.sports_events(id) on delete set null,
    market_id uuid references public.sports_markets(id) on delete set null,
    odds_id uuid references public.sports_odds(id) on delete set null,
    sport_key text not null,
    league_title text,
    event_label text not null,
    market_key text not null,
    market_title text not null,
    selection_key text not null,
    selection_label text not null,
    point numeric,
    decimal_odds numeric not null check (decimal_odds > 1),
    odds_locked_at timestamptz not null default now(),
    commence_time timestamptz,
    status text not null default 'pending'
        check (status in ('pending', 'won', 'lost', 'push', 'void')),
    result_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table if not exists public.sports_settlements (
    id uuid primary key default gen_random_uuid(),
    sports_ticket_id uuid not null references public.sports_tickets(id) on delete cascade,
    settlement_type text not null
        check (settlement_type in ('auto', 'manual', 'reversal')),
    previous_status text,
    next_status text not null,
    payout_amount numeric not null default 0 check (payout_amount >= 0),
    reason text,
    actor_key text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table if not exists public.sports_limits (
    scope_key text primary key,
    max_ticket_stake numeric not null default 0 check (max_ticket_stake >= 0),
    max_selection_stake numeric not null default 0 check (max_selection_stake >= 0),
    max_event_exposure numeric not null default 0 check (max_event_exposure >= 0),
    max_potential_payout numeric not null default 0 check (max_potential_payout >= 0),
    enabled_markets text[] not null default array['moneyline', 'runline', 'spread', 'total', 'first_half', 'first_five']::text[],
    updated_by text,
    updated_at timestamptz not null default now()
);

create table if not exists public.sports_audit_log (
    id uuid primary key default gen_random_uuid(),
    actor_key text,
    action text not null,
    entity_table text,
    entity_id text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists sports_events_commence_time_idx on public.sports_events(commence_time);
create index if not exists sports_events_status_idx on public.sports_events(status);
create index if not exists sports_markets_event_id_idx on public.sports_markets(event_id);
create index if not exists sports_odds_market_id_idx on public.sports_odds(market_id);
create index if not exists sports_tickets_owner_sold_idx on public.sports_tickets(owner_key, sold_at desc);
create index if not exists sports_tickets_status_idx on public.sports_tickets(status);
create index if not exists sports_ticket_legs_ticket_idx on public.sports_ticket_legs(sports_ticket_id);

alter table public.sports_feature_flags enable row level security;
alter table public.sports_events enable row level security;
alter table public.sports_markets enable row level security;
alter table public.sports_odds enable row level security;
alter table public.sports_odds_snapshots enable row level security;
alter table public.sports_tickets enable row level security;
alter table public.sports_ticket_legs enable row level security;
alter table public.sports_settlements enable row level security;
alter table public.sports_limits enable row level security;
alter table public.sports_audit_log enable row level security;
