create table if not exists public.sports_team_assets (
    id uuid primary key default gen_random_uuid(),
    provider text not null default 'thesportsdb',
    sport_key text not null default '',
    league_title text not null default '',
    team_name text not null,
    team_name_normalized text not null,
    logo_url text,
    badge_url text,
    source_payload jsonb not null default '{}'::jsonb,
    last_checked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, sport_key, league_title, team_name_normalized)
);

create index if not exists sports_team_assets_lookup_idx
    on public.sports_team_assets (sport_key, league_title, team_name_normalized);

alter table public.sports_team_assets enable row level security;
