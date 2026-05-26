import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, fetchKvValue, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

function isAllowedKey(key: string): boolean {
  return /^(cashier_limits|cashier_prize_payouts|recharge_limits|admin_operational_limits):[A-Za-z0-9_.:-]+$/.test(key) ||
    /^system_modes:[A-Za-z0-9_.:-]+$/.test(key) ||
    /^sys_[A-Za-z0-9_.:-]+$/.test(key);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const action = clean(body.action || "fetch");
    const key = clean(body.key);
    if (action === "probe") return json({ ok: true });
    if (!key || !isAllowedKey(key)) return json({ ok: false, message: "Clave de configuracion invalida." }, 400);

    const supabase = supabaseAdmin();
    const { data, error } = await supabase
      .from("lotterynet_master_state")
      .select("payload, updated_at")
      .eq("config_key", key)
      .maybeSingle();
    if (error) throw error;

    if (action === "updated-at") {
      return json({ ok: true, key, updatedAt: data?.updated_at ?? null });
    }

    const fallback = data?.payload === undefined || data?.payload === null ? await fetchKvValue(key) : null;
    return json({ ok: true, key, payload: data?.payload ?? fallback ?? null, updatedAt: data?.updated_at ?? null });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo leer master config." }, 500);
  }
});
