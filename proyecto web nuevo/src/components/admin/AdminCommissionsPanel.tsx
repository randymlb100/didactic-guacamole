import React, { useMemo, useState } from 'react';
import type { UserAccount } from '../../types';
import { ActionButton, Panel, PanelHeader, StatusBadge } from '../ui';

interface Props {
  admin: UserAccount;
  users: UserAccount[];
  onSaveCommission: (target: UserAccount, commissionRate: number) => Promise<void> | void;
}

const toPercent = (rate: number | null | undefined): string => String(Number(rate ?? 0).toFixed(2));
const fromPercent = (value: string): number => Math.max(0, Number(value || 0));

export const AdminCommissionsPanel: React.FC<Props> = ({ admin, users, onSaveCommission }) => {
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);
  const [message, setMessage] = useState('');

  const scopedUsers = useMemo(() => {
    return users.filter((user) => {
      if (user.role !== 'CASHIER' && user.role !== 'SUPERVISOR') return false;
      return user.adminId === admin.id || user.adminUser === admin.user || user.banca === admin.banca;
    });
  }, [admin, users]);

  const save = async (target: UserAccount, draft: string) => {
    setSavingId(target.id);
    setMessage('');
    try {
      await onSaveCommission(target, fromPercent(draft));
      setMessage(`Comisión guardada para ${target.displayName || target.user}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo guardar la comisión.');
    } finally {
      setSavingId(null);
    }
  };

  return (
    <Panel tone="primary" className="flex flex-col gap-4">
      <PanelHeader title="Comisiones" subtitle={`Cajeros y supervisores de ${admin.banca || admin.user}`} />

      {message && <StatusBadge tone="primary" className="self-start">{message}</StatusBadge>}

      <div className="flex flex-col gap-3">
        {scopedUsers.map((target) => {
          const draft = drafts[target.id] ?? toPercent(target.commissionRate);
          return (
            <div
              key={target.id}
              className="grid items-center gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-3 md:grid-cols-[minmax(160px,1fr)_160px_110px]"
            >
              <div>
                <strong>{target.displayName || target.user}</strong>
                <span className="block text-xs text-ln-text-secondary">
                  {target.role === 'SUPERVISOR' ? 'Supervisor' : 'Cajero'} · @{target.user}
                </span>
              </div>
              <label className="flex flex-col gap-1">
                <span className="text-xs text-ln-text-secondary">Comisión %</span>
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={draft}
                  onChange={(event) => setDrafts((current) => ({ ...current, [target.id]: event.target.value }))}
                />
              </label>
              <ActionButton variant="primary" disabled={savingId === target.id} onClick={() => save(target, draft)}>
                {savingId === target.id ? 'Guardando' : 'Guardar'}
              </ActionButton>
            </div>
          );
        })}
      </div>
    </Panel>
  );
};
