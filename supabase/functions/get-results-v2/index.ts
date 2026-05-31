import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

function normalizeDayKey(value: unknown): string {
  const raw = clean(value);
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) return raw;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-");
    return `${day}-${month}-${year}`;
  }
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date()).replaceAll("/", "-");
}

function rowsFromRpc(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string") {
    try {
      return rowsFromRpc(JSON.parse(value));
    } catch {
      return [];
    }
  }
  return [];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const dayKey = normalizeDayKey(body.dayKey ?? body.date);
    const { data, error } = await supabaseAdmin()
      .rpc("lotterynet_result_draws_payload", { p_raw_date: dayKey });
    if (error) throw error;

    const results = rowsFromRpc(data);
    return json({
      ok: true,
      dayKey,
      date: dayKey,
      source: "result_draws",
      results,
      rows: results,
      count: results.length,
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener resultados.",
    }, 500);
  }
});
