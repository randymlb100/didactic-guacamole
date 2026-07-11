import React from 'react';
import type { UserAccount } from '../types';
import { ActionButton, ModalShell, PanelHeader } from './ui';

interface RechargeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  form: {
    cashierId: string;
    amount: string;
  };
  setForm: React.Dispatch<React.SetStateAction<any>>;
  users: UserAccount[];
  currentUser: any;
}

export const RechargeModal: React.FC<RechargeModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  form,
  setForm,
  users,
  currentUser
}) => {
  if (!isOpen) return null;

  return (
    <ModalShell>
      <form
        onSubmit={onSubmit}
        className="flex w-full max-w-[430px] flex-col gap-5 rounded-ln-lg border border-ln-border bg-ln-surface p-6 shadow-ln-lg backdrop-blur-md"
      >
        <PanelHeader title="Asignar cupo a cajero" />

        <div className="form-group">
          <label className="form-label">Seleccionar Cajero Destino</label>
          <select
            className="form-input"
            value={form.cashierId}
            onChange={(e) => setForm({ ...form, cashierId: e.target.value })}
            required
          >
            <option value="">Seleccione un cajero...</option>
            {users.filter(u => {
              if (u.role !== 'CASHIER') return false;
              if (currentUser.role === 'MASTER') return true;
              if (currentUser.role === 'SUPERVISOR') return u.supervisorIds.includes(currentUser.id);
              return u.adminId === currentUser.id;
            }).map(c => (
              <option key={c.id} value={c.id}>{c.displayName} (@{c.user})</option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label className="form-label">Monto a asignar ($)</label>
          <input
            type="number"
            placeholder="ej. 5000"
            value={form.amount}
            onChange={(e) => setForm({ ...form, amount: e.target.value })}
            className="form-input"
            required
          />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto]">
          <ActionButton type="submit" variant="finance">
            Confirmar Traspaso
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </form>
    </ModalShell>
  );
};
