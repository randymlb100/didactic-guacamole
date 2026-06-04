import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const PROJECT_ROOT = new URL("../../", import.meta.url);

async function source(path) {
  return readFile(new URL(path, PROJECT_ROOT), "utf8");
}

function assertContains(text, expected, label) {
  assert.ok(text.includes(expected), `${label}: falta "${expected}"`);
}

test("ticket oficial conserva y muestra jugadas ganadoras", async () => {
  const [
    model,
    intentSnapshot,
    officialScreen,
    bitmapExport,
    thermalPrint,
    renderKey,
    hydration,
    localStorage,
    dayCache,
    edgeTicketList,
  ] = await Promise.all([
    source("app/src/main/java/com/lotterynet/pro/core/model/SalesModels.kt"),
    source("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketIntentSnapshot.kt"),
    source("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/render/RenderCacheKeys.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt"),
    source("app/src/main/java/com/lotterynet/pro/core/storage/SalesDayTicketCache.kt"),
    source("supabase/functions/get-ticket-list/index.ts"),
  ]);

  assertContains(model, "data class WinningPlayDetail", "modelo");
  assertContains(model, "winningDetails: List<WinningPlayDetail>", "modelo");

  for (const [label, text] of [
    ["snapshot intent", intentSnapshot],
    ["hidratacion", hydration],
    ["almacen local", localStorage],
    ["cache del dia", dayCache],
  ]) {
    assertContains(text, '"winningDetails"', label);
    assertContains(text, "lotteryName", label);
    assertContains(text, "playedNumber", label);
    assertContains(text, "payoutAmount", label);
  }

  assertContains(edgeTicketList, "winningDetails: items", "endpoint ticket list");
  assertContains(edgeTicketList, "is_winner,payout_amount,hit_position", "endpoint ticket list");

  assertContains(officialScreen, 'SectionHeader(title = "Premios del ticket"', "pantalla ticket oficial");
  assertContains(officialScreen, "WinningDetailRow(detail = detail)", "pantalla ticket oficial");
  assertContains(bitmapExport, '"PREMIOS DEL TICKET"', "imagen del ticket oficial");
  assertContains(thermalPrint, '"PREMIOS DEL TICKET"', "impresion termica");
  assertContains(renderKey, "ticket.winningDetails.forEach", "cache de imagen");
});
