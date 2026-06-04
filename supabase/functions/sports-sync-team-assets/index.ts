import { bearerToken, corsHeaders, json, clean, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const THESPORTSDB_BASE_URL = "https://www.thesportsdb.com/api/v1/json";
const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

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

function normalizeTeamName(value: unknown): string {
  return clean(value)
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function assetKey(sportKey: string, leagueTitle: string, teamName: string): string {
  return `${sportKey}::${leagueTitle}::${normalizeTeamName(teamName)}`;
}

function freshEnough(lastCheckedAt: unknown, logoUrl: unknown): boolean {
  const checkedMs = Date.parse(clean(lastCheckedAt));
  return clean(logoUrl).length > 0 && Number.isFinite(checkedMs) && Date.now() - checkedMs < CACHE_TTL_MS;
}

async function fetchLeagueTeams(leagueTitle: string, apiKey: string): Promise<JsonMap[]> {
  if (!clean(leagueTitle)) return [];
  const url = new URL(`${THESPORTSDB_BASE_URL}/${encodeURIComponent(apiKey)}/search_all_teams.php`);
  url.searchParams.set("l", leagueTitle);
  const response = await fetch(url, { headers: { "Accept": "application/json" } });
  const payload = asObject(await response.json().catch(() => ({})));
  if (!response.ok) {
    throw new Error(`${response.status} ${clean(payload.message || payload.error) || "TheSportsDB league error"}`);
  }
  return asArray(payload.teams).map(asObject);
}

async function fetchTeamAsset(teamName: string, apiKey: string): Promise<JsonMap> {
  const url = new URL(`${THESPORTSDB_BASE_URL}/${encodeURIComponent(apiKey)}/searchteams.php`);
  url.searchParams.set("t", teamName);
  const response = await fetch(url, { headers: { "Accept": "application/json" } });
  const payload = asObject(await response.json().catch(() => ({})));
  if (!response.ok) {
    throw new Error(`${response.status} ${clean(payload.message || payload.error) || "TheSportsDB error"}`);
  }

  const normalized = normalizeTeamName(teamName);
  const teams = asArray(payload.teams).map(asObject);
  return teams.find((team) => normalizeTeamName(team.strTeam) === normalized) ?? {};
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

  const apiKey = Deno.env.get("THESPORTSDB_API_KEY") ?? "";
  if (!apiKey) return json({ ok: false, message: "THESPORTSDB_API_KEY no esta configurada." }, 500);

  const supabase = supabaseAdmin();
  const requestedLimit = Number(body.limit);
  const limit = Math.min(Math.max(Number.isFinite(requestedLimit) ? requestedLimit : 24, 1), 40);
  const since = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
  const until = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();

  const { data: events, error: eventsError } = await supabase
    .from("sports_events")
    .select("sport_key, league_title, home_team, away_team, commence_time")
    .gte("commence_time", since)
    .lte("commence_time", until)
    .order("commence_time", { ascending: true })
    .limit(120);

  if (eventsError) return json({ ok: false, message: eventsError.message }, 500);

  const teams = new Map<string, { sportKey: string; leagueTitle: string; teamName: string; teamNameNormalized: string }>();
  for (const rawEvent of asArray(events)) {
    const event = asObject(rawEvent);
    const sportKey = clean(event.sport_key);
    const leagueTitle = clean(event.league_title);
    for (const teamName of [clean(event.home_team), clean(event.away_team)]) {
      const teamNameNormalized = normalizeTeamName(teamName);
      if (!teamNameNormalized) continue;
      const key = assetKey(sportKey, leagueTitle, teamName);
      if (!teams.has(key)) teams.set(key, { sportKey, leagueTitle, teamName, teamNameNormalized });
    }
  }

  const pendingTeams = Array.from(teams.values()).slice(0, limit);
  const existingByKey = new Map<string, JsonMap>();
  if (pendingTeams.length > 0) {
    const { data: existing } = await supabase
      .from("sports_team_assets")
      .select("sport_key, league_title, team_name_normalized, logo_url, last_checked_at")
      .eq("provider", "thesportsdb");
    for (const row of asArray(existing)) {
      const asset = asObject(row);
      existingByKey.set(
        `${clean(asset.sport_key)}::${clean(asset.league_title)}::${clean(asset.team_name_normalized)}`,
        asset,
      );
    }
  }

  let checked = 0;
  let skippedFresh = 0;
  let saved = 0;
  const errors: string[] = [];
  const leagueTeamsByKey = new Map<string, JsonMap[]>();

  for (const team of pendingTeams) {
    const existing = existingByKey.get(`${team.sportKey}::${team.leagueTitle}::${team.teamNameNormalized}`);
    if (existing && freshEnough(existing.last_checked_at, existing.logo_url)) {
      skippedFresh += 1;
      continue;
    }

    checked += 1;
    try {
      const leagueKey = `${team.sportKey}::${team.leagueTitle}`;
      let leagueTeams = leagueTeamsByKey.get(leagueKey);
      if (!leagueTeams) {
        leagueTeams = await fetchLeagueTeams(team.leagueTitle, apiKey);
        leagueTeamsByKey.set(leagueKey, leagueTeams);
      }
      const asset = leagueTeams.find((candidate) => normalizeTeamName(candidate.strTeam) === team.teamNameNormalized) ??
        await fetchTeamAsset(team.teamName, apiKey);
      const logoUrl = clean(asset.strTeamBadge || asset.strBadge || asset.strTeamLogo || asset.strLogo);
      const badgeUrl = clean(asset.strTeamBadge || asset.strBadge);
      const { error: upsertError } = await supabase
        .from("sports_team_assets")
        .upsert({
          provider: "thesportsdb",
          sport_key: team.sportKey,
          league_title: team.leagueTitle,
          team_name: clean(asset.strTeam) || team.teamName,
          team_name_normalized: team.teamNameNormalized,
          logo_url: logoUrl || null,
          badge_url: badgeUrl || null,
          source_payload: asset,
          last_checked_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        }, { onConflict: "provider,sport_key,league_title,team_name_normalized" });
      if (upsertError) {
        errors.push(`${team.teamName}: ${upsertError.message}`);
      } else {
        saved += 1;
      }
    } catch (error) {
      errors.push(`${team.teamName}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return ok({
    teamsFound: teams.size,
    checked,
    skippedFresh,
    saved,
    errors: errors.slice(0, 10),
    truncatedErrors: Math.max(0, errors.length - 10),
  });
}

Deno.serve(async (req: Request) => {
  try {
    return await handle(req);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("sports-sync-team-assets unhandled", message);
    return json({ ok: false, message }, 500);
  }
});
