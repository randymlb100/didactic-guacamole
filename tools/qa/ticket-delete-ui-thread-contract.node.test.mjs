import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt",
  "utf8",
);

const deleteBlock = source.slice(
  source.indexOf("onDelete = { record, returnLimit ->"),
  source.indexOf("canVoid = canVoidTicket("),
);

test("official ticket delete validates with the server off the UI thread", () => {
  assert.match(deleteBlock, /thread\(name = "ticket-delete-backend"\)/);
  assert.match(deleteBlock, /SupabaseTicketBackendClient\(\)\.deleteTicket/);
  assert.match(deleteBlock, /runOnUiThread\s*\{/);
});

test("official ticket delete does not block Compose with runBlocking", () => {
  assert.doesNotMatch(deleteBlock, /runBlocking\s*\{/);
  assert.doesNotMatch(deleteBlock, /withContext\(Dispatchers\.IO\)/);
});
