# LotteryNet Results Operations Runbook

## Normal Flow

GitHub Actions runs the Python scraper every 10 minutes. The scraper writes lottery and Pick results into Supabase cache keys. Render serves `/system-results` from that cache. Android POS devices read local data first, then Supabase/Render when they need refresh.

```text
GitHub scheduled scraper
  -> Python scraper
  -> Supabase results cache
  -> Render /system-results
  -> Android POS local cache
```

## What Must Stay True

- Android POS devices should not depend on inline scraping during normal sales.
- Render `/system-results` should return `source: "supabase-cache"` or `source: "cache-miss"` for normal traffic.
- `source: "live-scraper"` is an emergency/admin condition, not the normal path.
- Manual scraper endpoints must require `RESULTS_ADMIN_SECRET` when that environment variable is configured.

## Daily Check

Run from `C:\Users\Randy Cordero\Desktop\lotterynet_android`:

```powershell
npm run results:monitor
```

Healthy output has:

- `ok: true`
- `lotteryCount` greater than zero after RD results start publishing
- `pickCount` greater than zero after Pick results start publishing
- no `api-used-live-scraper` problem

## If Results Are Late

1. Check the latest GitHub Actions scraper run.
2. Check Render `/system-results?mode=both`.
3. Check Supabase cache keys:
   - `lot_results_cache_by_day:<date>`
   - `pick_results_cache_by_day:<date>`
4. Use manual refresh only with the admin secret.
5. Re-run the Node monitor and confirm `source` is not `live-scraper`.

## Manual Refresh Rule

Manual refresh is allowed when a lottery source published a result and the scheduled scraper did not capture it. Manual refresh is not a replacement for the scheduled scraper.

Required Render environment variables:

```text
ALLOW_INLINE_LIVE_SCRAPE=0
RESULTS_ADMIN_SECRET=<strong secret stored in Render>
```
