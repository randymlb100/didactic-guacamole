import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";
const source = readFileSync(`${root}/app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`, "utf8");

test("POS mode forces the compact sales layout without changing sale logic", () => {
  assert.match(source, /posLiteEnabled \|\| windowMode == LotteryNetWindowMode\.POS_TIGHT/);
  assert.match(source, /windowMode = if \(tight\) LotteryNetWindowMode\.POS_TIGHT else windowMode/);
  assert.match(source, /showStatsBadges = false/);
  assert.match(source, /keySpacingDp = 0/);
  assert.match(source, /keyHeightDp = 48/);
});

test("POS sales touch targets remain at least 48dp", () => {
  const compactBlock = source.match(/LotteryNetWindowMode\.POS_TIGHT -> VentaKeypadLayoutContract\(([^]*?)\n        \)/)?.[1] ?? "";
  assert.match(compactBlock, /keyHeightDp = 48/);
});
