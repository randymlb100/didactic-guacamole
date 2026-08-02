import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");

test("group password change updates every cashier Auth account before payload confirmation", () => {
  const server = read("supabase/functions/change-user-password/index.ts");
  assert.match(server, /change-cashier-group-password/);
  assert.match(server, /auth\.actor\.role !== "master"/);
  assert.match(server, /updateAuthIfPossible\(cashier, newPassword\)/);
  assert.match(server, /updatedCount/);
  assert.match(server, /authUpdatedCount/);
  assert.match(server, /payloadConfirmed: true/);
});

test("Master app calls the group backend contract before local credential mutation", () => {
  const app = read("app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt");
  const start = app.indexOf("onChangeCashierGroupPassword =");
  const end = app.indexOf("onOpenAudit", start);
  const fragment = app.slice(start, end === -1 ? start + 2600 : end);
  assert.match(fragment, /changeCashierGroupPassword/);
  assert.match(fragment, /payloadConfirmed/);
  assert.ok(fragment.indexOf("payloadConfirmed") < fragment.indexOf("userManager.changeCashierGroupPassword"));
});
