import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

function read(relativePath) {
  return readFileSync(`${root}/${relativePath}`, "utf8");
}

test("sale print button never falls back to the last saved ticket when there is no new play", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt");

  const contractSlice = source.slice(
    source.indexOf("internal fun resolveTicketPrintOpenContract"),
    source.indexOf("internal fun resolvePostAddCarryState"),
  );

  assert.doesNotMatch(contractSlice, /hasLatestTicket -> TicketPrintOpenContract\(/);
  assert.doesNotMatch(contractSlice, /openLatestTicket = true/);
  assert.match(contractSlice, /fallbackMessage = "No hay jugada para imprimir"/);

  const handlerSlice = source.slice(
    source.indexOf("onOpenOfficialTicket = {"),
    source.indexOf("onOpenSellerPicker = {"),
  );

  assert.doesNotMatch(handlerSlice, /openLatestTicket \? latestTicket : null/);
  assert.match(handlerSlice, /validationMessage = when \{/);
});
