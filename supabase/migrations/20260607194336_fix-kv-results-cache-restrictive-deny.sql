begin;

-- These restrictive deny-all policies were useful while closing the public KV
-- table, but they also override the narrow public result-cache read policy.
-- Removing them keeps writes and sensitive reads blocked because no permissive
-- policies exist for those paths.
drop policy if exists lotterynet_kv_internal_deny_anon on public.lotterynet_kv;
drop policy if exists lotterynet_kv_internal_deny_authenticated on public.lotterynet_kv;

commit;
