import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type HealthEvent = Record<string, unknown>;

const MAX_EVENTS = 100;

function eventType(event: HealthEvent): string {
  return clean(event.eventType ?? event.type ?? "client_event").slice(0, 80);
}

function safePayload(event: HealthEvent): Record<string, unknown> {
  const payload = event.payload;
  if (payload && typeof payload === "object" && !Array.isArray(payload)) {
    return payload as Record<string, unknown>;
  }
  return {};
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const events = Array.isArray(body.events) ? body.events.slice(0, MAX_EVENTS) as HealthEvent[] : [];
    if (events.length === 0) return json({ ok: true, inserted: 0 });

    const rows = events.map((event) => ({
      app_version: clean(event.appVersion ?? body.appVersion).slice(0, 80) || null,
      device_id: clean(event.deviceId ?? body.deviceId).slice(0, 120) || null,
      owner_key: clean(event.ownerKey ?? body.ownerKey).slice(0, 120) || null,
      event_type: eventType(event),
      payload: safePayload(event),
    }));

    const { error } = await supabaseAdmin()
      .from("client_health_events")
      .insert(rows);
    if (error) throw error;

    return json({ ok: true, inserted: rows.length });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo guardar health batch.",
    }, 500);
  }
});
