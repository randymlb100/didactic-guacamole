import type { UserAccount, UserRole } from '../types';

type RemoteUser = Record<string, unknown>;

const VALID_ROLES: UserRole[] = ['MASTER', 'ADMIN', 'SUPERVISOR', 'CASHIER', 'UNKNOWN'];

function readString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function readNumber(value: unknown, fallback = 0): number {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return fallback;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : fallback;
}

function readOptionalNumber(value: unknown): number | null {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function readStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}

function readUserRole(value: unknown): UserRole {
  if (typeof value !== 'string' || value.trim() === '') return 'CASHIER';
  const role = value.trim().toUpperCase();
  return VALID_ROLES.includes(role as UserRole) ? (role as UserRole) : 'UNKNOWN';
}

export function normalizeCommissionToPercent(value: unknown, fallback = 8): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return fallback;
  return numeric > 1 ? numeric : numeric * 100;
}

export function normalizeCommissionToDecimal(value: unknown, fallback = 0.08): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return fallback;
  return numeric > 1 ? numeric / 100 : numeric;
}

export function readRecargaTxLimit(remote: RemoteUser): number | null {
  const raw = remote.recargaTx ?? remote.recargaTxLimit ?? remote.rechargeTxLimit;
  const numeric = Number(raw);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
}

export function mapRemoteUserToAccount(remote: RemoteUser): UserAccount {
  const role = readUserRole(remote.role);
  const active = remote.activo !== false && remote.active !== false && remote.blocked !== true;

  return {
    ...remote,
    id: readString(remote.id, readString(remote.userId, readString(remote.user))),
    user: readString(remote.user, readString(remote.username)),
    role,
    displayName: readString(remote.displayName, readString(remote.nombre, readString(remote.name, readString(remote.user)))),
    ownerName: readString(remote.ownerName, readString(remote.own, readString(remote.nombre))),
    address: readString(remote.address, readString(remote.addr)),
    active,
    adminId: readString(remote.adminId) || null,
    adminUser: readString(remote.adminUser) || null,
    banca: readString(remote.banca) || null,
    cashierPrefix: readString(remote.cashierPrefix, readString(remote.cajPrefix)) || null,
    createdLabel: readString(remote.createdLabel, readString(remote.creado)) || null,
    territory: readString(remote.territory, 'RD'),
    phone: readString(remote.phone, readString(remote.tel)) || null,
    balance: readNumber(remote.balance),
    rechargesEnabled: Boolean(remote.rechargesEnabled ?? remote.recargasEnabled),
    rechargesAssignedBalance: readNumber(remote.rechargesAssignedBalance ?? remote.recargasAssignedBalance),
    rechargesBalance: readNumber(remote.rechargesBalance ?? remote.recargasBalance),
    recargasRapidasUsername: readString(remote.recargasRapidasUsername) || null,
    recargasRapidasPassword: readString(remote.recargasRapidasPassword) || null,
    commissionRate: normalizeCommissionToPercent(remote.commissionRate),
    recargaTxLimit: readRecargaTxLimit(remote),
    supervisorIds: readStringArray(remote.supervisorIds),
    supervisorUsers: readStringArray(remote.supervisorUsers),
    lastSeenAtEpochMs: readNumber(remote.lastSeenAtEpochMs ?? remote.lastSeenAt ?? remote.updatedAt, Date.now()),
    authUserId: readString(remote.authUserId) || null,
    credChangedAtEpochMs: readOptionalNumber(remote.credChangedAtEpochMs),
    passwordSalt: readString(remote.passwordSalt) || null,
    passwordHash: readString(remote.passwordHash) || null,
    passwordVersion: readString(remote.passwordVersion) || null,
    updatedAtEpochMs: readOptionalNumber(remote.updatedAtEpochMs ?? remote.updatedAt),
    systemModeOverride: readString(remote.systemModeOverride) || null,
    limitsPayload: readString(remote.limitsPayload) || null,
  };
}

export function mapAccountToRemoteUser(account: UserAccount): RemoteUser {
  return {
    ...account,
    userId: account.id,
    username: account.user,
    role: account.role,
    nombre: account.displayName,
    own: account.ownerName,
    addr: account.address,
    activo: account.active,
    blocked: !account.active,
    cajPrefix: account.cashierPrefix,
    creado: account.createdLabel,
    tel: account.phone,
    commissionRate: normalizeCommissionToDecimal(account.commissionRate),
    recargaTx: account.recargaTxLimit ?? null,
    recargaTxLimit: account.recargaTxLimit ?? null,
    lastSeenAt: account.lastSeenAtEpochMs || Date.now(),
    updatedAt: Date.now(),
  };
}
