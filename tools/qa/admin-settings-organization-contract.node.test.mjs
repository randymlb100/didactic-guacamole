import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt",
  "utf8",
);

test("admin settings has searchable categorized navigation", () => {
  assert.match(source, /Buscar ajustes/);
  assert.match(source, /AdminConfigArea\.values\(\)/);
  assert.match(source, /onSearchQueryChange/);
  assert.match(source, /matchingAreas\.groupBy \{ adminConfigAreaGroup\(it\.id\) \}/);
  assert.match(source, /onClick = \{ onAreaSelected\(area\.id\) \}/);
  assert.match(source, /Modo de venta/);
  assert.match(source, /Loterías y jugadas/);
  assert.match(source, /Caja/);
  assert.match(source, /SYSTEM\("system", "Servidor y sincronización"/);
  assert.match(source, /Estado del sistema/);
  assert.match(source, /AdminConfigAreaContextCard\(resolveAdminConfigArea\(selectedConfigAreaId!!\)\)/);
  assert.match(source, /Los bloqueos afectan la disponibilidad de venta/);
  assert.match(source, /Logo, impresora y tickets pertenecen/);
});

test("settings search preserves existing save and sync flows", () => {
  assert.match(source, /onSaveSystemModeConfig/);
  assert.match(source, /onSyncSystemModeConfig/);
  assert.match(source, /onSyncBranding/);
  assert.match(source, /onSetLotteryDisabled/);
});

test("settings navigation and blocking controls stay compact and explicit", () => {
  assert.match(source, /BackHandler\(enabled = selectedConfigAreaId != null\)/);
  assert.match(source, /matchingAreas\.groupBy \{ adminConfigAreaGroup\(it\.id\) \}/);
  assert.match(source, /label = "Solo bloquear la lotería"/);
  assert.match(source, /label = "Bloquear y anular tickets"/);
  assert.match(source, /label = "Bloquear y borrar tickets"/);
  assert.match(source, /label = "Mover tickets al día siguiente"/);
  assert.doesNotMatch(source, /AlertDialog\(/);
});

test("admin settings keeps semantic button hierarchy", () => {
  assert.match(source, /label = "Bloquear jugada"[\s\S]{0,500}tone = ActionTone\.Danger/);
  assert.match(source, /label = "Confirmar bloqueo"[\s\S]{0,500}tone = ActionTone\.Danger/);
  assert.match(source, /label = "Habilitar disponibles hoy"[\s\S]{0,400}tone = ActionTone\.Success/);
  assert.match(source, /label = adminSystemModeSaveButtonLabel\(\)[\s\S]{0,400}tone = ActionTone\.Primary/);
  assert.match(source, /enabled = branding\.logoUri\.isNotBlank\(\)/);
});
