import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

const VOID_ROLES = new Set(["admin", "cashier"]);

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function accountArray(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value as Record<string, unknown>[] : [];
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

function scopedTicketQuery(ticketId: string, role: string, actorKey: string, ownerKey: string, adminKey: string, cashierKey: string) {
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
      owner_key,
      admin_key,
      cashier_key,
      sports_ticket_legs (
        event_label,
        market_title,
        selection_label,
        decimal_odds,
        status
      )
    `)
    .eq("id", ticketId);
  if (role === "cashier") {
    query = query.eq("cashier_key", cashierKey || actorKey);
  } else {
    query = query.or(`owner_key.eq.${ownerKey || actorKey},admin_key.eq.${adminKey || actorKey}`);
  }
  return query.maybeSingle();
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

  if (!actorKey || !ticketId) return json({ ok: false, message: "Actor y ticket son requeridos." }, 400);
  if (!VOID_ROLES.has(actorRole)) return json({ ok: false, message: "Este rol no puede anular apuestas deportivas." }, 403);
  if (!metadataMatchesActor(auth.metadata, actorKey, adminKey, cashierKey)) {
    return json({ ok: false, message: "Sesion no pertenece al ticket deportivo." }, 403);
  }

  const { data: ticket, error } = await scopedTicketQuery(ticketId, actorRole, actorKey, ownerKey, adminKey, cashierKey);
  if (error) return json({ ok: false, message: error.message }, 500);
  if (!ticket) return json({ ok: false, message: "Ticket deportivo no encontrado." }, 404);

  const previousStatus = lower(ticket.status);
  if (previousStatus === "void" || previousStatus === "cancelled") {
    return json({ ok: true, alreadyVoided: true, ticket: ticketPayload(ticket as JsonMap) });
  }
  if (previousStatus !== "pending") {
    return json({ ok: false, message: "Solo se puede anular un ticket deportivo en estado pendiente." }, 409);
  }

  // Transactionally update the ticket status and legs status
  const voidedAt = new Date().toISOString();
  
  // 1. Update ticket legs status to void
  const { error: legsError } = await supabaseAdmin()
    .from("sports_ticket_legs")
    .update({ status: "void", result_payload: { voidedBy: actorKey, reason: clean(body.reason || "Anulado desde el panel administrativo") } })
    .eq("sports_ticket_id", ticketId);
  if (legsError) return json({ ok: false, message: "No se pudieron anular las jugadas del ticket." }, 500);

  // 2. Update ticket status to void
  const { data: updated, error: updateError } = await supabaseAdmin()
    .from("sports_tickets")
    .update({ status: "void", voided_at: voidedAt, updated_at: voidedAt })
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
      cashier_key,
      sports_ticket_legs (
        event_label,
        market_title,
        selection_label,
        decimal_odds,
        status
      )
    `)
    .single();

  if (updateError || !updated) {
    return json({ ok: false, message: updateError?.message ?? "No se pudo anular el ticket deportivo." }, 500);
  }

  // 3. Devolución de Balance inside lotterynet_users_state (Refund Cashier)
  const targetCashierKey = clean(updated.cashier_key || cashierKey);
  const stakeToRefund = number(updated.stake);
  
  if (targetCashierKey && stakeToRefund > 0) {
    try {
      const supabase = supabaseAdmin();
      const { data: stateData, error: stateError } = await supabase
        .from("lotterynet_users_state")
        .select("payload")
        .eq("scope", "global")
        .maybeSingle();

      if (!stateError && stateData?.payload) {
        const payload = stateData.payload as Record<string, unknown>;
        const users = accountArray(payload.users);
        let cashierFound = false;

        for (const user of users) {
          const userKey = lower(user.id || user.userId || user.user || user.username);
          if (userKey === lower(targetCashierKey)) {
            user.balance = number(user.balance) + stakeToRefund;
            cashierFound = true;
            break;
          }
        }

        if (cashierFound) {
          const { error: saveError } = await supabase
            .from("lotterynet_users_state")
            .upsert({ scope: "global", payload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
          
          if (saveError) {
            console.warn("Error refunding cashier balance in lotterynet_users_state", saveError.message);
          }
        } else {
          console.warn("Cashier not found in lotterynet_users_state for refund:", targetCashierKey);
        }
      }
    } catch (refundError) {
      console.warn("Exception refunding cashier balance", refundError);
    }
  }

  // 4. Log settlement and audits
  await supabaseAdmin().from("sports_settlements").insert({
    sports_ticket_id: ticketId,
    settlement_type: "manual",
    previous_status: previousStatus,
    next_status: "void",
    payout_amount: 0,
    reason: clean(body.reason || "Anulacion de apuesta deportiva"),
    actor_key: actorKey,
    metadata: { action: "void-sports-ticket", refundAmount: stakeToRefund },
  });

  await supabaseAdmin().from("sports_audit_log").insert({
    actor_key: actorKey,
    action: "void-sports-ticket",
    entity_table: "sports_tickets",
    entity_id: ticketId,
    metadata: { voidedAt, stake: stakeToRefund },
  });

  return json({ ok: true, ticket: ticketPayload(updated as JsonMap) });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error anulando ticket deportivo." }, 500);
}));
