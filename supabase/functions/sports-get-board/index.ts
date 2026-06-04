import { corsHeaders, json, clean, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

function ok(body: JsonMap): Response {
  return json({ ok: true, ...body });
}

function toNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function asPayload(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function normalizeMarketKey(value: unknown): string {
  const key = clean(value).toLowerCase();
  return ["moneyline", "runline", "spread", "total", "first_half", "first_five"].includes(key) ? key : "";
}

function isPrimarySportsBetType(marketKey: string, betType: string, selectionLabel: string): boolean {
  const type = clean(betType).toLowerCase();
  const label = clean(selectionLabel).toLowerCase();
  if (marketKey === "moneyline") return type === "" || type === "moneyline";
  if (marketKey === "runline") return type === "" || type === "runline";
  if (marketKey === "spread") return type === "" || type === "spread" || type === "handicap";
  if (marketKey === "total") {
    if (type && type !== "total" && type !== "totals") return false;
    return !/(corner|corners|card|cards|shot|shots|goal scorer|player|team total|1st half|first half)/.test(label);
  }
  if (marketKey === "first_half") return type.includes("half");
  if (marketKey === "first_five") return type.includes("5");
  return false;
}

function normalizedSelectionKey(value: string): string {
  return clean(value)
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9.+-]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function displaySelectionLabel(source: JsonMap, rawLabel: unknown, event: JsonMap, marketKey: string, point: number | null): string {
  const side = clean(source.side).toLowerCase();
  const withPoint = (label: string): string => {
    if ((marketKey === "spread" || marketKey === "runline") && point !== null && point !== 0) {
      const sign = point > 0 ? "+" : "";
      return `${label} ${sign}${point}`;
    }
    if (marketKey === "total" && point !== null && point > 0 && !/\d/.test(label)) {
      return `${label} ${point}`;
    }
    return label;
  };
  if (side === "home") return withPoint(clean(event.home_team) || clean(rawLabel));
  if (side === "away") return withPoint(clean(event.away_team) || clean(rawLabel));
  if (side === "over") return withPoint("Over");
  if (side === "under") return withPoint("Under");
  if (side === "draw") return "Empate";
  const label = clean(rawLabel);
  if (label === "1") return withPoint(clean(event.home_team) || label);
  if (label === "2") return withPoint(clean(event.away_team) || label);
  if (label.toLowerCase() === "x") return "Empate";
  return withPoint(label);
}

function selectionGroupKey(source: JsonMap, label: string, marketKey: string): string {
  const side = clean(source.side).toLowerCase();
  if (marketKey === "moneyline") {
    if (["home", "away", "draw"].includes(side)) return `${marketKey}:${side}`;
    const normalized = normalizedSelectionKey(label);
    if (normalized === "1") return `${marketKey}:home`;
    if (normalized === "2") return `${marketKey}:away`;
    if (normalized === "x" || normalized === "draw" || normalized === "empate") return `${marketKey}:draw`;
  }
  if (marketKey === "spread" || marketKey === "runline") {
    if (side === "home" || side === "away") return `${marketKey}:${side}`;
  }
  if (marketKey === "total") {
    if (side === "over" || normalizedSelectionKey(label).startsWith("over")) return `${marketKey}:over`;
    if (side === "under" || normalizedSelectionKey(label).startsWith("under")) return `${marketKey}:under`;
  }
  return `${marketKey}:${normalizedSelectionKey(label)}`;
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
  const includeQa = body.includeQa === true || clean(body.league).toLowerCase() === "qa";
  const supabase = supabaseAdmin();
  let query = supabase
    .from("sports_events")
    .select(`
      id,
      provider,
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
          last_updated,
          source_payload
        )
      )
    `)
    .in("status", ["scheduled", "open", "suspended"])
    .gte("commence_time", new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString())
    .order("commence_time", { ascending: true })
    .limit(80);

  if (sportKey) query = query.eq("sport_key", sportKey);
  if (!includeQa) query = query.neq("provider", "codex-qa");

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
      const marketOdds: JsonMap[] = [];
      const seenSelections = new Set<string>();
      const rawOdds = (Array.isArray(market.sports_odds) ? market.sports_odds as JsonMap[] : [])
        .slice()
        .sort((left, right) => Math.abs((toNumber(left.decimal_odds) ?? 99) - 1.91) - Math.abs((toNumber(right.decimal_odds) ?? 99) - 1.91));
      for (const odd of rawOdds) {
        const decimalOdds = toNumber(odd.decimal_odds);
        if (!decimalOdds || decimalOdds <= 1) continue;
        const source = asPayload(odd.source_payload);
        const period = clean(source.period).toLowerCase();
        const betType = clean(source.bet_type || source.market_key || market.market_key).toLowerCase();
        const point = toNumber(odd.point);
        const selectionLabel = displaySelectionLabel(source, odd.selection_label, event, marketKey, point);
        const selectionIdentity = selectionGroupKey(source, selectionLabel, marketKey);
        if (period && period !== "full time") continue;
        if (!isPrimarySportsBetType(marketKey, betType, selectionLabel)) continue;
        if (seenSelections.has(selectionIdentity)) continue;
        seenSelections.add(selectionIdentity);
        marketOdds.push({
          id: clean(odd.id),
          marketId: clean(odd.market_id || market.id),
          selectionKey: clean(odd.selection_key),
          selectionLabel,
          decimalOdds,
          americanOdds: toNumber(odd.american_odds),
          point,
          status: clean(odd.status) || "open",
          lastUpdated: clean(odd.last_updated),
        });
      }
      if (marketOdds.length === 0) continue;
      const maxOddsForMarket = marketKey === "moneyline" ? 3 : 4;
      markets.push({
        id: clean(market.id),
        eventId: clean(market.event_id || event.id),
        marketKey,
        marketTitle: clean(market.market_title) || marketKey,
        status: clean(market.status) || "open",
        line: toNumber(market.line),
      });
      odds.push(...marketOdds.slice(0, maxOddsForMarket));
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
  }).filter((game: JsonMap) => {
    const odds = Array.isArray(game.odds) ? game.odds : [];
    const markets = Array.isArray(game.markets) ? game.markets : [];
    return odds.length > 0 && markets.length > 0;
  });

  return ok({
    payload: {
      source: "sports_events",
      fetchedAt: new Date().toISOString(),
      games,
    },
  });
});
