import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const migration = readFileSync(
  join(root, "supabase/migrations/20260604184500_cashier_limits_alias_owner_scope.sql"),
  "utf8",
);
const adminLimitsActivity = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt"),
  "utf8",
);

function check(condition, label) {
  const ok = Boolean(condition);
  console.log(`${ok ? "PASS" : "FAIL"} ${label}`);
  if (!ok) process.exitCode = 1;
}

check(
  adminLimitsActivity.includes("val ownerId = session.adminId ?: session.userId"),
  "pantalla de limites guarda por adminId canonico cuando existe",
);

check(
  migration.includes("create or replace function public.ln_limit_self_keys"),
  "servidor resuelve aliases propios del actor",
);

check(
  migration.includes("with ordinality") && migration.includes("array_agg(k order by first_seen)"),
  "aliases de limites priorizan la llave exacta antes de UUID/alias viejos",
);

check(
  migration.includes("public.ln_cashier_limit_payload_for_admin(p_admin_key)"),
  "configuracion de limites se busca por aliases del admin",
);

check(
  migration.includes("public.ln_ticket_sale_is_admin_actor(p_admin_key, p_cashier_key)"),
  "admin self se detecta por identidad canonica y no solo por texto exacto",
);

check(
  migration.includes("v_is_admin_sale") &&
    migration.includes("lower(coalesce(t.cashier_key, '')) <> all(v_admin_lkeys)"),
  "ventas de admin quedan fuera del limite global compartido de cajeros",
);

check(
  migration.includes("lower(coalesce(t.admin_key, '')) = any(v_admin_lkeys)") &&
    migration.includes("ti.lottery_id = new.lottery_id") &&
    migration.includes("ti.play_type = new.play_type") &&
    migration.includes("public.ln_sale_limit_bucket"),
  "limite global de cajeros se comparte por admin, loteria, juego y numero",
);
