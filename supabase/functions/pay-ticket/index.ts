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
  const header = req.headers.get("Authorization") ?? "";
  return header.replace(/^Bearer\s+/i, "").trim();
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function isUnconfirmedPrizeError(message: string): boolean {
  const normalized = lower(message);
  return normalized.includes("no tiene premio confirmado") ||
    normalized.includes("no hay resultado confirmado") ||
    normalized.includes("premio no confirmado") ||
    (normalized.includes("prize") && normalized.includes("confirm"));
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (error && typeof error === "object") {
    const value = error as Record<string, unknown>;
    return clean(value.message ?? value.details ?? value.hint ?? value.code ?? JSON.stringify(value));
  }
  return clean(error);
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
    account.userId,
    account.authUserId,
    account.auth_user_id,
  ].some((candidate) => lower(candidate) === needle);
}

function normalizedSet(values: unknown[]): Set<string> {
  return new Set(values.map(lower).filter(Boolean));
}

function accountAliases(account: Record<string, unknown> | undefined, ...extra: unknown[]): Set<string> {
  return normalizedSet([
    ...(account
      ? [
          account.id,
          account.user,
          account.username,
          account.userId,
          account.authUserId,
          account.auth_user_id,
        ]
      : []),
    ...extra,
  ]);
}

function mergeSets(...sets: Set<string>[]): Set<string> {
  const merged = new Set<string>();
  for (const set of sets) for (const value of set) merged.add(value);
  return merged;
}

function findAccountByAny(accounts: Record<string, unknown>[], values: unknown[]): Record<string, unknown> | undefined {
  return accounts.find((account) => values.some((value) => accountMatches(account, clean(value))));
}

function aliasesForValue(accounts: Record<string, unknown>[], value: string): Set<string> {
  return accountAliases(findAccountByAny(accounts, [value]), value);
}

function intersects(left: Set<string>, right: Set<string>): boolean {
  for (const value of left) if (right.has(value)) return true;
  return false;
}

type AuthenticatedActor = {
  ok: boolean;
  message?: string;
  aliases: Set<string>;
  accounts: Record<string, unknown>[];
};

async function authenticatedActor(req: Request, actorKey: string): Promise<AuthenticatedActor> {
  const token = bearerToken(req);
  if (!token) return { ok: false, message: "Sesion del servidor requerida.", aliases: new Set(), accounts: [] };
  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) return { ok: false, message: "Sesion del servidor invalida.", aliases: new Set(), accounts: [] };

  const authUser = data.user;
  const metadata = authUser.app_metadata ?? {};
  const metadataValues = [metadata.legacy_id, metadata.username, metadata.user, metadata.admin_id, metadata.admin_user, authUser.id];
  const metadataMatches = metadataValues
    .some((candidate) => lower(candidate) === lower(actorKey));

  const { data: state, error: stateError } = await supabase
    .from("lotterynet_users_state")
    .select("payload")
    .eq("scope", "global")
    .maybeSingle();
  if (stateError) return { ok: false, message: stateError.message, aliases: new Set(), accounts: [] };
  const payload = (state?.payload ?? {}) as Record<string, unknown>;
  const accounts = allAccounts(payload);
  const authAccounts = accounts.filter((account) =>
    [account.authUserId, account.auth_user_id].some((candidate) => clean(candidate) === authUser.id)
  );
  const actor = findAccountByAny(accounts, [actorKey, ...metadataValues]);
  if (!actor && metadataMatches) {
    return {
      ok: true,
      aliases: mergeSets(accountAliases(undefined, actorKey, ...metadataValues), ...authAccounts.map((account) => accountAliases(account))),
      accounts,
    };
  }
  if (!actor && authAccounts.length === 0) return { ok: false, message: "Usuario no existe en servidor.", aliases: new Set(), accounts };
  if (actor && (actor.activo === false || actor.active === false || actor.blocked === true)) {
    return { ok: false, message: "Usuario bloqueado.", aliases: new Set(), accounts };
  }
  const linkedAuthUser = clean(actor?.authUserId ?? actor?.auth_user_id);
  if (linkedAuthUser && linkedAuthUser !== authUser.id && authAccounts.length === 0) {
    return { ok: false, message: "Sesion no pertenece al usuario que intenta pagar.", aliases: new Set(), accounts };
  }
  return {
    ok: true,
    aliases: mergeSets(accountAliases(actor, actorKey, ...metadataValues), ...authAccounts.map((account) => accountAliases(account))),
    accounts,
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const rawTicketId = clean(body.ticketId);
    const localTicketId = clean(body.localTicketId);
    const clientRequestId = clean(body.clientRequestId);
    const actorKey = clean(body.actorKey);
    const adminKey = clean(body.adminKey ?? body.ownerKey);
    const cashierKey = clean(body.cashierKey);

    if (!rawTicketId && !localTicketId && !clientRequestId) {
      return json({ ok: false, message: "ticketId, localTicketId o clientRequestId requerido." }, 400);
    }
    if (!actorKey) return json({ ok: false, message: "actorKey requerido." }, 400);
    const authActor = await authenticatedActor(req, actorKey);
    if (!authActor.ok) return json({ ok: false, message: authActor.message ?? "Usuario no autorizado." }, 403);

    const lookupValues = [rawTicketId, clientRequestId, localTicketId].map(clean).filter(Boolean);
    let query = supabase
      .from("tickets")
      .select("id, admin_key, cashier_key, legacy_ticket_id, client_request_id, ticket_code, deleted_at, voided_at, invalidated_at")
      .limit(1);
    const uuidValue = lookupValues.find(isUuid);
    if (uuidValue) {
      query = query.eq("id", uuidValue);
    } else {
      const escaped = lookupValues.map((value) => value.replaceAll('"', '\\"'));
      query = query.or(escaped.flatMap((value) => [
        `client_request_id.eq.${value}`,
        `legacy_ticket_id.eq.${value}`,
        `ticket_code.eq.${value}`,
      ]).join(","));
    }

    const { data: tickets, error: lookupError } = await query;
    if (lookupError) throw lookupError;
    const ticket = tickets?.[0] as Record<string, unknown> | undefined;
    if (!ticket) return json({ ok: false, message: "Ticket no encontrado para pago." }, 404);

    if (ticket.deleted_at || ticket.voided_at || ticket.invalidated_at) {
      return json({ ok: false, message: "Ticket anulado, borrado o invalidado no se puede pagar." }, 409);
    }

    const ticketAdmin = lower(ticket.admin_key);
    const ticketCashier = lower(ticket.cashier_key);
    const ticketAdminAliases = aliasesForValue(authActor.accounts, clean(ticket.admin_key));
    const ticketCashierAliases = aliasesForValue(authActor.accounts, clean(ticket.cashier_key));
    const requestedAdminAliases = aliasesForValue(authActor.accounts, adminKey);
    const requestedCashierAliases = aliasesForValue(authActor.accounts, cashierKey);
    const actorInScope = authActor.aliases.has(ticketAdmin) ||
      authActor.aliases.has(ticketCashier) ||
      intersects(authActor.aliases, ticketAdminAliases) ||
      intersects(authActor.aliases, ticketCashierAliases) ||
      intersects(authActor.aliases, requestedAdminAliases) ||
      intersects(authActor.aliases, requestedCashierAliases);
    if (!actorInScope) return json({ ok: false, message: "Usuario fuera del alcance de este ticket." }, 403);
    if (adminKey && ticketAdmin && !requestedAdminAliases.has(ticketAdmin) && !intersects(requestedAdminAliases, ticketAdminAliases)) {
      return json({ ok: false, message: "El ticket no pertenece a esta banca/admin." }, 403);
    }

    const { data, error } = await supabase.rpc("lotterynet_pay_ticket_server_first", {
      p_ticket_id: ticket.id,
      p_client_request_id: clean(ticket.client_request_id) || null,
      p_legacy_ticket_id: clean(ticket.legacy_ticket_id) || null,
      p_actor_key: actorKey,
      p_admin_key: adminKey || clean(ticket.admin_key),
      p_cashier_key: cashierKey || clean(ticket.cashier_key),
      p_reference: "edge-pay-ticket",
    });
    if (error) {
      const message = errorMessage(error);
      if (isUnconfirmedPrizeError(message)) {
        return json({ ok: false, message: "El ticket no tiene premio confirmado." }, 409);
      }
      throw error;
    }
    return json((data ?? { ok: true }) as Record<string, unknown>);
  } catch (error) {
    const message = errorMessage(error);
    if (isUnconfirmedPrizeError(message)) {
      return json({ ok: false, message: "El ticket no tiene premio confirmado." }, 409);
    }
    return json({
      ok: false,
      message: message || "No se pudo confirmar el pago en servidor.",
    }, 500);
  }
});
