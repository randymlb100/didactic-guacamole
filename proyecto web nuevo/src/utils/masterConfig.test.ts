import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  invoke: vi.fn(),
  getValidAccessToken: vi.fn(),
}));

vi.mock('./supabaseClient', () => ({
  isSupabaseConfigured: true,
  supabase: {
    functions: {
      invoke: mocks.invoke,
    },
  },
}));

vi.mock('./authSession', () => ({
  getValidAccessToken: mocks.getValidAccessToken,
}));

import {
  buildMasterConfigKey,
  DEFAULT_ADMIN_OPERATIONAL_LIMITS,
  DEFAULT_RECHARGE_LIMITS,
  getMasterConfig,
  saveMasterConfig,
} from './masterConfig';

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getValidAccessToken.mockReturnValue('test-access-token');
});

describe('buildMasterConfigKey', () => {
  it('builds Android-compatible admin config keys', () => {
    expect(buildMasterConfigKey('cashier_limits', 'admin-1')).toBe('cashier_limits:admin-1');
    expect(buildMasterConfigKey('cashier_prize_payouts', 'bank.user')).toBe('cashier_prize_payouts:bank.user');
    expect(buildMasterConfigKey('system_modes', 'adm_01')).toBe('system_modes:adm_01');
    expect(buildMasterConfigKey('manual_disabled_lotteries', 'adm:01')).toBe('manual_disabled_lotteries:adm:01');
    expect(buildMasterConfigKey('recharge_limits', 'adm-01')).toBe('recharge_limits:adm-01');
    expect(buildMasterConfigKey('admin_operational_limits', 'adm-01')).toBe('admin_operational_limits:adm-01');
  });

  it('builds Android-compatible sportsbook keys', () => {
    expect(buildMasterConfigKey('sportsbook_global')).toBe('sportsbook:global');
    expect(buildMasterConfigKey('sportsbook_admin', 'admin-1')).toBe('sportsbook:admin:admin-1');
    expect(buildMasterConfigKey('sportsbook_actor', 'cashier-1')).toBe('sportsbook:actor:cashier-1');
  });

  it('defines Android-compatible defaults for admin limits', () => {
    expect(DEFAULT_RECHARGE_LIMITS).toEqual({ globalPerTx: 0, masterPerTx: 0 });
    expect(DEFAULT_ADMIN_OPERATIONAL_LIMITS).toEqual({ cashierPayoutLimit: 0 });
  });

  it('dedupes repeated getMasterConfig requests for the same key', async () => {
    mocks.invoke.mockResolvedValueOnce({ data: { payload: { enabled: true } }, error: null });

    const [first, second] = await Promise.all([
      getMasterConfig('system_modes:admin-1', { enabled: false }),
      getMasterConfig('system_modes:admin-1', { enabled: false }),
    ]);

    expect(first).toEqual({ enabled: true });
    expect(second).toEqual({ enabled: true });
    expect(mocks.invoke).toHaveBeenCalledTimes(1);
  });

  it('invalidates cached master config after save', async () => {
    mocks.invoke
      .mockResolvedValueOnce({ data: { payload: { hidden: false } }, error: null })
      .mockResolvedValueOnce({ data: null, error: null })
      .mockResolvedValueOnce({ data: { payload: { hidden: true } }, error: null });

    await expect(getMasterConfig('manual_disabled_lotteries:admin-2', { hidden: false }))
      .resolves.toEqual({ hidden: false });

    await saveMasterConfig('manual_disabled_lotteries:admin-2', { hidden: true });

    await expect(getMasterConfig('manual_disabled_lotteries:admin-2', { hidden: false }))
      .resolves.toEqual({ hidden: true });

    expect(mocks.invoke).toHaveBeenCalledTimes(3);
  });
});
