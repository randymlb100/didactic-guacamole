import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  accountIdentityKeys,
  authenticatedActor,
  belongsToAdmin,
  canAccessOwner,
  bumpLotterynetUsersStateVersion,
  corsHeaders,
  json,
  metadataIdentityKeys,
  normalizeRole,
  sameText,
  supabaseAdmin,
  uniqueKeys,
  type AuthenticatedActor,
} from "../_shared/lotterynet-admin.ts";

type JsonRecord = Record<string, unknown>;

const USER_ARRAY_KEYS = ["users", "admins", "supervisores", "supervisors", "cajeros", "cashiers"] as const;

const clean = (value: unknown): string => String(value ?? "").trim();

const userKey = (user: JsonRecord): string => {
  const id = clean(user.id).toLowerCase();
  const username = clean(user.user).toLowerCase();
  return id || username;
};

const userKeys = (user: JsonRecord): string[] => {
  return [clean(user.id).toLowerCase(), clean(user.user).toLowerCase()]
    .filter((value) => value.length > 0);
};

const readCommissionRate = (user: JsonRecord): number | null => {
  const raw = user.commissionRate ?? user.comision;
  if (raw === null || raw === undefined || raw === "") return null;
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return parsed > 1 && parsed <= 100 ? parsed / 100 : Math.min(parsed, 1);
};

const collectExistingCommissions = (payload: unknown): Map<string, number> => {
  const commissions = new Map<string, number>();
  if (!payload || typeof payload !== "object") return commissions;
  const root = payload as JsonRecord;
  for (const key of USER_ARRAY_KEYS) {
    const users = root[key];
    if (!Array.isArray(users)) continue;
    for (const value of users) {
      if (!value || typeof value !== "object") continue;
      const user = value as JsonRecord;
      const rate = readCommissionRate(user);
      const key = userKey(user);
      if (key && rate !== null && rate > 0) commissions.set(key, rate);
    }
  }
  return commissions;
};

const preserveExistingCommissions = (
  incoming: unknown,
  existing: unknown,
  commissionOverrideKeys: Set<string> = new Set(),
): unknown => {
  if (!incoming || typeof incoming !== "object") return incoming;
  const existingCommissions = collectExistingCommissions(existing);
  if (existingCommissions.size === 0) return incoming;

  const root = incoming as JsonRecord;
  for (const key of USER_ARRAY_KEYS) {
    const users = root[key];
    if (!Array.isArray(users)) continue;
    for (const value of users) {
      if (!value || typeof value !== "object") continue;
      const user = value as JsonRecord;
      const existingRate = existingCommissions.get(userKey(user));
      if (existingRate === undefined) continue;
      const incomingRate = readCommissionRate(user);
      const explicitCommissionOverride = userKeys(user).some((key) => commissionOverrideKeys.has(key));
      if (incomingRate === null || (incomingRate <= 0 && !explicitCommissionOverride)) {
        user.commissionRate = existingRate;
      }
    }
  }
  return incoming;
};

const readCommissionOverrideKeys = (value: unknown): Set<string> => {
  if (!Array.isArray(value)) return new Set();
  return new Set(
    value
      .map((item) => clean(item).toLowerCase())
      .filter((item) => item.length > 0),
  );
};

const accountArray = (value: unknown): JsonRecord[] => Array.isArray(value) ? value as JsonRecord[] : [];

const flattenUsersPayload = (payload: unknown): JsonRecord[] => {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as JsonRecord;
  return dedupeUsers([
    ...accountArray(root.users),
    ...accountArray(root.admins),
    ...accountArray(root.supervisores),
    ...accountArray(root.supervisors),
    ...accountArray(root.cajeros),
    ...accountArray(root.cashiers),
  ]);
};

const dedupeUsers = (users: JsonRecord[]): JsonRecord[] => {
  const seen = new Set<string>();
  const result: JsonRecord[] = [];
  for (const user of users) {
    const key = userKey(user);
    if (key && seen.has(key)) continue;
    if (key) seen.add(key);
    result.push(user);
  }
  return result;
};

const actorAccountForScope = (actor: AuthenticatedActor): JsonRecord => {
  if (actor.account) return actor.account;
  return {
    id: actor.metadata.legacy_id ?? actor.metadata.legacy_key,
    user: actor.metadata.username ?? actor.metadata.user,
    username: actor.metadata.username,
    role: actor.role,
    banca: actor.metadata.banca,
  };
};

const userBelongsToActorAdmin = (user: JsonRecord, actor: AuthenticatedActor): boolean => {
  if (actor.role === "master") return true;
  if (actor.role !== "admin") return false;

  const admin = actorAccountForScope(actor);
  const adminKeys = uniqueKeys([
    ...accountIdentityKeys(admin),
    ...metadataIdentityKeys(actor.metadata),
    ...actor.ownerKeys,
  ]);

  if (normalizeRole(user.role) === "admin" && accountIdentityKeys(user).some((key) => adminKeys.some((adminKey) => sameText(key, adminKey)))) {
    return true;
  }

  if (belongsToAdmin(user, admin)) return true;

  return [user.adminId, user.adminUser, user.admin_id, user.admin_user, user.banca]
    .map(clean)
    .filter(Boolean)
    .some((value) => adminKeys.some((adminKey) => sameText(value, adminKey)));
};

const mergeScopedAdminPayload = (
  incoming: unknown,
  existing: unknown,
  actor: AuthenticatedActor,
): unknown => {
  if (actor.role === "master") return incoming;

  const existingRoot = existing && typeof existing === "object" ? existing as JsonRecord : {};
  const existingUsers = flattenUsersPayload(existingRoot);
  const incomingUsers = flattenUsersPayload(incoming);
  const scopedIncoming = incomingUsers.filter((user) => userBelongsToActorAdmin(user, actor));

  if (scopedIncoming.length === 0) {
    throw new Error("Payload de usuarios no contiene cuentas del admin autenticado.");
  }

  const preservedUsers = existingUsers.filter((user) => !userBelongsToActorAdmin(user, actor));
  return {
    ...existingRoot,
    users: dedupeUsers([...preservedUsers, ...scopedIncoming]),
  };
};

const normalizeUsersPayloadShape = (payload: unknown): unknown => {
  if (!payload || typeof payload !== "object") return payload;
  const root = payload as JsonRecord;
  const users = flattenUsersPayload(root);
  if (users.length === 0) return payload;
  const admins = users.filter((user) => {
    const role = normalizeRole(user.role);
    return role === "admin" || role === "master";
  });
  const supervisors = users.filter((user) => normalizeRole(user.role) === "supervisor");
  const cashiers = users.filter((user) => normalizeRole(user.role) === "cajero");
  return {
    ...root,
    users,
    admins,
    supervisores: supervisors,
    supervisors,
    cajeros: cashiers,
  };
};

const moneyCents = (value: unknown): number | null => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return Math.round(parsed * 100);
};

const updateRechargeFundPayload = (
  payload: unknown,
  accountId: string,
  enabled: boolean,
  amount: number,
  updatedAtEpochMs: number,
): JsonRecord => {
  const root = payload && typeof payload === "object" ? payload as JsonRecord : {};
  let matched = false;
  const users = flattenUsersPayload(root).map((user) => {
    if (!accountIdentityKeys(user).some((key) => sameText(key, accountId))) return user;
    matched = true;
    return {
      ...user,
      recargasEnabled: enabled,
      recargasAssignedBalance: amount,
      recargasBalance: amount,
      updatedAt: updatedAtEpochMs,
    };
  });
  if (!matched) throw new Error("La banca indicada no existe en users state.");
  return normalizeUsersPayloadShape({ ...root, users }) as JsonRecord;
};

const debitRechargeBalancePayload = (
  payload: unknown,
  accountId: string,
  balance: number,
  updatedAtEpochMs: number,
): JsonRecord => {
  const root = payload && typeof payload === "object" ? payload as JsonRecord : {};
  let matched = false;
  const users = flattenUsersPayload(root).map((user) => {
    if (!accountIdentityKeys(user).some((key) => sameText(key, accountId))) return user;
    matched = true;
    const persistedAssigned = moneyCents(user.recargasAssignedBalance);
    const assignedBalance = persistedAssigned === null ? user.recargasAssignedBalance : persistedAssigned / 100;
    return {
      ...user,
      recargasAssignedBalance: assignedBalance,
      recargasBalance: balance,
      updatedAt: updatedAtEpochMs,
    };
  });
  if (!matched) throw new Error("La banca indicada no existe en users state.");
  return normalizeUsersPayloadShape({ ...root, users }) as JsonRecord;
};

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
    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "upsert").trim().toLowerCase();
    if (action === "update-recharge-fund") {
      const auth = await authenticatedActor(req, ["master"]);
      if (!auth.ok) return auth.response;
      const accountId = clean(body.accountId);
      const amountCents = moneyCents(body.amount);
      if (!accountId) return json({ ok: false, message: "Banca requerida." }, 400);
      if (typeof body.enabled !== "boolean") return json({ ok: false, message: "Estado de recargas requerido." }, 400);
      if (amountCents === null) return json({ ok: false, message: "Monto de fondo invalido." }, 400);

      const requestedAmount = amountCents / 100;
      const { data: existing, error: existingError } = await supabase
        .from("lotterynet_users_state")
        .select("payload")
        .eq("scope", "global")
        .maybeSingle();
      if (existingError) throw existingError;

      const now = Date.now();
      const payload = updateRechargeFundPayload(
        existing?.payload ?? {},
        accountId,
        body.enabled,
        requestedAmount,
        now,
      );
      const { data: persisted, error: persistError } = await supabase
        .from("lotterynet_users_state")
        .upsert(
          { scope: "global", payload, updated_at: new Date(now).toISOString() },
          { onConflict: "scope" },
        )
        .select("payload, updated_at")
        .single();
      if (persistError) throw persistError;

      const persistedAccount = flattenUsersPayload(persisted?.payload)
        .find((user) => accountIdentityKeys(user).some((key) => sameText(key, accountId)));
      const persistedCents = moneyCents(persistedAccount?.recargasBalance);
      const persistedAmount = persistedCents === null ? null : persistedCents / 100;
      const confirmed = persistedCents === amountCents &&
        moneyCents(persistedAccount?.recargasAssignedBalance) === amountCents &&
        persistedAccount?.recargasEnabled === body.enabled;
      await bumpLotterynetUsersStateVersion();

      return json({
        ok: confirmed,
        confirmed,
        accountId,
        requestedAmount,
        persistedAmount,
        updatedAt: persisted?.updated_at ?? null,
      }, confirmed ? 200 : 409);
    }
    if (action === "update-recharge-balance") {
      const auth = await authenticatedActor(req, ["admin", "master", "supervisor", "cajero"]);
      if (!auth.ok) return auth.response;
      const accountId = clean(body.accountId);
      const balanceCents = moneyCents(body.amount ?? body.balance);
      if (!accountId) return json({ ok: false, message: "Banca requerida." }, 400);
      if (balanceCents === null) return json({ ok: false, message: "Saldo de recargas invalido." }, 400);
      if (!canAccessOwner(auth.actor, accountId)) {
        return json({ ok: false, message: "No tiene permiso para modificar esta banca." }, 403);
      }

      const requestedBalance = balanceCents / 100;
      const { data: existing, error: existingError } = await supabase
        .from("lotterynet_users_state")
        .select("payload")
        .eq("scope", "global")
        .maybeSingle();
      if (existingError) throw existingError;

      const now = Date.now();
      const payload = debitRechargeBalancePayload(
        existing?.payload ?? {},
        accountId,
        requestedBalance,
        now,
      );
      const { data: persisted, error: persistError } = await supabase
        .from("lotterynet_users_state")
        .upsert(
          { scope: "global", payload, updated_at: new Date(now).toISOString() },
          { onConflict: "scope" },
        )
        .select("payload, updated_at")
        .single();
      if (persistError) throw persistError;

      const persistedAccount = flattenUsersPayload(persisted?.payload)
        .find((user) => accountIdentityKeys(user).some((key) => sameText(key, accountId)));
      const persistedBalanceCents = moneyCents(persistedAccount?.recargasBalance);
      const persistedAmount = persistedBalanceCents === null ? null : persistedBalanceCents / 100;
      const confirmed = persistedBalanceCents === balanceCents;
      await bumpLotterynetUsersStateVersion();

      return json({
        ok: confirmed,
        confirmed,
        accountId,
        requestedAmount: requestedBalance,
        persistedAmount,
        updatedAt: persisted?.updated_at ?? null,
      }, confirmed ? 200 : 409);
    }
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

    const auth = await authenticatedActor(req, ["admin", "master"]);
    if (!auth.ok) return auth.response;

    let payload = body.payload;
    if (!payload || typeof payload !== "object") {
      return json({ ok: false, message: "Payload de usuarios requerido." }, 400);
    }
    const { data: existing, error: existingError } = await supabase
      .from("lotterynet_users_state")
      .select("payload")
      .eq("scope", "global")
      .maybeSingle();
    if (existingError) throw existingError;
    const commissionOverrideKeys = readCommissionOverrideKeys(body.commissionOverrideKeys);
    payload = mergeScopedAdminPayload(payload, existing?.payload ?? null, auth.actor);
    payload = preserveExistingCommissions(payload, existing?.payload ?? null, commissionOverrideKeys);
    payload = normalizeUsersPayloadShape(payload);
    const { error } = await supabase
      .from("lotterynet_users_state")
      .upsert({ scope: "global", payload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
    if (error) throw error;
    await bumpLotterynetUsersStateVersion();
    return json({ ok: true, scope: "global" });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar users state." }, 500);
  }
});
