import { bearerToken, corsHeaders, json, clean, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const ODDS_API_BASE_URL = "https://api.odds-api.net/v1";
const SUPPORTED_MARKETS = new Set(["moneyline", "runline", "spread", "total", "first_half", "first_five"]);

function ok(body: JsonMap): Response {
  return json({ ok: true, ...body });
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function sharedSecretMatches(req: Request): boolean {
  const expected = Deno.env.get("LOTTERYNET_ADMIN_SHARED_SECRET") ?? "";
  if (!expected) return false;
  const provided = req.headers.get("x-lotterynet-admin-secret") ?? "";
  return provided === expected;
}

async function adminJwtMatches(req: Request, body: JsonMap): Promise<boolean> {
  const token = bearerToken(req);
  if (!token) return false;
  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return false;
  const metadata = asObject(data.user.app_metadata);
  const role = lower(metadata.role || body.actorRole);
  if (role !== "admin" && role !== "master") return false;
  const actorKey = lower(body.actorKey || body.adminKey);
  if (!actorKey) return true;
  const accepted = [
    metadata.legacy_id,
    metadata.username,
    metadata.user,
    metadata.admin_id,
    metadata.admin_user,
  ].map(lower).filter(Boolean);
  return accepted.length === 0 || accepted.includes(actorKey);
}

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function unixSecondsToIso(value: unknown): string {
  const seconds = number(value);
  return new Date((seconds > 0 ? seconds : Math.floor(Date.now() / 1000)) * 1000).toISOString();
}

function marketKey(value: unknown, betTypeValue: unknown = ""): string {
  const raw = clean(betTypeValue || value).toLowerCase();
  if (raw.startsWith("moneyline/") || raw === "moneyline") return "moneyline";
  if (raw.startsWith("total/") || raw === "total" || raw === "totals") return "total";
  if (raw.startsWith("runline/") || raw === "runline") return "runline";
  if (raw === "handicap" || raw === "spread") return "spread";
  if (raw.startsWith("handicap/")) {
    if (raw.includes("5 innings")) return "first_five";
    if (raw.includes("1st half")) return "first_half";
    return "spread";
  }

  const key = raw.replace(/[\s-]+/g, "_");
  if (key === "money_line") return "moneyline";
  if (key === "totals") return "total";
  return SUPPORTED_MARKETS.has(key) ? key : "";
}

function titleFromMarket(key: string): string {
  switch (key) {
    case "moneyline":
      return "Moneyline";
    case "runline":
      return "Runline";
    case "spread":
      return "Spread";
    case "total":
      return "Alta/Baja";
    case "first_half":
      return "Mitad";
    case "first_five":
      return "F5";
    default:
      return key;
  }
}

function sportsFromBody(body: JsonMap): string[] {
  const raw = body.sports ?? body.sportKeys ?? Deno.env.get("SPORTS_SYNC_SPORTS") ?? "baseball,basketball";
  if (Array.isArray(raw)) return raw.map(clean).filter(Boolean).slice(0, 6);
  return clean(raw).split(",").map((item) => item.trim()).filter(Boolean).slice(0, 6);
}

function leaguesForSport(body: JsonMap, sport: string): string[] {
  const leagues = asObject(body.leagues);
  const raw = leagues[sport] ?? leagues[sport.toLowerCase()] ?? body.league ?? "";
  if (Array.isArray(raw)) return raw.map(clean).filter(Boolean).slice(0, 4);
  const parsed = clean(raw).split(",").map((item) => item.trim()).filter(Boolean).slice(0, 4);
  return parsed.length > 0 ? parsed : [""];
}

function selectionLabel(odd: JsonMap, selectionKey: string): string {
  const explicit = clean(odd.selection_name);
  if (explicit) return explicit;

  const side = clean(odd.side);
  const line = clean(odd.line || odd.point);
  if (side && line) return `${side} ${line}`;
  if (line) return line;
  if (side) return side;
  return selectionKey;
}

async function oddsApiGet(path: string, params: URLSearchParams, apiKey: string): Promise<JsonMap> {
  const url = new URL(`${ODDS_API_BASE_URL}${path}`);
  params.forEach((value, key) => url.searchParams.set(key, value));
  const response = await fetch(url, {
    headers: {
      "Accept": "application/json",
      "X-API-Key": apiKey,
    },
  });
  const payload = asObject(await response.json().catch(() => ({})));
  if (!response.ok) {
    const retryAfter = response.headers.get("Retry-After");
    throw new Error(`${response.status} ${clean(payload.detail || payload.message || payload.code) || "odds-api error"}${retryAfter ? ` retry_after=${retryAfter}` : ""}`);
  }
  return payload;
}

async function oddsSnapshot(providerEventId: string, marketKeys: string, marketTypes: string, periods: string, apiKey: string): Promise<JsonMap> {
  const filtered = await oddsApiGet(
    `/events/${encodeURIComponent(providerEventId)}/odds/snapshot`,
    new URLSearchParams({
      limit: "50",
      types: marketTypes,
      market_keys: marketKeys,
      periods,
      price_fields: "odds",
    }),
    apiKey,
  );

  if (asArray(filtered.items).length > 0) return filtered;

  return await oddsApiGet(
    `/events/${encodeURIComponent(providerEventId)}/odds/snapshot`,
    new URLSearchParams({
      limit: "50",
      types: marketTypes,
      price_fields: "odds",
    }),
    apiKey,
  );
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  let body: JsonMap = {};
  try {
    body = await req.json();
  } catch {
    body = {};
  }

  if (!sharedSecretMatches(req) && !(await adminJwtMatches(req, body))) {
    return json({ ok: false, message: "Admin deportivo requerido." }, 403);
  }

  const apiKey = Deno.env.get("ODDS_API_KEY") ?? "";
  if (!apiKey) return json({ ok: false, message: "ODDS_API_KEY no esta configurada." }, 500);

  if (body.discover === "sports") {
    const payload = await oddsApiGet("/sports", new URLSearchParams({ limit: String(number(body.limit) || 100) }), apiKey);
    return ok({
      source: "odds-api.net/sports",
      items: asArray(payload.items).slice(0, 80),
      total: asArray(payload.items).length,
    });
  }

  if (body.discover === "leagues") {
    const sport = clean(body.sport || sportsFromBody(body)[0] || "baseball");
    const payload = await oddsApiGet("/leagues", new URLSearchParams({ sport }), apiKey);
    return ok({
      source: "odds-api.net/leagues",
      sport,
      items: asArray(payload.items).slice(0, 120),
      total: asArray(payload.items).length,
    });
  }

  if (body.discover === "events") {
    const now = Math.floor(Date.now() / 1000);
    const sport = clean(body.sport || sportsFromBody(body)[0] || "baseball");
    const league = clean(body.league || "");
    const params = new URLSearchParams({
      sport,
      start_from: String(number(body.startFrom) || now - 60 * 60),
      start_to: String(number(body.startTo) || now + 7 * 24 * 60 * 60),
      limit: String(Math.min(Math.max(number(body.limit) || 10, 1), 50)),
    });
    if (league) params.set("league", league);
    const payload = await oddsApiGet("/events", params, apiKey);
    return ok({
      source: "odds-api.net/events",
      sport,
      league,
      params: Object.fromEntries(params.entries()),
      items: asArray(payload.items).slice(0, 20),
      total: asArray(payload.items).length,
      next_cursor: payload.next_cursor ?? null,
    });
  }

  const supabase = supabaseAdmin();
  const now = Math.floor(Date.now() / 1000);
  const startFrom = number(body.startFrom) || now - 60 * 60;
  const startTo = number(body.startTo) || now + 36 * 60 * 60;
  const limit = Math.min(Math.max(number(body.limit) || 25, 1), 50);
  const marketKeys = clean(body.marketKeys || "moneyline,runline,spread,total");
  const marketTypes = clean(body.marketTypes || body.types || "moneyline,runline,spread,total,handicap");
  const periods = clean(body.periods || "full time");
  let eventsSaved = 0;
  let marketsSaved = 0;
  let oddsSaved = 0;
  let oddsItemsSeen = 0;
  let oddsItemsAccepted = 0;
  const errors: string[] = [];

  for (const sport of sportsFromBody(body)) {
    for (const league of leaguesForSport(body, sport)) {
      const eventParams = new URLSearchParams({
          sport,
          start_from: String(startFrom),
          start_to: String(startTo),
          limit: String(limit),
        });
      if (league) eventParams.set("league", league);
      const eventsPayload = await oddsApiGet(
        "/events",
        eventParams,
        apiKey,
      );

      for (const rawEvent of asArray(eventsPayload.items)) {
      const event = asObject(rawEvent);
      const providerEventId = clean(event.event_id);
      if (!providerEventId) continue;

      const eventRow = {
        provider: "odds-api.net",
        provider_event_id: providerEventId,
        sport_key: clean(event.sport),
        sport_title: clean(event.sport) || sport,
        league_key: clean(event.league),
        league_title: clean(event.league),
        home_team: clean(event.home_team) || "Home",
        away_team: clean(event.away_team) || "Away",
        commence_time: unixSecondsToIso(event.start_time),
        status: "open",
        source_payload: event,
        updated_at: new Date().toISOString(),
      };

      const { data: storedEvent, error: eventError } = await supabase
        .from("sports_events")
        .upsert(eventRow, { onConflict: "provider,provider_event_id" })
        .select("id")
        .single();

      if (eventError || !storedEvent?.id) {
        errors.push(`event ${providerEventId}: ${eventError?.message ?? "sin id"}`);
        continue;
      }
      eventsSaved += 1;

      let oddsPayload: JsonMap;
      try {
        oddsPayload = await oddsSnapshot(providerEventId, marketKeys, marketTypes, periods, apiKey);
      } catch (error) {
        errors.push(`odds ${providerEventId}: ${error instanceof Error ? error.message : String(error)}`);
        continue;
      }

      const marketIdByKey = new Map<string, string>();
      for (const rawOdd of asArray(oddsPayload.items)) {
        oddsItemsSeen += 1;
        const odd = asObject(rawOdd);
        const key = marketKey(odd.market_key, odd.bet_type);
        const decimalOdds = number(odd.odds);
        if (!key || decimalOdds <= 1) continue;
        oddsItemsAccepted += 1;

        let marketId = marketIdByKey.get(key);
        if (!marketId) {
          const { data: existingMarket } = await supabase
            .from("sports_markets")
            .select("id")
            .eq("event_id", storedEvent.id)
            .eq("market_key", key)
            .maybeSingle();

          if (existingMarket?.id) {
            marketId = clean(existingMarket.id);
          } else {
            const { data: insertedMarket, error: marketError } = await supabase
              .from("sports_markets")
              .insert({
                event_id: storedEvent.id,
                market_key: key,
                market_title: titleFromMarket(key),
                status: "open",
                updated_at: new Date().toISOString(),
              })
              .select("id")
              .single();
            if (marketError || !insertedMarket?.id) {
              errors.push(`market ${providerEventId}/${key}: ${marketError?.message ?? "sin id"}`);
              continue;
            }
            marketId = clean(insertedMarket.id);
            marketsSaved += 1;
          }
          marketIdByKey.set(key, marketId);
        }

        const selectionKey = clean(odd.selection_key || odd.id || odd.side);
        if (!selectionKey) continue;
        const { error: oddError } = await supabase
          .from("sports_odds")
          .upsert({
            market_id: marketId,
            provider: "odds-api.net",
            bookmaker_key: clean(odd.bookmaker) || "consensus",
            selection_key: selectionKey,
            selection_label: selectionLabel(odd, selectionKey),
            decimal_odds: decimalOdds,
            point: number(odd.point || odd.line) || null,
            status: odd.is_available === false ? "suspended" : "open",
            last_updated: new Date(number(oddsPayload.as_of_ts_ms) || Date.now()).toISOString(),
            source_payload: odd,
          }, { onConflict: "market_id,bookmaker_key,selection_key" });
        if (oddError) {
          errors.push(`odd ${providerEventId}/${selectionKey}: ${oddError.message}`);
        } else {
          oddsSaved += 1;
        }
      }
    }
    }
  }

  return ok({
    eventsSaved,
    marketsSaved,
    oddsSaved,
    oddsItemsSeen,
    oddsItemsAccepted,
    errors: errors.slice(0, 12),
    truncatedErrors: Math.max(0, errors.length - 12),
  });
}

Deno.serve(async (req: Request) => {
  try {
    return await handle(req);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("sports-sync-odds unhandled", message);
    return json({ ok: false, message }, 500);
  }
});
