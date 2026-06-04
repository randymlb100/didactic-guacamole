import React from 'react';
import { Activity, AlertTriangle, Percent, ReceiptText, Settings, Sliders, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';

interface Props {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  onOpen: (tab: string) => void;
}

export const AdminOperationsConsole: React.FC<Props> = ({ user, users, tickets, onOpen }) => {
  const cashiers = users.filter((candidate) => candidate.role === 'CASHIER' && (candidate.adminId === user.id || candidate.adminUser === user.user));
  const scopedTickets = tickets.filter((ticket) => ticket.adminId === user.id || ticket.adminUser === user.user || cashiers.some((cashier) => cashier.user === ticket.sellerUser));
  const sales = scopedTickets.filter((ticket) => ticket.status !== 'cancelled' && ticket.status !== 'voided').reduce((sum, ticket) => sum + Number(ticket.total || 0), 0);
  const pendingPrizes = scopedTickets.filter((ticket) => ticket.status === 'winner').reduce((sum, ticket) => sum + Number(ticket.totalPrize || 0), 0);

  const shortcuts = [
    { label: 'Monitor', tab: 'monitoreo', icon: Activity },
    { label: 'Comisiones', tab: 'comisiones', icon: Percent },
    { label: 'Límite venta cajeros', tab: 'limites', icon: Sliders },
    { label: 'Tickets de cajeros', tab: 'tickets', icon: ReceiptText },
    { label: 'Ganadores', tab: 'ganadores', icon: Trophy },
    { label: 'Caja', tab: 'cuadre', icon: ReceiptText },
    { label: 'Alertas', tab: 'auditoria', icon: AlertTriangle },
    { label: 'Usuarios', tab: 'cajeros', icon: Users },
    { label: 'Sistema', tab: 'limites', icon: Settings },
  ];

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h3 className="fintech-panel-title">Panel admin</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>{user.banca || 'Banca'} · resumen operativo</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <div className="glass-panel" style={{ padding: 14 }}>Cajeros<br /><strong>{cashiers.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Ventas<br /><strong>${sales.toFixed(2)}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Pendiente<br /><strong>${pendingPrizes.toFixed(2)}</strong></div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 10 }}>
        {shortcuts.map((shortcut) => {
          const Icon = shortcut.icon;
          return (
            <button key={shortcut.label} type="button" className="btn btn-secondary" onClick={() => onOpen(shortcut.tab)} style={{ justifyContent: 'flex-start' }}>
              <Icon size={16} />
              {shortcut.label}
            </button>
          );
        })}
      </div>
    </div>
  );
};
