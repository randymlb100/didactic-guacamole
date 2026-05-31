import { corsHeaders, json, clean, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

function ok(body: JsonMap): Response {
  return json({ ok: true, ...body });
}

function toNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeMarketKey(value: unknown): string {
  const key = clean(value).toLowerCase();
  return ["moneyline", "runline", "spread", "total", "first_half", "first_five"].includes(key) ? key : "";
}

function normalizeTeamName(value: unknown): string {
  return clean(value)
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function teamAssetLookupKey(sportKey: unknown, leagueTitle: unknown, teamName: unknown): string {
  return `${clean(sportKey)}::${clean(leagueTitle)}::${normalizeTeamName(teamName)}`;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  let body: JsonMap = {};
  try {
    body = await req.json();
  } catch {
    body = {};
  }

  const action = clean(body.action || "fetch").toLowerCase();
  if (action !== "fetch") return json({ ok: false, message: "Accion no soportada." }, 400);

  const sportKey = clean(body.sportKey);
  const supabase = supabaseAdmin();
  let query = supabase
    .from("sports_events")
    .select(`
      id,
      sport_key,
      sport_title,
      league_title,
      home_team,
      away_team,
      commence_time,
      status,
      sports_markets (
        id,
        event_id,
        market_key,
        market_title,
        status,
        line,
        sports_odds (
          id,
          market_id,
          selection_key,
          selection_label,
          decimal_odds,
          american_odds,
          point,
          status,
          last_updated
        )
      )
    `)
    .in("status", ["scheduled", "open", "suspended"])
    .gte("commence_time", new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString())
    .order("commence_time", { ascending: true })
    .limit(80);

  if (sportKey) query = query.eq("sport_key", sportKey);

  const { data, error } = await query;
  if (error) return json({ ok: false, message: error.message }, 500);

  const events = Array.isArray(data) ? data as JsonMap[] : [];
  const { data: assets } = await supabase
    .from("sports_team_assets")
    .select("sport_key, league_title, team_name_normalized, logo_url, badge_url")
    .eq("provider", "thesportsdb");
  const assetUrlByKey = new Map<string, string>();
  for (const asset of Array.isArray(assets) ? assets as JsonMap[] : []) {
    const url = clean(asset.logo_url || asset.badge_url);
    if (!url) continue;
    assetUrlByKey.set(
      `${clean(asset.sport_key)}::${clean(asset.league_title)}::${clean(asset.team_name_normalized)}`,
      url,
    );
  }

  const games = events.map((event: JsonMap) => {
    const markets: JsonMap[] = [];
    const odds: JsonMap[] = [];

    for (const market of Array.isArray(event.sports_markets) ? event.sports_markets as JsonMap[] : []) {
      const marketKey = normalizeMarketKey(market.market_key);
      if (!marketKey) continue;
      markets.push({
        id: clean(market.id),
        eventId: clean(market.event_id || event.id),
        marketKey,
        marketTitle: clean(market.market_title) || marketKey,
        status: clean(market.status) || "open",
        line: toNumber(market.line),
      });
      for (const odd of Array.isArray(market.sports_odds) ? market.sports_odds as JsonMap[] : []) {
        const decimalOdds = toNumber(odd.decimal_odds);
        if (!decimalOdds || decimalOdds <= 1) continue;
        odds.push({
          id: clean(odd.id),
          marketId: clean(odd.market_id || market.id),
          selectionKey: clean(odd.selection_key),
          selectionLabel: clean(odd.selection_label),
          decimalOdds,
          americanOdds: toNumber(odd.american_odds),
          point: toNumber(odd.point),
          status: clean(odd.status) || "open",
          lastUpdated: clean(odd.last_updated),
        });
      }
    }

    return {
      event: {
        id: clean(event.id),
        sportKey: clean(event.sport_key),
        sportTitle: clean(event.sport_title),
        leagueTitle: clean(event.league_title),
        homeTeam: clean(event.home_team),
        awayTeam: clean(event.away_team),
        homeTeamLogoUrl: assetUrlByKey.get(teamAssetLookupKey(event.sport_key, event.league_title, event.home_team)) || "",
        awayTeamLogoUrl: assetUrlByKey.get(teamAssetLookupKey(event.sport_key, event.league_title, event.away_team)) || "",
        commenceTime: clean(event.commence_time),
        status: clean(event.status) || "scheduled",
      },
      markets,
      odds,
    };
  });

  return ok({
    payload: {
      source: "sports_events",
      fetchedAt: new Date().toISOString(),
      games,
    },
  });
});
