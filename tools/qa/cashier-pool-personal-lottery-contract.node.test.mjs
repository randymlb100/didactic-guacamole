import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

test("pool limits are explicit and are not silently read from defaults", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/core/storage/LocalCashierSalesLimitRepository.kt`,
    "utf8",
  );
  assert.match(source, /val pool = root\.optJSONObject\("pool"\) \?: return CashierSalesLimitInputs\(/);
  assert.doesNotMatch(source, /val pool = root\.optJSONObject\("pool"\) \?: root\.optJSONObject\("defaults"\)/);
});

test("server trigger scopes sales by lottery, play type and number bucket", () => {
  const source = readFileSync(
    `${root}/supabase/migrations/20260604121500_cashier_play_limits_shared_by_admin_lottery.sql`,
    "utf8",
  );
  assert.match(source, /ti\.lottery_id = new\.lottery_id/);
  assert.match(source, /ti\.play_type = new\.play_type/);
  assert.match(source, /ln_sale_limit_bucket\(ti\.play_type/);
});

test("server separates personal cashier limit from explicit shared pool", () => {
  const source = readFileSync(
    `${root}/supabase/migrations/20260715130000_separate_cashier_pool_and_personal_lottery_limits.sql`,
    "utf8",
  );
  assert.match(source, /ln_cashier_pool_limit_config/);
  assert.match(source, /v_personal_limit/);
  assert.match(source, /v_pool_limit/);
  assert.match(source, /v_sold \+ v_amount > v_personal_limit/);
  assert.match(source, /v_sold \+ v_amount > v_pool_limit/);
  assert.match(source, /Pool agotado/);
  assert.match(source, /lower\(coalesce\(t\.cashier_key, ''\)\) <> all\(v_admin_lkeys\)/);
  assert.match(source, /ti\.play_type = new\.play_type/);
  assert.match(source, /ln_sale_limit_bucket\(ti\.play_type/);
});

test("Android exposure does not treat defaults as a pool", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/core/sales/SaleExposureEngine.kt`,
    "utf8",
  );
  assert.match(source, /val pool = json\.optJSONObject\("pool"\)/);
  assert.doesNotMatch(source, /optJSONObject\("pool"\) \?: json\.optJSONObject\("defaults"\)/);
});
