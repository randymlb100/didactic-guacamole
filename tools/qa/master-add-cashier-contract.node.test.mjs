import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";

const root = resolve(import.meta.dirname, "..", "..");

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

function lower(value) {
  return String(value ?? "").trim().toLowerCase();
}

function belongsToAdmin(account, admin) {
  return [account.adminId, account.adminUser, account.banca].some((value) =>
    lower(value) && [admin.id, admin.user, admin.banca].some((key) => lower(value) === lower(key))
  );
}

function simulateAddCashiers({ admins, cashiers, adminId, count, prefix }) {
  const admin = admins.find((candidate) => lower(candidate.id) === lower(adminId));
  assert.ok(admin, "el admin objetivo debe existir");

  const existingUsers = new Set([...admins, ...cashiers].map((account) => lower(account.user)));
  const existingForAdmin = cashiers.filter((cashier) => belongsToAdmin(cashier, admin)).length;
  const added = [];
  for (let offset = 0; offset < count; offset += 1) {
    let index = existingForAdmin + offset + 1;
    let user = `${prefix}${String(index).padStart(2, "0")}`;
    while (existingUsers.has(lower(user))) {
      index += 1;
      user = `${prefix}${String(index).padStart(2, "0")}`;
    }
    existingUsers.add(lower(user));
    added.push({
      id: `CAJ-QA-${index}`,
      user,
      role: "cashier",
      adminId: admin.id,
      adminUser: admin.user,
      banca: admin.banca,
    });
  }
  return { admins, cashiers: [...cashiers, ...added], added };
}

test("Master agrega cajeros a nicola01 sin reemplazar los existentes", () => {
  const admins = [{ id: "ADM-163C38", user: "nicola01", banca: "Banca yuniel" }];
  const cashiers = [
    { id: "CAJ-1", user: "yuniel01", adminId: "ADM-163C38", adminUser: "nicola01", banca: "Banca yuniel" },
    { id: "CAJ-2", user: "yuniel02", adminId: "ADM-163C38", adminUser: "nicola01", banca: "Banca yuniel" },
  ];

  const result = simulateAddCashiers({
    admins,
    cashiers,
    adminId: "ADM-163C38",
    count: 2,
    prefix: "yuniel",
  });

  assert.equal(result.admins.length, 1);
  assert.equal(result.cashiers.length, 4);
  assert.equal(result.added.length, 2);
  assert.deepEqual(result.cashiers.slice(0, 2), cashiers);
  assert.ok(result.added.every((cashier) =>
    cashier.adminId === "ADM-163C38" &&
    cashier.adminUser === "nicola01" &&
    cashier.banca === "Banca yuniel"
  ));
});

test("el contrato Android conserva la lista y permite clave individual", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/master/MasterUserManager.kt");
  const dashboard = read("app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt");

  assert.match(source, /val cashiers = usersRepository\.getCashiers\(\)\.toMutableList\(\)/);
  assert.match(source, /usersRepository\.saveUsers\(admins, cashiers \+ newCashiers\)/);
  assert.match(source, /adminId = admin\.id/);
  assert.match(source, /adminUser = admin\.user/);
  assert.match(source, /banca = admin\.banca/);
  assert.match(source, /fun changePassword\(idOrUser: String, newPassword: String\)/);
  assert.match(source, /matches\(it, idOrUser\)/);
  assert.match(dashboard, /onAddCashiers = \{ admin, count, prefix ->/);
  assert.match(dashboard, /userManager\.addCashiers\(admin\.id, count, prefix\)/);
  assert.match(dashboard, /onChangePassword = \{ idOrUser, password ->/);
  assert.match(dashboard, /userPasswordBackendClient\.changePassword\(/);
  assert.match(dashboard, /action = "AGREGAR_CAJEROS_MASTER"/);
  assert.match(dashboard, /bearerTokenProvider = \{ sessionTokenProvider\.freshAccessToken\(\) \}/);
});

test("las acciones del Master no bloquean Compose y refrescan el estado remoto al entrar", () => {
  const dashboard = read("app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt");

  assert.match(dashboard, /rememberCoroutineScope\(\)/);
  assert.match(dashboard, /withContext\(Dispatchers\.IO\)\s*\{\s*onAddCashiers/s);
  assert.match(dashboard, /withContext\(Dispatchers\.IO\)\s*\{\s*onChangePassword/s);
  assert.match(dashboard, /var masterActionBusy by remember/);
  assert.match(dashboard, /if \(!autoRemoteHydrated && !remoteRefreshBusy\)/);
  assert.match(dashboard, /pendingAddCashiers = null/);
});
