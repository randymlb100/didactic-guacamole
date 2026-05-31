import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  let body: JsonMap = {};
  try {
    body = await req.json();
  } catch {
    return json({ ok: false, message: "JSON invalido." }, 400);
  }

  const eventId = clean(body.eventId);
  if (!eventId) return json({ ok: false, message: "eventId es requerido." }, 400);

  const supabase = supabaseAdmin();

  // 1. Fetch sports event
  const { data: event, error: eventError } = await supabase
    .from("sports_events")
    .select("*")
    .eq("id", eventId)
    .maybeSingle();

  if (eventError || !event) {
    return json({ ok: false, message: eventError?.message ?? "Evento deportivo no encontrado." }, 404);
  }

  if (lower(event.status) !== "final") {
    return json({ ok: false, message: "El evento no esta en estado final." }, 400);
  }

  const homeScore = number(event.home_score);
  const awayScore = number(event.away_score);

  // 2. Fetch pending legs for this event
  const { data: legs, error: legsError } = await supabase
    .from("sports_ticket_legs")
    .select("*, sports_tickets(*)")
    .eq("event_id", eventId)
    .eq("status", "pending");

  if (legsError) return json({ ok: false, message: legsError.message }, 500);
  if (!legs || legs.length === 0) {
    return json({ ok: true, message: "No hay jugadas pendientes para este evento.", processed: 0 });
  }

  // Group by ticket ID to update tickets together after settling legs
  const affectedTicketIds = new Set<string>();

  // 3. Process each leg
  for (const leg of legs) {
    const marketKey = lower(leg.market_key);
    const selectionKey = lower(leg.selection_key);
    const point = number(leg.point);
    let status: "won" | "lost" | "push" = "push";

    if (marketKey === "moneyline") {
      if (selectionKey === "home") {
        status = homeScore > awayScore ? "won" : homeScore < awayScore ? "lost" : "push";
      } else if (selectionKey === "away") {
        status = awayScore > homeScore ? "won" : awayScore < homeScore ? "lost" : "push";
      } else if (selectionKey === "draw" || selectionKey === "tie") {
        status = homeScore === awayScore ? "won" : "lost";
      }
    } else if (marketKey === "total") {
      const totalScore = homeScore + awayScore;
      if (selectionKey === "over") {
        status = totalScore > point ? "won" : totalScore < point ? "lost" : "push";
      } else if (selectionKey === "under") {
        status = totalScore < point ? "won" : totalScore > point ? "lost" : "push";
      }
    } else if (marketKey === "spread" || marketKey === "runline") {
      if (selectionKey === "home") {
        const homeDiff = (homeScore + point) - awayScore;
        status = homeDiff > 0 ? "won" : homeDiff < 0 ? "lost" : "push";
      } else if (selectionKey === "away") {
        const awayDiff = (awayScore + point) - homeScore;
        status = awayDiff > 0 ? "won" : awayDiff < 0 ? "lost" : "push";
      }
    }

    // Update leg status
    const { error: updateLegErr } = await supabase
      .from("sports_ticket_legs")
      .update({
        status,
        result_payload: {
          settledAt: new Date().toISOString(),
          homeScore,
          awayScore,
          settledBy: "auto-settle-webhook",
        },
      })
      .eq("id", leg.id);

    if (updateLegErr) {
      console.warn(`Error updating leg ${leg.id} status`, updateLegErr.message);
    } else {
      affectedTicketIds.add(leg.sports_ticket_id);
    }
  }

  let settledCount = 0;

  // 4. Recalculate and settle affected tickets
  for (const ticketId of affectedTicketIds) {
    // Fetch ticket and all its legs to see full status
    const { data: ticket, error: ticketErr } = await supabase
      .from("sports_tickets")
      .select("*, sports_ticket_legs(*)")
      .eq("id", ticketId)
      .maybeSingle();

    if (ticketErr || !ticket) continue;

    const ticketLegs = asArray(ticket.sports_ticket_legs).map(asObject);
    const anyPending = ticketLegs.some((l) => lower(l.status) === "pending");
    if (anyPending) continue; // Still waiting for other matches in the parlay

    const anyLost = ticketLegs.some((l) => lower(l.status) === "lost");
    let nextStatus: "won" | "lost" | "push" = "won";
    let finalOdds = 1.0;

    if (anyLost) {
      nextStatus = "lost";
    } else {
      // All legs are won, push, or void
      const allPushOrVoid = ticketLegs.every((l) => ["push", "void"].includes(lower(l.status)));
      if (allPushOrVoid) {
        nextStatus = "push";
      } else {
        nextStatus = "won";
        // Recalculate decimal odds (exclude pushes and voids)
        for (const l of ticketLegs) {
          const lStatus = lower(l.status);
          if (lStatus === "won") {
            finalOdds *= number(l.decimal_odds);
          }
        }
      }
    }

    const stake = number(ticket.stake);
    const potentialPayout = nextStatus === "won" ? Number((stake * finalOdds).toFixed(2)) : (nextStatus === "push" ? stake : 0);

    const settledAt = new Date().toISOString();
    const { error: updateTicketErr } = await supabase
      .from("sports_tickets")
      .update({
        status: nextStatus,
        decimal_odds: Number(finalOdds.toFixed(4)),
        potential_payout: potentialPayout,
        settled_at: settledAt,
        updated_at: settledAt,
      })
      .eq("id", ticketId);

    if (updateTicketErr) {
      console.warn(`Error updating ticket ${ticketId} status`, updateTicketErr.message);
    } else {
      settledCount++;

      // Log settlement
      await supabase.from("sports_settlements").insert({
        sports_ticket_id: ticketId,
        settlement_type: "auto",
        previous_status: ticket.status,
        next_status: nextStatus,
        payout_amount: potentialPayout,
        reason: "Liquidacion automatica por finalizacion de partido",
        actor_key: "system",
        metadata: { eventId, homeScore, awayScore },
      });

      // Audit log
      await supabase.from("sports_audit_log").insert({
        actor_key: "system",
        action: "auto-settle-ticket",
        entity_table: "sports_tickets",
        entity_id: ticketId,
        metadata: { finalStatus: nextStatus, payoutAmount: potentialPayout },
      });
    }
  }

  return json({ ok: true, message: "Liquidacion completada con exito.", processed: settledCount });
}

Deno.serve((req) => handle(req).catch((error) => {
  return json({ ok: false, message: error instanceof Error ? error.message : "Error liquidando tickets." }, 500);
}));
