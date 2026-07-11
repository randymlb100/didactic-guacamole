import type { TicketRecord, UserAccount, UserRole } from '../types';

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

export const getCashierScopedTickets = (tickets: TicketRecord[], cashier: UserAccount): TicketRecord[] => {
  const cashierKeys = new Set([cashier.id, cashier.user].map(normalizeKey).filter(Boolean));
  return tickets.filter((ticket) => {
    const rawTicket = ticket as TicketRecord & {
      cashierId?: string | null;
      cashierUser?: string | null;
      user?: string | null;
    };
    const ticketKeys = [rawTicket.cashierId, rawTicket.sellerUser, rawTicket.user, rawTicket.cashierUser]
      .map(normalizeKey)
      .filter(Boolean);
    return ticketKeys.some((key) => cashierKeys.has(key));
  });
};

export const getCashierScopedWinners = (tickets: TicketRecord[], cashier: UserAccount): TicketRecord[] => {
  return getCashierScopedTickets(tickets, cashier).filter((ticket) => {
    return ticket.status === 'winner' || ticket.status === 'paid' || Number(ticket.totalPrize || 0) > 0;
  });
};

export const canEditCashierFromMonitoring = (role: UserRole | string | null | undefined): boolean => {
  return String(role || '').toUpperCase() === 'ADMIN';
};
