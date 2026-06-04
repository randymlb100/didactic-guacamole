import { describe, expect, it } from 'vitest';
import { getRoleParity, getVisibleTabsForRole, isTabAllowedForRole } from './roleParity';

describe('roleParity', () => {
  it('matches real Android MASTER console sections', () => {
    const parity = getRoleParity('MASTER');
    expect(parity.homeTab).toBe('dashboard');
    expect(parity.visibleTabs).toEqual(['dashboard', 'admins', 'auditoria']);
    expect(parity.consoleSections).toEqual(['banks', 'credentials', 'server', 'recharges', 'audit']);
    expect(isTabAllowedForRole('MASTER', 'deportiva')).toBe(false);
    expect(isTabAllowedForRole('MASTER', 'finanzas')).toBe(false);
  });

  it('matches real Android ADMIN operational admin scope without POS sale', () => {
    expect(getVisibleTabsForRole('ADMIN')).toEqual([
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
    ]);
  });

  it('matches real Android SUPERVISOR assigned-operation scope', () => {
    expect(getVisibleTabsForRole('SUPERVISOR')).toEqual([
      'dashboard',
      'monitoreo',
      'deportiva',
      'tickets',
      'resultados',
      'finanzas',
      'cuadre',
      'reportes',
    ]);
    expect(isTabAllowedForRole('SUPERVISOR', 'limites')).toBe(false);
    expect(isTabAllowedForRole('SUPERVISOR', 'auditoria')).toBe(false);
  });

  it('can hide sportsbook at runtime without changing role parity source', () => {
    expect(getVisibleTabsForRole('ADMIN', { sportsbookVisible: false })).not.toContain('deportiva');
    expect(getVisibleTabsForRole('SUPERVISOR', { sportsbookVisible: false })).not.toContain('deportiva');
  });
});
