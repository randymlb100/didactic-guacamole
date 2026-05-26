import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

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
    const key = clean(body.key ?? body.configKey);
    const payload = body.payload;
    if (!key || !isAllowedKey(key)) return json({ ok: false, message: "Clave de configuracion invalida." }, 400);
    if (payload === undefined || payload === null || typeof payload !== "object") {
      return json({ ok: false, message: "Payload de configuracion requerido." }, 400);
    }

    const supabase = supabaseAdmin();
    const { error } = await supabase
      .from("lotterynet_master_state")
      .upsert({ config_key: key, payload, updated_at: new Date().toISOString() }, { onConflict: "config_key" });
    if (error) throw error;
    return json({ ok: true, key });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar master config." }, 500);
  }
});
