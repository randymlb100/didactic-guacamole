import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, requireAdminRole, requireSharedSecret, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const secretError = requireSharedSecret(req);
  if (secretError) return secretError;

  try {
    if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
    const body = await req.json().catch(() => ({}));
    const roleError = requireAdminRole(body.actorRole);
    if (roleError) return roleError;

    const configKey = clean(body.configKey);
    const payload = body.payload;
    if (!configKey || !payload || typeof payload !== "object") {
      return json({ ok: false, message: "configKey y payload son requeridos." }, 400);
    }

    const supabase = supabaseAdmin();
    const { error } = await supabase
      .from("lotterynet_master_state")
      .upsert({ config_key: configKey, payload, updated_at: new Date().toISOString() }, { onConflict: "config_key" });
    if (error) throw error;
    return json({ ok: true, configKey });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar master state." }, 500);
  }
});
