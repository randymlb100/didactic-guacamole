import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const PROJECT_ROOT = new URL("../../", import.meta.url);

async function source(path) {
  return readFile(new URL(path, PROJECT_ROOT), "utf8");
}

function assertContains(text, expected, label) {
  assert.ok(text.includes(expected), `${label}: falta "${expected}"`);
}

function functionSection(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  assert.notEqual(start, -1, `no encontre ${startToken}`);
  const end = endToken ? text.indexOf(endToken, start + startToken.length) : -1;
  return end === -1 ? text.slice(start) : text.slice(start, end);
}

test("ganadores no depende solo de hoy y valida cada ticket con su fecha de sorteo", async () => {
  const adminWinners = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");
  const refresh = functionSection(adminWinners, "private fun refreshWinnersData()", "private fun syncWinners");
  const byDrawDate = functionSection(adminWinners, "internal fun buildAdminWinnerTicketsByDrawDate", "internal fun resolveWinnerListTicketAfterValidation");

  assertContains(refresh, "salesRepository.getAvailableDayKeys()", "ganadores refresh");
  assertContains(refresh, "ADMIN_WINNERS_LOOKBACK_DAYS", "ganadores refresh");
  assertContains(refresh, "winnerDayKeys.flatMap(salesRepository::getTicketsForDay)", "ganadores refresh");
  assertContains(refresh, "it.effectiveDrawDateKey()", "ganadores refresh");
  assertContains(refresh, "associateWith(resultsRepository::getResultsForDate)", "ganadores refresh");
  assertContains(refresh, "buildAdminWinnerTicketsByDrawDate", "ganadores refresh");
  assert.doesNotMatch(refresh, /tickets\s*=\s*salesRepository\.getTicketsForDay\(dayKey\)/);

  assertContains(byDrawDate, ".groupBy { it.effectiveDrawDateKey() }", "ganadores por fecha");
  assertContains(byDrawDate, "resultsByDate[drawDateKey].orEmpty()", "ganadores por fecha");
});

test("ganadores recalcula premio con tabla del vendedor del ticket y no conserva premio viejo incorrecto", async () => {
  const adminWinners = await source("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");
  const contracts = await source("app/src/test/java/com/lotterynet/pro/ui/admin/AdminWinnersContractsTest.kt");

  assertContains(adminWinners, "prizePayoutRepository.resolveForTicket", "tabla premio ganadores");
  assertContains(adminWinners, "sellerUser = ticket.sellerUser ?: ticket.adminUser", "tabla premio ganadores");
  assertContains(adminWinners, "resolveWinnerListTicketAfterValidation", "revalidacion ganadores");

  assertContains(contracts, "winner section refreshes stale winner details with configured admin payout", "contrato admin");
  assertContains(contracts, "winner section refreshes cashier tickets with cashier payout table", "contrato cajero");
  assertContains(contracts, "winner section validates each ticket with its own draw date results", "contrato fecha sorteo");
  assertContains(contracts, "PrizeTableConfig(q1 = 72)", "contrato pago 72");
});

test("cache local y payload remoto conservan fecha real de sorteo, detalles ganadores y evitan tickets vacios", async () => {
  const localSales = await source("app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt");
  const hydration = await source("app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt");
  const model = await source("app/src/main/java/com/lotterynet/pro/core/model/SalesModels.kt");

  assertContains(model, "fun TicketRecord.effectiveDrawDateKey()", "modelo fecha sorteo");
  assertContains(model, "drawDateKey?.takeIf { it.isNotBlank() } ?: dominicanDayKey(createdAtEpochMs)", "modelo fecha sorteo");

  assertContains(localSales, "val dayKey = ticket.effectiveDrawDateKey()", "guardar ticket local");
  assertContains(localSales, "tickets.groupBy { it.effectiveDrawDateKey() }", "guardar todos local");
  assertContains(localSales, 'put("drawDateKey", ticket.drawDateKey)', "json local");
  assertContains(localSales, 'put("drawDate", ticket.effectiveDrawDateKey())', "json local");
  assertContains(localSales, '"winningDetails"', "json local");
  assertContains(localSales, "if (total > 0.0 && plays.isEmpty()) return false", "proteccion ticket vacio");

  assertContains(hydration, 'put("drawDateKey", ticket.effectiveDrawDateKey())', "payload remoto");
  assertContains(hydration, 'put("totalPrize", ticket.totalPrize)', "payload remoto");
  assertContains(hydration, 'put("totalPremio", ticket.totalPrize)', "payload remoto");
  assertContains(hydration, '"winningDetails"', "payload remoto");
});

test("supabase propaga premio autorizado del servidor sin romper tickets ya pagados", async () => {
  const syncMigration = await source("supabase/migrations/20260531144500_refine_winner_owner_snapshot_alias_scope.sql");
  const paidMigration = await source("supabase/migrations/20260531151000_server_authoritative_prize_overrides_paid_snapshot.sql");
  const pruneMigration = await source("supabase/migrations/20260531163000_prune_cross_owner_ticket_snapshot_duplicates.sql");

  assertContains(syncMigration, "lotterynet_sync_ticket_owner_payload", "sync ganador supabase");
  assertContains(syncMigration, "owner_row.owner_key = any(owner_keys)", "sync ganador supabase");
  assertContains(syncMigration, "'totalPrize', prize_amount", "sync ganador supabase");
  assertContains(syncMigration, "'totalPremio', prize_amount", "sync ganador supabase");
  assertContains(syncMigration, "'winningDetails', winning_details", "sync ganador supabase");
  assertContains(syncMigration, "'serverPrizeAuthoritative', true", "sync ganador supabase");

  assertContains(paidMigration, "incoming_server_authoritative", "proteccion pagado supabase");
  assertContains(paidMigration, "previous_status = any(paid_statuses)", "proteccion pagado supabase");
  assertContains(paidMigration, "'status', 'paid'", "proteccion pagado supabase");
  assertContains(paidMigration, "'totalPrize', incoming_prize", "proteccion pagado supabase");
  assert.doesNotMatch(paidMigration, /incoming_prize >= previous_prize/);

  assertContains(pruneMigration, "lotterynet_owner_payload_ticket_matches", "limpieza duplicados supabase");
  assertContains(pruneMigration, "canonical_ticket", "restaurar alias supabase");
  assertContains(pruneMigration, "jsonb_build_array(canonical_ticket || ticket_patch)", "restaurar alias supabase");
  assertContains(pruneMigration, "insert into public.lotterynet_tickets_by_owner", "restaurar alias supabase");
  assertContains(pruneMigration, "where not (owner_row.owner_key = any(owner_keys))", "limpieza duplicados supabase");
  assertContains(pruneMigration, "where not public.lotterynet_owner_payload_ticket_matches(ticket, ticket_row)", "limpieza duplicados supabase");
  assertContains(pruneMigration, "ticket->>'serial' = ticket_row.ticket_code", "limpieza duplicados supabase");
});
