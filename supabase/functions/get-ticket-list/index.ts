import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type JsonMap = Record<string, unknown>;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body ?? {}), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function number(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function epoch(value: unknown): number {
  const parsed = Date.parse(clean(value));
  return Number.isFinite(parsed) ? parsed : Date.now();
}

function statusForApp(value: unknown): string {
  const raw = clean(value).toUpperCase();
  if (raw === "VALIDO" || raw === "VALID" || raw === "ACTIVE") return "active";
  if (raw === "ANULADO" || raw === "VOIDED") return "voided";
  if (raw === "INVALIDADO" || raw === "INVALID") return "invalid";
  if (raw === "PAGADO" || raw === "PAID") return "paid";
  if (raw === "GANADOR" || raw === "WINNER") return "winner";
  if (raw === "BORRADO" || raw === "DELETED") return "deleted";
  return clean(value) || "active";
}

function dayLabel(ms: number): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(ms)).replace(/\//g, "-");
}

function timeLabel(ms: number): string {
  return new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Santo_Domingo",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  }).format(new Date(ms));
}

function payloadObject(payload: unknown): JsonMap {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return { tickets: [], deletedIds: [] };
  return payload as JsonMap;
}

function payloadTickets(payload: JsonMap): unknown[] {
  return Array.isArray(payload.tickets) ? payload.tickets : [];
}

function payloadDeletedIds(payload: JsonMap): string[] {
  const ids = new Set<string>();
  for (const key of ["deletedIds", "deletedTicketIds", "removedIds"]) {
    const value = payload[key];
    if (Array.isArray(value)) value.map(clean).filter(Boolean).forEach((id) => ids.add(id));
  }
  return Array.from(ids);
}

function ticketRow(ticket: unknown): JsonMap {
  return ticket && typeof ticket === "object" && !Array.isArray(ticket) ? ticket as JsonMap : {};
}

function ticketKey(ticket: unknown): string {
  const row = ticketRow(ticket);
  return clean(row.id || row.clientRequestId || row.client_request_id);
}

function hasDeletedStatus(ticket: unknown): boolean {
  const row = ticketRow(ticket);
  const status = clean(row.status || row.st || row.estado).toLowerCase();
  return ["deleted", "borrado", "removed"].includes(status);
}

function filterSnapshotTickets(tickets: unknown[], deletedIds: Set<string>): unknown[] {
  return tickets.filter((ticket) => {
    const key = ticketKey(ticket);
    return key && !deletedIds.has(key) && !hasDeletedStatus(ticket);
  });
}

function mergeTickets(snapshotTickets: unknown[], officialTickets: JsonMap[], deletedIds: Set<string>): unknown[] {
  const map = new Map<string, unknown>();
  for (const ticket of filterSnapshotTickets(snapshotTickets, deletedIds)) {
    map.set(ticketKey(ticket), ticket);
  }
  for (const ticket of officialTickets) {
    const key = ticketKey(ticket);
    if (key && !deletedIds.has(key) && !hasDeletedStatus(ticket)) map.set(key, ticket);
  }
  return Array.from(map.values()).sort((a, b) => number((b as JsonMap).createdAtMs) - number((a as JsonMap).createdAtMs));
}

function appTicketFromOfficial(ticket: JsonMap, items: JsonMap[]): JsonMap {
  const createdMs = epoch(ticket.server_created_at ?? ticket.created_at);
  const appId = clean(ticket.client_request_id) || clean(ticket.id);
  const adminKey = clean(ticket.admin_key ?? ticket.adminKey);
  const cashierKey = clean(ticket.cashier_key ?? ticket.cashierKey);
  return {
    id: appId,
    type: "lot",
    serial: clean(ticket.ticket_code) || appId,
    lots: clean(ticket.lottery_name ?? ticket.lottery_endpoint),
    lotteries: clean(ticket.lottery_name ?? ticket.lottery_endpoint),
    items: items.map((item) => ({
      type: clean(item.play_type),
      playType: clean(item.play_type),
      nums: clean(item.play_numbers),
      number: clean(item.play_numbers),
      amt: number(item.amount),
      amount: number(item.amount),
      lotId: clean(item.lottery_legacy_id ?? item.sorteo_id),
      lotName: clean(item.lottery_name ?? ticket.lottery_name),
      lotteryId: clean(item.lottery_legacy_id ?? item.sorteo_id),
      lotteryName: clean(item.lottery_name ?? ticket.lottery_name),
      secondaryLotteryId: clean(item.secondary_lottery_legacy_id),
      secondaryLotteryName: clean(item.secondary_lottery_name),
    })),
    subtotal: number(ticket.total_amount ?? ticket.monto),
    discount: 0,
    tot: number(ticket.total_amount ?? ticket.monto),
    total: number(ticket.total_amount ?? ticket.monto),
    totalPrize: number(ticket.payout_amount),
    adminId: adminKey,
    adminUser: adminKey,
    cajeroId: cashierKey,
    vendedorId: cashierKey,
    vendedorRol: "cashier",
    vendedorNombre: cashierKey,
    saleMode: "edge",
    offlineSale: false,
    createdAtMs: createdMs,
    createdAtEpochMs: createdMs,
    updatedAt: createdMs,
    date: dayLabel(createdMs),
    time: timeLabel(createdMs),
    securityCode: "",
    note: "",
    st: statusForApp(ticket.status ?? ticket.estado),
    status: statusForApp(ticket.status ?? ticket.estado),
  };
}

async function latestUpdatedAtForOwner(admin: ReturnType<typeof createClient>, ownerKey: string): Promise<string | null> {
  const { data: latest, error } = await admin
    .from("tickets")
    .select("server_created_at,updated_at")
    .or(`admin_key.eq.${ownerKey},cashier_key.eq.${ownerKey}`)
    .order("server_created_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw new Error(error.message);
  const latestStamp = clean((latest as JsonMap | null)?.updated_at ?? (latest as JsonMap | null)?.server_created_at);
  if (latestStamp) return latestStamp;
  const { data: snapshot, error: snapshotError } = await admin
    .from("lotterynet_tickets_by_owner")
    .select("updated_at")
    .eq("owner_key", ownerKey)
    .maybeSingle();
  if (snapshotError) throw new Error(snapshotError.message);
  return clean((snapshot as JsonMap | null)?.updated_at) || null;
}

async function officialTicketsForOwner(admin: ReturnType<typeof createClient>, ownerKey: string): Promise<JsonMap[]> {
  const since = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
  const { data: tickets, error } = await admin
    .from("tickets")
    .select("id,client_request_id,ticket_code,total_amount,monto,status,estado,payout_amount,admin_key,cashier_key,lottery_name,lottery_endpoint,server_created_at,created_at")
    .or(`admin_key.eq.${ownerKey},cashier_key.eq.${ownerKey}`)
    .is("deleted_at", null)
    .gte("server_created_at", since)
    .order("server_created_at", { ascending: false })
    .limit(300);
  if (error || !Array.isArray(tickets) || tickets.length === 0) return [];

  const ids = tickets.map((ticket: JsonMap) => clean(ticket.id)).filter(Boolean);
  const itemResult = ids.length
    ? await admin
      .from("ticket_items")
      .select("ticket_id,play_type,play_numbers,amount,lottery_legacy_id,lottery_name,secondary_lottery_legacy_id,secondary_lottery_name,sorteo_id")
      .in("ticket_id", ids)
    : { data: [] };
  const itemsByTicket = new Map<string, JsonMap[]>();
  if (Array.isArray(itemResult.data)) {
    for (const item of itemResult.data as JsonMap[]) {
      const key = clean(item.ticket_id);
      if (!itemsByTicket.has(key)) itemsByTicket.set(key, []);
      itemsByTicket.get(key)!.push(item);
    }
  }
  return (tickets as JsonMap[]).map((ticket) => appTicketFromOfficial(ticket, itemsByTicket.get(clean(ticket.id)) ?? []));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { ok: false, message: "Metodo no permitido" });

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRole) return json(500, { ok: false, message: "Servidor no configurado" });

    const admin = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
    const body = await req.json().catch(() => ({})) as JsonMap;
    const action = clean(body.action || "fetch").toLowerCase();
    const ownerKey = clean(body.ownerKey ?? body.owner_key);
    if (!ownerKey) return json(400, { ok: false, message: "Owner requerido" });

    if (action === "updated-at") {
      return json(200, { ok: true, ownerKey, updatedAt: await latestUpdatedAtForOwner(admin, ownerKey) });
    }

    const { data, error } = await admin
      .from("lotterynet_tickets_by_owner")
      .select("payload,updated_at")
      .eq("owner_key", ownerKey)
      .maybeSingle();
    if (error) throw new Error(error.message);

    const basePayload = payloadObject((data as JsonMap | null)?.payload);

    if (action === "upsert") {
      const incomingPayload = payloadObject(body.payload);
      const deletedIds = new Set([...payloadDeletedIds(basePayload), ...payloadDeletedIds(incomingPayload)]);
      const payload = {
        ...incomingPayload,
        schemaVersion: Number(incomingPayload.schemaVersion ?? basePayload.schemaVersion ?? 2),
        tickets: filterSnapshotTickets(payloadTickets(incomingPayload), deletedIds),
        deletedIds: Array.from(deletedIds),
      };
      const { error: upsertError } = await admin
        .from("lotterynet_tickets_by_owner")
        .upsert({ owner_key: ownerKey, payload, updated_at: new Date().toISOString() }, { onConflict: "owner_key" });
      if (upsertError) throw new Error(upsertError.message);
      return json(200, { ok: true, ownerKey });
    }

    const deletedIds = new Set(payloadDeletedIds(basePayload));
    const officialTickets = await officialTicketsForOwner(admin, ownerKey);
    const payload = {
      ...basePayload,
      schemaVersion: Number(basePayload.schemaVersion ?? 2),
      tickets: mergeTickets(payloadTickets(basePayload), officialTickets, deletedIds),
      deletedIds: Array.from(deletedIds),
    };
    return json(200, { ok: true, ownerKey, payload, updatedAt: (data as JsonMap | null)?.updated_at ?? null });
  } catch (error) {
    return json(400, { ok: false, message: error instanceof Error ? error.message : "No se pudo cargar tickets" });
  }
});
