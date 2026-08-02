import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/tickets/TicketLookupActivity.kt",
  "utf8",
);

test("payout date filters preserve presets and add exact calendar dates", () => {
  assert.match(source, /QuickFilterChip\("today", "Hoy"\)/);
  assert.match(source, /QuickFilterChip\("yesterday", "Ayer"\)/);
  assert.match(source, /QuickFilterChip\("all", "Todos"\)/);
  assert.match(source, /DatePickerDialog\(/);
  assert.match(source, /label = if \(paymentDateFilter\.startsWith\("date:"\)\)/);
  assert.match(source, /paymentDateFilter = "date:\$\{pickerUtcMillisToTicketDateKey\(it\)\}"/);
});

test("payout exact date compares against the existing ticket day key", () => {
  const filter = source.slice(
    source.indexOf("internal fun filterTicketLookupPaymentView("),
    source.indexOf("internal fun ticketLookupPaymentDateOptions()"),
  );

  assert.match(filter, /val ticketDayKey = dominicanDayKey\(ticket\.createdAtEpochMs\)/);
  assert.match(filter, /dateFilter\.removePrefix\("date:"\)/);
  assert.match(filter, /ticketDayKey == it/);
  assert.match(filter, /if \(mode != LookupMode\.PAY \|\| query\.trim\(\)\.isNotBlank\(\)\) return tickets/);
});
