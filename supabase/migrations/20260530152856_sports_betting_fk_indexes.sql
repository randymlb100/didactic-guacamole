-- Cover sportsbook foreign keys flagged by the Supabase performance advisor.
-- This keeps deletes/joins predictable before the module receives traffic.

create index if not exists sports_odds_snapshots_odds_id_idx
    on public.sports_odds_snapshots(odds_id);

create index if not exists sports_odds_snapshots_event_id_idx
    on public.sports_odds_snapshots(event_id);

create index if not exists sports_settlements_ticket_id_idx
    on public.sports_settlements(sports_ticket_id);

create index if not exists sports_ticket_legs_event_id_idx
    on public.sports_ticket_legs(event_id);

create index if not exists sports_ticket_legs_market_id_idx
    on public.sports_ticket_legs(market_id);

create index if not exists sports_ticket_legs_odds_id_idx
    on public.sports_ticket_legs(odds_id);
