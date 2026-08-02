import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const limits = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt"),
  "utf8",
);
const config = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt"),
  "utf8",
);

function check(condition, label) {
  const ok = Boolean(condition);
  console.log(`${ok ? "PASS" : "FAIL"} ${label}`);
  if (!ok) process.exitCode = 1;
}

check(
  limits.includes("val ownerId = session.adminId ?: session.userId"),
  "limites usa adminId canonico para guardar y leer",
);

check(
  limits.includes("hydrateAdminLimitsFromServer(") &&
    limits.indexOf("hydrateAdminLimitsFromServer(") < limits.indexOf("setContent {"),
  "limites hidrata servidor antes de pintar la pantalla",
);

check(
  limits.includes("resolveOperationalOwnerKeys(session)") &&
    limits.includes("cashierSalesLimitRepository.cachePayload(ownerId"),
  "limites recupera aliases viejos y cachea bajo la llave canonica",
);

check(
  config.includes("val ownerKeys = resolveOperationalOwnerKeys(session)") &&
    config.includes("firstAdminConfigRemoteValue(ownerKeys") &&
    config.includes("systemModeRemoteKey(key)"),
  "modos de juego buscan servidor por todas las llaves del admin",
);

check(
  config.includes("manualDisabledLotteriesRemoteKey(key)") &&
    config.includes("adminLotteryRepository::cacheManualDisabledLotteryConfig"),
  "bloqueos manuales de loteria tambien se recuperan en instalacion limpia",
);
