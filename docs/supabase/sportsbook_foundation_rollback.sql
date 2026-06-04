-- Rollback for 20260530145410_sports_betting_foundation.sql.
-- Use only if the sports betting foundation migration was applied and must be removed.
-- This drops only sports_* tables and does not touch lottery, recharge, ticket,
-- result, finance, or user tables.

drop table if exists public.sports_audit_log cascade;
drop table if exists public.sports_limits cascade;
drop table if exists public.sports_settlements cascade;
drop table if exists public.sports_ticket_legs cascade;
drop table if exists public.sports_tickets cascade;
drop table if exists public.sports_odds_snapshots cascade;
drop table if exists public.sports_odds cascade;
drop table if exists public.sports_markets cascade;
drop table if exists public.sports_events cascade;
drop table if exists public.sports_feature_flags cascade;
