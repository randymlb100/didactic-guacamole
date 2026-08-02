import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/users/UserAccountsActivity.kt",
  "utf8",
);

test("supervisor console separates team, security and destructive actions", () => {
  assert.match(source, /GROUP\("Equipo"\)/);
  assert.match(source, /SUMMARY\("Resumen"\)/);
  assert.match(source, /CREDENTIALS\("Acceso"\)/);
  assert.match(source, /Supervisor seleccionado/);
  assert.match(source, /Comisión del grupo/);
  assert.match(source, /Cajeros asignados/);
  assert.match(source, /Zona de peligro/);
  assert.match(source, /Guardar equipo y comisión/);
});

test("supervisor assignment and persistence callbacks remain wired", () => {
  assert.match(source, /onCashierToggle/);
  assert.match(source, /onSaveAssignments/);
  assert.match(source, /onResetPassword/);
  assert.match(source, /onDeleteSupervisor/);
});
