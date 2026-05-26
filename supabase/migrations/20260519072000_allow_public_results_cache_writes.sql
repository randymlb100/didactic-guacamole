-- Allow the public scraper key to refresh public lottery results only.
-- Keep users, tickets, payments, recargas, audit, and arbitrary KV keys closed.

drop policy if exists "kv_results_cache_insert_public" on public.lotterynet_kv;
drop policy if exists "kv_results_cache_update_public" on public.lotterynet_kv;
drop policy if exists "results_by_day_insert_public" on public.lotterynet_results_by_day;
drop policy if exists "results_by_day_update_public" on public.lotterynet_results_by_day;

create policy "kv_results_cache_insert_public"
on public.lotterynet_kv
for insert
to anon, authenticated
with check (
  key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

create policy "kv_results_cache_update_public"
on public.lotterynet_kv
for update
to anon, authenticated
using (
  key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
with check (
  key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

create policy "results_by_day_insert_public"
on public.lotterynet_results_by_day
for insert
to anon, authenticated
with check (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

create policy "results_by_day_update_public"
on public.lotterynet_results_by_day
for update
to anon, authenticated
using (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
with check (
  result_date ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);
