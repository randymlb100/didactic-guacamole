# Safe Results Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move results reads/reconciliation from legacy KV-first flow to `result_draws`-first flow without breaking current app refresh, realtime, tickets, or prize payout.

**Architecture:** Keep existing `lotterynet_kv` and `lotterynet_results_by_day` as compatibility writers/signals during rollout. Add a new read Edge Function backed by `lotterynet_result_draws_payload`, switch Android behind a safe fallback, then remove dead fallbacks only after production smoke tests pass.

**Tech Stack:** Supabase Postgres migrations, Supabase Edge Functions, Android Kotlin, existing Node QA scripts.

---

## Documentation Used

- Supabase database migrations: https://supabase.com/docs/guides/deployment/database-migrations
- Supabase local development/schema migrations: https://supabase.com/docs/guides/local-development/overview
- Supabase branching/staging: https://supabase.com/docs/guides/deployment/branching
- Supabase deployment environments: https://supabase.com/docs/guides/deployment
- Supabase Edge Function deploy: https://supabase.com/docs/guides/functions/deploy
- Supabase Realtime Postgres changes: https://supabase.com/docs/guides/realtime/postgres-changes

## Current Risk Map

- Android reads result cache from `lotterynet_kv`: `app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt`.
- `get-results-status` checks `lotterynet_kv`: `supabase/functions/get-results-status/index.ts`.
- New normalized table exists: `public.result_draws`.
- New prize path exists: `lotterynet_calculate_ticket_prize_v2`.
- Safety fallback still returns old prize calculation if normalized payload is empty.
- Android calls missing/dead `fetch-results` Edge Function as a swallowed fallback.

## Migration Rules

- Do not delete `lotterynet_kv` or `lotterynet_results_by_day` in the first release.
- Do not rename current Edge Functions used by Android.
- New flow must be additive first: new function, new tests, then Android switch.
- Production rollout must keep old cache working until the next app release is installed.
- Realtime must continue using a public signal row/table if direct normalized table subscription is not enough.

---

### Task 1: Add Server Read Contract For Normalized Results

**Files:**
- Create: `supabase/functions/get-results-v2/index.ts`
- Test: `tools/qa/results-v2-contract-smoke.mjs`

- [ ] **Step 1: Create Edge Function that reads normalized payload**

Implement `get-results-v2` so it accepts `{ "date": "29-05-2026" }`, calls RPC `lotterynet_result_draws_payload(date)`, and returns:

```json
{
  "ok": true,
  "source": "result_draws",
  "date": "29-05-2026",
  "results": []
}
```

If `result_draws` returns empty, it must return `ok: true` with empty results, not fall back silently.

- [ ] **Step 2: Add smoke test**

Create `tools/qa/results-v2-contract-smoke.mjs` to call:

```text
POST /functions/v1/get-results-v2
```

Expected checks:
- status is 200
- `ok === true`
- `source === "result_draws"`
- `results` is an array

- [ ] **Step 3: Deploy only the new function**

Run:

```powershell
npx supabase functions deploy get-results-v2 --project-ref unhoulkujbtsypccpirc
```

Expected: function deploy succeeds; existing functions are untouched.

---

### Task 2: Switch Android To V2 With Legacy Fallback

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/results/ResultsSupabaseStoreTest.kt`

- [ ] **Step 1: Add failing unit test**

Add a test that verifies Android prefers `get-results-v2` payload when it contains rows.

Expected behavior:
- V2 rows win.
- Old `lotterynet_kv` cache only runs if V2 is empty/unavailable.

- [ ] **Step 2: Implement V2 fetcher**

Add an Edge call to `get-results-v2` before direct `lotterynet_kv` cache. Keep current cache/render fallback.

Priority order during rollout:

```text
1. get-results-v2 / result_draws
2. Render live / system-results
3. lotterynet_kv compatibility cache
4. empty list
```

- [ ] **Step 3: Remove dead `fetch-results` fallback**

Delete the call to missing function `fetch-results`. Replace it with `get-results-v2`.

- [ ] **Step 4: Run targeted test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lotterynet.pro.core.results.ResultsSupabaseStoreTest --no-daemon
```

Expected: tests pass.

---

### Task 3: Update Status Endpoint Without Breaking App Refresh

**Files:**
- Modify: `supabase/functions/get-results-status/index.ts`
- Test: `tools/qa/results-stack-diagnostic.mjs`

- [ ] **Step 1: Make status compare both systems**

Return both:

```json
{
  "counts": {
    "resultDraws": 0,
    "lotteries": 0,
    "picks": 0
  },
  "source": {
    "primary": "result_draws",
    "compat": "lotterynet_kv"
  }
}
```

- [ ] **Step 2: Keep old fields**

Do not remove `counts.lotteries`, `counts.picks`, or `updatedAt` fields. Old app versions may still read them.

- [ ] **Step 3: Deploy**

```powershell
npx supabase functions deploy get-results-status --project-ref unhoulkujbtsypccpirc
```

---

### Task 4: Make Prize Reconcile Strictly V2 After Coverage Is Proven

**Files:**
- Create migration: `supabase/migrations/YYYYMMDDHHMMSS_results_v2_strict_prize_guard.sql`
- Test: `tools/qa/result-prize-v2-smoke.sql`

- [ ] **Step 1: Add SQL guard test**

Before changing fallback, run:

```sql
select jsonb_array_length(public.lotterynet_result_draws_payload('29-05-2026')) as result_draws_count;
```

Expected: count matches or exceeds current KV visible results for the day.

- [ ] **Step 2: Change fallback only after coverage**

Modify `lotterynet_resolve_ticket_prize_v2` so old `lotterynet_resolve_ticket_prize(ticket)` is used only when `result_draws` has no rows for that date, and log/return source clearly.

- [ ] **Step 3: Run SQL smoke**

Run:

```powershell
npx supabase db execute --file tools/qa/result-prize-v2-smoke.sql --project-ref unhoulkujbtsypccpirc
```

Expected: old prize and V2 prize match for sampled winners.

---

### Task 5: Realtime Safety

**Files:**
- Modify migration only if needed after testing.

- [ ] **Step 1: Verify publication**

Check whether `result_draws` or the signal row is in `supabase_realtime`.

Expected safe rollout:
- App can keep listening to current `lotterynet_kv` signal during first release.
- Server updates `result_draws` and touches signal row when results change.

- [ ] **Step 2: Do not move Android Realtime listener first**

Realtime listener migration should happen after read path is stable. First release should read V2 on refresh but still wake from current signal.

---

### Task 6: Production Rollout Checklist

- [ ] Deploy new `get-results-v2`.
- [ ] Run Node smoke for today and yesterday.
- [ ] Deploy Android update to one test device.
- [ ] Verify refresh results with no cache.
- [ ] Verify winning ticket calculation for normal and Pick.
- [ ] Verify old installed app still sees results through `lotterynet_kv`.
- [ ] Only after all pass, deploy broader Android release.
- [ ] Leave old tables for at least one full business day after release.

---

## Rollback

- If Android V2 reads fail, keep server deployed and switch app fallback back to `lotterynet_kv`.
- If `get-results-v2` returns wrong rows, do not delete it; fix server mapping and redeploy only that Edge Function.
- If prize mismatch appears, restore old fallback inside `lotterynet_resolve_ticket_prize_v2` and keep `result_draws` as read-only diagnostic until fixed.

