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

const MAX_PHONE_CLOCK_SKEW_MS = 15 * 60 * 1000;

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function lower(value: unknown): string {
  return clean(value).toLowerCase();
}

function bearerToken(req: Request): string {
  return (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function accountArray(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value as Record<string, unknown>[] : [];
}

function allAccounts(payload: Record<string, unknown>): Record<string, unknown>[] {
  return [
    ...accountArray(payload.users),
    ...accountArray(payload.admins),
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
    ...accountArray(payload.cajeros),
    ...accountArray(payload.cashiers),
  ];
}

function accountMatches(account: Record<string, unknown>, value: string): boolean {
  const needle = lower(value);
  if (!needle) return false;
  return [
    account.id,
    account.user,
    account.username,
    account.displayName,
    account.userId,
    account.authUserId,
    account.auth_user_id,
  ].some((candidate) => lower(candidate) === needle);
}

async function authenticatedActor(req: Request, actorKey: string): Promise<{ ok: boolean; message?: string }> {
  const token = bearerToken(req);
  if (!token) return { ok: false, message: "Sesion del servidor requerida." };

  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) return { ok: false, message: "Sesion del servidor invalida." };

  const metadata = data.user.app_metadata ?? {};
  if ([metadata.legacy_id, metadata.username, metadata.user, metadata.admin_id, metadata.admin_user]
    .some((candidate) => lower(candidate) === lower(actorKey))) {
    return { ok: true };
  }

  const { data: state, error: stateError } = await supabase
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (stateError) return { ok: false, message: stateError.message };

  const payload = (state?.payload ?? {}) as Record<string, unknown>;
  const actor = allAccounts(payload).find((account) => accountMatches(account, actorKey));
  if (!actor) return { ok: false, message: "Usuario no existe en servidor." };
  if (actor.activo === false || actor.active === false || actor.blocked === true || actor.disabled === true) {
    return { ok: false, message: "Usuario bloqueado." };
  }

  const linkedAuthUser = clean(actor.authUserId ?? actor.auth_user_id);
  if (linkedAuthUser && linkedAuthUser !== data.user.id) {
    return { ok: false, message: "Sesion no pertenece al usuario de la venta." };
  }
  return { ok: true };
}

function sanitizeCreateTicketBody(body: Record<string, unknown>): Record<string, unknown> {
  const sanitized = { ...body };
  const sorteoId = clean(sanitized.sorteoId);
  if (sorteoId && !isUuid(sorteoId)) {
    delete sanitized.sorteoId;
  }
  return sanitized;
}

function validatePhoneClock(body: Record<string, unknown>): { ok: boolean; message?: string; serverNow?: string; phoneTime?: string; skewMs?: number } {
  const rawPhoneTime = clean(body.phoneTime);
  if (!rawPhoneTime) {
    return { ok: false, message: "Hora del celular requerida para vender." };
  }

  const phoneTimeMs = Date.parse(rawPhoneTime);
  if (!Number.isFinite(phoneTimeMs)) {
    return { ok: false, message: "Hora del celular invalida." };
  }

  const serverNowMs = Date.now();
  const skewMs = Math.abs(serverNowMs - phoneTimeMs);
  if (skewMs > MAX_PHONE_CLOCK_SKEW_MS) {
    return {
      ok: false,
      message: "Hora del celular no confiable. Activa fecha y hora automatica e intenta de nuevo.",
      serverNow: new Date(serverNowMs).toISOString(),
      phoneTime: new Date(phoneTimeMs).toISOString(),
      skewMs,
    };
  }

  return {
    ok: true,
    serverNow: new Date(serverNowMs).toISOString(),
    phoneTime: new Date(phoneTimeMs).toISOString(),
    skewMs,
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const actorKey = clean(body.actorKey ?? body.cashierKey);
    if (!actorKey) return json({ ok: false, message: "actorKey requerido." }, 400);

    const authActor = await authenticatedActor(req, actorKey);
    if (!authActor.ok) return json({ ok: false, message: authActor.message ?? "Usuario no autorizado." }, 403);

    const phoneClock = validatePhoneClock(body);
    if (!phoneClock.ok) {
      return json({
        ok: false,
        message: phoneClock.message,
        clock: {
          allowed: false,
          serverNow: phoneClock.serverNow,
          phoneTime: phoneClock.phoneTime,
          skewMs: phoneClock.skewMs,
          maxSkewMs: MAX_PHONE_CLOCK_SKEW_MS,
        },
      }, 409);
    }

    const { data, error } = await supabase.rpc("ln_create_ticket_legacy", { p_body: sanitizeCreateTicketBody(body) });
    if (error) throw error;

    const payload = (data ?? { ok: true }) as Record<string, unknown>;
    return json(payload, Number(payload.status ?? 200));
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo crear el ticket en servidor.",
    }, 500);
  }
});
