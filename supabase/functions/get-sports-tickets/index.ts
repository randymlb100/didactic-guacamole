import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const READER_ROLES = new Set(["admin", "supervisor", "cashier"]);
const STATUS_VALUES = new Set(["pending", "won", "lost", "push", "void", "paid"]);

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

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

function scopedQuery(query: any, role: string, actorKey: string, ownerKey: string, adminKey: string, cashierKey: string) {
  if (role === "cashier") return query.eq("cashier_key", cashierKey || actorKey);
  if (role === "supervisor") return query.eq("supervisor_key", actorKey);
  return query.or(`owner_key.eq.${ownerKey || actorKey},admin_key.eq.${adminKey || actorKey}`);
}

function summarize(tickets: JsonMap[]): JsonMap {
  const totalStake = tickets.reduce((sum, ticket) => sum + number(ticket.stake), 0);
  const pendingTickets = tickets.filter((ticket) => clean(ticket.status) === "pending");
  const wonTickets = tickets.filter((ticket) => clean(ticket.status) === "won");
  const paidTickets = tickets.filter((ticket) => clean(ticket.status) === "paid");
  return {
    totalTickets: tickets.length,
    pendingTickets: pendingTickets.length,
    wonTickets: wonTickets.length,
    paidTickets: paidTickets.length,
    totalStake,
    pendingPayout: pendingTickets.reduce((sum, ticket) => sum + number(ticket.potential_payout), 0),
    paidPayout: paidTickets.reduce((sum, ticket) => sum + number(ticket.potential_payout), 0),
  };
}

function ticketPayload(ticket: JsonMap): JsonMap {
  const legs = Array.isArray(ticket.sports_ticket_legs) ? ticket.sports_ticket_legs.map(asObject) : [];
  return {
    id: clean(ticket.id),
    ticketCode: clean(ticket.ticket_code),
    sellerUsername: clean(ticket.seller_username),
    bancaName: clean(ticket.banca_name),
    ticketType: clean(ticket.ticket_type),
    stake: number(ticket.stake),
    decimalOdds: number(ticket.decimal_odds),
    potentialPayout: number(ticket.potential_payout),
    status: clean(ticket.status),
    soldAt: clean(ticket.sold_at),
    legs: legs.map((leg) => ({
      eventLabel: clean(leg.event_label),
      marketTitle: clean(leg.market_title),
      selectionLabel: clean(leg.selection_label),
      decimalOdds: number(leg.decimal_odds),
      status: clean(leg.status),
    })),
  };
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
  const status = lower(body.status);
  const limit = Math.min(Math.max(Math.trunc(number(body.limit) || 50), 1), 100);

  if (!actorKey) return json({ ok: false, message: "Actor requerido." }, 400);
  if (!READER_ROLES.has(actorRole)) return json({ ok: false, message: "Este rol no puede leer tickets deportivos." }, 403);
  if (!metadataMatchesActor(auth.metadata, actorKey, adminKey, cashierKey)) {
    return json({ ok: false, message: "Sesion no pertenece al vendedor deportivo." }, 403);
  }

  let query = supabaseAdmin()
    .from("sports_tickets")
    .select(`
      id,
      ticket_code,
      seller_username,
      banca_name,
      ticket_type,
      stake,
      decimal_odds,
      potential_payout,
      status,
      sold_at,
      sports_ticket_legs (
        event_label,
        market_title,
        selection_label,
        decimal_odds,
        status
      )
    `)
    .order("sold_at", { ascending: false })
    .limit(limit);

  query = scopedQuery(query, actorRole, actorKey, ownerKey, adminKey, cashierKey);
  if (STATUS_VALUES.has(status)) query = query.eq("status", status);

  const { data, error } = await query;
  if (error) return json({ ok: false, message: error.message }, 500);
  const tickets = Array.isArray(data) ? data as JsonMap[] : [];

  return json({
    ok: true,
    tickets: tickets.map(ticketPayload),
    summary: summarize(tickets),
  });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error leyendo tickets deportivos." }, 500);
}));
