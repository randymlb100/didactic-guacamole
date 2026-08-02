import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";

const root = resolve(import.meta.dirname, "..", "..");

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

test("get-ticket-list reuses the owner snapshot payload when the updated_at stamp is unchanged", () => {
  const source = read("supabase/functions/get-ticket-list/index.ts");

  assert.match(source, /const ownerSnapshotPayloadCache/);
  assert.match(source, /async function readCachedOwnerSnapshot\(/);
  assert.match(source, /const snapshot = await readCachedOwnerSnapshot\(admin, ownerKey, \{ useCache: true \}\);/);
  assert.match(source, /const basePayload = payloadObject\(snapshot\.payload\);/);
  assert.match(source, /const snapshotTickets = payloadTickets\(basePayload\);/);
});
