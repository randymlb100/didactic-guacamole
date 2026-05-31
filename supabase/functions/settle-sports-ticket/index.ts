import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const SETTLEMENT_ROLES = new Set(["admin"]);
const NEXT_STATUSES = new Set(["won", "lost", "push", "void"]);

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
  const ticketId = clean(body.ticketId);
  const nextStatus = lower(body.nextStatus);

  if (!actorKey || !ticketId) return json({ ok: false, message: "Actor y ticket son requeridos." }, 400);
  if (!SETTLEMENT_ROLES.has(actorRole)) return json({ ok: false, message: "Solo admin puede liquidar resultados deportivos." }, 403);
  if (!NEXT_STATUSES.has(nextStatus)) return json({ ok: false, message: "Estado deportivo invalido para liquidar." }, 400);
  if (!metadataMatchesActor(auth.metadata, actorKey, adminKey, cashierKey)) {
    return json({ ok: false, message: "Sesion no pertenece a la liquidacion deportiva." }, 403);
  }

  const { data: ticket, error } = await supabaseAdmin()
    .from("sports_tickets")
    .select("id,status,potential_payout,owner_key,admin_key")
    .eq("id", ticketId)
    .or(`owner_key.eq.${ownerKey || actorKey},admin_key.eq.${adminKey || actorKey}`)
    .maybeSingle();
  if (error) return json({ ok: false, message: error.message }, 500);
  if (!ticket) return json({ ok: false, message: "Ticket deportivo no encontrado." }, 404);

  const previousStatus = lower(ticket.status);
  if (previousStatus === "paid") return json({ ok: false, message: "Ticket deportivo ya pagado; no se liquida de nuevo." }, 409);

  const settledAt = new Date().toISOString();
  const payoutAmount = nextStatus === "won" ? number(ticket.potential_payout) : 0;
  const { error: legsError } = await supabaseAdmin()
    .from("sports_ticket_legs")
    .update({ status: nextStatus, result_payload: { settledBy: actorKey, reason: clean(body.reason) } })
    .eq("sports_ticket_id", ticketId);
  if (legsError) return json({ ok: false, message: legsError.message }, 500);

  const { data: updated, error: updateError } = await supabaseAdmin()
    .from("sports_tickets")
    .update({ status: nextStatus, settled_at: settledAt, updated_at: settledAt })
    .eq("id", ticketId)
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
    .single();
  if (updateError || !updated) return json({ ok: false, message: updateError?.message ?? "No se pudo liquidar ticket deportivo." }, 500);

  await supabaseAdmin().from("sports_settlements").insert({
    sports_ticket_id: ticketId,
    settlement_type: "manual",
    previous_status: previousStatus,
    next_status: nextStatus,
    payout_amount: payoutAmount,
    reason: clean(body.reason || "Liquidacion deportiva"),
    actor_key: actorKey,
    metadata: { action: "settle-sports-ticket" },
  });
  await supabaseAdmin().from("sports_audit_log").insert({
    actor_key: actorKey,
    action: "settle-sports-ticket",
    entity_table: "sports_tickets",
    entity_id: ticketId,
    metadata: { previousStatus, nextStatus, payoutAmount },
  });

  return json({ ok: true, ticket: ticketPayload(updated as JsonMap) });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error liquidando ticket deportivo." }, 500);
}));
