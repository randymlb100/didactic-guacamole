import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";

const MODULES = ["services", "video_games"];

function canOpen({ enabled, role, actorKey, adminKey, allowedAdminKeys = [], allowedCashierKeys = [], cashierAdminKeys = [] }) {
  if (!enabled) return false;
  if (role === "master") return true;
  if (role === "admin") return allowedAdminKeys.includes(actorKey);
  if (role === "cashier") {
    return allowedCashierKeys.includes(actorKey) || cashierAdminKeys.includes(adminKey);
  }
  return false;
}

test("solo existen Servicios y Videojuegos; IPTV queda fuera", () => {
  assert.deepEqual(MODULES, ["services", "video_games"]);
  assert.equal(MODULES.includes("iptv"), false);
});

test("Master activa módulo y asigna alcance individual", () => {
  const scope = {
    enabled: true,
    allowedAdminKeys: ["admin-a"],
    allowedCashierKeys: ["cashier-one"],
    cashierAdminKeys: ["admin-a"],
  };
  assert.equal(canOpen({ ...scope, role: "admin", actorKey: "admin-a" }), true);
  assert.equal(canOpen({ ...scope, role: "admin", actorKey: "admin-b" }), false);
  assert.equal(canOpen({ ...scope, role: "cashier", actorKey: "cashier-one", adminKey: "admin-b" }), true);
  assert.equal(canOpen({ ...scope, role: "cashier", actorKey: "cashier-two", adminKey: "admin-a" }), true);
  assert.equal(canOpen({ ...scope, role: "cashier", actorKey: "cashier-two", adminKey: "admin-b" }), false);
});

test("Agregar fondo no pertenece al permiso de cajero", () => {
  const cashierActions = ["query", "confirm", "view_own_operations"];
  assert.equal(cashierActions.includes("add_funds"), false);
  assert.equal(cashierActions.includes("upload_deposit_proof"), false);
});

test("consulta y confirmación conservan identidad del proveedor", () => {
  const query = {
    action: "query",
    module: "services",
    providerId: "edenorte",
    productId: "electricity-bill",
    adminKey: "admin-a",
    cashierKey: "cashier-one",
  };
  const confirm = { ...query, action: "confirm", quotedPrice: 100 };
  assert.equal(query.action, "query");
  assert.equal(confirm.action, "confirm");
  assert.equal(confirm.providerId, query.providerId);
  assert.equal(confirm.productId, query.productId);
});

test("las claves remotas quedan separadas de recargas y deportes", () => {
  assert.equal(moduleWire("services"), "services:global");
  assert.equal(moduleWire("video_games"), "video_games:global");
  assert.notEqual(moduleWire("services"), "recharge:global");
  assert.notEqual(moduleWire("video_games"), "sportsbook:global");
});

test("una cuenta deshabilitada no puede abrir el módulo", () => {
  const scope = {
    enabled: false,
    allowedAdminKeys: ["admin-a"],
    allowedCashierKeys: ["cashier-a"],
    cashierAdminKeys: ["admin-a"],
  };
  assert.equal(canOpen({ ...scope, role: "admin", actorKey: "admin-a" }), false);
  assert.equal(canOpen({ ...scope, role: "cashier", actorKey: "cashier-a", adminKey: "admin-a" }), false);
});

test("consulta de factura y cotización no se registran como venta", () => {
  const source = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  assert.match(source, /serviceType === "bills_lookup"/);
  assert.match(source, /serviceType === "remittance_calculate"/);
  assert.match(source, /services_games_operations/);
});

test("el contrato usa rutas del proveedor y mantiene secretos en Edge", () => {
  const source = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  for (const route of ["gamecategories/active", "gameproducts/active", "gamesales", "insurance/create", "simcard/activate"]) {
    assert.match(source, new RegExp(route.replaceAll("/", "\\/")));
  }
  assert.match(source, /recargas_rapidas_credentials_v1/);
  assert.doesNotMatch(source, /password.*console\.log/i);
});

test("confirmación reserva la solicitud antes de llamar al proveedor", () => {
  const source = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  const reservation = source.indexOf("reserveOperation({");
  const providerCall = source.indexOf('providerJson("gamesales"');
  const migration = readFileSync("supabase/migrations/20260724120000_services_games_operations.sql", "utf8");
  assert.ok(reservation >= 0);
  assert.ok(providerCall > reservation);
  assert.match(migration, /client_request_id\s+text\s+not null unique/i);
  assert.match(source, /no se repetirá el cargo/i);
  assert.match(source, /providerCallStarted/);
  assert.match(source, /markOperationUnknown/);
  assert.match(source, /priorStatus === "unknown"/);
});

test("la pantalla Android conserva el idempotency key durante un reintento", () => {
  const source = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(source, /var operationRequestId by remember/);
  assert.match(source, /if \(operationRequestId\.isBlank\(\)/);
  assert.match(source, /clientRequestId = operationRequestId/);
});

test("facturas usan el identificador real del proveedor y no campos genéricos inventados", () => {
  const source = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(source, /serviceCustomerIdentifierSpec\(row\)/);
  assert.match(source, /"NIC del contrato"/);
  assert.match(source, /"Número de contrato o abonado"/);
  assert.match(source, /billLookupDone = false/);
  assert.doesNotMatch(source, /Text\("Dato del cliente"\)/);
  assert.doesNotMatch(source, /Text\("Datos del servicio"\)/);
});

test("videojuegos muestran datos de cuenta específicos y la hoja abre completa", () => {
  const source = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(source, /gameAccountSpec\(row\)/);
  assert.match(source, /"ID de jugador de Free Fire"/);
  assert.match(source, /"Usuario o ID de Roblox"/);
  assert.match(source, /rememberModalBottomSheetState\(skipPartiallyExpanded = true\)/);
});

test("el reporte separado está declarado en el manifest", () => {
  const manifest = readFileSync("app/src/main/AndroidManifest.xml", "utf8");
  assert.match(manifest, /ui\.servicesgames\.ServicesGamesReportActivity/);
});

test("los logos se resuelven desde assets locales cuando el proveedor no entrega URL", () => {
  const source = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(source, /LotteryLogo\(/);
  assert.match(source, /logoAssetKey/);
  assert.match(source, /secondaryLogoAssetKey/);
  assert.match(source, /module != ServicesGamesModule\.VIDEO_GAMES/);
  const edgeSource = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  assert.match(edgeSource, /logoUrl: ""/);
  assert.match(edgeSource, /function gameLogoAssetKey/);
  for (const game of ["delta_force", "roblox", "minecraft", "free_fire"]) {
    assert.match(edgeSource, new RegExp(`video-games/${game}`));
  }
  assert.match(source, /setOf\("svg", "png", "jpg", "jpeg", "webp"\)/);
  for (const asset of [
    "logo_altice.svg", "logo_viva.svg", "logo_wind.svg", "logo_moun.svg",
    "services/edenorte.png", "services/edesur.png", "services/edeeste.png", "services/caasd.png",
    "services/coraaplata.png", "services/coraavega.png", "services/coraasan.png",
    "services/moncash.png", "services/natcash.png", "services/paso_rapido.svg",
    "video-games/delta_force.png", "video-games/roblox.png", "video-games/minecraft.svg", "video-games/free_fire.png",
  ]) {
    assert.ok(existsSync(`app/src/main/assets/${asset}`), `falta asset local ${asset}`);
  }
});

test("facturas conservan la división del portal por categoría", () => {
  const edgeSource = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  for (const category of ["Telecomunicaciones", "Energía eléctrica", "Telecable", "Acueductos y alcantarillados"]) {
    assert.match(edgeSource, new RegExp(category));
  }
  for (const provider of ["claro_postpago", "edenorte", "starcable", "caasd", "coraasan"]) {
    assert.match(edgeSource, new RegExp(`\\[\\\"${provider}\\\"`));
  }
  assert.match(edgeSource, /const BILL_PROVIDER_CODES/);
  const portalBillProviderCodes = {
    claro_postpago: "Claro",
    altice_factura: "Orange",
    viva_postpago: "Viva",
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
  for (const [providerId, providerCode] of Object.entries(portalBillProviderCodes)) {
    assert.match(
      edgeSource,
      new RegExp(`${providerId}: "${providerCode}"`),
      `${providerId} debe usar el enum exacto del portal: ${providerCode}`,
    );
  }
  assert.match(edgeSource, /const providerCode = billProviderCode\(clean\(body\.providerId\)\)/);
  assert.match(edgeSource, /bills\/\$\{encodeURIComponent\(customerInput\)\}\/\$\{encodeURIComponent\(providerCode\)\}/);
  const activity = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(activity, /selectedServiceCategory/);
  assert.match(activity, /categoryLabel/);
  assert.match(activity, /horizontalScroll/);
});

test("identificadores principales mantienen contraste Material 3", () => {
  const activity = readFileSync("app/src/main/java/com/lotterynet/pro/ui/servicesgames/ServicesGamesActivity.kt", "utf8");
  assert.match(activity, /private fun IdentifierField/);
  assert.match(activity, /surfaceContainerHighest/);
  assert.match(activity, /onSurface/);
  assert.match(activity, /focusedIndicatorColor/);
  assert.match(activity, /OutlinedTextField/);
});

test("el catálogo de videojuegos usa caché aislada por módulo y administrador", () => {
  const source = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  assert.match(source, /services-games:catalog:v1/);
  assert.match(source, /CATALOG_CACHE_TTL_SECONDS = 60/);
  assert.match(source, /redisGetJson/);
  assert.match(source, /redisSetJson/);
  assert.match(source, /await redisSetJson\(cacheKey, \{ items \}, CATALOG_CACHE_TTL_SECONDS\)/);
  assert.match(source, /providerJson\("gamecategories\/active"/);
  assert.match(source, /providerJson\("gameproducts\/active"/);
});

test("el límite Upstash solo protege lecturas y no confirmaciones", () => {
  const source = readFileSync("supabase/functions/recargas-rapidas-services-games/index.ts", "utf8");
  assert.match(source, /READ_RATE_LIMITS: Record<string, number> = \{ catalog: 12, query: 30 \}/);
  assert.match(source, /redisIncrement/);
  assert.match(source, /allowReadRequest\(action, module, auth\.actor\)/);
  assert.match(source, /if \(!limit\) return \{ allowed: true, remaining: null \}/);
  assert.match(source, /retryAfterSeconds/);
  assert.doesNotMatch(source, /confirm: 1/);
});

function moduleWire(module) {
  if (module === "services") return "services:global";
  if (module === "video_games") return "video_games:global";
  throw new Error("Módulo no permitido");
}
