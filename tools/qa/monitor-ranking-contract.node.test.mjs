import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const adminMonitor = readFileSync('app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt', 'utf8');
const lotteryMonitor = readFileSync('app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt', 'utf8');

test('monitor ranking normalizes legacy and descriptive play type names', () => {
  assert.match(adminMonitor, /internal fun normalizeLotteryMonitorPlayType\(playType: String\): String/);
  for (const [source, target] of [
    ['"Q", "QUINIELA"', '"Q"'],
    ['"P", "PALE", "PALÉ"', '"P"'],
    ['"T", "TRIPLETA"', '"T"'],
    ['"SP", "SUPER_PALE", "SUPERPALE", "SUPER_PALÉ"', '"SP"'],
  ]) {
    assert.match(adminMonitor, new RegExp(`${source}[\\s\\S]*?-> ${target}`));
  }
});

test('number ranking uses normalized play types for filtering and limits', () => {
  assert.match(adminMonitor, /val normalized = normalizeLotteryMonitorPlayType\(playType\)/);
  assert.match(adminMonitor, /bucket\.playTypes \+= normalizeLotteryMonitorPlayType\(play\.playType\)/);
});

test('monitor ranking shows a global fallback and still honors explicit cashier overrides', () => {
  assert.match(
    adminMonitor,
    /if \(selectedCashierId\.isBlank\(\) \|\| selectedCashierId == ALL_MONITOR_CASHIERS_ID\) \{\s*return decodeCashierSalesLimitInputs\(payload\)/,
    'When all cashiers are selected, ranking should fall back to the shared default limits.',
  );
  assert.match(
    adminMonitor,
    /decodeCashierUserSalesLimitInputs\(payload, selectedKeys\)\?\.let \{ return it \}/,
    'Monitor ranking must prefer an explicit cashier override.',
  );
  assert.match(
    adminMonitor,
    /decodeMonitorAdminSelfLimits\(payload\)\?\.let \{ return it \}/,
    'Monitor ranking must still honor the admin self override when the selected row is the current session.',
  );
  assert.match(
    adminMonitor,
    /val limitAmount = cashierLimits\?\.let \{ monitorCashierLimitForBucket\(view, bucket\.playTypes, it\) \}/,
    'Ranking rows should compute a limit even when the panel is in the global scope.',
  );
  assert.match(
    adminMonitor,
    /limitScopeLabel = when \{[\s\S]*lotteryId == null -> "Tope global"/,
    'Ranking rows should expose whether the current scope is global or cashier-scoped.',
  );
});

test('monitor cards separate true blockage from no-movement state', () => {
  assert.match(
    adminMonitor,
    /val active = !row\.isBlocked/,
    'Cashier cards should use the dedicated blocked flag instead of inferring blockage from lack of activity.',
  );
  assert.match(
    adminMonitor,
    /val blockedCount = remember\(rows\) \{ rows\.count \{ it\.isBlocked \} \}/,
    'Blocked counters should only count real blocked accounts.',
  );
  assert.doesNotMatch(
    adminMonitor,
    /CompactStatusBadge\(label = statusLabel, tone = statusTone\)/,
    'Monitor cards should stop showing the old oversized status badge in the dense card.',
  );
  assert.doesNotMatch(
    adminMonitor,
    /row\.lastSeenLabel/,
    'Monitor cards should keep the dense card focused on the relevant metrics.',
  );
  assert.doesNotMatch(
    adminMonitor,
    /Ese cupo se calcula solo para esta vista y no mezcla todos los números de la tabla/,
    'Monitor cards should stop showing the long explanatory helper text.',
  );
  assert.match(
    adminMonitor,
    /limitScopeLabel[\s\S]*remainingLimitAmount/,
    'Monitor cards should keep the scoped limit visible as a compact inline line.',
  );
});

test('lottery monitor rows stay compact and drop redundant sync/status chrome', () => {
  assert.doesNotMatch(
    lotteryMonitor,
    /CompactStatusBadge\(label = "Sincronizado"/,
    'Lottery monitor should no longer show a standalone synced badge above the controls.',
  );
  assert.doesNotMatch(
    lotteryMonitor,
    /CompactStatusBadge\(label = "\$\{row\.playsCount\}", tone = gainColor\(\)\)/,
    'Lottery monitor ranking rows should not carry the old trailing plays badge.',
  );
  assert.match(
    lotteryMonitor,
    /row\.limitScopeLabel \?: "Tope"/,
    'Lottery monitor rows should surface the limit scope directly in the first text line.',
  );
  assert.match(
    lotteryMonitor,
    /row\.remainingAmount\?\.let \{ remaining ->[\s\S]*row\.limitScopeLabel \?: "Tope"/,
    'Lottery monitor rows should keep the remaining limit inline when the bucket has a cap.',
  );
});

test('lottery monitor totals use the same normalized play type mapping', () => {
  assert.match(lotteryMonitor, /normalizeLotteryMonitorPlayType\(play\.playType\) == "SP"/);
  assert.match(lotteryMonitor, /when \(normalizeLotteryMonitorPlayType\(playType\)\)/);
});
