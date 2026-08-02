import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { authenticatedActor, clean, corsHeaders, json, roleOf, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

function keys(actor: { identityKeys: string[]; account: Record<string, unknown> | null }): string[] {
  return [
    ...actor.identityKeys,
    actor.account?.id,
    actor.account?.user,
    actor.account?.adminId,
    actor.account?.adminUser,
    actor.account?.banca,
  ].map(clean).filter(Boolean);
}

function same(value: unknown, candidates: string[]): boolean {
  const normalized = clean(value).toLowerCase();
  return !!normalized && candidates.some((candidate) => normalized === candidate.toLowerCase());
}

function accountKeys(account: Record<string, unknown>): string[] {
  return [
    account.id,
    account.user,
    account.username,
    account.userId,
    account.authUserId,
    account.auth_user_id,
    account.legacy_id,
    account.legacy_key,
    account.banca,
  ].map(clean).filter(Boolean);
}

function accountLabel(account: Record<string, unknown> | null, fallback: string, fallbackRole: string): { label: string; role: string } {
  const role = roleOf(account) || fallbackRole;
  if (role === "admin") return { label: "Admin", role };
  if (role === "master") return { label: "Master", role };
  const label = clean(account?.displayName ?? account?.nombre ?? account?.alias ?? account?.ownerName ?? account?.user ?? account?.username ?? account?.id) || fallback;
  return { label, role: role || "cajero" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
  const auth = await authenticatedActor(req, ["master", "admin", "cajero"]);
  if (!auth.ok) return auth.response;
  try {
    const body = await req.json().catch(() => ({}));
    const module = clean(body.module).toLowerCase();
    if (module && module !== "services" && module !== "video_games") return json({ ok: false, message: "Modulo invalido." }, 400);
    const from = clean(body.from);
    const to = clean(body.to);
    let query = supabaseAdmin()
      .from("services_games_operations")
      .select("id, client_request_id, module, provider_id, product_id, actor_user_id, admin_key, cashier_key, amount, provider_cost, commission, status, provider_reference, created_at, updated_at")
      .order("created_at", { ascending: false })
      .limit(500);
    if (module) query = query.eq("module", module);
    if (from) query = query.gte("created_at", from);
    if (to) query = query.lt("created_at", to);
    if (auth.actor.role === "cajero") {
      const cashierKey = clean(auth.actor.account?.id ?? auth.actor.account?.user ?? auth.actor.userId);
      query = query.eq("cashier_key", cashierKey);
    } else if (auth.actor.role === "admin") {
      const adminKeys = keys(auth.actor);
      query = query.in("admin_key", adminKeys);
    }
    const { data, error } = await query;
    if (error) throw error;
    const rows = (data ?? []).map((row) => {
      const rowKeys = [row.actor_user_id, row.cashier_key, row.admin_key].map(clean).filter(Boolean);
      const account = auth.actor.accounts.find((candidate) => accountKeys(candidate).some((key) => same(key, rowKeys))) ?? null;
      const fallback = clean(row.cashier_key) ? `Cajero ${clean(row.cashier_key)}` : auth.actor.role === "admin" ? "Admin" : auth.actor.role === "master" ? "Master" : "Cajero";
      const actor = accountLabel(account, fallback, auth.actor.role);
      return { ...row, actor_label: actor.label, actor_role: actor.role };
    });
    return json({
      ok: true,
      module: module || "all",
      rows,
      summary: {
        count: rows.length,
        amount: rows.reduce((sum, row) => sum + Number(row.amount || 0), 0),
        providerCost: rows.reduce((sum, row) => sum + Number(row.provider_cost || 0), 0),
        commission: rows.reduce((sum, row) => sum + Number(row.commission || 0), 0),
      },
    });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo leer el reporte separado." }, 500);
  }
});
