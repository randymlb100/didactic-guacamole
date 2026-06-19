import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";
import { redisGetJson, redisSetJson } from "./upstash-redis.ts";

export type JsonRecord = Record<string, unknown>;

export type AuthenticatedActor = {
  userId: string;
  role: string;
  metadata: JsonRecord;
  account: JsonRecord | null;
  accounts: JsonRecord[];
  identityKeys: string[];
  ownerKeys: string[];
};

export type CanonicalOwnerScope = {
  canonicalOwnerKey: string;
  ownerKeys: string[];
  cashierKeys: string[];
};

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

export function validIdentityKey(value: unknown): string {
  const key = clean(value);
  if (!key) return "";
  const normalized = key.toLowerCase();
  return normalized === "null" || normalized === "undefined" ? "" : key;
}

export function lower(value: unknown): string {
  return clean(value).toLowerCase();
}

export function sameText(a: unknown, b: unknown): boolean {
  const left = lower(a);
  const right = lower(b);
  return !!left && left === right;
}

export function asRecord(value: unknown): JsonRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonRecord : {};
}

export function accountArray(value: unknown): JsonRecord[] {
  return Array.isArray(value) ? value as JsonRecord[] : [];
}

export function normalizeRole(value: unknown): string {
  const role = lower(value);
  if (role === "cashier" || role === "cashiers" || role === "cajeros") return "cajero";
  if (role === "admins") return "admin";
  if (role === "masters") return "master";
  if (role === "supervisors" || role === "supervisores") return "supervisor";
  return role;
}

export function roleOf(account: JsonRecord | null | undefined): string {
  return normalizeRole(account?.role ?? account?._source);
}

export function isBlockedAccount(account: JsonRecord): boolean {
  return account.activo === false ||
    account.active === false ||
    account.blocked === true ||
    account.disabled === true;
}

export function accountIdentityKeys(account: JsonRecord | null | undefined): string[] {
  if (!account) return [];
  return [
    account.id,
    account.user,
    account.username,
    account.userId,
    account.authUserId,
    account.auth_user_id,
    account.legacy_id,
    account.legacy_key,
  ].map(validIdentityKey).filter(Boolean);
}

export function accountOwnerKeys(account: JsonRecord | null | undefined): string[] {
  if (!account) return [];
  const role = roleOf(account);
  const keys = role === "admin" || role === "master"
    ? [
      account.id,
      account.user,
      account.username,
      account.banca,
      account.legacy_key,
      account.legacy_admin_id,
      account.legacy_admin_user,
    ]
    : [
      account.adminId,
      account.adminUser,
      account.admin_id,
      account.admin_user,
      account.banca,
      account.id,
      account.user,
      account.username,
    ];
  return keys.map(validIdentityKey).filter(Boolean);
}

export function metadataIdentityKeys(metadata: JsonRecord): string[] {
  return [
    metadata.legacy_id,
    metadata.legacy_key,
    metadata.username,
    metadata.user,
    metadata.admin_id,
    metadata.admin_user,
    metadata.banca,
  ].map(validIdentityKey).filter(Boolean);
}

export function uniqueKeys(values: unknown[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values.map(validIdentityKey).filter(Boolean)) {
    const key = value.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(value);
  }
  return result;
}

export function flattenLotterynetAccounts(payload: JsonRecord): JsonRecord[] {
  const legacyUsers = [
    ...accountArray(payload.users),
    ...accountArray(payload.admins),
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
    ...accountArray(payload.cajeros),
    ...accountArray(payload.cashiers),
  ];
  const seen = new Set<string>();
  return legacyUsers.filter((account) => {
    const key = uniqueKeys([account.authUserId, account.auth_user_id, account.id, account.user, account.username])
      .join(":")
      .toLowerCase();
    if (!key) return true;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function accountMatchesAny(account: JsonRecord, keys: string[]): boolean {
  return accountIdentityKeys(account).some((candidate) => keys.some((key) => sameText(candidate, key)));
}

export function findAccountByKey(accounts: JsonRecord[], key: unknown): JsonRecord | null {
  const needle = validIdentityKey(key);
  if (!needle) return null;
  return accounts.find((account) =>
    accountIdentityKeys(account).some((candidate) => sameText(candidate, needle)) ||
    accountOwnerKeys(account).some((candidate) => sameText(candidate, needle))
  ) ?? null;
}

export function canonicalOwnerScope(
  actor: AuthenticatedActor,
  requestedOwner: string,
): CanonicalOwnerScope {
  const requested = validIdentityKey(requestedOwner);
  if (!requested) {
    return { canonicalOwnerKey: "", ownerKeys: [], cashierKeys: [] };
  }

  const requestedAccount = findAccountByKey(actor.accounts, requested);
  const requestedRole = roleOf(requestedAccount);
  const cashierAccount = requestedRole === "cajero" ? requestedAccount : actor.role === "cajero" ? actor.account : null;
  const adminReferenceKeys = uniqueKeys([
    cashierAccount?.adminId,
    cashierAccount?.adminUser,
    cashierAccount?.admin_id,
    cashierAccount?.admin_user,
    requestedAccount?.legacy_admin_id,
    requestedAccount?.legacy_admin_user,
    actor.account?.adminId,
    actor.account?.adminUser,
    actor.account?.admin_id,
    actor.account?.admin_user,
  ]);
  const adminAccount = requestedRole === "admin" || requestedRole === "master"
    ? requestedAccount
    : adminReferenceKeys.map((key) => findAccountByKey(actor.accounts, key)).find(Boolean) ?? null;
  const canonicalCandidates = uniqueKeys([
    adminAccount?.legacy_admin_id,
    adminAccount?.legacy_key,
    adminAccount?.id,
    ...adminReferenceKeys,
    requestedRole === "admin" || requestedRole === "master" ? requested : "",
    ...actor.ownerKeys,
  ]);
  const canonicalOwnerKey = canonicalCandidates.find((key) => /^ADM-/i.test(key))
    ?? canonicalCandidates[0]
    ?? requested;
  const ownerKeys = uniqueKeys([
    canonicalOwnerKey,
    requested,
    ...canonicalCandidates,
    ...accountOwnerKeys(adminAccount),
    ...accountIdentityKeys(adminAccount),
    ...accountOwnerKeys(requestedAccount),
  ]);
  const cashierKeys = uniqueKeys([
    ...accountIdentityKeys(cashierAccount),
    cashierAccount?.legacy_key,
  ]);

  return { canonicalOwnerKey, ownerKeys, cashierKeys };
}

export function belongsToAdmin(target: JsonRecord, admin: JsonRecord): boolean {
  return accountMatchesAny(target, accountIdentityKeys(admin)) ||
    sameText(target.adminId, admin.id) ||
    sameText(target.adminUser, admin.user ?? admin.username) ||
    sameText(target.admin_id, admin.id) ||
    sameText(target.admin_user, admin.user ?? admin.username) ||
    (!!clean(target.banca) && !!clean(admin.banca) && sameText(target.banca, admin.banca));
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
  const auth = await authenticatedActor(req, ["admin", "master"]);
  if (!auth.ok) return auth.response;
  return null;
}

export async function readLotterynetUsersPayload(): Promise<JsonRecord> {
  const { data, error } = await supabaseAdmin()
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (error) throw error;
  return asRecord(data?.payload);
}

export async function authenticatedActor(
  req: Request,
  allowedRoles?: string[],
): Promise<{ ok: true; actor: AuthenticatedActor } | { ok: false; response: Response }> {
  const token = bearerToken(req);
  if (!token) return { ok: false, response: json({ ok: false, message: "Sesion requerida." }, 401) };

  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return { ok: false, response: json({ ok: false, message: "Sesion invalida." }, 401) };

  const metadata = asRecord(data.user.app_metadata);
  const role = normalizeRole(metadata.role);
  if (allowedRoles && !allowedRoles.map(normalizeRole).includes(role)) {
    return { ok: false, response: json({ ok: false, message: "Admin role required." }, 403) };
  }

  let accounts: JsonRecord[] = [];
  let account: JsonRecord | null = null;
  try {
    const payload = await readLotterynetUsersPayload();
    accounts = flattenLotterynetAccounts(payload);
    account = accounts.find((candidate) =>
      sameText(candidate.authUserId, data.user.id) || sameText(candidate.auth_user_id, data.user.id)
    ) ?? accounts.find((candidate) => accountMatchesAny(candidate, metadataIdentityKeys(metadata))) ?? null;
  } catch {
    accounts = [];
    account = null;
  }

  if (account) {
    if (isBlockedAccount(account)) {
      return { ok: false, response: json({ ok: false, message: "Usuario bloqueado." }, 403) };
    }
    const linkedAuthUser = clean(account.authUserId ?? account.auth_user_id);
    if (linkedAuthUser && linkedAuthUser !== data.user.id) {
      return { ok: false, response: json({ ok: false, message: "Sesion no pertenece al usuario." }, 403) };
    }
  }

  const identityKeys = uniqueKeys([
    data.user.id,
    ...metadataIdentityKeys(metadata),
    ...accountIdentityKeys(account),
  ]);
  const ownerKeys = uniqueKeys([
    ...metadataIdentityKeys(metadata),
    ...accountOwnerKeys(account),
    ...accountIdentityKeys(account).filter(() => role === "admin" || role === "master"),
  ]);

  return {
    ok: true,
    actor: {
      userId: data.user.id,
      role,
      metadata,
      account,
      accounts,
      identityKeys,
      ownerKeys,
    },
  };
}

export function canAccessOwner(actor: AuthenticatedActor, ownerKey: unknown): boolean {
  const key = clean(ownerKey);
  if (!key) return false;
  if (actor.role === "master") return true;
  if (actor.ownerKeys.some((candidate) => sameText(candidate, key))) return true;

  const target = findAccountByKey(actor.accounts, key);
  if (!target) return false;

  if (actor.role === "admin") {
    if (actor.account) return belongsToAdmin(target, actor.account);
    return [target.adminId, target.adminUser, target.admin_id, target.admin_user, target.banca]
      .some((candidate) => actor.ownerKeys.some((owner) => sameText(candidate, owner)));
  }

  if (actor.role === "supervisor" || actor.role === "cajero") {
    return accountMatchesAny(target, actor.identityKeys) ||
      [target.id, target.user, target.username, target.adminId, target.adminUser, target.admin_id, target.admin_user, target.banca]
        .some((candidate) => actor.ownerKeys.some((owner) => sameText(candidate, owner)));
  }

  return false;
}

export function masterConfigScope(key: string): { kind: "global" | "admin"; ownerKey?: string } | null {
  if (/^sys_[A-Za-z0-9_.:-]+$/.test(key) || key === "sportsbook:global") return { kind: "global" };

  const prefixed = /^(cashier_limits|cashier_prize_payouts|recharge_limits|admin_operational_limits|system_modes|manual_disabled_lotteries):(.+)$/.exec(key);
  if (prefixed) return { kind: "admin", ownerKey: prefixed[2] };

  const sportsbookScoped = /^sportsbook:(actor|admin):(.+)$/.exec(key);
  if (sportsbookScoped) return { kind: "admin", ownerKey: sportsbookScoped[2] };

  return null;
}

export function canWriteMasterConfig(actor: AuthenticatedActor, key: string): boolean {
  const scope = masterConfigScope(key);
  if (!scope) return false;
  if (actor.role === "master") return true;
  if (actor.role !== "admin") return false;
  return scope.kind === "admin" && canAccessOwner(actor, scope.ownerKey);
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
  const cached = await redisGetJson<JsonRecord>(`kv:${key}`);
  if (cached && typeof cached === "object" && "value" in cached) return cached.value;

  const supabase = supabaseAdmin();
  const { data, error } = await supabase
    .from("lotterynet_kv")
    .select("value")
    .eq("key", key)
    .maybeSingle();
  if (error) throw error;
  const value = data?.value ?? null;
  if (value !== null) await redisSetJson(`kv:${key}`, { value });
  return value;
}

export async function upsertKvValue(key: string, value: unknown): Promise<void> {
  const supabase = supabaseAdmin();
  const storedValue = typeof value === "string" ? value : JSON.stringify(value);
  const { error } = await supabase
    .from("lotterynet_kv")
    .upsert({ key, value: storedValue, upd: new Date().toISOString() }, { onConflict: "key" });
  if (error) throw error;
  await redisSetJson(`kv:${key}`, { value: storedValue });
}
