import React from 'react';
import { ArrowRightLeft } from 'lucide-react';
import type { UserAccount, TicketRecord, SportsTicketRecord, AuditLog } from '../../types';
import { RechargeTrendChart } from '../../components/RechargeTrendChart';

interface FinanzasTabProps {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  sportsTickets: SportsTicketRecord[];
  audits: AuditLog[];
  finanzasDateFilter: 'today' | 'yesterday' | 'all';
  setFinanzasDateFilter: (filter: 'today' | 'yesterday' | 'all') => void;
  setRechargeModalOpen: (open: boolean) => void;
  setRechargeForm: (form: { cashierId: string; amount: string }) => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  normalizeRate: (rate: number | null | undefined) => number;
}

export const FinanzasTab: React.FC<FinanzasTabProps> = ({
  user,
  users,
  tickets,
  sportsTickets,
  audits,
  finanzasDateFilter,
  setFinanzasDateFilter,
  setRechargeModalOpen,
  setRechargeForm,
  isSameLocalDate,
  normalizeRate,
}) => {
  const totalCajaCajeros = users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : (user.role === 'SUPERVISOR' ? u.supervisorIds.includes(user.id) : u.adminId === user.id))).reduce((sum, c) => {
    const cashierTickets = tickets.filter(t => t.sellerUser === c.user && t.status !== 'cancelled' && t.status !== 'voided');
    const tkSales = cashierTickets.reduce((acc, t) => acc + t.total, 0);
    const tkPremiosPagados = cashierTickets.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
    const tkComisiones = tkSales * normalizeRate(c.commissionRate);

    const cashierSports = sportsTickets.filter(t => t.sellerUsername === c.user && t.status !== 'void');
    const spSales = cashierSports.reduce((acc, t) => acc + t.stake, 0);
    const spPremiosPagados = cashierSports.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.potentialPayout, 0);
    const spComisiones = spSales * normalizeRate(c.commissionRate);

    const totalSales = tkSales + spSales;
    const totalComisiones = tkComisiones + spComisiones;
    const totalPremios = tkPremiosPagados + spPremiosPagados;

    const cashierRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE' && a.details.includes("asignado a " + c.user));
    const tkRecargas = cashierRecharges.reduce((sum, a) => {
      const match = a.details.match(/\$([0-9.]+)/);
      return sum + (match ? parseFloat(match[1]) : 0);
    }, 0);

    return sum + (totalSales + tkRecargas - totalComisiones - totalPremios);
  }, 0);

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* Filtro de Recargas */}
      <div className="glass-panel" style={{
        padding: '12px 20px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        backgroundColor: 'hsl(var(--surface) / 0.6)',
        gap: '12px',
        flexWrap: 'wrap'
      }}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'hsl(var(--text-primary))' }}>
            Filtro de Recargas
          </span>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
            Historial de recargas por día
          </span>
        </div>
        <div style={{ display: 'flex', gap: '6px' }}>
          {(['today', 'yesterday', 'all'] as const).map((filter) => (
            <button
              key={filter}
              onClick={() => setFinanzasDateFilter(filter)}
              style={{
                padding: '8px 14px',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid hsl(var(--border))',
                backgroundColor: finanzasDateFilter === filter ? 'hsl(var(--primary))' : 'hsl(var(--surface-hover))',
                color: finanzasDateFilter === filter ? '#ffffff' : 'hsl(var(--text-secondary))',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.2s ease'
              }}
            >
              {filter === 'today' ? 'Hoy' : filter === 'yesterday' ? 'Ayer' : 'Todos los días'}
            </button>
          ))}
        </div>
      </div>

      {/* Cupos summary Bento Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))', fontWeight: 600, textTransform: 'uppercase' }}>
            Mi Cupo Total Recargas ({user.role === 'MASTER' ? 'Master' : user.role === 'SUPERVISOR' ? 'Supervisor' : 'Admin'})
          </span>
          <span style={{ fontSize: '1.8rem', fontWeight: 700, color: 'hsl(var(--text-primary))', fontFamily: 'var(--font-display)' }}>
            ${(user?.rechargesAssignedBalance || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '8px', borderLeft: '4px solid hsl(var(--success))' }}>
          <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))', fontWeight: 600, textTransform: 'uppercase' }}>
            Cupo disponible
          </span>
          <span style={{ fontSize: '1.8rem', fontWeight: 700, color: 'hsl(var(--success))', fontFamily: 'var(--font-display)' }}>
            ${(user?.rechargesBalance || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '8px', borderLeft: '4px solid hsl(var(--primary))' }}>
          <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))', fontWeight: 600, textTransform: 'uppercase' }}>
            Efectivo Físico en Cajas
          </span>
          <span style={{ fontSize: '1.8rem', fontWeight: 700, color: 'hsl(var(--primary))', fontFamily: 'var(--font-display)' }}>
            ${totalCajaCajeros.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="glass-panel" style={{ 
          padding: '20px 24px', 
          display: 'flex', 
          flexDirection: 'column', 
          justifyContent: 'center', 
          gap: '12px',
          border: '1px solid hsl(var(--primary) / 0.25)' 
        }}>
          <div>
            <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))', fontWeight: 600, textTransform: 'uppercase', display: 'block' }}>
              Acciones Financieras
            </span>
            <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '2px', display: 'block' }}>
              Asignar balance de recarga instantáneamente a tus cajeros.
            </span>
          </div>
          <button 
            className="btn btn-primary tap-active" 
            onClick={() => setRechargeModalOpen(true)}
            style={{ 
              width: '100%', 
              fontSize: '0.85rem', 
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center', 
              gap: '8px', 
              padding: '10px 14px' 
            }}
          >
            <ArrowRightLeft size={16} />
            Asignar cupo
          </button>
        </div>
      </div>

      {/* Table showing Cashier list and saldos inside Finanzas tab */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Resumen de Saldos y Recargas por Cajero</h3>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>Asignación de cupo y caja del día calendario</span>
        </div>
        
        <div className="table-container">
          <table className="table-el">
            <thead>
              <tr>
                <th>Cajero</th>
                <th>Efectivo en Caja</th>
                <th>Cupo Recargas Disponible</th>
                <th style={{ textAlign: 'right' }}>Acción Rápida</th>
              </tr>
            </thead>
            <tbody>
              {users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : (user.role === 'SUPERVISOR' ? u.supervisorIds.includes(user.id) : u.adminId === user.id))).length === 0 ? (
                <tr>
                  <td colSpan={4} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                    No hay cajeros creados todavía.
                  </td>
                </tr>
              ) : (
                users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : (user.role === 'SUPERVISOR' ? u.supervisorIds.includes(user.id) : u.adminId === user.id))).map((c) => {
                  const cashierTickets = tickets.filter(t => t.sellerUser === c.user && t.status !== 'cancelled' && t.status !== 'voided');
                  const tkSales = cashierTickets.reduce((acc, t) => acc + t.total, 0);
                  const tkPremiosPagados = cashierTickets.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
                  const tkComisiones = tkSales * normalizeRate(c.commissionRate);

                  const cashierSports = sportsTickets.filter(t => t.sellerUsername === c.user && t.status !== 'void');
                  const spSales = cashierSports.reduce((acc, t) => acc + t.stake, 0);
                  const spPremiosPagados = cashierSports.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.potentialPayout, 0);
                  const spComisiones = spSales * normalizeRate(c.commissionRate);

                  const totalSales = tkSales + spSales;
                  const totalComisiones = tkComisiones + spComisiones;
                  const totalPremios = tkPremiosPagados + spPremiosPagados;

                  const cashierRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE' && a.details.includes("asignado a " + c.user));
                  const tkRecargas = cashierRecharges.reduce((sum, a) => {
                    const match = a.details.match(/\$([0-9.]+)/);
                    return sum + (match ? parseFloat(match[1]) : 0);
                  }, 0);

                  const cashierCaja = totalSales + tkRecargas - totalComisiones - totalPremios;

                  return (
                    <tr key={c.id}>
                      <td style={{ fontWeight: 600 }}>
                        {c.displayName || c.user} <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', fontWeight: 400 }}>@{c.user}</span>
                      </td>
                      <td style={{ fontWeight: 700, color: cashierCaja >= 0 ? 'hsl(var(--success))' : 'hsl(var(--danger))' }}>
                        ${cashierCaja.toFixed(2)}
                      </td>
                      <td style={{ fontWeight: 700, color: 'hsl(var(--primary))' }}>
                        ${c.rechargesBalance.toFixed(2)}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button 
                          className="btn btn-secondary btn-sm tap-active"
                          onClick={() => {
                            setRechargeForm({ cashierId: c.id, amount: '' });
                            setRechargeModalOpen(true);
                          }}
                          style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                        >
                          <ArrowRightLeft size={12} style={{ marginRight: '6px' }} />
                          Asignar Recarga
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Gráfico de Tendencia de Recargas */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{ fontSize: '1.1rem', fontWeight: 600, color: 'hsl(var(--text-primary))' }}>
            Flujo y Distribución de Recargas
          </span>
          <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-muted))' }}>
            Monto total de recargas distribuidas a cajeros en los últimos 7 días
          </span>
        </div>
        <RechargeTrendChart audits={audits} />
      </div>

      {/* Transactions list */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <h3 style={{ fontSize: '1.1rem' }}>Historial Reciente de Recargas</h3>
        
        <div className="table-container">
          <table className="table-el">
            <thead>
              <tr>
                <th>Fecha y Hora</th>
                <th>Cajero Destinatario</th>
                <th>Monto Asignado</th>
                <th>Tipo</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {(() => {
                const allRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE');
                const filteredRecharges = allRecharges.filter(a => {
                  if (finanzasDateFilter === 'today') {
                    return isSameLocalDate(a.timestampMs, 0);
                  } else if (finanzasDateFilter === 'yesterday') {
                    return isSameLocalDate(a.timestampMs, 1);
                  }
                  return true;
                });

                if (filteredRecharges.length === 0) {
                  return (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                        No hay recargas financieras procesadas recientemente.
                      </td>
                    </tr>
                  );
                }

                return filteredRecharges.map((a) => (
                  <tr key={a.id}>
                    <td>{new Date(a.timestampMs).toLocaleString()}</td>
                    <td style={{ fontWeight: 600 }}>{a.details.split('a ')[1] || 'Cajero'}</td>
                    <td style={{ fontWeight: 600, color: 'hsl(var(--success))' }}>
                      {a.details.split(' ')[2] || 'Monto'}
                    </td>
                    <td>REPARTO_CUPOS</td>
                    <td>
                      <span className="badge badge-success">COMPLETADA</span>
                    </td>
                  </tr>
                ));
              })()}
            </tbody>
          </table>
        </div>
      </div>

    </div>
  );
};
