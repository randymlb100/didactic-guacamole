import { describe, expect, it } from 'vitest';
import { getAllowedAdminTabs, getSafeAdminTab } from './navigationPermissions';

describe('getSafeAdminTab', () => {
  it('matches the Android master hierarchy', () => {
    expect(getAllowedAdminTabs('MASTER')).toEqual(['dashboard', 'admins', 'auditoria']);
    expect(getSafeAdminTab('MASTER', 'admins')).toBe('admins');
    expect(getSafeAdminTab('MASTER', 'deportiva')).toBe('dashboard');
    expect(getSafeAdminTab('MASTER', 'finanzas')).toBe('dashboard');
    expect(getSafeAdminTab('MASTER', 'cuadre')).toBe('dashboard');
    expect(getSafeAdminTab('MASTER', 'resultados')).toBe('dashboard');
  });

  it('keeps admin away only from master bank dashboard', () => {
    expect(getSafeAdminTab('ADMIN', 'admins')).toBe('dashboard');
    expect(getSafeAdminTab('ADMIN', 'auditoria')).toBe('auditoria');
    expect(getSafeAdminTab('ADMIN', 'cajeros')).toBe('cajeros');
    expect(getSafeAdminTab('ADMIN', 'comisiones')).toBe('comisiones');
    expect(getSafeAdminTab('ADMIN', 'cierres')).toBe('cierres');
    expect(getSafeAdminTab('ADMIN', 'cuadre')).toBe('cuadre');
    expect(getSafeAdminTab('ADMIN', 'reportes')).toBe('reportes');
  });

  it('lets supervisor open Android-equivalent read-only operation sections', () => {
    expect(getSafeAdminTab('SUPERVISOR', 'limites')).toBe('dashboard');
    expect(getSafeAdminTab('SUPERVISOR', 'auditoria')).toBe('dashboard');
    expect(getSafeAdminTab('SUPERVISOR', 'finanzas')).toBe('finanzas');
    expect(getSafeAdminTab('SUPERVISOR', 'reportes')).toBe('reportes');
    expect(getSafeAdminTab('SUPERVISOR', 'deportiva')).toBe('deportiva');
    expect(getSafeAdminTab('SUPERVISOR', 'tickets')).toBe('tickets');
    expect(getSafeAdminTab('SUPERVISOR', 'cuadre')).toBe('cuadre');
  });
});
