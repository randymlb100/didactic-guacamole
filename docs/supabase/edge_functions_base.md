# LotteryNet Pro Edge Functions Base

Todas las funciones críticas usan JWT del usuario y `SUPABASE_SERVICE_ROLE_KEY` solo en Deno.
El APK nunca recibe service role ni claves externas.

## Variables

```env
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=
RESULTS_API_URL=
RESULTS_API_KEY=
RECARGAS_RAPIDAS_BASE_URL=
RECARGAS_RAPIDAS_MASTER_KEY=
APP_MIN_VERSION_ANDROID=1.0.0
```

## Shared `index.ts` Pattern

```ts
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

type Role = "master" | "admin" | "supervisor" | "cajero";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function requireProfile(req: Request, allowed: Role[]) {
  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.replace(/^Bearer\s+/i, "");
  if (!token) throw new Error("Sesion requerida");

  const { data: authData, error: authError } = await admin.auth.getUser(token);
  if (authError || !authData.user) throw new Error("Sesion invalida");

  const { data: profile, error } = await admin
    .from("profiles")
    .select("*")
    .eq("id", authData.user.id)
    .single();
  if (error || !profile) throw new Error("Perfil no encontrado");
  if (profile.status !== "activo") throw new Error("Usuario bloqueado");
  if (!allowed.includes(profile.role)) throw new Error("Rol no autorizado");
  return profile;
}

async function audit(actorId: string, action: string, metadata: Record<string, unknown>) {
  await admin.from("auditoria").insert({
    actor_id: actorId,
    action,
    metadata,
  });
}
```

## `create-ticket`

```ts
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const actor = await requireProfile(req, ["cajero", "admin"]);
    const body = await req.json();

    const { data: config } = await admin.from("app_config").select("value").eq("key", "maintenance").maybeSingle();
    if (config?.value?.enabled === true) throw new Error("Sistema en mantenimiento");

    const { data: sorteo } = await admin.from("sorteos").select("*, lotteries(*)").eq("id", body.sorteoId).single();
    if (!sorteo?.active || !sorteo.lotteries?.active) throw new Error("Loteria o sorteo inactivo");

    const { data: closed } = await admin
      .from("cierres_sorteos")
      .select("id")
      .eq("sorteo_id", body.sorteoId)
      .eq("draw_date", body.drawDate)
      .maybeSingle();
    if (closed) throw new Error("Sorteo cerrado");

    const total = body.jugadas.reduce((sum: number, item: any) => sum + Number(item.amount || 0), 0);
    if (total <= 0) throw new Error("Monto invalido");

    const { data: balance } = await admin.from("balances").select("*").eq("owner_id", actor.id).single();
    if (!balance || Number(balance.available) < total) throw new Error("Balance insuficiente");

    const ticketCode = crypto.randomUUID().replaceAll("-", "").slice(0, 16).toUpperCase();
    const now = new Date();
    const voidUntil = new Date(now.getTime() + 5 * 60_000);

    const { data: ticket, error } = await admin.from("tickets").insert({
      ticket_code: ticketCode,
      qr_payload: JSON.stringify({ ticketCode }),
      profile_id: actor.id,
      admin_id: actor.admin_owner_id ?? actor.parent_user_id ?? actor.id,
      banca_uuid: actor.banca_id,
      sorteo_id: body.sorteoId,
      draw_date_real: body.drawDate,
      total_amount: total,
      status: "VALIDO",
      estado: "VALIDO",
      printed_at: now.toISOString(),
      void_until: voidUntil.toISOString(),
    }).select("*").single();
    if (error) throw error;

    await admin.from("ticket_items").insert(body.jugadas.map((item: any) => ({
      ticket_id: ticket.id,
      play_type: item.playType,
      normalized_number: item.number,
      play_numbers: item.number,
      amount: item.amount,
      lottery_id: sorteo.lottery_id,
      sorteo_id: body.sorteoId,
    })));

    await admin.from("balances").update({ available: Number(balance.available) - total }).eq("owner_id", actor.id);
    await admin.from("movimientos_balance").insert({
      from_user_id: actor.id,
      movement_type: "TICKET_SALE",
      amount: total,
      before_balance: balance.available,
      after_balance: Number(balance.available) - total,
      reference: ticketCode,
    });
    await audit(actor.id, "ticket.create", { ticketId: ticket.id, ticketCode, total });
    return json({ ticket });
  } catch (error) {
    return json({ message: error instanceof Error ? error.message : "Error creando ticket" }, 400);
  }
});
```

## Function Responsibilities

- `auth-validate-session`: valida JWT, estado, dispositivo y version minima.
- `create-admin`: solo master; crea auth user, profile, banca/admin_network y balance.
- `create-supervisor`: master/admin; valida misma red; crea profile/supervisores.
- `create-cajero`: master/admin; valida cuota y banca; crea profile/cajeros.
- `block-user-cascade`: master/admin; bloquea usuario y descendientes.
- `validate-ticket`: servidor valida horario, limites, balance, loteria y sorteo.
- `create-ticket`: crea ticket oficial al imprimir.
- `void-ticket`: cajero solo antes de `void_until`; admin/supervisor con permiso.
- `invalidate-ticket`: admin/supervisor con permiso; siempre audita.
- `close-sorteo`: cierra sorteo usando hora del servidor.
- `fetch-results`: consulta API externa con secrets.
- `process-results`: guarda resultado y llama calculo.
- `calculate-prizes`: marca GANADOR/PERDEDOR y calcula payout.
- `pay-ticket`: usa constraint `pagos(ticket_id)` para impedir doble pago.
- `recharge-user`: valida jerarquia, limites, balance y registra movimiento.
- `get-cashier-report`: reporta solo cajero o red permitida.
- `get-admin-report`: admin su red; master global.
- `audit-log`: inserta auditoria desde otras funciones.
- `check-app-version`: lee `app_config.min_app_version`.
- `notify-closing`: notifica cierres próximos por sorteo.

