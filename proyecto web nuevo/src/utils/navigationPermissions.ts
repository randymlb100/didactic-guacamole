import type { UserRole } from '../types';
import { getVisibleTabsForRole, isTabAllowedForRole, type RuntimeFeatureFlags } from './roleParity';

export const getAllowedAdminTabs = (
  role: UserRole | string | null | undefined,
  flags: RuntimeFeatureFlags = {},
): string[] => {
  return getVisibleTabsForRole(role, flags);
};

export const isAdminTabAllowed = (
  role: UserRole | string | null | undefined,
  tab: string,
  flags: RuntimeFeatureFlags = {},
): boolean => {
  return isTabAllowedForRole(role, tab, flags);
};

export const getSafeAdminTab = (
  role: UserRole | string | null | undefined,
  activeTab: string,
  flags: RuntimeFeatureFlags = {},
): string => {
  return isAdminTabAllowed(role, activeTab, flags) ? activeTab : 'dashboard';
};
