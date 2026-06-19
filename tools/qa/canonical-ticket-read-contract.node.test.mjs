import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const remoteStorePath =
  "app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt";
const edgePath = "supabase/functions/get-ticket-list/index.ts";
const sharedAdminPath = "supabase/functions/_shared/lotterynet-admin.ts";

test("bounded Android ticket reads require authoritative authenticated scope", async () => {
  const source = await readFile(remoteStorePath, "utf8");

  assert.match(source, /val completeScope: Boolean/);
  assert.match(source, /val source: String\?/);
  assert.match(source, /put\("preferSnapshot", false\)/);
  assert.match(source, /put\("includeOfficialStamp", true\)/);
  assert.match(source, /isBoundedOperationalRead/);
  assert.match(source, /invokeAuthenticatedTicketList/);
});

test("Edge ticket reads canonicalize owners and label response completeness", async () => {
  const [edge, shared] = await Promise.all([
    readFile(edgePath, "utf8"),
    readFile(sharedAdminPath, "utf8"),
  ]);

  assert.match(shared, /export function validIdentityKey/);
  assert.match(shared, /export function canonicalOwnerScope/);
  assert.match(edge, /canonicalOwnerScope\(auth\.actor, ownerKey\)/);
  assert.match(edge, /source: "authoritative"/);
  assert.match(edge, /completeScope/);
  assert.match(edge, /source: "snapshot",\s*completeScope: false/s);
});

test("bounded Android path does not select snapshot based on missing auth", async () => {
  const source = await readFile(remoteStorePath, "utf8");
  const boundedStart = source.indexOf("if (isBoundedOperationalRead)");
  const boundedEnd = source.indexOf("} else {", boundedStart);

  assert.notEqual(boundedStart, -1);
  assert.notEqual(boundedEnd, -1);
  const boundedPath = source.slice(boundedStart, boundedEnd);
  assert.doesNotMatch(boundedPath, /preferSnapshot.*authToken\s*==\s*null/s);
});
