import { describe, expect, it } from 'vitest';
import { mapAccountToRemoteUser, mapRemoteUserToAccount } from './userMapping';

describe('user mapping', () => {
  it('keeps cashier commission as web percent while preserving Android decimal storage', () => {
    const account = mapRemoteUserToAccount({
      id: 'c1',
      user: 'cajero1',
      pass: '1234',
      role: 'CASHIER',
      commissionRate: 0.075,
      recargaTx: 500,
    });

    expect(account.commissionRate).toBe(7.5);
    expect(account.recargaTxLimit).toBe(500);

    const remote = mapAccountToRemoteUser(account);
    expect(remote.commissionRate).toBe(0.075);
    expect(remote.recargaTx).toBe(500);
  });

  it('falls back to UNKNOWN for invalid remote roles', () => {
    const account = mapRemoteUserToAccount({
      id: 'u1',
      user: 'badrole',
      role: 'OWNER',
    });

    expect(account.role).toBe('UNKNOWN');
  });

  it('keeps null optional number fields nullable instead of converting them to zero', () => {
    const account = mapRemoteUserToAccount({
      id: 'u2',
      user: 'nullable',
      role: 'ADMIN',
      balance: null,
      rechargesAssignedBalance: '',
      rechargesBalance: undefined,
      credChangedAtEpochMs: null,
      updatedAtEpochMs: '',
    });

    expect(account.balance).toBe(0);
    expect(account.rechargesAssignedBalance).toBe(0);
    expect(account.rechargesBalance).toBe(0);
    expect(account.credChangedAtEpochMs).toBeNull();
    expect(account.updatedAtEpochMs).toBeNull();
  });

  it('round-trips supervisor auth system fields commission and recarga values', () => {
    const account = mapRemoteUserToAccount({
      id: 'c2',
      user: 'cajero2',
      role: 'cashier',
      commissionRate: 12.5,
      recargaTxLimit: 750,
      supervisorIds: ['sup-1', 'sup-2'],
      supervisorUsers: ['supervisor1', 'supervisor2'],
      authUserId: 'auth-123',
      credChangedAtEpochMs: 1717000000000,
      updatedAtEpochMs: 1717000001000,
      systemModeOverride: 'pick',
      limitsPayload: '{"quiniela":1000}',
    });

    expect(account.role).toBe('CASHIER');
    expect(account.commissionRate).toBe(12.5);
    expect(account.recargaTxLimit).toBe(750);
    expect(account.supervisorIds).toEqual(['sup-1', 'sup-2']);
    expect(account.supervisorUsers).toEqual(['supervisor1', 'supervisor2']);
    expect(account.authUserId).toBe('auth-123');
    expect(account.credChangedAtEpochMs).toBe(1717000000000);
    expect(account.updatedAtEpochMs).toBe(1717000001000);
    expect(account.systemModeOverride).toBe('pick');
    expect(account.limitsPayload).toBe('{"quiniela":1000}');

    const remote = mapAccountToRemoteUser(account);
    expect(remote.role).toBe('CASHIER');
    expect(remote.commissionRate).toBe(0.125);
    expect(remote.recargaTx).toBe(750);
    expect(remote.recargaTxLimit).toBe(750);
    expect(remote.supervisorIds).toEqual(['sup-1', 'sup-2']);
    expect(remote.supervisorUsers).toEqual(['supervisor1', 'supervisor2']);
    expect(remote.authUserId).toBe('auth-123');
    expect(remote.credChangedAtEpochMs).toBe(1717000000000);
    expect(remote.updatedAtEpochMs).toBe(1717000001000);
    expect(remote.systemModeOverride).toBe('pick');
    expect(remote.limitsPayload).toBe('{"quiniela":1000}');
  });
});
