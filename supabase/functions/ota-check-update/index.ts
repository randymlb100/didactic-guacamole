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

const CACHE_TTL_SECONDS = 21_600;
const SIGNED_URL_SECONDS = 86_400;
const EXPECTED_PACKAGE = Deno.env.get("OTA_ANDROID_PACKAGE") ?? "com.lotterynet.pro";
const RELEASE_CHANNEL = Deno.env.get("OTA_RELEASE_CHANNEL") ?? "production";
const SAFE_ROLES = new Set(["master", "admin", "supervisor", "cajero"]);
const APK_HASH = /^[A-Fa-f0-9]{64}$/;

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
      "Cache-Control": status === 200 ? `private, max-age=${CACHE_TTL_SECONDS}` : "no-store",
      "Connection": "keep-alive",
    },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function intValue(value: unknown): number {
  const parsed = Number.parseInt(clean(value), 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function changelogLines(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((item) => clean(item)).filter(Boolean);
  return [];
}

function clientRole(value: unknown): string | null {
  const role = clean(value).toLowerCase();
  return SAFE_ROLES.has(role) ? role : null;
}

function validateRelease(data: Record<string, unknown>) {
  const versionCode = Number(data.version_code);
  const minimumVersion = Number(data.minimum_version);
  const bucket = clean(data.storage_bucket);
  const path = clean(data.storage_path);
  const sha256 = clean(data.apk_sha256);

  if (!Number.isInteger(versionCode) || versionCode <= 0) throw new Error("Release OTA con version_code invalido.");
  if (!Number.isInteger(minimumVersion) || minimumVersion <= 0) throw new Error("Release OTA con minimum_version invalido.");
  if (!bucket || !path.toLowerCase().endsWith(".apk")) throw new Error("Release OTA sin APK valido.");
  if (!APK_HASH.test(sha256)) throw new Error("Release OTA sin SHA-256 valido.");

  return { versionCode, minimumVersion, bucket, path, sha256 };
}

async function logEvent(body: Record<string, unknown>, event: string, targetVersionCode?: number, message?: string) {
  const { error } = await supabase.from("ota_update_logs").insert({
    event,
    user_id: clean(body.userId) || null,
    username: clean(body.username) || null,
    role: clientRole(body.role),
    package_name: EXPECTED_PACKAGE,
    release_channel: RELEASE_CHANNEL,
    current_version_code: intValue(body.currentVersionCode) || null,
    current_version_name: clean(body.currentVersionName) || null,
    target_version_code: targetVersionCode ?? null,
    status: event,
    message: message ?? null,
  });
  if (error) console.warn("ota log failed", error.message);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ message: "Metodo no permitido" }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const currentVersionCode = intValue(body.currentVersionCode);
    const packageName = clean(body.packageName);
    const role = clientRole(body.role);
    if (currentVersionCode <= 0) return json({ message: "Version actual requerida" }, 400);
    if (packageName !== EXPECTED_PACKAGE) {
      return json({ message: "Paquete no autorizado" }, 403);
    }

    const { data, error } = await supabase
      .from("ota_releases")
      .select("version_code, version_name, minimum_version, force_update, title, changelog, allowed_roles, storage_bucket, storage_path, apk_sha256, apk_size_bytes")
      .eq("package_name", EXPECTED_PACKAGE)
      .eq("release_channel", RELEASE_CHANNEL)
      .eq("active", true)
      .order("version_code", { ascending: false })
      .order("published_at", { ascending: false })
      .limit(10);

    if (error) throw error;
    await logEvent(body, "check");

    const release = (data ?? []).find((item) => {
      const allowed = Array.isArray(item.allowed_roles) ? item.allowed_roles.map((entry) => clean(entry).toLowerCase()) : [];
      return allowed.length === 0 || role === null || allowed.includes(role);
    });

    if (!release) {
      return json({ ok: true, updateAvailable: false, cacheTtlSeconds: CACHE_TTL_SECONDS });
    }

    const { versionCode: targetVersionCode, minimumVersion, bucket, path, sha256 } = validateRelease(release);
    const forceUpdate = Boolean(release.force_update) || minimumVersion > currentVersionCode;
    const updateAvailable = targetVersionCode > currentVersionCode || minimumVersion > currentVersionCode;
    if (!updateAvailable) {
      return json({ ok: true, updateAvailable: false, cacheTtlSeconds: CACHE_TTL_SECONDS });
    }

    const { data: signed, error: signedError } = await supabase.storage
      .from(bucket)
      .createSignedUrl(path, SIGNED_URL_SECONDS, {
        download: `lotterynet-${release.version_name}.apk`,
      });
    if (signedError) throw signedError;
    if (!signed?.signedUrl) throw new Error("No se pudo firmar URL de APK.");

    await logEvent(body, "update_available", targetVersionCode);

    return json({
      ok: true,
      updateAvailable: true,
      forceUpdate,
      minimumVersion,
      versionCode: targetVersionCode,
      versionName: release.version_name,
      title: release.title,
      changelog: changelogLines(release.changelog),
      apkUrl: signed.signedUrl,
      apkSha256: sha256,
      apkSizeBytes: Number(release.apk_size_bytes ?? 0),
      cacheTtlSeconds: CACHE_TTL_SECONDS,
    });
  } catch (error) {
    return json({ message: error instanceof Error ? error.message : "No se pudo revisar actualizacion." }, 500);
  }
});
