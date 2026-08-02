-- Close direct public reads on the shared KV table.
--
-- Current Android flows read master/user/result/ticket state through Edge
-- Functions. Public Data API reads of this mixed-purpose table are no longer
-- needed and can expose operational metadata even when secrets are filtered.

revoke select on table public.lotterynet_kv from anon;
revoke select on table public.lotterynet_kv from authenticated;

drop policy if exists "LotteryNet compatibility read non-sensitive kv" on public.lotterynet_kv;

-- Rollback, if a legacy APK is proven to require direct KV reads:
--
-- grant select on table public.lotterynet_kv to anon, authenticated;
--
-- create policy "LotteryNet compatibility read non-sensitive kv"
-- on public.lotterynet_kv
-- for select
-- to anon, authenticated
-- using (
--   key <> 'lotterynet_results_cron_secret'
--   and key <> 'sys_rldly_client_secret'
--   and key not like 'sys_results_refresh_lock%'
-- );
