import { describe, expect, it } from 'vitest';
import type { UserAccount } from '../types';
import { canShowRechargeAccess, canShowSportsbookAccess, type MasterSportsbookSettings } from './userFeatureAccess';

const admin: UserAccount = {
  id: 'adm-1',
  user: 'admin1',
  role: 'ADMIN',
  displayName: 'Admin Uno',
  active: true,
  banca: 'Banca Uno',
  balance: 0,
  rechargesEnabled: true,
  rechargesAssignedBalance: 1000,
  rechargesBalance: 800,
  supervisorIds: [],
  supervisorUsers: [],
};

const cashier: UserAccount = {
  id: 'caj-1',
  user: 'caj01',
  role: 'CASHIER',
  displayName: 'Cajero Uno',
  active: true,
  adminId: 'adm-1',
  adminUser: 'admin1',
  banca: 'Banca Uno',
  balance: 0,
  rechargesEnabled: true,
  rechargesAssignedBalance: 100,
  rechargesBalance: 50,
  supervisorIds: [],
  supervisorUsers: [],
};

const settings: MasterSportsbookSettings = {
  enabled: true,
  adminEnabled: true,
  supervisorEnabled: false,
  cashierEnabled: false,
  allowedActorKeys: [],
  cashierAdminKeys: ['adm-1'],
};

describe('userFeatureAccess', () => {
  it('shows recharge access only for active enabled admin/cashier owner paths', () => {
    expect(canShowRechargeAccess(admin, admin)).toBe(true);
    expect(canShowRechargeAccess(cashier, admin)).toBe(true);
    expect(canShowRechargeAccess({ ...admin, rechargesEnabled: false }, { ...admin, rechargesEnabled: false })).toBe(false);
    expect(canShowRechargeAccess({ ...cashier, role: 'SUPERVISOR' }, admin)).toBe(false);
  });

  it('shows sportsbook access from master settings by role and admin key', () => {
    expect(canShowSportsbookAccess(admin, settings)).toBe(true);
    expect(canShowSportsbookAccess(cashier, settings)).toBe(true);
    expect(canShowSportsbookAccess({ ...cashier, id: 'caj-2', user: 'caj02', adminId: 'adm-2' }, settings)).toBe(false);
  });

  it('supports explicit actor access for supervisors and individual cashiers', () => {
    expect(canShowSportsbookAccess({ ...cashier, id: 'caj-9', user: 'solo' }, {
      ...settings,
      adminEnabled: false,
      cashierAdminKeys: [],
      allowedActorKeys: ['solo'],
    })).toBe(true);
    expect(canShowSportsbookAccess({ ...cashier, role: 'SUPERVISOR', id: 'sup-1', user: 'sup1' }, {
      ...settings,
      adminEnabled: false,
      supervisorEnabled: true,
      cashierAdminKeys: [],
    })).toBe(true);
  });
});
