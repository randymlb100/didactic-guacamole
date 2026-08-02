import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";
import { authenticatedActor, canAccessOwner, type AuthenticatedActor } from "../_shared/lotterynet-admin.ts";

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
    headers: { ...corsHeaders, "Content-Type": "application/json", "Connection": "keep-alive" },
  });
}

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function lower(value: unknown): string {
  return clean(value).toLowerCase();
}

function accountArray(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value as Record<string, unknown>[] : [];
}

function matchesAccount(account: Record<string, unknown>, idOrUser: string): boolean {
  const needle = idOrUser.trim().toLowerCase();
  if (!needle) return false;
  return lower(account.id) === needle || lower(account.user) === needle;
}

function roleOf(account: Record<string, unknown>): string {
  const role = lower(account.role);
  return role === "cashier" ? "cajero" : role;
}

function sameNetwork(actor: Record<string, unknown>, target: Record<string, unknown>): boolean {
  return (!!clean(actor.id) && lower(target.adminId) === lower(actor.id)) ||
    (!!clean(actor.user) && lower(target.adminUser) === lower(actor.user)) ||
    (!!clean(actor.banca) && lower(target.banca) === lower(actor.banca));
}

function canChangePassword(
  actor: AuthenticatedActor,
  target: Record<string, unknown>,
): boolean {
  if (actor.role === "master") return true;
  if (actor.role !== "admin") return false;
  const targetRole = roleOf(target);
  return (targetRole === "supervisor" || targetRole === "cashier" || targetRole === "cajero") &&
    (
      canAccessOwner(actor, clean(target.id)) ||
      canAccessOwner(actor, clean(target.user)) ||
      canAccessOwner(actor, clean(target.username)) ||
      actor.ownerKeys.some((ownerKey) =>
        sameNetwork({ id: ownerKey, user: ownerKey, banca: ownerKey }, target)
      )
    );
}

function randomHex(length: number): string {
  const bytes = new Uint8Array(Math.ceil(Math.max(length, 2) / 2));
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("").slice(0, length);
}

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function passwordSecret(password: string): Promise<Record<string, unknown>> {
  const salt = randomHex(16);
  return {
    passwordSalt: salt,
    passwordHash: await sha256Hex(`${salt}:${password.trim()}`),
    passwordVersion: "sha256-v1",
    credChangedAt: Date.now(),
    updatedAt: Date.now(),
  };
}

function replaceAccount(
  accounts: Record<string, unknown>[],
  target: Record<string, unknown>,
  secret: Record<string, unknown>,
): Record<string, unknown>[] {
  return accounts.map((account) => {
    if (!matchesAccount(account, clean(target.id)) && !matchesAccount(account, clean(target.user))) return account;
    return { ...account, ...secret };
  });
}

async function updateAuthIfPossible(target: Record<string, unknown>, password: string): Promise<boolean> {
  const rawId = clean(target.authUserId ?? target.auth_id ?? target.profileId ?? target.profile_id ?? target.id);
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!uuidPattern.test(rawId)) return false;
  const { error } = await supabase.auth.admin.updateUserById(rawId, { password });
  if (error) throw error;
  return true;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ message: "Metodo no permitido" }, 405);
  try {
    const auth = await authenticatedActor(req, ["admin", "master"]);
    if (!auth.ok) return auth.response;

    const body = await req.json().catch(() => ({}));
    const action = lower(body.action);
    const targetId = clean(body.targetId);
    const targetUser = clean(body.targetUser);
    const newPassword = clean(body.newPassword);
    if (newPassword.length < 6) return json({ message: "La clave debe tener 6 caracteres o mas." }, 400);

    const { data, error } = await supabase
      .from("lotterynet_users_state")
      .select("payload")
      .eq("scope", "global")
      .maybeSingle();
    if (error) throw error;
    const payload = (data?.payload ?? {}) as Record<string, unknown>;
    const admins = accountArray(payload.admins);
    const supervisors = [
      ...accountArray(payload.supervisores),
      ...accountArray(payload.supervisors),
    ].filter((account, index, all) =>
      all.findIndex((other) => lower(other.id) === lower(account.id) || lower(other.user) === lower(account.user)) === index
    );
    const cashiers = accountArray(payload.cajeros);

    if (action === "change-cashier-group-password") {
      if (auth.actor.role !== "master") return json({ message: "Solo Master puede cambiar la clave grupal." }, 403);
      const adminId = clean(body.adminId);
      const admin = admins.find((account) => matchesAccount(account, adminId));
      if (!admin) return json({ message: "No se encontro la banca." }, 404);
      const group = cashiers.filter((cashier) =>
        lower(cashier.adminId) === lower(admin.id) ||
        lower(cashier.adminUser) === lower(admin.user) ||
        lower(cashier.banca) === lower(admin.banca)
      );
      if (group.length === 0) return json({ message: "Esta banca no tiene cajeros." }, 400);

      const authResults: Record<string, unknown>[] = [];
      for (const cashier of group) {
        try {
          authResults.push({
            id: clean(cashier.id),
            user: clean(cashier.user),
            authUpdated: await updateAuthIfPossible(cashier, newPassword),
          });
        } catch (error) {
          return json({
            ok: false,
            partial: true,
            updated: authResults,
            failedUser: clean(cashier.user),
            message: error instanceof Error ? error.message : "No se pudo actualizar Auth.",
          }, 502);
        }
      }

      const secretById = new Map<string, Record<string, unknown>>();
      for (const cashier of group) secretById.set(lower(cashier.id), await passwordSecret(newPassword));
      const nextCashiers = cashiers.map((cashier) => {
        const secret = secretById.get(lower(cashier.id));
        return secret ? { ...cashier, ...secret } : cashier;
      });
      const { error: groupSaveError } = await supabase
        .from("lotterynet_users_state")
        .upsert({ scope: "global", payload: { ...payload, cajeros: nextCashiers }, updated_at: new Date().toISOString() }, { onConflict: "scope" });
      if (groupSaveError) throw groupSaveError;

      return json({
        ok: true,
        action,
        adminId: clean(admin.id),
        updated: authResults,
        updatedCount: group.length,
        authUpdatedCount: authResults.filter((item) => item.authUpdated === true).length,
        payloadConfirmed: true,
        message: "Clave grupal actualizada.",
      });
    }

    if (!targetId && !targetUser) return json({ message: "Usuario destino requerido" }, 400);
    const allAccounts = [...admins, ...supervisors, ...cashiers];
    const target = allAccounts.find((account) =>
      matchesAccount(account, targetId) || matchesAccount(account, targetUser)
    );
    if (!target) return json({ message: "No se encontro el usuario." }, 404);
    if (!canChangePassword(auth.actor, target)) {
      return json({ message: "No tiene permiso para cambiar esta clave." }, 403);
    }

    const secret = await passwordSecret(newPassword);
    const authUpdated = await updateAuthIfPossible(target, newPassword);
    const nextPayload = {
      ...payload,
      admins: replaceAccount(admins, target, secret),
      supervisores: replaceAccount(supervisors, target, secret),
      cajeros: replaceAccount(cashiers, target, secret),
    };
    const { error: saveError } = await supabase
      .from("lotterynet_users_state")
      .upsert({ scope: "global", payload: nextPayload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
    if (saveError) throw saveError;

    return json({
      ok: true,
      targetId: clean(target.id),
      targetUser: clean(target.user),
      targetRole: roleOf(target),
      authUpdated,
      message: "Clave actualizada",
    });
  } catch (error) {
    return json({ message: error instanceof Error ? error.message : "No se pudo cambiar la clave." }, 500);
  }
});
