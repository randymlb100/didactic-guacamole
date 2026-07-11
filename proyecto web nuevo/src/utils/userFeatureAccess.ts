import type { UserAccount } from '../types';

export interface MasterSportsbookSettings {
  enabled: boolean;
  adminEnabled: boolean;
  supervisorEnabled: boolean;
  cashierEnabled: boolean;
  allowedActorKeys: string[];
  cashierAdminKeys: string[];
}

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

const accountKeys = (account: Pick<UserAccount, 'id' | 'user' | 'adminId' | 'adminUser'>): string[] => {
  return [account.id, account.user, account.adminId || '', account.adminUser || '']
    .map(normalizeKey)
    .filter(Boolean);
};

export const defaultMasterSportsbookSettings: MasterSportsbookSettings = {
  enabled: false,
  adminEnabled: false,
  supervisorEnabled: false,
  cashierEnabled: false,
  allowedActorKeys: [],
  cashierAdminKeys: [],
};

export const canShowRechargeAccess = (sessionUser: UserAccount, ownerAccount?: UserAccount | null): boolean => {
  if (sessionUser.role !== 'ADMIN' && sessionUser.role !== 'CASHIER') return false;
  const owner = sessionUser.role === 'ADMIN' ? sessionUser : ownerAccount;
  return owner?.active !== false && owner?.rechargesEnabled === true;
};

export const canShowSportsbookAccess = (sessionUser: UserAccount, settings: MasterSportsbookSettings): boolean => {
  if (!settings.enabled) return false;
  if (sessionUser.role === 'MASTER') return true;

  const actorKeys = new Set(accountKeys(sessionUser));
  const allowedActorKeys = new Set(settings.allowedActorKeys.map(normalizeKey));
  const allowedAdminKeys = new Set(settings.cashierAdminKeys.map(normalizeKey));
  const hasExplicitActorAccess = [...actorKeys].some((key) => allowedActorKeys.has(key));
  const hasAdminScopeAccess = [...actorKeys].some((key) => allowedAdminKeys.has(key));

  if (sessionUser.role === 'ADMIN') return settings.adminEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  if (sessionUser.role === 'SUPERVISOR') return settings.supervisorEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  if (sessionUser.role === 'CASHIER') return settings.cashierEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  return false;
};
