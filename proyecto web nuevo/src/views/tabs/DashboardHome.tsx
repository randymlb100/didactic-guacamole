import React from 'react';
import { 
  Users, Layers, TrendingUp, DollarSign, AlertTriangle, ArrowRightLeft, RefreshCw 
} from 'lucide-react';
import type { UserAccount, TicketRecord, SportsTicketRecord, AuditLog, LotteryCatalogItem } from '../../types';
import { STATIC_LOTTERIES } from '../../utils/supabase';
import { FinancialTrendChart } from '../../components/FinancialTrendChart';

interface DashboardSystemModeConfig {
  lotteryModeEnabled?: boolean;
  pickModeEnabled?: boolean;
  sportsbookEnabled?: boolean;
}

interface DashboardSportsLimitsForm {
  max_ticket_stake: number;
  max_potential_payout: number;
}

type DashboardMetricTone = 'primary' | 'sales' | 'balance' | 'recharge' | 'risk' | 'system';

interface DashboardMetricCard {
  title: string;
  value: string;
  icon: React.ComponentType<{ size?: number }>;
  tone: DashboardMetricTone;
}

type DashboardStats = {
  card1: DashboardMetricCard;
  card2: DashboardMetricCard;
  card3: DashboardMetricCard;
  card4: DashboardMetricCard;
  cardGlobal: DashboardMetricCard;
};

interface DashboardHomeProps {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  sportsTickets: SportsTicketRecord[];
  audits: AuditLog[];
  lotteries: LotteryCatalogItem[];
  systemModeConfig: DashboardSystemModeConfig;
  sportsLimitsForm: DashboardSportsLimitsForm;
  manualDisabledLotteryIds: string[];
  dashboardDateFilter: 'today' | 'yesterday' | 'all';
  setDashboardDateFilter: (filter: 'today' | 'yesterday' | 'all') => void;
  dashboardViewContext: 'combined' | 'lottery' | 'sports';
  setDashboardViewContext: (ctx: 'combined' | 'lottery' | 'sports') => void;
  drawsSubTab: 'lottery' | 'pick_sports';
  setDrawsSubTab: (tab: 'lottery' | 'pick_sports') => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  normalizeRate: (rate: number | null | undefined) => number;
  handleToggleManualDisabledLottery: (lotteryId: string) => Promise<void>;
  setSelectedTicketForDetail: (ticket: TicketRecord) => void;
  loadData: () => void;
  parseTimeToMinutes: (timeStr: string) => number;
  getCurrentDRMinutesSinceMidnight: () => number;
}

export const DashboardHome: React.FC<DashboardHomeProps> = ({
  user,
  users,
  tickets,
  sportsTickets,
  audits,
  lotteries,
  systemModeConfig,
  sportsLimitsForm,
  manualDisabledLotteryIds,
  dashboardDateFilter,
  setDashboardDateFilter,
  dashboardViewContext,
  setDashboardViewContext,
  drawsSubTab,
  setDrawsSubTab,
  isSameLocalDate,
  normalizeRate,
  handleToggleManualDisabledLottery,
  setSelectedTicketForDetail,
  loadData,
  parseTimeToMinutes,
  getCurrentDRMinutesSinceMidnight,
}) => {
  const getDashboardStats = () => {
    if (user.role === 'MASTER') {
      const activeAdmins = users.filter(u => u.role === 'ADMIN' && u.active).length;
      const totalAdmins = users.filter(u => u.role === 'ADMIN').length;
      const activeCashiers = users.filter(u => u.role === 'CASHIER' && u.active).length;
      
      let salesTotalDaily = 0;
      let prizesTotalDaily = 0;
      let salesTotalGlobal = 0;

      // Global sales (accumulated)
      if (dashboardViewContext === 'lottery' || dashboardViewContext === 'combined') {
        salesTotalGlobal += tickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided').reduce((acc, t) => acc + t.total, 0);
      }
      
      if (dashboardViewContext === 'sports' || dashboardViewContext === 'combined') {
        salesTotalGlobal += sportsTickets.filter(t => t.status !== 'void').reduce((acc, t) => acc + t.stake, 0);
      }

      // Daily filtered sales
      const filterDays = dashboardDateFilter === 'today' ? 0 : dashboardDateFilter === 'yesterday' ? 1 : null;
      if (dashboardViewContext === 'lottery' || dashboardViewContext === 'combined') {
        const filteredTickets = filterDays !== null 
          ? tickets.filter(t => isSameLocalDate(t.createdAtEpochMs, filterDays))
          : tickets;
        salesTotalDaily += filteredTickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided').reduce((acc, t) => acc + t.total, 0);
        prizesTotalDaily += filteredTickets.filter(t => t.status === 'paid' || t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
      }
      
      if (dashboardViewContext === 'sports' || dashboardViewContext === 'combined') {
        const filteredSports = filterDays !== null
          ? sportsTickets.filter(t => isSameLocalDate(new Date(t.soldAt).getTime(), filterDays))
          : sportsTickets;
        salesTotalDaily += filteredSports.filter(t => t.status !== 'void').reduce((acc, t) => acc + t.stake, 0);
        prizesTotalDaily += filteredSports.filter(t => t.status === 'paid' || t.status === 'won').reduce((acc, t) => acc + t.potentialPayout, 0);
      }
      
      const titleSuffix = dashboardDateFilter === 'today' ? ' (Hoy)' : dashboardDateFilter === 'yesterday' ? ' (Ayer)' : ' (Global)';
      return {
        card1: { title: 'Bancas Activas', value: `${activeAdmins}/${totalAdmins}`, icon: Layers, tone: 'system' },
        card2: { title: 'Cajeros de Red', value: activeCashiers.toString(), icon: Users, tone: 'primary' },
        card3: { title: `Ventas${titleSuffix}`, value: `$${salesTotalDaily.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: DollarSign, tone: 'sales' },
        card4: { title: `Premios${titleSuffix}`, value: `$${prizesTotalDaily.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: AlertTriangle, tone: 'risk' },
        cardGlobal: { title: 'Ventas globales', value: `$${salesTotalGlobal.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: TrendingUp, tone: 'balance' }
      } satisfies DashboardStats;
    } else {
      // ADMIN or SUPERVISOR red stats
      const myCashiers = user.role === 'SUPERVISOR' 
        ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id))
        : users.filter(u => u.role === 'CASHIER' && u.adminId === user.id);
      
      const activeMyCashiers = myCashiers.filter(c => c.active).length;
      const cashierUsernames = myCashiers.map(c => c.user);
      
      let salesTotalDaily = 0;
      let salesTotalGlobal = 0;

      const filterDays = dashboardDateFilter === 'today' ? 0 : dashboardDateFilter === 'yesterday' ? 1 : null;

      // Daily sales
      if (dashboardViewContext === 'lottery' || dashboardViewContext === 'combined') {
        const myTickets = tickets.filter(t => cashierUsernames.includes(t.sellerUser || ''));
        const filteredTickets = filterDays !== null
          ? myTickets.filter(t => isSameLocalDate(t.createdAtEpochMs, filterDays))
          : myTickets;
        salesTotalDaily += filteredTickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided').reduce((acc, t) => acc + t.total, 0);
      }
      if (dashboardViewContext === 'sports' || dashboardViewContext === 'combined') {
        const mySportsTickets = sportsTickets.filter(t => cashierUsernames.includes(t.sellerUsername || ''));
        const filteredSports = filterDays !== null
          ? mySportsTickets.filter(t => isSameLocalDate(new Date(t.soldAt).getTime(), filterDays))
          : mySportsTickets;
        salesTotalDaily += filteredSports.filter(t => t.status !== 'void').reduce((acc, t) => acc + t.stake, 0);
      }

      // Global sales
      if (dashboardViewContext === 'lottery' || dashboardViewContext === 'combined') {
        const myTickets = tickets.filter(t => cashierUsernames.includes(t.sellerUser || ''));
        salesTotalGlobal += myTickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided').reduce((acc, t) => acc + t.total, 0);
      }
      if (dashboardViewContext === 'sports' || dashboardViewContext === 'combined') {
        const mySportsTickets = sportsTickets.filter(t => cashierUsernames.includes(t.sellerUsername || ''));
        salesTotalGlobal += mySportsTickets.filter(t => t.status !== 'void').reduce((acc, t) => acc + t.stake, 0);
      }
      
      let balance = user?.balance ?? 0;
      let rechargesBalance = user?.rechargesBalance ?? 0;

      // Dynamic fallbacks when static value is 0 or empty for ADMIN / SUPERVISOR
      if (balance === 0 || user.role === 'SUPERVISOR') {
        const calculatedBalance = myCashiers.reduce((sum, cashier) => {
          let tkSales = 0;
          let tkPremiosPagados = 0;
          let tkPremiosPendientes = 0;
          let tkComisiones = 0;

          if (dashboardViewContext === 'lottery' || dashboardViewContext === 'combined') {
            const cashierTks = tickets.filter(t => t.sellerUser === cashier.user && t.status !== 'cancelled' && t.status !== 'voided');
            tkSales = cashierTks.reduce((acc, t) => acc + t.total, 0);
            tkPremiosPagados = cashierTks.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
            tkPremiosPendientes = cashierTks.filter(t => t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
            tkComisiones = tkSales * normalizeRate(cashier.commissionRate);
          }

          let sportsSalesAmt = 0;
          let sportsPaidAmt = 0;
          let sportsWonAmt = 0;
          let sportsComisiones = 0;

          if (dashboardViewContext === 'sports' || dashboardViewContext === 'combined') {
            const cashierSports = sportsTickets.filter(t => t.sellerUsername === cashier.user && t.status !== 'void');
            sportsSalesAmt = cashierSports.reduce((acc, t) => acc + t.stake, 0);
            sportsPaidAmt = cashierSports.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.potentialPayout, 0);
            sportsWonAmt = cashierSports.filter(t => t.status === 'won').reduce((acc, t) => acc + t.potentialPayout, 0);
            sportsComisiones = sportsSalesAmt * normalizeRate(cashier.commissionRate);
          }

          const cashierRecharges = audits.filter(a => a.action === 'PROCESS_RECHARGE' && a.details.includes("asignado a " + cashier.user))
            .filter(a => {
              if (dashboardDateFilter === 'today') {
                return isSameLocalDate(a.timestampMs, 0);
              } else if (dashboardDateFilter === 'yesterday') {
                return isSameLocalDate(a.timestampMs, 1);
              }
              return true; // all
            });
          const tkRecargas = cashierRecharges.reduce((sum, a) => {
            const match = a.details.match(/\$([0-9.]+)/);
            return sum + (match ? parseFloat(match[1]) : 0);
          }, 0);
          const tkCaja = (tkSales + sportsSalesAmt) + tkRecargas - (tkComisiones + sportsComisiones) - (tkPremiosPagados + sportsPaidAmt) - (tkPremiosPendientes + sportsWonAmt);
          return sum + tkCaja;
        }, 0);
        
        balance = calculatedBalance;
      }

      if (rechargesBalance === 0) {
        const calculatedRecharges = myCashiers.reduce((sum, cashier) => {
          return sum + (cashier.rechargesBalance || 0);
        }, 0);
        rechargesBalance = calculatedRecharges;
      }

      const titleSuffix = dashboardDateFilter === 'today' ? ' Hoy' : dashboardDateFilter === 'yesterday' ? ' Ayer' : ' Global';
      return {
        card1: { title: user.role === 'SUPERVISOR' ? 'Mis Cajeros Activos' : 'Cajeros Activos', value: `${activeMyCashiers}/${myCashiers.length}`, icon: Users, tone: 'primary' },
        card2: { title: user.role === 'SUPERVISOR' ? 'Mi Balance' : 'Balance de Bancas', value: `$${balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: DollarSign, tone: 'balance' },
        card3: { title: user.role === 'SUPERVISOR' ? `Mis Ventas${titleSuffix}` : `Ventas${titleSuffix}`, value: `$${salesTotalDaily.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: TrendingUp, tone: 'sales' },
        card4: { title: user.role === 'SUPERVISOR' ? 'Mi cupo de recargas' : 'Cupo de recargas', value: `$${rechargesBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: ArrowRightLeft, tone: 'recharge' },
        cardGlobal: { title: 'Ventas globales', value: `$${salesTotalGlobal.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: TrendingUp, tone: 'system' }
      } satisfies DashboardStats;
    }
  };

  const stats = getDashboardStats();

  const myCashiersForDashboard = user.role === 'SUPERVISOR' 
    ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id))
    : (user.role === 'ADMIN' ? users.filter(u => (u.role === 'CASHIER' || u.role === 'ADMIN') && (u.adminId === user.id || u.id === user.id)) : []);

  const cashierUsernamesForDashboard = myCashiersForDashboard.map(c => c.user);
  const allDashboardTickets = user.role === 'MASTER'
    ? tickets
    : tickets.filter(t => cashierUsernamesForDashboard.includes(t.sellerUser || ''));

  const dashboardTicketsToShow = allDashboardTickets.filter(t => {
    if (dashboardDateFilter === 'today') {
      return isSameLocalDate(t.createdAtEpochMs, 0);
    } else if (dashboardDateFilter === 'yesterday') {
      return isSameLocalDate(t.createdAtEpochMs, 1);
    }
    return true; // 'all'
  });

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* Filtro Temporal de Ventas */}
      <div className="glass-panel fintech-toolbar fintech-section" style={{
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
            Filtro Temporal de Ventas
          </span>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
            Ventas y métricas por período
          </span>
        </div>
        <div className="segmented-control">
          {(['today', 'yesterday', 'all'] as const).map((filter) => (
            <button
              key={filter}
              onClick={() => setDashboardDateFilter(filter)}
              data-active={dashboardDateFilter === filter}
              style={{
                padding: '8px 14px',
              }}
            >
              {filter === 'today' ? 'Hoy' : filter === 'yesterday' ? 'Ayer' : 'Todos los días'}
            </button>
          ))}
        </div>
      </div>

      {/* Context Selector (Lotería / Deportes / Combinado) */}
      {user.role !== 'MASTER' && (
        <div className="glass-panel fintech-toolbar fintech-section" style={{
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
              Contexto Operativo
            </span>
            <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
              Acumulados de ventas y comisiones
            </span>
          </div>
          <div className="segmented-control">
            {(['combined', 'lottery', 'sports'] as const).map((ctx) => (
              <button
                key={ctx}
                onClick={() => setDashboardViewContext(ctx)}
                data-active={dashboardViewContext === ctx}
                style={{
                  padding: '8px 14px',
                }}
              >
                {ctx === 'combined' ? 'Combinado' : ctx === 'lottery' ? 'Solo Lotería' : 'Solo Deportes'}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* METRIC CARDS GRID */}
      <div className="fintech-section" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: '16px' }}>
        {[stats.card1, stats.card2, stats.card3, stats.card4, stats.cardGlobal].map((c, i) => {
          const Icon = c.icon;
          return (
            <div key={i} className={`metric-card-pro metric-card-pro--${c.tone || 'primary'}`}>
              <div className="metric-card-pro__content">
                <div className="metric-card-pro__top">
                  <span className="metric-card-pro__label">{c.title}</span>
                  <span className="metric-card-pro__icon">
                    <Icon size={20} />
                  </span>
                </div>
                <span className="metric-card-pro__value">{c.value}</span>
                <div className="metric-card-pro__meta">
                  <span>{i === 4 ? 'Acumulado' : dashboardDateFilter === 'today' ? 'Hoy' : dashboardDateFilter === 'yesterday' ? 'Ayer' : 'Todos los días'}</span>
                  <span className="metric-card-pro__rail" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* GRÁFICO FINANCIERO SEMANAL */}
      {user.role !== 'MASTER' && (
        <div className="glass-panel-premium fintech-section" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <span style={{ fontSize: '1.1rem', fontWeight: 600, color: 'hsl(var(--text-primary))' }}>
              Tendencia Financiera Semanal
            </span>
            <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-muted))' }}>
              Ventas globales acumuladas de lotería y deportes de los últimos 7 días
            </span>
          </div>
          <FinancialTrendChart tickets={tickets} sportsTickets={sportsTickets} />
        </div>
      )}

      {/* TWO COLUMN SUMMARY CONTENT */}
      <div style={{ display: 'grid', gridTemplateColumns: user.role === 'MASTER' ? '1fr' : '2fr 1fr', gap: '24px' }} className="grid-responsive">
        
        {/* Visual exposure monitoring / live transactions */}
        {user.role !== 'MASTER' && (
          <div className="glass-panel-premium fintech-section" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
                Tickets recientes
              </h3>
              <button className="btn-icon" onClick={loadData}>
                <RefreshCw size={16} />
              </button>
            </div>

            <div className="table-container">
              <table className="table-el">
                <thead>
                  <tr>
                    <th>Serial</th>
                    <th>Cajero</th>
                    <th>Loterías</th>
                    <th>Total</th>
                    <th>Premios</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardTicketsToShow.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="empty-state-ledger" style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                        No hay transacciones registradas hoy.
                      </td>
                    </tr>
                  ) : (
                    dashboardTicketsToShow.map((t) => (
                      <tr key={t.id} onClick={() => setSelectedTicketForDetail(t)} style={{ cursor: 'pointer' }}>
                        <td style={{ fontWeight: 600 }}>{t.serial || t.id.substring(0, 8).toUpperCase()}</td>
                        <td>{t.sellerUser}</td>
                        <td style={{ fontSize: '0.8rem' }}>
                          {(() => {
                            const uniqueLots = new Set();
                            t.plays.forEach(p => {
                              if (p.lotteryName) {
                                p.lotteryName.split(/[/,]+/).forEach(part => {
                                  const trimmed = part.trim();
                                  if (trimmed) uniqueLots.add(trimmed);
                                });
                              }
                            });
                            return Array.from(uniqueLots).join(' / ');
                          })()}
                        </td>
                        <td style={{ fontWeight: 600 }}>${t.total.toFixed(2)}</td>
                        <td style={{ color: t.totalPrize > 0 ? 'hsl(var(--success))' : 'inherit', fontWeight: 600 }}>
                          ${t.totalPrize.toFixed(2)}
                        </td>
                        <td>
                          <span className={`badge ${
                            t.status === 'paid' ? 'badge-success' : t.status === 'cancelled' ? 'badge-danger' : 'badge-primary'
                          }`}>
                            {t.status === 'paid' ? 'Cobrado' : t.status === 'cancelled' ? 'Anulado' : 'Activo'}
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Exposición y Loterías Abiertas / Master-specific Audit Logs Summary */}
        {user.role === 'MASTER' ? (
          <div className="glass-panel fintech-section" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
              Bitácora de Auditoría Reciente
            </h3>
            <div className="table-container">
              <table className="table-el">
                <thead>
                  <tr>
                    <th>Usuario</th>
                    <th>Acción</th>
                    <th>Detalles</th>
                    <th>Fecha y Hora</th>
                  </tr>
                </thead>
                <tbody>
                  {audits.length === 0 ? (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                        No hay logs de auditoría registrados.
                      </td>
                    </tr>
                  ) : (
                    audits.slice(0, 10).map((a) => (
                      <tr key={a.id}>
                        <td>
                          <strong>@{a.actorUser}</strong>
                          <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', display: 'block' }}>{a.role}</span>
                        </td>
                        <td>
                          <span className={`badge ${a.status === 'success' ? 'badge-success' : a.status === 'failed' ? 'badge-danger' : 'badge-primary'}`}>
                            {a.action}
                          </span>
                        </td>
                        <td style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>{a.details}</td>
                        <td>{new Date(a.timestampMs).toLocaleString()}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          /* Exposición y Loterías Abiertas (ADMIN / SUPERVISOR) */
          <div className="glass-panel fintech-section" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
              Horarios y Control Loterías
            </h3>
            
            {/* Draws Card Sub-tabs */}
            <div style={{ display: 'flex', borderBottom: '1px solid hsl(var(--border))', gap: '8px', marginBottom: '8px' }}>
              <button 
                onClick={() => setDrawsSubTab('lottery')}
                style={{
                  padding: '8px 12px',
                  border: 'none',
                  borderBottom: drawsSubTab === 'lottery' ? '2px solid hsl(var(--primary))' : '2px solid transparent',
                  background: 'transparent',
                  color: drawsSubTab === 'lottery' ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontSize: '0.8rem',
                  transition: 'all 0.2s ease'
                }}
              >
                Lotería Tradicional
              </button>
              <button 
                onClick={() => setDrawsSubTab('pick_sports')}
                style={{
                  padding: '8px 12px',
                  border: 'none',
                  borderBottom: drawsSubTab === 'pick_sports' ? '2px solid hsl(var(--primary))' : '2px solid transparent',
                  background: 'transparent',
                  color: drawsSubTab === 'pick_sports' ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontSize: '0.8rem',
                  transition: 'all 0.2s ease'
                }}
              >
                Picks y Deportes
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {lotteries
                .filter((l) => {
                  const isPick = l.type === 'Pick3' || l.type === 'Pick4' || l.name.startsWith('US-P') || l.id.startsWith('US-P');
                  
                  // Filter by drawsSubTab first
                  if (drawsSubTab === 'lottery' && isPick) return false;
                  if (drawsSubTab === 'pick_sports' && !isPick) return false;

                  const isModeEnabled = isPick
                    ? systemModeConfig.pickModeEnabled !== false
                    : systemModeConfig.lotteryModeEnabled !== false;
                  if (!isModeEnabled) return false;

                  // Hide closed lotteries from active dashboard list
                  const currentMinutes = getCurrentDRMinutesSinceMidnight();
                  const closeMinutes = parseTimeToMinutes(l.baseCloseTime);
                  const isTimeClosed = currentMinutes >= closeMinutes;
                  const isManuallyBlocked = manualDisabledLotteryIds.includes(l.id);
                  const isClosed = isTimeClosed || isManuallyBlocked;
                  return !isClosed;
                })
                .map((l) => {
                  const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === l.id);
                  const logoUrl = catalogEntry?.logoAssetPath || l.logoAssetPath || '/favicon.svg';
                  
                  const currentMinutes = getCurrentDRMinutesSinceMidnight();
                  const closeMinutes = parseTimeToMinutes(l.baseCloseTime);
                  const isTimeClosed = currentMinutes >= closeMinutes;
                  const isManuallyBlocked = manualDisabledLotteryIds.includes(l.id);
                  const isClosed = isTimeClosed || isManuallyBlocked;

                  return (
                    <div key={l.id} style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '12px',
                      borderRadius: 'var(--radius-md)',
                      backgroundColor: 'hsl(var(--background))',
                      borderLeft: `4px solid ${l.colorHex}`
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <img 
                          src={logoUrl} 
                          alt={l.name} 
                          style={{ width: '32px', height: '32px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                          onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                        />
                        <div>
                          <strong style={{ fontSize: '0.875rem', display: 'block' }}>{l.name}</strong>
                          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>
                            Cierre: {l.baseCloseTime} | Sorteo: {l.baseDrawTime}
                          </span>
                        </div>
                      </div>
                      
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        {isClosed ? (
                          <span className="badge" style={{ fontSize: '0.625rem', backgroundColor: 'hsl(var(--danger) / 0.1)', color: 'hsl(var(--danger))', border: '1px solid hsl(var(--danger) / 0.2)' }}>
                            {isManuallyBlocked ? 'Bloqueado Admin' : 'Cerrado'}
                          </span>
                        ) : (
                          <span className="badge badge-success" style={{ fontSize: '0.625rem' }}>
                            Abierto
                          </span>
                        )}
                        
                        {(user.role === 'ADMIN' || user.role === 'SUPERVISOR') && (
                          <button
                            onClick={() => handleToggleManualDisabledLottery(l.id)}
                            className="btn"
                            style={{
                              padding: '4px 8px',
                              fontSize: '0.7rem',
                              borderRadius: 'var(--radius-sm)',
                              cursor: 'pointer',
                              backgroundColor: isManuallyBlocked ? 'hsl(var(--success) / 0.1)' : 'hsl(var(--danger) / 0.1)',
                              color: isManuallyBlocked ? 'hsl(var(--success))' : 'hsl(var(--danger))',
                              border: `1px solid ${isManuallyBlocked ? 'hsl(var(--success) / 0.2)' : 'hsl(var(--danger) / 0.2)'}`,
                              fontWeight: 600,
                              transition: 'all 0.2s ease'
                            }}
                            title={isManuallyBlocked ? 'Habilitar Lotería' : 'Bloquear Lotería'}
                          >
                            {isManuallyBlocked ? 'Habilitar' : 'Bloquear'}
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}

              {drawsSubTab === 'pick_sports' && (
                <div className="glass-panel-premium table-row-stagger" style={{ 
                  padding: '16px', 
                  marginTop: '4px', 
                  display: 'flex', 
                  flexDirection: 'column', 
                  gap: '10px',
                  border: '1px solid hsl(var(--primary) / 0.2)' 
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <strong style={{ fontSize: '0.875rem', display: 'block', color: 'hsl(var(--text-primary))' }}>Banca Deportiva (Sportsbook)</strong>
                      <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))' }}>Límites globales configurados en la red</span>
                    </div>
                    <span className={`badge ${
                      systemModeConfig.sportsbookEnabled !== false ? 'badge-success badge-glow-success' : 'badge-danger badge-glow-danger'
                    }`} style={{ fontSize: '0.65rem' }}>
                      {systemModeConfig.sportsbookEnabled !== false ? 'Activo' : 'Desactivado'}
                    </span>
                  </div>
                  
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '0.75rem', backgroundColor: 'hsl(var(--background) / 0.6)', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid hsl(var(--border) / 0.5)' }}>
                    <div>
                      <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.65rem' }}>Riesgo Máx por Apuesta</span>
                      <strong>${sportsLimitsForm.max_ticket_stake.toLocaleString()}</strong>
                    </div>
                    <div>
                      <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.65rem' }}>Ganancia Máx Potencial</span>
                      <strong>${sportsLimitsForm.max_potential_payout.toLocaleString()}</strong>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
