import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, json, requireAdminRole, requireSharedSecret, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const secretError = requireSharedSecret(req);
  if (secretError) return secretError;

  try {
    if (req.method === "GET") {
      const supabase = supabaseAdmin();
      const { data, error } = await supabase
        .from("lotterynet_users_state")
        .select("payload, updated_at")
        .eq("scope", "global")
        .maybeSingle();
      if (error) throw error;
      return json({ ok: true, scope: "global", payload: data?.payload ?? null, updatedAt: data?.updated_at ?? null });
    }

    if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
    const body = await req.json().catch(() => ({}));
    const roleError = requireAdminRole(body.actorRole);
    if (roleError) return roleError;
    const payload = body.payload;
    if (!payload || typeof payload !== "object") {
      return json({ ok: false, message: "Payload de usuarios requerido." }, 400);
    }
    const supabase = supabaseAdmin();
    const { error } = await supabase
      .from("lotterynet_users_state")
      .upsert({ scope: "global", payload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
    if (error) throw error;
    return json({ ok: true, scope: "global" });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar users state." }, 500);
  }
});
