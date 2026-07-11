import React, { useState } from 'react';
import type { TicketRecord, UserAccount } from '../../types';
import { DataToolbar, MetricCard, Panel, StatusBadge, TableActionButton } from '../../components/ui';
import { cn } from '../../utils/classNames';

interface GanadoresTabProps {
  user: UserAccount;
  tickets: TicketRecord[];
  handlePayWinner: (ticket: TicketRecord) => Promise<void>;
}

export const GanadoresTab: React.FC<GanadoresTabProps> = ({
  user,
  tickets,
  handlePayWinner
}) => {
  const [ganadoresFilter, setGanadoresFilter] = useState<'pending' | 'paid' | 'all'>('pending');

  const adminTickets = tickets.filter(
    (t) =>
      (user.role === 'MASTER' ? true : t.adminId === user.id) &&
      (t.status === 'winner' || t.status === 'paid' || t.totalPrize > 0)
  );

  const pendingWinners = adminTickets.filter(
    (t) => t.status === 'winner' || (t.status !== 'paid' && t.totalPrize > 0)
  );
  const paidWinners = adminTickets.filter((t) => t.status === 'paid');

  const pendingPrizeSum = pendingWinners.reduce((acc, t) => acc + t.totalPrize, 0);
  const paidPrizeSum = paidWinners.reduce((acc, t) => acc + t.totalPrize, 0);

  const filtered = adminTickets.filter((t) => {
    if (ganadoresFilter === 'pending') return t.status === 'winner' || (t.status !== 'paid' && t.totalPrize > 0);
    if (ganadoresFilter === 'paid') return t.status === 'paid';
    return true;
  });

  const filters = [
    { key: 'pending', label: 'Pendientes de Pago' },
    { key: 'paid', label: 'Pagados' },
    { key: 'all', label: 'Todos' },
  ] as const;

  return (
    <div className="fade-in flex flex-col gap-6">
      {/* Summary Cards */}
      <div className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(220px,1fr))]">
        <MetricCard
          label="Premios pendientes"
          value={`$${pendingPrizeSum.toFixed(2)}`}
          meta={`${pendingWinners.length} ticket(s) pendiente(s)`}
          accent="warning"
        />
        <MetricCard
          label="Premios pagados"
          value={`$${paidPrizeSum.toFixed(2)}`}
          meta={`${paidWinners.length} ticket(s) pagado(s)`}
          accent="success"
        />
        <MetricCard
          label="Total premiados hoy"
          value={`$${(pendingPrizeSum + paidPrizeSum).toFixed(2)}`}
          meta={`${adminTickets.length} ticket(s) de banca`}
          accent="risk"
        />
      </div>

      <Panel className="flex flex-col gap-5">
        <DataToolbar title="Premios ganadores">
          {filters.map((filter) => (
            <button
              key={filter.key}
              onClick={() => setGanadoresFilter(filter.key)}
              className={cn(
                'rounded-ln-md border px-3 py-2 text-xs font-semibold transition-colors',
                ganadoresFilter === filter.key
                  ? 'border-ln-primary bg-ln-primary/10 text-ln-primary'
                  : 'border-ln-border text-ln-text-secondary hover:border-ln-primary/50 hover:text-ln-text-primary',
              )}
            >
              {filter.label}
            </button>
          ))}
        </DataToolbar>

        {/* List of winners */}
        {filtered.length === 0 ? (
          <div className="rounded-ln-md border border-dashed border-ln-border bg-ln-surface/50 px-4 py-8 text-center text-sm text-ln-text-muted">
            No hay ganadores registrados en esta categoría de filtro.
          </div>
        ) : (
          <div className="table-container">
            <table className="table-el">
              <thead>
                <tr>
                  <th>Serie / Ticket ID</th>
                  <th>Cajero</th>
                  <th>Emisión</th>
                  <th>Combinaciones</th>
                  <th>Total Premio</th>
                  <th>Estado</th>
                  <th>Operación</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((t) => (
                  <tr key={t.id}>
                    <td className="font-semibold">{t.serial || t.id}</td>
                    <td>@{t.sellerUser}</td>
                    <td>{new Date(t.createdAtEpochMs).toLocaleTimeString()}</td>
                    <td>
                      <div className="max-h-16 max-w-[280px] overflow-y-auto break-words rounded border border-ln-border/40 bg-ln-background/40 px-2 py-1 text-xs text-ln-text-secondary">
                        {t.plays.map(p => `${p.playType.toUpperCase()} ${p.number}`).join(' · ')}
                      </div>
                    </td>
                    <td className="text-base font-bold text-ln-danger">
                      ${t.totalPrize.toFixed(2)}
                    </td>
                    <td>
                      <StatusBadge tone={t.status === 'paid' ? 'success' : 'warning'}>
                        {t.status === 'paid' ? 'Cobrado' : 'Premio Pendiente'}
                      </StatusBadge>
                    </td>
                    <td>
                      {t.status !== 'paid' && (
                        <TableActionButton tone="success" onClick={() => handlePayWinner(t)}>
                          Registrar Pago
                        </TableActionButton>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
};
