import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import {
  authenticatedActor,
  canonicalOwnerScope,
  canAccessOwner,
  sameText,
  validIdentityKey,
  type AuthenticatedActor,
} from "../_shared/lotterynet-admin.ts";
import { captureEdgeError } from "../_shared/sentry-edge.ts";
import { redisGetJson, redisSetJson } from "../_shared/upstash-redis.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type JsonMap = Record<string, unknown>;
type SupabaseAdminClient = ReturnType<typeof createClient<any, "public", any>>;
const MAX_PRIZE_PROCESS_DAYS = 2;
const UPDATED_AT_STAMP_CACHE_TTL_MS = 5_000;
const OWNER_SNAPSHOT_CACHE_TTL_MS = 30_000;
const ownerUpdatedAtStampCache = new Map<string, { updatedAt: string | null; cachedAtMs: number }>();
const ownerSnapshotPayloadCache = new Map<string, { updatedAt: string | null; cachedAtMs: number; payload: JsonMap }>();
type DateRangeFilter = { from: string; to: string };
type CachedOwnerSnapshot = {
  updatedAt: string | null;
  payload: JsonMap;
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body ?? {}), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}


function number(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function positiveInt(value: unknown, fallback: number, max: number): number {
  const parsed = Math.trunc(Number(value));
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, max);
}

function requestedTicketLimit(body: JsonMap, dateRange: DateRangeFilter | null, action: string): number {
  const explicitLimit = clean(body.limit);
  if (explicitLimit) return positiveInt(body.limit, dateRange ? 700 : 150, dateRange ? 1000 : 300);
  if (action === "summary") return dateRange ? 700 : 300;
  return 0;
}

function limitTickets(tickets: unknown[], limit: number): unknown[] {
  return limit > 0 ? tickets.slice(0, limit) : tickets;
}

function officialTicketQueryLimit(limit: number, dateRange: DateRangeFilter | null): number {
  if (limit > 0) return positiveInt(limit, dateRange ? 700 : 150, dateRange ? 1000 : 300);
  return dateRange ? 1000 : 1500;
}

function bool(value: unknown): boolean {
  if (value === true) return true;
  const raw = clean(value).toLowerCase();
  return raw === "true" || raw === "1" || raw === "yes";
}

function normalizeDayKey(value: unknown): string {
  const raw = clean(value);
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) return raw;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-");
    return `${day}-${month}-${year}`;
  }
  return "";
}

function prizeDaysFromBody(body: JsonMap): string[] {
  const values = Array.isArray(body.processPrizeDays)
    ? body.processPrizeDays
    : Array.isArray(body.processPrizesForDays)
      ? body.processPrizesForDays
      : [body.processPrizeDay ?? body.dayKey ?? body.date];
  const days = new Set<string>();
  for (const value of values) {
    const day = normalizeDayKey(value);
    if (day) days.add(day);
    if (days.size >= MAX_PRIZE_PROCESS_DAYS) break;
  }
  return Array.from(days);
}

async function processPendingPrizes(admin: SupabaseAdminClient, body: JsonMap): Promise<JsonMap[]> {
  // The client sends processPendingPrizes=false together with explicit prize days.
  // Treat the requested days as authoritative so prize jobs still get processed.
  const days = prizeDaysFromBody(body);
  if (days.length === 0) return [];
  const { data: pendingDays, error: pendingError } = await admin
    .from("result_reconcile_jobs")
    .select("result_day_key")
    .eq("status", "pending")
    .in("result_day_key", days);
  if (pendingError) {
    console.warn("get-ticket-list prize precheck failed", { message: pendingError.message, days });
  }
  const daysToProcess = Array.from(new Set((Array.isArray(pendingDays) ? pendingDays : [])
    .map((row) => clean((row as JsonMap).result_day_key))
    .filter(Boolean)));
  if (daysToProcess.length === 0) return [];
  const results: JsonMap[] = [];
  for (const day of daysToProcess) {
    const { data, error } = await admin.rpc("lotterynet_process_result_reconcile_jobs_for_day", {
      p_result_day_key: day,
      p_job_limit: 4,
      p_ticket_limit: 150,
    });
    results.push({ day, ok: !error, data: data ?? null, error: error?.message ?? null });
  }
  return results;
}

function canUseTicketList(actor: AuthenticatedActor, ownerKey: string): boolean {
  if (!["master", "admin", "supervisor", "cajero"].includes(actor.role)) return false;
  if (actor.role === "master") {
    return actor.ownerKeys.some((candidate) => sameText(candidate, ownerKey));
  }
  return canAccessOwner(actor, ownerKey);
}

function ticketBelongsToActor(ticket: unknown, actor: AuthenticatedActor): boolean {
  if (actor.role !== "cajero") return true;
  const row = ticketRow(ticket);
  const candidates = [
    row.cajeroId,
    row.cajeroUser,
    row.cashierKey,
    row.cashier_key,
    row.sellerId,
    row.sellerUser,
    row.vendedorId,
    row.vendedorUser,
    row.vendedorNombre,
  ].map(clean).filter(Boolean);
  return candidates.some((candidate) =>
    actor.identityKeys.some((identity) => sameText(candidate, identity))
  );
}

function filterTicketsForActor(tickets: unknown[], actor: AuthenticatedActor): unknown[] {
  return tickets.filter((ticket) => ticketBelongsToActor(ticket, actor));
}

function epoch(value: unknown): number {
  const parsed = Date.parse(clean(value));
  return Number.isFinite(parsed) ? parsed : Date.now();
}

function statusForApp(value: unknown): string {
  const raw = clean(value).toUpperCase();
  if (raw === "VALIDO" || raw === "VALID" || raw === "ACTIVE") return "active";
  if (raw === "ANULADO" || raw === "VOIDED") return "voided";
  if (raw === "INVALIDADO" || raw === "INVALID") return "invalid";
  if (raw === "PAGADO" || raw === "PAID") return "paid";
  if (raw === "GANADOR" || raw === "WINNER") return "winner";
  if (raw === "BORRADO" || raw === "DELETED") return "deleted";
  return clean(value) || "active";
}

function dayLabel(ms: number): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(ms)).replace(/\//g, "-");
}

function dominicanIsoDay(ms: number): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(ms));
  const byType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${byType.year}-${byType.month}-${byType.day}`;
}

function isoDayKey(value: unknown): string {
  const raw = clean(value);
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw;
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) {
    const [day, month, year] = raw.split("-");
    return `${year}-${month}-${day}`;
  }
  const firstTen = raw.slice(0, 10);
  if (/^\d{4}-\d{2}-\d{2}$/.test(firstTen)) return firstTen;
  return "";
}

function isoDayToLabel(value: string): string {
  const day = isoDayKey(value);
  if (!day) return "";
  const [year, month, date] = day.split("-");
  return `${date}-${month}-${year}`;
}

function isoDaySequence(from: string, to: string): string[] {
  const start = Date.parse(`${from}T00:00:00.000Z`);
  const end = Date.parse(`${to}T00:00:00.000Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return [];
  const days: string[] = [];
  for (let cursor = start; cursor <= end; cursor += 24 * 60 * 60 * 1000) {
    days.push(new Date(cursor).toISOString().slice(0, 10));
  }
  return days;
}

function dateRangeAliases(range: DateRangeFilter | null): { isoDays: string[]; legacyDays: string[] } {
  if (!range) return { isoDays: [], legacyDays: [] };
  const isoDays = isoDaySequence(range.from, range.to);
  const legacyDays = isoDays.map(isoDayToLabel).filter(Boolean);
  return {
    isoDays: Array.from(new Set(isoDays)),
    legacyDays: Array.from(new Set(legacyDays)),
  };
}

function ticketDrawDayIso(ticket: unknown): string {
  const row = ticketRow(ticket);
  return isoDayKey(row.drawDateKey) ||
    isoDayKey(row.drawDate) ||
    isoDayKey(row.dayKey) ||
    isoDayKey(row.draw_date_real) ||
    isoDayKey(row.draw_date) ||
    isoDayKey(row.legacy_day_key) ||
    dominicanIsoDay(epoch(row.server_created_at ?? row.created_at ?? row.createdAtIso ?? row.createdAtMs));
}

function dateRangeFromBody(body: JsonMap): DateRangeFilter | null {
  const from = isoDayKey(body.fromDate ?? body.fromDay ?? body.from ?? body.dateFrom);
  const to = isoDayKey(body.toDate ?? body.toDay ?? body.to ?? body.dateTo);
  const dayKey = isoDayKey(body.dayKey ?? body.day ?? body.dateKey);
  if (!from && !to && dayKey) return { from: dayKey, to: dayKey };
  if (!from || !to) return null;
  return from <= to ? { from, to } : { from: to, to: from };
}

function ticketInDateRange(ticket: unknown, range: DateRangeFilter | null): boolean {
  if (!range) return true;
  const day = ticketDrawDayIso(ticket);
  return day >= range.from && day <= range.to;
}

function expandedServerCreatedRange(range: DateRangeFilter | null): DateRangeFilter | null {
  if (!range) return null;
  const fromMs = Date.parse(`${range.from}T00:00:00.000Z`) - 24 * 60 * 60 * 1000;
  const toMs = Date.parse(`${range.to}T00:00:00.000Z`) + 2 * 24 * 60 * 60 * 1000;
  return {
    from: new Date(fromMs).toISOString(),
    to: new Date(toMs).toISOString(),
  };
}

function timeLabel(ms: number): string {
  return new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Santo_Domingo",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  }).format(new Date(ms));
}

function payloadObject(payload: unknown): JsonMap {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return { tickets: [], deletedIds: [] };
  return payload as JsonMap;
}

function payloadTickets(payload: JsonMap): unknown[] {
  return Array.isArray(payload.tickets) ? payload.tickets : [];
}

function payloadDeletedIds(payload: JsonMap): string[] {
  const ids = new Set<string>();
  for (const key of ["deletedIds", "deletedTicketIds", "removedIds"]) {
    const value = payload[key];
    if (Array.isArray(value)) value.map(clean).filter(Boolean).forEach((id) => ids.add(id));
  }
  return Array.from(ids);
}

function ticketRow(ticket: unknown): JsonMap {
  return ticket && typeof ticket === "object" && !Array.isArray(ticket) ? ticket as JsonMap : {};
}

function canonicalJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalJson);
  if (!value || typeof value !== "object") return value;

  const source = value as JsonMap;
  return Object.keys(source)
    .sort()
    .reduce<JsonMap>((acc, key) => {
      acc[key] = canonicalJson(source[key]);
      return acc;
    }, {});
}

function ticketKey(ticket: unknown): string {
  const row = ticketRow(ticket);
  return clean(row.id || row.clientRequestId || row.client_request_id);
}

function hasDeletedStatus(ticket: unknown): boolean {
  const row = ticketRow(ticket);
  const status = clean(row.status || row.st || row.estado).toLowerCase();
  return ["deleted", "borrado", "removed"].includes(status);
}

function filterSnapshotTickets(tickets: unknown[], deletedIds: Set<string>): unknown[] {
  return tickets.filter((ticket) => {
    const key = ticketKey(ticket);
    return key && !deletedIds.has(key) && !hasDeletedStatus(ticket);
  });
}

function stableSnapshotPayload(payload: JsonMap): string {
  const deletedIds = payloadDeletedIds(payload)
    .map((id) => id.toLowerCase())
    .sort();
  const tickets = filterSnapshotTickets(payloadTickets(payload), new Set(deletedIds))
    .map((ticket) => ticketRow(ticket))
    .map(canonicalJson)
    .sort((a, b) => ticketKey(a).localeCompare(ticketKey(b)));
  return JSON.stringify({
    schemaVersion: Number(payload.schemaVersion ?? 2),
    deletedIds,
    tickets,
  });
}

function mergeTickets(snapshotTickets: unknown[], officialTickets: JsonMap[], deletedIds: Set<string>): unknown[] {
  const map = new Map<string, unknown>();
  for (const ticket of filterSnapshotTickets(snapshotTickets, deletedIds)) {
    map.set(ticketKey(ticket), ticket);
  }
  for (const ticket of officialTickets) {
    const key = ticketKey(ticket);
    const items = Array.isArray(ticket.items) ? ticket.items : [];
    const total = number(ticket.total ?? ticket.tot);
    if (!key || deletedIds.has(key) || hasDeletedStatus(ticket)) continue;
    if (items.length === 0 && total > 0 && map.has(key)) {
      const existing = ticketRow(map.get(key));
      const existingItems = Array.isArray(existing.items) ? existing.items : [];
      const existingWinningDetails = Array.isArray(existing.winningDetails) ? existing.winningDetails : [];
      const officialWinningDetails = Array.isArray(ticket.winningDetails) ? ticket.winningDetails : [];
      map.set(key, {
        ...existing,
        ...ticket,
        items: existingItems,
        winningDetails: officialWinningDetails.length > 0 ? officialWinningDetails : existingWinningDetails,
      });
      continue;
    }
    map.set(key, ticket);
  }
  return Array.from(map.values()).sort((a, b) => number((b as JsonMap).createdAtMs) - number((a as JsonMap).createdAtMs));
}

function isTerminalSnapshotTicket(ticket: unknown): boolean {
  const row = ticketRow(ticket);
  const status = clean(row.status || row.st || row.estado).toLowerCase();
  return [
    "paid",
    "pagado",
    "paid_out",
    "payout",
    "cobrado",
    "premio_pagado",
    "winner",
    "ganador",
  ].includes(status) || number(row.totalPrize ?? row.totalPremio) > 0;
}

function mergeSnapshotUploadTickets(
  snapshotTickets: unknown[],
  incomingTickets: JsonMap[],
  deletedIds: Set<string>,
): unknown[] {
  const map = new Map<string, unknown>();
  for (const ticket of filterSnapshotTickets(snapshotTickets, deletedIds)) {
    map.set(ticketKey(ticket), ticket);
  }
  for (const ticket of filterSnapshotTickets(incomingTickets, deletedIds)) {
    const key = ticketKey(ticket);
    if (!key) continue;
    const previous = map.get(key);
    if (previous && isTerminalSnapshotTicket(previous) && !bool(ticketRow(ticket).serverPrizeAuthoritative)) {
      continue;
    }
    map.set(key, ticket);
  }
  return Array.from(map.values())
    .sort((a, b) => number((b as JsonMap).createdAtMs) - number((a as JsonMap).createdAtMs));
}

function accountForKey(actor: AuthenticatedActor, key: string): JsonMap | null {
  const normalized = clean(key);
  if (!normalized) return null;
  return actor.accounts.find((account) =>
    [
      account.id,
      account.user,
      account.username,
      account.userId,
      account.authUserId,
      account.auth_user_id,
      account.legacy_id,
      account.legacy_key,
      account.legacy_admin_id,
      account.legacy_admin_user,
      account.adminId,
      account.adminUser,
      account.admin_id,
      account.admin_user,
      account.banca,
    ].some((candidate) => sameText(candidate, normalized))
  ) ?? null;
}

function displayAliasForKey(actor: AuthenticatedActor, key: string): string {
  const account = accountForKey(actor, key);
  return clean(
    account?.user ??
      account?.username ??
      account?.displayName ??
      account?.nombre ??
      account?.name,
  ) || key;
}

function appTicketFromOfficial(ticket: JsonMap, items: JsonMap[], actor: AuthenticatedActor): JsonMap {
  const createdMs = epoch(ticket.server_created_at ?? ticket.created_at);
  const appId = clean(ticket.client_request_id) || clean(ticket.id);
  const adminKey = clean(ticket.admin_key ?? ticket.adminKey);
  const cashierKey = clean(ticket.cashier_key ?? ticket.cashierKey);
  const adminAlias = displayAliasForKey(actor, adminKey);
  const sellerAlias = displayAliasForKey(actor, cashierKey);
  const sellerRole = sameText(adminKey, cashierKey) ? "admin" : "cashier";
  const drawDay = ticketDrawDayIso(ticket);
  return {
    id: appId,
    type: "lot",
    serial: clean(ticket.ticket_code) || appId,
    lots: clean(ticket.lottery_name ?? ticket.lottery_endpoint),
    lotteries: clean(ticket.lottery_name ?? ticket.lottery_endpoint),
    items: items.map((item) => ({
      type: clean(item.play_type),
      playType: clean(item.play_type),
      nums: clean(item.play_numbers),
      number: clean(item.play_numbers),
      amt: number(item.amount),
      amount: number(item.amount),
      lotId: clean(item.lottery_legacy_id ?? item.sorteo_id),
      lotName: clean(item.lottery_name ?? ticket.lottery_name),
      lotteryId: clean(item.lottery_legacy_id ?? item.sorteo_id),
      lotteryName: clean(item.lottery_name ?? ticket.lottery_name),
      secondaryLotteryId: clean(item.secondary_lottery_legacy_id),
      secondaryLotteryName: clean(item.secondary_lottery_name),
      isWinner: Boolean(item.is_winner),
      payoutAmount: number(item.payout_amount),
      hitPosition: clean(item.hit_position),
      resultNumber: clean(item.result_number),
    })),
    winningDetails: items
      .filter((item) => Boolean(item.is_winner) || number(item.payout_amount) > 0)
      .map((item) => ({
        lotteryName: clean(item.lottery_name ?? ticket.lottery_name),
        playType: clean(item.play_type),
        playedNumber: clean(item.play_numbers),
        resultNumber: clean(item.result_number),
        hitPosition: clean(item.hit_position),
        amount: number(item.amount),
        payoutAmount: number(item.payout_amount),
      })),
    subtotal: number(ticket.total_amount ?? ticket.monto),
    discount: 0,
    tot: number(ticket.total_amount ?? ticket.monto),
    total: number(ticket.total_amount ?? ticket.monto),
    totalPrize: number(ticket.payout_amount),
    adminId: adminKey,
    adminUser: adminAlias,
    cajeroId: cashierKey,
    vendedorId: cashierKey,
    vendedorRol: sellerRole,
    vendedorNombre: sellerAlias,
    saleMode: "edge",
    offlineSale: false,
    createdAtMs: createdMs,
    createdAtEpochMs: createdMs,
    updatedAt: createdMs,
    drawDateKey: drawDay,
    drawDate: drawDay,
    dayKey: drawDay,
    date: isoDayToLabel(drawDay) || dayLabel(createdMs),
    time: timeLabel(createdMs),
    securityCode: "",
    note: "",
    st: statusForApp(ticket.status ?? ticket.estado),
    status: statusForApp(ticket.status ?? ticket.estado),
  };
}

function actorOwnerLookupKeys(actor: AuthenticatedActor, ownerKey: string): string[] {
  const keys = new Set([ownerKey, ...actor.ownerKeys, ...actor.identityKeys].map(clean).filter(Boolean));
  const target = actor.accounts.find((account) =>
    [
      account.id,
      account.user,
      account.username,
      account.userId,
      account.authUserId,
      account.auth_user_id,
      account.legacy_key,
      account.legacy_admin_id,
      account.legacy_admin_user,
      account.adminId,
      account.adminUser,
      account.admin_id,
      account.admin_user,
      account.banca,
    ].some((candidate) => sameText(candidate, ownerKey))
  );
  for (const account of [actor.account, target]) {
    if (!account) continue;
    [
      account.id,
      account.user,
      account.username,
      account.userId,
      account.authUserId,
      account.auth_user_id,
      account.legacy_key,
      account.legacy_admin_id,
      account.legacy_admin_user,
      account.adminId,
      account.adminUser,
      account.admin_id,
      account.admin_user,
      account.banca,
    ].map(clean).filter(Boolean).forEach((key) => keys.add(key));
  }
  return Array.from(keys);
}

async function ownerLookupKeys(admin: SupabaseAdminClient, ownerKey: string, actor: AuthenticatedActor): Promise<string[]> {
  const keys = new Set(actorOwnerLookupKeys(actor, ownerKey));
  const { data, error } = await admin
    .from("profiles")
    .select("username,legacy_key,legacy_admin_id,legacy_admin_user")
    .or(`username.eq.${ownerKey},legacy_key.eq.${ownerKey},legacy_admin_id.eq.${ownerKey},legacy_admin_user.eq.${ownerKey}`);
  if (error) return Array.from(keys);
  if (Array.isArray(data)) {
    for (const profile of data as JsonMap[]) {
      [profile.username, profile.legacy_key, profile.legacy_admin_id, profile.legacy_admin_user]
        .map(clean)
        .filter(Boolean)
        .forEach((key) => keys.add(key));
    }
  }
  return Array.from(keys);
}

type UpdatedAtStampResult = { updatedAt: string | null; degraded: boolean };

async function snapshotUpdatedAtForOwner(
  admin: SupabaseAdminClient,
  ownerKey: string,
  options: { useCache?: boolean } = {},
): Promise<string | null> {
  const cacheKey = ownerKey.trim().toLowerCase();
  if (options.useCache && cacheKey) {
    const cached = ownerUpdatedAtStampCache.get(cacheKey);
    if (cached && Date.now() - cached.cachedAtMs <= UPDATED_AT_STAMP_CACHE_TTL_MS) {
      return cached.updatedAt;
    }
  }
  const { data: snapshot, error: snapshotError } = await admin
    .from("lotterynet_tickets_by_owner")
    .select("updated_at")
    .eq("owner_key", ownerKey)
    .maybeSingle();
  if (snapshotError) throw new Error(snapshotError.message);
  const updatedAt = clean((snapshot as JsonMap | null)?.updated_at) || null;
  if (options.useCache && cacheKey) {
    ownerUpdatedAtStampCache.set(cacheKey, { updatedAt, cachedAtMs: Date.now() });
  }
  return updatedAt;
}

async function safeSnapshotUpdatedAtForOwner(
  admin: SupabaseAdminClient,
  ownerKey: string,
  options: { useCache?: boolean } = {},
): Promise<UpdatedAtStampResult> {
  try {
    return {
      updatedAt: await snapshotUpdatedAtForOwner(admin, ownerKey, options),
      degraded: false,
    };
  } catch (error) {
    console.warn("get-ticket-list updated-at degraded", {
      ownerKey,
      message: error instanceof Error ? error.message : String(error),
    });
    return { updatedAt: null, degraded: true };
  }
}

function ownerSnapshotCacheKey(ownerKey: string, updatedAt: string | null): string {
  return `${ownerKey.trim().toLowerCase()}:${clean(updatedAt) || "none"}`;
}

async function readCachedOwnerSnapshot(
  admin: SupabaseAdminClient,
  ownerKey: string,
  options: { useCache?: boolean } = {},
): Promise<CachedOwnerSnapshot> {
  const stamp = await safeSnapshotUpdatedAtForOwner(admin, ownerKey, { useCache: true });
  const cacheKey = ownerSnapshotCacheKey(ownerKey, stamp.updatedAt);
  const now = Date.now();
  if (options.useCache) {
    const memoryCached = ownerSnapshotPayloadCache.get(cacheKey);
    if (memoryCached && now - memoryCached.cachedAtMs <= OWNER_SNAPSHOT_CACHE_TTL_MS) {
      return { updatedAt: memoryCached.updatedAt, payload: memoryCached.payload };
    }

    const redisCached = await redisGetJson<{ cachedAtMs: number; updatedAt: string | null; payload: JsonMap }>(cacheKey);
    if (
      redisCached &&
      typeof redisCached === "object" &&
      typeof redisCached.cachedAtMs === "number" &&
      now - redisCached.cachedAtMs <= OWNER_SNAPSHOT_CACHE_TTL_MS &&
      redisCached.payload &&
      typeof redisCached.payload === "object"
    ) {
      ownerSnapshotPayloadCache.set(cacheKey, redisCached);
      return { updatedAt: redisCached.updatedAt ?? stamp.updatedAt, payload: redisCached.payload };
    }
  }

  const { data, error } = await admin
    .from("lotterynet_tickets_by_owner")
    .select("payload,updated_at")
    .eq("owner_key", ownerKey)
    .maybeSingle();
  if (error) throw new Error(error.message);

  const updatedAt = clean((data as JsonMap | null)?.updated_at) || stamp.updatedAt;
  const payload = payloadObject((data as JsonMap | null)?.payload);
  const cachedValue = { cachedAtMs: Date.now(), updatedAt, payload };
  ownerSnapshotPayloadCache.set(ownerSnapshotCacheKey(ownerKey, updatedAt), cachedValue);
  if (options.useCache) {
    await redisSetJson(ownerSnapshotCacheKey(ownerKey, updatedAt), cachedValue, Math.ceil(OWNER_SNAPSHOT_CACHE_TTL_MS / 1000));
  }
  return { updatedAt, payload };
}

async function latestUpdatedAtForOwner(
  admin: SupabaseAdminClient,
  ownerKey: string,
  ownerKeys: string[],
  snapshotStamp: string | null = null,
): Promise<string | null> {
  const stamps: string[] = [];
  const latestByColumn = async (column: "admin_key" | "cashier_key", orderColumn: "server_created_at" | "updated_at"): Promise<JsonMap | null> => {
    const { data, error } = await admin
      .from("tickets")
      .select("server_created_at,updated_at")
      .in(column, ownerKeys)
      .order(orderColumn, { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error) throw new Error(error.message);
    return data as JsonMap | null;
  };

  for (const latestServerCreated of [
    await latestByColumn("admin_key", "server_created_at"),
    await latestByColumn("cashier_key", "server_created_at"),
  ]) {
    const createdStamp = clean(latestServerCreated?.server_created_at);
    const createdRowUpdatedStamp = clean(latestServerCreated?.updated_at);
    if (createdStamp) stamps.push(createdStamp);
    if (createdRowUpdatedStamp) stamps.push(createdRowUpdatedStamp);
  }

  for (const latestUpdated of [
    await latestByColumn("admin_key", "updated_at"),
    await latestByColumn("cashier_key", "updated_at"),
  ]) {
    const updatedStamp = clean(latestUpdated?.updated_at);
    const updatedRowCreatedStamp = clean(latestUpdated?.server_created_at);
    if (updatedStamp) stamps.push(updatedStamp);
    if (updatedRowCreatedStamp) stamps.push(updatedRowCreatedStamp);
  }

  if (snapshotStamp) stamps.push(snapshotStamp);

  return stamps.sort().at(-1) ?? null;
}

function chunk<T>(values: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let index = 0; index < values.length; index += size) {
    chunks.push(values.slice(index, index + size));
  }
  return chunks;
}

async function officialItemsByTicket(admin: SupabaseAdminClient, ids: string[]): Promise<Map<string, JsonMap[]>> {
  const itemsByTicket = new Map<string, JsonMap[]>();
  for (const idChunk of chunk(ids, 35)) {
    const { data, error } = await admin
      .from("ticket_items")
      .select("ticket_id,play_type,play_numbers,amount,lottery_legacy_id,lottery_name,secondary_lottery_legacy_id,secondary_lottery_name,sorteo_id,is_winner,payout_amount,hit_position")
      .in("ticket_id", idChunk)
      .range(0, 4999);
    if (error) throw new Error(error.message);
    if (!Array.isArray(data)) continue;
    for (const item of data as JsonMap[]) {
      const key = clean(item.ticket_id);
      if (!itemsByTicket.has(key)) itemsByTicket.set(key, []);
      itemsByTicket.get(key)!.push(item);
    }
  }
  return itemsByTicket;
}

async function officialTicketsForOwner(
  admin: SupabaseAdminClient,
  ownerKeys: string[],
  includeItems = true,
  snapshotTickets: unknown[] = [],
  actor: AuthenticatedActor,
  limit = 150,
  includeSnapshotLookup = false,
  dateRange: DateRangeFilter | null = null,
): Promise<JsonMap[]> {
  const serverRange = expandedServerCreatedRange(dateRange);
  const since = serverRange?.from ?? new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
  const selectColumns = "id,client_request_id,ticket_code,total_amount,monto,status,estado,payout_amount,admin_key,cashier_key,lottery_name,lottery_endpoint,draw_date_real,draw_date,legacy_day_key,server_created_at,created_at,updated_at";
  const rowLimit = officialTicketQueryLimit(limit, dateRange);
  const aliases = dateRangeAliases(dateRange);
  const dateClauses = [
    ...aliases.isoDays.map((day) => `draw_date_real.eq.${day}`),
    ...aliases.isoDays.map((day) => `draw_date.eq.${day}`),
    ...aliases.isoDays.map((day) => `legacy_day_key.eq.${day}`),
    ...aliases.legacyDays.map((day) => `draw_date.eq.${day}`),
    ...aliases.legacyDays.map((day) => `legacy_day_key.eq.${day}`),
  ];
  const dateFilter = dateClauses.length > 0 ? dateClauses.join(",") : "";
  const fetchByColumn = async (column: "admin_key" | "cashier_key"): Promise<JsonMap[]> => {
    let query = admin
      .from("tickets")
      .select(selectColumns)
      .in(column, ownerKeys)
      .is("deleted_at", null)
      .gte("server_created_at", since)
      .order("server_created_at", { ascending: false })
      .limit(rowLimit);
    if (dateFilter) {
      query = query.or(dateFilter);
    }
    const { data, error } = await query;
    if (error) throw new Error(error.message);
    return Array.isArray(data) ? (data as JsonMap[]).filter((ticket) => ticketInDateRange(ticket, dateRange)) : [];
  };
  const fetchBySnapshotKey = async (column: "client_request_id" | "ticket_code", values: string[]): Promise<JsonMap[]> => {
    const rows: JsonMap[] = [];
    for (const valueChunk of chunk(values, 35)) {
      let query = admin
        .from("tickets")
        .select(selectColumns)
        .in(column, valueChunk)
        .is("deleted_at", null)
        .gte("server_created_at", since)
        .order("server_created_at", { ascending: false })
        .limit(rowLimit);
      if (dateFilter) {
        query = query.or(dateFilter);
      }
      const { data, error } = await query;
      if (error) throw new Error(error.message);
      rows.push(...(Array.isArray(data) ? (data as JsonMap[]).filter((ticket) => ticketInDateRange(ticket, dateRange)) : []));
    }
    return rows;
  };
  const byId = new Map<string, JsonMap>();
  for (const ticket of [...await fetchByColumn("admin_key"), ...await fetchByColumn("cashier_key")]) {
    const key = clean(ticket.id);
    if (key) byId.set(key, ticket);
  }
  if (includeSnapshotLookup) {
    const snapshotClientIds = snapshotTickets
      .map((ticket) => ticketRow(ticket))
      .map((ticket) => clean(ticket.id || ticket.clientRequestId || ticket.client_request_id))
      .filter(Boolean)
      .slice(0, rowLimit);
    const snapshotSerials = snapshotTickets
      .map((ticket) => ticketRow(ticket))
      .map((ticket) => clean(ticket.serial || ticket.ticket_code || ticket.ticketCode))
      .filter(Boolean)
      .slice(0, rowLimit);
    for (const ticket of [
      ...await fetchBySnapshotKey("client_request_id", snapshotClientIds),
      ...await fetchBySnapshotKey("ticket_code", snapshotSerials),
    ]) {
      const key = clean(ticket.id);
      if (key) byId.set(key, ticket);
    }
  }
  const tickets = Array.from(byId.values())
    .sort((a, b) => clean(b.server_created_at ?? b.created_at).localeCompare(clean(a.server_created_at ?? a.created_at)))
    .slice(0, rowLimit);
  if (tickets.length === 0) return [];
  if (!includeItems) return tickets.map((ticket) => appTicketFromOfficial(ticket, [], actor));

  const ids = tickets.map((ticket: JsonMap) => clean(ticket.id)).filter(Boolean);
  const itemsByTicket = await officialItemsByTicket(admin, ids);
  return tickets.map((ticket) => appTicketFromOfficial(ticket, itemsByTicket.get(clean(ticket.id)) ?? [], actor));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { ok: false, message: "Metodo no permitido" });

  let action = "";
  let ownerKey = "";
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY");
    if (!supabaseUrl || !serviceRole) return json(500, { ok: false, message: "Servidor no configurado" });

    const admin = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
    const body = await req.json().catch(() => ({})) as JsonMap;
    action = clean(body.action || "fetch").toLowerCase();
    ownerKey = validIdentityKey(body.ownerKey ?? body.owner_key);
    if (!ownerKey) return json(400, { ok: false, message: "Owner requerido" });

    if (action === "upsert") {
      return new Response(JSON.stringify({
        ok: false,
        deferred: true,
        ownerKey,
        message: "Sincronizacion de historial pausada temporalmente. La venta permanece guardada localmente.",
      }), {
        status: 503,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
          "Retry-After": "120",
        },
      });
    }

    const boundedFetchRange = dateRangeFromBody(body);
    const boundedFetchLimit = requestedTicketLimit(body, boundedFetchRange, action);
    if (action === "fetch" && (!boundedFetchRange || boundedFetchLimit <= 0)) {
      return new Response(JSON.stringify({
        ok: false,
        degraded: true,
        deferred: true,
        ownerKey,
        message: "Historial remoto completo pausado temporalmente. Se conserva el cache local.",
      }), {
        status: 503,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
          "Retry-After": "120",
        },
      });
    }

    if (action === "updated-at") {
      const snapshotStamp = await safeSnapshotUpdatedAtForOwner(admin, ownerKey, { useCache: true });
      if (!bool(body.includeOfficialStamp)) {
        return json(200, { ok: true, ownerKey, updatedAt: snapshotStamp.updatedAt, degraded: snapshotStamp.degraded });
      }
    }

    const auth = await authenticatedActor(req);
    if (!auth.ok) return auth.response;
    if (!canUseTicketList(auth.actor, ownerKey)) {
      return json(403, { ok: false, message: "No tiene permiso para leer este owner." });
    }

    const scope = canonicalOwnerScope(auth.actor, ownerKey);
    if (!scope.canonicalOwnerKey) {
      return json(400, { ok: false, message: "Owner requerido" });
    }
    ownerKey = scope.canonicalOwnerKey;
    const ownerKeys = Array.from(new Set([
      ...scope.ownerKeys,
      ...await ownerLookupKeys(admin, ownerKey, auth.actor),
    ].map(validIdentityKey).filter(Boolean)));

    if (action === "updated-at") {
      const snapshotStamp = await snapshotUpdatedAtForOwner(admin, ownerKey, { useCache: true });
      return json(200, {
        ok: true,
        ownerKey,
        updatedAt: await latestUpdatedAtForOwner(admin, ownerKey, ownerKeys, snapshotStamp),
      });
    }

    const snapshot = await readCachedOwnerSnapshot(admin, ownerKey, { useCache: true });
    const basePayload = payloadObject(snapshot.payload);

    if (action === "upsert") {
      const incomingPayload = payloadObject(body.payload);
      const deletedIds = new Set([...payloadDeletedIds(basePayload), ...payloadDeletedIds(incomingPayload)]);
      const mergedTickets = mergeTickets(
        payloadTickets(basePayload),
        payloadTickets(incomingPayload) as JsonMap[],
        deletedIds,
      );
      const payload = {
        ...incomingPayload,
        schemaVersion: Number(incomingPayload.schemaVersion ?? basePayload.schemaVersion ?? 2),
        tickets: filterSnapshotTickets(mergedTickets, deletedIds),
        deletedIds: Array.from(deletedIds),
      };
      if (stableSnapshotPayload(payload) === stableSnapshotPayload(basePayload)) {
        return json(200, { ok: true, ownerKey, unchanged: true });
      }
      const nextUpdatedAt = new Date().toISOString();
      const { error: upsertError } = await admin
        .from("lotterynet_tickets_by_owner")
        .upsert(
          { owner_key: ownerKey, payload, updated_at: nextUpdatedAt },
          { onConflict: "owner_key" },
        );
      if (upsertError) throw new Error(upsertError.message);
      ownerUpdatedAtStampCache.set(ownerKey.trim().toLowerCase(), {
        updatedAt: nextUpdatedAt,
        cachedAtMs: Date.now(),
      });
      return json(200, { ok: true, ownerKey });
    }

    const deletedIds = new Set(payloadDeletedIds(basePayload));
    const snapshotTickets = payloadTickets(basePayload);
    const dateRange = dateRangeFromBody(body);
    const requestedLimit = requestedTicketLimit(body, dateRange, action);
    const preferSnapshot = bool(body.preferSnapshot ?? body.snapshotFirst);
    const allowSnapshotOnly = preferSnapshot && !bool(body.includeOfficialStamp);
    if (allowSnapshotOnly && (action === "fetch" || action === "summary") && snapshotTickets.length > 0) {
      const payload = {
        ...basePayload,
        schemaVersion: Number(basePayload.schemaVersion ?? 2),
        tickets: limitTickets(filterTicketsForActor(
          filterSnapshotTickets(snapshotTickets, deletedIds)
            .filter((ticket) => ticketInDateRange(ticket, dateRange))
            .sort((a, b) => number((b as JsonMap).createdAtMs) - number((a as JsonMap).createdAtMs)),
          auth.actor,
        ), requestedLimit),
        deletedIds: Array.from(deletedIds),
      };
      return json(200, {
        ok: true,
        ownerKey,
        payload,
        updatedAt: snapshot.updatedAt ?? null,
        prizeReconcile: [],
        source: "snapshot",
        completeScope: false,
      });
    }

    const prizeReconcile = action === "fetch" || action === "summary"
      ? await processPendingPrizes(admin, body)
      : [];
    const includeItems = action !== "summary" && body.includeItems !== false;
    const officialTickets = await officialTicketsForOwner(
      admin,
      ownerKeys,
      includeItems,
      snapshotTickets,
      auth.actor,
      requestedLimit,
      bool(body.includeSnapshotOfficialLookup),
      dateRange,
    );
    const payload = {
      ...basePayload,
      schemaVersion: Number(basePayload.schemaVersion ?? 2),
      tickets: limitTickets(filterTicketsForActor(
        mergeTickets(snapshotTickets, officialTickets, deletedIds)
          .filter((ticket) => ticketInDateRange(ticket, dateRange)),
        auth.actor,
      ), requestedLimit),
      deletedIds: Array.from(deletedIds),
    };
    const completeScope = requestedLimit > 0 && payload.tickets.length < requestedLimit;
    return json(200, {
      ok: true,
      ownerKey,
      payload,
      updatedAt: snapshot.updatedAt ?? null,
      prizeReconcile,
      source: "authoritative",
      completeScope,
    });
  } catch (error) {
    await captureEdgeError(error, { functionName: "get-ticket-list", operation: action, ownerKey });
    return json(400, { ok: false, message: error instanceof Error ? error.message : "No se pudo cargar tickets" });
  }
});
