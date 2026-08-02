import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const activity = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt",
  "utf8",
);
const contracts = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsContracts.kt",
  "utf8",
);

test("limits UX distinguishes pool exposure from cashier limits", () => {
  assert.match(activity, /Pool de exposición por jugada/);
  assert.match(activity, /Lotería \+ número \+ tipo de jugada/);
  assert.match(activity, /Límites base del cajero/);
  assert.match(activity, /no controlan el pool/);
  assert.match(activity, /Límite de cobro/);
  assert.match(activity, /Pool = exposición global por lotería, número y jugada/);
});

test("limits scope contracts remain separate", () => {
  assert.match(contracts, /CASHIER_DEFAULTS/);
  assert.match(contracts, /CASHIER_SPECIFIC/);
  assert.match(contracts, /cashierDefaultsAffectAdmin = false/);
  assert.match(activity, /pushPoolLimitsServiceFirst/);
  assert.match(activity, /pushDefaultLimitsServiceFirst/);
  assert.match(activity, /recharge_limits/);
});
