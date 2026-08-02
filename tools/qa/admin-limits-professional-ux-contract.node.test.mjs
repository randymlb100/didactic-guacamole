import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const root = new URL('../../', import.meta.url);
const activity = await readFile(new URL('app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt', root), 'utf8');
const models = await readFile(new URL('app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsUiModels.kt', root), 'utf8');

test('limits UI exposes independent professional destinations', () => {
  for (const id of ['overview', 'pool', 'cashiers', 'adminSelf', 'cash', 'system']) {
    assert.match(activity, new RegExp(`"${id}"`));
  }
  for (const title of ['Pool de banca', 'Límites de cajeros', 'Límite propio del admin', 'Cobros y recargas', 'Modo POS']) {
    assert.match(models, new RegExp(title));
  }
});

test('pool and cashier scopes retain distinct copy', () => {
  assert.match(activity, /Estos valores limitan la exposición acumulada/);
  assert.match(activity, /Estos valores no controlan el pool/);
  assert.match(activity, /Cada bloque controla un alcance distinto/);
});

test('existing save and server contracts remain present', () => {
  assert.match(activity, /pushPoolLimitsServiceFirst/);
  assert.match(activity, /pushDefaultLimitsServiceFirst/);
  assert.match(activity, /pushAdminSelfLimitsServiceFirst/);
  assert.match(activity, /admin_operational_limits:/);
  assert.match(activity, /recharge_limits:/);
});

test('POS copy remains presentation-only', () => {
  assert.match(models, /Solo compacta la interfaz; no cambia límites/);
  assert.match(activity, /Este modo cambia la experiencia visual, no los límites de venta/);
});
