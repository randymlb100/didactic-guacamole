import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../../", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

test("Cobro prioritizes winner tickets without changing winner logic", async () => {
  const activity = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");

  assert.match(activity, /title = "Cobro"/);
  assert.match(activity, /title = "Estado de cobro"/);
  assert.match(activity, /title = "Tickets ganadores"/);
  assert.match(activity, /CompactSegmentedSelector\(/);
  assert.match(activity, /"Premio \$\{formatWinnerMoney\(winnerAmount\(ticket\)\)\}"/);
  assert.match(activity, /filterPendingWinnerTickets\(tickets\)/);
  assert.match(activity, /filterPaidWinnerTickets\(tickets\)/);
  assert.doesNotMatch(activity, /CurrentScopeDropdownCard\(/);
  assert.doesNotMatch(activity, /WinnerFilterChip/);
  assert.doesNotMatch(activity, /\bFilterChip\(/);
  assert.doesNotMatch(activity, /WinnerMetric\(/);
});

test("Cobro Kotlin source remains structurally balanced", async () => {
  const activity = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");
  assert.equal(
    [...activity].filter((character) => character === "{").length,
    [...activity].filter((character) => character === "}").length,
  );
  assert.equal(
    [...activity].filter((character) => character === "(").length,
    [...activity].filter((character) => character === ")").length,
  );
});

test("Limits exposes each business scope before editing", async () => {
  const activity = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt");
  const models = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsUiModels.kt");

  for (const label of ["GLOBAL", "POR USUARIO", "SOLO ADMIN", "OPERACIÓN", "INTERFAZ"]) {
    assert.match(models, new RegExp(`scopeLabel = "${label}"`));
  }
  assert.match(activity, /Text\("Centro de límites"/);
  assert.match(activity, /Pool · usuarios · operación/);
  assert.match(activity, /private fun AdminLimitsOverview\(/);
  assert.match(activity, /private fun AdminLimitsDetailNavigation\(/);
  assert.match(activity, /Volver al resumen/);
  assert.match(activity, /label = item\.scopeLabel/);
  assert.match(activity, /contentDescription = "Volver al resumen de límites"/);
  assert.doesNotMatch(activity, /private fun AdminLimitsHub/);
  assert.doesNotMatch(activity, /private fun AdminLimitsEditorContext/);
  assert.doesNotMatch(activity, /Text\("⋮"/);
  assert.doesNotMatch(activity, /CurrentScopeDropdownCard\(/);
});

test("Limits keeps the existing storage and server payload path", async () => {
  const activity = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt");

  for (const contract of [
    "pushPoolLimitsServiceFirst",
    "pushDefaultLimitsServiceFirst",
    "pushAdminSelfLimitsServiceFirst",
    "admin_operational_limits:",
    "recharge_limits:",
  ]) {
    assert.match(activity, new RegExp(contract));
  }
});

test("Limits Kotlin source remains structurally balanced", async () => {
  const activity = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt");
  assert.equal(
    [...activity].filter((character) => character === "{").length,
    [...activity].filter((character) => character === "}").length,
  );
  assert.equal(
    [...activity].filter((character) => character === "(").length,
    [...activity].filter((character) => character === ")").length,
  );
});
