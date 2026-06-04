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
const createTicketFunction = read("supabase/functions/create-sports-ticket/index.ts");
const getTicketsFunction = read("supabase/functions/get-sports-tickets/index.ts");
const payTicketFunction = read("supabase/functions/pay-sports-ticket/index.ts");
const settleTicketFunction = read("supabase/functions/settle-sports-ticket/index.ts");
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
  assert.match(sportsbookActivity, /DropdownMenu\(/);
  assert.match(sportsbookActivity, /ModalBottomSheet\(/);
  assert.match(sportsbookActivity, /SportsbookGameSheet/);
  assert.match(sportsbookActivity, /SportsbookFinancePreview/);
  assert.match(sportsbookActivity, /Ganancia \/ perdida/);
  assert.match(sportsbookPlan, /Dropdown solo para filtros/);
  assert.match(sportsbookPlan, /Modal bottom sheet para detalle de juego/);
  assert.match(sportsbookPlan, /Finanza usa lenguaje fintech/);
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

test("create sports ticket validates sale before writing", () => {
  assert.match(createTicketFunction, /authenticatedUser\(req\)/);
  assert.match(createTicketFunction, /canRoleSell\(actorRole\)/);
  assert.match(createTicketFunction, /metadataMatchesActor\(auth\.metadata, actorKey, adminKey, cashierKey\)/);
  assert.match(createTicketFunction, /featureEnabledFor\(actorRole, actorKey, adminKey, cashierKey\)/);
  assert.match(createTicketFunction, /existingTicket\(clientRequestId\)/);
  assert.match(createTicketFunction, /validateResolvedOdds\(odds, maxOddsAgeSeconds\)/);
  assert.match(createTicketFunction, /validateLimits\(stake, potentialPayout, limits\)/);
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
  assert.match(payTicketFunction, /\.from\("sports_settlements"\)/);
  assert.match(settleTicketFunction, /\.from\("sports_tickets"\)/);
  assert.match(settleTicketFunction, /nextStatus/);
  assert.match(settleTicketFunction, /\.from\("sports_ticket_legs"\)/);
  assert.match(settleTicketFunction, /\.from\("sports_settlements"\)/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"pay-sports-ticket"/);
  assert.match(sportsbookTicketStore, /invokeAuthenticated\(\s*"settle-sports-ticket"/);
  assert.match(sportsbookActivity, /onPayTicket/);
  assert.match(sportsbookActivity, /Pagar/);
  assert.match(supabaseConfig, /\[functions\.pay-sports-ticket\]\s+verify_jwt = true/s);
  assert.match(supabaseConfig, /\[functions\.settle-sports-ticket\]\s+verify_jwt = true/s);
  assert.doesNotMatch(payTicketFunction, /lotterynet_tickets|create-ticket-v2|void-ticket/);
  assert.doesNotMatch(settleTicketFunction, /lotterynet_tickets|create-ticket-v2|void-ticket/);
});
