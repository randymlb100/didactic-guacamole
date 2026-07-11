import React from 'react';
import type { UserAccount, TicketRecord } from '../../types';
import { CompactSelect, DataToolbar, Panel, PanelHeader, SearchInput, StatusBadge, TableActionButton } from '../../components/ui';

type TicketDateFilter = 'today' | 'yesterday' | 'all';

interface TicketsTabProps {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  ticketSearchSerial: string;
  setTicketSearchSerial: (v: string) => void;
  ticketFilterStatus: string;
  setFilterStatus: (v: string) => void; // mapped to ticketFilterStatus
  ticketFilterCashier: string;
  setTicketFilterCashier: (v: string) => void;
  ticketDateFilter: TicketDateFilter;
  setTicketDateFilter: (v: TicketDateFilter) => void;
  isSameLocalDate: (epochMs: number, relativeDays: number) => boolean;
  setSelectedTicketForDetail: (t: TicketRecord | null) => void;
  setSelectedTicketForAnnul: (t: TicketRecord | null) => void;
  setAnnulModalOpen: (open: boolean) => void;
  setSelectedTicketForDelete: (t: TicketRecord | null) => void;
  setDeleteModalOpen: (open: boolean) => void;
  handlePayWinner: (t: TicketRecord) => Promise<void>;
}

export const TicketsTab: React.FC<TicketsTabProps> = ({
  user,
  users,
  tickets,
  ticketSearchSerial,
  setTicketSearchSerial,
  ticketFilterStatus,
  setFilterStatus,
  ticketFilterCashier,
  setTicketFilterCashier,
  ticketDateFilter,
  setTicketDateFilter,
  isSameLocalDate,
  setSelectedTicketForDetail,
  setSelectedTicketForAnnul,
  setAnnulModalOpen,
  setSelectedTicketForDelete,
  setDeleteModalOpen,
  handlePayWinner
}) => {
  const isSupervisor = user.role === 'SUPERVISOR';
  const isMaster = user.role === 'MASTER';
  const allowedAdminId = isSupervisor ? user.adminId : user.id;
  const supervisedCashierUsers = isSupervisor
    ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id)).map(u => u.user)
    : [];

  const filtered = tickets
    .filter(t => (isMaster ? true : t.adminId === allowedAdminId))
    .filter(t => {
      if (isSupervisor && (!t.sellerUser || !supervisedCashierUsers.includes(t.sellerUser))) return false;
      if (
        ticketSearchSerial &&
        !t.id.toLowerCase().includes(ticketSearchSerial.toLowerCase()) &&
        !t.serial?.toLowerCase().includes(ticketSearchSerial.toLowerCase())
      ) {
        return false;
      }

      // Date Filter
      if (ticketDateFilter === 'today') {
        if (!isSameLocalDate(t.createdAtEpochMs, 0)) return false;
      } else if (ticketDateFilter === 'yesterday') {
        if (!isSameLocalDate(t.createdAtEpochMs, 1)) return false;
      }

      // Exclude cancelled/voided tickets by default from 'all' view
      if (ticketFilterStatus === 'all') {
        if (t.status === 'cancelled' || t.status === 'voided') return false;
      } else if (t.status !== ticketFilterStatus) {
        return false;
      }

      if (ticketFilterCashier !== 'all' && t.sellerUser !== ticketFilterCashier) return false;
      return true;
    });

  const statusTone = (status: string): 'primary' | 'success' | 'warning' | 'neutral' => {
    if (status === 'paid') return 'success';
    if (status === 'winner') return 'warning';
    if (status === 'cancelled' || status === 'voided') return 'neutral';
    return 'primary';
  };

  const statusLabel = (status: string): string => {
    if (status === 'paid') return 'Cobrado';
    if (status === 'cancelled' || status === 'voided') return 'Anulado';
    if (status === 'winner') return 'Premio Pendiente';
    return 'Activo';
  };

  return (
    <Panel className="fade-in flex flex-col gap-5">
      <PanelHeader
        title="Tickets"
        subtitle="Visualiza las transacciones de ventas y realiza anulaciones dentro del plazo permitido para restablecer balances de caja."
      />

      {/* Filters topbar */}
      <DataToolbar title="Filtros" subtitle={`${filtered.length} tickets visibles`}>
        <SearchInput
          wrapperClassName="min-w-[220px] flex-1 sm:!w-[260px] sm:flex-none"
          placeholder="Buscar por serie o ID..."
          value={ticketSearchSerial}
          onChange={(e) => setTicketSearchSerial(e.target.value)}
        />

        <CompactSelect
          value={ticketDateFilter}
          onChange={(e) => setTicketDateFilter(e.target.value as TicketDateFilter)}
          className="!w-full sm:!w-40"
        >
          <option value="today">Hoy</option>
          <option value="yesterday">Ayer</option>
          <option value="all">Todos los días</option>
        </CompactSelect>

        <CompactSelect
          className="!w-full sm:!w-40"
          value={ticketFilterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
        >
          <option value="all">Todos los Estados</option>
          <option value="active">Activos</option>
          <option value="paid">Cobrados</option>
          <option value="cancelled">Anulados / Voided</option>
          <option value="winner">Premiados</option>
        </CompactSelect>

        <CompactSelect
          className="!w-full sm:!w-56"
          value={ticketFilterCashier}
          onChange={(e) => setTicketFilterCashier(e.target.value)}
        >
          <option value="all">Todos los Puntos</option>
          {users.filter(u => {
            if (user.role === 'MASTER') return u.role === 'CASHIER' || u.role === 'ADMIN';
            if (user.role === 'ADMIN') return (u.role === 'CASHIER' || u.role === 'ADMIN') && (u.adminId === user.id || u.id === user.id);
            return u.role === 'CASHIER' && u.supervisorIds.includes(user.id);
          }).map(c => (
            <option key={c.id} value={c.user}>@{c.user} {c.role === 'ADMIN' ? '(Banca/Admin)' : ''}</option>
          ))}
        </CompactSelect>
      </DataToolbar>

      {/* Table of tickets */}
      {filtered.length === 0 ? (
        <div className="rounded-ln-md border border-dashed border-ln-border bg-ln-background/45 p-8 text-center text-sm text-ln-text-secondary">
          No se encontraron tickets con los filtros aplicados.
        </div>
      ) : (
        <div className="table-container">
          <table className="table-el">
            <thead>
              <tr>
                <th>Serie / Ticket ID</th>
                <th>Cajero</th>
                <th>Fecha y Hora</th>
                <th>Jugadas Realizadas</th>
                <th>Monto</th>
                <th>Premio</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((t) => (
                <tr key={t.id} onClick={() => setSelectedTicketForDetail(t)} className="cursor-pointer">
                  <td>
                    <div className="flex flex-col">
                      <strong className="text-ln-text-primary">{t.serial || t.id}</strong>
                      <span className="text-xs text-ln-text-secondary">ID: {t.id}</span>
                    </div>
                  </td>
                  <td>@{t.sellerUser}</td>
                  <td>{new Date(t.createdAtEpochMs).toLocaleString()}</td>
                  <td>
                    <div className="max-h-[60px] max-w-[280px] overflow-y-auto break-words rounded-ln-sm border border-ln-border/30 bg-white/[0.02] px-2 py-1 text-xs text-ln-text-secondary">
                      {t.plays.map(p => `${p.playType.toUpperCase()} ${p.number} ($${p.amount})`).join(' · ')}
                    </div>
                  </td>
                  <td className="font-semibold">${t.total.toFixed(2)}</td>
                  <td className={t.totalPrize > 0 ? 'font-semibold text-ln-danger' : 'font-semibold'}>
                    ${t.totalPrize.toFixed(2)}
                  </td>
                  <td>
                    <StatusBadge tone={statusTone(t.status)}>{statusLabel(t.status)}</StatusBadge>
                  </td>
                  <td>
                    <div className="flex flex-wrap gap-2">
                      <TableActionButton
                        tone="info"
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedTicketForDetail(t);
                        }}
                      >
                        Ver
                      </TableActionButton>
                      {t.status === 'active' && (
                        <TableActionButton
                          tone="warning"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedTicketForAnnul(t);
                            setAnnulModalOpen(true);
                          }}
                        >
                          Anular
                        </TableActionButton>
                      )}
                      {t.status === 'winner' && (
                        <TableActionButton
                          tone="success"
                          onClick={(e) => {
                            e.stopPropagation();
                            handlePayWinner(t);
                          }}
                        >
                          Pagar
                        </TableActionButton>
                      )}
                      {(user.role === 'ADMIN' || user.role === 'MASTER') && (
                        <TableActionButton
                          tone="danger"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedTicketForDelete(t);
                            setDeleteModalOpen(true);
                          }}
                        >
                          Eliminar
                        </TableActionButton>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
};
