import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, fetchKvValue, json, supabaseAdmin, upsertKvValue } from "../_shared/lotterynet-admin.ts";

type ResultRow = Record<string, unknown>;

const RENDER_BASE_URL = Deno.env.get("LOTTERYNET_RENDER_RESULTS_URL") ?? "https://didactic-guacamole.onrender.com";
const ENV_CRON_SECRET = Deno.env.get("LOTTERYNET_RESULTS_CRON_SECRET") ?? Deno.env.get("LOTTERYNET_ADMIN_SHARED_SECRET") ?? "";
const RENDER_LIVE_TIMEOUT_MS = 60_000;

function normalizeDateKey(value: unknown): string {
  const raw = clean(value);
  if (/^\d{2}-\d{2}-\d{4}$/.test(raw)) return raw;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-");
    return `${day}-${month}-${year}`;
  }
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Santo_Domingo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date()).replaceAll("/", "-");
}

function renderDateKey(date: string): string {
  const [day, month, year] = date.split("-");
  return `${year}-${month}-${day}`;
}

function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value as Record<string, unknown>).sort().map((key) => {
      return `${JSON.stringify(key)}:${stableStringify((value as Record<string, unknown>)[key])}`;
    }).join(",")}}`;
  }
  return JSON.stringify(value);
}

function rowsFrom(value: unknown): ResultRow[] {
  if (!value) return [];
  if (typeof value === "string") {
    try {
      return rowsFrom(JSON.parse(value));
    } catch {
      return [];
    }
  }
  if (Array.isArray(value)) return value.filter((row): row is ResultRow => !!row && typeof row === "object" && !Array.isArray(row));
  if (typeof value !== "object") return [];
  const objectValue = value as Record<string, unknown>;
  if (Array.isArray(objectValue.results)) return rowsFrom(objectValue.results);
  if (Array.isArray(objectValue.rows)) return rowsFrom(objectValue.rows);
  if (typeof objectValue.payload === "object") return rowsFrom(objectValue.payload);
  return [];
}

function rowIdentity(row: ResultRow): string {
  return clean(row.id) || clean(row.lotteryId) || rowText(row);
}

function hasPublishedNumbers(row: ResultRow): boolean {
  return clean(row.number) !== "" ||
    clean(row.numero) !== "" ||
    clean(row.numbers) !== "" ||
    clean(row.first) !== "" ||
    clean(row.second) !== "" ||
    clean(row.third) !== "" ||
    clean(row.pick3) !== "" ||
    clean(row.pick4) !== "" ||
    (Array.isArray(row.n) && row.n.length > 0);
}

function isProtectedNoDrawRow(row: ResultRow): boolean {
  const text = [
    row.status,
    row.estado,
    row.source,
    row.reason,
    row.message,
  ].map((value) => clean(value).toLowerCase()).join(" ");
  return text.includes("no_draw") ||
    text.includes("no draw") ||
    text.includes("sin sorteo") ||
    text.includes("no hubo sorteo") ||
    text.includes("feriado") ||
    text.includes("holiday");
}

function mergeProtectedNoDrawRows(currentRows: ResultRow[], nextRows: ResultRow[]): ResultRow[] {
  if (currentRows.length === 0) return nextRows;
  const nextPublishedIds = new Set(nextRows.filter(hasPublishedNumbers).map(rowIdentity).filter(Boolean));
  const nextIds = new Set(nextRows.map(rowIdentity).filter(Boolean));
  const protectedRows = currentRows.filter((row) => {
    const id = rowIdentity(row);
    return id !== "" && isProtectedNoDrawRow(row) && !nextPublishedIds.has(id) && !nextIds.has(id);
  });
  return protectedRows.length === 0 ? nextRows : [...nextRows, ...protectedRows];
}

function mergeMissingCurrentRows(currentRows: ResultRow[], nextRows: ResultRow[]): ResultRow[] {
  if (currentRows.length === 0 || nextRows.length === 0) return nextRows;
  const nextIds = new Set(nextRows.map(rowIdentity).filter(Boolean));
  const missingRows = currentRows.filter((row) => {
    const id = rowIdentity(row);
    return id !== "" && !nextIds.has(id);
  });
  return missingRows.length === 0 ? nextRows : [...nextRows, ...missingRows];
}

function mergeHolidayNoDrawRows(nextRows: ResultRow[], holidayRows: ResultRow[]): ResultRow[] {
  if (holidayRows.length === 0) return nextRows;
  const holidayById = new Map<string, ResultRow>(
    holidayRows
      .map((row): [string, ResultRow] => [rowIdentity(row), row])
      .filter(([id]) => id !== ""),
  );
  const mergedRows = nextRows.map((row) => {
    const id = rowIdentity(row);
    if (!holidayById.has(id) || hasPublishedNumbers(row)) return row;
    const holidayRow = holidayById.get(id) as ResultRow;
    holidayById.delete(id);
    return holidayRow;
  });
  return [...mergedRows, ...holidayById.values()];
}

function dateIsoFromKey(date: string): string {
  const [day, month, year] = date.split("-");
  return `${year}-${month}-${day}`;
}

function annualDateFromKey(date: string): string {
  const [day, month] = date.split("-");
  return `${month}-${day}`;
}

function matchesHolidayRuleDate(rule: Record<string, unknown>, date: string): boolean {
  const isoDate = dateIsoFromKey(date);
  const annualDate = annualDateFromKey(date);
  const dates = Array.isArray(rule.dates) ? rule.dates.map(clean) : [];
  const annualDates = Array.isArray(rule.annualDates) ? rule.annualDates.map(clean) : [];
  return dates.includes(date) || dates.includes(isoDate) || annualDates.includes(annualDate);
}

async function fetchHolidayNoDrawRows(date: string): Promise<ResultRow[]> {
  const rawConfig = await fetchKvValue("lottery_holiday_rules");
  const config = typeof rawConfig === "string"
    ? (() => {
      try {
        return JSON.parse(rawConfig) as unknown;
      } catch {
        return null;
      }
    })()
    : rawConfig;
  if (!config || typeof config !== "object" || Array.isArray(config)) return [];
  const rules = Array.isArray((config as Record<string, unknown>).rules)
    ? (config as Record<string, unknown>).rules as Record<string, unknown>[]
    : [];
  return rules.flatMap((rule) => {
    if (rule.noDraw !== true || !matchesHolidayRuleDate(rule, date)) return [];
    const reason = clean(rule.reason) || clean(rule.name) || "Feriado";
    const lotteries = Array.isArray(rule.lotteries) ? rule.lotteries : [];
    return lotteries.flatMap((lottery) => {
      if (!lottery || typeof lottery !== "object" || Array.isArray(lottery)) return [];
      const lotteryObject = lottery as Record<string, unknown>;
      const id = clean(lotteryObject.id);
      const name = clean(lotteryObject.name);
      if (!id || !name) return [];
      return [{
        id,
        lotteryId: id,
        name,
        lotteryName: name,
        date,
        status: "no_draw",
        source: "no_draw",
        reason,
        message: `No hubo sorteo por ${reason}`,
      }];
    });
  });
}

function payloadRows(payload: unknown, section: "lotteries" | "picks"): ResultRow[] {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return [];
  return rowsFrom((payload as Record<string, unknown>)[section]);
}

function rowText(row: ResultRow): string {
  return [
    row.id,
    row.lotteryId,
    row.name,
    row.lotteryName,
    row.game,
    row.gameName,
  ].map((value) => clean(value).toLowerCase()).join(" ");
}

function isPickRow(row: ResultRow): boolean {
  const text = rowText(row).replace(/[^a-z0-9]/g, "");
  return clean(row.pick3) !== "" ||
    clean(row.pick4) !== "" ||
    text.includes("pick3") ||
    text.includes("pick4") ||
    text.includes("play3") ||
    text.includes("play4") ||
    text.includes("cash3") ||
    text.includes("cash4") ||
    text.includes("daily3") ||
    text.includes("daily4") ||
    text.includes("win4");
}

function splitPayload(payload: unknown): { lotteries: ResultRow[]; picks: ResultRow[] } {
  const sectionLotteryRows = payloadRows(payload, "lotteries");
  const sectionPickRows = payloadRows(payload, "picks");
  if (sectionLotteryRows.length > 0 || sectionPickRows.length > 0) {
    return { lotteries: sectionLotteryRows, picks: sectionPickRows };
  }
  const rows = rowsFrom(payload);
  return {
    lotteries: rows.filter((row) => !isPickRow(row)),
    picks: rows.filter(isPickRow),
  };
}

async function fetchLiveResults(date: string): Promise<unknown> {
  const url = new URL("/run-system-scraper", RENDER_BASE_URL);
  url.searchParams.set("date", renderDateKey(date));
  url.searchParams.set("mode", "both");
  const response = await fetch(url, {
    method: "POST",
    headers: { "Accept": "application/json" },
    signal: AbortSignal.timeout(RENDER_LIVE_TIMEOUT_MS),
  });
  if (!response.ok) {
    throw new Error(`Render results failed ${response.status}`);
  }
  return await response.json();
}

async function fetchLiveResultsOrEmpty(date: string): Promise<{ payload: unknown; error: string }> {
  try {
    return { payload: await fetchLiveResults(date), error: "" };
  } catch (error) {
    return {
      payload: {},
      error: error instanceof Error ? error.message : "No se pudo consultar Render.",
    };
  }
}

async function currentRowsFor(date: string, source: "lottery" | "pick"): Promise<ResultRow[]> {
  const gameFilter = source === "pick" ? ["pick3", "pick4"] : ["normal"];
  const { data, error } = await supabaseAdmin()
    .from("result_draws")
    .select("source_payload")
    .eq("result_day_key", date)
    .in("game", gameFilter);
  if (error) throw error;
  return (data ?? []).map((row) => row.source_payload as ResultRow).filter(Boolean);
}

async function upsertIfChanged(source: "lottery" | "pick", date: string, rows: ResultRow[]): Promise<boolean> {
  if (rows.length === 0) return false;
  const currentRows = await currentRowsFor(date, source);
  const effectiveRows = mergeMissingCurrentRows(currentRows, mergeProtectedNoDrawRows(currentRows, rows));
  if (stableStringify(currentRows) === stableStringify(effectiveRows)) return false;
  const { data, error } = await supabaseAdmin()
    .rpc("lotterynet_upsert_result_draws_from_payload", {
      p_result_day_key: date,
      p_payload: effectiveRows,
      p_source: source,
    });
  if (error) throw error;
  return Number(data ?? 0) > 0;
}

async function saveLegacyResultCaches(date: string, lotteryRows: ResultRow[], pickRows: ResultRow[]): Promise<void> {
  if (lotteryRows.length > 0) {
    await upsertKvValue(`lot_results_cache_by_day:${date}`, lotteryRows);
  }
  if (pickRows.length > 0) {
    await upsertKvValue(`pick_results_cache_by_day:${date}`, pickRows);
  }
}

async function processPrizeJobsForDay(date: string): Promise<{ data: unknown; error: string | null }> {
  try {
    const { data, error } = await supabaseAdmin()
      .rpc("lotterynet_process_result_reconcile_jobs_for_day", {
        p_result_day_key: date,
        p_job_limit: 12,
        p_ticket_limit: 500,
      });
    if (error) throw error;
    return { data, error: null };
  } catch (error) {
    return {
      data: null,
      error: error instanceof Error ? error.message : "No se pudo procesar premios del dia.",
    };
  }
}

async function configuredCronSecret(): Promise<string> {
  if (ENV_CRON_SECRET) return ENV_CRON_SECRET;
  const stored = await fetchKvValue("lotterynet_results_cron_secret");
  if (typeof stored === "string") {
    try {
      const parsed = JSON.parse(stored) as Record<string, unknown>;
      const parsedSecret = clean(parsed.secret);
      return parsedSecret || stored;
    } catch {
      return stored;
    }
  }
  if (stored && typeof stored === "object") return clean((stored as Record<string, unknown>).secret);
  return "";
}

async function authorize(req: Request): Promise<Response | null> {
  const expected = await configuredCronSecret();
  if (!expected) {
    return json({ ok: false, message: "LOTTERYNET_RESULTS_CRON_SECRET is not configured." }, 500);
  }
  const provided = req.headers.get("x-lotterynet-results-secret") ?? req.headers.get("x-lotterynet-admin-secret") ?? "";
  if (provided !== expected) {
    return json({ ok: false, message: "Results refresh secret is invalid." }, 403);
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
  const authError = await authorize(req);
  if (authError) return authError;

  try {
    const body = await req.json().catch(() => ({}));
    const date = normalizeDateKey(body.date);
    const live = await fetchLiveResultsOrEmpty(date);
    const payload = live.payload;
    const split = splitPayload(payload);
    const holidaySplit = splitPayload(await fetchHolidayNoDrawRows(date));
    const lotteryBaseRows = split.lotteries.length > 0 ? split.lotteries : await currentRowsFor(date, "lottery");
    const pickBaseRows = split.picks.length > 0 ? split.picks : await currentRowsFor(date, "pick");
    const lotteryRows = mergeHolidayNoDrawRows(lotteryBaseRows, holidaySplit.lotteries);
    const pickRows = mergeHolidayNoDrawRows(pickBaseRows, holidaySplit.picks);
    const lotteryChanged = await upsertIfChanged("lottery", date, lotteryRows);
    const pickChanged = await upsertIfChanged("pick", date, pickRows);
    await saveLegacyResultCaches(date, lotteryRows, pickRows);
    const prizeReconcile = lotteryChanged || pickChanged
      ? await processPrizeJobsForDay(date)
      : { data: null, error: null };

    return json({
      ok: true,
      date,
      liveError: live.error || null,
      changed: lotteryChanged || pickChanged,
      prizeReconcile,
      lotteries: { rows: lotteryRows.length, changed: lotteryChanged },
      picks: { rows: pickRows.length, changed: pickChanged },
      legacyCache: {
        lotteryKey: `lot_results_cache_by_day:${date}`,
        pickKey: `pick_results_cache_by_day:${date}`,
      },
    });
  } catch (error) {
    return json({
      ok: false,
      message: error instanceof Error ? error.message : "No se pudo refrescar resultados en servidor.",
    }, 500);
  }
});
