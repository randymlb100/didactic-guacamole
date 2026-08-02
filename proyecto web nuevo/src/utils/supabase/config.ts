import type { UserAccount, AuditLog, TicketRecord } from '../../types';
import { isSupabaseConfigured, supabase } from '../supabaseClient';
import { clearUsersFetchCache, fetchAuditLogs } from './queries';
import { STATIC_LOTTERIES } from './lotteries';
import { mapAccountToRemoteUser } from '../userMapping';
import { getValidAccessToken } from '../authSession';

const isDev = import.meta.env.DEV;
const logError = (...args: unknown[]) => { if (isDev) console.error(...args); };

const authHeaders = (): Record<string, string> => {
  const accessToken = getValidAccessToken();
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
};

export const INITIAL_USERS: UserAccount[] = [
  {
    id: 'USR-MASTER-01',
    user: 'master',
    role: 'MASTER',
    displayName: 'Randy Cordero',
    ownerName: 'Randy Cordero',
    active: true,
    createdLabel: '12/05/2026 09:30 AM',
    phone: '809-555-0100',
    balance: 1548200.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 3600000,
  },
  {
    id: 'ADM-BANCA-REAL',
    user: 'bancareal',
    role: 'ADMIN',
    displayName: 'Juan Pérez',
    ownerName: 'Juan Pérez',
    address: 'Av. Winston Churchill, Santo Domingo',
    active: true,
    banca: 'Banca Real Churchill',
    cashierPrefix: 'chu',
    createdLabel: '14/05/2026 10:15 AM',
    territory: 'RD',
    phone: '829-555-0112',
    balance: 85200.0,
    rechargesEnabled: true,
    rechargesAssignedBalance: 50000.0,
    rechargesBalance: 12500.0,
    commissionRate: 8.0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 120000,
  },
  {
    id: 'ADM-BANCA-ESTRELLA',
    user: 'bancaestrella',
    role: 'ADMIN',
    displayName: 'María Rodríguez',
    ownerName: 'María Rodríguez',
    address: 'Calle El Sol, Santiago',
    active: false, // blocked
    banca: 'Banca Estrella Santiago',
    cashierPrefix: 'est',
    createdLabel: '15/05/2026 02:40 PM',
    territory: 'RD',
    phone: '809-555-0189',
    balance: -4200.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    commissionRate: 7.5,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 86400000 * 3,
  },
  {
    id: 'USR-SUP-01',
    user: 'supechurchill',
    role: 'SUPERVISOR',
    displayName: 'Carlos Gómez',
    ownerName: 'Carlos Gómez',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '16/05/2026 08:30 AM',
    territory: 'RD',
    phone: '809-555-0199',
    balance: 0.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 1500000,
  },
  {
    id: 'CAJ-CHU-01',
    user: 'chu01',
    role: 'CASHIER',
    displayName: 'Cajero 01 - Banca Real Churchill',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '14/05/2026 10:30 AM',
    territory: 'RD',
    balance: 3200.0,
    rechargesEnabled: true,
    rechargesAssignedBalance: 10000.0,
    rechargesBalance: 4500.0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 300000,
  },
  {
    id: 'CAJ-CHU-02',
    user: 'chu02',
    role: 'CASHIER',
    displayName: 'Cajero 02 - Banca Real Churchill',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '14/05/2026 10:32 AM',
    territory: 'RD',
    balance: 1450.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 10000000,
  },
  {
    id: 'CAJ-EST-01',
    user: 'est01',
    role: 'CASHIER',
    displayName: 'Cajero 01 - Banca Estrella Santiago',
    active: false, // blocked, because parent admin is blocked
    adminId: 'ADM-BANCA-ESTRELLA',
    adminUser: 'bancaestrella',
    banca: 'Banca Estrella Santiago',
    createdLabel: '15/05/2026 02:50 PM',
    territory: 'RD',
    balance: 0.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 86400000 * 3,
  },
];

export const INITIAL_TICKETS: TicketRecord[] = [
  {
    id: 'TK-100201',
    serial: 'A09F-D776-90B1',
    securityCode: '4998',
    sellerId: 'CAJ-CHU-01',
    sellerUser: 'chu01',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 1800000,
    drawDateKey: '2026-05-29',
    subtotal: 100.0,
    discount: 0.0,
    total: 100.0,
    totalPrize: 0.0,
    status: 'active',
    plays: [
      { number: '14', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
      { number: '22', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
    ],
    winningDetails: [],
  },
  {
    id: 'TK-100202',
    serial: 'BD89-CE34-45F2',
    securityCode: '2119',
    sellerId: 'CAJ-CHU-01',
    sellerUser: 'chu01',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 7200000,
    drawDateKey: '2026-05-29',
    subtotal: 250.0,
    discount: 0.0,
    total: 250.0,
    totalPrize: 1500.0,
    status: 'paid', // cobrado
    plays: [
      { number: '14-22', playType: 'pale', amount: 100.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
      { number: '05', playType: 'quiniela', amount: 150.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
    ],
    winningDetails: [
      {
        lotteryName: 'Real Tarde',
        playType: 'quiniela',
        playedNumber: '05',
        resultNumber: '05-18-42',
        hitPosition: 'primera',
        amount: 150.0,
        payoutAmount: 1500.0,
      },
    ],
  },
  {
    id: 'TK-100203',
    serial: 'F2D4-3298-AA9F',
    securityCode: '8701',
    sellerId: 'CAJ-CHU-02',
    sellerUser: 'chu02',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 3600000 * 5,
    drawDateKey: '2026-05-29',
    subtotal: 50.0,
    discount: 0.0,
    total: 50.0,
    totalPrize: 0.0,
    status: 'cancelled', // anulado
    plays: [
      { number: '88', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-GANAMAS', lotteryName: 'Gana Más' },
    ],
    winningDetails: [],
  },
];

export const INITIAL_AUDITS: AuditLog[] = [
  {
    id: 'AUD-001',
    timestampMs: Date.now() - 7200000,
    actorId: 'USR-MASTER-01',
    actorUser: 'master',
    role: 'MASTER',
    action: 'LOGIN_SUCCESS',
    details: 'Inicio de sesión exitoso desde panel web.',
    ipAddress: '186.6.120.45',
    status: 'success',
  },
  {
    id: 'AUD-002',
    timestampMs: Date.now() - 5400000,
    actorId: 'USR-MASTER-01',
    actorUser: 'master',
    role: 'MASTER',
    action: 'CREATE_BANK',
    details: 'Creada nueva banca: Banca Real Churchill, Admin: Juan Pérez.',
    ipAddress: '186.6.120.45',
    status: 'success',
  },
];

export const initMockDatabase = () => {
  if (!localStorage.getItem('lotterynet_users')) {
    localStorage.setItem('lotterynet_users', JSON.stringify(INITIAL_USERS));
  }
  if (!localStorage.getItem('lotterynet_tickets')) {
    localStorage.setItem('lotterynet_tickets', JSON.stringify(INITIAL_TICKETS));
  }
  if (!localStorage.getItem('lotterynet_lotteries')) {
    localStorage.setItem('lotterynet_lotteries', JSON.stringify(STATIC_LOTTERIES));
  }
  if (!localStorage.getItem('lotterynet_audits')) {
    localStorage.setItem('lotterynet_audits', JSON.stringify(INITIAL_AUDITS));
  }
};

export const addAuditLog = async (
  actor: { id: string; user: string; role: string },
  action: string,
  details: string,
  status: 'success' | 'failed' | 'warning' = 'success'
): Promise<void> => {
  const newLog: AuditLog = {
    id: `AUD-${Math.random().toString(36).substr(2, 9).toUpperCase()}`,
    timestampMs: Date.now(),
    actorId: actor.id,
    actorUser: actor.user,
    role: actor.role as any,
    action,
    details,
    ipAddress: '127.0.0.1',
    status,
  };

  const formattedDate = (() => {
    const d = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    let hours = d.getHours();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12;
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(hours)}:${pad(d.getMinutes())} ${ampm}`;
  })();

  const compatLog = {
    ...newLog,
    ts: formattedDate,
    user: actor.user,
    role: actor.role,
    accion: action,
    detalle: details
  };

  const canWriteGlobalAudit = String(actor.role || '').toUpperCase() === 'MASTER';

  if (canWriteGlobalAudit && isSupabaseConfigured && supabase) {
    try {
      const currentLogs = await fetchAuditLogs();
      const mergedLogs = [compatLog, ...currentLogs].slice(0, 100);

      const { error } = await supabase.functions.invoke('update-master-config', {
        headers: authHeaders(),
        body: { key: 'sys_audit_v4', payload: mergedLogs },
      });

      if (!error) return;
    } catch (e) {
      logError('Failed to insert audit through Edge Function, writing locally', e);
    }
  }

  const logs = JSON.parse(localStorage.getItem('lotterynet_audits') || '[]');
  logs.unshift(newLog);
  localStorage.setItem('lotterynet_audits', JSON.stringify(logs.slice(0, 100)));
};

export const saveAllUsers = async (users: UserAccount[]): Promise<void> => {
  if (isSupabaseConfigured && supabase) {
    try {
      const mappedUsers = users.map(mapAccountToRemoteUser);
      const { data: current, error: fetchError } = await supabase.functions.invoke('lotterynet-users-state', {
        method: 'GET',
      });
      if (fetchError) throw fetchError;
      const currentPayload = (current?.payload || {}) as Record<string, unknown>;
      const usesLegacyShape = !Array.isArray((currentPayload as any).users) &&
        (Array.isArray((currentPayload as any).admins) || Array.isArray((currentPayload as any).cajeros) || Array.isArray((currentPayload as any).supervisores));

      const payload = usesLegacyShape
        ? {
          ...currentPayload,
          admins: mappedUsers.filter((u: any) => String(u.role).toUpperCase() === 'ADMIN'),
          supervisores: mappedUsers.filter((u: any) => String(u.role).toUpperCase() === 'SUPERVISOR'),
          supervisors: mappedUsers.filter((u: any) => String(u.role).toUpperCase() === 'SUPERVISOR'),
          cajeros: mappedUsers.filter((u: any) => String(u.role).toUpperCase() === 'CASHIER'),
        }
        : { ...currentPayload, users: mappedUsers };
      const { error } = await supabase.functions.invoke('lotterynet-users-state', {
        headers: authHeaders(),
        body: { action: 'upsert', payload },
      });
      if (!error) {
        clearUsersFetchCache();
        return;
      }
    } catch (e) {
      logError('Failed to save through Edge Function, saving locally', e);
    }
  }

  localStorage.setItem('lotterynet_users', JSON.stringify(users));
  clearUsersFetchCache();
};
