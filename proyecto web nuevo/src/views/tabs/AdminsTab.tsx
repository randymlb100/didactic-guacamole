import React from 'react';
import { Plus, Lock, Unlock, RefreshCw, Trash2 } from 'lucide-react';
import type { UserAccount } from '../../types';
import { ActionButton, CompactSelect, DataToolbar, Panel, SearchInput, StatusBadge } from '../../components/ui';
import { cn } from '../../utils/classNames';

interface AdminsTabProps {
  filteredUsers: UserAccount[];
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  filterStatus: string;
  setFilterStatus: (val: 'all' | 'active' | 'blocked') => void;
  setAdminModalOpen: (open: boolean) => void;
  handleToggleAdmin: (id: string) => Promise<void>;
  handleRegenCreds: (admin: UserAccount) => void;
  handleDeleteBanca: (id: string) => Promise<void>;
}

export const AdminsTab: React.FC<AdminsTabProps> = ({
  filteredUsers,
  searchQuery,
  setSearchQuery,
  filterStatus,
  setFilterStatus,
  setAdminModalOpen,
  handleToggleAdmin,
  handleRegenCreds,
  handleDeleteBanca,
}) => {
  const admins = filteredUsers.filter(u => u.role === 'ADMIN');

  return (
    <div className="fade-in flex flex-col gap-5">
      <DataToolbar>
        <SearchInput
          wrapperClassName="min-w-[240px] flex-1 sm:!w-72 sm:flex-none"
          placeholder="Buscar banca o administrador..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />

        <CompactSelect
          className="!w-full sm:!w-36"
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value as 'all' | 'active' | 'blocked')}
        >
          <option value="all">Todos</option>
          <option value="active">Activos</option>
          <option value="blocked">Bloqueados</option>
        </CompactSelect>

        <ActionButton icon={<Plus size={16} />} onClick={() => setAdminModalOpen(true)} variant="primary">
          Crear Banca
        </ActionButton>
      </DataToolbar>

      <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(320px,1fr))]">
        {admins.length === 0 ? (
          <Panel className="col-span-full py-12 text-center text-sm text-ln-text-secondary">
            No se encontraron bancas registradas.
          </Panel>
        ) : (
          admins.map((a) => (
            <Panel
              key={a.id}
              className={cn(
                'table-row-stagger flex flex-col gap-4',
                !a.active && 'border-ln-danger/30 shadow-[0_8px_32px_hsl(var(--danger)/0.08)]',
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <span className="block text-[0.7rem] font-semibold uppercase text-ln-text-muted">ID: {a.id}</span>
                  <strong className="mt-0.5 block truncate text-lg text-ln-text-primary">{a.banca}</strong>
                  <span className="block truncate text-sm text-ln-text-secondary">@{a.user}</span>
                </div>

                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <StatusBadge tone={a.active ? 'success' : 'danger'}>
                    {a.active ? 'Activo' : 'Bloqueado'}
                  </StatusBadge>
                  <StatusBadge tone="primary">{a.cashierPrefix}</StatusBadge>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 rounded-ln-md border border-ln-border/50 bg-ln-background/60 p-3">
                <div>
                  <span className="block text-xs text-ln-text-secondary">Administrador</span>
                  <strong className="mt-1 block truncate text-sm font-semibold text-ln-text-primary">{a.displayName}</strong>
                </div>
                <div>
                  <span className="block text-xs text-ln-text-secondary">Teléfono</span>
                  <strong className="mt-1 block truncate text-sm font-semibold text-ln-text-primary">{a.phone || 'N/A'}</strong>
                </div>
                <div className="col-span-2">
                  <span className="block text-xs text-ln-text-secondary">Cupo Financiero</span>
                  <strong className="mt-1 block text-lg font-bold text-ln-text-primary">${a.rechargesBalance.toFixed(2)}</strong>
                </div>
              </div>

              <div className="text-xs text-ln-text-muted">
                Creado: {a.createdLabel}
              </div>

              <div className="mt-auto flex flex-wrap gap-2 border-t border-ln-border/50 pt-3">
                <ActionButton
                  className="flex-1"
                  icon={a.active ? <Lock size={14} /> : <Unlock size={14} />}
                  onClick={() => handleToggleAdmin(a.id)}
                  variant={a.active ? 'danger' : 'success'}
                >
                  {a.active ? 'Bloquear' : 'Activar'}
                </ActionButton>

                <ActionButton
                  className="flex-1"
                  icon={<RefreshCw size={14} />}
                  onClick={() => handleRegenCreds(a)}
                  title="Regenerar credenciales de acceso"
                  variant="info"
                >
                  Creds
                </ActionButton>

                <ActionButton
                  icon={<Trash2 size={14} />}
                  onClick={() => handleDeleteBanca(a.id)}
                  title="Eliminar banca de raíz"
                  variant="danger"
                />
              </div>
            </Panel>
          ))
        )}
      </div>
    </div>
  );
};
