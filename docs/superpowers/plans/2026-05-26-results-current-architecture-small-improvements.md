# Results Current Architecture Small Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the current LotteryNet results system without a full Node.js rewrite, keeping Python scraping in place and adding safer cache-first behavior, protected manual execution, Node.js monitoring, and operational documentation.

**Architecture:** Keep the production flow as scheduled scraper -> Supabase cache -> Render API -> Android POS. Render must respond fast from cache for normal POS traffic, while Node.js tools monitor correctness and freshness from the outside. Manual live scraping remains available only as an authenticated emergency/admin path.

**Tech Stack:** Python 3.11, Flask, Gunicorn, GitHub Actions, Render Web Service, Supabase/Postgres/KV, Android Kotlin, Node.js ESM monitoring scripts.

---

## Documented Basis

- Render Web Services are the right shape for the public API: https://render.com/docs/web-services
- Render Cron Jobs and Background Workers are the right shape for scheduled/background jobs when we decide to move scheduling from GitHub to Render: https://render.com/docs/cronjobs and https://render.com/docs/background-workers
- GitHub scheduled workflows can run on a cron schedule, but GitHub documents that schedule events can be delayed during high load: https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/events-that-trigger-workflows
- Supabase API security should be controlled with grants, roles, and RLS policies around exposed data: https://supabase.com/docs/guides/api/securing-your-api and https://supabase.com/docs/guides/database/postgres/row-level-security

## Current Flow

```text
GitHub Actions every 10 minutes
        |
        v
Python scraper in C:\Users\Randy Cordero\Desktop\didactic-guacamole\scraper\scrape_and_save.py
        |
        v
Supabase cache keys:
  lot_results_cache_by_day:<date>
  pick_results_cache_by_day:<date>
        |
        v
Render Flask API in C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py
        |
        v
Android local-first results flow in C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\results
```

## File Structure

- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py`
  - Add one policy gate for inline live scraping.
  - Add one policy gate for manual scraper endpoints.
  - Keep `/system-results` cache-first for normal POS traffic.
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\test_app.py`
  - Add contract tests proving normal requests do not scrape inline.
  - Add contract tests proving manual scraper routes require a secret.
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\.github\workflows\scrape.yml`
  - Keep the existing 10-minute schedule.
  - Add a lightweight post-scrape health check step.
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\results-monitor.mjs`
  - Node monitor that checks Render and Supabase cache health without replacing the Python scraper.
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\docs\results-operations-runbook.md`
  - Human runbook: what runs where, how to diagnose late/missing results, and when to use manual refresh.
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\package.json`
  - Add `results:monitor` script.

---

### Task 1: Render Cache-First Policy Gate

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\test_app.py`

- [ ] **Step 1: Add failing tests for cache-first behavior**

Append these tests inside `RenderApiContractsTest` in `C:\Users\Randy Cordero\Desktop\didactic-guacamole\test_app.py`:

```python
    def test_system_results_live_request_uses_cache_when_inline_scrape_disabled(self):
        with patch.dict(app.os.environ, {"ALLOW_INLINE_LIVE_SCRAPE": "0"}), \
            patch("app.get_dr_date_str", return_value="26-05-2026"), \
            patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock, \
            patch("app.scrape_us_picks") as pick_scrape_mock:
            response = self.client.get("/system-results?date=26-05-2026&mode=both&live=1")

        scrape_mock.assert_not_called()
        pick_scrape_mock.assert_not_called()
        self.assertEqual(200, response.status_code)
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("supabase-cache", payload["source"])
        self.assertEqual(25, payload["lotteries"]["count"])

    def test_system_results_allows_inline_scrape_only_when_enabled(self):
        with patch.dict(app.os.environ, {"ALLOW_INLINE_LIVE_SCRAPE": "1"}), \
            patch("app.get_dr_date_str", return_value="26-05-2026"), \
            patch("app.scrape", return_value=fake_results()) as scrape_mock, \
            patch("app.scrape_us_picks", return_value=[]) as pick_scrape_mock:
            response = self.client.get("/system-results?date=26-05-2026&mode=both&live=1")

        scrape_mock.assert_called()
        pick_scrape_mock.assert_called()
        self.assertEqual(200, response.status_code)
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("live-scraper", payload["source"])
```

- [ ] **Step 2: Run tests and verify the first test fails**

Run from `C:\Users\Randy Cordero\Desktop\didactic-guacamole`:

```powershell
python -m unittest test_app.RenderApiContractsTest.test_system_results_live_request_uses_cache_when_inline_scrape_disabled -v
```

Expected before implementation: fail because `live=1` still permits inline scraping.

- [ ] **Step 3: Implement the inline live scrape gate**

In `C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py`, add this helper near the existing request policy helpers:

```python
def inline_live_scrape_enabled():
    return str(os.environ.get("ALLOW_INLINE_LIVE_SCRAPE", "")).strip().lower() in ("1", "true", "yes", "on")


def should_inline_live_scrape():
    return should_use_live_scrape() and inline_live_scrape_enabled()
```

Then in `/system-results`, replace the live scrape decision with `should_inline_live_scrape()`:

```python
    use_inline_live = should_inline_live_scrape()
    if use_inline_live and date_key == get_dr_date_str():
        cached_payload = get_composed_live_system_results_cache(date_key, mode)
        if cached_payload is not None:
            log_live_request(date_key, mode, cached_payload.get("servedFrom", "response-cache"), started_at)
            return json_utf8(cached_payload)
```

Use `use_inline_live` for the remaining live scrape branches:

```python
        "source": "live-scraper" if use_inline_live else "supabase-cache",
```

```python
    if use_inline_live:
        if mode == "lottery":
            lottery_rows = lottery_rows_for_request_date(date_key)
        elif mode == "pick":
            pick_rows = pick_rows_for_request_date(date_key)
        else:
            lottery_rows, pick_rows = live_results_sections_for_date(
                date_key,
                include_lottery=True,
                include_pick=True,
            )
```

```python
        if not use_inline_live:
            lottery_rows = lottery_rows_for_request_date(date_key)
```

```python
        if not use_inline_live:
            pick_rows = pick_rows_for_request_date(date_key)
```

```python
        payload["source"] = "live-scraper" if use_inline_live else "cache-miss"
```

```python
    if use_inline_live and date_key == get_dr_date_str():
        payload["servedFrom"] = live_served_from(date_key, mode, lottery_rows, pick_rows)
        set_live_system_results_cache(date_key, mode, payload)
        log_live_request(date_key, mode, payload.get("servedFrom", "inline-scrape"), started_at)
```

- [ ] **Step 4: Run the cache-first tests**

Run from `C:\Users\Randy Cordero\Desktop\didactic-guacamole`:

```powershell
python -m unittest test_app.RenderApiContractsTest.test_system_results_live_request_uses_cache_when_inline_scrape_disabled test_app.RenderApiContractsTest.test_system_results_allows_inline_scrape_only_when_enabled -v
```

Expected: both tests pass.

- [ ] **Step 5: Run full Render API tests**

Run:

```powershell
python -m unittest test_app -v
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" add app.py test_app.py
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" commit -m "fix: keep results API cache-first by default"
```

---

### Task 2: Protect Manual Scraper Routes

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\test_app.py`

- [ ] **Step 1: Add failing tests for scraper route authorization**

Append these tests inside `RenderApiContractsTest`:

```python
    def test_run_system_scraper_requires_admin_secret(self):
        with patch.dict(app.os.environ, {"RESULTS_ADMIN_SECRET": "local-secret"}):
            response = self.client.post("/run-system-scraper?date=26-05-2026&mode=both")

        self.assertEqual(401, response.status_code)
        payload = json.loads(response.data.decode("utf-8"))
        self.assertFalse(payload["authorized"])

    def test_run_system_scraper_accepts_admin_secret_header(self):
        with patch.dict(app.os.environ, {"RESULTS_ADMIN_SECRET": "local-secret"}), \
            patch("app.scrape_cached", return_value=[]), \
            patch("app.pick_scrape_cached", return_value=[]), \
            patch("app.save_to_supabase") as save_lottery_mock, \
            patch("app.save_us_picks_to_supabase") as save_pick_mock:
            response = self.client.post(
                "/run-system-scraper?date=26-05-2026&mode=both",
                headers={"X-Results-Admin-Secret": "local-secret"},
            )

        self.assertEqual(200, response.status_code)
        save_lottery_mock.assert_called()
        save_pick_mock.assert_called()
```

- [ ] **Step 2: Run one failing authorization test**

Run:

```powershell
python -m unittest test_app.RenderApiContractsTest.test_run_system_scraper_requires_admin_secret -v
```

Expected before implementation: fail because the route does not reject missing secret.

- [ ] **Step 3: Implement shared admin secret helper**

In `C:\Users\Randy Cordero\Desktop\didactic-guacamole\app.py`, add:

```python
def results_admin_secret():
    return str(os.environ.get("RESULTS_ADMIN_SECRET", "")).strip()


def require_results_admin_secret():
    expected = results_admin_secret()
    if not expected:
        return None
    provided = str(request.headers.get("X-Results-Admin-Secret") or request.args.get("secret") or "").strip()
    if provided == expected:
        return None
    return json_utf8({"authorized": False, "error": "Results admin secret required"}, status=401)
```

At the top of these route handlers, add:

```python
    auth_error = require_results_admin_secret()
    if auth_error is not None:
        return auth_error
```

Routes that must receive the guard:

- `/run-scraper`
- `/run-system-scraper`
- `/run-pick-scraper`

- [ ] **Step 4: Run scraper route authorization tests**

Run:

```powershell
python -m unittest test_app.RenderApiContractsTest.test_run_system_scraper_requires_admin_secret test_app.RenderApiContractsTest.test_run_system_scraper_accepts_admin_secret_header -v
```

Expected: both tests pass.

- [ ] **Step 5: Run full Render API tests**

Run:

```powershell
python -m unittest test_app -v
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" add app.py test_app.py
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" commit -m "chore: protect manual results scraper routes"
```

---

### Task 3: Node.js Results Monitor

**Files:**
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\results-monitor.mjs`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\package.json`

- [ ] **Step 1: Create monitor script**

Create `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\results-monitor.mjs`:

```javascript
#!/usr/bin/env node

const renderBaseUrl = process.env.RESULTS_RENDER_URL || 'https://didactic-guacamole.onrender.com';
const date = process.env.RESULTS_MONITOR_DATE || toDrDate(new Date());

function toDrDate(value) {
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Santo_Domingo',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  return formatter.format(value).replaceAll('/', '-');
}

async function fetchJson(url) {
  const startedAt = Date.now();
  const response = await fetch(url, { headers: { accept: 'application/json' } });
  const body = await response.text();
  const durationMs = Date.now() - startedAt;
  let json;
  try {
    json = JSON.parse(body);
  } catch (error) {
    throw new Error(`Invalid JSON from ${url}: ${body.slice(0, 160)}`);
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} from ${url}: ${body.slice(0, 160)}`);
  }
  return { json, durationMs };
}

function sectionCount(payload, section) {
  return Number(payload?.[section]?.count || 0);
}

function buildReport(payload, durationMs) {
  const lotteryCount = sectionCount(payload, 'lotteries');
  const pickCount = sectionCount(payload, 'picks');
  const problems = [];
  if (lotteryCount <= 0) problems.push('lottery-cache-empty');
  if (pickCount <= 0) problems.push('pick-cache-empty');
  if (payload.source === 'live-scraper') problems.push('api-used-live-scraper');
  if (durationMs > 5000) problems.push('api-slow-response');

  return {
    ok: problems.length === 0,
    date: payload.date || date,
    source: payload.source || 'unknown',
    servedFrom: payload.servedFrom || null,
    durationMs,
    lotteryCount,
    pickCount,
    problems,
    checkedAt: new Date().toISOString(),
  };
}

const url = `${renderBaseUrl.replace(/\/$/, '')}/system-results?date=${encodeURIComponent(date)}&mode=both`;
const { json, durationMs } = await fetchJson(url);
const report = buildReport(json, durationMs);

console.log(JSON.stringify(report, null, 2));
if (!report.ok) {
  process.exitCode = 1;
}
```

- [ ] **Step 2: Add package script**

Modify `C:\Users\Randy Cordero\Desktop\lotterynet_android\package.json` and add:

```json
"results:monitor": "node tools/results-monitor.mjs"
```

The scripts block should keep the existing scripts and include the new one:

```json
"scripts": {
  "check": "node tools/check-project.mjs",
  "check:verbose": "node tools/check-project.mjs --verbose",
  "release:check": "node tools/pre-release.mjs",
  "results:monitor": "node tools/results-monitor.mjs"
}
```

- [ ] **Step 3: Run monitor against Render**

Run from `C:\Users\Randy Cordero\Desktop\lotterynet_android`:

```powershell
npm run results:monitor
```

Expected: JSON report prints `ok`, `date`, `source`, `lotteryCount`, `pickCount`, and `problems`.

- [ ] **Step 4: Commit**

```powershell
git -C "C:\Users\Randy Cordero\Desktop\lotterynet_android" add package.json tools/results-monitor.mjs
git -C "C:\Users\Randy Cordero\Desktop\lotterynet_android" commit -m "chore: add results monitor"
```

---

### Task 4: Post-Scrape Health Check in GitHub Actions

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole\.github\workflows\scrape.yml`

- [ ] **Step 1: Add health check step after scraper run**

In `.github/workflows/scrape.yml`, after the `Run scraper` step, add:

```yaml
      - name: Check Render results API health
        env:
          RESULTS_RENDER_URL: ${{ secrets.RESULTS_RENDER_URL }}
        run: |
          python - <<'PY'
          import json
          import os
          import sys
          import urllib.request

          base_url = os.environ.get("RESULTS_RENDER_URL", "https://didactic-guacamole.onrender.com").rstrip("/")
          url = f"{base_url}/system-results?mode=both"
          with urllib.request.urlopen(url, timeout=20) as response:
              payload = json.loads(response.read().decode("utf-8"))
          lotteries = int(payload.get("lotteries", {}).get("count", 0))
          picks = int(payload.get("picks", {}).get("count", 0))
          source = payload.get("source")
          print(json.dumps({
              "url": url,
              "source": source,
              "lotteries": lotteries,
              "picks": picks,
          }, indent=2))
          if source == "live-scraper":
              print("Render API should be cache-first for scheduled health checks", file=sys.stderr)
              sys.exit(1)
          if lotteries <= 0 and picks <= 0:
              print("Render API returned no results after scraper run", file=sys.stderr)
              sys.exit(1)
          PY
```

- [ ] **Step 2: Validate workflow syntax locally**

Run from `C:\Users\Randy Cordero\Desktop\didactic-guacamole`:

```powershell
python -c "import pathlib, sys; p=pathlib.Path('.github/workflows/scrape.yml'); print(p.read_text(encoding='utf-8')[:120])"
```

Expected: command prints the start of the workflow file without encoding errors.

- [ ] **Step 3: Commit**

```powershell
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" add .github/workflows/scrape.yml
git -C "C:\Users\Randy Cordero\Desktop\didactic-guacamole" commit -m "ci: check results API after scraper run"
```

---

### Task 5: Results Operations Runbook

**Files:**
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\docs\results-operations-runbook.md`

- [ ] **Step 1: Create runbook**

Create `docs\results-operations-runbook.md`:

```markdown
# LotteryNet Results Operations Runbook

## Normal Flow

GitHub Actions runs the Python scraper every 10 minutes. The scraper writes lottery and Pick results into Supabase cache keys. Render serves `/system-results` from that cache. Android POS devices read local data first, then Supabase/Render when they need refresh.

## What Must Stay True

- Android POS devices should not depend on inline scraping during normal sales.
- Render `/system-results` should return `source: "supabase-cache"` or `source: "cache-miss"` for normal traffic.
- `source: "live-scraper"` is an emergency/admin condition, not the normal path.
- Manual scraper endpoints must require `RESULTS_ADMIN_SECRET`.

## Daily Checks

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

1. Check GitHub Actions scraper run.
2. Check Render `/system-results?mode=both`.
3. Check Supabase cache keys:
   - `lot_results_cache_by_day:<date>`
   - `pick_results_cache_by_day:<date>`
4. Use manual refresh only with the admin secret.
5. Re-run the Node monitor and confirm `source` is not `live-scraper`.

## Manual Refresh Rule

Manual refresh is allowed when a lottery source published a result and the scheduled scraper did not capture it. Manual refresh is not a replacement for the scheduled scraper.
```

- [ ] **Step 2: Commit**

```powershell
git -C "C:\Users\Randy Cordero\Desktop\lotterynet_android" add docs/results-operations-runbook.md docs/superpowers/plans/2026-05-26-results-current-architecture-small-improvements.md
git -C "C:\Users\Randy Cordero\Desktop\lotterynet_android" commit -m "docs: plan results architecture improvements"
```

---

## Verification Before Release

- [ ] Run Render API tests:

```powershell
python -m unittest test_app -v
```

- [ ] Run Android project checks:

```powershell
npm run check
```

- [ ] Run results monitor:

```powershell
npm run results:monitor
```

- [ ] Confirm Render environment variables:

```text
ALLOW_INLINE_LIVE_SCRAPE=0
RESULTS_ADMIN_SECRET=<strong secret stored in Render, not committed>
```

## Rollout Order

1. Merge and deploy Task 1 first so normal POS traffic stays cache-first.
2. Deploy Task 2 immediately after Task 1 to protect manual scraper endpoints.
3. Add Task 3 monitor and run it manually for several days.
4. Add Task 4 CI health check after the monitor behavior is trusted.
5. Keep Task 5 runbook updated when schedules, sources, or endpoints change.

## Decision Record

Chosen path: keep Python scraper and add Node.js monitoring.

Rejected for now: rewrite all results/scraping to Node.js.

Reason: scale improves more from cache-first architecture, protected admin paths, and monitoring than from changing language. The current Python scraper already contains business logic for RD, Pick, backfill, Supabase writes, and deduplication; replacing it all at once creates unnecessary operational risk.
