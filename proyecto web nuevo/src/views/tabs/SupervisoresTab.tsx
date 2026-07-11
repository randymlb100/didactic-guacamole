import React from 'react';
import { Plus, Users, Key, Lock, Unlock, Trash2 } from 'lucide-react';
import type { UserAccount } from '../../types';
import { ActionButton, Panel, PanelHeader, StatusBadge } from '../../components/ui';
import { cn } from '../../utils/classNames';

interface SupervisoresTabProps {
  user: UserAccount;
  users: UserAccount[];
  setSupervisorModalOpen: (open: boolean) => void;
  handleOpenAssignModal: (supervisor: UserAccount) => void;
  handleResetSupervisorPassword: (supervisor: UserAccount) => void;
  handleToggleSupervisor: (supervisor: UserAccount) => Promise<void>;
  handleDeleteSupervisor: (supervisor: UserAccount) => Promise<void>;
}

export const SupervisoresTab: React.FC<SupervisoresTabProps> = ({
  user,
  users,
  setSupervisorModalOpen,
  handleOpenAssignModal,
  handleResetSupervisorPassword,
  handleToggleSupervisor,
  handleDeleteSupervisor,
}) => {
  const supervisores = users.filter(u => u.role === 'SUPERVISOR' && (user.role === 'MASTER' ? true : u.adminId === user.id));

  return (
    <div className="fade-in flex flex-col gap-5">
      <PanelHeader
        title="Lista de Supervisores Asignados"
        action={
          <ActionButton icon={<Plus size={16} />} onClick={() => setSupervisorModalOpen(true)} variant="primary">
            Crear Supervisor
          </ActionButton>
        }
      />

      <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(320px,1fr))]">
        {supervisores.length === 0 ? (
          <Panel className="col-span-full py-12 text-center text-sm text-ln-text-secondary">
            No hay supervisores asociados a tu banca.
          </Panel>
        ) : (
          supervisores.map((s) => {
            const assignedCashiersCount = users.filter(u => u.role === 'CASHIER' && u.supervisorIds?.includes(s.id)).length;
            return (
              <Panel
                key={s.id}
                className={cn(
                  'table-row-stagger flex flex-col gap-4',
                  !s.active && 'border-ln-danger/30 shadow-[0_8px_32px_hsl(var(--danger)/0.08)]',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <span className="block text-[0.7rem] font-semibold uppercase text-ln-text-muted">ID: {s.id}</span>
                    <strong className="mt-0.5 block truncate text-lg text-ln-text-primary">{s.displayName}</strong>
                    <span className="block truncate text-sm text-ln-text-secondary">@{s.user}</span>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-1.5">
                    <StatusBadge tone={s.active ? 'success' : 'danger'}>
                      {s.active ? 'Activo' : 'Bloqueado'}
                    </StatusBadge>
                    <StatusBadge tone="primary">{s.territory || 'N/A'}</StatusBadge>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 rounded-ln-md border border-ln-border/50 bg-ln-background/60 p-3">
                  <div>
                    <span className="block text-xs text-ln-text-secondary">Cajeros a Cargo</span>
                    <div className="mt-1 flex items-center gap-2">
                      <Users size={16} className="text-ln-primary" />
                      <strong className="text-lg text-ln-text-primary">{assignedCashiersCount}</strong>
                    </div>
                  </div>
                  <div>
                    <span className="block text-xs text-ln-text-secondary">Teléfono</span>
                    <strong className="mt-1 block truncate text-sm font-semibold text-ln-text-primary">{s.phone || 'N/A'}</strong>
                  </div>
                </div>

                <div className="text-xs text-ln-text-muted">
                  Registrado: {s.createdLabel || 'N/A'}
                </div>

                <div className="mt-auto flex flex-wrap gap-2 border-t border-ln-border/50 pt-3">
                  <ActionButton
                    className="flex-1"
                    icon={<Users size={14} />}
                    onClick={() => handleOpenAssignModal(s)}
                    title="Asignar y desasignar cajeros bajo la tutela del supervisor"
                    variant="info"
                  >
                    Asignar
                  </ActionButton>

                  <ActionButton
                    className="flex-1"
                    icon={<Key size={14} />}
                    onClick={() => handleResetSupervisorPassword(s)}
                    title="Restablecer contraseña de acceso"
                    variant="warning"
                  >
                    Clave
                  </ActionButton>

                  <ActionButton
                    icon={s.active ? <Lock size={14} /> : <Unlock size={14} />}
                    onClick={() => handleToggleSupervisor(s)}
                    title={s.active ? 'Bloquear Supervisor' : 'Activar Supervisor'}
                    variant={s.active ? 'danger' : 'success'}
                  />

                  <ActionButton
                    icon={<Trash2 size={14} />}
                    onClick={() => handleDeleteSupervisor(s)}
                    title="Eliminar Supervisor"
                    variant="danger"
                  />
                </div>
              </Panel>
            );
          })
        )}
      </div>
    </div>
  );
};
