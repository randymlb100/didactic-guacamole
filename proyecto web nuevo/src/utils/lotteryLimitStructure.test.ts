import { describe, expect, it } from 'vitest';
import { resolveEffectiveLotteryLimit, type LotteryLimitStructure } from './lotteryLimitStructure';

const limits: LotteryLimitStructure = {
  global: { maxTicketAmount: 1000, maxPlayAmount: 200 },
  byLottery: {
    'lot-nacional': { maxTicketAmount: 700, maxPlayAmount: 100 },
  },
  byCashier: {
    'caj-1': { maxTicketAmount: 500 },
  },
  byPlay: {
    'lot-nacional:quiniela:12': { maxPlayAmount: 25 },
  },
};

describe('lotteryLimitStructure', () => {
  it('resolves limits by priority play > cashier > lottery > global', () => {
    expect(resolveEffectiveLotteryLimit(limits, {
      lotteryId: 'lot-nacional',
      cashierId: 'caj-1',
      playType: 'quiniela',
      playValue: '12',
    })).toEqual({
      maxTicketAmount: 500,
      maxPlayAmount: 25,
    });
  });

  it('falls back to lottery and global when cashier/play are missing', () => {
    expect(resolveEffectiveLotteryLimit(limits, {
      lotteryId: 'lot-nacional',
      cashierId: 'caj-2',
      playType: 'pale',
      playValue: '12-34',
    })).toEqual({
      maxTicketAmount: 700,
      maxPlayAmount: 100,
    });
  });
});
