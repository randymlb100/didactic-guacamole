import React from 'react';
import { RefreshCw, AlertTriangle } from 'lucide-react';
import type { UserAccount, SportsTicketRecord } from '../../types';
import { ActionButton, CompactField, CompactSelect, SearchInput } from '../../components/ui';
import { supabase, isSupabaseConfigured } from '../../utils/supabase';

type AuditUser = Pick<UserAccount, 'id' | 'user' | 'role'>;

const getErrorMessage = (error: unknown, fallback: string) => {
  return error instanceof Error ? error.message : fallback;
};

interface DeportivaTabProps {
  user: UserAccount;
  sportsTickets: SportsTicketRecord[];
  setSportsTickets: React.Dispatch<React.SetStateAction<SportsTicketRecord[]>>;
  users: UserAccount[];
  ticketFilterCashier: string;
  setTicketFilterCashier: (val: string) => void;
  ticketDateFilter: 'today' | 'yesterday' | 'all';
  setTicketDateFilter: (val: 'today' | 'yesterday' | 'all') => void;
  ticketFilterStatus: string;
  setTicketFilterStatus: (val: string) => void;
  ticketSearchSerial: string;
  setTicketSearchSerial: (val: string) => void;
  setSelectedSportsTicketForDetail: (ticket: SportsTicketRecord) => void;
  loadData: () => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  saveAllUsers: (users: UserAccount[]) => Promise<void>;
  addAuditLog: (user: AuditUser, action: string, msg: string, type?: "success" | "failed" | "warning") => Promise<void>;
}

export const DeportivaTab: React.FC<DeportivaTabProps> = ({
  user,
  sportsTickets,
  setSportsTickets,
  users,
  ticketFilterCashier,
  setTicketFilterCashier,
  ticketDateFilter,
  setTicketDateFilter,
  ticketFilterStatus,
  setTicketFilterStatus,
  ticketSearchSerial,
  setTicketSearchSerial,
  setSelectedSportsTicketForDetail,
  loadData,
  isSameLocalDate,
  saveAllUsers,
  addAuditLog,
}) => {
  let filtered = [...sportsTickets];

  // Scoping rules: MASTER sees all, ADMIN sees only their network's wagers
  if (user.role === 'ADMIN') {
    const cashierUsernames = users.filter(u => u.adminId === user.id).map(u => u.user);
    filtered = filtered.filter(t => cashierUsernames.includes(t.sellerUsername || ''));
  } else if (user.role === 'SUPERVISOR') {
    const cashierUsernames = users.filter(u => u.supervisorIds.includes(user.id)).map(u => u.user);
    filtered = filtered.filter(t => cashierUsernames.includes(t.sellerUsername || ''));
  }

  // Date Filter
  filtered = filtered.filter(t => {
    const epoch = Date.parse(t.soldAt);
    if (Number.isNaN(epoch)) return false;
    if (ticketDateFilter === 'today') {
      return isSameLocalDate(epoch, 0);
    } else if (ticketDateFilter === 'yesterday') {
      return isSameLocalDate(epoch, 1);
    }
    return true;
  });

  if (ticketFilterCashier !== 'all') {
    filtered = filtered.filter(t => t.sellerUsername === ticketFilterCashier);
  }

  // Exclude void by default on 'all' status
  if (ticketFilterStatus === 'all') {
    filtered = filtered.filter(t => t.status !== 'void');
  } else {
    filtered = filtered.filter(t => t.status === ticketFilterStatus);
  }

  if (ticketSearchSerial.trim()) {
    const query = ticketSearchSerial.trim().toLowerCase();
    filtered = filtered.filter(t => t.ticketCode.toLowerCase().includes(query));
  }

  return (
    <div className="fade-in glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>
            Monitoreo de Apuestas Deportivas
          </h3>
          <p style={{ fontSize: '0.8rem', color: 'hsl(var(--text-muted))', marginTop: '2px' }}>
            Administre, anule y procese el cobro de tickets de banca deportiva
          </p>
        </div>
        <ActionButton variant="info" onClick={loadData} icon={<RefreshCw size={16} />}>
          Actualizar Datos
        </ActionButton>
      </div>

      {/* Filters Bar */}
      <div style={{
        display: 'flex',
        gap: '12px',
        flexWrap: 'wrap',
        padding: '16px',
        borderRadius: 'var(--radius-md)',
        backgroundColor: 'hsl(var(--surface-hover))',
        border: '1px solid hsl(var(--border))'
      }}>
        <CompactField label="Cajero Emisor" style={{ minWidth: '150px' }}>
          <CompactSelect
            value={ticketFilterCashier}
            onChange={(e) => setTicketFilterCashier(e.target.value)}
          >
            <option value="all">Todos los cajeros</option>
            {users.filter(u => {
              if (u.role !== 'CASHIER') return false;
              if (user.role === 'MASTER') return true;
              if (user.role === 'ADMIN') return u.adminId === user.id;
              return u.supervisorIds.includes(user.id);
            }).map(c => (
              <option key={c.id} value={c.user}>@{c.user} - {c.displayName}</option>
            ))}
          </CompactSelect>
        </CompactField>

        <CompactField label="Fecha" style={{ minWidth: '150px' }}>
          <CompactSelect
            value={ticketDateFilter}
            onChange={(e) => setTicketDateFilter(e.target.value as 'today' | 'yesterday' | 'all')}
          >
            <option value="today">Hoy</option>
            <option value="yesterday">Ayer</option>
            <option value="all">Todos los días</option>
          </CompactSelect>
        </CompactField>

        <CompactField label="Estado" style={{ minWidth: '150px' }}>
          <CompactSelect
            value={ticketFilterStatus}
            onChange={(e) => setTicketFilterStatus(e.target.value)}
          >
            <option value="all">Todos los estados</option>
            <option value="pending">Pendientes</option>
            <option value="won">Ganadores (Sin Cobrar)</option>
            <option value="paid">Cobrados (Pagados)</option>
            <option value="lost">Perdedores</option>
            <option value="void">Anulados (Void)</option>
          </CompactSelect>
        </CompactField>

        <CompactField label="Buscar por Código" style={{ flex: 1, minWidth: '200px' }}>
          <SearchInput
            placeholder="Buscar por código de ticket..."
            value={ticketSearchSerial}
            onChange={(e) => setTicketSearchSerial(e.target.value)}
          />
        </CompactField>
      </div>

      {/* Tickets Table */}
      {filtered.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
          <AlertTriangle size={32} style={{ marginBottom: '12px', color: 'hsl(var(--warning))' }} />
          <p>No se encontraron apuestas deportivas con los filtros especificados.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="table-el">
            <thead>
              <tr>
                <th>Código</th>
                <th>Cajero</th>
                <th>Banca</th>
                <th>Tipo</th>
                <th>Jugadas (Parlay Legs)</th>
                <th>Monto</th>
                <th>Cuota</th>
                <th>Posible Premio</th>
                <th>Fecha</th>
                <th>Estado</th>
                <th style={{ textAlign: 'right' }}>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((t) => {
                const formattedDate = new Date(t.soldAt).toLocaleString('es-DO', {
                  day: '2-digit',
                  month: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                  hour12: true
                });

                return (
                  <tr
                    key={t.id}
                    onClick={() => setSelectedSportsTicketForDetail(t)}
                    style={{ cursor: 'pointer', transition: 'all 0.2s ease' }}
                    className="table-row-hover"
                  >
                    <td style={{ fontWeight: 700, fontFamily: 'monospace' }}>
                      {t.ticketCode}
                    </td>
                    <td style={{ fontWeight: 600 }}>@{t.sellerUsername}</td>
                    <td style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem' }}>{t.bancaName || 'N/A'}</td>
                    <td>
                      <span className={`badge ${t.ticketType === 'parlay' ? 'badge-primary' : 'badge-success'}`}>
                        {t.ticketType === 'parlay' ? 'Parlay' : 'Directa'}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.8rem', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {(t.legs || []).map(l => l.eventLabel).join(' | ') || 'Sin piernas'}
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      ${t.stake.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </td>
                    <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                      x{Number(t.decimalOdds).toFixed(2)}
                    </td>
                    <td style={{ color: 'hsl(var(--success))', fontWeight: 700 }}>
                      ${t.potentialPayout.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </td>
                    <td style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>
                      {formattedDate}
                    </td>
                    <td>
                      <span className={`badge ${
                        t.status === 'pending' ? 'badge-warning' :
                        t.status === 'won' ? 'badge-warning' :
                        t.status === 'paid' ? 'badge-success' :
                        t.status === 'lost' ? 'badge-danger' : 'badge-secondary'
                      }`}>
                        {t.status === 'pending' ? 'Pendiente' :
                         t.status === 'won' ? 'Premio Pendiente' :
                         t.status === 'paid' ? 'Cobrado' :
                         t.status === 'lost' ? 'Perdido' : 'Anulado'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                      <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                          onClick={() => setSelectedSportsTicketForDetail(t)}
                        >
                          Ver
                        </button>
                        {t.status === 'pending' && (
                          <button
                            className="btn btn-secondary"
                            style={{ padding: '4px 8px', fontSize: '0.75rem', color: 'hsl(var(--warning))', backgroundColor: 'hsl(var(--warning) / 0.1)', border: '1px solid hsl(var(--warning) / 0.3)' }}
                            onClick={async () => {
                              if (!window.confirm(`¿Está seguro de ANULAR administrativamente la apuesta ${t.ticketCode}? Se devolverá el balance de fianza al cajero.`)) return;
                              try {
                                if (isSupabaseConfigured && supabase) {
                                  const { error } = await supabase.functions.invoke('void-sports-ticket', {
                                    body: { ticketId: t.id, actorKey: user.user }
                                  });
                                  if (error) throw error;
                                } else {
                                  // Mock voiding in local storage
                                  const updated = sportsTickets.map(st => st.id === t.id ? { ...st, status: 'void' as const } : st);
                                  setSportsTickets(updated);
                                  localStorage.setItem('lotterynet_sports_tickets', JSON.stringify(updated));

                                  // Return balance to cashier in local storage mock
                                  const uList = [...users];
                                  const cashierIdx = uList.findIndex(u => u.user === t.sellerUsername);
                                  if (cashierIdx !== -1) {
                                    uList[cashierIdx].balance = Math.max(0, uList[cashierIdx].balance - t.stake);
                                    await saveAllUsers(uList);
                                  }
                                }
                                await addAuditLog(
                                  { id: user.id, user: user.user, role: user.role },
                                  'VOID_SPORTS_TICKET',
                                  `Apuesta deportiva anulada: ${t.ticketCode}. Se retornó $${t.stake} al cajero @${t.sellerUsername}`,
                                  'warning'
                                );
                                loadData();
                                alert('Apuesta deportiva anulada correctamente.');
                              } catch (e: unknown) {
                                alert(getErrorMessage(e, 'Error al anular apuesta deportiva'));
                              }
                            }}
                          >
                            Anular
                          </button>
                        )}
                        {t.status === 'won' && (
                          <button
                            className="btn btn-success"
                            style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                            onClick={async () => {
                              if (!window.confirm(`¿Está seguro de PAGAR el premio de $${t.potentialPayout} para la apuesta ${t.ticketCode}?`)) return;
                              try {
                                if (isSupabaseConfigured && supabase) {
                                  const { error } = await supabase.functions.invoke('pay-sports-ticket', {
                                    body: { ticketId: t.id, actorKey: user.user }
                                  });
                                  if (error) throw error;
                                } else {
                                  // Mock paying in local storage
                                  const updated = sportsTickets.map(st => st.id === t.id ? { ...st, status: 'paid' as const } : st);
                                  setSportsTickets(updated);
                                  localStorage.setItem('lotterynet_sports_tickets', JSON.stringify(updated));
                                }
                                await addAuditLog(
                                  { id: user.id, user: user.user, role: user.role },
                                  'PAY_SPORTS_TICKET',
                                  `Premio deportivo pagado: $${t.potentialPayout} para la apuesta ${t.ticketCode}`,
                                  'success'
                                );
                                loadData();
                                alert('Premio deportivo pagado y cobrado correctamente.');
                              } catch (e: unknown) {
                                alert(getErrorMessage(e, 'Error al pagar premio deportivo'));
                              }
                            }}
                          >
                            Pagar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
