import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const salesActivity = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt"),
  "utf8",
);
const exposureEngine = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/core/sales/SaleExposureEngine.kt"),
  "utf8",
);
const sharedLimitMigration = readFileSync(
  join(root, "supabase/migrations/20260604184500_cashier_limits_alias_owner_scope.sql"),
  "utf8",
);

assert.match(
  salesActivity,
  /internal fun usesConfiguredSaleLimits\(role: UserRole\): Boolean[\s\S]*role == UserRole\.CASHIER \|\| role == UserRole\.ADMIN/,
  "Venta must treat admin self limits and cashier limits as configured sale limits.",
);

assert.match(
  salesActivity,
  /UserRole\.ADMIN -> repository\.getAdminSelfLimits\(ownerId\) \?: noConfiguredSaleLimits\(\)/,
  "Admin badge must read adminSelf limits only; admin must not inherit cashier defaults visually.",
);

assert.match(
  salesActivity,
  /resolveSaleExposureLimitBucket\(play\.playType, play\.normalizedNumber, selectedLottery\?\.id\)/,
  "Limit badge must scope visible remaining amount to the selected lottery, matching server validation.",
);

assert.doesNotMatch(
  salesActivity,
  /resolveSaleLimitBadgeMain[\s\S]{0,260}role != UserRole\.CASHIER\) return "Sin tope"/,
  "Badge helpers must not force all admins to Sin tope.",
);

assert.match(
  exposureEngine,
  /saleLimitExposureCashierPoolOnly[\s\S]*role == UserRole\.CASHIER/,
  "Runtime cashier limit check must use the shared cashier pool for cashiers.",
);

assert.match(
  exposureEngine,
  /internal fun resolveExposureOwnerKeys\(session: ActiveSession\?\): Set<String>[\s\S]*session\.adminId\?\.takeIf[\s\S]*session\.adminUser\?\.takeIf[\s\S]*session\.userId\.takeIf[\s\S]*session\.username\.takeIf/,
  "Client exposure must keep all admin identity keys so id and alias tickets share one limit pool.",
);

assert.match(
  exposureEngine,
  /matchesExposureOwner\(ticket, resolvedOwnerKeys\)/,
  "Global exposure must match owner using the full identity-key set, not only one ownerKey.",
);

assert.match(
  sharedLimitMigration,
  /ti\.lottery_id = new\.lottery_id[\s\S]*ti\.play_type = new\.play_type[\s\S]*public\.ln_sale_limit_bucket/,
  "Server limit trigger must scope the shared cashier pool by lottery, game type, and number bucket.",
);

assert.match(
  sharedLimitMigration,
  /lower\(coalesce\(t\.admin_key, ''\)\) = any\(v_admin_lkeys\)/,
  "Server limit trigger must share the cashier limit across all cashiers under the same admin.",
);

console.log("Admin badge and cashier global limit contract passed");
