import React from 'react';
import type { UserAccount, TicketRecord, LotteryCatalogItem } from '../../types';
import { STATIC_LOTTERIES } from '../../utils/supabase';
import { Panel, PanelHeader, StatusBadge } from '../../components/ui';
import { cn } from '../../utils/classNames';

interface MonitorRow {
  displayNumber: string;
  amount: number;
  playsCount: number;
  actors: string[];
}

function buildLotteryMonitorRows(tickets: TicketRecord[], playFocus: 'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4'): MonitorRow[] {
  const exposureMap: Record<string, { amount: number; playsCount: number; actors: Set<string> }> = {};

  tickets.forEach(ticket => {
    ticket.plays.forEach(play => {
      const type = play.playType.toUpperCase();
      let isMatch = false;
      if (playFocus === 'Q' && (type === 'Q' || type === 'QUINIELA')) isMatch = true;
      else if (playFocus === 'P' && (type === 'P' || type === 'PALE')) isMatch = true;
      else if (playFocus === 'T' && (type === 'T' || type === 'TRIPLETA')) isMatch = true;
      else if (playFocus === 'SP' && (type === 'SP' || type === 'SUPER PALE' || type === 'SUPERPALE')) isMatch = true;
      else if (playFocus === 'P3' && (type === 'P3' || type === 'PICK3' || type === 'PICK 3' || type === 'P3BOX')) isMatch = true;
      else if (playFocus === 'P4' && (type === 'P4' || type === 'PICK4' || type === 'PICK 4' || type === 'P4BOX')) isMatch = true;

      if (isMatch) {
        let formattedNumber = play.number.trim();
        
        if (playFocus === 'P' || playFocus === 'SP') {
          if (!formattedNumber.includes('-') && formattedNumber.length === 4) {
            formattedNumber = `${formattedNumber.slice(0, 2)}-${formattedNumber.slice(2, 4)}`;
          }
        } else if (playFocus === 'T') {
          if (!formattedNumber.includes('/') && !formattedNumber.includes('-') && formattedNumber.length === 6) {
            formattedNumber = `${formattedNumber.slice(0, 2)}/${formattedNumber.slice(2, 4)}/${formattedNumber.slice(4, 6)}`;
          } else {
            formattedNumber = formattedNumber.replace(/-/g, '/');
          }
        }

        if (!exposureMap[formattedNumber]) {
          exposureMap[formattedNumber] = { amount: 0, playsCount: 0, actors: new Set<string>() };
        }

        exposureMap[formattedNumber].amount += play.amount;
        exposureMap[formattedNumber].playsCount += 1;
        if (ticket.sellerUser) {
          exposureMap[formattedNumber].actors.add(ticket.sellerUser);
        }
      }
    });
  });

  const list = Object.keys(exposureMap).map(num => ({
    displayNumber: num,
    amount: exposureMap[num].amount,
    playsCount: exposureMap[num].playsCount,
    actors: Array.from(exposureMap[num].actors)
  }));

  return list.sort((a, b) => {
    if (b.amount !== a.amount) {
      return b.amount - a.amount;
    }
    return a.displayNumber.localeCompare(b.displayNumber);
  });
}

interface MonitoreoTabProps {
  user: UserAccount;
  tickets: TicketRecord[];
  users: UserAccount[];
  lotteries: LotteryCatalogItem[];
  monitoreoSubTab: 'lotteries' | 'plays' | 'ranking' | 'cajeros';
  setMonitoreoSubTab: (subTab: 'lotteries' | 'plays' | 'ranking' | 'cajeros') => void;
  monitoreoPlayFocus: 'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4';
  setMonitoreoPlayFocus: (focus: 'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4') => void;
  monitoreoHighestFirst: boolean;
  setMonitoreoHighestFirst: (highest: boolean) => void;
  monitoreoShowEmptyLotteries: boolean;
  setMonitoreoShowEmptyLotteries: (show: boolean) => void;
  monitoreoRange: 'day' | 'week' | 'month';
  setMonitoreoRange: (range: 'day' | 'week' | 'month') => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  cashierSalesTotals: Record<string, number>;
}

export const MonitoreoTab: React.FC<MonitoreoTabProps> = ({
  user,
  tickets,
  users,
  lotteries,
  monitoreoSubTab,
  setMonitoreoSubTab,
  monitoreoPlayFocus,
  setMonitoreoPlayFocus,
  monitoreoHighestFirst,
  setMonitoreoHighestFirst,
  monitoreoShowEmptyLotteries,
  setMonitoreoShowEmptyLotteries,
  monitoreoRange,
  setMonitoreoRange,
  isSameLocalDate,
  cashierSalesTotals,
}) => {
  const subTabs = [
    { id: 'lotteries', label: 'Ventas por Lotería' },
    { id: 'plays', label: 'Números Más Jugados' },
    { id: 'ranking', label: 'Ranking' },
    { id: 'cajeros', label: 'Presencia de Cajeros' },
  ] as const;
  const ranges = [
    { id: 'day', label: 'Hoy' },
    { id: 'week', label: 'Semana' },
    { id: 'month', label: 'Mes' },
  ] as const;

  return (
    <div className="fade-in flex flex-col gap-6">
      {/* Monitoreo Top Controls */}
      <Panel className="flex flex-col gap-4">
        <PanelHeader
          title="Monitoreo de Red y Loterías"
          subtitle="Audita las ventas de cajeros, loterías activas y la exposición acumulada de números en tiempo real."
          action={
            <StatusBadge tone="success">
              <span className="size-1.5 rounded-full bg-white animate-pulse" />
              Sincronizado
            </StatusBadge>
          }
        />

        <div className="flex flex-wrap gap-2 border-b border-ln-border pb-4">
          {subTabs.map((subTab) => (
            <button
              key={subTab.id}
              onClick={() => setMonitoreoSubTab(subTab.id)}
              className={cn(
                'rounded-ln-md border px-4 py-2.5 text-sm font-semibold transition-colors',
                monitoreoSubTab === subTab.id
                  ? 'border-ln-primary bg-ln-primary/10 text-ln-primary'
                  : 'border-ln-border text-ln-text-secondary hover:border-ln-primary/50 hover:text-ln-text-primary',
              )}
            >
              {subTab.label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-2">
            {ranges.map((r) => (
              <button
                key={r.id}
                onClick={() => setMonitoreoRange(r.id)}
                className={cn(
                  'rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors',
                  monitoreoRange === r.id
                    ? 'border-ln-primary bg-ln-primary text-white'
                    : 'border-ln-border bg-ln-surface text-ln-text-secondary hover:text-ln-text-primary',
                )}
              >
                {r.label}
              </button>
            ))}
          </div>

          <label className="flex items-center gap-2 text-sm text-ln-text-secondary">
            <span>Mostrar Loterías Vacías:</span>
            <input
              type="checkbox"
              checked={monitoreoShowEmptyLotteries}
              onChange={(e) => setMonitoreoShowEmptyLotteries(e.target.checked)}
              className="size-4 cursor-pointer accent-ln-primary"
            />
          </label>
        </div>
      </Panel>

      {/* Sub-Tab 1: LOTTERIES BREAKDOWN */}
      {monitoreoSubTab === 'lotteries' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {(() => {
            const scopedTickets = tickets.filter(t => {
              if (t.status === 'cancelled' || t.status === 'voided') return false;
              if (monitoreoRange === 'day') {
                return isSameLocalDate(t.createdAtEpochMs, 0);
              } else if (monitoreoRange === 'week') {
                return (Date.now() - t.createdAtEpochMs) <= (7 * 86400000);
              } else {
                return (Date.now() - t.createdAtEpochMs) <= (30 * 86400000);
              }
            });

            // Build lottery wagers
            const lotteryWagers = lotteries.map(l => {
              let q = 0, p = 0, t = 0, sp = 0, pick = 0, total = 0;
              scopedTickets.forEach(tk => {
                tk.plays.forEach(play => {
                  if (play.lotteryId === l.id || play.secondaryLotteryId === l.id) {
                    const amt = play.amount;
                    total += amt;
                    const type = play.playType.toUpperCase();
                    if (type === 'Q' || type === 'QUINIELE') q += amt;
                    else if (type === 'P' || type === 'PALE') p += amt;
                    else if (type === 'T' || type === 'TRIPLETA') t += amt;
                    else if (type === 'SP' || type === 'SUPER PALE') sp += amt;
                    else if (['P3', 'P3BOX', 'P4', 'P4BOX'].includes(type)) pick += amt;
                  }
                });
              });
              return { lottery: l, q, p, t, sp, pick, total };
            }).filter((item: any) => monitoreoShowEmptyLotteries || item.total > 0)
              .sort((a: any, b: any) => b.total - a.total);

            const grandTotal = lotteryWagers.reduce((acc: number, item: any) => acc + item.total, 0);

            if (lotteryWagers.length === 0) {
              return (
                <div className="glass-panel" style={{ padding: '40px', textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                  No hay ventas registradas para este periodo de monitoreo.
                </div>
              );
            }

            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {lotteryWagers.map((item: any) => {
                  const percent = grandTotal > 0 ? (item.total / grandTotal) * 100 : 0;
                  const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === item.lottery.id);
                  const logoUrl = catalogEntry?.logoAssetPath || item.lottery.logoAssetPath || '/favicon.svg';

                  return (
                    <div key={item.lottery.id} className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <img 
                            src={logoUrl} 
                            alt={item.lottery.name} 
                            style={{ width: '36px', height: '36px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                            onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                          />
                          <div>
                            <strong style={{ fontSize: '1rem', display: 'block' }}>{item.lottery.name}</strong>
                            <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>({item.lottery.territory})</span>
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <strong style={{ fontSize: '1.05rem', color: 'hsl(var(--primary))' }}>${item.total.toFixed(2)}</strong>
                          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', display: 'block' }}>{percent.toFixed(1)}% del total</span>
                        </div>
                      </div>

                      {/* Progress bar */}
                      <div style={{ width: '100%', height: '6px', borderRadius: '3px', backgroundColor: 'hsl(var(--border))', overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${percent}%`, backgroundColor: item.lottery.colorHex, borderRadius: '3px' }} />
                      </div>

                      {/* Breakdown */}
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))', gap: '12px', fontSize: '0.8rem', marginTop: '4px' }}>
                        <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                          <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Quiniela</span>
                          <strong>${item.q.toFixed(2)}</strong>
                        </div>
                        <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                          <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Palé</span>
                          <strong>${item.p.toFixed(2)}</strong>
                        </div>
                        <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                          <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Super Palé</span>
                          <strong>${item.sp.toFixed(2)}</strong>
                        </div>
                        <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                          <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Tripleta</span>
                          <strong>${item.t.toFixed(2)}</strong>
                        </div>
                        <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                          <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Pick 3/4</span>
                          <strong>${item.pick.toFixed(2)}</strong>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            );
          })()}
        </div>
      )}

      {monitoreoSubTab === 'plays' && (
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {[
                { id: 'Q', label: 'Quiniela' },
                { id: 'P', label: 'Pale' },
                { id: 'SP', label: 'Super Pale' },
                { id: 'T', label: 'Tripleta' },
                { id: 'P3', label: 'Pick 3' },
                { id: 'P4', label: 'Pick 4' },
              ].map((view) => (
                <button
                  key={view.id}
                  onClick={() => setMonitoreoPlayFocus(view.id as any)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid ' + (monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                    background: monitoreoPlayFocus === view.id ? 'hsl(var(--primary) / 0.06)' : 'transparent',
                    color: monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  {view.label}
                </button>
              ))}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>Mayor Apuesta Primero:</span>
              <input
                type="checkbox"
                checked={monitoreoHighestFirst}
                onChange={(e) => setMonitoreoHighestFirst(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
            </div>
          </div>

          {(() => {
            const scopedTickets = tickets.filter(t => {
              if (t.status === 'cancelled' || t.status === 'voided') return false;
              if (monitoreoRange === 'day') {
                return isSameLocalDate(t.createdAtEpochMs, 0);
              } else if (monitoreoRange === 'week') {
                return (Date.now() - t.createdAtEpochMs) <= (7 * 86400000);
              } else {
                return (Date.now() - t.createdAtEpochMs) <= (30 * 86400000);
              }
            });

            let ranking = buildLotteryMonitorRows(scopedTickets, monitoreoPlayFocus);
            if (!monitoreoHighestFirst) {
              ranking = ranking.sort((a: any, b: any) => a.amount - b.amount);
            }

            if (ranking.length === 0) {
              return (
                <div style={{ padding: '30px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
                  No hay números apostados para esta combinación en el periodo seleccionado.
                </div>
              );
            }

            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {ranking.map((row: any) => (
                  <div
                    key={row.displayNumber}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '12px 16px',
                      borderRadius: 'var(--radius-sm)',
                      backgroundColor: 'hsl(var(--background))',
                      border: '1px solid hsl(var(--border))'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                      <span style={{
                        padding: '8px 14px',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'hsl(var(--primary) / 0.1)',
                        color: 'hsl(var(--primary))',
                        fontWeight: 700,
                        fontSize: '1rem',
                        fontFamily: 'monospace'
                      }}>
                        {row.displayNumber}
                      </span>
                      <div>
                        <strong style={{ fontSize: '0.9rem', display: 'block' }}>Apostado: ${row.amount.toFixed(2)}</strong>
                        <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                          {row.playsCount} jugadas · Cajeros: {row.actors.join(', ') || 'sin cajero'}
                        </span>
                      </div>
                    </div>
                    <span className="badge badge-success" style={{ fontSize: '0.75rem' }}>
                      {row.playsCount} veces
                    </span>
                  </div>
                ))}
              </div>
            );
          })()}
        </div>
      )}

      {monitoreoSubTab === 'ranking' && (
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '1.05rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>
                Leaderboard de Números (Ranking)
              </span>
              <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                Números más jugados ordenados por frecuencia de apuestas en la red
              </span>
            </div>

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {[
                { id: 'Q', label: 'Quiniela' },
                { id: 'P', label: 'Pale' },
                { id: 'SP', label: 'Super Pale' },
                { id: 'T', label: 'Tripleta' },
                { id: 'P3', label: 'Pick 3' },
                { id: 'P4', label: 'Pick 4' },
              ].map((view) => (
                <button
                  key={view.id}
                  onClick={() => setMonitoreoPlayFocus(view.id as any)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid ' + (monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                    background: monitoreoPlayFocus === view.id ? 'hsl(var(--primary) / 0.06)' : 'transparent',
                    color: monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  {view.label}
                </button>
              ))}
            </div>
          </div>

          {(() => {
            const scopedTickets = tickets.filter(t => {
              if (t.status === 'cancelled' || t.status === 'voided') return false;
              if (monitoreoRange === 'day') {
                return isSameLocalDate(t.createdAtEpochMs, 0);
              } else if (monitoreoRange === 'week') {
                return (Date.now() - t.createdAtEpochMs) <= (7 * 86400000);
              } else {
                return (Date.now() - t.createdAtEpochMs) <= (30 * 86400000);
              }
            });

            let ranking = buildLotteryMonitorRows(scopedTickets, monitoreoPlayFocus);
            
            // Sort by playsCount desc, then amount desc
            ranking.sort((a: any, b: any) => {
              if (b.playsCount !== a.playsCount) {
                return b.playsCount - a.playsCount;
              }
              return b.amount - a.amount;
            });

            if (ranking.length === 0) {
              return (
                <div style={{ padding: '30px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
                  No hay números registrados para el ranking en el período seleccionado.
                </div>
              );
            }

            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {/* Podium Top 3 layout */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.1fr 1fr', gap: '16px', marginBottom: '16px', alignItems: 'end', maxWidth: '640px', margin: '0 auto', width: '100%' }}>
                  {/* 2nd Place */}
                  {ranking[1] ? (
                    <div className="glass-panel" style={{ padding: '16px', textAlign: 'center', borderTop: '4px solid #c0c0c0', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', height: '140px', justifyContent: 'center', boxShadow: 'none' }}>
                      <span style={{ fontSize: '1.5rem' }}>🥈</span>
                      <strong style={{ fontSize: '1.25rem', fontFamily: 'monospace', color: '#c0c0c0' }}>{ranking[1].displayNumber}</strong>
                      <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{ranking[1].playsCount} veces</span>
                      <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>${ranking[1].amount.toFixed(0)}</span>
                    </div>
                  ) : <div />}

                  {/* 1st Place */}
                  {ranking[0] ? (
                    <div className="glass-panel" style={{ padding: '20px', textAlign: 'center', borderTop: '5px solid #ffd700', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', height: '170px', justifyContent: 'center', boxShadow: '0 8px 32px hsl(var(--primary) / 0.15)' }}>
                      <span style={{ fontSize: '2rem' }}>🥇</span>
                      <strong style={{ fontSize: '1.6rem', fontFamily: 'monospace', color: '#ffd700', textShadow: '0 0 10px rgba(255, 215, 0, 0.3)' }}>{ranking[0].displayNumber}</strong>
                      <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>{ranking[0].playsCount} veces</span>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>${ranking[0].amount.toFixed(0)}</span>
                    </div>
                  ) : <div />}

                  {/* 3rd Place */}
                  {ranking[2] ? (
                    <div className="glass-panel" style={{ padding: '16px', textAlign: 'center', borderTop: '4px solid #cd7f32', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', height: '120px', justifyContent: 'center', boxShadow: 'none' }}>
                      <span style={{ fontSize: '1.25rem' }}>🥉</span>
                      <strong style={{ fontSize: '1.15rem', fontFamily: 'monospace', color: '#cd7f32' }}>{ranking[2].displayNumber}</strong>
                      <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{ranking[2].playsCount} veces</span>
                      <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>${ranking[2].amount.toFixed(0)}</span>
                    </div>
                  ) : <div />}
                </div>

                {/* List from 4th place onwards */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {ranking.slice(3).map((row: any, idx: number) => {
                    const position = idx + 4;
                    return (
                      <div
                        key={row.displayNumber}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '10px 16px',
                          borderRadius: 'var(--radius-md)',
                          backgroundColor: 'hsl(var(--surface) / 0.4)',
                          border: '1px solid hsl(var(--border) / 0.5)'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                          <span style={{
                            fontSize: '0.85rem',
                            fontWeight: 700,
                            color: 'hsl(var(--text-secondary))',
                            width: '24px',
                            textAlign: 'center'
                          }}>
                            #{position}
                          </span>
                          <span style={{
                            padding: '4px 10px',
                            borderRadius: 'var(--radius-sm)',
                            backgroundColor: 'hsl(var(--primary) / 0.08)',
                            color: 'hsl(var(--text-primary))',
                            fontWeight: 700,
                            fontSize: '0.95rem',
                            fontFamily: 'monospace'
                          }}>
                            {row.displayNumber}
                          </span>
                          <div>
                            <span style={{ fontSize: '0.825rem', color: 'hsl(var(--text-primary))', fontWeight: 600 }}>
                              {row.playsCount} jugadas
                            </span>
                            <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginLeft: '8px' }}>
                              Monto: ${row.amount.toFixed(0)}
                            </span>
                          </div>
                        </div>
                        <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-muted))' }}>
                          Cajeros: {row.actors.join(', ') || 'sin cajero'}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })()}
        </div>
      )}

      {/* Sub-Tab 3: CASHIER PRESENCE & ONLINE STATUS */}
      {monitoreoSubTab === 'cajeros' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
          {users.filter(u => {
            if (user.role === 'MASTER') return u.role === 'CASHIER';
            if (user.role === 'ADMIN') return (u.role === 'CASHIER' || u.role === 'ADMIN') && (u.adminId === user.id || u.id === user.id);
            return u.role === 'CASHIER' && u.supervisorIds.includes(user.id);
          }).map((c) => {
            const salesTotalToday = cashierSalesTotals[c.user] || 0;
            const presence = !c.active ? 'Bloqueado' : (salesTotalToday > 0 ? 'Activo' : 'Sin movimiento');

            return (
              <div key={c.id} className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <strong style={{ fontSize: '1rem', display: 'block' }}>{c.displayName || c.user}</strong>
                    <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>@{c.user}</span>
                  </div>
                  <span className={`badge ${
                    presence === 'Activo' ? 'badge-success' : presence === 'Bloqueado' ? 'badge-danger' : 'badge-secondary'
                  }`}>
                    {presence}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', fontSize: '0.825rem' }}>
                  <div style={{ backgroundColor: 'hsl(var(--background))', padding: '10px', borderRadius: 'var(--radius-sm)' }}>
                    <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Balance Caja</span>
                    <strong>${c.balance.toFixed(2)}</strong>
                  </div>
                  <div style={{ backgroundColor: 'hsl(var(--background))', padding: '10px', borderRadius: 'var(--radius-sm)' }}>
                    <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Balance Recargas</span>
                    <strong>${c.rechargesBalance.toFixed(2)}</strong>
                  </div>
                </div>

                <div style={{ borderTop: '1px solid hsl(var(--border))', paddingTop: '10px', fontSize: '0.75rem', color: 'hsl(var(--text-muted))', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>Modo de Juego:</span>
                    <strong style={{ color: 'hsl(var(--text-primary))' }}>
                      {c.systemModeOverride === 'lottery' ? 'Solo Lotería' : 
                       c.systemModeOverride === 'pick' ? 'Solo Pick' : 
                       c.systemModeOverride === 'both' ? 'Lotería + Pick' : 'Heredado'}
                    </strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>Comisión Ventas:</span>
                    <strong style={{ color: 'hsl(var(--text-primary))' }}>
                      {c.commissionRate !== undefined && c.commissionRate !== null ? c.commissionRate : 8.0}%
                    </strong>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
