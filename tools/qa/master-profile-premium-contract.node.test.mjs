import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const masterActivity = readFileSync(
  new URL("../../app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt", import.meta.url),
  "utf8",
);
const masterModels = readFileSync(
  new URL("../../app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardModels.kt", import.meta.url),
  "utf8",
);
const moduleActivity = readFileSync(
  new URL("../../app/src/main/java/com/lotterynet/pro/ui/master/MasterServicesGamesActivity.kt", import.meta.url),
  "utf8",
);

test("master center exposes the five premium administrative destinations", () => {
  for (const destination of ["Resumen", "Bancas", "Módulos", "Sistema", "Seguridad"]) {
    assert.match(masterModels, new RegExp(`"${destination}"`));
  }
  assert.doesNotMatch(masterActivity, /MasterDashboardSection\./);
});

test("master dashboard visibly connects the services and games administration route", () => {
  assert.match(
    masterActivity,
    /onOpenModules\s*=\s*\{\s*startSafeNativeDestination\([\s\S]*?NativeDestination\.MASTER_SERVICES_GAMES/,
  );
  assert.match(masterActivity, /MasterDestination\.MODULES/);
  assert.match(masterActivity, /"Administrar accesos"/);
});

test("system groups recargas and server without changing their callbacks", () => {
  const systemSections = masterActivity.match(
    /selectedMasterSectionId\s*==\s*MasterDestination\.SYSTEM\.id/g,
  ) ?? [];
  assert.ok(systemSections.length >= 2, "Sistema debe agrupar recargas y servidor.");
  assert.match(masterActivity, /onSaveMasterRecargaLimit/);
  assert.match(masterActivity, /onProbeServer/);
  assert.match(masterActivity, /onSyncCloud/);
});

test("bank detail separates overview cashiers funds and security", () => {
  for (const area of ["OVERVIEW", "CASHIERS", "FUNDS", "SECURITY"]) {
    assert.match(masterModels, new RegExp(`${area}\\(`));
    assert.match(masterActivity, new RegExp(`MasterBankDetailArea\\.${area}`));
  }
  assert.doesNotMatch(masterActivity, /cashiersExpanded/);
  assert.doesNotMatch(masterActivity, /expandedBankIds/);
});

test("module editor keeps admin-first cashier scoping and existing remote keys", () => {
  assert.match(moduleActivity, /selectedAdminCashiers[\s\S]*?cashiers\.filter\s*\{\s*it\.belongsToAdmin\(admin\)/);
  assert.match(moduleActivity, /servicesGamesRemoteKey\(normalized\.module\)/);
  assert.match(moduleActivity, /remoteStore\.upsertJsonValue/);
  assert.match(moduleActivity, /allowedAdminKeys/);
  assert.match(moduleActivity, /allowedCashierKeys/);
  assert.match(moduleActivity, /Lifecycle\.Event\.ON_RESUME/);
  assert.doesNotMatch(moduleActivity, /remember\s*\{\s*users\.getAdmins\(\)/);
  assert.match(
    moduleActivity,
    /allowedCashierKeys\s*=\s*settings\.allowedCashierKeys\s*-\s*cashierKeysForAdmin/,
  );
  assert.match(moduleActivity, /remotelyLoadedModules/);
  assert.match(moduleActivity, /dirtyModules/);
  assert.match(moduleActivity, /if\s*\(selectedModule in remotelyLoadedModules\)\s*return@LaunchedEffect/);
  assert.match(moduleActivity, /\.clickable\(enabled\s*=\s*enabled\)\s*\{\s*onCheckedChange\(!checked\)\s*\}/);
  assert.match(moduleActivity, /onCheckedChange\s*=\s*null/);
});

test("new Kotlin sources remain structurally balanced", () => {
  for (const [name, source] of [
    ["MasterDashboardActivity.kt", masterActivity],
    ["MasterDashboardModels.kt", masterModels],
  ]) {
    const opens = (source.match(/\{/g) ?? []).length;
    const closes = (source.match(/\}/g) ?? []).length;
    const openParens = (source.match(/\(/g) ?? []).length;
    const closeParens = (source.match(/\)/g) ?? []).length;
    assert.equal(opens, closes, `${name} tiene llaves desbalanceadas.`);
    assert.equal(openParens, closeParens, `${name} tiene paréntesis desbalanceados.`);
  }
});

test("master header uses a real accessible back action and honest status", () => {
  assert.match(masterActivity, /contentDescription\s*=\s*"Volver al inicio Master"/);
  assert.match(masterActivity, /resolveMasterStatusBadge/);
  assert.match(masterActivity, /BackHandler\(/);
  assert.doesNotMatch(masterActivity, /Text\("☰"/);
  assert.doesNotMatch(masterActivity, /Text\("⋮"/);
  assert.doesNotMatch(masterActivity, /else\s+"Sincronizado"/);
});
