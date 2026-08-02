import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  authenticatedActor,
  canAccessOwner,
  clean,
  corsHeaders,
  json,
  sameText,
  supabaseAdmin,
} from "../_shared/lotterynet-admin.ts";

type Row = Record<string, unknown>;

function normalizeDayKey(value: unknown): { iso: string; legacy: string } {
  const raw = clean(value);
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) {
    const [day, month, year] = raw.split("-");
    return { iso: `${year}-${month}-${day}`, legacy: raw };
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-");
    return { iso: raw, legacy: `${day}-${month}-${year}` };
  }
  const iso = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  const [year, month, day] = iso.split("-");
  return { iso, legacy: `${day}-${month}-${year}` };
}

function normalizePlayType(value: unknown): string {
  const raw = clean(value).toUpperCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[\s_-]+/g, "");
  if (raw === "QUINIELA") return "Q";
  if (raw === "PALE") return "P";
  if (raw === "SUPERPALE") return "SP";
  if (raw === "TRIPLETA") return "T";
  if (raw === "PICK3" || raw === "PICK3STRAIGHT" || raw === "P3STRAIGHT") return "P3";
  if (raw === "PICK3BOX") return "P3BOX";
  if (raw === "PICK4" || raw === "PICK4STRAIGHT" || raw === "P4STRAIGHT") return "P4";
  if (raw === "PICK4BOX") return "P4BOX";
  return raw;
}

function bucket(playType: unknown, number: unknown): string {
  const type = normalizePlayType(playType);
  const digits = clean(number).replace(/\D/g, "");
  if (type === "Q") return `${type}:${digits.padStart(2, "0").slice(-2)}`;
  if (type === "P3BOX" || type === "P4BOX") return `${type}:${digits.split("").sort().join("")}`;
  return `${type}:${digits || clean(number)}`;
}

function isVoidStatus(row: Row): boolean {
  return ["BORRADO", "ANULADO", "INVALIDADO", "VOIDED", "NULLED", "INVALID"].includes(
    clean(row.status ?? row.estado).toUpperCase(),
  );
}

function sameLottery(item: Row, lotteryId: string): boolean {
  const candidates = [
    clean(item.lottery_id),
    clean(item.lottery_legacy_id),
  ].filter(Boolean);
  return candidates.some((candidate) => sameText(candidate, lotteryId));
}

async function adminLimitKeys(ownerKey: string): Promise<string[]> {
  const { data, error } = await supabaseAdmin().rpc("ln_limit_self_keys", { p_key: ownerKey });
  if (error) throw error;
  return Array.isArray(data) ? data.map(clean).filter(Boolean) : [ownerKey].filter(Boolean);
}

async function fetchTicketRows(ownerKeys: string[], dayIso: string, dayLegacy: string): Promise<Row[]> {
  const { data, error } = await supabaseAdmin()
    .from("tickets")
    .select("id,admin_key,cashier_key,status,estado,draw_date_real,legacy_day_key,deleted_at,server_created_at,updated_at")
    .in("admin_key", ownerKeys)
    .or(`draw_date_real.eq.${dayIso},legacy_day_key.eq.${dayLegacy},legacy_day_key.eq.${dayIso}`)
    .limit(5000);
  if (error) throw error;
  return (data ?? []) as Row[];
}

async function fetchItems(ticketIds: string[]): Promise<Row[]> {
  if (ticketIds.length === 0) return [];
  const rows: Row[] = [];
  for (let index = 0; index < ticketIds.length; index += 80) {
    const { data, error } = await supabaseAdmin()
      .from("ticket_items")
      .select("ticket_id,play_numbers,normalized_number,play_type,amount,lottery_legacy_id,lottery_id")
      .in("ticket_id", ticketIds.slice(index, index + 80))
      .range(0, 4999);
    if (error) throw error;
    rows.push(...((data ?? []) as Row[]));
  }
  return rows;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const ownerKey = clean(body.ownerKey);
    if (!ownerKey) return json({ ok: false, message: "ownerKey requerido." }, 400);

    return json({
      ok: false,
      degraded: true,
      ownerKey,
      authoritativeValidation: "create-ticket-v2",
      message: "Validacion previa pausada temporalmente. El servidor validara el tope al crear el ticket.",
      updatedAt: new Date().toISOString(),
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo leer el tope vendido.",
    }, 500);
  }
});
