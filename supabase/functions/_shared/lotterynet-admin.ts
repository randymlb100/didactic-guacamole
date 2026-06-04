import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-lotterynet-admin-secret, x-lotterynet-results-secret",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
};

export function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

export function clean(value: unknown): string {
  return String(value ?? "").trim();
}

export function lower(value: unknown): string {
  return clean(value).toLowerCase();
}

export function requireSharedSecret(req: Request): Response | null {
  const expected = Deno.env.get("LOTTERYNET_ADMIN_SHARED_SECRET") ?? "";
  if (!expected) {
    return json({ ok: false, message: "Server shared secret is not configured." }, 500);
  }
  const provided = req.headers.get("x-lotterynet-admin-secret") ?? "";
  if (provided !== expected) {
    return json({ ok: false, message: "Shared secret is invalid." }, 403);
  }
  return null;
}

export function requireAdminRole(role: unknown): Response | null {
  const normalized = lower(role);
  if (normalized !== "admin" && normalized !== "master") {
    return json({ ok: false, message: "Admin role required." }, 403);
  }
  return null;
}

export function bearerToken(req: Request): string {
  const header = req.headers.get("Authorization") ?? "";
  return header.replace(/^Bearer\s+/i, "").trim();
}

export async function requireAdminJwt(req: Request): Promise<Response | null> {
  const token = bearerToken(req);
  if (!token) return json({ ok: false, message: "Sesion admin requerida." }, 401);
  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return json({ ok: false, message: "Sesion admin invalida." }, 401);
  const role = lower(data.user.app_metadata?.role);
  if (role !== "admin" && role !== "master") {
    return json({ ok: false, message: "Admin role required." }, 403);
  }
  return null;
}

export function supabaseAdmin() {
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
  return createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    serviceKey,
    { auth: { persistSession: false } },
  );
}

export async function fetchKvValue(key: string): Promise<unknown> {
  const supabase = supabaseAdmin();
  const { data, error } = await supabase
    .from("lotterynet_kv")
    .select("value")
    .eq("key", key)
    .maybeSingle();
  if (error) throw error;
  return data?.value ?? null;
}

export async function upsertKvValue(key: string, value: unknown): Promise<void> {
  const supabase = supabaseAdmin();
  const storedValue = typeof value === "string" ? value : JSON.stringify(value);
  const { error } = await supabase
    .from("lotterynet_kv")
    .upsert({ key, value: storedValue, upd: new Date().toISOString() }, { onConflict: "key" });
  if (error) throw error;
}
