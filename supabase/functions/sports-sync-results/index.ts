import { bearerToken, clean, corsHeaders, json, lower, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type JsonMap = Record<string, unknown>;
const ODDS_API_BASE_URL = "https://api.odds-api.net/v1";

function asObject(value: unknown): JsonMap {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonMap : {};
}

function score(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
}

function sharedSecretMatches(req: Request): boolean {
  const expected = Deno.env.get("LOTTERYNET_ADMIN_SHARED_SECRET") ?? "";
  return !!expected && req.headers.get("x-lotterynet-admin-secret") === expected;
}

async function adminJwtMatches(req: Request): Promise<boolean> {
  const token = bearerToken(req);
  if (!token) return false;
  const { data, error } = await supabaseAdmin().auth.getUser(token);
  if (error || !data.user) return false;
  const metadata = asObject(data.user.app_metadata);
  return [metadata.role, metadata.user_role].map(lower).some((role) => role === "admin" || role === "master");
}

async function fetchResult(providerEventId: string, apiKey: string): Promise<JsonMap> {
  const response = await fetch(`${ODDS_API_BASE_URL}/events/${encodeURIComponent(providerEventId)}/results`, {
    headers: { Accept: "application/json", "X-API-Key": apiKey },
  });
  const payload = asObject(await response.json().catch(() => ({})));
  if (!response.ok) throw new Error(`${response.status} ${clean(payload.detail || payload.message || "results api error")}`);
  return payload;
}

async function settleEvent(eventId: string): Promise<string> {
  const supabaseUrl = clean(Deno.env.get("SUPABASE_URL"));
  const serviceKey = clean(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"));
  if (!supabaseUrl || !serviceKey) return "settlement deferred: service role not configured";
  const response = await fetch(`${supabaseUrl}/functions/v1/settle-sports-tickets`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: serviceKey, Authorization: `Bearer ${serviceKey}` },
    body: JSON.stringify({ eventId }),
  });
  if (!response.ok) return `settlement failed: ${response.status}`;
  return "settlement requested";
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
  if (!sharedSecretMatches(req) && !(await adminJwtMatches(req))) return json({ ok: false, message: "Admin deportivo requerido." }, 403);

  const apiKey = Deno.env.get("ODDS_API_KEY") ?? "";
  if (!apiKey) return json({ ok: false, message: "ODDS_API_KEY no esta configurada." }, 500);

  const body = asObject(await req.json().catch(() => ({})));
  const eventId = clean(body.eventId);
  let query = supabaseAdmin().from("sports_events").select("id,provider_event_id,status,home_team,away_team");
  if (eventId) query = query.eq("id", eventId);
  else query = query.in("status", ["scheduled", "open", "suspended", "started"]);
  const requestedLimit = Number(body.limit);
  const limit = Number.isFinite(requestedLimit) ? Math.trunc(requestedLimit) : 50;
  const { data: events, error } = await query.limit(Math.min(Math.max(limit, 1), 100));
  if (error) return json({ ok: false, message: error.message }, 500);

  const updated: string[] = [];
  const pending: string[] = [];
  const errors: string[] = [];
  const settlements: Record<string, string> = {};
  for (const event of events ?? []) {
    try {
      const result = await fetchResult(clean(event.provider_event_id), apiKey);
      const resultData = asObject(result.result);
      const status = lower(result.status || "pending");
      if (status !== "final") {
        if (["cancelled", "canceled", "void"].includes(status)) {
          const { error: cancelError } = await supabaseAdmin().from("sports_events").update({
            status: "cancelled",
            result_source: "odds-api.net",
            result_payload: result,
            result_updated_at: new Date().toISOString(),
          }).eq("id", event.id);
          if (cancelError) throw cancelError;
          updated.push(clean(event.id));
          settlements[clean(event.id)] = await settleEvent(clean(event.id));
          continue;
        }
        pending.push(clean(event.id));
        continue;
      }
      const homeScore = score(resultData.home_score);
      const awayScore = score(resultData.away_score);
      if (homeScore === null || awayScore === null) throw new Error("resultado final sin marcadores validos");
      const { error: updateError } = await supabaseAdmin().from("sports_events").update({
        status: "final",
        home_score: homeScore,
        away_score: awayScore,
        result_source: "odds-api.net",
        result_payload: result,
        result_updated_at: new Date().toISOString(),
      }).eq("id", event.id);
      if (updateError) throw updateError;
      updated.push(clean(event.id));
      settlements[clean(event.id)] = await settleEvent(clean(event.id));
    } catch (error) {
      errors.push(`${clean(event.id)}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  return json({ ok: true, source: "odds-api.net/results", updated, pending, errors, settlements });
}

Deno.serve((req) => handle(req).catch((error) => json({ ok: false, message: error instanceof Error ? error.message : "Error sincronizando resultados deportivos." }, 500)));
