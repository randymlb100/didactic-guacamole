import React from 'react';
import type { UserAccount } from '../types';
import { ActionButton, ModalCard, ModalShell, PanelHeader } from './ui';

interface AssignCashiersModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedSupervisor: UserAccount | null;
  users: UserAccount[];
  currentUser: any;
  assignedCashiersSet: Set<string>;
  setAssignedCashiersSet: React.Dispatch<React.SetStateAction<Set<string>>>;
  onSave: () => void;
}

export const AssignCashiersModal: React.FC<AssignCashiersModalProps> = ({
  isOpen,
  onClose,
  selectedSupervisor,
  users,
  currentUser,
  assignedCashiersSet,
  setAssignedCashiersSet,
  onSave
}) => {
  if (!isOpen || !selectedSupervisor) return null;

  const cashierList = users.filter(u => u.role === 'CASHIER' && (currentUser.role === 'MASTER' ? true : u.adminId === currentUser.id));

  return (
    <ModalShell>
      <ModalCard className="flex flex-col gap-5">
        <PanelHeader
          title="Asignar Cajeros a Supervisor"
          subtitle={`Seleccione los cajeros de su red para ${selectedSupervisor.displayName} (@${selectedSupervisor.user}).`}
        />

        <div className="flex max-h-[260px] flex-col gap-3 overflow-y-auto rounded-ln-md border border-ln-border bg-ln-background/60 p-3">
          {cashierList.length === 0 ? (
            <div className="p-3 text-center text-sm text-ln-text-secondary">
              No tiene cajeros creados en su red.
            </div>
          ) : (
            cashierList.map(c => {
              const isChecked = assignedCashiersSet.has(c.id);
              return (
                <label key={c.id} className="flex cursor-pointer select-none items-center gap-3 rounded-ln-sm border border-ln-border/60 bg-ln-surface/60 px-3 py-2 text-sm">
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => {
                      const newSet = new Set(assignedCashiersSet);
                      if (e.target.checked) {
                        newSet.add(c.id);
                      } else {
                        newSet.delete(c.id);
                      }
                      setAssignedCashiersSet(newSet);
                    }}
                    className="size-4 accent-ln-primary"
                  />
                  <div>
                    <strong>{c.displayName}</strong>
                    <span className="block text-xs text-ln-text-secondary">@{c.user} • ${c.balance.toFixed(2)} Balance</span>
                  </div>
                </label>
              );
            })
          )}
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton variant="success" onClick={onSave}>
            Guardar Asignaciones
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </ModalCard>
    </ModalShell>
  );
};
