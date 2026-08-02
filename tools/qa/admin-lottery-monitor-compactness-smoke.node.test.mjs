import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const lotteryMonitor = readFileSync(
  'app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt',
  'utf8',
);

test('lottery monitor compactness smoke keeps the ranking row focused and removes sync noise', () => {
  assert.doesNotMatch(
    lotteryMonitor,
    /CompactStatusBadge\(label = "Sincronizado"/,
    'The monitor should not show the standalone synced badge anymore.',
  );
  assert.doesNotMatch(
    lotteryMonitor,
    /CompactStatusBadge\(label = "\$\{row\.playsCount\}", tone = gainColor\(\)\)/,
    'The ranking row should not end with the old trailing plays badge.',
  );
  assert.match(
    lotteryMonitor,
    /row\.limitScopeLabel \?: "Tope"/,
    'The row should show the limit scope directly in the first line.',
  );
  assert.match(
    lotteryMonitor,
    /row\.remainingAmount\?\.let \{ remaining ->[\s\S]*limitScopeLabel \?: "Tope"/,
    'The row should keep the remaining limit inline when a cap exists.',
  );
});

console.log('Admin lottery monitor compactness smoke passed');
