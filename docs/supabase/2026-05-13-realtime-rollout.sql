alter publication supabase_realtime add table public.lotterynet_users_state;
alter publication supabase_realtime add table public.lotterynet_master_state;
alter publication supabase_realtime add table public.lotterynet_tickets_by_owner;
alter publication supabase_realtime add table public.lotterynet_kv;

-- If recharge-history-state is later moved from edge-only payloads to a real table,
-- add that table here instead of subscribing to broad master-state changes.
