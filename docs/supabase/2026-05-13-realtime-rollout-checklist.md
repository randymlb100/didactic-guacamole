# Supabase Realtime Rollout Checklist

1. In Supabase, verify `public.lotterynet_users_state`, `public.lotterynet_master_state`, `public.lotterynet_tickets_by_owner`, and `public.lotterynet_kv` are in the `supabase_realtime` publication.
2. Keep RLS enabled on every exposed table. Realtime should not become a bypass for public reads.
3. Confirm the app only subscribes with narrow filters:
   - `scope=eq.global`
   - `config_key=eq.cashier_limits:<owner>`
   - `owner_key=eq.<owner>`
   - `key=eq.<results cache key>`
4. Verify one device updates users, cashier limits, tickets, and today's result cache, and a second device receives the change without manual polling.
5. Verify fallback behavior by temporarily disabling Realtime: login, sales, results, and manual refresh must keep working through the existing REST/edge paths.
