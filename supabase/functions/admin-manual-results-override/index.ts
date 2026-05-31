import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, lower, requireAdminRole, requireSharedSecret, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

type OverrideRow = {
  id: string;
  name: string;
  date: string;
  number: string;
  game: string;
  editedBy?: string;
  editedAt?: string;
};

function isValidNumber(number: string, game: string): boolean {
  if (game === "pick3") return /^\d-\d-\d$/.test(number);
  if (game === "pick4") return /^\d-\d-\d-\d$/.test(number);
  return /^\d{2}-\d{2}-\d{2}$/.test(number);
}

function dateKey(value: unknown): string {
  return clean(value);
}

function resultDateFromDayKey(date: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) return date;
  const [day, month, year] = date.split("-");
  return `${year}-${month}-${day}`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const secretError = requireSharedSecret(req);
  if (secretError) return secretError;

  try {
    const body = await req.json().catch(() => ({}));
    const roleError = requireAdminRole(body.actorRole ?? body.role);
    if (roleError) return roleError;

    const date = dateKey(body.date);
    const resultId = clean(body.resultId ?? body.id);
    if (!date || !resultId) {
      return json({ ok: false, message: "date y resultId son requeridos." }, 400);
    }

    if (req.method === "DELETE") {
      const { error } = await supabaseAdmin()
        .from("result_draws")
        .delete()
        .eq("result_day_key", date)
        .eq("lottery_legacy_id", resultId)
        .eq("source", "manual-override");
      if (error) throw error;
      return json({ ok: true, deleted: true, date, resultId });
    }

    if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

    const name = clean(body.name);
    const number = clean(body.number);
    const game = lower(body.game).replaceAll("-", "");
    const editedBy = clean(body.editedBy ?? body.actorUser);
    if (!name || !number) {
      return json({ ok: false, message: "name y number son requeridos." }, 400);
    }
    if (!isValidNumber(number, game)) {
      return json({ ok: false, message: "Formato de numero invalido." }, 400);
    }

    const overrideRow: OverrideRow = {
      id: resultId,
      name,
      date,
      number,
      game,
      editedBy,
      editedAt: new Date().toISOString(),
    };
    const { error } = await supabaseAdmin()
      .from("result_draws")
      .upsert({
        source: "manual-override",
        result_date: resultDateFromDayKey(date),
        result_day_key: date,
        lottery_legacy_id: resultId,
        lottery_name: name,
        game: game === "pick3" || game === "pick4" ? game : "normal",
        draw_name: "",
        number_raw: number,
        number_digits: number.replace(/\D/g, ""),
        status: "published",
        source_payload: {
          ...overrideRow,
          isManualOverride: true,
          source: "manual-override",
          lotteryId: resultId,
          lotteryName: name,
        },
        source_hash: crypto.randomUUID(),
        updated_at: new Date().toISOString(),
      }, { onConflict: "result_day_key,lottery_legacy_id,game,draw_name" });
    if (error) throw error;
    return json({ ok: true, saved: true, date, resultId });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar override manual." }, 500);
  }
});
