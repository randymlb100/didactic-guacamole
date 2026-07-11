import React from 'react';
import type { UserAccount, TicketRecord, SportsTicketRecord, AuditLog } from '../../types';

interface CuadreTabProps {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  sportsTickets: SportsTicketRecord[];
  audits: AuditLog[];
  cuadrePeriod: 'today' | 'week' | 'month' | 'manual';
  setCuadrePeriod: (period: 'today' | 'week' | 'month' | 'manual') => void;
  cuadreCashierFilter: string;
  setCuadreCashierFilter: (cashier: string) => void;
  cuadreDateFrom: string;
  setCuadreDateFrom: (date: string) => void;
  cuadreDateTo: string;
  setCuadreDateTo: (date: string) => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  getLocalDateStringDR: (date?: Date | number) => string;
  normalizeRate: (rate: number | null | undefined) => number;
}

export const CuadreTab: React.FC<CuadreTabProps> = ({
  user,
  users,
  tickets,
  sportsTickets,
  audits,
  cuadrePeriod,
  setCuadrePeriod,
  cuadreCashierFilter,
  setCuadreCashierFilter,
  cuadreDateFrom,
  setCuadreDateFrom,
  cuadreDateTo,
  setCuadreDateTo,
  isSameLocalDate,
  getLocalDateStringDR,
  normalizeRate,
}) => {
  const isSupervisor = user.role === 'SUPERVISOR';
  const isMaster = user.role === 'MASTER';
  const allowedAdminId = isSupervisor ? user.adminId : user.id;
  const supervisedCashierUsers = isSupervisor 
    ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id)).map(u => u.user)
    : [];

  const scopedTickets = tickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided')
    .filter(t => {
      if (isMaster) return true;
      if (isSupervisor && (!t.sellerUser || !supervisedCashierUsers.includes(t.sellerUser))) return false;
      if (!isSupervisor && t.adminId !== allowedAdminId) return false;
      return true;
    })
    .filter(t => {
      if (isSupervisor && (!t.sellerUser || !supervisedCashierUsers.includes(t.sellerUser))) return false;
      if (cuadreCashierFilter !== 'all' && t.sellerUser !== cuadreCashierFilter) return false;
      
      if (cuadrePeriod === 'today') {
        return isSameLocalDate(t.createdAtEpochMs, 0);
      } else if (cuadrePeriod === 'week') {
        return (Date.now() - t.createdAtEpochMs) <= (7 * 86400000);
      } else if (cuadrePeriod === 'month') {
        return (Date.now() - t.createdAtEpochMs) <= (30 * 86400000);
      } else if (cuadrePeriod === 'manual') {
        const tDateStr = getLocalDateStringDR(t.createdAtEpochMs);
        return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
      }
      return true;
    });

  const scopedSportsTickets = sportsTickets.filter(t => t.status !== 'void')
    .filter(t => {
      if (isMaster) return true;
      if (isSupervisor && (!t.sellerUsername || !supervisedCashierUsers.includes(t.sellerUsername))) return false;
      if (!isSupervisor && t.adminKey !== allowedAdminId && t.ownerKey !== allowedAdminId) return false;
      return true;
    })
    .filter(t => {
      if (cuadreCashierFilter !== 'all' && t.sellerUsername !== cuadreCashierFilter) return false;
      
      const soldAtTime = new Date(t.soldAt).getTime();
      if (cuadrePeriod === 'today') {
        return isSameLocalDate(soldAtTime, 0);
      } else if (cuadrePeriod === 'week') {
        return (Date.now() - soldAtTime) <= (7 * 86400000);
      } else if (cuadrePeriod === 'month') {
        return (Date.now() - soldAtTime) <= (30 * 86400000);
      } else if (cuadrePeriod === 'manual') {
        const tDateStr = getLocalDateStringDR(soldAtTime);
        return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
      }
      return true;
    });

  const loteriaVentas = scopedTickets.reduce((acc, t) => acc + t.total, 0);
  const loteriaPremiosPagados = scopedTickets.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
  const loteriaPremiosPendientes = scopedTickets.filter(t => t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
  let loteriaComisiones = 0;
  scopedTickets.forEach(t => {
    const cashier = users.find(u => u.user === t.sellerUser);
    loteriaComisiones += t.total * normalizeRate(cashier?.commissionRate);
  });

  const deportesVentas = scopedSportsTickets.reduce((acc, t) => acc + t.stake, 0);
  const deportesPremiosPagados = scopedSportsTickets.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.potentialPayout, 0);
  const deportesPremiosPendientes = scopedSportsTickets.filter(t => t.status === 'won').reduce((acc, t) => acc + t.potentialPayout, 0);
  let deportesComisiones = 0;
  scopedSportsTickets.forEach(t => {
    const cashier = users.find(u => u.user === t.sellerUsername);
    deportesComisiones += t.stake * normalizeRate(cashier?.commissionRate);
  });

  const ventasBrutas = loteriaVentas + deportesVentas;
  const comisiones = loteriaComisiones + deportesComisiones;
  const premiosPagados = loteriaPremiosPagados + deportesPremiosPagados;
  const premiosPendientes = loteriaPremiosPendientes + deportesPremiosPendientes;

  const scopedRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE')
    .filter(a => {
      const timestamp = Number(a.timestampMs || 0);
      if (!timestamp) return false;
      
      if (cuadrePeriod === 'today') {
        return isSameLocalDate(timestamp, 0);
      } else if (cuadrePeriod === 'week') {
        return (Date.now() - timestamp) <= (7 * 86400000);
      } else if (cuadrePeriod === 'month') {
        return (Date.now() - timestamp) <= (30 * 86400000);
      } else if (cuadrePeriod === 'manual') {
        const tDateStr = getLocalDateStringDR(timestamp);
        return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
      }
      return true;
    })
    .filter(a => {
      const cashierScope = user.role === 'MASTER'
        ? users.filter(u => u.role === 'CASHIER')
        : users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id)));
      if (cuadreCashierFilter === 'all') {
        return cashierScope.some(c => a.details.includes("asignado a " + c.user));
      } else {
        return a.details.includes("asignado a " + cuadreCashierFilter);
      }
    });

  const recargas = scopedRecharges.reduce((sum, a) => {
    const match = a.details.match(/\$([0-9.]+)/);
    const val = match ? parseFloat(match[1]) : 0;
    return sum + val;
  }, 0);

  const cajaDisponible = (ventasBrutas + recargas) - comisiones - premiosPagados - premiosPendientes;
  const beneficioNeto = ventasBrutas - comisiones - premiosPagados - premiosPendientes;

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* Cuadre controls */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>Cuadre de Caja y Conciliación Operativa</h3>
        
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: '6px' }}>
            {[
              { id: 'today', label: 'Hoy' },
              { id: 'week', label: 'Semana' },
              { id: 'month', label: 'Mes' },
              { id: 'manual', label: 'Periodo' }
            ].map((p) => (
              <button
                key={p.id}
                onClick={() => setCuadrePeriod(p.id as any)}
                style={{
                  padding: '8px 14px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid ' + (cuadrePeriod === p.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                  background: cuadrePeriod === p.id ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                  color: cuadrePeriod === p.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                {p.label}
              </button>
            ))}
          </div>

          <select
            className="form-input"
            value={cuadreCashierFilter}
            onChange={(e) => setCuadreCashierFilter(e.target.value)}
            style={{ width: '200px' }}
          >
            <option value="all">Todos los Puntos</option>
            {users.filter(u => {
              if (user.role === 'MASTER') return u.role === 'CASHIER' || u.role === 'ADMIN';
              if (user.role === 'ADMIN') return (u.role === 'CASHIER' || u.role === 'ADMIN') && (u.adminId === user.id || u.id === user.id);
              return u.role === 'CASHIER' && u.supervisorIds.includes(user.id);
            }).map(c => (
              <option key={c.id} value={c.user}>@{c.user} {c.role === 'ADMIN' ? '(Banca/Admin)' : ''}</option>
            ))}
          </select>

          {cuadrePeriod === 'manual' && (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="date"
                value={cuadreDateFrom}
                onChange={(e) => setCuadreDateFrom(e.target.value)}
                className="form-input"
                style={{ width: '140px' }}
              />
              <span style={{ fontSize: '0.8rem' }}>a</span>
              <input
                type="date"
                value={cuadreDateTo}
                onChange={(e) => setCuadreDateTo(e.target.value)}
                className="form-input"
                style={{ width: '140px' }}
              />
            </div>
          )}
        </div>
      </div>

      {/* Computes and metrics layout */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px' }}>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase' }}>Venta Bruta</span>
          <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--text-primary))' }}>${ventasBrutas.toFixed(2)}</strong>
          <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Lot: ${loteriaVentas.toFixed(2)} | Dep: ${deportesVentas.toFixed(2)}</span>
        </div>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase' }}>Comisión Retenida</span>
          <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--danger))' }}>${comisiones.toFixed(2)}</strong>
          <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Lot: ${loteriaComisiones.toFixed(2)} | Dep: ${deportesComisiones.toFixed(2)}</span>
        </div>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase' }}>Premios Pagados</span>
          <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--warning))' }}>${premiosPagados.toFixed(2)}</strong>
          <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Lot: ${loteriaPremiosPagados.toFixed(2)} | Dep: ${deportesPremiosPagados.toFixed(2)}</span>
        </div>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase' }}>Recarga Distribuida</span>
          <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--primary))' }}>${recargas.toFixed(2)}</strong>
          <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Cupos de venta FF</span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px' }}>
        <div className="glass-panel" style={{ padding: '24px', borderLeft: '4px solid hsl(var(--primary))', boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.3)' }}>
          <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Caja Disponible (Efectivo Físico)</span>
          <h4 style={{ fontSize: '2rem', fontWeight: 700, color: 'hsl(var(--text-primary))', fontFamily: 'var(--font-display)', marginTop: '4px' }}>${cajaDisponible.toFixed(2)}</h4>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', marginTop: '2px', display: 'block' }}>Dinero físico que debe estar presente en el cajón de los cajeros (Ventas + Recargas - Comisión - Premios Pagados).</span>
        </div>

        <div className="glass-panel" style={{ padding: '24px', borderLeft: '4px solid hsl(var(--success))', background: 'hsl(var(--success) / 0.03)', boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.3)' }}>
          <span style={{ color: 'hsl(var(--success))', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Beneficio Neto (Ganancia Banca)</span>
          <h4 style={{ fontSize: '2rem', fontWeight: 700, color: 'hsl(var(--success))', fontFamily: 'var(--font-display)', marginTop: '4px' }}>${beneficioNeto.toFixed(2)}</h4>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', marginTop: '2px', display: 'block' }}>Ganancia neta consolidada de tu negocio (Ventas - Comisión - Premios Totales Pagados/Pendientes).</span>
        </div>
      </div>

      {/* Cashiers performance breakdown table */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Desglose de Conciliación por Cajero</h3>
        
        <div className="table-container">
          <table className="table-el">
            <thead>
              <tr>
                <th>Cajero</th>
                <th>Ventas</th>
                <th>Comisiones</th>
                <th>Premios Pagados</th>
                <th>Premios Pendientes</th>
                <th>Recargas</th>
                <th>Caja Neto (Efectivo)</th>
                <th>Beneficio Neto</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id)))).map(cashier => {
                const cashierTks = tickets.filter(t => t.sellerUser === cashier.user && t.status !== 'cancelled' && t.status !== 'voided')
                  .filter(t => {
                    if (cuadrePeriod === 'today') {
                      return isSameLocalDate(t.createdAtEpochMs, 0);
                    } else if (cuadrePeriod === 'week') {
                      return (Date.now() - t.createdAtEpochMs) <= (7 * 86400000);
                    } else if (cuadrePeriod === 'month') {
                      return (Date.now() - t.createdAtEpochMs) <= (30 * 86400000);
                    } else if (cuadrePeriod === 'manual') {
                      const tDateStr = getLocalDateStringDR(t.createdAtEpochMs);
                      return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
                    }
                    return true;
                  });

                const tkSales = cashierTks.reduce((acc, t) => acc + t.total, 0);
                const tkPremiosPagados = cashierTks.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
                const tkPremiosPendientes = cashierTks.filter(t => t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
                const tkComisiones = tkSales * normalizeRate(cashier.commissionRate);

                const cashierSports = sportsTickets.filter(t => t.sellerUsername === cashier.user && t.status !== 'void')
                  .filter(t => {
                    const soldAtTime = new Date(t.soldAt).getTime();
                    if (cuadrePeriod === 'today') {
                      return isSameLocalDate(soldAtTime, 0);
                    } else if (cuadrePeriod === 'week') {
                      return (Date.now() - soldAtTime) <= (7 * 86400000);
                    } else if (cuadrePeriod === 'month') {
                      return (Date.now() - soldAtTime) <= (30 * 86400000);
                    } else if (cuadrePeriod === 'manual') {
                      const tDateStr = getLocalDateStringDR(soldAtTime);
                      return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
                    }
                    return true;
                  });

                const spSales = cashierSports.reduce((acc, t) => acc + t.stake, 0);
                const spPremiosPagados = cashierSports.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.potentialPayout, 0);
                const spPremiosPendientes = cashierSports.filter(t => t.status === 'won').reduce((acc, t) => acc + t.potentialPayout, 0);
                const spComisiones = spSales * normalizeRate(cashier.commissionRate);

                const totalSales = tkSales + spSales;
                const totalComisiones = tkComisiones + spComisiones;
                const totalPremios = tkPremiosPagados + spPremiosPagados;
                const totalPremiosPendientes = tkPremiosPendientes + spPremiosPendientes;
                
                const cashierRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE' && a.details.includes("asignado a " + cashier.user))
                  .filter(a => {
                    const timestamp = Number(a.timestampMs || 0);
                    if (!timestamp) return false;
                    
                    if (cuadrePeriod === 'today') {
                      return isSameLocalDate(timestamp, 0);
                    } else if (cuadrePeriod === 'week') {
                      return (Date.now() - timestamp) <= (7 * 86400000);
                    } else if (cuadrePeriod === 'month') {
                      return (Date.now() - timestamp) <= (30 * 86400000);
                    } else if (cuadrePeriod === 'manual') {
                      const tDateStr = getLocalDateStringDR(timestamp);
                      return tDateStr >= cuadreDateFrom && tDateStr <= cuadreDateTo;
                    }
                    return true;
                  });
                const tkRecargas = cashierRecharges.reduce((sum, a) => {
                  const match = a.details.match(/\$([0-9.]+)/);
                  return sum + (match ? parseFloat(match[1]) : 0);
                }, 0);
                
                const tkCaja = totalSales + tkRecargas - totalComisiones - totalPremios - totalPremiosPendientes;
                const beneficioNeto = totalSales - totalComisiones - totalPremios - totalPremiosPendientes;

                return (
                  <tr key={cashier.id}>
                    <td style={{ fontWeight: 600 }}>{cashier.displayName || cashier.user}</td>
                    <td style={{ fontWeight: 600 }}>${totalSales.toFixed(2)}</td>
                    <td style={{ color: 'hsl(var(--danger))' }}>${totalComisiones.toFixed(2)}</td>
                    <td style={{ color: 'hsl(var(--warning))' }}>${totalPremios.toFixed(2)}</td>
                    <td style={{ color: 'hsl(var(--text-muted))' }}>${totalPremiosPendientes.toFixed(2)}</td>
                    <td>${tkRecargas.toFixed(2)}</td>
                    <td style={{ fontWeight: 700, color: tkCaja >= 0 ? 'hsl(var(--success))' : 'hsl(var(--danger))' }}>
                      ${tkCaja.toFixed(2)}
                    </td>
                    <td style={{ fontWeight: 700, color: beneficioNeto >= 0 ? 'hsl(var(--success))' : 'hsl(var(--danger))' }}>
                      ${beneficioNeto.toFixed(2)}
                    </td>
                    <td>
                      <span className={`badge ${cashier.active ? 'badge-success' : 'badge-danger'}`}>
                        {cashier.active ? 'Activo' : 'Suspendido'}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
