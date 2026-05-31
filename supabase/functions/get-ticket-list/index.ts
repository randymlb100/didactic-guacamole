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
    const items = Array.isArray(ticket.items) ? ticket.items : [];
    const total = number(ticket.total ?? ticket.tot);
    if (!key || deletedIds.has(key) || hasDeletedStatus(ticket)) continue;
    if (items.length === 0 && total > 0 && map.has(key)) continue;
    map.set(key, ticket);
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
      isWinner: Boolean(item.is_winner),
      payoutAmount: number(item.payout_amount),
      hitPosition: clean(item.hit_position),
      resultNumber: clean(item.result_number),
    })),
    winningDetails: items
      .filter((item) => Boolean(item.is_winner) || number(item.payout_amount) > 0)
      .map((item) => ({
        lotteryName: clean(item.lottery_name ?? ticket.lottery_name),
        playType: clean(item.play_type),
        playedNumber: clean(item.play_numbers),
        resultNumber: clean(item.result_number),
        hitPosition: clean(item.hit_position),
        amount: number(item.amount),
        payoutAmount: number(item.payout_amount),
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

async function ownerLookupKeys(admin: ReturnType<typeof createClient>, ownerKey: string): Promise<string[]> {
  const keys = new Set([ownerKey].map(clean).filter(Boolean));
  const { data, error } = await admin
    .from("profiles")
    .select("username,legacy_key,legacy_admin_id,legacy_admin_user")
    .or(`username.eq.${ownerKey},legacy_key.eq.${ownerKey},legacy_admin_id.eq.${ownerKey},legacy_admin_user.eq.${ownerKey}`);
  if (error) return Array.from(keys);
  if (Array.isArray(data)) {
    for (const profile of data as JsonMap[]) {
      [profile.username, profile.legacy_key, profile.legacy_admin_id, profile.legacy_admin_user]
        .map(clean)
        .filter(Boolean)
        .forEach((key) => keys.add(key));
    }
  }
  return Array.from(keys);
}

async function latestUpdatedAtForOwner(admin: ReturnType<typeof createClient>, ownerKey: string, ownerKeys: string[]): Promise<string | null> {
  const stamps: string[] = [];
  const latestByColumn = async (column: "admin_key" | "cashier_key", orderColumn: "server_created_at" | "updated_at"): Promise<JsonMap | null> => {
    const { data, error } = await admin
      .from("tickets")
      .select("server_created_at,updated_at")
      .in(column, ownerKeys)
      .order(orderColumn, { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error) throw new Error(error.message);
    return data as JsonMap | null;
  };

  for (const latestServerCreated of [
    await latestByColumn("admin_key", "server_created_at"),
    await latestByColumn("cashier_key", "server_created_at"),
  ]) {
    const createdStamp = clean(latestServerCreated?.server_created_at);
    const createdRowUpdatedStamp = clean(latestServerCreated?.updated_at);
    if (createdStamp) stamps.push(createdStamp);
    if (createdRowUpdatedStamp) stamps.push(createdRowUpdatedStamp);
  }

  for (const latestUpdated of [
    await latestByColumn("admin_key", "updated_at"),
    await latestByColumn("cashier_key", "updated_at"),
  ]) {
    const updatedStamp = clean(latestUpdated?.updated_at);
    const updatedRowCreatedStamp = clean(latestUpdated?.server_created_at);
    if (updatedStamp) stamps.push(updatedStamp);
    if (updatedRowCreatedStamp) stamps.push(updatedRowCreatedStamp);
  }

  const { data: snapshot, error: snapshotError } = await admin
    .from("lotterynet_tickets_by_owner")
    .select("updated_at")
    .eq("owner_key", ownerKey)
    .maybeSingle();
  if (snapshotError) throw new Error(snapshotError.message);
  const snapshotStamp = clean((snapshot as JsonMap | null)?.updated_at);
  if (snapshotStamp) stamps.push(snapshotStamp);

  return stamps.sort().at(-1) ?? null;
}

function chunk<T>(values: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let index = 0; index < values.length; index += size) {
    chunks.push(values.slice(index, index + size));
  }
  return chunks;
}

async function officialItemsByTicket(admin: ReturnType<typeof createClient>, ids: string[]): Promise<Map<string, JsonMap[]>> {
  const itemsByTicket = new Map<string, JsonMap[]>();
  for (const idChunk of chunk(ids, 35)) {
    const { data, error } = await admin
      .from("ticket_items")
      .select("ticket_id,play_type,play_numbers,amount,lottery_legacy_id,lottery_name,secondary_lottery_legacy_id,secondary_lottery_name,sorteo_id,is_winner,payout_amount,hit_position")
      .in("ticket_id", idChunk)
      .range(0, 4999);
    if (error) throw new Error(error.message);
    if (!Array.isArray(data)) continue;
    for (const item of data as JsonMap[]) {
      const key = clean(item.ticket_id);
      if (!itemsByTicket.has(key)) itemsByTicket.set(key, []);
      itemsByTicket.get(key)!.push(item);
    }
  }
  return itemsByTicket;
}

async function officialTicketsForOwner(admin: ReturnType<typeof createClient>, ownerKeys: string[]): Promise<JsonMap[]> {
  const since = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
  const selectColumns = "id,client_request_id,ticket_code,total_amount,monto,status,estado,payout_amount,admin_key,cashier_key,lottery_name,lottery_endpoint,server_created_at,created_at,updated_at";
  const fetchByColumn = async (column: "admin_key" | "cashier_key"): Promise<JsonMap[]> => {
    const { data, error } = await admin
      .from("tickets")
      .select(selectColumns)
      .in(column, ownerKeys)
      .is("deleted_at", null)
      .gte("server_created_at", since)
      .order("server_created_at", { ascending: false })
      .limit(300);
    if (error) throw new Error(error.message);
    return Array.isArray(data) ? data as JsonMap[] : [];
  };
  const byId = new Map<string, JsonMap>();
  for (const ticket of [...await fetchByColumn("admin_key"), ...await fetchByColumn("cashier_key")]) {
    const key = clean(ticket.id);
    if (key) byId.set(key, ticket);
  }
  const tickets = Array.from(byId.values())
    .sort((a, b) => clean(b.server_created_at ?? b.created_at).localeCompare(clean(a.server_created_at ?? a.created_at)))
    .slice(0, 300);
  if (tickets.length === 0) return [];

  const ids = tickets.map((ticket: JsonMap) => clean(ticket.id)).filter(Boolean);
  const itemsByTicket = await officialItemsByTicket(admin, ids);
  return tickets.map((ticket) => appTicketFromOfficial(ticket, itemsByTicket.get(clean(ticket.id)) ?? []));
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
    const ownerKeys = await ownerLookupKeys(admin, ownerKey);

    if (action === "updated-at") {
      return json(200, { ok: true, ownerKey, updatedAt: await latestUpdatedAtForOwner(admin, ownerKey, ownerKeys) });
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
    const officialTickets = await officialTicketsForOwner(admin, ownerKeys);
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
