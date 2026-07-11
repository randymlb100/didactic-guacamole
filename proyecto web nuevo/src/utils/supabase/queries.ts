import type { UserAccount, TicketRecord, LotteryCatalogItem, AuditLog, DrawResult, SportsTicketRecord } from '../../types';
import { isSupabaseConfigured, supabase } from '../supabaseClient';
import { getValidAccessToken } from '../authSession';
import { buildMasterConfigKey, getMasterConfig } from '../masterConfig';
import { getTodayResultDateKeyDR, sameResultDay, toResultCacheDateKey } from '../resultDates';
import { mapRemoteUserToAccount } from '../userMapping';
import { STATIC_LOTTERIES } from './lotteries';
import { initMockDatabase } from './config';

const isDev = import.meta.env.DEV;
const logWarn = (...args: unknown[]) => { if (isDev) console.warn(...args); };

const readLocalArray = <T>(key: string): T[] => {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const writeLocalArray = <T>(key: string, value: T[]) => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {}
};

const TICKET_FETCH_CACHE_MS = 30_000;
let ticketFetchCache:
  | {
      key: string;
      at: number;
      data: TicketRecord[];
      promise: Promise<TicketRecord[]> | null;
    }
  | null = null;

const retryQuery = async <T>(fn: () => Promise<T>, retries = 2, delay = 1000): Promise<T> => {
  try {
    return await fn();
  } catch (error: any) {
    if (retries <= 0) throw error;
    // Don't retry on client HTTP errors (400-499)
    if (error && (error.name === 'FunctionsHttpError' || error.status)) {
      const status = error.status || error.context?.status;
      if (typeof status === 'number' && status >= 400 && status < 500) {
        throw error;
      }
    }
    await new Promise(resolve => setTimeout(resolve, delay));
    return retryQuery(fn, retries - 1, delay * 2);
  }
};

// USER CRUD HELPERS
const accountArray = (value: unknown): Record<string, unknown>[] => Array.isArray(value) ? value as Record<string, unknown>[] : [];

const normalizeRemoteUsersPayload = (payload: any): any[] => {
  if (Array.isArray(payload?.users)) return payload.users;
  const supervisors = [
    ...accountArray(payload?.supervisores),
    ...accountArray(payload?.supervisors),
  ];
  const merged = [
    ...accountArray(payload?.admins),
    ...supervisors,
    ...accountArray(payload?.cajeros),
  ];
  const seen = new Set<string>();
  return merged.filter((user) => {
    const key = String(user.id || user.user || user.username || '').trim().toLowerCase();
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

export const fetchUsers = async (): Promise<UserAccount[]> => {
  if (isSupabaseConfigured && supabase) {
    const client = supabase;
    try {
      const queryFn = async () => {
        const { data, error } = await client.functions.invoke('lotterynet-users-state', {
          method: 'GET',
        });
        if (error) throw error;
        return data;
      };
      const data = await retryQuery(queryFn, 2, 1000);
      if (data?.payload) {
        const rawUsers = normalizeRemoteUsersPayload(data.payload);
        if (rawUsers.length === 0) return [];
        const mappedUsers = rawUsers.map(mapRemoteUserToAccount);
        writeLocalArray('lotterynet_users', mappedUsers);
        return mappedUsers;
      }
    } catch (e) {
      logWarn('Failed to fetch users from Supabase Edge Function, loading mock users', e);
    }
  }

  initMockDatabase();
  return readLocalArray<UserAccount>('lotterynet_users');
};

// TICKETS
export const fetchTickets = async (adminId?: string, cachedUsers?: UserAccount[]): Promise<TicketRecord[]> => {
  if (isSupabaseConfigured && supabase) {
    const client = supabase;
    try {
      const users = cachedUsers ?? readLocalArray<UserAccount>('lotterynet_users');
      
      let ownerKey = adminId || '';
      if (!ownerKey) {
        try {
          const savedUser = localStorage.getItem('lotterynet_session_user');
          const parsed = savedUser ? JSON.parse(savedUser) : null;
          ownerKey = parsed?.adminId || parsed?.id || '';
        } catch {}
      }

      if (!ownerKey) {
        logWarn('fetchTickets called without ownerKey/adminId and no session fallback');
        return [];
      }

      const cacheKey = ownerKey.trim().toLowerCase();
      if (
        ticketFetchCache &&
        ticketFetchCache.key === cacheKey &&
        ticketFetchCache.promise
      ) {
        return ticketFetchCache.promise;
      }
      if (
        ticketFetchCache &&
        ticketFetchCache.key === cacheKey &&
        Date.now() - ticketFetchCache.at < TICKET_FETCH_CACHE_MS
      ) {
        return ticketFetchCache.data;
      }

      const accessToken = getValidAccessToken();
      const headers: Record<string, string> = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const queryFn = async (): Promise<TicketRecord[]> => {
        const { data, error } = await client.functions.invoke('get-ticket-list', {
          headers,
          body: {
            action: 'fetch',
            ownerKey,
            includeOfficialStamp: true,
            preferSnapshot: true,
            processPendingPrizes: false
          }
        });
        if (error) throw error;
        const rawTickets = data?.payload?.tickets || data?.payload?.items || data?.payload?.data || data?.payload;
        if (!Array.isArray(rawTickets)) return readLocalArray<TicketRecord>('lotterynet_tickets');
        const allTickets: TicketRecord[] = [];
        for (const t of rawTickets) {
          const seller = users.find(u => u.id === t.cajeroId || u.id === t.vendedorId || u.user === t.cajeroId || u.user === t.vendedorId);
          const admin = users.find(u => u.id === t.adminId);
          
          const plays = (t.items || []).map((item: any) => ({
            number: item.nums || item.number || '',
            playType: item.type || item.playType || 'Q',
            amount: Number(item.amt || item.amount || 0),
            lotteryId: item.lotId || item.lotteryId || '',
            lotteryName: item.lotName || item.lotteryName || '',
          }));
          
          allTickets.push({
            id: t.id || '',
            serial: t.serial || t.id || '',
            securityCode: t.securityCode || '',
            sellerId: t.cajeroId || t.vendedorId || '',
            sellerUser: seller ? seller.user : (t.vendedorNombre || t.vendedorId || 'cajero'),
            adminId: t.adminId || '',
            adminUser: admin ? admin.user : (t.adminUser || 'admin'),
            role: ((t.vendedorRol || 'cashier').toUpperCase()) as any,
            createdAtEpochMs: t.createdAtMs || t.createdAtEpochMs || Date.now(),
            drawDateKey: t.drawDateKey || t.drawDate || t.dayKey || t.date || '',
            plays,
            subtotal: Number(t.subtotal ?? t.tot ?? t.total ?? 0),
            discount: Number(t.discount ?? 0),
            total: Number(t.total ?? t.tot ?? 0),
            totalPrize: Number(t.totalPrize ?? t.totalPremio ?? 0),
            winningDetails: t.winningDetails || [],
            status: t.status || t.st || 'active',
            note: t.note || null,
          });
        }
        allTickets.sort((a, b) => b.createdAtEpochMs - a.createdAtEpochMs);
        writeLocalArray('lotterynet_tickets', allTickets);
        return allTickets;
      };

      const promise = retryQuery(queryFn, 2, 1000)
        .then((result) => {
          ticketFetchCache = { key: cacheKey, at: Date.now(), data: result, promise: null };
          return result;
        })
        .catch((error) => {
          if (ticketFetchCache?.key === cacheKey) ticketFetchCache = null;
          throw error;
        });
      ticketFetchCache = {
        key: cacheKey,
        at: Date.now(),
        data: ticketFetchCache?.key === cacheKey ? ticketFetchCache.data : readLocalArray<TicketRecord>('lotterynet_tickets'),
        promise,
      };
      return promise;
    } catch (ticketsErr) {
      logWarn('Failed to fetch tickets from Supabase Edge Function', ticketsErr);
    }
  }

  initMockDatabase();
  return readLocalArray<TicketRecord>('lotterynet_tickets');
};

export const fetchLotteries = async (): Promise<LotteryCatalogItem[]> => {
  return STATIC_LOTTERIES;
};

// AUDIT LOGS
export const fetchAuditLogs = async (): Promise<AuditLog[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      const accessToken = getValidAccessToken();
      const headers: Record<string, string> = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const { data, error } = await supabase.functions.invoke('get-master-config', {
        headers,
        body: { key: 'sys_audit_v4' }
      });

      if (!error && data && data.payload) {
        const parsed = data.payload;
        if (Array.isArray(parsed)) {
          const auditRows = parsed.map((item: any, index: number) => {
            const timestampMs = item.timestampMs || (item.ts ? Date.parse(item.ts.replace(/(\d{2})\/(\d{2})\/(\d{4})/, '$3-$2-$1')) : Date.now()) || Date.now();
            return {
              id: item.id || `AUD-KV-${index}-${timestampMs}`,
              timestampMs,
              actorId: item.actorId || '',
              actorUser: item.actorUser || item.user || 'system',
              role: item.role || 'system',
              action: item.action || item.accion || 'UNKNOWN',
              details: item.details || item.detalle || '',
              ipAddress: item.ipAddress || '127.0.0.1',
              status: item.status || 'success'
            } as AuditLog;
          });
          writeLocalArray('lotterynet_audits', auditRows);
          return auditRows;
        }
      }
    } catch (e) {
      logWarn('Failed to fetch audits from Supabase Edge Function', e);
    }
  }

  initMockDatabase();
  return readLocalArray<AuditLog>('lotterynet_audits');
};

// LIMITS
export const getAdminLimitsPayload = async (adminId: string): Promise<string> => {
  const users = await fetchUsers();
  const admin = users.find(u => u.id === adminId && u.role === 'ADMIN');
  if (admin && admin.limitsPayload) {
    return admin.limitsPayload;
  }
  
  const localVal = localStorage.getItem(`lotterynet_limits_${adminId}`);
  if (localVal) return localVal;
  
  const defaultLimits = {
    defaults: {
      daySale: 10000.0,
      payout: 0.0,
      q: 10000.0,
      pale: 500.0,
      sp: 500.0,
      t: 75.0,
      p3: 500.0,
      p3box: 500.0,
      p4: 500.0,
      p4box: 500.0
    },
    byUser: {},
    adminSelf: {
      daySale: 0.0,
      payout: 0.0,
      q: 0.0,
      pale: 0.0,
      sp: 0.0,
      t: 0.0,
      p3: 0.0,
      p3box: 0.0,
      p4: 0.0,
      p4box: 0.0
    }
  };
  return JSON.stringify(defaultLimits);
};

// PAYOUTS
export interface PrizeTableConfig {
  q1: number;
  q2: number;
  q3: number;
  pale: number;
  pale12: number;
  pale13: number;
  pale23: number;
  tripleta: number;
  tripleta3: number;
  tripleta2: number;
  superPale: number;
  pick3Straight: number;
  pick3Box3: number;
  pick3Box6: number;
  pick4Straight: number;
  pick4Box4: number;
  pick4Box6: number;
  pick4Box12: number;
  pick4Box24: number;
  pick3BackPair: number;
  pick4BackPair: number;
}

export interface PayoutsPayload {
  defaults: PrizeTableConfig;
  byUser: Record<string, PrizeTableConfig>;
}

export const getAdminPayoutsPayload = async (adminId: string): Promise<string> => {
  const defaultPayouts: PayoutsPayload = {
    defaults: {
      q1: 60, q2: 12, q3: 4,
      pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
      tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
      superPale: 3000,
      pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
      pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
    },
    byUser: {}
  };

  if (isSupabaseConfigured && supabase) {
    try {
      const masterPayload = await getMasterConfig<PayoutsPayload | null>(
        buildMasterConfigKey('cashier_prize_payouts', adminId),
        null
      );
      if (masterPayload) {
        return JSON.stringify({
          defaults: { ...defaultPayouts.defaults, ...masterPayload.defaults },
          byUser: masterPayload.byUser || {}
        });
      }
    } catch (e) {
      logWarn(`Failed to fetch cashier_prize_payouts:${adminId} from Supabase`, e);
    }
  }

  const localVal = localStorage.getItem(`lotterynet_payouts_${adminId}`);
  if (localVal) return localVal;
  return JSON.stringify(defaultPayouts);
};

// DRAW RESULTS HELPERS & FETCH
const normalizeResultNumbers = (row: any): string => {
  const arrayLike = row.numbers || row.n || row.result || row.results || row.winningNumbers || row.digits;
  if (Array.isArray(arrayLike)) {
    const joined = arrayLike.map((part: unknown) => String(part).trim()).filter(Boolean).join('-');
    if (joined) return joined;
  }

  const compact = String(row.number || row.numero || row.numbers || row.result || row.results || row.pick3 || row.pick4 || '').trim();
  if (compact) return compact;

  const parts = [
    row.first || row.primera || row['1ra'] || row['1era'],
    row.second || row.segunda || row['2da'],
    row.third || row.tercera || row['3ra'],
  ].map((part) => String(part || '').trim()).filter(Boolean);

  return parts.join('-');
};

const extractResultsFromEdgePayload = (data: any, fallbackDateKey: string): DrawResult[] => {
  if (!data) return [];
  const results: DrawResult[] = [];
  const seenIds = new Set<string>();

  const processRows = (rows: any[], sourceKind: 'lot' | 'pick') => {
    if (!Array.isArray(rows)) return;
    for (const r of rows) {
      const lotteryId = String(r.id || r.lotteryId || '').trim();
      const numbers = normalizeResultNumbers(r);
      if (!lotteryId || !numbers) continue;

      const dateKey = toResultCacheDateKey(String(r.date || r.dateKey || fallbackDateKey));
      const uniqueId = `${sourceKind}:${lotteryId}:${dateKey}`;
      if (seenIds.has(uniqueId)) continue;
      seenIds.add(uniqueId);

      results.push({
        id: uniqueId,
        lotteryId,
        lotteryName: String(r.name || r.lotteryName || lotteryId),
        dateKey,
        numbers,
      });
    }
  };

  if (Array.isArray(data)) {
    processRows(data, 'lot');
    return results;
  }

  if (data && typeof data === 'object') {
    const payload = data.payload !== undefined ? data.payload : data;
    
    if (payload.lotteries?.results || payload.picks?.results) {
      if (payload.lotteries?.results) {
        processRows(payload.lotteries.results, 'lot');
      }
      if (payload.picks?.results) {
        processRows(payload.picks.results, 'pick');
      }
      return results;
    }

    const rows = payload.results || payload.rows || payload.data;
    if (Array.isArray(rows)) {
      processRows(rows, 'lot');
    } else if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
      if (payload.id || payload.lotteryId) {
        processRows([payload], 'lot');
      }
    }
  }

  return results;
};

export const fetchDrawResults = async (dateOverride?: string): Promise<DrawResult[]> => {
  const dateKey = toResultCacheDateKey(dateOverride || getTodayResultDateKeyDR());

  if (isSupabaseConfigured && supabase) {
    try {
      const accessToken = getValidAccessToken();
      const headers: Record<string, string> = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Convert date format from YYYY-MM-DD to DD-MM-YYYY if needed for scraper compatibility
      let reqDate = dateKey;
      const parts = dateKey.split('-');
      if (parts.length === 3 && parts[0].length === 4) {
        reqDate = `${parts[2]}-${parts[1]}-${parts[0]}`;
      }

      const { data, error } = await supabase.functions.invoke('get-results-v2', {
        headers,
        body: { date: reqDate }
      });

      if (!error && data) {
        const results = extractResultsFromEdgePayload(data, dateKey);
        if (results.length > 0) {
          const cached = readLocalArray<DrawResult>('lotterynet_results');
          const next = [
            ...results,
            ...cached.filter((result) => !sameResultDay(result.dateKey, dateKey)),
          ];
          writeLocalArray('lotterynet_results', next);
          return results;
        }
      }
    } catch (e) {
      logWarn('Failed to fetch draw results from Supabase Edge Function get-results-v2', e);
    }
  }

  const saved = localStorage.getItem('lotterynet_results');
  if (saved) {
    const parsed = readLocalArray<DrawResult>('lotterynet_results');
    return parsed.filter((result: DrawResult) => sameResultDay(result.dateKey, dateKey));
  }
  return [];
};

// SYSTEM MODE CONFIG
export interface BlockedSalePlay {
  playType: string;
  number: string;
}

export interface AdminSystemModeConfig {
  posLiteEnabled: boolean;
  lotteryModeEnabled: boolean;
  pickModeEnabled: boolean;
  cashierPickEnabled: boolean;
  cashierModeEnabled: boolean;
  cashierLotteryModeEnabled: boolean;
  cashierPickModeEnabled: boolean;
  blockedSalePlays: BlockedSalePlay[];
  updatedAt: number;
}

export const getAdminSystemModeConfig = async (adminId: string): Promise<AdminSystemModeConfig> => {
  const defaultVal: AdminSystemModeConfig = {
    posLiteEnabled: false,
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierPickEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    blockedSalePlays: [],
    updatedAt: Date.now()
  };

  if (isSupabaseConfigured && supabase) {
    try {
      const masterPayload = await getMasterConfig<Partial<AdminSystemModeConfig> | null>(
        buildMasterConfigKey('system_modes', adminId),
        null
      );
      if (masterPayload) {
        return {
          ...defaultVal,
          ...masterPayload,
          blockedSalePlays: Array.isArray(masterPayload.blockedSalePlays) ? masterPayload.blockedSalePlays : []
        };
      }
    } catch (e) {
      logWarn(`Failed to fetch system_modes:${adminId} from Supabase, returning default`, e);
    }
  }

  const localVal = localStorage.getItem(`lotterynet_system_modes_${adminId}`);
  if (localVal) {
    try {
      return JSON.parse(localVal);
    } catch (e) {
      return defaultVal;
    }
  }
  return defaultVal;
};

// MANUAL DISABLED LOTTERIES
export interface ManualDisabledLotteryConfig {
  ids: string[];
  date: string;
  permanent: boolean;
  updatedAt: number;
}

export const getManualDisabledLotteries = async (adminId: string): Promise<ManualDisabledLotteryConfig> => {
  const todayStr = new Intl.DateTimeFormat('fr-CA', { timeZone: 'America/Santo_Domingo' }).format(new Date());
  const defaultVal: ManualDisabledLotteryConfig = {
    ids: [],
    date: todayStr,
    permanent: false,
    updatedAt: Date.now()
  };

  if (isSupabaseConfigured && supabase) {
    try {
      const masterPayload = await getMasterConfig<Partial<ManualDisabledLotteryConfig> | null>(
        buildMasterConfigKey('manual_disabled_lotteries', adminId),
        null
      );
      if (masterPayload) {
        const isStillActive = masterPayload.permanent || masterPayload.date === todayStr;
        if (isStillActive) {
          return {
            ...defaultVal,
            ...masterPayload,
            ids: Array.isArray(masterPayload.ids) ? masterPayload.ids : []
          };
        }
        return defaultVal;
      }
    } catch (e) {
      logWarn(`Failed to fetch manual_disabled_lotteries:${adminId} from Supabase, returning default`, e);
    }
  }

  const localVal = localStorage.getItem(`lotterynet_manual_disabled_lotteries_${adminId}`);
  if (localVal) {
    try {
      const parsed = JSON.parse(localVal);
      const isStillActive = parsed.permanent || parsed.date === todayStr;
      if (isStillActive) return parsed;
    } catch (e) {}
  }
  return defaultVal;
};

// SPORTSBOOK
export interface SportsLimitConfig {
  scope_key: string;
  max_ticket_stake: number;
  max_selection_stake: number;
  max_event_exposure: number;
  max_potential_payout: number;
  enabled_markets: string[];
  updated_by?: string;
  updated_at?: string;
}

export const fetchSportsTickets = async (adminId?: string): Promise<SportsTicketRecord[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      let query = supabase
        .from('sports_tickets')
        .select('*, sports_ticket_legs(*)');
      
      if (adminId) {
        query = query.eq('admin_key', adminId);
      }
      
      const { data, error } = await query.order('sold_at', { ascending: false });
      if (error) throw error;
      
      if (data) {
        const mappedTickets = data.map((t: any) => ({
          id: t.id,
          ticketCode: t.ticket_code,
          sellerUsername: t.seller_username,
          bancaName: t.banca_name,
          ticketType: t.ticket_type,
          stake: Number(t.stake),
          decimalOdds: Number(t.decimal_odds),
          potentialPayout: Number(t.potential_payout),
          status: t.status,
          soldAt: t.sold_at,
          adminKey: t.admin_key,
          ownerKey: t.owner_key,
          legs: (t.sports_ticket_legs || []).map((leg: any) => ({
            eventLabel: leg.event_label,
            marketTitle: leg.market_title,
            selectionLabel: leg.selection_label,
            decimalOdds: Number(leg.decimal_odds),
            status: leg.status,
          }))
        }));
        writeLocalArray('lotterynet_sports_tickets', mappedTickets);
        return mappedTickets;
      }
    } catch (e) {
      logWarn('Failed to fetch sports tickets from Supabase', e);
    }
  }
  
  return readLocalArray<SportsTicketRecord>('lotterynet_sports_tickets');
};

export const getSportsLimits = async (scopeKey: string): Promise<SportsLimitConfig> => {
  const defaultVal: SportsLimitConfig = {
    scope_key: scopeKey,
    max_ticket_stake: 10000,
    max_selection_stake: 5000,
    max_event_exposure: 50000,
    max_potential_payout: 100000,
    enabled_markets: ['moneyline', 'runline', 'spread', 'total', 'first_half', 'first_five']
  };

  if (isSupabaseConfigured && supabase) {
    try {
      const { data, error } = await supabase
        .from('sports_limits')
        .select('*')
        .eq('scope_key', scopeKey)
        .maybeSingle();

      if (!error && data) {
        return {
          scope_key: data.scope_key,
          max_ticket_stake: Number(data.max_ticket_stake),
          max_selection_stake: Number(data.max_selection_stake),
          max_event_exposure: Number(data.max_event_exposure),
          max_potential_payout: Number(data.max_potential_payout),
          enabled_markets: Array.isArray(data.enabled_markets) ? data.enabled_markets : defaultVal.enabled_markets
        };
      }
    } catch (e) {
      logWarn(`Failed to fetch sports limits for ${scopeKey} from Supabase, returning default`, e);
    }
  }

  const localVal = localStorage.getItem(`lotterynet_sports_limits_${scopeKey}`);
  if (localVal) {
    try {
      return JSON.parse(localVal);
    } catch (e) {
      return defaultVal;
    }
  }
  return defaultVal;
};
