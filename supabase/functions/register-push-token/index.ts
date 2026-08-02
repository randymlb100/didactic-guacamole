import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { authenticatedActor, clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

async function sha256(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  const auth = await authenticatedActor(req, ["admin", "master", "supervisor", "cajero"]);
  if (!auth.ok) return auth.response;

  try {
    const body = await req.json().catch(() => ({}));
    const token = clean(body.token);
    const platform = clean(body.platform) || "android";
    const ownerKeyHash = clean(body.ownerKeyHash);
    if (!token || !ownerKeyHash) {
      return json({ ok: false, message: "Token push requerido." }, 400);
    }

    const tokenHash = await sha256(token);
    const supabase = supabaseAdmin();
    const { error } = await supabase
      .from("lotterynet_push_tokens")
      .upsert(
        {
          token_hash: tokenHash,
          token,
          auth_user_id: auth.actor.userId,
          role: auth.actor.role,
          owner_key_hash: ownerKeyHash,
          platform,
          enabled: true,
          last_seen_at: new Date().toISOString(),
        },
        { onConflict: "token_hash" },
      );
    if (error) throw error;
    return json({ ok: true });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo registrar push." }, 500);
  }
});
