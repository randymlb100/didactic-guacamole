# LotteryNet Realtime Cutover

## Goal
Enable Realtime for the narrow tables and keys that reduce polling without replacing Render as the results API.

## Execute in order
1. Open Supabase SQL Editor.
2. Run [2026-05-14-supabase-cutover.sql](/C:/Users/Randy%20Cordero/Desktop/lotterynet_android/docs/supabase/2026-05-14-supabase-cutover.sql).
3. Confirm the query returns these four tables in `supabase_realtime`:
   - `lotterynet_users_state`
   - `lotterynet_master_state`
   - `lotterynet_tickets_by_owner`
   - `lotterynet_kv`
4. Confirm the webhook trigger `lotterynet_render_cache_invalidate_results` exists on `public.lotterynet_kv`.
5. Deploy the Edge Functions in `supabase/functions` once the database is responsive again.
6. Set the shared secret env var in both places:
   - Supabase Edge Functions: `LOTTERYNET_ADMIN_SHARED_SECRET`
   - Render: `LOTTERYNET_ADMIN_SHARED_SECRET`
7. Confirm Render has the internal invalidation route enabled:
   - `POST /internal/supabase-cache-invalidate`
8. Validate app subscriptions:
   - users: `scope=eq.global`
   - master: `config_key=eq.cashier_limits:<owner>`
   - tickets: `owner_key=eq.<owner>`
   - results cache:
     - `key=eq.lot_results_cache_by_day:<date>`
     - `key=eq.pick_results_cache_by_day:<date>`
     - `key=eq.manual_results_overrides_by_day:<date>`
9. Validate one webhook round-trip:
   - update one results cache key in `lotterynet_kv`
   - confirm Render drops that date from cache
   - confirm the app refreshes from snapshot without short polling loops

## What Realtime is for here
- users state changes
- master config and cashier limits
- owner ticket updates
- “today results cache changed” signals

## What Realtime is not for here
- scraping live results
- replacing Render `/system-results`
- full historical results transport

## Smoke test
1. Update a user in `lotterynet_users_state`.
2. Update a cashier limit key.
3. Insert or upsert one owner ticket payload.
4. Update one results cache key for today.
5. Confirm Render invalidates stale in-memory cache for that date.
6. Confirm the app reacts without polling loops.

## Rollback
If Realtime causes too much traffic, remove only the extra tables from `supabase_realtime`. The app keeps fallback REST behavior.
