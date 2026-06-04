import React from 'react';
import { Activity, BarChart3, ReceiptText, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';

interface Props {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  onOpen: (tab: string) => void;
}

export const SupervisorConsole: React.FC<Props> = ({ user, users, tickets, onOpen }) => {
  const assignedCashiers = users.filter((candidate) => candidate.role === 'CASHIER' && (
    candidate.supervisorIds.includes(user.id) || candidate.supervisorUsers.includes(user.user)
  ));
  const assignedUsers = new Set(assignedCashiers.map((cashier) => cashier.user));
  const scopedTickets = tickets.filter((ticket) => assignedUsers.has(ticket.sellerUser || ''));
  const sales = scopedTickets.filter((ticket) => ticket.status !== 'cancelled' && ticket.status !== 'voided').reduce((sum, ticket) => sum + Number(ticket.total || 0), 0);

  const actions = [
    { label: 'Mis cajeros', tab: 'monitoreo', icon: Users },
    { label: 'Monitoreo', tab: 'monitoreo', icon: Activity },
    { label: 'Finanzas', tab: 'finanzas', icon: BarChart3 },
    { label: 'Reporte', tab: 'reportes', icon: BarChart3 },
    { label: 'Tickets', tab: 'tickets', icon: ReceiptText },
    { label: 'Resultados', tab: 'resultados', icon: Trophy },
  ];

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h3 className="fintech-panel-title">Supervisión</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Cajeros asignados y operación del grupo</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <div className="glass-panel" style={{ padding: 14 }}>Mis cajeros<br /><strong>{assignedCashiers.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Tickets grupo<br /><strong>{scopedTickets.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Ventas grupo<br /><strong>${sales.toFixed(2)}</strong></div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10 }}>
        {actions.map((action) => {
          const Icon = action.icon;
          return (
            <button key={action.label} type="button" className="btn btn-secondary" onClick={() => onOpen(action.tab)} style={{ justifyContent: 'flex-start' }}>
              <Icon size={16} />
              {action.label}
            </button>
          );
        })}
      </div>
    </div>
  );
};
