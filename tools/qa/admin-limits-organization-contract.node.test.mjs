import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const limits = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt"),
  "utf8",
);
const contracts = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsContracts.kt"),
  "utf8",
);

assert.match(
  limits,
  /private fun AdminLimitsOverview\(/,
  "limits center should start from one business-scope overview",
);

assert.match(
  limits,
  /private fun AdminLimitsDetailNavigation\([\s\S]*Volver al resumen/,
  "each limits editor should provide a clear path back to the overview",
);

assert.doesNotMatch(
  limits,
  /CurrentScopeDropdownCard\(/,
  "limits should not stack a global dropdown over the active editor",
);

assert.match(
  limits,
  /Separa topes por jugada, por cajero y por caja para que no se mezclen\./,
  "limits hub should explain the shared, cashier, self, and number scopes in one visible legend",
);

assert.match(
  limits,
  /adminLimitsSectionScope[\s\S]*"GLOBAL"[\s\S]*"POR USUARIO"[\s\S]*"SOLO ADMIN"[\s\S]*"OPERACIÓN"[\s\S]*"INTERFAZ"/,
  "limits center should keep every business scope visibly distinct",
);

assert.match(
  limits,
  /Mis límites de venta[\s\S]*No se mezcla con los cajeros/,
  "admin self limits should be described as isolated from cashier defaults",
);

assert.match(
  limits,
  /Límites por jugada[\s\S]*meta = "Base"/,
  "sales limits should keep the play-type section separated and compact",
);

assert.match(
  limits,
  /Caja y recargas[\s\S]*No afecta el ranking de números/,
  "cash section should be framed separately from number ranking",
);

assert.match(
  limits,
  /Sistema POS[\s\S]*no los límites de venta/,
  "POS section should be clearly visual-only",
);

assert.match(
  contracts,
  /adminLimitSections\(\): List<AdminLimitSection> = listOf\([\s\S]*"Mis límites de venta"[\s\S]*"Límite de venta de cajeros"[\s\S]*"Caja"[\s\S]*"Sistema POS"/,
  "section registry should preserve the four admin areas",
);

console.log("Admin limits organization contract passed");
