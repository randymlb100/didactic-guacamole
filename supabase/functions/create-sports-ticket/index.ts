import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const SELLER_ROLES = new Set(["admin", "cashier"]);
const MARKET_KEYS = new Set(["moneyline", "runline", "spread", "total", "first_half", "first_five"]);
const DEFAULT_MAX_ODDS_AGE_SECONDS = 10 * 60;

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function ticketCode(): string {
  const random = crypto.getRandomValues(new Uint8Array(4));
  return `SN-${Array.from(random).map((item) => item.toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

function canRoleSell(role: string): boolean {
  return SELLER_ROLES.has(lower(role));
}

function marketIsSupported(value: unknown): boolean {
  return MARKET_KEYS.has(lower(value));
}

async function authenticatedUser(req: Request): Promise<{ ok: true; userId: string; metadata: JsonMap } | { ok: false; response: Response }> {
  const token = bearerToken(req);
  if (!token) return { ok: false, response: json({ ok: false, message: "Sesion requerida." }, 401) };

  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return { ok: false, response: json({ ok: false, message: "Sesion invalida." }, 401) };
  return { ok: true, userId: data.user.id, metadata: asObject(data.user.app_metadata) };
}

function metadataMatchesActor(metadata: JsonMap, actorKey: string, adminKey: string, cashierKey: string): boolean {
  const metadataValues = [
    metadata.legacy_id,
    metadata.username,
    metadata.user,
    metadata.admin_id,
    metadata.admin_user,
    metadata.cashier_id,
    metadata.cashier_user,
  ].map(lower).filter(Boolean);
  if (metadataValues.length === 0) return true;
  const accepted = [actorKey, adminKey, cashierKey].map(lower).filter(Boolean);
  return accepted.some((value) => metadataValues.includes(value));
}

function cleanArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map(clean).filter(Boolean) : [];
}

function roleFlagName(role: string): string {
  if (lower(role) === "admin") return "adminEnabled";
  if (lower(role) === "supervisor") return "supervisorEnabled";
  if (lower(role) === "cashier") return "cashierEnabled";
  return "";
}

function actorMatchesAny(candidates: string[], allowed: string[]): boolean {
  if (allowed.length === 0) return true;
  const normalized = new Set(allowed.map(lower).filter(Boolean));
  return candidates.map(lower).some((candidate) => normalized.has(candidate));
}

function actorMatchesConfigured(candidates: string[], allowed: string[]): boolean {
  if (allowed.length === 0) return false;
  return actorMatchesAny(candidates, allowed);
}

async function featureEnabledFor(role: string, actorKey: string, adminKey: string, cashierKey: string): Promise<boolean> {
  const supabase = supabaseAdmin();
  const candidates = [actorKey, cashierKey, adminKey].map(clean).filter(Boolean);
  const { data: masterConfig } = await supabase
    .from("lotterynet_master_state")
    .select("payload")
    .eq("config_key", "sportsbook:global")
    .maybeSingle();
  const payload = asObject(masterConfig?.payload);
  if (payload.enabled === true) {
    const flagName = roleFlagName(role);
    if (!flagName || payload[flagName] !== true) return false;
    const allowedActors = cleanArray(payload.allowedActorKeys);
    const cashierAdminKeys = cleanArray(payload.cashierAdminKeys);
    if (allowedActors.length === 0 && cashierAdminKeys.length === 0) return false;
    if (lower(role) === "cashier") {
      return actorMatchesAny([actorKey, cashierKey], allowedActors) ||
        actorMatchesConfigured([adminKey], cashierAdminKeys);
    }
    return actorMatchesConfigured(candidates, allowedActors);
  }

  const { data: flags } = await supabase
    .from("sports_feature_flags")
    .select("enabled,allowed_roles,allowed_actor_keys")
    .eq("scope", "global")
    .maybeSingle();

  if (flags?.enabled === true) {
    const allowedRoles = Array.isArray(flags.allowed_roles) ? flags.allowed_roles.map(lower) : [];
    const allowedActors = cleanArray(flags.allowed_actor_keys);
    const roleAllowed = allowedRoles.length === 0 || allowedRoles.includes(lower(role));
    const actorAllowed = actorMatchesAny(candidates, allowedActors);
    return roleAllowed && actorAllowed;
  }

  const { data: kv } = await supabase
    .from("lotterynet_kv")
    .select("value")
    .eq("key", "sportsbook:global")
    .maybeSingle();
  const rawValue = clean(kv?.value);
  if (!rawValue) return false;
  let parsed: JsonMap = {};
  try {
    parsed = asObject(JSON.parse(rawValue));
  } catch {
    return false;
  }
  if (parsed.enabled !== true) return false;
  const allowedActors = cleanArray(parsed.allowedActorKeys);
  const cashierAdminKeys = cleanArray(parsed.cashierAdminKeys);
  if (allowedActors.length === 0 && cashierAdminKeys.length === 0) return false;
  if (lower(role) === "admin") return parsed.adminEnabled === true && actorMatchesConfigured(candidates, allowedActors);
  if (lower(role) === "supervisor") return parsed.supervisorEnabled === true && actorMatchesConfigured(candidates, allowedActors);
  if (lower(role) === "cashier") {
    return parsed.cashierEnabled === true &&
      (actorMatchesConfigured([actorKey, cashierKey], allowedActors) || actorMatchesConfigured([adminKey], cashierAdminKeys));
  }
  return false;
}

async function existingTicket(clientRequestId: string): Promise<JsonMap | null> {
  const { data, error } = await supabaseAdmin()
    .from("sports_tickets")
    .select("id,ticket_code,status,stake,decimal_odds,potential_payout")
    .eq("client_request_id", clientRequestId)
    .maybeSingle();
  if (error) throw error;
  return data as JsonMap | null;
}

async function resolveOdds(oddsId: string): Promise<JsonMap | null> {
  const { data, error } = await supabaseAdmin()
    .from("sports_odds")
    .select(`
      id,
      market_id,
      selection_key,
      selection_label,
      decimal_odds,
      american_odds,
      point,
      status,
      last_updated,
      sports_markets (
        id,
        event_id,
        market_key,
        market_title,
        status,
        line,
        sports_events (
          id,
          sport_key,
          sport_title,
          league_title,
          home_team,
          away_team,
          commence_time,
          status
        )
      )
    `)
    .eq("id", oddsId)
    .maybeSingle();
  if (error) throw error;
  return data as JsonMap | null;
}

function validateResolvedOdds(row: JsonMap, maxAgeSeconds: number): { ok: true } | { ok: false; message: string } {
  const market = asObject(row.sports_markets);
  const event = asObject(market.sports_events);
  const decimalOdds = number(row.decimal_odds);
  if (!row.id || decimalOdds <= 1) return { ok: false, message: "Cuota invalida." };
  if (lower(row.status) !== "open") return { ok: false, message: "Cuota cerrada." };
  if (lower(market.status) !== "open") return { ok: false, message: "Mercado cerrado." };
  if (!marketIsSupported(market.market_key)) return { ok: false, message: "Mercado no soportado." };
  if (!["scheduled", "open"].includes(lower(event.status))) return { ok: false, message: "Juego cerrado." };
  const commenceMs = Date.parse(clean(event.commence_time));
  if (!Number.isFinite(commenceMs) || commenceMs <= Date.now()) return { ok: false, message: "Juego iniciado o sin hora valida." };
  const updatedMs = Date.parse(clean(row.last_updated));
  if (!Number.isFinite(updatedMs) || Date.now() - updatedMs > maxAgeSeconds * 1000) {
    return { ok: false, message: "Cuota vencida. Refresca el tablero." };
  }
  return { ok: true };
}

async function bestLimits(ownerKey: string, adminKey: string, cashierKey: string): Promise<JsonMap> {
  const scopeKeys = [cashierKey, adminKey, ownerKey, "global"].map(clean).filter(Boolean);
  const { data, error } = await supabaseAdmin()
    .from("sports_limits")
    .select("*")
    .in("scope_key", scopeKeys);
  if (error) throw error;
  const rows = Array.isArray(data) ? data as JsonMap[] : [];
  return rows.find((row) => clean(row.scope_key) === cashierKey) ??
    rows.find((row) => clean(row.scope_key) === adminKey) ??
    rows.find((row) => clean(row.scope_key) === ownerKey) ??
    rows.find((row) => clean(row.scope_key) === "global") ??
    {};
}

function validateLimits(stake: number, potentialPayout: number, limits: JsonMap): { ok: true } | { ok: false; message: string } {
  const maxTicketStake = number(limits.max_ticket_stake);
  const maxPotentialPayout = number(limits.max_potential_payout);
  if (maxTicketStake > 0 && stake > maxTicketStake) return { ok: false, message: "Monto supera limite por ticket." };
  if (maxPotentialPayout > 0 && potentialPayout > maxPotentialPayout) {
    return { ok: false, message: "Pago posible supera limite permitido." };
  }
  return { ok: true };
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  const auth = await authenticatedUser(req);
  if (!auth.ok) return auth.response;

  let body: JsonMap = {};
  try {
    body = await req.json();
  } catch {
    return json({ ok: false, message: "JSON invalido." }, 400);
  }

  const actorRole = lower(body.actorRole);
  const actorKey = clean(body.actorKey);
  const ownerKey = clean(body.ownerKey || body.adminKey || actorKey);
  const adminKey = clean(body.adminKey || ownerKey);
  const cashierKey = clean(body.cashierKey || actorKey);
  const clientRequestId = clean(body.clientRequestId);
  const stake = number(body.stake);
  const maxOddsAgeSeconds = number(body.maxOddsAgeSeconds) || DEFAULT_MAX_ODDS_AGE_SECONDS;
  const selections = asArray(body.selections).map(asObject);

  if (!actorKey || !clientRequestId) return json({ ok: false, message: "Actor y clientRequestId son requeridos." }, 400);
  if (!canRoleSell(actorRole)) return json({ ok: false, message: "Este rol no puede vender apuestas deportivas." }, 403);
  if (!metadataMatchesActor(auth.metadata, actorKey, adminKey, cashierKey)) {
    return json({ ok: false, message: "Sesion no pertenece al vendedor deportivo." }, 403);
  }
  if (stake <= 0) return json({ ok: false, message: "Monto invalido." }, 400);
  if (selections.length === 0) return json({ ok: false, message: "Selecciona al menos una cuota." }, 400);

  const existing = await existingTicket(clientRequestId);
  if (existing) return json({ ok: true, duplicate: true, ticket: existing });

  if (!(await featureEnabledFor(actorRole, actorKey, adminKey, cashierKey))) {
    return json({ ok: false, message: "Deportes no esta habilitado para esta cuenta." }, 403);
  }

  const resolvedLegs: JsonMap[] = [];
  let combinedOdds = 1;
  for (const selection of selections) {
    const oddsId = clean(selection.oddsId);
    if (!oddsId) return json({ ok: false, message: "Seleccion incompleta." }, 400);
    const odds = await resolveOdds(oddsId);
    if (!odds) return json({ ok: false, message: "Cuota no existe." }, 404);
    const oddsValidation = validateResolvedOdds(odds, maxOddsAgeSeconds);
    if (!oddsValidation.ok) return json({ ok: false, message: oddsValidation.message }, 409);
    const market = asObject(odds.sports_markets);
    const event = asObject(market.sports_events);
    combinedOdds *= number(odds.decimal_odds);
    resolvedLegs.push({ odds, market, event });
  }

  const potentialPayout = Number((stake * combinedOdds).toFixed(2));
  const limits = await bestLimits(ownerKey, adminKey, cashierKey);
  const limitValidation = validateLimits(stake, potentialPayout, limits);
  if (!limitValidation.ok) return json({ ok: false, message: limitValidation.message }, 409);

  const ticket = {
    ticket_code: ticketCode(),
    client_request_id: clientRequestId,
    owner_key: ownerKey,
    admin_key: adminKey,
    supervisor_key: clean(body.supervisorKey),
    cashier_key: cashierKey,
    seller_user_id: auth.userId,
    seller_username: clean(body.sellerUsername || actorKey),
    banca_name: clean(body.bancaName),
    ticket_type: resolvedLegs.length > 1 ? "parlay" : "straight",
    stake,
    decimal_odds: Number(combinedOdds.toFixed(4)),
    potential_payout: potentialPayout,
    status: "pending",
    metadata: { actorRole, createdBy: "create-sports-ticket" },
  };

  const { data: storedTicket, error: ticketError } = await supabaseAdmin()
    .from("sports_tickets")
    .insert(ticket)
    .select("id,ticket_code,status,stake,decimal_odds,potential_payout")
    .single();
  if (ticketError || !storedTicket?.id) return json({ ok: false, message: ticketError?.message ?? "No se pudo crear ticket." }, 500);

  const legs = resolvedLegs.map(({ odds, market, event }) => ({
    sports_ticket_id: storedTicket.id,
    event_id: clean(event.id),
    market_id: clean(market.id),
    odds_id: clean(odds.id),
    sport_key: clean(event.sport_key),
    league_title: clean(event.league_title),
    event_label: `${clean(event.away_team)} @ ${clean(event.home_team)}`,
    market_key: lower(market.market_key),
    market_title: clean(market.market_title),
    selection_key: clean(odds.selection_key),
    selection_label: clean(odds.selection_label),
    point: number(odds.point) || null,
    decimal_odds: number(odds.decimal_odds),
    odds_locked_at: new Date().toISOString(),
    commence_time: clean(event.commence_time),
    status: "pending",
  }));

  const { error: legsError } = await supabaseAdmin().from("sports_ticket_legs").insert(legs);
  if (legsError) return json({ ok: false, message: legsError.message }, 500);

  await supabaseAdmin().from("sports_audit_log").insert({
    actor_key: actorKey,
    action: "create-sports-ticket",
    entity_table: "sports_tickets",
    entity_id: clean(storedTicket.id),
    metadata: { clientRequestId, stake, potentialPayout, legs: legs.length },
  });

  return json({ ok: true, ticket: storedTicket, legs });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error creando ticket deportivo." }, 500);
}));
