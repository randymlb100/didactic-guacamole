import React from 'react';
import { Activity, BarChart3, ReceiptText, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';
import { ActionButton, MetricCard, Panel, PanelHeader } from '../ui';

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
    <Panel tone="primary" className="flex flex-col gap-4">
      <PanelHeader title="Supervisión" subtitle="Cajeros asignados y operación del grupo" />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-3">
        <MetricCard label="Mis cajeros" value={assignedCashiers.length} />
        <MetricCard label="Tickets grupo" value={scopedTickets.length} accent="primary" />
        <MetricCard label="Ventas grupo" value={`$${sales.toFixed(2)}`} accent="success" />
      </div>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] gap-3">
        {actions.map((action) => {
          const Icon = action.icon;
          return (
            <ActionButton key={action.label} variant="info" className="justify-start" icon={<Icon size={16} />} onClick={() => onOpen(action.tab)}>
              {action.label}
            </ActionButton>
          );
        })}
      </div>
    </Panel>
  );
};
