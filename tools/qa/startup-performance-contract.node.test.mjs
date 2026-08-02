import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const manifest = readFileSync("app/src/main/AndroidManifest.xml", "utf8");
const loginSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt",
  "utf8",
);
const shellSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt",
  "utf8",
);
const appSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt",
  "utf8",
);
const updateManagerSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/update/UpdateManager.kt",
  "utf8",
);
const sessionRepositorySource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/storage/LocalSessionRepository.kt",
  "utf8",
);

test("startup uses the Android splash theme for the real entry activities", () => {
  assert.match(
    manifest,
    /android:name="\.ui\.login\.LoginActivity"[\s\S]*?android:theme="@style\/Theme\.LotteryNetPro\.Startup"/,
  );
  assert.match(
    manifest,
    /android:name="\.ui\.shell\.ShellActivity"[\s\S]*?android:theme="@style\/Theme\.LotteryNetPro\.Startup"/,
  );
  assert.match(loginSource, /installSplashScreen\(\)\s*\n\s*super\.onCreate/);
  assert.match(shellSource, /installSplashScreen\(\)\s*\n\s*super\.onCreate/);
});

test("first-frame reporting happens after Compose has produced a frame", () => {
  for (const source of [loginSource, shellSource]) {
    assert.match(source, /withFrameNanos\s*\{\s*\}\s*\n\s*reportFullyDrawn\(\)/);
  }
});

test("shell does not block setContent on remote configuration", () => {
  const setContentIndex = shellSource.indexOf("setContent {");
  assert.ok(setContentIndex > 0, "ShellActivity must set Compose content");
  assert.doesNotMatch(shellSource.slice(0, setContentIndex), /fetchValue\(/);
  assert.match(shellSource, /LaunchedEffect\(session\.userId, session\.role\)/);
  assert.match(shellSource, /coroutineScope\s*\{/);
  assert.match(shellSource, /async\(Dispatchers\.IO\)/);
});

test("startup remains local-first and avoids blocking coroutine primitives", () => {
  const shellBeforeContent = shellSource.slice(0, shellSource.indexOf("setContent {"));
  const loginBeforeContent = loginSource.slice(0, loginSource.indexOf("setContent {"));
  assert.doesNotMatch(shellBeforeContent, /runBlocking\s*\{/);
  assert.doesNotMatch(loginBeforeContent, /runBlocking\s*\{/);
});

test("cold start defers non-critical SDK and WorkManager initialization until after a frame", () => {
  const onCreate = appSource.slice(
    appSource.indexOf("override fun onCreate()"),
    appSource.indexOf("private fun registerSafeLifecycleCallbacks"),
  );
  assert.doesNotMatch(onCreate, /bootstrapSentry\(\)/);
  assert.doesNotMatch(onCreate, /schedulePeriodic\(this\)/);
  assert.match(appSource, /postFrameCallback\s*\{/);
  assert.match(appSource, /initializeDeferredStartup\(\)/);
});

test("encrypted session storage is initialized once per app process", () => {
  assert.match(sessionRepositorySource, /@Volatile\s+private var sharedPrefs: SharedPreferences\? = null/);
  assert.match(sessionRepositorySource, /context\.applicationContext/);
  assert.match(sessionRepositorySource, /synchronized\(prefsLock\)/);
});

test("OTA registration does not eagerly open encrypted session storage", () => {
  assert.match(
    updateManagerSource,
    /private val sessions by lazy\(LazyThreadSafetyMode\.SYNCHRONIZED\)/,
  );
});
