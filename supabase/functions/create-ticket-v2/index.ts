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
const LOTTERY_TYPES = new Set(["Q", "P", "SP", "T"]);
const PICK_TYPES = new Set(["P3", "P3BOX", "P4", "P4BOX"]);

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

function validateTicketPlays(body: Record<string, unknown>): { ok: boolean; message?: string } {
  const plays = Array.isArray(body.plays) ? body.plays as Record<string, unknown>[] : [];
  if (plays.length === 0) {
    return { ok: false, message: "No hay jugadas para guardar." };
  }
  const invalid = plays.find((play) => {
    const number = clean(play.number);
    const playType = clean(play.playType ?? play.localPlayType);
    const lotteryId = clean(play.lotteryId ?? play.lottery_id ?? play.sorteoId ?? play.sorteo_id);
    const lotteryName = clean(play.lotteryName ?? play.lottery_name);
    const amount = Number(play.amount);
    return !number || !playType || !lotteryId || !lotteryName || !Number.isFinite(amount) || amount <= 0;
  });
  if (invalid) {
    return { ok: false, message: "Hay una jugada incompleta. Revisa numero, tipo, loteria y monto." };
  }
  return { ok: true };
}

async function fetchMasterPayload(key: string): Promise<Record<string, unknown> | null> {
  const { data, error } = await supabase
    .from("lotterynet_master_state")
    .select("payload")
    .eq("config_key", key)
    .maybeSingle();
  if (error) throw error;
  const payload = data?.payload;
  return payload && typeof payload === "object" && !Array.isArray(payload) ? payload as Record<string, unknown> : null;
}

function todaySantoDomingo(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function normalizedPlayType(value: unknown): string | null {
  const type = clean(value).toUpperCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[\s_-]+/g, "");
  switch (type) {
    case "Q":
    case "QUINIELA":
      return "Q";
    case "P":
    case "PALE":
      return "P";
    case "SP":
    case "SUPERPALE":
      return "SP";
    case "T":
    case "TRIPLETA":
      return "T";
    case "P3":
    case "PICK3":
    case "P3S":
    case "P3STRAIGHT":
      return "P3";
    case "P3BOX":
    case "PICK3BOX":
      return "P3BOX";
    case "P4":
    case "PICK4":
    case "P4S":
    case "P4STRAIGHT":
      return "P4";
    case "P4BOX":
    case "PICK4BOX":
      return "P4BOX";
    default:
      return null;
  }
}

function normalizedBlockedNumber(playType: string, value: unknown): string | null {
  const digits = clean(value).replace(/\D/g, "");
  switch (playType) {
    case "Q":
      return digits.length === 2 ? digits : null;
    case "P":
      return digits.length === 4 ? digits : null;
    case "SP":
      return digits.length === 4 ? `${digits.slice(0, 2)}-${digits.slice(2)}` : null;
    case "T":
      return digits.length === 6 ? digits : null;
    case "P3":
    case "P3BOX":
      return digits.length === 3 ? digits : null;
    case "P4":
    case "P4BOX":
      return digits.length === 4 ? digits : null;
    default:
      return null;
  }
}

function playLabel(playType: string, number: string): string {
  const names: Record<string, string> = {
    Q: "Quiniela",
    P: "Pale",
    SP: "Super Pale",
    T: "Tripleta",
    P3: "Pick 3 Straight",
    P3BOX: "Pick 3 Box",
    P4: "Pick 4 Straight",
    P4BOX: "Pick 4 Box",
  };
  return `${names[playType] ?? "Jugada"} ${number.replace("-", "/")}`;
}

function blockedPlaySet(systemConfig: Record<string, unknown> | null): Set<string> {
  const rows = Array.isArray(systemConfig?.blockedSalePlays) ? systemConfig?.blockedSalePlays as Record<string, unknown>[] : [];
  const set = new Set<string>();
  for (const row of rows) {
    const type = normalizedPlayType(row.playType);
    if (!type) continue;
    const number = normalizedBlockedNumber(type, row.number);
    if (number) set.add(`${type}:${number}`);
  }
  return set;
}

function disabledLotterySet(config: Record<string, unknown> | null): Set<string> {
  if (!config) return new Set();
  const permanent = config.permanent === true;
  const date = clean(config.date);
  if (!permanent && date && date !== todaySantoDomingo()) return new Set();
  const ids = Array.isArray(config.ids) ? config.ids : [];
  return new Set(ids.map((id) => clean(id)).filter(Boolean));
}

function booleanField(config: Record<string, unknown> | null, field: string): boolean | null {
  return typeof config?.[field] === "boolean" ? config[field] as boolean : null;
}

function normalizedSystemMode(config: Record<string, unknown> | null): {
  lotteryModeEnabled: boolean;
  pickModeEnabled: boolean;
  cashierModeEnabled: boolean;
  cashierLotteryModeEnabled: boolean;
  cashierPickModeEnabled: boolean;
} {
  const configured = config?.configured === true;
  let lotteryModeEnabled = booleanField(config, "lotteryModeEnabled") ?? true;
  let pickModeEnabled = booleanField(config, "pickModeEnabled") ?? false;
  const cashierModeEnabled = booleanField(config, "cashierModeEnabled") === true;
  let cashierLotteryModeEnabled = booleanField(config, "cashierLotteryModeEnabled") ?? true;
  let cashierPickModeEnabled = booleanField(
    config,
    "cashierPickModeEnabled",
  ) ?? (booleanField(config, "cashierPickEnabled") ?? false);

  if (!lotteryModeEnabled && !pickModeEnabled) {
    lotteryModeEnabled = true;
  }
  if (!configured && !lotteryModeEnabled) {
    lotteryModeEnabled = true;
    pickModeEnabled = false;
  }
  if (!cashierLotteryModeEnabled && !cashierPickModeEnabled) {
    cashierLotteryModeEnabled = true;
  }
  if (!cashierModeEnabled) {
    cashierLotteryModeEnabled = true;
    cashierPickModeEnabled = false;
  }

  return {
    lotteryModeEnabled,
    pickModeEnabled,
    cashierModeEnabled,
    cashierLotteryModeEnabled,
    cashierPickModeEnabled,
  };
}

function validateAdministrativeControls(
  body: Record<string, unknown>,
  systemConfig: Record<string, unknown> | null,
  disabledLotteryConfig: Record<string, unknown> | null,
): { ok: boolean; message?: string; status?: number } {
  const plays = Array.isArray(body.plays) ? body.plays as Record<string, unknown>[] : [];
  const disabledLotteries = disabledLotterySet(disabledLotteryConfig);
  const blockedPlays = blockedPlaySet(systemConfig);
  const actorRole = lower(body.actorRole ?? body.role);
  const isCashier = actorRole === "cashier" || actorRole === "cajero";
  const systemMode = normalizedSystemMode(systemConfig);

  const lotteryEnabled = isCashier && systemMode.cashierModeEnabled
    ? systemMode.cashierLotteryModeEnabled
    : systemMode.lotteryModeEnabled;
  const pickEnabled = isCashier && systemMode.cashierModeEnabled
    ? systemMode.cashierPickModeEnabled
    : systemMode.pickModeEnabled;

  for (const play of plays) {
    const lotteryId = clean(play.lotteryId ?? play.lottery_id ?? play.sorteoId ?? play.sorteo_id);
    const lotteryName = clean(play.lotteryName ?? play.lottery_name) || lotteryId;
    if (disabledLotteries.has(lotteryId)) {
      return { ok: false, status: 409, message: `Loteria bloqueada para venta: ${lotteryName}.` };
    }

    const type = normalizedPlayType(play.playType ?? play.localPlayType);
    if (!type) continue;
    if (lotteryEnabled === false && LOTTERY_TYPES.has(type)) {
      return { ok: false, status: 409, message: "Las jugadas de loteria estan bloqueadas para esta cuenta." };
    }
    if (pickEnabled === false && PICK_TYPES.has(type)) {
      return { ok: false, status: 409, message: "Las jugadas Pick estan bloqueadas para esta cuenta." };
    }

    const number = normalizedBlockedNumber(type, play.number);
    if (number && blockedPlays.has(`${type}:${number}`)) {
      return { ok: false, status: 409, message: `${playLabel(type, number)} esta bloqueada por administracion.` };
    }
  }
  return { ok: true };
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

    const playsValidation = validateTicketPlays(body);
    if (!playsValidation.ok) {
      return json({ ok: false, message: playsValidation.message }, 400);
    }

    const adminKey = clean(body.adminKey ?? body.adminId ?? body.ownerKey);
    if (adminKey) {
      const [systemConfig, disabledLotteryConfig] = await Promise.all([
        fetchMasterPayload(`system_modes:${adminKey}`),
        fetchMasterPayload(`manual_disabled_lotteries:${adminKey}`),
      ]);
      const administrativeControls = validateAdministrativeControls(body, systemConfig, disabledLotteryConfig);
      if (!administrativeControls.ok) {
        return json({ ok: false, message: administrativeControls.message }, administrativeControls.status ?? 409);
      }
    }

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
