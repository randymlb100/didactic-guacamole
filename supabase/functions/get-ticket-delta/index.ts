import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type Row = Record<string, unknown>;

const MAX_LIMIT = 300;

function limitFrom(value: unknown): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return 150;
  return Math.min(Math.trunc(parsed), MAX_LIMIT);
}

function stampFrom(row: Row): string {
  return clean(row.updated_at ?? row.server_created_at ?? row.created_at);
}

function latestStamp(rows: Row[], fallback: string): string {
  return rows.map(stampFrom).filter(Boolean).sort().at(-1) ?? fallback;
}

async function fetchTicketRows(ownerKey: string, sinceCursor: string, limit: number): Promise<Row[]> {
  const supabase = supabaseAdmin();
  const columns = ["admin_key", "cashier_key"];
  const rows: Row[] = [];
  for (const column of columns) {
    let query = supabase
      .from("tickets")
      .select("*")
      .eq(column, ownerKey)
      .order("server_created_at", { ascending: false, nullsFirst: false })
      .order("created_at", { ascending: false, nullsFirst: false })
      .limit(limit);
    if (sinceCursor) {
      query = query.or(`updated_at.gt.${sinceCursor},server_created_at.gt.${sinceCursor},created_at.gt.${sinceCursor}`);
    }
    const { data, error } = await query;
    if (error) throw error;
    rows.push(...((data ?? []) as Row[]));
  }
  const byId = new Map<string, Row>();
  for (const row of rows) {
    const id = clean(row.id);
    if (id) byId.set(id, row);
  }
  return [...byId.values()]
    .sort((a, b) => stampFrom(b).localeCompare(stampFrom(a)))
    .slice(0, limit);
}

async function fetchTicketItems(ticketIds: string[]): Promise<Row[]> {
  if (ticketIds.length === 0) return [];
  const supabase = supabaseAdmin();
  const { data, error } = await supabase
    .from("ticket_items")
    .select("*")
    .in("ticket_id", ticketIds);
  if (error) throw error;
  return (data ?? []) as Row[];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const ownerKey = clean(body.ownerKey);
    if (!ownerKey) return json({ ok: false, message: "ownerKey requerido." }, 400);

    const sinceCursor = clean(body.sinceCursor ?? body.cursor);
    const limit = limitFrom(body.limit);
    const tickets = await fetchTicketRows(ownerKey, sinceCursor, limit);
    const ids = tickets.map((row) => clean(row.id)).filter(Boolean);
    const items = await fetchTicketItems(ids);

    return json({
      ok: true,
      ownerKey,
      sinceCursor: sinceCursor || null,
      cursor: latestStamp(tickets, sinceCursor),
      count: tickets.length,
      tickets,
      items,
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener delta de tickets.",
    }, 500);
  }
});
