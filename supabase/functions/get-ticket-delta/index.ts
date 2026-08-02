import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  accountIdentityKeys,
  bumpTicketDeltaResponseVersion,
  authenticatedActor,
  canAccessOwner,
  clean,
  corsHeaders,
  json,
  lower,
  sameText,
  supabaseAdmin,
  readTicketDeltaResponseVersion,
  type AuthenticatedActor,
} from "../_shared/lotterynet-admin.ts";
import { redisGetJson, redisSetJson } from "../_shared/upstash-redis.ts";

type Row = Record<string, unknown>;

const MAX_LIMIT = 300;
const MAX_PRIZE_DAYS = 2;
const TICKET_FULL_COLUMNS = [
  "id",
  "client_request_id",
  "ticket_code",
  "numero",
  "monto",
  "estado",
  "lottery_name",
  "lottery_endpoint",
  "draw_date",
  "draw_date_real",
  "total_amount",
  "status",
  "result_number",
  "payout_amount",
  "sorteo_id",
  "admin_key",
  "cashier_key",
  "supervisor_key",
  "server_created_at",
  "created_at",
  "updated_at",
  "paid_at",
  "voided_at",
  "invalidated_at",
  "deleted_at",
  "deleted_by_key",
].join(",");
const TICKET_DELTA_COLUMNS = [
  "id",
  "client_request_id",
  "ticket_code",
  "monto",
  "estado",
  "lottery_name",
  "draw_date",
  "draw_date_real",
  "total_amount",
  "status",
  "admin_key",
  "cashier_key",
  "server_created_at",
  "created_at",
  "updated_at",
  "paid_at",
  "voided_at",
  "deleted_at",
].join(",");
const ITEM_COLUMNS = [
  "id",
  "ticket_id",
  "play_numbers",
  "amount",
  "potential_payout",
  "is_winner",
  "payout_amount",
  "hit_position",
  "play_type",
  "normalized_number",
  "sorteo_id",
  "lottery_legacy_id",
  "lottery_name",
  "secondary_lottery_legacy_id",
  "secondary_lottery_name",
].join(",");
type CachedTicketDeltaResponse = {
  cachedAtMs: number;
  response: Record<string, unknown>;
};

const DELTA_RESPONSE_CACHE_TTL_MS = 15_000;
const deltaResponseMemoryCache = new Map<string, CachedTicketDeltaResponse>();
const deltaResponseInflight = new Map<string, Promise<Record<string, unknown> | null>>();

function limitFrom(value: unknown): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return 150;
  return Math.min(Math.trunc(parsed), MAX_LIMIT);
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

function prizeDaysFromBody(body: Row): string[] {
  const values = Array.isArray(body.processPrizeDays)
    ? body.processPrizeDays
    : Array.isArray(body.processPrizesForDays)
      ? body.processPrizesForDays
      : [body.processPrizeDay ?? body.dayKey ?? body.date];
  const days = new Set<string>();
  for (const value of values) {
    const day = normalizeDayKey(value);
    if (day) days.add(day);
    if (days.size >= MAX_PRIZE_DAYS) break;
  }
  return Array.from(days);
}

async function processPendingPrizes(body: Row): Promise<Row[]> {
  // The client may send processPendingPrizes=false while still specifying prize days.
  // Do not treat that flag as a hard veto when explicit days were requested.
  const days = prizeDaysFromBody(body);
  if (days.length === 0) return [];
  const supabase = supabaseAdmin();
  const { data: pendingDays, error: pendingError } = await supabase
    .from("result_reconcile_jobs")
    .select("result_day_key")
    .eq("status", "pending")
    .in("result_day_key", days);
  if (pendingError) {
    console.warn("get-ticket-delta prize precheck failed", { message: pendingError.message, days });
  }
  const daysToProcess = Array.from(new Set((Array.isArray(pendingDays) ? pendingDays : [])
    .map((row) => clean((row as Row).result_day_key))
    .filter(Boolean)));
  if (daysToProcess.length === 0) return [];
  const results: Row[] = [];
  for (const day of daysToProcess) {
    const { data, error } = await supabase.rpc("lotterynet_process_result_reconcile_jobs_for_day", {
      p_result_day_key: day,
      p_job_limit: 4,
      p_ticket_limit: 150,
    });
    results.push({ day, ok: !error, data: data ?? null, error: error?.message ?? null });
  }
  return results;
}

function stampFrom(row: Row): string {
  return clean(row.updated_at ?? row.server_created_at ?? row.created_at);
}

function latestStamp(rows: Row[], fallback: string): string {
  return rows.map(stampFrom).filter(Boolean).sort().at(-1) ?? fallback;
}

function uniqueKeys(values: unknown[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values.map(clean).filter(Boolean)) {
    const key = value.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(value);
  }
  return result;
}

function ownerKeysForDelta(actor: AuthenticatedActor, ownerKey: string): string[] {
  if (actor.role === "cajero") {
    return uniqueKeys([
      ownerKey,
      actor.userId,
      ...accountIdentityKeys(actor.account),
    ]);
  }
  return uniqueKeys([
    ownerKey,
    ...actor.ownerKeys,
    ...actor.identityKeys.filter(() => actor.role === "admin" || actor.role === "master"),
  ]);
}

function ticketVisibleToActor(row: Row, actor: AuthenticatedActor): boolean {
  if (actor.role !== "cajero") return true;
  const cashierKey = clean(row.cashier_key);
  const cashierKeys = uniqueKeys([actor.userId, ...accountIdentityKeys(actor.account)]);
  return cashierKeys.some((identity) => sameText(identity, cashierKey));
}

function postgrestInList(values: string[]): string {
  return `(${values.map((value) => clean(value)).filter(Boolean).join(",")})`;
}

function deltaResponseCacheKey(
  version: string,
  actor: AuthenticatedActor,
  ownerKey: string,
  ownerKeys: string[],
  sinceCursor: string,
  limit: number,
  includeItems: boolean,
): string {
  return [
    "edge:get-ticket-delta:v2",
    lower(version || "0"),
    lower(actor.role),
    lower(actor.userId),
    lower(ownerKey),
    lower(ownerKeys.join("|")),
    lower(sinceCursor || ""),
    String(limit),
    includeItems ? "1" : "0",
  ].join(":");
}

async function readCachedDeltaResponse(cacheKey: string): Promise<Record<string, unknown> | null> {
  const now = Date.now();
  const memoryCached = deltaResponseMemoryCache.get(cacheKey);
  if (memoryCached && now - memoryCached.cachedAtMs <= DELTA_RESPONSE_CACHE_TTL_MS) {
    return memoryCached.response;
  }

  const inflight = deltaResponseInflight.get(cacheKey);
  if (inflight) return inflight;

  const fetchPromise = (async () => {
    const redisCached = await redisGetJson<CachedTicketDeltaResponse>(cacheKey);
    if (
      redisCached &&
      typeof redisCached === "object" &&
      typeof redisCached.cachedAtMs === "number" &&
      redisCached.response &&
      typeof redisCached.response === "object" &&
      Date.now() - redisCached.cachedAtMs <= DELTA_RESPONSE_CACHE_TTL_MS
    ) {
      deltaResponseMemoryCache.set(cacheKey, redisCached);
      return redisCached.response;
    }
    return null;
  })();

  deltaResponseInflight.set(cacheKey, fetchPromise);
  try {
    return await fetchPromise;
  } finally {
    deltaResponseInflight.delete(cacheKey);
  }
}

async function storeCachedDeltaResponse(cacheKey: string, response: Record<string, unknown>): Promise<void> {
  const cachedValue: CachedTicketDeltaResponse = { cachedAtMs: Date.now(), response };
  deltaResponseMemoryCache.set(cacheKey, cachedValue);
  await redisSetJson(cacheKey, cachedValue, Math.ceil(DELTA_RESPONSE_CACHE_TTL_MS / 1000));
}

async function fetchTicketRows(
  ownerKeys: string[],
  sinceCursor: string,
  limit: number,
  actor: AuthenticatedActor,
  includeItems: boolean,
): Promise<Row[]> {
  const supabase = supabaseAdmin();
  const selectColumns = includeItems ? TICKET_FULL_COLUMNS : TICKET_DELTA_COLUMNS;
  let query = supabase
    .from("tickets")
    .select(selectColumns)
    // Keep the sort aligned with the incremental cursor/filter. Sorting by
    // server_created_at/created_at after filtering updated_at can force a
    // larger in-memory sort for admin owners and makes the delta path slower.
    .order("updated_at", { ascending: false, nullsFirst: false })
    .order("id", { ascending: false, nullsFirst: false })
    .limit(limit);
  if (actor.role === "cajero") {
    query = query.in("cashier_key", ownerKeys);
  } else {
    const ownerList = postgrestInList(ownerKeys);
    query = query.or(`admin_key.in.${ownerList},cashier_key.in.${ownerList}`);
  }
  if (sinceCursor) {
    query = query.gt("updated_at", sinceCursor);
  }
  const { data, error } = await query;
  const rows: Row[] = [];
  if (error) throw error;
  rows.push(...((data ?? []) as unknown as Row[]));
  const byId = new Map<string, Row>();
  for (const row of rows) {
    const id = clean(row.id);
    if (id && ticketVisibleToActor(row, actor)) byId.set(id, row);
  }
  return [...byId.values()]
    .sort((a, b) => stampFrom(b).localeCompare(stampFrom(a)))
    .slice(0, limit);
}

async function fetchTicketItems(ticketIds: string[]): Promise<Row[]> {
  if (ticketIds.length === 0) return [];
  const supabase = supabaseAdmin();
  const rows: Row[] = [];
  for (let index = 0; index < ticketIds.length; index += 35) {
    const idChunk = ticketIds.slice(index, index + 35);
    const { data, error } = await supabase
      .from("ticket_items")
      .select(ITEM_COLUMNS)
      .in("ticket_id", idChunk)
      .range(0, 4999);
    if (error) throw error;
    rows.push(...((data ?? []) as unknown as Row[]));
  }
  return rows;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const ownerKey = clean(body.ownerKey);
    if (!ownerKey) return json({ ok: false, message: "ownerKey requerido." }, 400);

    const auth = await authenticatedActor(req);
    if (!auth.ok) return auth.response;
    if (!canAccessOwner(auth.actor, ownerKey)) {
      return json({ ok: false, message: "No tiene permiso para leer este owner." }, 403);
    }

    const sinceCursor = clean(body.sinceCursor ?? body.cursor);
    const limit = limitFrom(body.limit);
    const ownerKeys = ownerKeysForDelta(auth.actor, ownerKey);
    const includeItems = !bool(body.metadataOnly) && body.includeItems !== false;
    const prizeDays = prizeDaysFromBody(body);
    const cacheVersion = prizeDays.length === 0 ? await readTicketDeltaResponseVersion() : "0";
    const cacheKey = deltaResponseCacheKey(cacheVersion, auth.actor, ownerKey, ownerKeys, sinceCursor, limit, includeItems);
    if (prizeDays.length === 0) {
      const cachedResponse = await readCachedDeltaResponse(cacheKey);
      if (cachedResponse) {
        return json({ ok: true, ...cachedResponse });
      }
    }

    const prizeReconcile = await processPendingPrizes(body);
    if (prizeReconcile.length > 0) {
      await bumpTicketDeltaResponseVersion();
    }
    const tickets = await fetchTicketRows(ownerKeys, sinceCursor, limit, auth.actor, includeItems);
    const ids = tickets.map((row) => clean(row.id)).filter(Boolean);
    const items = includeItems ? await fetchTicketItems(ids) : [];

    const responseBody = {
      ok: true,
      ownerKey,
      ownerKeys,
      sinceCursor: sinceCursor || null,
      cursor: latestStamp(tickets, sinceCursor),
      count: tickets.length,
      tickets,
      items,
      includeItems,
      prizeReconcile,
    };

    if (prizeDays.length === 0) {
      await storeCachedDeltaResponse(cacheKey, responseBody);
    }

    return json(responseBody);
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener delta de tickets.",
    }, 500);
  }
});
