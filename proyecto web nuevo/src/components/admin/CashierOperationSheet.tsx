import React, { useMemo, useState } from 'react';
import { X } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';
import { canEditCashierFromMonitoring, getCashierScopedTickets, getCashierScopedWinners } from '../../utils/cashierScope';
import { ActionButton, MetricCard, StatusBadge } from '../ui';
import { cn } from '../../utils/classNames';

interface Props {
  role: string;
  cashier: UserAccount | null;
  tickets: TicketRecord[];
  onClose: () => void;
  onOpenLimits: (cashier: UserAccount) => void;
  onOpenRecharge: (cashier: UserAccount) => void;
}

type SheetTab = 'summary' | 'tickets' | 'payouts' | 'limits' | 'recharges' | 'data';

export const CashierOperationSheet: React.FC<Props> = ({ role, cashier, tickets, onClose, onOpenLimits, onOpenRecharge }) => {
  const [tab, setTab] = useState<SheetTab>('summary');
  const scopedTickets = useMemo(() => (cashier ? getCashierScopedTickets(tickets, cashier) : []), [cashier, tickets]);
  const winners = useMemo(() => (cashier ? getCashierScopedWinners(tickets, cashier) : []), [cashier, tickets]);
  const canEdit = canEditCashierFromMonitoring(role);

  if (!cashier) return null;

  const sales = scopedTickets.reduce((sum, ticket) => sum + Number(ticket.total || 0), 0);
  const pendingPayout = winners.filter((ticket) => ticket.status !== 'paid').reduce((sum, ticket) => sum + Number(ticket.totalPrize || 0), 0);
  const tabs: Array<[SheetTab, string]> = [
    ['summary', 'Resumen'],
    ['tickets', 'Tickets'],
    ['payouts', 'Cobros'],
    ['limits', 'Límites'],
    ['recharges', 'Recargas'],
    ['data', 'Datos'],
  ];

  return (
    <div className="fixed inset-0 z-[100] flex justify-end bg-black/45" role="dialog" aria-modal="true">
      <section className="flex h-full w-[min(560px,100vw)] flex-col gap-4 overflow-y-auto rounded-l-ln-lg border border-ln-border bg-ln-surface/95 p-5 shadow-ln-lg backdrop-blur-md">
        <header className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-ln-text-primary">{cashier.displayName || cashier.user}</h3>
            <span className="text-sm text-ln-text-secondary">{cashier.banca || 'Banca'} · @{cashier.user}</span>
          </div>
          <ActionButton className="min-h-9 px-3" onClick={onClose} aria-label="Cerrar" icon={<X size={18} />}>
            Cerrar
          </ActionButton>
        </header>

        <nav className="flex flex-wrap gap-2">
          {tabs.map(([id, label]) => (
            <button
              key={id}
              type="button"
              className={cn(
                'min-h-9 rounded-ln-md border px-3 py-2 text-sm font-semibold transition-colors',
                tab === id
                  ? 'border-ln-primary bg-ln-primary text-white shadow-ln-md'
                  : 'border-ln-border bg-ln-surface text-ln-text-primary hover:bg-ln-surface-hover',
              )}
              onClick={() => setTab(id)}
            >
              {label}
            </button>
          ))}
        </nav>

        {tab === 'summary' && (
          <div className="grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-3">
            <MetricCard label="Tickets" value={scopedTickets.length} />
            <MetricCard label="Ventas" value={`$${sales.toFixed(2)}`} accent="success" />
            <MetricCard label="Pendiente" value={`$${pendingPayout.toFixed(2)}`} accent="warning" />
          </div>
        )}

        {tab === 'tickets' && (
          <div className="flex flex-col gap-3">
            {scopedTickets.length === 0 && <EmptySheetState text="No hay tickets para este cajero." />}
            {scopedTickets.map((ticket) => (
              <SheetRow key={ticket.id} title={ticket.serial || ticket.id} value={`$${Number(ticket.total || 0).toFixed(2)} · ${ticket.status}`} />
            ))}
          </div>
        )}

        {tab === 'payouts' && (
          <div className="flex flex-col gap-3">
            {winners.length === 0 && <EmptySheetState text="No hay cobros pendientes para este cajero." />}
            {winners.map((ticket) => (
              <SheetRow key={ticket.id} title={ticket.serial || ticket.id} value={`$${Number(ticket.totalPrize || 0).toFixed(2)} · ${ticket.status}`} />
            ))}
          </div>
        )}

        {tab === 'limits' && (
          <div className="flex items-center justify-between gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
            <span className="text-sm text-ln-text-primary">Límites operacionales de este cajero</span>
            <ActionButton variant="warning" disabled={!canEdit} onClick={() => onOpenLimits(cashier)}>Administrar</ActionButton>
          </div>
        )}

        {tab === 'recharges' && (
          <div className="flex items-center justify-between gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
            <span className="text-sm text-ln-text-primary">Cupo y acceso de recargas</span>
            <ActionButton variant="finance" disabled={!canEdit} onClick={() => onOpenRecharge(cashier)}>Administrar</ActionButton>
          </div>
        )}

        {tab === 'data' && (
          <div className="grid gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
            <span className="flex items-center justify-between gap-3">
              Estado:
              <StatusBadge tone={cashier.active === false ? 'danger' : 'success'}>{cashier.active === false ? 'Bloqueado' : 'Activo'}</StatusBadge>
            </span>
            <span>Balance: <strong>${Number(cashier.balance || 0).toFixed(2)}</strong></span>
            <span>Comisión: <strong>{Number(cashier.commissionRate || 0).toFixed(2)}%</strong></span>
          </div>
        )}
      </section>
    </div>
  );
};

const SheetRow = ({ title, value }: { title: string; value: string }) => (
  <div className="flex items-center justify-between gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-3">
    <strong className="min-w-0 truncate text-sm text-ln-text-primary">{title}</strong>
    <span className="shrink-0 text-sm text-ln-text-secondary">{value}</span>
  </div>
);

const EmptySheetState = ({ text }: { text: string }) => (
  <div className="rounded-ln-md border border-dashed border-ln-border bg-ln-background/45 p-4 text-center text-sm text-ln-text-secondary">
    {text}
  </div>
);
