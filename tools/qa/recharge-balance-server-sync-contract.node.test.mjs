import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

function read(relativePath) {
  return readFileSync(`${root}/${relativePath}`, "utf8");
}

test("recharge balance updates now persist server-side without rewriting the assigned fund", () => {
  const usersState = read("supabase/functions/lotterynet-users-state/index.ts");
  const remoteStore = read("app/src/main/java/com/lotterynet/pro/core/users/SupabaseUsersRemoteStore.kt");
  const rechargeUi = read("app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt");

  assert.match(usersState, /update-recharge-balance/);
  assert.match(usersState, /debitRechargeBalancePayload/);
  assert.match(usersState, /recargasAssignedBalance:\s*assignedBalance/);
  assert.match(usersState, /recargasBalance:\s*balance/);
  assert.match(usersState, /canAccessOwner\(auth\.actor,\s*accountId\)/);

  assert.match(remoteStore, /fun updateRechargeBalance\(/);
  assert.match(remoteStore, /buildRechargeBalancePayload\(/);
  assert.match(remoteStore, /"action", "update-recharge-balance"/);

  assert.match(rechargeUi, /rechargeBalanceSynced = discountRechargeBalance\(session, usersRepository, usersRemoteStore, amount\)/);
  assert.match(rechargeUi, /usersRemoteStore\.updateRechargeBalance\(updatedAccount\.id, updatedAccount\.rechargesBalance\)/);
  assert.match(rechargeUi, /OperationalFeedback\.error\("La venta quedó registrada, pero el saldo de recargas no se confirmó en servidor\."/);
});
