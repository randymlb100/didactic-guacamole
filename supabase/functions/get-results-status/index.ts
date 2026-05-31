import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type ResultDrawRow = {
  game?: string | null;
  status?: string | null;
  updated_at?: string | null;
  source_payload?: Record<string, unknown> | null;
};

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

function latestVersion(rows: ResultDrawRow[]): string | null {
  return rows.map((row) => clean(row.updated_at)).filter(Boolean).sort().at(-1) ?? null;
}

function isPick(row: ResultDrawRow): boolean {
  const game = clean(row.game).toLowerCase();
  return game === "pick3" || game === "pick4";
}

function isManualOverride(row: ResultDrawRow): boolean {
  const payload = row.source_payload ?? {};
  return payload.isManualOverride === true || clean(payload.source).toLowerCase() === "manual-override";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const dayKey = normalizeDayKey(body.dayKey ?? body.date);
    const { data, error } = await supabaseAdmin()
      .from("result_draws")
      .select("game,status,updated_at,source_payload")
      .eq("result_day_key", dayKey);
    if (error) throw error;

    const rows = (data ?? []) as ResultDrawRow[];

    return json({
      ok: true,
      dayKey,
      source: "result_draws",
      version: latestVersion(rows),
      counts: {
        lotteries: rows.filter((row) => !isPick(row)).length,
        picks: rows.filter(isPick).length,
        overrides: rows.filter(isManualOverride).length,
      },
      updatedAt: {
        lotteries: latestVersion(rows.filter((row) => !isPick(row))),
        picks: latestVersion(rows.filter(isPick)),
        overrides: latestVersion(rows.filter(isManualOverride)),
      },
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener estado de resultados.",
    }, 500);
  }
});
