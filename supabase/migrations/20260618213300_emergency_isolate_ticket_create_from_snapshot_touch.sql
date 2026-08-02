-- Emergency production isolation:
-- official ticket creation remains authoritative, while the legacy owner-snapshot
-- refresh is paused to keep JSONB/realtime maintenance out of the sale transaction.
alter table public.tickets
disable trigger lotterynet_ticket_owner_realtime_touch;

comment on function public.lotterynet_touch_ticket_owners_from_ticket()
is 'Legacy owner-snapshot touch. Its tickets trigger is temporarily disabled during the 2026-06-18 production recovery.';
