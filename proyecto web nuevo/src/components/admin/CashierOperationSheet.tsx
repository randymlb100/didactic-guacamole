import React, { useMemo, useState } from 'react';
import { X } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';
import { canEditCashierFromMonitoring, getCashierScopedTickets, getCashierScopedWinners } from '../../utils/cashierScope';

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
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(8, 22, 28, 0.44)', zIndex: 100, display: 'flex', justifyContent: 'flex-end' }} role="dialog" aria-modal="true">
      <section className="glass-panel" style={{ width: 'min(560px, 100vw)', height: '100%', borderRadius: '18px 0 0 18px', padding: 18, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
        <header style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
          <div>
            <h3 style={{ margin: 0 }}>{cashier.displayName || cashier.user}</h3>
            <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.82rem' }}>{cashier.banca || 'Banca'} · @{cashier.user}</span>
          </div>
          <button type="button" className="btn btn-secondary" onClick={onClose} aria-label="Cerrar">
            <X size={18} />
          </button>
        </header>

        <nav style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {tabs.map(([id, label]) => (
            <button key={id} type="button" className={tab === id ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setTab(id)}>
              {label}
            </button>
          ))}
        </nav>

        {tab === 'summary' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
            <div className="glass-panel" style={{ padding: 12 }}>Tickets<br /><strong>{scopedTickets.length}</strong></div>
            <div className="glass-panel" style={{ padding: 12 }}>Ventas<br /><strong>${sales.toFixed(2)}</strong></div>
            <div className="glass-panel" style={{ padding: 12 }}>Pendiente<br /><strong>${pendingPayout.toFixed(2)}</strong></div>
          </div>
        )}

        {tab === 'tickets' && scopedTickets.map((ticket) => (
          <div key={ticket.id} className="glass-panel" style={{ padding: 12, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
            <strong>{ticket.serial || ticket.id}</strong>
            <span>${Number(ticket.total || 0).toFixed(2)} · {ticket.status}</span>
          </div>
        ))}

        {tab === 'payouts' && winners.map((ticket) => (
          <div key={ticket.id} className="glass-panel" style={{ padding: 12, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
            <strong>{ticket.serial || ticket.id}</strong>
            <span>${Number(ticket.totalPrize || 0).toFixed(2)} · {ticket.status}</span>
          </div>
        ))}

        {tab === 'limits' && (
          <div className="glass-panel" style={{ padding: 14, display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
            <span>Límites operacionales de este cajero</span>
            <button type="button" className="btn btn-primary" disabled={!canEdit} onClick={() => onOpenLimits(cashier)}>Administrar</button>
          </div>
        )}

        {tab === 'recharges' && (
          <div className="glass-panel" style={{ padding: 14, display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
            <span>Cupo y acceso de recargas</span>
            <button type="button" className="btn btn-primary" disabled={!canEdit} onClick={() => onOpenRecharge(cashier)}>Administrar</button>
          </div>
        )}

        {tab === 'data' && (
          <div className="glass-panel" style={{ padding: 14, display: 'grid', gap: 8 }}>
            <span>Estado: <strong>{cashier.active === false ? 'Bloqueado' : 'Activo'}</strong></span>
            <span>Balance: <strong>${Number(cashier.balance || 0).toFixed(2)}</strong></span>
            <span>Comisión: <strong>{Number(cashier.commissionRate || 0).toFixed(2)}%</strong></span>
          </div>
        )}
      </section>
    </div>
  );
};
