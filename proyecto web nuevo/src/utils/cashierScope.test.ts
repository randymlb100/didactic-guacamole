import { describe, expect, it } from 'vitest';
import type { TicketRecord, UserAccount } from '../types';
import { canEditCashierFromMonitoring, getCashierScopedTickets } from './cashierScope';

const cashier: UserAccount = {
  id: 'caj-1',
  user: 'caj01',
  role: 'CASHIER',
  displayName: 'Cajero Uno',
  active: true,
  balance: 0,
  rechargesEnabled: false,
  rechargesAssignedBalance: 0,
  rechargesBalance: 0,
  supervisorIds: [],
  supervisorUsers: [],
};

const tickets = [
  { id: 't1', sellerUser: 'caj01', total: 100, status: 'active' },
  { id: 't2', sellerUser: 'caj02', total: 200, status: 'active' },
  { id: 't3', cashierId: 'caj-1', sellerUser: 'otro', total: 300, status: 'winner' },
] as TicketRecord[];

describe('cashierScope', () => {
  it('filters tickets to the selected cashier only', () => {
    expect(getCashierScopedTickets(tickets, cashier).map((ticket) => ticket.id)).toEqual(['t1', 't3']);
  });

  it('lets ADMIN edit the selected cashier operational blocks but keeps SUPERVISOR read-only', () => {
    expect(canEditCashierFromMonitoring('ADMIN')).toBe(true);
    expect(canEditCashierFromMonitoring('SUPERVISOR')).toBe(false);
    expect(canEditCashierFromMonitoring('MASTER')).toBe(false);
  });
});
