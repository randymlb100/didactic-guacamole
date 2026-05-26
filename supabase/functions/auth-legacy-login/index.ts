import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const service = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  { auth: { persistSession: false } },
);

const publicAuth = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  { auth: { persistSession: false } },
);

const MASTER_SALT = "lotterynet-master-v1";
const MASTER_HASH = "e3f47a15e241ff814b2c8aececb8c1d1e7c8c69a58daa2c58a7ad9d43339f78f";

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

function normalizeRole(value: unknown): string {
  const role = lower(value);
  return role === "cashier" ? "cajero" : role;
}

function isAccountBlocked(account: Record<string, unknown>): boolean {
  return account.activo === false ||
    account.active === false ||
    account.blocked === true ||
    account.disabled === true;
}

function authEmailFor(username: string): string {
  const safe = username
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return `ln-${safe || "user"}@lotterynet.local`;
}

function matchesAccount(account: Record<string, unknown>, idOrUser: string): boolean {
  const needle = idOrUser.trim().toLowerCase();
  if (!needle) return false;
  return lower(account.id) === needle || lower(account.user) === needle;
}

function allAccounts(payload: Record<string, unknown>): Record<string, unknown>[] {
  const supervisors = [
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
  ];
  return [
    ...accountArray(payload.admins),
    ...supervisors,
    ...accountArray(payload.cajeros),
  ].filter((account, index, all) =>
    all.findIndex((other) => lower(other.id) === lower(account.id) || lower(other.user) === lower(account.user)) === index
  );
}

function masterAccount(): Record<string, unknown> {
  return {
    id: "master",
    user: "master",
    role: "master",
    activo: true,
    nombre: "Master",
    passwordSalt: MASTER_SALT,
    passwordHash: MASTER_HASH,
    passwordVersion: "sha256-v1",
  };
}

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function verifyLegacyPassword(account: Record<string, unknown>, password: string): Promise<boolean> {
  const salt = clean(account.passwordSalt);
  const hash = clean(account.passwordHash);
  const version = lower(account.passwordVersion);
  if (!salt || !hash || version !== "sha256-v1") return false;
  return await sha256Hex(`${salt}:${password.trim()}`) === hash.toLowerCase();
}

async function findAuthUserByEmail(email: string): Promise<string | null> {
  const perPage = 1000;
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await service.auth.admin.listUsers({ page, perPage });
    if (error) throw error;
    const found = data.users.find((user) => lower(user.email) === email.toLowerCase());
    if (found) return found.id;
    if (data.users.length < perPage) break;
  }
  return null;
}

async function ensureAuthUser(account: Record<string, unknown>, password: string): Promise<{ id: string; email: string }> {
  const email = authEmailFor(clean(account.user));
  const appMetadata = {
    provider: "legacy-lotterynet",
    legacy_id: clean(account.id),
    username: clean(account.user),
    role: normalizeRole(account.role),
    admin_id: clean(account.adminId),
    admin_user: clean(account.adminUser),
    banca: clean(account.banca),
  };
  const userMetadata = {
    display_name: clean(account.nombre ?? account.name ?? account.displayName),
    username: clean(account.user),
  };

  const existingId = await findAuthUserByEmail(email);
  if (existingId) {
    const { error } = await service.auth.admin.updateUserById(existingId, {
      password,
      email_confirm: true,
      app_metadata: appMetadata,
      user_metadata: userMetadata,
    });
    if (error) throw error;
    return { id: existingId, email };
  }

  const { data, error } = await service.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    app_metadata: appMetadata,
    user_metadata: userMetadata,
  });
  if (error) throw error;
  if (!data.user?.id) throw new Error("Supabase Auth no devolvio usuario.");
  return { id: data.user.id, email };
}

async function signIn(email: string, password: string) {
  const { data, error } = await publicAuth.auth.signInWithPassword({ email, password });
  if (error) throw error;
  if (!data.session?.access_token) throw new Error("Supabase Auth no devolvio JWT.");
  return data.session;
}

async function upsertProfile(account: Record<string, unknown>, authUserId: string) {
  const role = normalizeRole(account.role);
  const { error } = await service.from("profiles").upsert({
    id: authUserId,
    legacy_key: clean(account.id),
    username: clean(account.user),
    display_name: clean(account.nombre ?? account.name ?? account.displayName) || clean(account.user),
    role,
    status: isAccountBlocked(account) ? "bloqueado" : "activo",
    legacy_admin_id: clean(account.adminId) || null,
    legacy_admin_user: clean(account.adminUser) || null,
    legacy_banca: clean(account.banca) || null,
    territory: clean(account.territory ?? account.territorio) || null,
    commission_rate: Number(account.commissionRate ?? account.comision ?? 0) || 0,
    updated_at: new Date().toISOString(),
  }, { onConflict: "id" });
  if (error) throw error;
}

function replaceAccountWithAuth(
  accounts: Record<string, unknown>[],
  account: Record<string, unknown>,
  authUserId: string,
): Record<string, unknown>[] {
  return accounts.map((candidate) => {
    if (!matchesAccount(candidate, clean(account.id)) && !matchesAccount(candidate, clean(account.user))) return candidate;
    return { ...candidate, authUserId, authEmail: authEmailFor(clean(account.user)), updatedAt: Date.now() };
  });
}

async function saveAuthLink(account: Record<string, unknown>, authUserId: string) {
  const { data, error: readError } = await service
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (readError) throw readError;
  const payload = (data?.payload ?? {}) as Record<string, unknown>;
  const admins = accountArray(payload.admins);
  const supervisors = [
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
  ].filter((candidate, index, all) =>
    all.findIndex((other) => lower(other.id) === lower(candidate.id) || lower(other.user) === lower(candidate.user)) === index
  );
  const cajeros = accountArray(payload.cajeros);
  const nextPayload = {
    ...payload,
    admins: replaceAccountWithAuth(admins, account, authUserId),
    supervisores: replaceAccountWithAuth(supervisors, account, authUserId),
    cajeros: replaceAccountWithAuth(cajeros, account, authUserId),
  };
  const { error } = await service
    .from("lotterynet_users_state")
    .upsert({ scope: "global", payload: nextPayload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
  if (error) throw error;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ message: "Metodo no permitido" }, 405);
  try {
    const body = await req.json().catch(() => ({}));
    const username = clean(body.username);
    const password = clean(body.password);
    if (!username || !password) return json({ message: "Usuario y clave requeridos." }, 400);

    const { data, error } = await service
      .from("lotterynet_users_state")
      .select("payload")
      .eq("scope", "global")
      .maybeSingle();
    if (error) throw error;
    const payload = (data?.payload ?? {}) as Record<string, unknown>;
    const account = username.toLowerCase() === "master"
      ? masterAccount()
      : allAccounts(payload).find((candidate) => matchesAccount(candidate, username));
    if (!account) return json({ message: "Usuario no encontrado." }, 404);
    if (isAccountBlocked(account)) return json({ message: "Usuario bloqueado." }, 403);
    if (!await verifyLegacyPassword(account, password)) return json({ message: "Credenciales invalidas." }, 401);

    const authUser = await ensureAuthUser(account, password);
    const session = await signIn(authUser.email, password);
    await upsertProfile(account, authUser.id);
    await saveAuthLink(account, authUser.id);

    return json({
      ok: true,
      authUserId: authUser.id,
      authEmail: authUser.email,
      accessToken: session.access_token,
      refreshToken: session.refresh_token,
      expiresAt: session.expires_at ?? null,
      expiresIn: session.expires_in ?? null,
      tokenType: session.token_type ?? "bearer",
      user: {
        id: clean(account.id),
        username: clean(account.user),
        role: normalizeRole(account.role),
        adminId: clean(account.adminId),
        adminUser: clean(account.adminUser),
        banca: clean(account.banca),
      },
    });
  } catch (error) {
    return json({ message: error instanceof Error ? error.message : "No se pudo activar Supabase Auth." }, 500);
  }
});
