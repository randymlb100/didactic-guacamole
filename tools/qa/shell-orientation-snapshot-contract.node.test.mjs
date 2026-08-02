import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const shellSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt",
  "utf8",
);
const viewModelPath =
  "app/src/main/java/com/lotterynet/pro/ui/shell/ShellDashboardViewModel.kt";
const viewModelSource = existsSync(viewModelPath)
  ? readFileSync(viewModelPath, "utf8")
  : "";

test("shell keeps the latest business snapshot across orientation changes", () => {
  assert.ok(viewModelSource, "ShellDashboardViewModel must retain the inline state");
  assert.match(viewModelSource, /class ShellDashboardViewModel\s*:\s*ViewModel\(\)/);
  assert.match(viewModelSource, /StateFlow<ShellDashboardUiState>/);
  assert.match(viewModelSource, /snapshot = if \(current\.key == key\) current\.snapshot else null/);
  assert.match(shellSource, /val dashboardViewModel: ShellDashboardViewModel = viewModel\(\)/);
  assert.match(shellSource, /collectAsStateWithLifecycle\(\)/);
  assert.doesNotMatch(
    shellSource,
    /var dashboardSnapshot by remember\(session\.userId, dayKey\)/,
  );
});

test("rotation does not start a duplicate server refresh", () => {
  assert.match(viewModelSource, /if \(refreshJob\?\.isActive == true && activeKey == key\) return/);
  assert.match(viewModelSource, /if \(current\.key == key && current\.snapshot != null\) return/);
  assert.match(shellSource, /var hasSeenResume = false/);
  assert.match(shellSource, /if \(hasSeenResume\)/);
});
