import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(path, "utf8");

test("Super Pale creates one staged play with a secondary lottery", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/sales/SaleValidator.kt");
  const merge = source.slice(
    source.indexOf("fun mergeIntoRows("),
    source.indexOf("fun buildLigarRows(", source.indexOf("fun mergeIntoRows(")),
  );

  assert.match(merge, /if \(play\.playType == "SP"\)/);
  assert.match(merge, /secondaryLotteryId = secondary\?\.id/);
  assert.match(merge, /secondaryLotteryName = secondary\?\.name/);
  assert.match(merge, /mergeRow\(/);
  const superPaleBranch = merge.slice(0, merge.indexOf("} else {"));
  assert.doesNotMatch(superPaleBranch, /selectedLotteries\.fold\(existing\)/);
});

test("two selected lotteries without Super Pale remain independent plays", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/core/sales/SaleValidator.kt");
  const merge = source.slice(
    source.indexOf("fun mergeIntoRows("),
    source.indexOf("fun buildLigarRows(", source.indexOf("fun mergeIntoRows(")),
  );

  assert.match(merge, /else \{\s*selectedLotteries\.fold\(existing\)/s);
  assert.match(merge, /secondaryLotteryId = secondary\?\.id/);
});

test("official ticket groups Super Pale by both lotteries as one group", () => {
  const source = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt");
  const grouping = source.slice(
    source.indexOf("val groups = ticket.plays"),
    source.indexOf("val hasConfirmedPrize", source.indexOf("val groups = ticket.plays")),
  );

  assert.match(grouping, /officialTicketSnapshotPlayTypeLabel\(play\.playType\) == "SP"/);
  assert.match(grouping, /play\.secondaryLotteryName/);
  assert.match(grouping, /\$primary \/ \$secondary/);
  assert.match(grouping, /playCount = plays\.size/);
});
