# LotteryNet Supabase Functions Multi-Banca

## Purpose
These functions are the secure write layer that can move admin writes away from noisy direct REST calls while keeping Render as the main results API.

## Functions added

### `admin-users-state`
- Table: `lotterynet_users_state`
- Use for:
  - create/update admin
  - create/update supervisor
  - create/update cashier
  - toggle active/block state

### `admin-master-state`
- Table: `lotterynet_master_state`
- Use for:
  - system mode
  - global business config
  - future multi-banca shared config keys

### `admin-cashier-limits`
- Table: `lotterynet_master_state`
- Key pattern:
  - `cashier_limits:<adminId>`
- Use for:
  - per-admin cashier limits
  - payout / exposure limits payloads

### `admin-manual-results-override`
- Table: `lotterynet_kv`
- Key pattern:
  - `manual_results_overrides_by_day:<date>`
- Use for:
  - create manual override
  - delete manual override

## Security model
- These functions do **not** trust public clients by default.
- They require:
  - `verify_jwt = false`
  - header: `x-lotterynet-admin-secret`
  - env var: `LOTTERYNET_ADMIN_SHARED_SECRET`
- They also check the declared admin role in body:
  - `admin`
  - `master`

## Why this split is correct
- Results scraping stays in Render.
- Narrow state writes move to Supabase Functions.
- Realtime then fans those writes out to clients without polling.

## What still stays out of Supabase Functions
- live scraping
- historical results API
- heavy pick reconciliation
- large snapshot assembly

## Suggested next cut
1. Deploy these functions.
2. Set `LOTTERYNET_ADMIN_SHARED_SECRET`.
3. Point admin writes to functions one module at a time:
   - cashier limits
   - master config
   - manual results override
   - users state
4. Keep Render results untouched.
