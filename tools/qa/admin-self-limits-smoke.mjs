const SUPABASE_URL = "https://unhoulkujbtsypccpirc.supabase.co";
const API_KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK";

function headers() {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${API_KEY}`,
    "Content-Type": "application/json; charset=utf-8",
    Accept: "application/json",
  };
}

function numberValue(value) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function playLimit(config, playType) {
  const source = config ?? {};
  switch (playType) {
    case "SUPER_PALE":
      return numberValue(source.sp ?? source.superPale ?? source.super_pale ?? source.p);
    case "PICK3_STRAIGHT":
      return numberValue(source.p3 ?? source.pick3Straight ?? source.pick3_straight);
    default:
      return 0;
  }
}

function hasPositiveLimit(config) {
  return [
    config?.daySale,
    config?.payout,
    config?.q,
    config?.quiniela,
    config?.pale,
    config?.p,
    config?.sp,
    config?.superPale,
    config?.super_pale,
    config?.t,
    config?.tripleta,
    config?.p3,
    config?.pick3Straight,
    config?.pick3_straight,
    config?.p3box,
    config?.pick3Box,
    config?.pick3_box,
    config?.p4,
    config?.pick4Straight,
    config?.pick4_straight,
    config?.p4box,
    config?.pick4Box,
    config?.pick4_box,
  ].some((value) => numberValue(value) > 0);
}

function resolveLimitConfig(root, actorKey, role) {
  const defaults = root?.defaults ?? {};
  const byUser = root?.byUser ?? {};
  const adminSelf = root?.adminSelf ?? {};
  const row = byUser[actorKey] ?? null;
  if (["admin", "admins", "master", "masters"].includes(String(role ?? "").toLowerCase())) {
    if (hasPositiveLimit(adminSelf)) return adminSelf;
    if (row && hasPositiveLimit(row)) return row;
    return {};
  }
  return { ...defaults, ...(row ?? {}) };
}

async function requestJson(label, url) {
  const response = await fetch(url, {
    method: "GET",
    headers: headers(),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  if (!response.ok) {
    throw new Error(`${label} ${response.status}: ${text}`);
  }
  return json;
}

async function fetchLimitPayload(adminKey) {
  const rows = await requestJson(
    `cashier_limits ${adminKey}`,
    `${SUPABASE_URL}/rest/v1/lotterynet_master_state?config_key=eq.cashier_limits%3A${encodeURIComponent(adminKey)}&select=payload`,
  );
  return rows?.[0]?.payload ?? {};
}

function check(condition, label, data = {}) {
  const ok = Boolean(condition);
  console.log(`${ok ? "PASS" : "BUG"} ${label} ${JSON.stringify(data)}`);
  if (!ok) process.exitCode = 1;
}

async function run() {
  console.log("Inicio admin self limits smoke");

  const nicolaPayload = await fetchLimitPayload("ADM-163C38");
  const poderosoPayload = await fetchLimitPayload("ADM-C5FFB0");
  const unlimitedAdmin = resolveLimitConfig(nicolaPayload, "nicola01", "admin");
  const limitedCashier = resolveLimitConfig(nicolaPayload, "bancay03", "cashier");
  const legacySelfAdmin = resolveLimitConfig(poderosoPayload, "podero02", "admin");

  check(
    Object.keys(unlimitedAdmin ?? {}).length === 0,
    "admin sin adminSelf no hereda defaults de cajeros",
    { adminSuperPale: playLimit(unlimitedAdmin, "SUPER_PALE") },
  );
  check(
    playLimit(limitedCashier, "SUPER_PALE") > 0 && playLimit(limitedCashier, "PICK3_STRAIGHT") > 0,
    "cajero sigue usando sus limites normales",
    {
      cashierSuperPale: playLimit(limitedCashier, "SUPER_PALE"),
      cashierPick3: playLimit(limitedCashier, "PICK3_STRAIGHT"),
    },
  );
  check(
    playLimit(legacySelfAdmin, "SUPER_PALE") === 100,
    "admin con limite propio legacy conserva su tope",
    { adminSuperPale: playLimit(legacySelfAdmin, "SUPER_PALE") },
  );

  if (process.exitCode) {
    throw new Error("Admin self limits smoke detecto una regresion");
  }
}

run().catch((error) => {
  console.error(`BUG admin self limits smoke ${error?.message ?? error}`);
  process.exit(1);
});
