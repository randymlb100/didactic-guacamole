import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

function isAllowedKey(key: string): boolean {
  return /^(cashier_limits|cashier_prize_payouts|recharge_limits|admin_operational_limits|system_modes|manual_disabled_lotteries):[A-Za-z0-9_.:-]+$/.test(key) ||
    /^sys_[A-Za-z0-9_.:-]+$/.test(key);
}

function normalizeSystemModePayload(key: string, payload: Record<string, unknown>): Record<string, unknown> {
  if (!key.startsWith("system_modes:")) return payload;
  const normalized: Record<string, unknown> = { ...payload, configured: true };

  if (typeof normalized.lotteryModeEnabled !== "boolean") normalized.lotteryModeEnabled = true;
  if (typeof normalized.pickModeEnabled !== "boolean") normalized.pickModeEnabled = false;
  if (typeof normalized.cashierModeEnabled !== "boolean") {
    normalized.cashierModeEnabled = normalized.cashierPickEnabled === true;
  }
  if (typeof normalized.cashierLotteryModeEnabled !== "boolean") normalized.cashierLotteryModeEnabled = true;
  if (typeof normalized.cashierPickModeEnabled !== "boolean") {
    normalized.cashierPickModeEnabled = normalized.cashierPickEnabled === true;
  }

  if (normalized.lotteryModeEnabled !== true && normalized.pickModeEnabled !== true) {
    normalized.lotteryModeEnabled = true;
    normalized.pickModeEnabled = false;
  }
  if (normalized.cashierLotteryModeEnabled !== true && normalized.cashierPickModeEnabled !== true) {
    normalized.cashierLotteryModeEnabled = true;
    normalized.cashierPickModeEnabled = false;
  }
  if (normalized.cashierModeEnabled !== true) {
    normalized.cashierPickEnabled = false;
    normalized.cashierLotteryModeEnabled = true;
    normalized.cashierPickModeEnabled = false;
  } else {
    normalized.cashierPickEnabled = normalized.cashierPickModeEnabled === true;
  }
  if (!Array.isArray(normalized.blockedSalePlays)) normalized.blockedSalePlays = [];
  if (typeof normalized.updatedAt !== "number") normalized.updatedAt = Date.now();

  return normalized;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    if (clean(body.action) === "probe") return json({ ok: true, version: "manual-disabled-lotteries-v2" });
    const key = clean(body.key ?? body.configKey);
    const payload = body.payload;
    if (!key || !isAllowedKey(key)) return json({ ok: false, message: "Clave de configuracion invalida." }, 400);
    if (payload === undefined || payload === null || typeof payload !== "object") {
      return json({ ok: false, message: "Payload de configuracion requerido." }, 400);
    }

    const supabase = supabaseAdmin();
    const normalizedPayload = normalizeSystemModePayload(key, payload as Record<string, unknown>);
    const { error } = await supabase
      .from("lotterynet_master_state")
      .upsert({ config_key: key, payload: normalizedPayload, updated_at: new Date().toISOString() }, { onConflict: "config_key" });
    if (error) throw error;
    return json({ ok: true, key });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar master config." }, 500);
  }
});
