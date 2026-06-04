import { describe, expect, it } from 'vitest';
import {
  buildMasterConfigKey,
  DEFAULT_ADMIN_OPERATIONAL_LIMITS,
  DEFAULT_RECHARGE_LIMITS,
} from './masterConfig';

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
});
