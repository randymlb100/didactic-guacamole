import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const settings = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt",
  "utf8",
);
const sports = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt",
  "utf8",
);
const servicesGames = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt",
  "utf8",
);
const winners = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt",
  "utf8",
);

test("settings detail uses one compact category context", () => {
  assert.match(settings, /private fun AdminConfigAreaContextCard/);
  assert.match(settings, /AdminConfigAreaContextCard\(/);
});

test("sports navigation is one compact scope selector", () => {
  const start = sports.indexOf("private fun SportsbookTabStrip(");
  const end = sports.indexOf("private fun sportsbookTabTone", start);
  const body = sports.slice(start, end);
  assert.match(body, /CurrentScopeDropdownCard/);
  assert.doesNotMatch(body, /CompactAdaptiveGrid/);
});

test("services and games share the native app chrome and compact catalog toolbar", () => {
  assert.match(servicesGames, /AppTopBar\(/);
  assert.match(servicesGames, /ScreenChromeAction\(/);
  assert.match(servicesGames, /private fun ServicesGamesCatalogToolbar/);
  assert.match(servicesGames, /CompactSegmentedSelector\(/);
});

test("catalog cards reserve the center column for product information", () => {
  assert.match(servicesGames, /private fun ServicesGamesCatalogCard/);
  assert.match(servicesGames, /Column\(modifier = Modifier\.weight\(1f\)/);
});

test("cobro keeps one compact filter panel so the winner list stays primary", () => {
  const routeStart = winners.indexOf("private fun AdminWinnersRoute(");
  const rowStart = winners.indexOf("private fun WinnerTicketRow(", routeStart);
  const route = winners.slice(routeStart, rowStart);
  assert.match(route, /CompactSegmentedSelector\(/);
  assert.match(route, /contentPadding = PaddingValues\(horizontal = 10\.dp, vertical = 9\.dp\)/);
  assert.doesNotMatch(route, /CurrentScopeDropdownCard\(/);
  assert.doesNotMatch(route, /WinnerMetric\(/);
});
