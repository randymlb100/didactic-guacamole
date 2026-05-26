import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type KvRow = {
  key: string;
  value: unknown;
  upd?: string | null;
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

function rowsFrom(value: unknown): unknown[] {
  if (!value) return [];
  if (typeof value === "string") {
    try {
      return rowsFrom(JSON.parse(value));
    } catch {
      return [];
    }
  }
  if (Array.isArray(value)) return value;
  if (typeof value !== "object") return [];
  const objectValue = value as Record<string, unknown>;
  if (Array.isArray(objectValue.results)) return objectValue.results;
  if (Array.isArray(objectValue.rows)) return objectValue.rows;
  return [];
}

function versionFrom(row: KvRow | undefined): string | null {
  if (!row) return null;
  return clean(row.upd) || null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const dayKey = normalizeDayKey(body.dayKey);
    const keys = [
      `lot_results_cache_by_day:${dayKey}`,
      `pick_results_cache_by_day:${dayKey}`,
      `manual_results_overrides_by_day:${dayKey}`,
    ];
    const { data, error } = await supabaseAdmin()
      .from("lotterynet_kv")
      .select("key,value,upd")
      .in("key", keys);
    if (error) throw error;

    const byKey = new Map((data ?? []).map((row) => [row.key, row as KvRow]));
    const lotteries = byKey.get(keys[0]);
    const picks = byKey.get(keys[1]);
    const overrides = byKey.get(keys[2]);

    return json({
      ok: true,
      dayKey,
      version: [versionFrom(lotteries), versionFrom(picks), versionFrom(overrides)].filter(Boolean).sort().at(-1) ?? null,
      counts: {
        lotteries: rowsFrom(lotteries?.value).length,
        picks: rowsFrom(picks?.value).length,
        overrides: rowsFrom(overrides?.value).length,
      },
      updatedAt: {
        lotteries: versionFrom(lotteries),
        picks: versionFrom(picks),
        overrides: versionFrom(overrides),
      },
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo obtener estado de resultados.",
    }, 500);
  }
});
