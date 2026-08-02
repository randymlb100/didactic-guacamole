import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const hydration = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt",
  "utf8",
);
const ticketDelta = readFileSync(
  "supabase/functions/get-ticket-delta/index.ts",
  "utf8",
);

test("historical ticket metadata without items is never invented as a current empty ticket", () => {
  const deltaParserStart = hydration.indexOf("internal fun parseTicketDeltaPayload");
  const nextFunction = hydration.indexOf("\ninternal fun ", deltaParserStart + 1);
  const deltaParser = hydration.slice(deltaParserStart, nextFunction);

  assert.ok(deltaParserStart >= 0, "ticket delta parser must exist");
  assert.match(deltaParser, /return parseWebTicketArray\(tickets\)/);
  assert.doesNotMatch(deltaParser, /System\.currentTimeMillis\(\)/);
  assert.doesNotMatch(deltaParser, /TicketRecord\(/);
  assert.match(
    hydration,
    /if \(plays\.isEmpty\(\) && total > 0\.0 && !status\.isRemoteTicketTombstone\(\)\) \{\s*return null/,
  );
});

test("ticket delta includes authoritative sale and draw dates", () => {
  const columnsStart = ticketDelta.indexOf("const TICKET_DELTA_COLUMNS");
  const columnsEnd = ticketDelta.indexOf("].join", columnsStart);
  const columns = ticketDelta.slice(columnsStart, columnsEnd);

  assert.ok(columnsStart >= 0, "ticket delta columns must exist");
  assert.match(columns, /"server_created_at"/);
  assert.match(columns, /"draw_date"/);
  assert.match(columns, /"draw_date_real"/);
});
