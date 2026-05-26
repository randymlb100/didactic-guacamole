import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, json, requireAdminJwt, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const supabase = supabaseAdmin();
    if (req.method === "GET") {
      const { data, error } = await supabase
        .from("lotterynet_users_state")
        .select("payload, updated_at")
        .eq("scope", "global")
        .maybeSingle();
      if (error) throw error;
      return json({ ok: true, scope: "global", payload: data?.payload ?? null, updatedAt: data?.updated_at ?? null });
    }

    if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
    const authError = await requireAdminJwt(req);
    if (authError) return authError;

    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "upsert").trim().toLowerCase();
    if (action === "fetch") {
      const { data, error } = await supabase
        .from("lotterynet_users_state")
        .select("payload, updated_at")
        .eq("scope", "global")
        .maybeSingle();
      if (error) throw error;
      return json({ ok: true, scope: "global", payload: data?.payload ?? null, updatedAt: data?.updated_at ?? null });
    }
    if (action !== "upsert") return json({ ok: false, message: "Accion no permitida." }, 400);

    const payload = body.payload;
    if (!payload || typeof payload !== "object") {
      return json({ ok: false, message: "Payload de usuarios requerido." }, 400);
    }
    const { error } = await supabase
      .from("lotterynet_users_state")
      .upsert({ scope: "global", payload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
    if (error) throw error;
    return json({ ok: true, scope: "global" });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar users state." }, 500);
  }
});
