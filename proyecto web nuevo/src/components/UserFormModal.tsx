import React from 'react';
import type { UserAccount } from '../types';
import { ActionButton, FieldGroup, FormGrid, ModalShell, PanelHeader } from './ui';

interface AdminFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  form: {
    ownerName: string;
    bankName: string;
    address: string;
    phone: string;
    cashierPrefix: string;
    cashierCount: number;
    territory: string;
    baseBalance: number;
  };
  setForm: React.Dispatch<React.SetStateAction<any>>;
}

export const AdminFormModal: React.FC<AdminFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  form,
  setForm
}) => {
  if (!isOpen) return null;

  return (
    <ModalShell>
      <form onSubmit={onSubmit} className="flex max-h-[90vh] w-full max-w-[520px] flex-col gap-5 overflow-y-auto rounded-ln-lg border border-ln-border bg-ln-surface p-6 shadow-ln-lg">
        <PanelHeader title="Registrar Nueva Banca Comercial" />
        
        <FormGrid>
          <FieldGroup label="Nombre Comercial Banca">
            <input
              type="text"
              placeholder="ej. Banca El Sol Churchill"
              value={form.bankName}
              onChange={(e) => setForm({ ...form, bankName: e.target.value })}
              className="form-input"
              required
            />
          </FieldGroup>

          <FieldGroup label="Nombre del Dueño (Socio)">
            <input
              type="text"
              placeholder="ej. Juan Pérez"
              value={form.ownerName}
              onChange={(e) => setForm({ ...form, ownerName: e.target.value })}
              className="form-input"
              required
            />
          </FieldGroup>
        </FormGrid>

        <FormGrid>
          <FieldGroup label="Prefijo Cajeros (Auto)">
            <input
              type="text"
              placeholder="ej. sol"
              value={form.cashierPrefix}
              onChange={(e) => setForm({ ...form, cashierPrefix: e.target.value })}
              className="form-input"
              maxLength={6}
            />
          </FieldGroup>

          <FieldGroup label="Cantidad Cajeros Iniciales">
            <input
              type="number"
              value={form.cashierCount}
              onChange={(e) => setForm({ ...form, cashierCount: parseInt(e.target.value) || 3 })}
              className="form-input"
              min={1}
              max={10}
              required
            />
          </FieldGroup>
        </FormGrid>

        <FormGrid>
          <FieldGroup label="Cupo Financiero Inicial ($)">
            <input
              type="number"
              value={form.baseBalance}
              onChange={(e) => setForm({ ...form, baseBalance: parseFloat(e.target.value) || 0 })}
              className="form-input"
              required
            />
          </FieldGroup>

          <FieldGroup label="Teléfono">
            <input
              type="text"
              placeholder="809-555-0199"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              className="form-input"
            />
          </FieldGroup>
        </FormGrid>

        <FieldGroup label="Dirección Local comercial">
          <input
            type="text"
            placeholder="Av. Principal #20"
            value={form.address}
            onChange={(e) => setForm({ ...form, address: e.target.value })}
            className="form-input"
          />
        </FieldGroup>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton type="submit" variant="primary">
            Crear Banca
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </form>
    </ModalShell>
  );
};

interface CajeroFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  form: {
    user: string;
    displayName: string;
    banca: string;
    territory: string;
    baseBalance: number;
    rechargesEnabled: boolean;
    rechargesAssignedBalance: number;
    supervisorId: string;
  };
  setForm: React.Dispatch<React.SetStateAction<any>>;
  users: UserAccount[];
  editingCashier: UserAccount | null;
  currentUser: any;
}

export const CajeroFormModal: React.FC<CajeroFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  form,
  setForm,
  users,
  editingCashier,
  currentUser
}) => {
  if (!isOpen) return null;

  return (
    <ModalShell>
      <form onSubmit={onSubmit} className="flex w-full max-w-[460px] flex-col gap-5 rounded-ln-lg border border-ln-border bg-ln-surface p-6 shadow-ln-lg">
        <PanelHeader title={editingCashier ? 'Editar Cajero Terminal' : 'Registrar Nuevo Cajero Terminal'} />

        <FieldGroup label="Nombre del Cajero">
          <input
            type="text"
            placeholder="ej. Cajera Principal Churchill"
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            className="form-input"
            required
          />
        </FieldGroup>

        <FieldGroup label="Usuario de Venta (ej. caj01)">
          <input
            type="text"
            placeholder="ej. chu03"
            value={form.user}
            onChange={(e) => setForm({ ...form, user: e.target.value })}
            className="form-input"
            required
          />
        </FieldGroup>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1.5fr_1fr]">
          <FieldGroup label="Supervisor Asignado (Opcional)">
            <select
              className="form-input"
              value={form.supervisorId}
              onChange={(e) => setForm({ ...form, supervisorId: e.target.value })}
            >
              <option value="">Ninguno</option>
              {users.filter(u => u.role === 'SUPERVISOR' && u.adminId === currentUser.id).map(s => (
                <option key={s.id} value={s.id}>{s.displayName}</option>
              ))}
            </select>
          </FieldGroup>

          <FieldGroup label="Cupo Recarga ($)">
            <input
              type="number"
              value={form.baseBalance}
              onChange={(e) => setForm({ ...form, baseBalance: parseFloat(e.target.value) || 0 })}
              className="form-input"
              required
            />
          </FieldGroup>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton type="submit" variant="primary">
            {editingCashier ? 'Guardar Cambios' : 'Crear Cajero'}
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </form>
    </ModalShell>
  );
};

interface SupervisorFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  form: {
    user: string;
    displayName: string;
    phone: string;
    territory: string;
  };
  setForm: React.Dispatch<React.SetStateAction<any>>;
}

export const SupervisorFormModal: React.FC<SupervisorFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  form,
  setForm
}) => {
  if (!isOpen) return null;

  return (
    <ModalShell>
      <form onSubmit={onSubmit} className="flex w-full max-w-[440px] flex-col gap-5 rounded-ln-lg border border-ln-border bg-ln-surface p-6 shadow-ln-lg">
        <PanelHeader title="Registrar Nuevo Supervisor" />

        <FieldGroup label="Nombre del Supervisor">
          <input
            type="text"
            placeholder="ej. Carlos Gómez"
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            className="form-input"
            required
          />
        </FieldGroup>

        <FieldGroup label="Usuario de Acceso">
          <input
            type="text"
            placeholder="ej. carlosg"
            value={form.user}
            onChange={(e) => setForm({ ...form, user: e.target.value })}
            className="form-input"
            required
          />
        </FieldGroup>

        <FieldGroup label="Teléfono Celular">
          <input
            type="text"
            placeholder="809-555-0199"
            value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
            className="form-input"
          />
        </FieldGroup>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton type="submit" variant="primary">
            Crear Supervisor
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </form>
    </ModalShell>
  );
};
