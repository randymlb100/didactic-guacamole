// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  fetchUsers: vi.fn(),
  saveAllUsers: vi.fn(),
  addAuditLog: vi.fn(),
  saveMasterConfig: vi.fn(),
}));

vi.mock('./queries', () => ({
  fetchUsers: mocks.fetchUsers,
}));

vi.mock('./config', () => ({
  saveAllUsers: mocks.saveAllUsers,
  addAuditLog: mocks.addAuditLog,
}));

vi.mock('../masterConfig', () => ({
  buildMasterConfigKey: (prefix: string, ownerId: string) => `${prefix}:${ownerId}`,
  saveMasterConfig: mocks.saveMasterConfig,
}));

vi.mock('../supabaseClient', () => ({
  isSupabaseConfigured: false,
  supabase: null,
}));

import {
  createDrawResult,
  createUserAccount,
  deleteUserAccount,
  processRecharge,
  saveAdminLimitsPayload,
  saveAdminPayoutsPayload,
  saveAdminSystemModeConfig,
  saveManualDisabledLotteries,
  saveSportsLimits,
  toggleAdminStatus,
  updateUserAccount,
} from './mutations';

describe('Supabase mutations module', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('keeps every mutation required by the web dashboard available', () => {
    [
      createUserAccount,
      updateUserAccount,
      deleteUserAccount,
      toggleAdminStatus,
      processRecharge,
      saveAdminLimitsPayload,
      saveAdminPayoutsPayload,
      createDrawResult,
      saveAdminSystemModeConfig,
      saveManualDisabledLotteries,
      saveSportsLimits,
    ].forEach((mutation) => expect(mutation).toBeTypeOf('function'));
  });

  it('applies one cashier recharge and persists the user state once', async () => {
    const admin = {
      id: 'ADM-1', user: 'admin', role: 'ADMIN', active: true, balance: 0,
      rechargesEnabled: true, rechargesAssignedBalance: 5000, rechargesBalance: 3000,
      supervisorIds: [], supervisorUsers: [],
    };
    const cashier = {
      id: 'CAJ-1', user: 'cashier', role: 'CASHIER', active: true, balance: 0,
      rechargesEnabled: true, rechargesAssignedBalance: 500, rechargesBalance: 200,
      supervisorIds: [], supervisorUsers: [],
    };
    mocks.fetchUsers.mockResolvedValue([admin, cashier]);

    const result = await processRecharge('ADM-1', 'CAJ-1', 300, {
      id: 'MASTER-1', user: 'master', role: 'MASTER',
    });

    expect(result.admin.rechargesBalance).toBe(2700);
    expect(result.cashier.rechargesBalance).toBe(500);
    expect(result.cashier.rechargesAssignedBalance).toBe(800);
    expect(mocks.saveAllUsers).toHaveBeenCalledTimes(1);
    expect(mocks.addAuditLog).toHaveBeenCalledTimes(1);
  });

  it('keeps manual draw results without replacing the existing history', async () => {
    localStorage.setItem('lotterynet_results', JSON.stringify([{ id: 'old' }]));

    await createDrawResult({
      id: 'new', lotteryId: 'LOT-1', lotteryName: 'Loteria',
      dateKey: '2026-07-11', numbers: '01-02-03',
    });

    expect(JSON.parse(localStorage.getItem('lotterynet_results') || '[]')).toEqual([
      expect.objectContaining({ id: 'new' }),
      { id: 'old' },
    ]);
  });
});
