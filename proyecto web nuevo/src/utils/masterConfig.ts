import { supabase } from './supabaseClient';
import { emptyLotteryLimitStructure, type LotteryLimitStructure } from './lotteryLimitStructure';
import {
  emptyRecargasRapidasCredentialConfig,
  type RecargasRapidasCredentialConfig,
} from './recargasRapidasCredentials';
import { defaultMasterSportsbookSettings, type MasterSportsbookSettings } from './userFeatureAccess';

export type AdminMasterConfigPrefix =
  | 'cashier_limits'
  | 'cashier_prize_payouts'
  | 'system_modes'
  | 'manual_disabled_lotteries'
  | 'recharge_limits'
  | 'admin_operational_limits';

export type SportsbookMasterConfigPrefix =
  | 'sportsbook_global'
  | 'sportsbook_admin'
  | 'sportsbook_actor';

export type MasterConfigPrefix = AdminMasterConfigPrefix | SportsbookMasterConfigPrefix;

export interface RechargeLimitsPayload {
  globalPerTx: number;
  masterPerTx: number;
}

export interface AdminOperationalLimitsPayload {
  cashierPayoutLimit: number;
}

export interface SystemAlertPayload {
  id: string;
  timestampLabel: string;
  type: string;
  message: string;
  level: 'info' | 'success' | 'warning' | 'error' | string;
  read: boolean;
}

export const DEFAULT_RECHARGE_LIMITS: RechargeLimitsPayload = {
  globalPerTx: 0,
  masterPerTx: 0,
};

export const DEFAULT_ADMIN_OPERATIONAL_LIMITS: AdminOperationalLimitsPayload = {
  cashierPayoutLimit: 0,
};

export function buildMasterConfigKey(prefix: AdminMasterConfigPrefix, ownerId: string): string;
export function buildMasterConfigKey(prefix: 'sportsbook_global'): string;
export function buildMasterConfigKey(prefix: 'sportsbook_admin' | 'sportsbook_actor', ownerId: string): string;
export function buildMasterConfigKey(prefix: MasterConfigPrefix, ownerId?: string): string {
  if (prefix === 'sportsbook_global') return 'sportsbook:global';
  if (!ownerId || ownerId.trim().length === 0) {
    throw new Error(`Missing owner id for ${prefix}`);
  }
  if (prefix === 'sportsbook_admin') return `sportsbook:admin:${ownerId}`;
  if (prefix === 'sportsbook_actor') return `sportsbook:actor:${ownerId}`;
  return `${prefix}:${ownerId}`;
}

export async function getMasterConfig<T>(key: string, fallback: T): Promise<T> {
  if (!supabase) return fallback;

  const { data, error } = await supabase.functions.invoke('get-master-config', {
    body: { key },
  });

  if (error) {
    console.warn(`Failed to fetch master config ${key}`, error);
    return fallback;
  }

  if (!data || typeof data !== 'object' || !('payload' in data) || data.payload == null) {
    return fallback;
  }

  return data.payload as T;
}

export async function saveMasterConfig<T>(key: string, payload: T): Promise<void> {
  if (!supabase) {
    throw new Error(`No se pudo guardar ${key}: Supabase no esta configurado`);
  }

  const { error } = await supabase.functions.invoke('update-master-config', {
    body: { key, payload },
  });

  if (error) {
    throw new Error(`No se pudo guardar ${key}: ${error.message}`);
  }
}

const normalizeMoneyValue = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
};

export async function getRechargeLimits(adminId: string): Promise<RechargeLimitsPayload> {
  const payload = await getMasterConfig<Partial<RechargeLimitsPayload> & { recarga?: number } | null>(
    buildMasterConfigKey('recharge_limits', adminId),
    null
  );

  if (!payload) return DEFAULT_RECHARGE_LIMITS;

  return {
    globalPerTx: normalizeMoneyValue(payload.globalPerTx),
    masterPerTx: normalizeMoneyValue(payload.masterPerTx ?? payload.recarga),
  };
}

export async function saveRechargeLimits(adminId: string, payload: RechargeLimitsPayload): Promise<void> {
  await saveMasterConfig(buildMasterConfigKey('recharge_limits', adminId), {
    globalPerTx: normalizeMoneyValue(payload.globalPerTx),
    masterPerTx: normalizeMoneyValue(payload.masterPerTx),
  });
}

export async function getAdminOperationalLimits(adminId: string): Promise<AdminOperationalLimitsPayload> {
  const payload = await getMasterConfig<Partial<AdminOperationalLimitsPayload> | null>(
    buildMasterConfigKey('admin_operational_limits', adminId),
    null
  );

  if (!payload) return DEFAULT_ADMIN_OPERATIONAL_LIMITS;

  return {
    cashierPayoutLimit: normalizeMoneyValue(payload.cashierPayoutLimit),
  };
}

export async function saveAdminOperationalLimits(adminId: string, payload: AdminOperationalLimitsPayload): Promise<void> {
  await saveMasterConfig(buildMasterConfigKey('admin_operational_limits', adminId), {
    cashierPayoutLimit: normalizeMoneyValue(payload.cashierPayoutLimit),
  });
}

export async function getSystemAlerts(): Promise<SystemAlertPayload[]> {
  const payload = await getMasterConfig<unknown>('sys_alerts_v4', []);
  if (Array.isArray(payload)) return payload as SystemAlertPayload[];

  if (typeof payload === 'string') {
    try {
      const parsed = JSON.parse(payload);
      return Array.isArray(parsed) ? parsed as SystemAlertPayload[] : [];
    } catch {
      return [];
    }
  }

  return [];
}

export async function saveSystemAlerts(alerts: SystemAlertPayload[]): Promise<void> {
  await saveMasterConfig('sys_alerts_v4', alerts);
}

export async function getMasterSportsbookSettings(): Promise<MasterSportsbookSettings> {
  return getMasterConfig<MasterSportsbookSettings>('master_sportsbook_settings', defaultMasterSportsbookSettings);
}

export async function saveMasterSportsbookSettings(settings: MasterSportsbookSettings): Promise<void> {
  await saveMasterConfig('master_sportsbook_settings', settings);
}

export async function getRecargasRapidasCredentialConfig(): Promise<RecargasRapidasCredentialConfig> {
  return getMasterConfig<RecargasRapidasCredentialConfig>(
    'recargas_rapidas_credentials_v1',
    emptyRecargasRapidasCredentialConfig,
  );
}

export async function saveRecargasRapidasCredentialConfig(config: RecargasRapidasCredentialConfig): Promise<void> {
  await saveMasterConfig('recargas_rapidas_credentials_v1', config);
}

export async function getLotteryLimitStructure(): Promise<LotteryLimitStructure> {
  return getMasterConfig<LotteryLimitStructure>('lottery_limit_structure_v1', emptyLotteryLimitStructure);
}

export async function saveLotteryLimitStructure(limits: LotteryLimitStructure): Promise<void> {
  await saveMasterConfig('lottery_limit_structure_v1', limits);
}
