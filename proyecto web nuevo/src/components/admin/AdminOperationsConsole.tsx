import React from 'react';
import { Activity, AlertTriangle, Percent, ReceiptText, Settings, Sliders, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';
import { ActionButton, MetricCard, Panel, PanelHeader } from '../ui';

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
    <Panel tone="primary" className="flex flex-col gap-4">
      <PanelHeader title="Panel admin" subtitle={`${user.banca || 'Banca'} · resumen operativo`} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-3">
        <MetricCard label="Cajeros" value={cashiers.length} />
        <MetricCard label="Ventas" value={`$${sales.toFixed(2)}`} accent="success" />
        <MetricCard label="Pendiente" value={`$${pendingPrizes.toFixed(2)}`} accent="warning" />
      </div>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3">
        {shortcuts.map((shortcut) => {
          const Icon = shortcut.icon;
          return (
            <ActionButton key={shortcut.label} variant="info" onClick={() => onOpen(shortcut.tab)} className="justify-start" icon={<Icon size={16} />}>
              {shortcut.label}
            </ActionButton>
          );
        })}
      </div>
    </Panel>
  );
};
