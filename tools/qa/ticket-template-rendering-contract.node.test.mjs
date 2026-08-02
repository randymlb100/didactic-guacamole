import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(path, "utf8");

test("thermal ticket template hides single-lottery subtotal and formats Pick labels", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt");

  assert.match(source, /val showPlayedLotterySubtotals = groupedTicketPlays\.size > 1/);
  assert.match(source, /if \(showPlayedLotterySubtotals\) \{\s*lines \+= ThermalLineStyling\.bold\(\s*alignMoney\("SUBTOTAL"/s);
  assert.doesNotMatch(source, /MONTO LOTERIA/);
  assert.match(source, /"PICK3_STRAIGHT", "PICK_3_STRAIGHT" -> "P3"/);
  assert.match(source, /"PICK3_BOX", "PICK_3_BOX" -> "P3BOX"/);
  assert.match(source, /"P3", "P3BOX", "P4", "P4BOX" -> normalizedPick/);
  assert.match(source, /"TRIPLETA" -> "T"/);
});

test("official and bitmap ticket exports keep original abbreviated Pick labels", () => {
  const official = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt");
  const bitmap = read("app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt");
  const staticHtml = read("app/src/main/java/com/lotterynet/pro/core/export/StaticExportTemplateRepository.kt");
  const cache = read("app/src/main/java/com/lotterynet/pro/core/render/RenderCacheKeys.kt");

  assert.match(official, /"PICK3_STRAIGHT", "PICK_3_STRAIGHT" -> "P3"/);
  assert.match(official, /"PICK3_BOX", "PICK_3_BOX" -> "P3BOX"/);
  assert.match(official, /"TRIPLETA" -> "T"/);
  assert.doesNotMatch(official, /P3STRAIGHT/);

  assert.match(bitmap, /playTypeShortLabel\(play\.playType\)/);
  assert.doesNotMatch(bitmap, /\$\{play\.playType\.uppercase\(Locale\.US\)\}/);
  assert.match(bitmap, /"P3", "P3BOX", "P4", "P4BOX" -> normalizePickPlayType\(playType\)\.orEmpty\(\)/);

  assert.match(staticHtml, /val showLotterySubtotals = grouped\.size > 1/);
  assert.match(staticHtml, /<span>Subtotal<\/span>/);
  assert.doesNotMatch(staticHtml, /Monto lotería/);
  assert.match(staticHtml, /officialPlayTypeShortLabel\(detail\.playType\)/);
  assert.match(staticHtml, /"P3", "P3BOX", "P4", "P4BOX" -> normalizePickPlayType\(playType\)\.orEmpty\(\)/);

  assert.match(cache, /append\("ticket-v2\|"\)/);
});

test("official share layout keeps a cleaner and wider shared ticket template", () => {
  const bitmap = read("app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt");
  const renderSlice = bitmap.slice(
    bitmap.indexOf("val shareSpacing = resolveOfficialTicketShareSpacing("),
    bitmap.indexOf("internal fun resolveOfficialTicketShareSpacing("),
  );

  assert.match(bitmap, /internal data class OfficialTicketShareSpacing/);
  assert.match(bitmap, /resolveOfficialTicketShareSpacing\(/);
  assert.match(bitmap, /showRowDividers = false/);
  assert.match(bitmap, /columnGapPx = when \{/);
  assert.match(bitmap, /rowGapPx = if \(density\.compact\) 6f else 10f/);
  assert.match(renderSlice, /val columnGap = shareSpacing\.columnGapPx/);
  assert.match(renderSlice, /val rowTop = top \+ 44f \+ \(rowIndex \* \(rowHeight \+ shareSpacing\.rowGapPx\)\)/);
  assert.match(renderSlice, /if \(shareSpacing\.showRowDividers && rowIndex > 0\)/);
});
