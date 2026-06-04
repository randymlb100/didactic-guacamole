import type { UserRole } from '../types';

export type RoleName = UserRole | string | null | undefined;

export interface RuntimeFeatureFlags {
  sportsbookVisible?: boolean;
}

export interface RoleParityDefinition {
  role: string;
  homeTab: string;
  visibleTabs: string[];
  consoleSections: string[];
  androidSourceFiles: string[];
}

const normalizeRole = (role: RoleName): string => String(role || 'UNKNOWN').toUpperCase();

const ROLE_PARITY: Record<string, RoleParityDefinition> = {
  MASTER: {
    role: 'MASTER',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard', 'admins', 'auditoria'],
    consoleSections: ['banks', 'credentials', 'server', 'recharges', 'audit'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt',
    ],
  },
  ADMIN: {
    role: 'ADMIN',
    homeTab: 'dashboard',
    visibleTabs: [
      'dashboard',
      'cajeros',
      'supervisores',
      'comisiones',
      'monitoreo',
      'deportiva',
      'tickets',
      'ganadores',
      'resultados',
      'limites',
      'cierres',
      'finanzas',
      'cuadre',
      'reportes',
      'auditoria',
    ],
    consoleSections: ['summary', 'critical', 'secondary', 'administration', 'system'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt',
    ],
  },
  SUPERVISOR: {
    role: 'SUPERVISOR',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard', 'monitoreo', 'deportiva', 'tickets', 'resultados', 'finanzas', 'cuadre', 'reportes'],
    consoleSections: ['myCashiers', 'monitoring', 'finance', 'report', 'tickets', 'results'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt',
    ],
  },
};

export const getRoleParity = (role: RoleName): RoleParityDefinition => {
  return ROLE_PARITY[normalizeRole(role)] || {
    role: 'UNKNOWN',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard'],
    consoleSections: [],
    androidSourceFiles: [],
  };
};

export const getVisibleTabsForRole = (role: RoleName, flags: RuntimeFeatureFlags = {}): string[] => {
  return getRoleParity(role).visibleTabs.filter((tab) => {
    if (tab === 'deportiva' && flags.sportsbookVisible === false) return false;
    return true;
  });
};

export const isTabAllowedForRole = (role: RoleName, tab: string, flags: RuntimeFeatureFlags = {}): boolean => {
  return getVisibleTabsForRole(role, flags).includes(tab);
};
