import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt",
  "utf8",
);

test("manual ticket refresh hydrates only the visible bounded period", () => {
  const start = source.indexOf("private fun hydrateVisibleTicketsForSession()");
  const end = source.indexOf("private fun hydrateTicketPeriodInBackground", start);
  const method = source.slice(start, end);

  assert.match(
    method,
    /resolveTicketSummaryDateRange\(\s*currentSummaryPeriodId,\s*currentSummaryMonthValue,\s*\)/,
  );
  assert.match(method, /fromDate = fromDate/);
  assert.match(method, /toDate = toDate/);
  assert.match(method, /limit = TICKET_SUMMARY_PERIOD_FETCH_LIMIT/);
  assert.doesNotMatch(method, /fetchSnapshot\(ownerKey\)/);
});
