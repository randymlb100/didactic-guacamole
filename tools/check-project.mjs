import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const rootDir = process.cwd();
const verbose = process.argv.includes("--verbose");
const failures = [];
const warnings = [];

function rel(filePath) {
  return path.relative(rootDir, filePath) || path.basename(filePath);
}

function logOk(message) {
  console.log(`OK  ${message}`);
}

function logWarn(message) {
  warnings.push(message);
  console.warn(`WARN ${message}`);
}

function logFail(message) {
  failures.push(message);
  console.error(`FAIL ${message}`);
}

function readText(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function checkJson(filePath) {
  try {
    JSON.parse(readText(filePath));
    logOk(`JSON valido: ${rel(filePath)}`);
  } catch (error) {
    logFail(`JSON invalido en ${rel(filePath)}: ${error.message}`);
  }
}

function compileJavaScript(code, label) {
  try {
    new vm.Script(code, { filename: label });
    logOk(`JS valido: ${label}`);
    return true;
  } catch (error) {
    logFail(`JS invalido en ${label}: ${error.message}`);
    return false;
  }
}

function extractInlineScripts(html) {
  const matches = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
  return matches
    .map((match, index) => ({
      index: index + 1,
      code: match[1] || "",
    }))
    .filter((entry) => entry.code.trim());
}

function listFiles(dirPath, predicate) {
  if (!fs.existsSync(dirPath)) return [];
  return fs
    .readdirSync(dirPath, { withFileTypes: true })
    .filter((entry) => entry.isFile() && predicate(entry.name))
    .map((entry) => path.join(dirPath, entry.name));
}

function expectContains(source, snippet, okMessage, failMessage) {
  if (source.includes(snippet)) {
    logOk(okMessage);
  } else {
    logFail(failMessage);
  }
}

function expectNotContains(source, snippet, okMessage, failMessage) {
  if (!source.includes(snippet)) {
    logOk(okMessage);
  } else {
    logFail(failMessage);
  }
}

function between(source, startNeedle, endNeedle) {
  const start = source.indexOf(startNeedle);
  if (start === -1) return "";
  const end = source.indexOf(endNeedle, start + startNeedle.length);
  if (end === -1) return source.slice(start);
  return source.slice(start, end);
}

function checkHtmlAsset(filePath) {
  const html = readText(filePath);
  const inlineScripts = extractInlineScripts(html);
  if (!inlineScripts.length) {
    logWarn(`HTML sin <script> embebidos: ${rel(filePath)}`);
  }
  inlineScripts.forEach((entry) => {
    compileJavaScript(entry.code, `${rel(filePath)} <script #${entry.index}>`);
  });
  if (!/<div id="app">/i.test(html)) {
    logWarn(`No se encontro #app en ${rel(filePath)}`);
  }
  if (!/window\._MAIN_SCRIPT_OK/i.test(html)) {
    logWarn(`No se encontro marcador MAIN_SCRIPT_OK en ${rel(filePath)}`);
  }
  logOk(`HTML revisado: ${rel(filePath)} (${inlineScripts.length} bloques script)`);
  return html;
}

function checkStandaloneJs(filePath) {
  const source = readText(filePath);
  compileJavaScript(source, rel(filePath));
}

function checkManifest(filePath) {
  const manifest = readText(filePath);
  expectContains(
    manifest,
    'android:name="android.permission.INTERNET"',
    "Manifest incluye INTERNET",
    "Manifest no incluye INTERNET"
  );
  expectContains(
    manifest,
    'android.intent.action.MAIN',
    "Manifest define activity launcher",
    "Manifest no define activity launcher"
  );
  expectContains(
    manifest,
    'android.intent.category.LAUNCHER',
    "Manifest define categoria LAUNCHER",
    "Manifest no define categoria LAUNCHER"
  );
  expectContains(
    manifest,
    'android:hardwareAccelerated="true"',
    "Manifest mantiene hardwareAccelerated",
    "Manifest perdio hardwareAccelerated"
  );
  expectContains(
    manifest,
    'android:windowSoftInputMode="adjustResize"',
    "Activities mantienen adjustResize",
    "No se encontro adjustResize en actividades"
  );
}

function checkBusinessRules(indexHtml) {
  const financeBreakdown = between(indexHtml, "function getFinanceBreakdown(", "function getCurrentAdminRecord(");
  const adminFinance = between(indexHtml, "function getAdminFinanceData(", "function buildAdminFinancePanel(");
  const turnoFinance = between(indexHtml, "function getTurnoFinance(", "function updTurno(");
  const syncR = between(indexHtml, "function syncR(", "function rRes(");
  const sbUpsert = between(indexHtml, "async function sbUpsert(", "function sbFlushKeys(");
  const posEnterMonto = between(indexHtml, "function posEnterMonto(", "function getCanvasScale(");
  const posRender = between(indexHtml, "function posRender(", "function vK(");
  const thermalPreview = between(indexHtml, "function canRenderThermalPreview(", "function loadImpresoraScreen(");
  const liteGate = between(indexHtml, "function shouldAutoEnablePerfLite()", "function setForcedLiteMode(");
  const aclBlock = between(indexHtml, "function canAccessSection(", "function getSafeHomeScreen(");
  const navBlock = between(indexHtml, "function nav(", "window._appResume=function()");

  expectNotContains(
    financeBreakdown,
    "isPerfLiteMode()",
    "Perf-lite no toca finanza base",
    "Perf-lite entro en getFinanceBreakdown"
  );
  expectNotContains(
    adminFinance,
    "isPerfLiteMode()",
    "Perf-lite no toca finanza admin",
    "Perf-lite entro en getAdminFinanceData"
  );
  expectNotContains(
    turnoFinance,
    "isPerfLiteMode()",
    "Perf-lite no toca turno",
    "Perf-lite entro en getTurnoFinance"
  );
  expectNotContains(
    syncR,
    "isPerfLiteMode()",
    "Perf-lite no toca sync de resultados",
    "Perf-lite entro en syncR"
  );
  expectNotContains(
    sbUpsert,
    "isPerfLiteMode()",
    "Perf-lite no toca subida a Supabase",
    "Perf-lite entro en sbUpsert"
  );
  expectNotContains(
    posEnterMonto,
    "isPerfLiteMode()",
    "Perf-lite no toca confirmacion de venta",
    "Perf-lite entro en posEnterMonto"
  );
  expectContains(
    posEnterMonto,
    "getActorSoldTodayTotal()",
    "Venta usa cache diario para tope de cajero",
    "Venta no usa cache diario de cajero"
  );
  expectContains(
    indexHtml,
    "function getExposureStats(",
    "Existe cache de exposicion",
    "Falta getExposureStats"
  );
  expectContains(
    indexHtml,
    "function getSaleRecentTickets(",
    "Existe cache de tickets recientes",
    "Falta getSaleRecentTickets"
  );
  expectContains(
    posRender,
    "setCachedNodeStyle(",
    "POS render usa cache de estilos",
    "POS render no usa cache de estilos"
  );
  expectNotContains(
    posRender,
    "innerHTML=",
    "POS render ya no hace innerHTML directo",
    "POS render aun hace innerHTML directo"
  );
  expectNotContains(
    posRender,
    "textContent=",
    "POS render ya no hace textContent directo",
    "POS render aun hace textContent directo"
  );
  expectContains(
    thermalPreview,
    "return myRole!=='cashier';",
    "Preview termico bloqueado para cajero",
    "Preview termico no bloquea cajero"
  );
  expectContains(
    indexHtml,
    "La vista previa térmica está desactivada para cajero.",
    "Mensaje de no-preview para cajero existe",
    "Falta mensaje de no-preview para cajero"
  );
  expectContains(
    liteGate,
    "if(myRole&&myRole!=='cashier')return false;",
    "Perf-lite automatico solo aplica a cajero",
    "Perf-lite automatico no esta limitado a cajero"
  );
  expectContains(
    aclBlock,
    "master:['master','crear-admin','auditoria']",
    "ACL de master es administrativa",
    "ACL de master permite secciones no esperadas"
  );
  expectContains(
    navBlock,
    "if(s!=='login'&&myRole&& !canAccessSection(myRole,s))",
    "Nav bloquea pantallas fuera del rol",
    "Nav no bloquea pantallas fuera del rol"
  );
}

function run() {
  const htmlFile = path.join(rootDir, "app", "src", "main", "assets", "index.html");
  const manifestJsonFile = path.join(rootDir, "app", "src", "main", "assets", "manifest.json");
  const androidManifestFile = path.join(rootDir, "app", "src", "main", "AndroidManifest.xml");
  const assetsDir = path.join(rootDir, "app", "src", "main", "assets");

  let htmlSource = "";
  if (fs.existsSync(htmlFile)) {
    htmlSource = checkHtmlAsset(htmlFile);
  } else {
    logWarn(`No existe ${rel(htmlFile)}; app nativa Kotlin sin WebView principal`);
  }

  if (fs.existsSync(manifestJsonFile)) {
    checkJson(manifestJsonFile);
  } else {
    logWarn(`No existe ${rel(manifestJsonFile)}`);
  }

  if (fs.existsSync(androidManifestFile)) {
    checkManifest(androidManifestFile);
  } else {
    logWarn(`No existe ${rel(androidManifestFile)}`);
  }

  const assetJsFiles = listFiles(assetsDir, (name) => name.endsWith(".js"));
  assetJsFiles.forEach(checkStandaloneJs);
  if (!assetJsFiles.length) {
    logWarn("No se encontraron JS en app/src/main/assets; app nativa Kotlin");
  }

  const rootJsonFiles = listFiles(rootDir, (name) => name.endsWith(".json"));
  rootJsonFiles
    .filter((filePath) => !filePath.includes(`${path.sep}app${path.sep}build${path.sep}`))
    .forEach(checkJson);

  if (htmlSource) {
    checkBusinessRules(htmlSource);
  }

  if (verbose) {
    const tempJs = listFiles(rootDir, (name) => name.endsWith(".js") && !name.endsWith(".min.js"));
    tempJs
      .filter((filePath) => path.basename(filePath) !== "check-project.mjs")
      .forEach((filePath) => logWarn(`JS adicional fuera de assets: ${rel(filePath)}`));
  }

  console.log("");
  console.log(`Resumen: ${failures.length} fallo(s), ${warnings.length} aviso(s)`);
  if (failures.length) {
    process.exitCode = 1;
  }
}

run();
