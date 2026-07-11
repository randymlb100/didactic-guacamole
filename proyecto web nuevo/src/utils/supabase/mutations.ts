import type { DrawResult, UserAccount } from '../../types';
import { buildMasterConfigKey, saveMasterConfig } from '../masterConfig';
import { isSupabaseConfigured, supabase } from '../supabaseClient';
import { addAuditLog, saveAllUsers } from './config';
import {
  fetchUsers,
  type AdminSystemModeConfig,
  type ManualDisabledLotteryConfig,
  type SportsLimitConfig,
} from './queries';

const isDev = import.meta.env.DEV;
const logWarn = (...args: unknown[]) => { if (isDev) console.warn(...args); };

export const createUserAccount = async (
  newUser: Omit<UserAccount, 'id' | 'createdLabel' | 'balance'> & { baseBalance?: number },
): Promise<UserAccount> => {
  const users = await fetchUsers();
  const idPrefix = newUser.role === 'ADMIN' ? 'ADM' : newUser.role === 'SUPERVISOR' ? 'SUP' : 'CAJ';
  const created: UserAccount = {
    ...newUser,
    id: `${idPrefix}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`,
    createdLabel: new Intl.DateTimeFormat('es-DO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }).format(new Date()),
    balance: newUser.baseBalance || 0,
    supervisorIds: newUser.supervisorIds || [],
    supervisorUsers: newUser.supervisorUsers || [],
  };

  users.push(created);
  await saveAllUsers(users);
  return created;
};

export const updateUserAccount = async (updated: UserAccount): Promise<UserAccount> => {
  const users = await fetchUsers();
  const index = users.findIndex((candidate) => candidate.id === updated.id);
  if (index === -1) throw new Error('Usuario no encontrado');

  users[index] = {
    ...users[index],
    ...updated,
    updatedAtEpochMs: Date.now(),
  };
  await saveAllUsers(users);
  return users[index];
};

export const deleteUserAccount = async (id: string): Promise<void> => {
  const users = await fetchUsers();
  await saveAllUsers(users.filter((candidate) => candidate.id !== id));
};

export const toggleAdminStatus = async (
  adminId: string,
): Promise<{ admin: UserAccount; affectedCashiers: number }> => {
  const users = await fetchUsers();
  const adminIndex = users.findIndex((candidate) => candidate.id === adminId && candidate.role === 'ADMIN');
  if (adminIndex === -1) throw new Error('Banca administradora no encontrada');

  const admin = users[adminIndex];
  const nextStatus = !admin.active;
  admin.active = nextStatus;

  let affectedCashiers = 0;
  users.forEach((candidate) => {
    if ((candidate.role === 'CASHIER' || candidate.role === 'SUPERVISOR') && candidate.adminId === adminId) {
      candidate.active = nextStatus;
      affectedCashiers += 1;
    }
  });

  await saveAllUsers(users);
  return { admin, affectedCashiers };
};

export const processRecharge = async (
  adminId: string,
  cashierId: string,
  amount: number,
  actor: { id: string; user: string; role: string },
): Promise<{ admin: UserAccount; cashier: UserAccount }> => {
  const users = await fetchUsers();
  const adminIndex = users.findIndex((candidate) => candidate.id === adminId && candidate.role === 'ADMIN');
  const cashierIndex = users.findIndex((candidate) => candidate.id === cashierId && candidate.role === 'CASHIER');

  if (adminIndex === -1) throw new Error('Administrador no encontrado');
  if (cashierIndex === -1) throw new Error('Cajero no encontrado');

  const admin = users[adminIndex];
  const cashier = users[cashierIndex];
  if (admin.rechargesBalance < amount && adminId !== actor.id) {
    throw new Error('Balance de recarga insuficiente en el administrador.');
  }

  if (admin.rechargesEnabled) admin.rechargesBalance -= amount;
  cashier.rechargesBalance += amount;
  cashier.rechargesAssignedBalance += amount;
  await saveAllUsers(users);

  await addAuditLog(
    actor,
    'PROCESS_RECHARGE',
    `Recargado balance de cajero: $${amount.toFixed(2)} asignado a ${cashier.displayName || cashier.user}`,
    'success',
  );
  return { admin, cashier };
};

export const saveAdminLimitsPayload = async (adminId: string, payload: string): Promise<void> => {
  const parsedPayload = JSON.parse(payload);
  localStorage.setItem(`lotterynet_limits_${adminId}`, payload);

  if (isSupabaseConfigured && supabase) {
    try {
      await saveMasterConfig(buildMasterConfigKey('cashier_limits', adminId), parsedPayload);
    } catch (error) {
      logWarn(`Failed to save cashier_limits:${adminId} to master_state, keeping compatibility cache`, error);
    }
  }

  const users = await fetchUsers();
  const adminIndex = users.findIndex((candidate) => candidate.id === adminId && candidate.role === 'ADMIN');
  if (adminIndex !== -1) {
    users[adminIndex].limitsPayload = payload;
    await saveAllUsers(users);
  }
};

export const saveAdminPayoutsPayload = async (adminId: string, payload: string): Promise<void> => {
  const parsed = JSON.parse(payload);
  localStorage.setItem(`lotterynet_payouts_${adminId}`, payload);

  if (isSupabaseConfigured && supabase) {
    try {
      await saveMasterConfig(buildMasterConfigKey('cashier_prize_payouts', adminId), parsed);
    } catch (error) {
      logWarn(`Failed to upsert cashier_prize_payouts:${adminId} to master_state`, error);
    }

    try {
      const { error } = await supabase.from('lotterynet_kv').upsert({
        key: `cashier_prize_payouts:${adminId}`,
        value: parsed,
        updated_at: new Date().toISOString(),
      });
      if (error) throw error;
    } catch (error) {
      logWarn(`Failed to update compatibility payout cache for ${adminId}`, error);
    }
  }
};

export const createDrawResult = async (result: DrawResult): Promise<DrawResult> => {
  let list: DrawResult[] = [];
  try {
    const parsed = JSON.parse(localStorage.getItem('lotterynet_results') || '[]');
    if (Array.isArray(parsed)) list = parsed;
  } catch {
    list = [];
  }
  list.unshift(result);
  localStorage.setItem('lotterynet_results', JSON.stringify(list));
  return result;
};

export const saveAdminSystemModeConfig = async (
  adminId: string,
  config: AdminSystemModeConfig,
): Promise<void> => {
  const payload = { ...config, updatedAt: Date.now() };

  if (isSupabaseConfigured && supabase) {
    try {
      await saveMasterConfig(buildMasterConfigKey('system_modes', adminId), payload);
    } catch (error) {
      logWarn(`Failed to upsert system_modes:${adminId} to master_state`, error);
    }

    try {
      const { error } = await supabase.from('lotterynet_kv').upsert({
        key: `system_modes:${adminId}`,
        value: payload,
        updated_at: new Date().toISOString(),
      });
      if (error) throw error;
      localStorage.setItem(`lotterynet_system_modes_${adminId}`, JSON.stringify(payload));
      return;
    } catch (error) {
      logWarn(`Failed to update compatibility system mode cache for ${adminId}`, error);
    }
  }

  localStorage.setItem(`lotterynet_system_modes_${adminId}`, JSON.stringify(payload));
};

export const saveManualDisabledLotteries = async (
  adminId: string,
  config: ManualDisabledLotteryConfig,
): Promise<void> => {
  const payload = { ...config, updatedAt: Date.now() };

  if (isSupabaseConfigured && supabase) {
    try {
      await saveMasterConfig(buildMasterConfigKey('manual_disabled_lotteries', adminId), payload);
    } catch (error) {
      logWarn(`Failed to upsert manual_disabled_lotteries:${adminId} to master_state`, error);
    }

    try {
      const { error } = await supabase.from('lotterynet_kv').upsert({
        key: `manual_disabled_lotteries:${adminId}`,
        value: payload,
        updated_at: new Date().toISOString(),
      });
      if (error) throw error;
      localStorage.setItem(`lotterynet_manual_disabled_lotteries_${adminId}`, JSON.stringify(payload));
      return;
    } catch (error) {
      logWarn(`Failed to update compatibility disabled-lottery cache for ${adminId}`, error);
    }
  }

  localStorage.setItem(`lotterynet_manual_disabled_lotteries_${adminId}`, JSON.stringify(payload));
};

export const saveSportsLimits = async (config: SportsLimitConfig): Promise<void> => {
  if (isSupabaseConfigured && supabase) {
    try {
      const { error } = await supabase.from('sports_limits').upsert({
        scope_key: config.scope_key,
        max_ticket_stake: config.max_ticket_stake,
        max_selection_stake: config.max_selection_stake,
        max_event_exposure: config.max_event_exposure,
        max_potential_payout: config.max_potential_payout,
        enabled_markets: config.enabled_markets,
        updated_at: new Date().toISOString(),
      });
      if (error) throw error;
      return;
    } catch (error) {
      logWarn(`Failed to upsert sports_limits for ${config.scope_key} to Supabase`, error);
    }
  }

  localStorage.setItem(`lotterynet_sports_limits_${config.scope_key}`, JSON.stringify(config));
};
