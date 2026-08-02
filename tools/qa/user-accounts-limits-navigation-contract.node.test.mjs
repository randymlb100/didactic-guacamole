import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(
  "app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt",
  "utf8",
);

test("limits use a clear overview and three independent destinations", () => {
  assert.match(source, /enum class CashierLimitsDestination[\s\S]*OVERVIEW,[\s\S]*GLOBAL,[\s\S]*PERSONAL,[\s\S]*POOL,/);
  assert.match(source, /Centro de límites/);
  assert.match(source, /Reglas del negocio/);
  assert.match(source, /Pool del negocio/);
  assert.match(source, /Límites de cajeros/);
  assert.match(source, /Base para cajeros/);
  assert.match(source, /Límite personal/);
  assert.match(source, /ListItem\(/);
  assert.ok(
    source.indexOf('title = "Pool del negocio"') < source.indexOf('title = "Base para cajeros"'),
    "the business pool should be presented before cashier limits",
  );
});

test("cashier account controls are not rendered inside limits", () => {
  assert.match(
    source,
    /selectedAdminSection != CashierAdminSection\.LIMITS[\s\S]*AdminAccountsControlPanel\(/,
  );
  assert.match(source, /Área administrativa/);
  assert.match(
    source,
    /private fun CashierAdminSectionTabs\([\s\S]{0,2600}DropdownMenu\(/,
  );
});

test("detail navigation and progressive disclosure preserve one save path", () => {
  assert.match(source, /CashierLimitsDetailHeader\(/);
  assert.match(source, /Volver al centro/);
  assert.match(source, /LimitEditorSectionHeader\([\s\S]*Lotería normal/);
  assert.match(source, /LimitEditorSectionHeader\([\s\S]*Pick/);
  assert.match(source, /cashierAdminSaveServerActionLabel\(\)/);
});

test("existing persistence callbacks remain unchanged", () => {
  assert.match(source, /onSaveCashierLimits\(it, limits\)/);
  assert.match(source, /onSaveDefaultCashierLimits\(limits\)/);
  assert.match(source, /onSaveCashierPoolLimits\(poolLimits\)/);
  assert.match(source, /CashierGlobalLimitConfirmSheet\(/);
  assert.match(source, /CashierPoolLimitsSheet\(/);
});
