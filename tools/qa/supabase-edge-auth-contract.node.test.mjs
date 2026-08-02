import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";

const root = resolve(import.meta.dirname, "..", "..");

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

function functionConfig(source, name) {
  const match = new RegExp(`\\[functions\\.${name}\\][\\s\\S]*?verify_jwt = (true|false)`).exec(source);
  return match?.[1] ?? "";
}

test("critical admin Edge Functions require platform JWT verification", () => {
  const config = read("supabase/config.toml");

  assert.equal(functionConfig(config, "update-master-config"), "true");
  assert.equal(functionConfig(config, "change-user-password"), "true");
  assert.equal(functionConfig(config, "get-results-v2"), "true");
});

test("get-ticket-delta requires JWT and owner authorization", () => {
  const config = read("supabase/config.toml");
  const source = read("supabase/functions/get-ticket-delta/index.ts");

  assert.equal(functionConfig(config, "get-ticket-delta"), "true");
  assert.match(source, /authenticatedActor\(req\)/);
  assert.match(source, /canAccessOwner\(auth\.actor, ownerKey\)/);
  assert.match(source, /No tiene permiso para leer este owner/);
  assert.match(source, /includeItems\s*=\s*!bool\(body\.metadataOnly\) && body\.includeItems !== false/);
  assert.doesNotMatch(source, /\\.select\\("\\*"\\)/);
});

test("get-ticket-list keeps only the anonymous updated-at compatibility path", () => {
  const config = read("supabase/config.toml");
  const source = read("supabase/functions/get-ticket-list/index.ts");
  const ticketReadMigration = read("supabase/migrations/20260606182500_close_tickets_public_read.sql");

  assert.equal(functionConfig(config, "get-ticket-list"), "false");
  assert.match(config, /legacy APK compatibility/);
  assert.match(source, /if \(action === "updated-at"\)/);
  assert.match(source, /UPDATED_AT_STAMP_CACHE_TTL_MS/);
  assert.match(source, /safeSnapshotUpdatedAtForOwner\(admin, ownerKey, \{ useCache: true \}\)/);
  assert.match(source, /!bool\(body\.includeOfficialStamp\)/);
  assert.match(source, /degraded: snapshotStamp\.degraded/);
  assert.match(source, /const auth = await authenticatedActor\(req\)/);
  assert.match(source, /canUseTicketList\(auth\.actor, ownerKey\)/);
  assert.match(source, /actor\.role === "master"/);
  assert.match(source, /filterTicketsForActor/);
  assert.match(source, /if \(action === "upsert"\)/);
  assert.match(source, /ownerUpdatedAtStampCache\.set/);

  assert.match(ticketReadMigration, /revoke select on table public\.tickets from anon/);
  assert.match(ticketReadMigration, /revoke select on table public\.tickets from authenticated/);
  assert.match(ticketReadMigration, /drop policy if exists "Enable read access for all users"/);
  assert.match(ticketReadMigration, /get-ticket-list keeps its legacy anonymous updated-at compatibility path/);
});

test("get-ticket-list keeps full snapshots when no explicit ticket limit is requested", () => {
  const source = read("supabase/functions/get-ticket-list/index.ts");

  assert.match(source, /function requestedTicketLimit\(body: JsonMap, dateRange: DateRangeFilter \| null, action: string\): number/);
  assert.match(source, /function limitTickets\(tickets: unknown\[], limit: number\): unknown\[\]/);
  assert.match(source, /const dayKey = isoDayKey\(body\.dayKey \?\? body\.day \?\? body\.dateKey\)/);
  assert.match(source, /if \(!from && !to && dayKey\) return \{ from: dayKey, to: dayKey \}/);
  assert.match(source, /const requestedLimit = requestedTicketLimit\(body, dateRange, action\)/);
  assert.match(source, /limitTickets\(\s*filterTicketsForActor/);
  assert.doesNotMatch(source, /const requestedLimit = positiveInt\(body\.limit, dateRange \? 700 : 150, dateRange \? 1000 : 300\)/);
  assert.doesNotMatch(source, /filterTicketsForActor\([\s\S]*?\)\.slice\(0, requestedLimit\)/);
});

test("get-ticket-list scopes owner visibility by draw date aliases when a date range is requested", () => {
  const source = read("supabase/functions/get-ticket-list/index.ts");

  assert.match(source, /function dateRangeAliases\(range: DateRangeFilter \| null\): \{ isoDays: string\[\]; legacyDays: string\[\] \}/);
  assert.match(source, /const dateClauses = \[/);
  assert.match(source, /draw_date_real\.eq\.\$\{day\}/);
  assert.match(source, /legacy_day_key\.eq\.\$\{day\}/);
  assert.match(source, /const dateFilter = dateClauses\.length > 0 \? dateClauses\.join\(","\) : "";?/);
  assert.match(source, /\.or\(dateFilter\)/);
  assert.match(source, /\.gte\("server_created_at", since\)/);
  assert.match(source, /ticketInDateRange\(ticket, dateRange\)/);
  assert.doesNotMatch(source, /\.lt\("server_created_at", serverRange\.to\)/);
});

test("get-ticket-list legacy snapshot upsert fails closed before database writes", () => {
  const source = read("supabase/functions/get-ticket-list/index.ts");

  const earlyUpsertGate = source.slice(
    source.indexOf('if (action === "upsert")'),
    source.indexOf("const boundedFetchRange"),
  );
  assert.match(earlyUpsertGate, /deferred:\s*true/);
  assert.match(earlyUpsertGate, /Sincronizacion de historial pausada temporalmente/);
  assert.match(earlyUpsertGate, /status:\s*503/);
  assert.match(earlyUpsertGate, /"Retry-After":\s*"120"/);
  assert.doesNotMatch(earlyUpsertGate, /\.from\(|\.rpc\(/);
});

test("get-ticket-list can rebuild pruned snapshots from more than the old 150 official ticket fallback", () => {
  const source = read("supabase/functions/get-ticket-list/index.ts");

  assert.match(source, /function officialTicketQueryLimit\(limit: number, dateRange: DateRangeFilter \| null\): number/);
  assert.match(source, /if \(limit > 0\) return positiveInt\(limit, dateRange \? 700 : 150, dateRange \? 1000 : 300\)/);
  assert.match(source, /return dateRange \? 1000 : 1500/);
  assert.match(source, /const rowLimit = officialTicketQueryLimit\(limit, dateRange\)/);
  assert.doesNotMatch(source, /const rowLimit = positiveInt\(limit, dateRange \? 700 : 150, dateRange \? 1000 : 300\)/);
});

test("ticket hydration can process pending prize jobs before returning live data", () => {
  const ticketList = read("supabase/functions/get-ticket-list/index.ts");
  const ticketDelta = read("supabase/functions/get-ticket-delta/index.ts");
  const androidStore = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt");
  const smoke = read("tools/qa/real-flow-smoke.mjs");

  assert.match(ticketList, /processPendingPrizes\(admin, body\)/);
  assert.match(ticketList, /result_reconcile_jobs/);
  assert.match(ticketList, /\.eq\("status", "pending"\)/);
  assert.match(ticketList, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.match(ticketList, /p_job_limit:\s*4/);
  assert.match(ticketList, /p_ticket_limit:\s*150/);
  assert.match(ticketList, /prizeReconcile/);
  assert.doesNotMatch(ticketList, /processPendingPrizes === false/);
  assert.match(ticketList, /if \(days\.length === 0\) return \[\];/);

  assert.match(ticketDelta, /processPendingPrizes\(body\)/);
  assert.match(ticketDelta, /result_reconcile_jobs/);
  assert.match(ticketDelta, /\.eq\("status", "pending"\)/);
  assert.match(ticketDelta, /lotterynet_process_result_reconcile_jobs_for_day/);
  assert.match(ticketDelta, /p_job_limit:\s*4/);
  assert.match(ticketDelta, /p_ticket_limit:\s*150/);
  assert.match(ticketDelta, /prizeReconcile/);
  assert.doesNotMatch(ticketDelta, /processPendingPrizes === false/);
  assert.match(ticketDelta, /if \(days\.length === 0\) return \[\];/);

  assert.match(androidStore, /"processPendingPrizes"/);
  assert.match(androidStore, /currentPrizeProcessDays\(\)/);
  assert.match(androidStore, /ticketRemoteDateOffset\(0\)/);
  assert.match(androidStore, /ticketRemoteDateOffset\(-1\)/);

  assert.match(smoke, /processPendingPrizes:\s*true/);
  assert.match(smoke, /delta procesa premios pendientes antes de responder/);
});

test("NativeTicketRemoteStore keeps Android reads inside the recent authoritative window", () => {
  const androidStore = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt");

  assert.match(androidStore, /private const val RECENT_AUTHORITATIVE_TICKET_LIMIT = 1000/);
  assert.match(androidStore, /private fun fetchRecentAuthoritativeSnapshot\(ownerKey: String\): NativeTicketRemoteSnapshot/);
  assert.match(androidStore, /fetchSnapshot\(\s*ownerKey = ownerKey,\s*fromDate = fromDate,\s*toDate = toDate,\s*limit = RECENT_AUTHORITATIVE_TICKET_LIMIT/);
  assert.match(androidStore, /fun fetchTickets\(ownerKey: String\): List<TicketRecord> \{\s*return fetchRecentAuthoritativeSnapshot\(ownerKey\)\.tickets\s*\}/);
  assert.match(androidStore, /val existingDeletedIds = fetchRecentAuthoritativeSnapshot\(key\)\.deletedIds/);
  assert.doesNotMatch(androidStore, /fun fetchTickets\(ownerKey: String\): List<TicketRecord> \{\s*return fetchSnapshot\(ownerKey\)\.tickets\s*\}/);
});

test("update-master-config authorizes by JWT app_metadata and scoped config keys", () => {
  const source = read("supabase/functions/update-master-config/index.ts");
  const shared = read("supabase/functions/_shared/lotterynet-admin.ts");

  assert.match(source, /authenticatedActor\(req, \["admin", "master"\]\)/);
  assert.match(source, /canWriteMasterConfig\(auth\.actor, key\)/);
  assert.doesNotMatch(source, /body\.actorRole|requireAdminRole|requireSharedSecret/);
  assert.match(shared, /data\.user\.app_metadata/);
  assert.match(shared, /masterConfigScope/);
  assert.match(shared, /scope\.kind === "admin" && canAccessOwner\(actor, scope\.ownerKey\)/);
});

test("change-user-password ignores client actor fields and limits admin to its network", () => {
  const source = read("supabase/functions/change-user-password/index.ts");

  assert.match(source, /authenticatedActor\(req, \["admin", "master"\]\)/);
  assert.match(source, /if \(actor\.role === "master"\) return true/);
  assert.match(source, /if \(actor\.role !== "admin"\) return false/);
  assert.match(source, /canAccessOwner\(actor, clean\(target\.id\)\)/);
  assert.doesNotMatch(source, /body\.actorRole|body\.actorId|body\.actorUser|actorRole/);
});
