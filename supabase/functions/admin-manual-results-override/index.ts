import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, fetchKvValue, json, lower, requireAdminRole, requireSharedSecret, upsertKvValue } from "../_shared/lotterynet-admin.ts";

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

function kvKeyForDate(date: string): string {
  return `manual_results_overrides_by_day:${date}`;
}

async function loadOverrides(date: string): Promise<OverrideRow[]> {
  const value = await fetchKvValue(kvKeyForDate(date));
  return Array.isArray(value) ? value as OverrideRow[] : [];
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
      const current = await loadOverrides(date);
      const next = current.filter((row) => clean(row.id) != resultId);
      await upsertKvValue(kvKeyForDate(date), next);
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

    const current = await loadOverrides(date);
    const next = current.filter((row) => clean(row.id) != resultId);
    next.push({
      id: resultId,
      name,
      date,
      number,
      game,
      editedBy,
      editedAt: new Date().toISOString(),
    });
    await upsertKvValue(kvKeyForDate(date), next);
    return json({ ok: true, saved: true, date, resultId });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo guardar override manual." }, 500);
  }
});
