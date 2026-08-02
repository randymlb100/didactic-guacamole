-- Drop legacy duplicate trigger. The active trigger
-- ln_protect_ticket_owner_snapshot_trigger already runs the same function
-- on the same payload changes, so keeping both doubles JSON snapshot work.
drop trigger if exists trg_ln_protect_ticket_owner_snapshot
on public.lotterynet_tickets_by_owner;
