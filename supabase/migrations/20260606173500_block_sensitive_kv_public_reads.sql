-- Keep legacy KV compatibility, but block direct public reads of sensitive keys.
--
-- This is intentionally narrower than a full KV lockdown. Full KV closure needs
-- prefix-by-prefix replacement paths. These keys are not app-readable data.

drop policy if exists "LotteryNet compatibility read kv"
on public.lotterynet_kv;

create policy "LotteryNet compatibility read non-sensitive kv"
on public.lotterynet_kv
for select
to anon, authenticated
using (
  key <> 'lotterynet_results_cron_secret'
  and key <> 'sys_rldly_client_secret'
  and key not like 'sys_results_refresh_lock%'
);

-- Rollback, if a legacy emergency requires the old broad public read:
--
-- drop policy if exists "LotteryNet compatibility read non-sensitive kv"
-- on public.lotterynet_kv;
--
-- create policy "LotteryNet compatibility read kv"
-- on public.lotterynet_kv
-- for select
-- to anon, authenticated
-- using (true);
