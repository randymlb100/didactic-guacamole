# Results Supabase Compose Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Results screen and result-cache pipeline faster and safer by reducing unnecessary network work, avoiding duplicate Supabase writes, and lowering Compose recomposition/render pressure.

**Architecture:** Keep GitHub as the scheduler/scraper runner, but make the scraper write only when payloads changed. Keep Android reading Supabase cache first, then Render only when needed. Optimize Compose by reducing state reads and expensive board rebuilding during normal viewing.

**Tech Stack:** Kotlin, Jetpack Compose, Supabase Postgres/PostgREST, Python scraper, GitHub Actions, Node.js smoke tests.

---

## Evidence Already Found

- GitHub is not the bottleneck: latest scraper workflow passed after smart backfill and took about 40 seconds.
- Android Results reads are acceptable: direct Supabase cache reads measured about 150-724ms from this machine; Render measured about 449-869ms.
- Supabase write path is the bottleneck: `pg_stat_statements` showed `lotterynet_results_by_day` PostgREST upserts averaging about 820ms with nearly 3s max, while cache reads average below 1ms inside Postgres.
- The app does not remotely fill all three days all the time. It warms local cache for today, yesterday, and anteayer, but remote refresh runs for the selected date.
- Current Android cache client reads `lotterynet_kv` keys `lot_results_cache_by_day:<date>` and `pick_results_cache_by_day:<date>`.

## Technical References Used

- Android Compose performance best practices: use `remember` for expensive calculations, use `derivedStateOf` to limit recomposition, defer state reads, and use lazy layout stable keys.
- Android Compose performance overview: configure Compose to avoid common performance pitfalls and reduce unnecessary recomposition.
- Supabase `pg_stat_statements`: use query statistics to identify high-call and high-time queries.
- Supabase query optimization/index docs: add indexes only where the planner and query shape justify them; indexes also add write overhead.
- Supabase performance tuning: use `pg_stat_activity`, Query Performance, and workload-specific tuning before scaling compute.

## Files To Review Or Modify

- `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save.py`
  - Add payload-change detection before Supabase upserts.
  - Avoid duplicate native-table writes when KV/native payload already matches.
  - Keep smart backfill behavior.

- `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save_test.py`
  - Add tests for skip-write behavior.
  - Add tests that yesterday/anteayer only scrape missing rows.

- `C:\Users\Randy Cordero\Desktop\didactic-guacamole\test_app.py`
  - Add endpoint tests to prove Render serves cached system results without triggering live scrape unless needed.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\results\SupabaseResultsRemoteStore.kt`
  - Prefer native results tables only if they are proven faster and complete, otherwise keep KV path.
  - Avoid Render fallback if Supabase cache already has expected coverage.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\results\ResultsScraperOrchestrator.kt`
  - Tighten freshness logic so past complete days do not keep hydrating unless missing or explicitly refreshed.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`
  - Reduce board rebuilding and auto-refresh triggers.
  - Use stable list keys where result rows render in lazy lists.
  - Use `derivedStateOf` around frequently changing UI decisions derived from timer/scroll/board state.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\test\java\com\lotterynet\pro\...`
  - Add focused unit tests for date hydration, auto refresh, and local-cache skip behavior.

---

## Phase 1: Baseline And Safety

### Task 1: Capture Current Baseline

**Files:**
- Read only: Android and scraper files listed above.
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\docs\superpowers\plans\artifacts\results-optimization-baseline.md`

- [ ] Record current Git commit hashes for both repos.
- [ ] Record current Supabase query stats for `lotterynet_kv`, `lotterynet_results_by_day`, and `lotterynet_pick_results_by_day`.
- [ ] Run Node.js read timing against today, yesterday, and anteayer:

```powershell
node scripts/results-read-smoke.mjs
```

Expected:
- Supabase cache endpoints return HTTP 200.
- Render `/system-results` returns HTTP 200.
- No forced live scrape for complete cached dates.

- [ ] Run current scraper tests:

```powershell
python -m unittest scraper.scrape_and_save_test
python -m unittest test_app
```

Expected:
- Existing tests pass before any optimization.

### Task 2: Add A Repeatable Node Smoke Test

**Files:**
- Create: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scripts\results-read-smoke.mjs`

- [ ] Add a Node script that checks:
  - Supabase KV lot cache for today/yesterday/anteayer.
  - Supabase KV pick cache for today/yesterday/anteayer.
  - Render `/system-results?mode=both` for the same dates.
  - Response status, payload size, and elapsed time.

- [ ] Expected output format:

```text
supabase kv lot 24-05-2026: 200 180ms 4035 bytes
supabase kv pick 24-05-2026: 200 190ms 57416 bytes
render system 24-05-2026: 200 650ms 56311 bytes
```

- [ ] This script must not write to Supabase.

---

## Phase 2: Supabase Write Optimization

### Task 3: Skip KV Writes When Payload Did Not Change

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save_test.py`

- [ ] Add a test where existing KV results equal the newly merged results.
- [ ] Expected: scraper logs skip and does not call Supabase POST/PATCH for that cache key.
- [ ] Add a test where one result changed.
- [ ] Expected: scraper writes once.

Implementation direction:
- Normalize rows before comparison with existing normalizers.
- Compare semantic payload, not raw JSON string formatting.
- Keep `upd` unchanged if nothing changed.

### Task 4: Skip Native Table Writes When Payload Did Not Change

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save_test.py`

- [ ] Add helper to fetch existing `lotterynet_results_by_day` row for a date.
- [ ] Add helper to fetch existing `lotterynet_pick_results_by_day` row for a date.
- [ ] Add tests proving native upsert is skipped when payload is equal.
- [ ] Add tests proving native upsert happens when payload changed.

Reason:
- Supabase stats showed native result upserts are the heaviest write path.
- Tables are small; this is not an index-first problem.

### Task 5: Reduce Backfill Writes To Missing/Changed Dates Only

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save_test.py`

- [ ] Keep current smart backfill.
- [ ] Add regression test:
  - Today has partial rows: scrape and save today.
  - Yesterday complete: skip scrape and skip write.
  - Anteayer complete: skip scrape and skip write.
- [ ] Add regression test:
  - Yesterday missing NJ day Pick 3/Pick 4 only.
  - Scraper requests only the missing Pick rows and writes only if changed.

---

## Phase 3: Android Results Network Optimization

### Task 6: Make Past Complete Dates Prefer Local Cache

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`
- Test: Android local unit test file for Results date hydration helpers.

- [ ] Change policy so:
  - Today can hydrate if incomplete or waiting.
  - Yesterday/anteayer hydrate only if local results are incomplete, missing expected IDs, user manually refreshes, or realtime says cache changed.
  - Older complete dates stay local.
- [ ] Add tests for `shouldSkipInitialResultsHydration`.
- [ ] Expected:
  - Complete yesterday returns skip=true.
  - Incomplete yesterday returns skip=false.
  - Today incomplete returns skip=false.

### Task 7: Avoid Render Fallback When Supabase Cache Has Coverage

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\results\SupabaseResultsRemoteStore.kt`
- Test: Android unit test for `SupabaseResultsRemoteStore`.

- [ ] Add test where Supabase cached payload has all expected IDs.
- [ ] Expected: Render fetcher is not called.
- [ ] Add test where Supabase cache is missing NJ Pick result.
- [ ] Expected: Render fetcher is called for selected date only.

### Task 8: Keep Manual Refresh Strong

**Files:**
- Modify only if needed: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`

- [ ] Manual refresh should still force remote for the selected date.
- [ ] Manual refresh should not silently update other dates.
- [ ] Auto refresh should still run for today when a draw is waiting or marked no-draw but recoverable.

---

## Phase 4: Compose UI Smoothness

### Task 9: Reduce Board Rebuild Frequency

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`

- [ ] Identify where `tickUtcMs` causes `buildResultsBoardRows` to rebuild.
- [ ] Keep timer-based rebuild only for today and only when waiting/no-draw rows exist.
- [ ] For past dates, use a stable board clock so scrolling does not fight recomposition.

Acceptance:
- Past results screen should not rebuild every 30/60 seconds.
- Today should still update state labels around draw times.

### Task 10: Add Stable Lazy Keys To Result Rows

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`

- [ ] Find LazyColumn/LazyVerticalGrid rendering result rows.
- [ ] Use stable key from result/lottery identity:

```kotlin
key = { row -> "${row.lottery.id}:${row.result?.date.orEmpty()}:${row.result?.id.orEmpty()}" }
```

- [ ] Verify UI order remains the same.

Reason:
- Android docs recommend stable keys so Compose can avoid unnecessary recomposition when list data changes.

### Task 11: Use derivedStateOf For Frequently Changing UI Decisions

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt`

- [ ] Wrap derived values affected by scroll/timer/board state in `remember { derivedStateOf { ... } }` where appropriate.
- [ ] Do not wrap plain cheap immutable values.
- [ ] Keep logic readable and local.

Acceptance:
- No behavior change.
- Less recomposition during scroll and clock ticks.

---

## Phase 5: Validation Before Production

### Task 12: Supabase Validation

**Files:**
- No app code changes.

- [ ] Run `pg_stat_statements` again for result tables.
- [ ] Confirm:
  - Read queries remain fast.
  - Native upsert call count drops.
  - KV upsert call count drops when payload unchanged.

### Task 13: Node End-To-End Simulation

**Files:**
- Use: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\scripts\results-read-smoke.mjs`

- [ ] Simulate today/yesterday/anteayer reads.
- [ ] Trigger one scraper run.
- [ ] Run smoke again.
- [ ] Expected:
  - Existing complete dates are not rewritten.
  - Missing today data is updated.
  - Render and Supabase return HTTP 200.

### Task 14: Android Build/Unit Test Only After Permission

**Files:**
- Android project.

- [ ] Do not compile release until Randy says yes.
- [ ] When allowed:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

Expected:
- Unit tests pass.
- Release APK builds.

---

## Rollback Plan

- Every phase must be committed separately.
- If Node smoke or Supabase stats get worse, revert only the last phase.
- Do not remove `pg_net`.
- Do not drop unused indexes in this phase.
- Do not change production RLS policies unless a separate security plan is approved.

## Recommended Order Tonight

1. Baseline and Node smoke test.
2. Supabase skip-write optimization.
3. Android remote hydration tightening.
4. Compose recomposition cleanup.
5. Supabase stats comparison.

## Decision Needed Before Execution

- Execute tonight in phases with tests after each phase.
- Do not run release build until explicit approval.
- Do not alter `pg_net`, unused indexes, or auth settings in this plan.
