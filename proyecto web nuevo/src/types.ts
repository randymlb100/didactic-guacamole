export type UserRole = 'MASTER' | 'ADMIN' | 'SUPERVISOR' | 'CASHIER' | 'UNKNOWN';

export interface UserAccount {
  id: string;
  user: string;
  role: UserRole;
  displayName?: string | null;
  ownerName?: string | null;
  address?: string | null;
  active: boolean;
  adminId?: string | null;
  adminUser?: string | null;
  banca?: string | null;
  cashierPrefix?: string | null;
  createdLabel?: string | null;
  territory?: string | null;
  phone?: string | null;
  balance: number;
  rechargesEnabled: boolean;
  rechargesAssignedBalance: number;
  rechargesBalance: number;
  recargasRapidasUsername?: string | null;
  recargasRapidasPassword?: string | null;
  commissionRate?: number | null;
  recargaTxLimit?: number | null;
  supervisorIds: string[];
  supervisorUsers: string[];
  lastSeenAtEpochMs?: number | null;
  authUserId?: string | null;
  credChangedAtEpochMs?: number | null;
  passwordSalt?: string | null;
  passwordHash?: string | null;
  passwordVersion?: string | null;
  updatedAtEpochMs?: number | null;
  systemModeOverride?: string | null;
  limitsPayload?: string | null;
}

export interface PlayItem {
  number: string;
  playType: string;
  amount: number;
  lotteryId?: string | null;
  lotteryName?: string | null;
  secondaryLotteryId?: string | null;
  secondaryLotteryName?: string | null;
}

export interface WinningPlayDetail {
  lotteryName: string;
  playType: string;
  playedNumber: string;
  resultNumber: string;
  hitPosition: string;
  amount: number;
  payoutAmount: number;
}

export interface TicketRecord {
  id: string;
  serial?: string | null;
  securityCode?: string | null;
  sellerId?: string | null;
  sellerUser?: string | null;
  adminId?: string | null;
  adminUser?: string | null;
  role: UserRole;
  createdAtEpochMs: number;
  drawDateKey?: string | null;
  plays: PlayItem[];
  subtotal: number;
  discount: number;
  total: number;
  totalPrize: number;
  winningDetails: WinningPlayDetail[];
  status: 'active' | 'paid' | 'winner' | 'cancelled' | string;
  note?: string | null;
}

export interface LotterySchedule {
  drawTime: string;
  closeTime: string;
}

export interface LotteryPlayCapabilities {
  supportsStraight: boolean;
  supportsBox: boolean;
  supportsQuiniela: boolean;
  supportsPale: boolean;
  supportsTripleta: boolean;
  supportsSuperPale: boolean;
}

export interface LotteryCatalogItem {
  id: string;
  name: string;
  type: string;
  baseDrawTime: string;
  baseCloseTime: string;
  colorHex: string;
  logoAssetPath?: string | null;
  territory: 'RD' | 'USA';
  timeZoneId?: string | null;
  playCapabilities: LotteryPlayCapabilities;
  sundayOverride?: LotterySchedule | null;
  standardTimeOverride?: LotterySchedule | null;
  usesExplicitCloseTime: boolean;
}

export interface AuditLog {
  id: string;
  timestampMs: number;
  actorId: string;
  actorUser: string;
  role: UserRole;
  action: string;
  details: string;
  ipAddress?: string;
  status: 'success' | 'failed' | 'warning';
}

export interface FinanceSummary {
  totalSales: number;
  totalPrizes: number;
  totalCommissions: number;
  netRevenue: number;
  activeTicketsCount: number;
  winningTicketsCount: number;
  paidTicketsCount: number;
  cancelledTicketsCount: number;
}

export interface DrawResult {
  id: string;
  lotteryId: string;
  lotteryName: string;
  dateKey: string;
  numbers: string;
}

export interface BlockedSalePlay {
  playType: string;
  number: string;
}

