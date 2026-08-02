import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  asRecord,
  authenticatedActor,
  type AuthenticatedActor,
  clean,
  corsHeaders,
  fetchKvValue,
  json,
  supabaseAdmin,
} from "../_shared/lotterynet-admin.ts";
import { redisGetJson, redisIncrement, redisSetJson } from "../_shared/upstash-redis.ts";

const PROVIDER_BASE_URL = (Deno.env.get("RECARGAS_RAPIDAS_API_BASE") || "http://198.23.59.27:4000").replace(/\/$/, "");
const CATALOG_CACHE_TTL_SECONDS = 60;
const READ_RATE_WINDOW_SECONDS = 60;
const READ_RATE_LIMITS: Record<string, number> = { catalog: 12, query: 30 };
const tokenCache = new Map<string, { token: string; expiresAt: number }>();

type ProviderCredentials = { username: string; password: string };

function validModule(value: unknown): "services" | "video_games" | null {
  const module = clean(value).toLowerCase();
  return module === "services" || module === "video_games" ? module : null;
}

function normalizeArray(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter((item) => item && typeof item === "object") as Record<string, unknown>[] : [];
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map(clean).filter(Boolean).map((item) => item.toLowerCase()) : [];
}

function identityKeys(value: Record<string, unknown> | null): string[] {
  if (!value) return [];
  return [value.id, value.user, value.username, value.authUserId, value.auth_user_id]
    .map(clean)
    .filter(Boolean)
    .map((item) => item.toLowerCase());
}

function accountAdminKeys(value: Record<string, unknown> | null): string[] {
  if (!value) return [];
  return [value.adminId, value.admin_id, value.adminUser, value.admin_user, value.banca]
    .map(clean)
    .filter(Boolean)
    .map((item) => item.toLowerCase());
}

function actorAdminKey(actor: AuthenticatedActor): string {
  // For an admin, the actor itself is the business owner. For a cashier,
  // prefer the parent admin identity. Master is kept unscoped in reports.
  if (actor.role === "admin") {
    return clean(actor.account?.id ?? actor.account?.user ?? actor.account?.username ?? actor.userId);
  }
  return clean(actor.account?.adminId ?? actor.account?.adminUser ?? actor.account?.banca);
}

function readPayload(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try { return asRecord(JSON.parse(value)); } catch { return {}; }
  }
  return asRecord(value);
}

async function readMasterConfig(key: string): Promise<Record<string, unknown>> {
  const { data, error } = await supabaseAdmin()
    .from("lotterynet_master_state")
    .select("payload")
    .eq("config_key", key)
    .maybeSingle();
  if (error) throw error;
  if (data?.payload !== null && data?.payload !== undefined) return readPayload(data.payload);
  return readPayload(await fetchKvValue(key));
}

function canOpenModule(actor: AuthenticatedActor, config: Record<string, unknown>): boolean {
  if (actor.role === "master") return config.enabled === true;
  if (config.enabled !== true) return false;
  const actorKeys = new Set(identityKeys(actor.account).concat(actor.identityKeys.map((item) => item.toLowerCase())));
  const accountKeys = new Set(accountAdminKeys(actor.account));
  const admins = new Set(stringArray(config.allowedAdminKeys));
  const cashiers = new Set(stringArray(config.allowedCashierKeys));
  const cashierAdmins = new Set(stringArray(config.cashierAdminKeys));
  // The config arrays are intentionally compared case-insensitively, like the Android contract.
  const has = (set: Set<string>, keys: Set<string>) => [...keys].some((key) => set.has(key) || set.has(key.toLowerCase()));
  if (actor.role === "admin") return has(admins, actorKeys);
  if (actor.role === "cajero") return has(cashiers, actorKeys) || has(cashierAdmins, accountKeys);
  return false;
}

async function resolveCredentials(actor: AuthenticatedActor): Promise<ProviderCredentials> {
  const adminKeys = [actorAdminKey(actor), ...accountAdminKeys(actor.account)]
    .map(clean)
    .filter(Boolean);
  const scopes = [...new Set(adminKeys.flatMap((key) => [`admin:${key}`, `admin:${key.toLowerCase()}`])), "default"];
  for (const scope of scopes) {
    const { data, error } = await supabaseAdmin()
      .from("lotterynet_recargas_rapidas_credentials")
      .select("scope, username, password_secret, active")
      .eq("scope", scope)
      .maybeSingle();
    if (error) throw error;
    if (data && data.active !== false && clean(data.username) && clean(data.password_secret)) {
      return { username: clean(data.username), password: clean(data.password_secret) };
    }
  }

  // Backward-compatible read for deployments that still have only the old
  // master-state shape; new saves use the credentials table above.
  const config = await readMasterConfig("recargas_rapidas_credentials_v1");
  const byUser = asRecord(config.byUser);
  const byAdmin = asRecord(config.byAdmin);
  const actorKeys = [...identityKeys(actor.account), ...actor.identityKeys.map((item) => item.toLowerCase())];
  const legacyAdminKeys = accountAdminKeys(actor.account);
  const findEntry = (source: Record<string, unknown>, keys: string[]) => {
    for (const key of keys) {
      const entry = asRecord(source[key]);
      if (clean(entry.username) && clean(entry.password)) return entry;
    }
    return null;
  };
  const entry = findEntry(byUser, actorKeys) || findEntry(byAdmin, legacyAdminKeys) || asRecord(config.default);
  const username = clean(entry.username);
  const password = clean(entry.password);
  if (!username || !password) throw new Error("Credenciales de Recargas Rápidas no configuradas para este usuario.");
  return { username, password };
}

async function providerJson(path: string, options: RequestInit = {}, credentials: ProviderCredentials): Promise<unknown> {
  const cacheKey = `${credentials.username}:${credentials.password}`;
  let token = tokenCache.get(cacheKey);
  if (!token || token.expiresAt <= Date.now() + 30_000) {
    const loginResponse = await fetch(`${PROVIDER_BASE_URL}/oauth/token`, {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });
    const loginBody = await loginResponse.json().catch(() => ({}));
    if (!loginResponse.ok || !clean(loginBody.token)) throw new Error(`Proveedor no autorizó el acceso (${loginResponse.status}).`);
    token = { token: clean(loginBody.token), expiresAt: Date.now() + 20 * 60_000 };
    tokenCache.set(cacheKey, token);
  }
  const response = await fetch(`${PROVIDER_BASE_URL}/${path.replace(/^\//, "")}`, {
    ...options,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Bearer ${token.token}`,
      ...(options.headers || {}),
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Proveedor HTTP ${response.status}: ${clean(body.message) || "respuesta no disponible"}`);
  return body;
}

function servicesCatalog() {
  return [
    ["claro_postpago", "Claro", "Pago de factura", "logo_claro.svg", "", "bills_lookup", "telecom", "Telecomunicaciones"],
    ["altice_factura", "Altice", "Pago de factura", "logo_altice.svg", "", "bills_lookup", "telecom", "Telecomunicaciones"],
    ["viva_postpago", "Viva", "Pago de factura", "logo_viva.svg", "", "bills_lookup", "telecom", "Telecomunicaciones"],
    ["edenorte", "Edenorte", "Pago de factura", "services/edenorte.png", "", "bills_lookup", "electricity", "Energía eléctrica"],
    ["edesur", "Edesur", "Pago de factura", "services/edesur.png", "", "bills_lookup", "electricity", "Energía eléctrica"],
    ["edeeste", "Edeeste", "Pago de factura", "services/edeeste.png", "", "bills_lookup", "electricity", "Energía eléctrica"],
    ["luz_y_fuerza", "Luz y Fuerza", "Pago de factura", "", "", "bills_lookup", "electricity", "Energía eléctrica"],
    ["ceb", "CEB", "Pago de factura", "", "", "bills_lookup", "electricity", "Energía eléctrica"],
    ["starcable", "StarCable", "Pago de factura", "", "", "bills_lookup", "telecable", "Telecable"],
    ["alticetv", "AlticeTV", "Pago de factura", "logo_altice.svg", "", "bills_lookup", "telecable", "Telecable"],
    ["wind", "Wind", "Pago de factura", "logo_wind.svg", "", "bills_lookup", "telecable", "Telecable"],
    ["skymax", "Skymax", "Pago de factura", "", "", "bills_lookup", "telecable", "Telecable"],
    ["aster", "Aster", "Pago de factura", "", "", "bills_lookup", "telecable", "Telecable"],
    ["caasd", "CAASD", "Pago de factura", "services/caasd.png", "", "bills_lookup", "water", "Acueductos y alcantarillados"],
    ["coraaplata", "Coraaplata", "Pago de factura", "services/coraaplata.png", "", "bills_lookup", "water", "Acueductos y alcantarillados"],
    ["coraavega", "Coraavega", "Pago de factura", "services/coraavega.png", "", "bills_lookup", "water", "Acueductos y alcantarillados"],
    ["coraasan", "Coraasan", "Pago de factura", "services/coraasan.png", "", "bills_lookup", "water", "Acueductos y alcantarillados"],
    ["coaarom", "Coaarom", "Pago de factura", "", "", "bills_lookup", "water", "Acueductos y alcantarillados"],
    ["insurance", "Seguros", "Seguros de vehículos de ley", "", "", "insurance_sale", "insurance", "Seguros"],
    ["sim_activation", "Activaciones", "Activar SIM card", "", "", "sim_activation", "sim", "Activación de SIM"],
    ["remittance", "Remesas", "MonCash y NatCash", "services/moncash.png", "services/natcash.png", "remittance_calculate", "remittance", "Remesas"],
  ].map(([providerId, name, description, logoAssetKey, secondaryLogoAssetKey, serviceType, categoryKey, categoryLabel]) => ({ providerId, productId: providerId, name, description, logoAssetKey, secondaryLogoAssetKey, serviceType, categoryKey, categoryLabel, module: "services" }));
}

// La app usa IDs estables para sus catálogos; el proveedor externo espera sus
// códigos comerciales. Esta traducción vive en el adaptador server-side para
// que ninguna pantalla tenga que conocer rutas ni nombres del proveedor.
const BILL_PROVIDER_CODES: Record<string, string> = {
  claro_postpago: "Claro",
  claro: "Claro",
  altice_factura: "Orange",
  altice: "Orange",
  viva_postpago: "Viva",
  viva: "Viva",
  edenorte: "Edenorte",
  edesur: "Edesur",
  edeeste: "Edeeste",
  luz_y_fuerza: "LuzYFuerza",
  ceb: "Ceb",
  starcable: "StarCable",
  alticetv: "AlticeTV",
  wind: "Wind",
  skymax: "Skymax",
  aster: "Aster",
  caasd: "CaaSD",
  coraaplata: "CoraaPlata",
  coraavega: "CoraaVega",
  coraasan: "Coraasan",
  coaarom: "Coaarom",
};

function billProviderCode(providerId: string): string {
  const key = clean(providerId).toLowerCase();
  const code = BILL_PROVIDER_CODES[key];
  if (!code) throw new Error(`Proveedor de factura no soportado: ${providerId}`);
  return code;
}

function gameLogoAssetKey(category: Record<string, unknown>): string {
  const explicit = clean(category.localLogoAsset);
  if (/^video-games\/(delta_force|roblox|minecraft|free_fire)\.(png|svg)$/i.test(explicit)) return explicit;
  const name = clean(category.name).toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
  const known: Record<string, string> = {
    delta_force: "video-games/delta_force.png",
    deltaforce: "video-games/delta_force.png",
    roblox: "video-games/roblox.png",
    minecraft: "video-games/minecraft.svg",
    free_fire: "video-games/free_fire.png",
    freefire: "video-games/free_fire.png",
  };
  return known[name] || (name ? `video-games/${name}.svg` : "video-games/logo_videojuegos.svg");
}

function normalizeGames(categories: Record<string, unknown>[], products: Record<string, unknown>[]) {
  const categoryById = new Map(categories.map((item) => [String(item.id ?? item._id), item]));
  return products.map((product) => {
    const category = categoryById.get(String(product.categoryId ?? product.category_id)) || {};
    return {
      module: "video_games",
      providerId: "recargas_rapidas",
      productId: clean(product.id ?? product._id),
      categoryId: clean(product.categoryId ?? product.category_id),
      name: `${clean(category.name) || "Videojuego"} · ${clean(product.quantity) || clean(product.name)}`.trim(),
      categoryName: clean(category.name),
      logoUrl: "",
      logoAssetKey: gameLogoAssetKey(category),
      quantity: product.quantity ?? null,
      price: product.priceCliente ?? product.clientPrice ?? product.price ?? null,
      providerProduct: product,
    };
  });
}

function jsonObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function catalogCacheKey(module: string, actor: AuthenticatedActor, username: string): string {
  const owner = actor.role === "cajero"
    ? actor.account?.adminId ?? actor.account?.adminUser ?? actor.account?.banca ?? "cashier"
    : actor.role === "admin"
      ? actorAdminKey(actor)
      : "master";
  return `services-games:catalog:v1:${module}:${clean(owner).toLowerCase()}:${clean(username).toLowerCase()}`;
}

async function allowReadRequest(action: string, module: string, actor: AuthenticatedActor): Promise<{ allowed: boolean; remaining: number | null }> {
  const limit = READ_RATE_LIMITS[action];
  if (!limit) return { allowed: true, remaining: null };
  const actorKey = clean(actor.userId || actor.account?.id || actor.account?.user).toLowerCase();
  const key = `services-games:read-rate:v1:${module}:${action}:${actorKey}`;
  const count = await redisIncrement(key, READ_RATE_WINDOW_SECONDS);
  if (count === null) return { allowed: true, remaining: null };
  return { allowed: count <= limit, remaining: Math.max(0, limit - count) };
}

async function existingOperation(clientRequestId: string): Promise<Record<string, unknown> | null> {
  if (!clientRequestId) return null;
  const { data, error } = await supabaseAdmin()
    .from("services_games_operations")
    .select("*")
    .eq("client_request_id", clientRequestId)
    .maybeSingle();
  if (error) {
    // The migration is deployed before this function. A missing table must not be
    // mistaken for a provider response or silently create a duplicate sale.
    throw error;
  }
  return data ?? null;
}

async function reserveOperation(input: {
  clientRequestId: string;
  module: string;
  providerId: string;
  productId: string;
  actor: AuthenticatedActor;
  amount: number;
}): Promise<{ claimed: boolean; row: Record<string, unknown> }> {
  const pending = {
    client_request_id: input.clientRequestId,
    module: input.module,
    provider_id: input.providerId,
    product_id: input.productId,
    actor_user_id: input.actor.userId,
    admin_key: actorAdminKey(input.actor) || null,
    cashier_key: input.actor.role === "cajero" ? clean(input.actor.account?.id ?? input.actor.account?.user ?? input.actor.userId) : null,
    amount: input.amount,
    status: "pending",
    provider_payload: {},
  };
  const { data, error } = await supabaseAdmin()
    .from("services_games_operations")
    .insert(pending)
    .select("*")
    .single();
  if (!error && data) return { claimed: true, row: data };
  if (error?.code !== "23505") throw error;
  const existing = await existingOperation(input.clientRequestId);
  if (!existing) throw new Error("No se pudo reservar la operación idempotente.");
  return { claimed: false, row: existing };
}

async function markOperationFailed(clientRequestId: string, message: string): Promise<void> {
  if (!clientRequestId) return;
  await supabaseAdmin()
    .from("services_games_operations")
    .update({ status: "failed", provider_payload: { error: message.slice(0, 500) }, updated_at: new Date().toISOString() })
    .eq("client_request_id", clientRequestId)
    .eq("status", "pending");
}

async function markOperationUnknown(clientRequestId: string, message: string): Promise<void> {
  if (!clientRequestId) return;
  await supabaseAdmin()
    .from("services_games_operations")
    .update({ status: "unknown", provider_payload: { error: message.slice(0, 500), outcome: "unknown" }, updated_at: new Date().toISOString() })
    .eq("client_request_id", clientRequestId)
    .eq("status", "pending");
}

async function recordOperation(input: {
  clientRequestId: string;
  module: string;
  providerId: string;
  productId: string;
  actor: AuthenticatedActor;
  amount: number;
  provider: unknown;
}) {
  const provider = jsonObject(input.provider);
  const row = {
    client_request_id: input.clientRequestId,
    module: input.module,
    provider_id: input.providerId,
    product_id: input.productId,
    actor_user_id: input.actor.userId,
    admin_key: actorAdminKey(input.actor) || null,
    cashier_key: input.actor.role === "cajero" ? clean(input.actor.account?.id ?? input.actor.account?.user ?? input.actor.userId) : null,
    amount: input.amount,
    provider_cost: Number(provider.providerCost ?? provider.cost ?? provider.amount ?? input.amount) || input.amount,
    commission: Number(provider.commission ?? provider.benefit ?? provider.profit ?? 0) || 0,
    status: clean(provider.status ?? provider.state ?? "submitted") || "submitted",
    provider_reference: clean(provider.codAuth ?? provider.validationCode ?? provider.id ?? provider._id) || null,
    provider_payload: provider,
  };
  const { data, error } = await supabaseAdmin()
    .from("services_games_operations")
    .upsert(row, { onConflict: "client_request_id" })
    .select("*")
    .single();
  if (error) throw error;
  return data;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);

  const auth = await authenticatedActor(req, ["master", "admin", "cajero"]);
  if (!auth.ok) return auth.response;
  let operationRequestId = "";
  let providerCallStarted = false;
  try {
    const body = jsonObject(await req.json().catch(() => ({})));
    const module = validModule(body.module);
    const action = clean(body.action).toLowerCase();
    if (!module || !["catalog", "query", "confirm"].includes(action)) return json({ ok: false, message: "Modulo o accion invalida." }, 400);
    const config = await readMasterConfig(`${module}:global`);
    if (!canOpenModule(auth.actor, config)) return json({ ok: false, message: "Modulo no habilitado para este usuario." }, 403);
    const readRate = await allowReadRequest(action, module, auth.actor);
    if (!readRate.allowed) {
      return json({ ok: false, message: "Demasiadas consultas seguidas. Espere un momento e intente nuevamente.", retryAfterSeconds: READ_RATE_WINDOW_SECONDS }, 429);
    }
    const clientRequestId = clean(body.clientRequestId);
    operationRequestId = clientRequestId;
    if (!clientRequestId) return json({ ok: false, message: "clientRequestId requerido." }, 400);
    if (action === "confirm") {
      const prior = await existingOperation(clientRequestId);
      if (prior) {
        const priorStatus = clean(prior.status).toLowerCase();
        if (priorStatus === "pending") {
          return json({ ok: false, message: "Esta operación ya está en proceso; no se repetirá el cargo." }, 409);
        }
        if (priorStatus === "unknown") {
          return json({ ok: false, idempotent: true, retryable: false, message: "El proveedor pudo haber procesado esta operación; no se repetirá el cargo automáticamente.", operation: prior }, 409);
        }
        if (priorStatus === "failed") {
          return json({ ok: false, idempotent: true, retryable: true, message: "La operación falló antes de completarse. Genere una nueva solicitud para reintentar.", operation: prior }, 409);
        }
        return json({ ok: true, idempotent: true, module, operation: prior, provider: prior.provider_payload });
      }
    }
    if (action === "catalog") {
      if (module === "services") return json({ ok: true, module, items: servicesCatalog() });
      const credentials = await resolveCredentials(auth.actor);
      const cacheKey = catalogCacheKey(module, auth.actor, credentials.username);
      const cached = await redisGetJson<{ items?: unknown[] }>(cacheKey);
      if (cached && Array.isArray(cached.items)) {
        return json({ ok: true, module, items: cached.items, cached: true });
      }
      const categories = normalizeArray(await providerJson("gamecategories/active", { method: "GET" }, credentials));
      const products = normalizeArray(await providerJson("gameproducts/active", { method: "GET" }, credentials));
      const items = normalizeGames(categories, products);
      await redisSetJson(cacheKey, { items }, CATALOG_CACHE_TTL_SECONDS);
      return json({ ok: true, module, items, cached: false });
    }

    const credentials = await resolveCredentials(auth.actor);
    const providerPayload = jsonObject(body.providerPayload);
    if (module === "video_games" && action === "confirm") {
      const required = ["categoryId", "productId", "playerId", "zoneId", "clientName"];
      if (required.some((key) => !clean(providerPayload[key]))) return json({ ok: false, message: "Faltan datos del jugador." }, 400);
      const reservation = await reserveOperation({
        clientRequestId,
        module,
        providerId: clean(body.providerId),
        productId: clean(body.productId),
        actor: auth.actor,
        amount: Number(body.quotedPrice) || Number(providerPayload.priceCliente) || 0,
      });
      if (!reservation.claimed) return json({ ok: false, message: "Esta operación ya está en proceso; no se repetirá el cargo." }, 409);
      providerCallStarted = true;
      const result = await providerJson("gamesales", { method: "POST", body: JSON.stringify(providerPayload) }, credentials);
      const operation = await recordOperation({
        clientRequestId,
        module,
        providerId: clean(body.providerId),
        productId: clean(body.productId),
        actor: auth.actor,
        amount: Number(body.quotedPrice) || Number(providerPayload.priceCliente) || 0,
        provider: result,
      });
      return json({ ok: true, module, action, operation, provider: result });
    }

    const serviceType = clean(body.serviceType);
    // Facturas usa un identificador de cliente plano (NIC/contrato).  La
    // envoltura del endpoint de la app puede recibirlo como { value }, pero
    // nunca debemos convertir ese objeto a "[object Object]".
    const customerInputValue = body.customerInput && typeof body.customerInput === "object"
      ? (body.customerInput as Record<string, unknown>).value
        ?? (body.customerInput as Record<string, unknown>).nic
        ?? (body.customerInput as Record<string, unknown>).account
      : body.customerInput;
    const customerInput = clean(customerInputValue);
    if (!serviceType) return json({ ok: false, message: "Servicio requerido." }, 400);
    if (serviceType === "bills_lookup" || serviceType === "bills_pay") {
      if (!customerInput || !clean(body.providerId)) return json({ ok: false, message: "Proveedor y documento requeridos." }, 400);
      if (serviceType === "bills_pay") {
        const reservation = await reserveOperation({
          clientRequestId,
          module,
          providerId: clean(body.providerId),
          productId: clean(body.productId),
          actor: auth.actor,
          amount: Number(body.amount) || 0,
        });
        if (!reservation.claimed) return json({ ok: false, message: "Esta operación ya está en proceso; no se repetirá el cargo." }, 409);
      }
      const providerCode = billProviderCode(clean(body.providerId));
      const path = `bills/${encodeURIComponent(customerInput)}/${encodeURIComponent(providerCode)}`;
      if (serviceType === "bills_pay") providerCallStarted = true;
      const result = await providerJson(path, { method: serviceType === "bills_pay" ? "POST" : "GET", ...(serviceType === "bills_pay" ? { body: JSON.stringify({ amount: body.amount }) } : {}) }, credentials);
      // A lookup is a read, not a sale. It must never enter the financial
      // ledger; only the explicit payment operation is recorded.
      if (serviceType === "bills_lookup") {
        return json({ ok: true, module, action, provider: result });
      }
      const operation = await recordOperation({ clientRequestId, module, providerId: clean(body.providerId), productId: clean(body.productId), actor: auth.actor, amount: Number(body.amount) || 0, provider: result });
      return json({ ok: true, module, action, operation, provider: result });
    }
    const servicePaths: Record<string, string> = {
      insurance_sale: "insurance/create",
      sim_activation: "simcard/activate",
      energy: "refill/add",
      remittance_calculate: "money-transfer/calculate",
      remittance_send: "money-transfer/send",
    };
    const path = servicePaths[serviceType];
    if (!path) return json({ ok: false, message: "Operación de servicio aún no soportada." }, 422);
    if (serviceType !== "remittance_calculate") {
      const reservation = await reserveOperation({
        clientRequestId,
        module,
        providerId: clean(body.providerId),
        productId: clean(body.productId),
        actor: auth.actor,
        amount: Number(body.amount) || Number(body.quotedPrice) || 0,
      });
      if (!reservation.claimed) return json({ ok: false, message: "Esta operación ya está en proceso; no se repetirá el cargo." }, 409);
      providerCallStarted = true;
    }
    const result = await providerJson(path, { method: "POST", body: JSON.stringify(providerPayload) }, credentials);
    if (serviceType === "remittance_calculate") {
      // Cotizar una remesa es lectura; solo remittance_send debe impactar caja.
      return json({ ok: true, module, action, provider: result });
    }
    const operation = await recordOperation({ clientRequestId, module, providerId: clean(body.providerId), productId: clean(body.productId), actor: auth.actor, amount: Number(body.amount) || Number(body.quotedPrice) || 0, provider: result });
    return json({ ok: true, module, action, operation, provider: result });
  } catch (error) {
    const message = error instanceof Error ? error.message : "No se pudo procesar el servicio.";
    await (providerCallStarted ? markOperationUnknown(operationRequestId, message) : markOperationFailed(operationRequestId, message)).catch(() => undefined);
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo procesar el servicio." }, 500);
  }
});
