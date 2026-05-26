# Production Ready Results and System Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make production reliable so Results always has complete normal + Pick data, and System mode saved on one phone loads on a new phone.

**Architecture:** Keep the app local-first, but make production data authoritative. Render/GitHub refresh Supabase caches; Android reads complete cache by exact date and downloads `system_modes:<owner>` before applying local defaults.

**Tech Stack:** Android/Kotlin, Python scraper, Render web service, GitHub Actions, Supabase `lotterynet_kv`, Gradle unit tests.

---

### Task 1: Make Results Production Job Real

**Files:**
- Modify: `.github/workflows/scrape.yml`
- Modify: `render.yaml`
- Test: `scraper/render_app_test.py`

- [ ] **Step 1: Decide one scheduled owner**

Use only one automatic source to avoid one job overwriting another. Recommended:

```yaml
# GitHub Actions becomes the scheduled production refresh.
on:
  workflow_dispatch:
  schedule:
    - cron: "*/15 * * * *"
```

Render can stay as web API, but should not be the only scheduler unless a Render cron service is added.

- [ ] **Step 2: Add a read-only health route contract**

Add/keep a test that `/health` returns immediately without scraping:

```python
def test_health_is_fast_and_does_not_scrape(self):
    status_headers = {}

    def start_response(status, headers):
        status_headers["status"] = status

    body = b"".join(app.application({"PATH_INFO": "/health", "QUERY_STRING": ""}, start_response))

    self.assertEqual("200 OK", status_headers["status"])
    self.assertIn(b"didactic-guacamole", body)
```

- [ ] **Step 3: Verify Render no longer blocks health**

Run:

```powershell
Invoke-WebRequest -Uri "https://didactic-guacamole.onrender.com/health" -TimeoutSec 10 -UseBasicParsing
```

Expected: HTTP 200 in under 10 seconds.

---

### Task 2: Backfill Incomplete Supabase Result Caches

**Files:**
- Use existing: `scraper/scrape_and_save.py`

- [ ] **Step 1: Run dry audit first**

Run read-only audit for the bad dates:

```powershell
python -m unittest discover -s scraper -p "*_test.py"
```

Expected: all scraper tests pass.

- [ ] **Step 2: Backfill exact dates after deployment**

After the fixed scraper is deployed with Supabase key configured, run the scraper for exact dates:

```powershell
python scraper/scrape_and_save.py 08-05-2026 10-05-2026 11-05-2026
```

Expected:
- `08-05-2026`: Pick missing `0`
- `10-05-2026`: Pick missing `0`, with Sunday closed draws as `no_draw`
- normal lotteries remain present

- [ ] **Step 3: Re-run read-only production audit**

Use the existing Python audit pattern:

```powershell
python tools/audit_results_cache.py
```

If the file does not exist yet, create it from the read-only script already used in this thread.

Expected:
- `09-05-2026`: Pick `115/115`
- `08-05-2026`: Pick `115/115`
- `10-05-2026`: Pick `115/115`
- normal numeric found remains `42`

---

### Task 3: Load System Mode From Server On New Phones

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/storage/LocalAdminLotteryConfigRepository.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/core/storage/AdminSystemModeConfigContractsTest.kt`

- [ ] **Step 1: Add owner key helper**

Create a stable helper:

```kotlin
fun adminSystemModeRemoteKey(adminId: String?, userId: String?, banca: String?): String {
    val ownerKey = adminId?.takeIf { it.isNotBlank() }
        ?: userId?.takeIf { it.isNotBlank() }
        ?: banca.orEmpty().ifBlank { "default" }
    return "system_modes:$ownerKey"
}
```

- [ ] **Step 2: Test key consistency**

```kotlin
@Test
fun `system mode remote key prefers admin id for all phones`() {
    assertEquals(
        "system_modes:ADM-1",
        adminSystemModeRemoteKey(adminId = "ADM-1", userId = "USER-2", banca = "Banca A"),
    )
}
```

- [ ] **Step 3: Add remote fetch function**

Use `SupabaseMasterConfigRemoteStore.fetchValue(key)` and decode:

```kotlin
fun fetchRemoteSystemModeConfig(
    remoteStore: MasterConfigRemoteStore,
    key: String,
): AdminSystemModeConfig? {
    val payload = runCatching { remoteStore.fetchValue(key) }.getOrNull() ?: return null
    return decodeAdminSystemModeConfig(payload.toString())
}
```

- [ ] **Step 4: Apply remote config before screens use defaults**

In Admin, Venta, Resultados, and Monitor screens:

```kotlin
thread(name = "system-mode-bootstrap") {
    val remote = fetchRemoteSystemModeConfig(remoteStore, remoteKey)
    if (remote != null) {
        val saved = adminLotteryRepository.saveSystemModeConfig(remote)
        runOnUiThread { systemModeConfig = saved }
    }
}
```

Expected behavior: a new phone opens with the server-saved mode, not default local mode.

---

### Task 4: Verify End To End

**Files:**
- Existing tests only.

- [ ] **Step 1: Android tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest" --tests "com.lotterynet.pro.ui.results.ResultsActivityContractsTest" --tests "com.lotterynet.pro.core.results.ResultsSupabaseStoreTest" --tests "com.lotterynet.pro.core.results.ResultsScraperOrchestratorTest" --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Scraper tests**

Run:

```powershell
python -m unittest discover -s scraper -p "*_test.py"
```

Expected: all tests pass.

- [ ] **Step 3: Build APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

---

### Ready Criteria

- Render `/health` responds under 10 seconds.
- There is one real automatic scheduler.
- Supabase has normal + Pick caches complete for checked dates.
- `system_modes:*` exists after pressing Guardar servidor.
- A new phone loads the saved System mode from Supabase.
- APK builds after tests pass.
