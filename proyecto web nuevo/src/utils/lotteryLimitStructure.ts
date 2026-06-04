export interface LotteryLimitRule {
  maxTicketAmount?: number;
  maxPlayAmount?: number;
  maxPayoutAmount?: number;
  disabled?: boolean;
}

export interface LotteryLimitStructure {
  global: LotteryLimitRule;
  byLottery: Record<string, LotteryLimitRule>;
  byCashier: Record<string, LotteryLimitRule>;
  byPlay: Record<string, LotteryLimitRule>;
}

export interface LotteryLimitLookup {
  lotteryId?: string;
  cashierId?: string;
  playType?: string;
  playValue?: string;
}

export const emptyLotteryLimitStructure: LotteryLimitStructure = {
  global: {},
  byLottery: {},
  byCashier: {},
  byPlay: {},
};

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

export const buildPlayLimitKey = (lotteryId?: string, playType?: string, playValue?: string): string => {
  return [lotteryId, playType, playValue].map(normalizeKey).join(':');
};

export const resolveEffectiveLotteryLimit = (
  limits: LotteryLimitStructure,
  lookup: LotteryLimitLookup,
): LotteryLimitRule => {
  const lotteryRule = limits.byLottery[normalizeKey(lookup.lotteryId)] || {};
  const cashierRule = limits.byCashier[normalizeKey(lookup.cashierId)] || {};
  const playRule = limits.byPlay[buildPlayLimitKey(lookup.lotteryId, lookup.playType, lookup.playValue)] || {};

  return {
    ...limits.global,
    ...lotteryRule,
    ...cashierRule,
    ...playRule,
  };
};
