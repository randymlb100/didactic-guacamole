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
    account.adminId,
    account.adminUser,
    account.adminKey,
    account.cashierId,
    account.cashierUser,
    account.cashierKey,
    account.ownerAdminId,
    account.parentAdminId,
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
  const metadataMatches = [metadata.legacy_id, metadata.username, metadata.user, metadata.admin_id, metadata.admin_user]
    .some((candidate) => lower(candidate) === lower(actorKey));

  const { data: state, error: stateError } = await supabase
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (stateError) return { ok: false, message: stateError.message };

  const payload = (state?.payload ?? {}) as Record<string, unknown>;
  const accounts = allAccounts(payload);
  const actor = accounts.find((account) => accountMatches(account, actorKey)) ??
    accounts.find((account) =>
      [metadata.legacy_id, metadata.username, metadata.user, metadata.admin_id, metadata.admin_user]
        .some((candidate) => accountMatches(account, clean(candidate)))
    );
  if (!actor) {
    return metadataMatches ? { ok: true } : { ok: false, message: "Usuario no existe en servidor." };
  }
  if (actor.activo === false || actor.active === false || actor.blocked === true || actor.disabled === true) {
    return { ok: false, message: "Usuario bloqueado." };
  }

  const linkedAuthUser = clean(actor.authUserId ?? actor.auth_user_id);
  if (linkedAuthUser && linkedAuthUser !== data.user.id) {
    return { ok: false, message: "Sesion no pertenece al usuario que intenta anular." };
  }
  return { ok: true };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const actorKey = clean(body.actorKey ?? body.adminKey ?? body.cashierKey);
    if (!actorKey) return json({ ok: false, message: "actorKey requerido." }, 400);

    const authActor = await authenticatedActor(req, actorKey);
    if (!authActor.ok) return json({ ok: false, message: authActor.message ?? "Usuario no autorizado." }, 403);

    const { data, error } = await supabase.rpc("ln_void_ticket_legacy", { p_body: body });
    if (error) throw error;

    const payload = (data ?? { ok: true }) as Record<string, unknown>;
    return json(payload, Number(payload.status ?? 200));
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo procesar el ticket en servidor.",
    }, 500);
  }
});
