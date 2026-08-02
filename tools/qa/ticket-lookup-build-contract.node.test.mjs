import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

test("ticket lookup applies cashier scope inside visible ticket filtering", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /requestedCashierKey,\s*\n\s*\)\s*\{/s);
  assert.match(source, /dateFiltered\.filter\s*\{\s*ticket\s*->/s);
  assert.doesNotMatch(source, /SectionHeader\([\s\S]{0,500}\)\.filter\s*\{/);
});

test("ticket lookup refreshes from Realtime without a polling loop", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /subscribeTicketOwnerSignals/);
  assert.match(source, /refreshOwnerFromRealtime/);
  assert.match(source, /override fun onResume\(\)[\s\S]{0,260}refreshLookupLocalState[\s\S]{0,420}refreshLookupFromRealtime/);
  assert.match(source, /realtimeSubscriptions\.forEach \{ it\.close\(\) \}/);
  assert.doesNotMatch(source, /object\s*:\s*Runnable\s*\{[\s\S]{0,500}postDelayed\(\s*this/);
});

test("ticket lookup shares the admin realtime owner scope for cashier visibility", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /resolveTicketLookupRealtimeOwnerKeys\(lookupSession\)/);
  assert.match(source, /session\.role == UserRole\.CASHIER \|\| session\.role == UserRole\.SUPERVISOR/);
  assert.match(source, /listOf\(session\.adminId, session\.adminUser\)/);
  assert.match(source, /invalidateTicketRealtimeCaches\(ownerKey\)/);
});

test("audited realtime screens invalidate local ticket caches before syncing", () => {
  const files = [
    "ui/tickets/TicketLookupActivity.kt",
    "ui/tickets/TicketSummaryActivity.kt",
    "ui/sales/SalesActivity.kt",
    "ui/admin/AdminDashboardActivity.kt",
    "ui/admin/AdminCashierDetailActivity.kt",
    "ui/admin/AdminWinnersActivity.kt",
    "ui/admin/AdminMonitorActivity.kt",
    "ui/admin/AdminLotteryMonitorActivity.kt",
  ];
  for (const relativePath of files) {
    const source = readFileSync(
      `${root}/app/src/main/java/com/lotterynet/pro/${relativePath}`,
      "utf8",
    );
    assert.match(source, /subscribeTicketOwnerSignals/);
    assert.match(source, /invalidateTicketRealtimeCaches\(ownerKey\)/, relativePath);
  }
});

test("ticket lookup starts list-first with collapsible filters", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /var showFilters by rememberSaveable/);
  assert.match(source, /AnimatedVisibility\(visible = showFilters\)/);
  assert.match(source, /FilterChip\(/);
  assert.match(source, /filterTicketLookupToolbarTickets/);
  assert.match(source, /selectedCashierKey/);
  assert.match(source, /DropdownMenuItem\(/);
  assert.match(source, /TicketLookupResults\([\s\S]{0,900}modifier = Modifier\.weight\(1f, fill = true\)/);
});

test("ticket mode owns paid/unpaid status and pay-all stays in pay mode", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /"unpaid" to "No pagados"/);
  assert.match(source, /mode == LookupMode\.PAY/);
  assert.match(source, /label = if \(isPayingAll\) "Pagando \$payAllProgress" else "Paga todo"/);
});

test("cobro keeps the list first with independent search and filter chips", () => {
  const source = readFileSync(
    `${root}/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt`,
    "utf8",
  );
  assert.match(source, /selected = showSearch \|\| query\.isNotBlank\(\)/);
  assert.match(source, /label = \{ Text\("Buscar"\) \}/);
  assert.match(source, /label = \{ Text\("Filtros"\) \}/);
  assert.match(source, /AnimatedVisibility\(visible = showSearch\)/);
  assert.match(source, /Buscar ticket, serial o usuario/);
  assert.match(source, /TicketLookupResults\([\s\S]{0,900}modifier = Modifier\.weight\(1f, fill = true\)/);
});
