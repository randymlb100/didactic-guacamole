import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { bearerToken, clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type AdminCommand =
  | "toggle_bank"
  | "delete_bank"
  | "regenerate_bank_credentials"
  | "reset_user_password"
  | "update_user_commission"
  | "assign_supervisor_cashiers";

type UserRow = Record<string, unknown> & {
  id?: string;
  user?: string;
  username?: string;
  role?: string;
  adminId?: string;
  adminUser?: string;
  supervisorIds?: string[];
  supervisorUsers?: string[];
  active?: boolean;
  activo?: boolean;
  blocked?: boolean;
  authUserId?: string;
};

type UsersState = {
  payload: Record<string, unknown>;
  users: UserRow[];
  shape: "users" | "legacy" | "empty";
};

const allowedActions = new Set<AdminCommand>([
  "toggle_bank",
  "delete_bank",
  "regenerate_bank_credentials",
  "reset_user_password",
  "update_user_commission",
  "assign_supervisor_cashiers",
]);

const accountArray = (value: unknown): UserRow[] => Array.isArray(value) ? value as UserRow[] : [];

const normalizeRole = (value: unknown): string => {
  const role = clean(value).toUpperCase();
  if (role === "CAJERO") return "CASHIER";
  if (role === "MASTER" || role === "ADMIN" || role === "SUPERVISOR" || role === "CASHIER") return role;
  return role || "UNKNOWN";
};

const roleOf = (user: UserRow | null | undefined): string => normalizeRole(user?.role);
const idOf = (user: UserRow | null | undefined): string => clean(user?.id || (user as any)?.userId);
const usernameOf = (user: UserRow | null | undefined): string => clean(user?.user || user?.username);
const adminIdOf = (user: UserRow | null | undefined): string => clean(user?.adminId);
const adminUserOf = (user: UserRow | null | undefined): string => clean(user?.adminUser);

const sameText = (a: unknown, b: unknown): boolean => clean(a).toLowerCase() === clean(b).toLowerCase();

const dedupeUsers = (users: UserRow[]): UserRow[] => {
  const seen = new Set<string>();
  return users.filter((user) => {
    const key = `${idOf(user)}:${usernameOf(user)}`.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const flattenPayloadUsers = (payload: Record<string, unknown>): { users: UserRow[]; shape: UsersState["shape"] } => {
  if (Array.isArray(payload.users)) return { users: payload.users as UserRow[], shape: "users" };
  const legacyUsers = dedupeUsers([
    ...accountArray(payload.admins),
    ...accountArray(payload.supervisores),
    ...accountArray(payload.supervisors),
    ...accountArray(payload.cajeros),
  ]);
  return { users: legacyUsers, shape: legacyUsers.length ? "legacy" : "empty" };
};

const normalizeCommissionToDecimal = (value: unknown): number | null => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 100) return null;
  return parsed > 1 ? parsed / 100 : parsed;
};

const isActive = (user: UserRow): boolean => {
  if (typeof user.active === "boolean") return user.active;
  if (typeof user.activo === "boolean") return user.activo;
  if (typeof user.blocked === "boolean") return !user.blocked;
  return true;
};

const setActive = (user: UserRow, active: boolean): UserRow => ({
  ...user,
  active,
  activo: active,
  blocked: !active,
});

const belongsToAdmin = (user: UserRow, admin: UserRow): boolean => {
  return sameText(adminIdOf(user), idOf(admin)) ||
    sameText(adminUserOf(user), usernameOf(admin)) ||
    (!!clean(user.banca) && !!clean(admin.banca) && sameText(user.banca, admin.banca));
};

const canAdminMutateUser = (actor: UserRow, target: UserRow): boolean => {
  const actorRole = roleOf(actor);
  if (actorRole === "MASTER") return true;
  if (actorRole !== "ADMIN") return false;
  if (roleOf(target) === "ADMIN") return sameText(idOf(actor), idOf(target)) || sameText(usernameOf(actor), usernameOf(target));
  return sameText(adminIdOf(target), idOf(actor)) || sameText(adminUserOf(target), usernameOf(actor));
};

const randomPassword = (): string => {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const bytes = new Uint8Array(10);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((byte) => alphabet[byte % alphabet.length]).join("");
};

const sha256Hex = async (input: string): Promise<string> => {
  const encoded = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
};

const updateAuthPasswordIfLinked = async (user: UserRow, password: string): Promise<boolean> => {
  const authUserId = clean(user.authUserId || (user as any).auth_id || (user as any).profileId || (user as any).profile_id);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(authUserId)) {
    return false;
  }
  const { error } = await supabaseAdmin().auth.admin.updateUserById(authUserId, { password });
  if (error) throw error;
  return true;
};

const applyPassword = async (user: UserRow): Promise<{ user: UserRow; password: string; authUpdated: boolean }> => {
  const password = randomPassword();
  const salt = `lotterynet-${idOf(user) || usernameOf(user)}-${Date.now()}`;
  const hash = await sha256Hex(`${salt}:${password}`);
  const nextUser = {
    ...user,
    passwordSalt: salt,
    passwordHash: hash,
    passwordVersion: "sha256-v1",
    credChangedAtEpochMs: Date.now(),
    credChangedAt: Date.now(),
    updatedAtEpochMs: Date.now(),
    updatedAt: Date.now(),
  };
  const authUpdated = await updateAuthPasswordIfLinked(nextUser, password);
  return { password, user: nextUser, authUpdated };
};

const readUsersState = async (): Promise<UsersState> => {
  const { data, error } = await supabaseAdmin()
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (error) throw error;
  const payload = (data?.payload ?? {}) as Record<string, unknown>;
  const flattened = flattenPayloadUsers(payload);
  return { payload, users: flattened.users, shape: flattened.shape };
};

const saveUsersState = async (state: UsersState): Promise<void> => {
  const users = state.users;
  const payload = state.shape === "legacy"
    ? {
      ...state.payload,
      admins: users.filter((user) => roleOf(user) === "ADMIN"),
      supervisores: users.filter((user) => roleOf(user) === "SUPERVISOR"),
      supervisors: users.filter((user) => roleOf(user) === "SUPERVISOR"),
      cajeros: users.filter((user) => roleOf(user) === "CASHIER"),
    }
    : {
      ...state.payload,
      users,
    };

  const { error } = await supabaseAdmin()
    .from("lotterynet_users_state")
    .upsert({ scope: "global", payload, updated_at: new Date().toISOString() }, { onConflict: "scope" });
  if (error) throw error;
};

const findUser = (users: UserRow[], idOrUser: unknown): UserRow | null => {
  const needle = clean(idOrUser);
  return users.find((u) => sameText(idOf(u), needle) || sameText(usernameOf(u), needle)) ?? null;
};

const getActor = async (req: Request, body: Record<string, unknown>, users: UserRow[]): Promise<UserRow | Response> => {
  const token = bearerToken(req);
  if (token) {
    const { data, error } = await supabaseAdmin().auth.getUser(token);
    if (error || !data.user) return json({ ok: false, message: "Sesion admin invalida." }, 401);
    const metadata = data.user.app_metadata || {};
    const role = normalizeRole(metadata.role);
    if (role !== "MASTER" && role !== "ADMIN") return json({ ok: false, message: "Admin role required." }, 403);
    const legacyId = clean(metadata.legacy_id);
    const username = clean(metadata.username);
    return findUser(users, legacyId) || findUser(users, username) || {
      id: legacyId || username || data.user.id,
      user: username || clean(data.user.email) || data.user.id,
      role,
      adminId: clean(metadata.admin_id),
      adminUser: clean(metadata.admin_user),
      banca: clean(metadata.banca),
      active: true,
    };
  }

  if (Deno.env.get("LOTTERYNET_ALLOW_LEGACY_ADMIN_COMMANDS") === "true") {
    const actor = findUser(users, body.actorId) || findUser(users, body.actorUser);
    if (actor) return actor;
  }

  return json({ ok: false, message: "Sesion admin requerida." }, 401);
};

const appendAudit = async (actor: UserRow, action: string, detail: string): Promise<string> => {
  const auditId = `AUD-${Date.now()}`;
  const entry = {
    id: auditId,
    ts: new Date().toISOString(),
    actorId: idOf(actor),
    actorUser: usernameOf(actor),
    role: roleOf(actor).toLowerCase(),
    action,
    details: detail,
    status: "success",
  };

  const supabase = supabaseAdmin();
  const { data } = await supabase
    .from("lotterynet_kv")
    .select("value")
    .eq("key", "sys_audit_v4")
    .maybeSingle();

  let current: unknown = data?.value;
  try {
    current = typeof current === "string" ? JSON.parse(current || "[]") : current;
  } catch {
    current = [];
  }

  const next = [entry, ...(Array.isArray(current) ? current : [])].slice(0, 250);
  const { error } = await supabase
    .from("lotterynet_kv")
    .upsert({ key: "sys_audit_v4", value: JSON.stringify(next), upd: new Date().toISOString() }, { onConflict: "key" });
  if (error) throw error;
  return auditId;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const action = clean(body.action) as AdminCommand;
    if (!allowedActions.has(action)) return json({ ok: false, message: "Accion administrativa invalida." }, 400);

    const state = await readUsersState();
    const users = state.users;
    const actorOrResponse = await getActor(req, body, users);
    if (actorOrResponse instanceof Response) return actorOrResponse;
    const actor = actorOrResponse;
    const actorRole = roleOf(actor);

    if (actorRole === "SUPERVISOR" || actorRole === "CASHIER") {
      return json({ ok: false, message: "Este perfil no puede ejecutar acciones administrativas sensibles." }, 403);
    }

    const targetId = clean(body.targetId);
    const targetIndex = users.findIndex((u) => sameText(idOf(u), targetId) || sameText(usernameOf(u), targetId));
    if (targetIndex < 0) return json({ ok: false, message: "Usuario destino no encontrado." }, 404);
    const target = users[targetIndex];

    let message = "Accion aplicada.";
    let data: Record<string, unknown> = {};

    if (action === "toggle_bank") {
      if (actorRole !== "MASTER" || roleOf(target) !== "ADMIN") return json({ ok: false, message: "Solo MASTER puede bloquear bancas." }, 403);
      const nextActive = !isActive(target);
      users[targetIndex] = setActive(target, nextActive);
      let affectedUsers = 0;
      for (let index = 0; index < users.length; index += 1) {
        if (index !== targetIndex && ["CASHIER", "SUPERVISOR"].includes(roleOf(users[index])) && belongsToAdmin(users[index], target)) {
          users[index] = setActive(users[index], nextActive);
          affectedUsers += 1;
        }
      }
      message = nextActive ? "Banca desbloqueada." : "Banca bloqueada.";
      data = { affectedUsers, active: nextActive };
    }

    if (action === "delete_bank") {
      if (actorRole !== "MASTER" || roleOf(target) !== "ADMIN") return json({ ok: false, message: "Solo MASTER puede eliminar bancas." }, 403);
      const remaining = users.filter((u, index) => index === targetIndex ? false : !belongsToAdmin(u, target));
      const removedCount = users.length - remaining.length;
      users.splice(0, users.length, ...remaining);
      message = "Banca eliminada con su red asociada.";
      data = { removedCount };
    }

    if (action === "regenerate_bank_credentials") {
      if (actorRole !== "MASTER" || roleOf(target) !== "ADMIN") return json({ ok: false, message: "Solo MASTER puede regenerar claves de banca." }, 403);
      const issuedCredentials: Array<Record<string, string | boolean>> = [];
      for (let index = 0; index < users.length; index += 1) {
        if (index === targetIndex || (["CASHIER", "SUPERVISOR"].includes(roleOf(users[index])) && belongsToAdmin(users[index], target))) {
          const issued = await applyPassword(users[index]);
          users[index] = issued.user;
          issuedCredentials.push({
            username: usernameOf(users[index]),
            displayName: clean(users[index].displayName || users[index].nombre || users[index].ownerName || usernameOf(users[index])),
            role: roleOf(users[index]),
            password: issued.password,
            authUpdated: issued.authUpdated,
          });
        }
      }
      message = "Credenciales regeneradas.";
      data = { issuedCredentials };
    }

    if (action === "reset_user_password") {
      if (!canAdminMutateUser(actor, target)) return json({ ok: false, message: "No puedes cambiar esta clave." }, 403);
      const issued = await applyPassword(target);
      users[targetIndex] = issued.user;
      message = "Clave actualizada.";
      data = {
        issuedCredential: {
          username: usernameOf(users[targetIndex]),
          displayName: clean(users[targetIndex].displayName || users[targetIndex].nombre || users[targetIndex].ownerName || usernameOf(users[targetIndex])),
          role: roleOf(users[targetIndex]),
          password: issued.password,
          authUpdated: issued.authUpdated,
        },
      };
    }

    if (action === "update_user_commission") {
      if (!canAdminMutateUser(actor, target)) return json({ ok: false, message: "No puedes cambiar esta comision." }, 403);
      const commissionRate = normalizeCommissionToDecimal((body.payload as Record<string, unknown> | undefined)?.commissionRate);
      if (commissionRate === null) return json({ ok: false, message: "Comision invalida." }, 400);
      users[targetIndex] = { ...target, commissionRate };
      message = "Comision actualizada.";
      data = { commissionRate };
    }

    if (action === "assign_supervisor_cashiers") {
      if (!canAdminMutateUser(actor, target) || roleOf(target) !== "SUPERVISOR") return json({ ok: false, message: "Supervisor fuera de alcance." }, 403);
      const payload = (body.payload || {}) as Record<string, unknown>;
      const cashierIds = Array.isArray(payload.cashierIds) ? payload.cashierIds.map(clean) : [];
      const groupCommissionRate = normalizeCommissionToDecimal(payload.groupCommissionRate);
      const ownerAdminId = actorRole === "MASTER" ? adminIdOf(target) : idOf(actor);
      const ownerAdminUser = actorRole === "MASTER" ? adminUserOf(target) : usernameOf(actor);
      let affectedCashiers = 0;
      users[targetIndex] = groupCommissionRate === null ? target : { ...target, commissionRate: groupCommissionRate };
      for (let index = 0; index < users.length; index += 1) {
        if (roleOf(users[index]) !== "CASHIER") continue;
        if (!sameText(adminIdOf(users[index]), ownerAdminId) && !sameText(adminUserOf(users[index]), ownerAdminUser)) continue;
        const assigned = cashierIds.some((id: string) => sameText(idOf(users[index]), id) || sameText(usernameOf(users[index]), id));
        const currentIds = Array.isArray(users[index].supervisorIds) ? users[index].supervisorIds as string[] : [];
        const currentUsers = Array.isArray(users[index].supervisorUsers) ? users[index].supervisorUsers as string[] : [];
        users[index] = {
          ...users[index],
          supervisorIds: assigned
            ? Array.from(new Set([...currentIds, idOf(target)]))
            : currentIds.filter((id) => !sameText(id, idOf(target))),
          supervisorUsers: assigned
            ? Array.from(new Set([...currentUsers, usernameOf(target)]))
            : currentUsers.filter((user) => !sameText(user, usernameOf(target))),
          ...(assigned && groupCommissionRate !== null ? { commissionRate: groupCommissionRate } : {}),
        };
        affectedCashiers += 1;
      }
      message = "Asignacion de supervisor actualizada.";
      data = { affectedCashiers };
    }

    await saveUsersState(state);
    const auditId = await appendAudit(actor, action, message);
    return json({ ok: true, message, data, auditId });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo ejecutar la accion administrativa." }, 500);
  }
});
