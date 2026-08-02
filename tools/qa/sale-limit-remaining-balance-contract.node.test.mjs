import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const salesSource = fs.readFileSync(
  path.join(
    root,
    'app',
    'src',
    'main',
    'java',
    'com',
    'lotterynet',
    'pro',
    'ui',
    'sales',
    'SalesActivity.kt',
  ),
  'utf8',
);
const exposureSource = fs.readFileSync(
  path.join(
    root,
    'app',
    'src',
    'main',
    'java',
    'com',
    'lotterynet',
    'pro',
    'core',
    'sales',
    'SaleExposureEngine.kt',
  ),
  'utf8',
);

function remaining(limit, sold, pending, nextAmount) {
  return Math.max(0, limit - sold - pending - nextAmount);
}

function tryStageSale({ limit, sold, pending = 0, amount }) {
  const available = Math.max(0, limit - sold - pending);
  const accepted = amount > 0 && amount <= available;
  return {
    accepted,
    remaining: accepted ? available - amount : available,
    message: accepted ? null : 'Límite agotado',
  };
}

test('remaining balance persists after a new ticket: 80 - 60 - 10 = 10', () => {
  assert.equal(remaining(80, 60, 0, 10), 10);
  assert.equal(remaining(80, 60, 0, 10), 10, 'creating a new ticket must not reset the balance');
});

test('remaining balance is isolated by lottery, play type and number', () => {
  const buckets = new Map([
    ['leidsa|Q|02', 60],
    ['nacional|Q|02', 5],
  ]);

  assert.equal(80 - buckets.get('leidsa|Q|02') - 10, 10);
  assert.equal(80 - buckets.get('nacional|Q|02') - 10, 65);
  assert.notEqual(
    buckets.get('leidsa|Q|02'),
    buckets.get('nacional|Q|02'),
    'the same number in another lottery must use its own exposure',
  );
});

test('real sale flow keeps the balance and blocks only the exhausted bucket', () => {
  let leidsa02Sold = 60;

  const first = tryStageSale({ limit: 80, sold: leidsa02Sold, amount: 10 });
  assert.equal(first.accepted, true);
  assert.equal(first.remaining, 10);
  leidsa02Sold += 10;

  const second = tryStageSale({ limit: 80, sold: leidsa02Sold, amount: 10 });
  assert.equal(second.accepted, true);
  assert.equal(second.remaining, 0);
  leidsa02Sold += 10;

  const third = tryStageSale({ limit: 80, sold: leidsa02Sold, amount: 1 });
  assert.equal(third.accepted, false);
  assert.equal(third.remaining, 0);
  assert.match(third.message, /Límite agotado/);

  const otherLottery = tryStageSale({ limit: 80, sold: 5, amount: 10 });
  const otherNumber = tryStageSale({ limit: 80, sold: 5, amount: 10 });
  assert.equal(otherLottery.accepted, true);
  assert.equal(otherNumber.accepted, true);
  assert.equal(leidsa02Sold, 80, 'only Leidsa Q 02 reaches the limit');
});

test('a request above the remaining balance is rejected before staging', () => {
  const result = tryStageSale({ limit: 50, sold: 0, amount: 950 });
  assert.equal(result.accepted, false);
  assert.equal(result.remaining, 50, 'rejected input must not consume the remaining balance');
  assert.equal(result.message, 'Límite agotado');
});

test('Android recomputes from saved day tickets and staged exposure', () => {
  assert.match(salesSource, /salesRepository\.getTicketsForDay\(dayKey\)/);
  assert.match(salesSource, /calculateSaleLimitSoldExposureForRole\(/);
  assert.match(salesSource, /calculateGlobalStagedExposure\(/);
  assert.match(salesSource, /resolveSaleExposureLimitBucket\(play\.playType, play\.normalizedNumber, selectedLottery\?\.id\)/);
  assert.match(salesSource, /remoteSaleLimitExposure = null/);
  assert.match(salesSource, /remoteSoldExposure\?\.let \{ maxOf\(it, localSoldExposure\) \} \?: localSoldExposure/);
  assert.match(salesSource, /localSaleLimitExposureOverrides\[bucket\] \?\: 0\.0/);
  assert.match(salesSource, /confirmedExposureByBucket/);
});

test('exposure bucket keeps lottery, play type and number separated', () => {
  assert.match(exposureSource, /resolveSaleExposureLimitBucket\(play\.playType, play\.normalizedNumber, lottery\.id\)/);
  assert.match(exposureSource, /calculateGlobalStagedExposure\(stagedRows, bucket\)/);
  assert.match(exposureSource, /soldOwn \+ pending \+ amount > ownLimit/);
  assert.match(exposureSource, /soldPool \+ pending \+ amount > poolLimit/);
});

test('Android source keeps the blocking and reset rules wired to the flow', () => {
  assert.match(salesSource, /buildSaleLimitBlockedMessage\(/);
  assert.match(salesSource, /This is the last gate before a row can enter the pending-sale list/);
  assert.match(salesSource, /finalLimitError = checkedValidation\.resolvedPlay\?\.let/);
  assert.match(salesSource, /Use the same configured limit state that paints the center/);
  assert.match(salesSource, /if \(sold \+ pending \+ checkedAmount > limit\)/);
  assert.match(salesSource, /amount = checkedAmount/);
  assert.match(salesSource, /if \(finalLimitError != null\)/);
  assert.match(salesSource, /Selecting a new number starts a new validation/);
  assert.match(exposureSource, /sold \+ pending >= limit/);
  assert.match(exposureSource, /scope = "Límite"/);
  assert.match(exposureSource, /scope = "Límite de pool"/);
  assert.match(exposureSource, /\$scope agotado · \$location/);
  assert.match(exposureSource, /\$scope: quedan \$\{formatWholeAmount\(available\)\}/);
  assert.match(exposureSource, /limitErrors\.distinct\(\)\.joinToString\(" · "\)/);
  assert.match(salesSource, /scope = "Límite"/);
  assert.match(salesSource, /Nº \$\{bucket\.number\}/);
});
