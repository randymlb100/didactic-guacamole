import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync("supabase/functions/lotterynet-users-state/index.ts", "utf8");

test("master fund update is a dedicated authenticated server-first action", () => {
  assert.match(source, /update-recharge-fund/);
  assert.match(source, /authenticatedActor\(req,\s*\["master"\]\)/);
  assert.match(source, /requestedAmount/);
  assert.match(source, /persistedAmount/);
  assert.match(source, /confirmed/);
  assert.match(source, /updatedAt/);
});

test("master fund update writes and reads back the authoritative users state", () => {
  assert.match(source, /\.from\("lotterynet_users_state"\)/);
  assert.match(source, /\.upsert\(/);
  assert.match(source, /\.select\("payload,\s*updated_at"\)/);
  assert.match(source, /recargasAssignedBalance/);
  assert.match(source, /recargasBalance/);
});
