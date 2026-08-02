import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const sourcePath = path.join(
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
);

const source = fs.readFileSync(sourcePath, 'utf8');

test('sale list resolves an individual limit issue from the same lottery bucket', () => {
  assert.match(source, /internal fun resolveSaleRowLimitIssue\(/);
  assert.match(source, /resolveSaleExposureLimitBucket\(/);
  assert.match(source, /candidate\.lotteryId == bucket\.lotteryId/);
  assert.match(source, /if \(!limitRow\.overLimit\) return null/);
});

test('sale list renders the limit issue on the affected row', () => {
  assert.match(source, /saleLimitRemainingRows = saleLimitRemainingRows/);
  assert.match(source, /resolveSaleRowLimitIssue\(row, saleLimitRemainingRows\)/);
  assert.match(source, /Límite agotado/);
  assert.match(source, /Modifier\.semantics/);
  assert.match(source, /error\("\$\{limitIssue\.title\}/);
});

test('sale list keeps stable item keys and preserves the remove action', () => {
  assert.match(source, /items\(stagedRows, key = \{ it\.id \}\)/);
  assert.match(source, /onRemoveRow\(row\.id\)/);
  assert.match(source, /SaleRowCard\(/);
});

test('sale limit badge keeps the real remaining balance while previewing a larger amount', () => {
  assert.match(source, /internal fun resolveSaleLimitPreviewOverage\(/);
  assert.match(source, /requested - remaining/);
  assert.match(source, /return "\$remainingLabel · Excede/);
  assert.match(source, /effectiveSaleLimitBadgeTone/);
});

test('sale limit violations block staging and preserve per-lottery context', () => {
  assert.match(source, /buildSaleLimitBlockedMessage\(listOf\(limitError\)\)/);
  assert.match(source, /internal fun buildSaleLimitBlockedMessage\(/);
  assert.match(source, /candidate\.lotteryId == bucket\.lotteryId/);
  assert.doesNotMatch(source, /validationMessage = buildSaleLimitAdvisoryMessage\(listOf\(limitError\)\)/);
});

test('confirmed sales invalidate the remote exposure snapshot before recomputing the badge', () => {
  const saveIndex = source.indexOf('salesRepository.saveTicket(securedTicket)');
  const invalidateIndex = source.indexOf('remoteSaleLimitExposure = null', saveIndex);
  const refreshIndex = source.indexOf('exposureRefreshTick = System.currentTimeMillis()', invalidateIndex);

  assert.ok(saveIndex >= 0, 'confirmed ticket must be persisted locally');
  assert.ok(invalidateIndex > saveIndex, 'remote exposure must be invalidated after local save');
  assert.ok(refreshIndex > invalidateIndex, 'UI recomposition must follow cache invalidation');
  assert.match(source.slice(invalidateIndex - 260, refreshIndex + 100), /confirmed sale changes this bucket.*remote snapshot/s);
});

test('remote exposure remains a single active-bucket refresh with local fallback', () => {
  const stateIndex = source.indexOf('var remoteSaleLimitExposure');
  const effectStart = source.indexOf('LaunchedEffect(', stateIndex);
  const effectEnd = source.indexOf('    var saleLimitBadgeTone', effectStart);
  const effect = source.slice(effectStart, effectEnd);

  assert.match(effect, /activeSaleLimitBucket/);
  assert.match(effect, /remoteSaleLimitExposure = null/);
  assert.match(effect, /getSaleLimitExposure\(/);
  assert.match(source, /remoteSoldExposure \?: matchingRow\?\.sold/);
});
