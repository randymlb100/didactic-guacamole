import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  { auth: { persistSession: false } },
);

const allowedEvents = new Set([
  "check",
  "update_available",
  "download_started",
  "download_completed",
  "install_opened",
  "dismissed",
  "error",
]);
const expectedPackage = Deno.env.get("OTA_ANDROID_PACKAGE") ?? "com.lotterynet.pro";
const releaseChannel = Deno.env.get("OTA_RELEASE_CHANNEL") ?? "production";
const safeRoles = new Set(["master", "admin", "supervisor", "cajero"]);

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Connection": "keep-alive" },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function intOrNull(value: unknown): number | null {
  const parsed = Number.parseInt(clean(value), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function roleOrNull(value: unknown): string | null {
  const role = clean(value).toLowerCase();
  return safeRoles.has(role) ? role : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ message: "Metodo no permitido" }, 405);
  try {
    const body = await req.json().catch(() => ({}));
    const event = clean(body.event).toLowerCase();
    if (!allowedEvents.has(event)) return json({ message: "Evento OTA invalido" }, 400);
    const packageName = clean(body.packageName);
    if (packageName !== expectedPackage) return json({ message: "Paquete no autorizado" }, 403);

    const { error } = await supabase.from("ota_update_logs").insert({
      event,
      user_id: clean(body.userId) || null,
      username: clean(body.username) || null,
      role: roleOrNull(body.role),
      package_name: expectedPackage,
      release_channel: releaseChannel,
      current_version_code: intOrNull(body.currentVersionCode),
      current_version_name: clean(body.currentVersionName) || null,
      target_version_code: intOrNull(body.targetVersionCode),
      status: clean(body.status) || event,
      message: clean(body.message) || null,
    });
    if (error) throw error;
    return json({ ok: true });
  } catch (error) {
    return json({ message: error instanceof Error ? error.message : "No se pudo registrar evento OTA." }, 500);
  }
});
