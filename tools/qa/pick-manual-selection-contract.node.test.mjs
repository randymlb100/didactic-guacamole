import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";

const root = fileURLToPath(new URL("../..", import.meta.url));
const salesActivity = readFileSync(
  join(root, "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt"),
  "utf8",
);

assert(
  !salesActivity.includes("LaunchedEffect(pickAssistedEntry, pickerLotteries, selectedLotteryIds)"),
  "Pick selection must stay manual while the user types; do not auto-switch lotteries from pickAssistedEntry.",
);

assert(
  salesActivity.includes("selectedLotteryIds = resolvePostTicketLotterySelection("),
  "After a ticket is completed, sale flow must still move to the next preferred/closing lottery.",
);

console.log("Pick manual selection contract passed");
