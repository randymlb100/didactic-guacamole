import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  canonicalOwnerScope,
  validIdentityKey,
  type AuthenticatedActor,
} from "../_shared/lotterynet-admin.ts";

Deno.test("invalid owner placeholders are rejected", () => {
  assertEquals(validIdentityKey("null"), "");
  assertEquals(validIdentityKey(" undefined "), "");
  assertEquals(validIdentityKey(" ADM-163C38 "), "ADM-163C38");
});

Deno.test("admin aliases resolve to the canonical legacy admin key", () => {
  const admin = {
    id: "5e9553d2-72b2-484e-8b85-095fbce6f2a4",
    user: "nicola01",
    username: "nicola01",
    role: "admin",
    legacy_key: "ADM-163C38",
  };
  const actor: AuthenticatedActor = {
    userId: String(admin.id),
    role: "admin",
    metadata: {},
    account: admin,
    accounts: [admin],
    identityKeys: [String(admin.id), String(admin.user), String(admin.legacy_key)],
    ownerKeys: [String(admin.user), String(admin.legacy_key)],
  };

  const scope = canonicalOwnerScope(actor, "nicola01");

  assertEquals(scope.canonicalOwnerKey, "ADM-163C38");
  assert(scope.ownerKeys.includes("nicola01"));
  assert(scope.ownerKeys.includes("ADM-163C38"));
});

Deno.test("cashier aliases retain cashier identity but resolve to their administrator", () => {
  const admin = {
    id: "ADM-163C38",
    user: "nicola01",
    role: "admin",
    legacy_key: "ADM-163C38",
  };
  const cashier = {
    id: "CAJ-6C33FF",
    user: "bancay12",
    role: "cajero",
    adminId: "ADM-163C38",
    adminUser: "nicola01",
  };
  const actor: AuthenticatedActor = {
    userId: "cashier-auth-user",
    role: "cajero",
    metadata: {},
    account: cashier,
    accounts: [admin, cashier],
    identityKeys: ["cashier-auth-user", "CAJ-6C33FF", "bancay12"],
    ownerKeys: ["ADM-163C38", "nicola01", "CAJ-6C33FF"],
  };

  const scope = canonicalOwnerScope(actor, "bancay12");

  assertEquals(scope.canonicalOwnerKey, "ADM-163C38");
  assert(scope.cashierKeys.includes("CAJ-6C33FF"));
  assert(scope.cashierKeys.includes("bancay12"));
});
