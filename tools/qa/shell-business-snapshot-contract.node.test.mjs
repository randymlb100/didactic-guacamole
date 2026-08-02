import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync("app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt", "utf8");
const loaderSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/shell/ShellDashboardLoader.kt",
  "utf8",
);
const chromeSource = readFileSync("app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt", "utf8");

test("business inline uses the aggregated daily finance report", () => {
  assert.match(loaderSource, /getScopedPeriodReport\(/);
  assert.match(loaderSource, /preset = FinancePeriodPreset\.DAY/);
  assert.match(loaderSource, /\)\.summary/);
  assert.match(source, /buildShellDashboardSnapshot\(/);
});

test("returning to menu reuses the existing shell metrics while refreshing", () => {
  const openMenu = chromeSource.match(
    /fun openShellMenu\(context: Context\) \{([\s\S]*?)\n\}/,
  )?.[1] ?? "";

  assert.match(openMenu, /Intent\.FLAG_ACTIVITY_CLEAR_TOP/);
  assert.match(openMenu, /Intent\.FLAG_ACTIVITY_SINGLE_TOP/);
});
