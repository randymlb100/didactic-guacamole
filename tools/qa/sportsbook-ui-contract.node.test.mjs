import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..", "..");

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

const sportsbookActivity = read("app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt");
const sportsbookPlan = read("docs/superpowers/specs/2026-05-30-deportes-apuestas-plan.md");
const getBoardFunction = read("supabase/functions/sports-get-board/index.ts");
const syncOddsFunction = read("supabase/functions/sports-sync-odds/index.ts");
const syncTeamAssetsFunction = read("supabase/functions/sports-sync-team-assets/index.ts");
const syncResultsFunction = read("supabase/functions/sports-sync-results/index.ts");
const syncResultsJob = read("tools/render/sync_sports_results.py");
const renderConfig = read("render.yaml");
const resultsMigration = read("supabase/migrations/20260718120000_sports_results_provenance.sql");
const atomicSportsMigration = read("supabase/migrations/20260719043000_atomic_sports_ticket_creation.sql");
const createTicketFunction = read("supabase/functions/create-sports-ticket/index.ts");
const getTicketsFunction = read("supabase/functions/get-sports-tickets/index.ts");
const payTicketFunction = read("supabase/functions/pay-sports-ticket/index.ts");
const settleTicketFunction = read("supabase/functions/settle-sports-ticket/index.ts");
const autoSettleFunction = read("supabase/functions/settle-sports-tickets/index.ts");
const nativeBitmapExport = read("app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt");
const thermalTicketRenderer = read("app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt");
const teamAssetsMigration = read("supabase/migrations/20260531091500_sports_team_assets.sql");
const sportsbookModels = read("app/src/main/java/com/lotterynet/pro/core/model/SportsbookModels.kt");
const sportsbookBoardStore = read("app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookBoardRemoteStore.kt");
const sportsbookTicketStore = read("app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookTicketRemoteStore.kt");
const supabaseConfig = read("supabase/config.toml");

test("sportsbook master stays isolated from business operations", () => {
  assert.match(sportsbookActivity, /UserRole\.MASTER -> listOf\("config"\)/);
  assert.match(sportsbookActivity, /if \(role == UserRole\.MASTER\) return false/);
  assert.match(sportsbookPlan, /Master no ve tickets, cobros, reportes, ventas, premios/);
  assert.match(sportsbookPlan, /Master administra el modulo, pero no ve el negocio operativo/);
});

test("sportsbook admin can sell and also has business control", () => {
  assert.match(
    sportsbookActivity,
    /UserRole\.ADMIN -> listOf\("juegos", "ticket", "cobros", "finanza", "reportes", "control"\)/,
  );
  assert.match(sportsbookPlan, /Admin puede vender igual que un cajero/);
  assert.match(sportsbookPlan, /Controles administrativos equivalentes a Loteria/);
});

test("sportsbook UI uses filters, tabs, modal sheet, and separated finance", () => {
  assert.match(sportsbookActivity, /SportsbookBoardFilterSheet/);
  assert.match(sportsbookActivity, /buildSportsbookSportFilterOptions/);
  assert.match(sportsbookActivity, /sportsbookDateFilterOptions/);
  assert.match(sportsbookActivity, /buildSportsbookMarketFilterOptions/);
  assert.match(sportsbookActivity, /title = "Fecha"/);
  assert.match(sportsbookActivity, /title = "Mercado"/);
  assert.match(sportsbookActivity, /title = "Deporte"/);
  assert.match(sportsbookActivity, /sportId: String = SportsbookBoardFilterOption\.ALL\.id/);
  assert.match(sportsbookActivity, /Filtros de cartelera/);
  assert.match(sportsbookActivity, /OperationalModalSheet\(/);
  assert.match(sportsbookActivity, /SportsbookGameSheet/);
  assert.match(sportsbookActivity, /SportsbookFinancePreview/);
  assert.match(sportsbookActivity, /Ganancia \/ perdida/);
  assert.match(sportsbookActivity, /Finanza deportiva/);
  assert.match(sportsbookPlan, /Modal bottom sheet para detalle de juego/);
  assert.match(sportsbookPlan, /Finanza usa lenguaje fintech/);
});

test("sportsbook keeps the board visible while selections are being prepared", () => {
  assert.match(sportsbookActivity, /SportsbookCompactBetSlip/);
  assert.match(sportsbookActivity, /Boleto en preparación/);
  assert.match(sportsbookActivity, /onOpenTicket = \{ selectedTab = "ticket" \}/);
  assert.match(sportsbookActivity, /selections = selections/);
});

test("sportsbook filters stay compact and use Material 3 filter chips", () => {
  assert.match(sportsbookActivity, /LazyRow\(/);
  assert.match(sportsbookActivity, /FilterChip\(/);
  assert.match(sportsbookActivity, /SportsbookActiveFilterChip/);
  assert.match(sportsbookActivity, /label = "Mercado"/);
  assert.match(sportsbookActivity, /label = "Fecha"/);
});

test("sportsbook cashier can pick odds fast and print last sale from sports ticket", () => {
  assert.match(sportsbookActivity, /SportsbookInlineOddButton/);
  assert.match(sportsbookActivity, /buildSportsbookSelection\(game, market, odd\)/);
  assert.match(sportsbookActivity, /SportsbookLastSalePanel/);
  assert.match(sportsbookActivity, /TicketPrintMark\.ORIGINAL/);
  assert.match(sportsbookActivity, /Guardado en finanza deportiva separada/);
  assert.doesNotMatch(sportsbookActivity, /lotterynet_tickets|create-ticket-v2/);
  assert.match(sportsbookActivity, /sportsbookTicketValidationMessage/);
  assert.match(sportsbookActivity, /tone = ActionTone\.Secondary/);
});

test("sportsbook board filters are contract-covered in Kotlin tests", () => {
  const testSource = read("app/src/test/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivityContractsTest.kt");
  assert.match(testSource, /sportsbook board filters by league and status/);
  assert.match(testSource, /buildSportsbookLeagueFilterOptions/);
  assert.match(testSource, /filterSportsbookBoardGames/);
});

test("sportsbook server endpoints keep Android away from direct provider calls", () => {
  assert.match(getBoardFunction, /\.from\("sports_events"\)/);
  assert.match(getBoardFunction, /source: "sports_events"/);
  assert.doesNotMatch(getBoardFunction, /ODDS_API_KEY/);
  assert.match(syncOddsFunction, /Deno\.env\.get\("ODDS_API_KEY"\)/);
  assert.match(syncOddsFunction, /sharedSecretMatches\(req\)/);
  assert.match(syncOddsFunction, /adminJwtMatches\(req, body\)/);
});

test("sportsbook team logos are cached by server instead of fetched by Android", () => {
  assert.match(teamAssetsMigration, /create table if not exists public\.sports_team_assets/);
  assert.match(teamAssetsMigration, /alter table public\.sports_team_assets enable row level security/);
  assert.match(syncTeamAssetsFunction, /Deno\.env\.get\("THESPORTSDB_API_KEY"\)/);
  assert.match(syncTeamAssetsFunction, /sharedSecretMatches\(req\)/);
  assert.match(syncTeamAssetsFunction, /adminJwtMatches\(req, body\)/);
  assert.match(syncTeamAssetsFunction, /searchteams\.php/);
  assert.match(syncTeamAssetsFunction, /\.from\("sports_team_assets"\)/);
  assert.match(getBoardFunction, /\.from\("sports_team_assets"\)/);
  assert.match(getBoardFunction, /homeTeamLogoUrl/);
  assert.match(getBoardFunction, /awayTeamLogoUrl/);
  assert.doesNotMatch(getBoardFunction, /THESPORTSDB_API_KEY|searchteams\.php/);
  assert.match(sportsbookModels, /homeTeamLogoUrl: String\? = null/);
  assert.match(sportsbookModels, /awayTeamLogoUrl: String\? = null/);
  assert.match(sportsbookBoardStore, /optString\("homeTeamLogoUrl"\)/);
  assert.match(sportsbookActivity, /AsyncImage\(/);
});

test("sportsbook logo integration tolerates provider aliases and league-name differences", () => {
  assert.match(getBoardFunction, /source\.strTeamAlternate/);
  assert.match(getBoardFunction, /source\.strTeamShort/);
  assert.match(getBoardFunction, /sportTeamLookupKey/);
  assert.match(getBoardFunction, /assetUrlBySportTeam/);
  assert.match(getBoardFunction, /ambiguousSportTeams/);
  assert.match(getBoardFunction, /normalizeTeamName\(leagueTitle\)/);
});

test("create sports ticket validates sale before writing", () => {
  assert.match(createTicketFunction, /authenticatedUser\(req\)/);
  assert.match(createTicketFunction, /canRoleSell\(actorRole\)/);
  assert.match(createTicketFunction, /metadataMatchesActor\(auth\.metadata, actorKey, adminKey, cashierKey\)/);
  assert.match(createTicketFunction, /featureEnabledFor\(actorRole, actorKey, adminKey, cashierKey\)/);
  assert.match(createTicketFunction, /existingTicket\(clientRequestId\)/);
  assert.match(createTicketFunction, /validateResolvedOdds\(odds, maxOddsAgeSeconds\)/);
  assert.match(createTicketFunction, /validateLimits\(/);
  assert.match(createTicketFunction, /max_selection_stake/);
  assert.match(createTicketFunction, /enabled_markets/);
  assert.match(createTicketFunction, /metadataValues\.length === 0\) return false/);
  assert.match(createTicketFunction, /eventExposure\(/);
  assert.match(createTicketFunction, /max_event_exposure/);
  assert.match(createTicketFunction, /selectedEventMarkets/);
  assert.match(createTicketFunction, /Solo se permite una seleccion por mercado del mismo juego/);
  assert.match(createTicketFunction, /selectedOddsIds/);
});

test("sportsbook does not sell markets without official settlement scores", () => {
  assert.match(createTicketFunction, /new Set\(\["moneyline", "runline", "spread", "total"\]\)/);
  assert.match(syncOddsFunction, /new Set\(\["moneyline", "runline", "spread", "total"\]\)/);
  assert.doesNotMatch(getBoardFunction, /first_half|first_five/);
});

test("sportsbook master config saves and sells by selected business scope", () => {
  const getMasterConfig = read("supabase/functions/get-master-config/index.ts");
  const updateMasterConfig = read("supabase/functions/update-master-config/index.ts");

  assert.match(getMasterConfig, /sportsbook:\(global\|actor:/);
  assert.match(updateMasterConfig, /sportsbook:\(global\|actor:/);
  assert.match(createTicketFunction, /\.from\("lotterynet_master_state"\)/);
  assert.match(createTicketFunction, /\.eq\("config_key", "sportsbook:global"\)/);
  assert.match(createTicketFunction, /allowedActorKeys/);
  assert.match(createTicketFunction, /cashierAdminKeys/);
  assert.match(sportsbookActivity, /SportsbookAdminDropdown/);
  assert.match(sportsbookActivity, /withAccountAccess/);
  assert.match(sportsbookActivity, /withCashierAdminAccess/);
});

test("create sports ticket freezes odds and keeps sports finance separate", () => {
  assert.match(createTicketFunction, /\.from\("sports_tickets"\)/);
  assert.match(createTicketFunction, /\.from\("sports_ticket_legs"\)/);
  assert.match(createTicketFunction, /odds_locked_at/);
  assert.match(createTicketFunction, /potential_payout/);
  assert.match(createTicketFunction, /sports_audit_log/);
  assert.doesNotMatch(createTicketFunction, /TicketRecord|lotterynet_tickets|create-ticket-v2/);
});

test("android sportsbook sale flow uses server-first ticket creation", () => {
  assert.match(sportsbookActivity, /onOddSelected/);
  assert.match(sportsbookActivity, /SportsbookTicketPreview\(/);
  assert.match(sportsbookActivity, /onCreateTicket\(draft\)/);
  assert.match(sportsbookModels, /val oddsId: String = ""/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"create-sports-ticket"/);
  assert.match(sportsbookTicketStore, /clientRequestId/);
  assert.match(sportsbookTicketStore, /bearerTokenProvider/);
  assert.match(sportsbookTicketStore, /freshBearerToken\(session\)/);
  assert.match(sportsbookActivity, /SportsbookTicketRemoteStore\(\s*bearerTokenProvider = \{ sessionTokenProvider\.freshAccessToken\(\) \},\s*\)/s);
  assert.match(supabaseConfig, /\[functions\.create-sports-ticket\]\s+verify_jwt = true/s);
  assert.doesNotMatch(sportsbookTicketStore, /lotterynet_tickets|create-ticket-v2/);
});

test("sportsbook tickets and finance read from sports tables only", () => {
  assert.match(getTicketsFunction, /authenticatedUser\(req\)/);
  assert.match(getTicketsFunction, /\.from\("sports_tickets"\)/);
  assert.match(getTicketsFunction, /sports_ticket_legs/);
  assert.match(getTicketsFunction, /summarize\(tickets\)/);
  assert.match(
    sportsbookActivity,
    /SportsbookCollectionPreview\(\s*tickets = ticketSnapshot\.tickets,\s*ticketStatus = ticketStatus,/s,
  );
  assert.match(sportsbookActivity, /SportsbookFinancePreview\(ticketSnapshot\.summary, ticketStatus\)/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"get-sports-tickets"/);
  assert.match(supabaseConfig, /\[functions\.get-sports-tickets\]\s+verify_jwt = true/s);
  assert.doesNotMatch(getTicketsFunction, /\.from\("tickets"\)|lotterynet_tickets|create-ticket-v2/);
});

test("sportsbook official and thermal ticket templates exist", () => {
  assert.match(nativeBitmapExport, /fun renderSportsbookTicketBitmap\(/);
  assert.match(nativeBitmapExport, /APUESTA DEPORTIVA/);
  assert.match(nativeBitmapExport, /PAGO POSIBLE/);
  assert.match(thermalTicketRenderer, /fun renderSportsbookTicket\(/);
  assert.match(thermalTicketRenderer, /DEPORTE/);
  assert.match(thermalTicketRenderer, /PAGO POSIBLE/);
  assert.match(sportsbookActivity, /renderSportsbookTicketBitmap/);
  assert.match(sportsbookActivity, /renderSportsbookTicket/);
  assert.match(sportsbookActivity, /Icons\.Rounded\.Whatsapp/);
});

test("sportsbook payout and settlement stay on sports tables", () => {
  assert.match(payTicketFunction, /\.from\("sports_tickets"\)/);
  assert.match(payTicketFunction, /status.*won/s);
  assert.match(payTicketFunction, /pay_sports_ticket_atomic/);
  assert.match(payTicketFunction, /alreadyPaid/);
  assert.match(atomicSportsMigration, /insert into public\.sports_settlements/);
  assert.match(settleTicketFunction, /\.from\("sports_tickets"\)/);
  assert.match(settleTicketFunction, /settle_sports_ticket_atomic/);
  assert.match(autoSettleFunction, /status: "void"/);
  assert.match(autoSettleFunction, /\.eq\("status", "pending"\)/);
  assert.match(atomicSportsMigration, /where id = p_ticket_id and status = 'pending'/);
  assert.match(settleTicketFunction, /alreadySettled/);
  assert.match(atomicSportsMigration, /create or replace function public\.settle_sports_ticket_atomic/);
  assert.match(atomicSportsMigration, /update public\.sports_ticket_legs/);
  assert.match(atomicSportsMigration, /insert into public\.sports_settlements/);
  assert.match(payTicketFunction, /rpc\("pay-sports-ticket-atomic|rpc\("pay_sports_ticket_atomic/);
  assert.match(atomicSportsMigration, /create or replace function public\.pay_sports_ticket_atomic/);
  assert.match(atomicSportsMigration, /where id = p_ticket_id and status = 'won'/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"pay-sports-ticket"/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"settle-sports-ticket"/);
  assert.match(sportsbookActivity, /onPayTicket/);
  assert.match(sportsbookActivity, /Pagar/);
  assert.match(supabaseConfig, /\[functions\.pay-sports-ticket\]\s+verify_jwt = true/s);
  assert.match(supabaseConfig, /\[functions\.settle-sports-ticket\]\s+verify_jwt = true/s);
  assert.doesNotMatch(payTicketFunction, /lotterynet_tickets|create-ticket-v2|void-ticket/);
  assert.doesNotMatch(settleTicketFunction, /lotterynet_tickets|create-ticket-v2|void-ticket/);
  assert.match(payTicketFunction, /metadataValues\.length === 0\) return false/);
  assert.match(settleTicketFunction, /metadataValues\.length === 0\) return false/);
  assert.match(getTicketsFunction, /metadataValues\.length === 0\) return false/);
});

test("sportsbook sale never leaves a ticket without legs when leg insert fails", () => {
  assert.match(createTicketFunction, /rpc\("create_sports_ticket_atomic"/);
  assert.match(createTicketFunction, /p_ticket: ticket/);
  assert.match(createTicketFunction, /p_legs: legs/);
  assert.match(createTicketFunction, /p_max_event_exposure: maxEventExposure/);
  assert.match(atomicSportsMigration, /create or replace function public\.create_sports_ticket_atomic/);
  assert.match(atomicSportsMigration, /sports_ticket_legs/);
  assert.match(atomicSportsMigration, /pg_advisory_xact_lock/);
});

test("sportsbook exposure counts one ticket once per event", () => {
  assert.match(createTicketFunction, /const seenTicketIds = new Set<string>\(\)/);
  assert.match(createTicketFunction, /if \(ticketId && seenTicketIds\.has\(ticketId\)\) return total/);
  assert.match(createTicketFunction, /return total \+ number\(ticket\.stake\)/);
});

test("sportsbook preserves provider odds timestamp when locking a leg", () => {
  assert.match(createTicketFunction, /odds_locked_at: clean\(odds\.last_updated\) \|\| new Date\(\)\.toISOString\(\)/);
});

test("sportsbook open state is case insensitive in Android", () => {
  assert.match(sportsbookModels, /event\.status\.trim\(\)\.lowercase\(Locale\.US\) == "open"/);
  assert.match(sportsbookModels, /it\.status\.trim\(\)\.lowercase\(Locale\.US\) == "open"/);
});

test("sports results use the official provider endpoint and persist provenance", () => {
  assert.match(syncResultsFunction, /events\/\$\{encodeURIComponent\(providerEventId\)\}\/results/);
  assert.match(syncResultsFunction, /X-API-Key/);
  assert.match(syncResultsFunction, /result_source: "odds-api\.net"/);
  assert.match(syncResultsFunction, /result_payload: result/);
  assert.match(syncResultsFunction, /result_updated_at:/);
  assert.match(syncResultsFunction, /status: "final"/);
  assert.match(syncResultsFunction, /status: "cancelled"/);
  assert.match(syncResultsFunction, /sports_events/);
  assert.match(syncResultsFunction, /settle-sports-tickets/);
  assert.match(syncResultsFunction, /settlement requested/);
  assert.doesNotMatch(syncResultsFunction, /lotterynet_tickets|pick_/i);
});

test("sports results cron is separate, opt-in, and isolated", () => {
  assert.match(renderConfig, /name: lotterynet-sports-results-sync/);
  assert.match(renderConfig, /startCommand: python tools\/render\/sync_sports_results\.py/);
  assert.match(renderConfig, /SPORTS_RESULTS_SYNC_ENABLED/);
  assert.match(syncResultsJob, /SPORTS_RESULTS_SYNC_ENABLED/);
  assert.match(syncResultsJob, /sports-sync-results/);
  assert.doesNotMatch(syncResultsJob, /lotterynet_tickets|pick_/i);
  assert.match(resultsMigration, /result_payload jsonb/);
  assert.match(resultsMigration, /result_updated_at timestamptz/);
});
