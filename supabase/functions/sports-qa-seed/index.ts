import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

async function authenticatedUser(req: Request): Promise<{ ok: true; metadata: JsonMap } | { ok: false; response: Response }> {
  const token = bearerToken(req);
  if (!token) return { ok: false, response: json({ ok: false, message: "Sesion requerida." }, 401) };
  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return { ok: false, response: json({ ok: false, message: "Sesion invalida." }, 401) };
  return { ok: true, metadata: asObject(data.user.app_metadata) };
}

function metadataMatchesActor(metadata: JsonMap, actorKey: string, adminKey: string): boolean {
  const values = [
    metadata.legacy_id,
    metadata.username,
    metadata.user,
    metadata.admin_id,
    metadata.admin_user,
  ].map(lower).filter(Boolean);
  if (values.length === 0) return true;
  return [actorKey, adminKey].map(lower).filter(Boolean).some((value) => values.includes(value));
}

function isoHoursFromNow(hours: number): string {
  return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
}

async function upsertEvent(providerEventId: string, sportKey: string, homeTeam: string, awayTeam: string, hoursFromNow: number): Promise<string> {
  const { data, error } = await supabaseAdmin()
    .from("sports_events")
    .upsert({
      provider: "codex-qa",
      provider_event_id: providerEventId,
      sport_key: sportKey,
      sport_title: sportKey === "basketball" ? "Basketball" : "Baseball",
      league_key: "qa",
      league_title: "QA League",
      home_team: homeTeam,
      away_team: awayTeam,
      commence_time: isoHoursFromNow(hoursFromNow),
      status: "open",
      source_payload: { qa: true, providerEventId },
      updated_at: new Date().toISOString(),
    }, { onConflict: "provider,provider_event_id" })
    .select("id")
    .single();
  if (error || !data?.id) throw new Error(error?.message ?? "No se pudo preparar evento QA.");
  return clean(data.id);
}

async function resolveMarket(eventId: string): Promise<string> {
  const existing = await supabaseAdmin()
    .from("sports_markets")
    .select("id")
    .eq("event_id", eventId)
    .eq("market_key", "moneyline")
    .maybeSingle();
  if (existing.error) throw existing.error;
  if (existing.data?.id) return clean(existing.data.id);

  const { data, error } = await supabaseAdmin()
    .from("sports_markets")
    .insert({
      event_id: eventId,
      market_key: "moneyline",
      market_title: "Moneyline",
      status: "open",
    })
    .select("id")
    .single();
  if (error || !data?.id) throw new Error(error?.message ?? "No se pudo preparar mercado QA.");
  return clean(data.id);
}

async function upsertOdd(marketId: string, selectionKey: string, selectionLabel: string, decimalOdds: number): Promise<string> {
  const { data, error } = await supabaseAdmin()
    .from("sports_odds")
    .upsert({
      market_id: marketId,
      provider: "codex-qa",
      bookmaker_key: "qa",
      selection_key: selectionKey,
      selection_label: selectionLabel,
      decimal_odds: decimalOdds,
      american_odds: null,
      point: null,
      status: "open",
      last_updated: new Date().toISOString(),
      source_payload: { qa: true },
    }, { onConflict: "market_id,bookmaker_key,selection_key" })
    .select("id")
    .single();
  if (error || !data?.id) throw new Error(error?.message ?? "No se pudo preparar cuota QA.");
  return clean(data.id);
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
  if (lower(Deno.env.get("LOTTERYNET_QA_ENABLED")) !== "true") {
    return json({ ok: false, message: "QA no esta habilitado en Supabase." }, 403);
  }

  const auth = await authenticatedUser(req);
  if (!auth.ok) return auth.response;

  let body: JsonMap = {};
  try {
    body = await req.json();
  } catch {
    body = {};
  }

  const actorRole = lower(body.actorRole);
  const actorKey = clean(body.actorKey);
  const adminKey = clean(body.adminKey || body.ownerKey || actorKey);
  const cashierKey = clean(body.cashierKey);
  const runId = clean(body.runId || `sportsqa-${Date.now()}`);

  if (actorRole !== "admin") return json({ ok: false, message: "Solo admin puede preparar QA deportivo." }, 403);
  if (!actorKey || !adminKey || !cashierKey) return json({ ok: false, message: "Actor, admin y cajero requeridos." }, 400);
  if (!metadataMatchesActor(auth.metadata, actorKey, adminKey)) {
    return json({ ok: false, message: "Sesion no pertenece al admin QA." }, 403);
  }

  const eventA = await upsertEvent(`${runId}-event-a`, "baseball", "QA Home A", "QA Away A", 3);
  const eventB = await upsertEvent(`${runId}-event-b`, "basketball", "QA Home B", "QA Away B", 4);
  const marketA = await resolveMarket(eventA);
  const marketB = await resolveMarket(eventB);
  const oddsA = await upsertOdd(marketA, "home", "QA Home A", 1.8);
  const oddsB = await upsertOdd(marketB, "away", "QA Away B", 1.95);

  await supabaseAdmin()
    .from("sports_feature_flags")
    .upsert({
      scope: "global",
      enabled: true,
      allowed_roles: ["admin", "cashier"],
      allowed_actor_keys: [actorKey, adminKey, cashierKey],
      markets: {},
      limits: {},
      updated_by: "sports-qa-seed",
      updated_at: new Date().toISOString(),
    }, { onConflict: "scope" });

  await supabaseAdmin()
    .from("lotterynet_kv")
    .upsert({
      key: "sportsbook:global",
      value: JSON.stringify({
        configured: true,
        enabled: true,
        adminEnabled: true,
        supervisorEnabled: false,
        cashierEnabled: true,
        enabledMarkets: ["moneyline", "runline", "spread", "total"],
        updatedAt: Date.now(),
        updatedBy: "sports-qa-seed",
      }),
      upd: new Date().toISOString(),
    }, { onConflict: "key" });

  return json({
    ok: true,
    runId,
    oddsIds: [oddsA, oddsB],
    events: [eventA, eventB],
  });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error preparando QA deportivo." }, 500);
}));
