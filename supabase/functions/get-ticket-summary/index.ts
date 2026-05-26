import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type Row = Record<string, unknown>;

function isoDateFromDayKey(value: unknown): string {
  const raw = clean(value);
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw;
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) {
    const [day, month, year] = raw.split("-");
    return `${year}-${month}-${day}`;
  }
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function amount(row: Row): number {
  const value = Number(row.total_amount ?? 0);
  return Number.isFinite(value) ? value : 0;
}

function status(row: Row): string {
  return clean(row.status ?? row.estado ?? "ACTIVO").toUpperCase() || "ACTIVO";
}

async function fetchRows(ownerKey: string, dayIso: string): Promise<Row[]> {
  const supabase = supabaseAdmin();
  const rows: Row[] = [];
  const dayKey = dayIso.split("-").reverse().join("-");
  for (const column of ["admin_key", "cashier_key"]) {
    const { data, error } = await supabase
      .from("tickets")
      .select("id,admin_key,cashier_key,total_amount,status,estado,draw_date_real,legacy_day_key,created_at,server_created_at")
      .eq(column, ownerKey)
      .or(`draw_date_real.eq.${dayIso},legacy_day_key.eq.${dayKey},legacy_day_key.eq.${dayIso}`)
      .limit(1000);
    if (error) throw error;
    rows.push(...((data ?? []) as Row[]));
  }
  const byId = new Map<string, Row>();
  for (const row of rows) {
    const id = clean(row.id);
    if (id) byId.set(id, row);
  }
  return [...byId.values()];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const ownerKey = clean(body.ownerKey);
    if (!ownerKey) return json({ ok: false, message: "ownerKey requerido." }, 400);

    const dayKey = clean(body.dayKey);
    const dayIso = isoDateFromDayKey(dayKey);
    const rows = await fetchRows(ownerKey, dayIso);
    const byStatus: Record<string, { count: number; total: number }> = {};
    let total = 0;
    for (const row of rows) {
      const key = status(row);
      const rowAmount = amount(row);
      total += rowAmount;
      byStatus[key] = byStatus[key] ?? { count: 0, total: 0 };
      byStatus[key].count += 1;
      byStatus[key].total += rowAmount;
    }

    return json({
      ok: true,
      ownerKey,
      dayKey: dayKey || dayIso,
      count: rows.length,
      total,
      byStatus,
      updatedAt: new Date().toISOString(),
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener resumen de tickets.",
    }, 500);
  }
});
