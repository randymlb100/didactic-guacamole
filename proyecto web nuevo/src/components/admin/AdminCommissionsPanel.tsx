import React, { useMemo, useState } from 'react';
import type { UserAccount } from '../../types';

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
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <h3 className="fintech-panel-title">Comisiones</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Cajeros y supervisores de {admin.banca || admin.user}</span>
      </div>

      {message && <div className="badge badge-secondary" style={{ alignSelf: 'flex-start' }}>{message}</div>}

      {scopedUsers.map((target) => {
        const draft = drafts[target.id] ?? toPercent(target.commissionRate);
        return (
          <div key={target.id} className="glass-panel" style={{ padding: 12, display: 'grid', gridTemplateColumns: 'minmax(160px, 1fr) 160px 110px', gap: 10, alignItems: 'center' }}>
            <div>
              <strong>{target.displayName || target.user}</strong>
              <span style={{ display: 'block', color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>{target.role === 'SUPERVISOR' ? 'Supervisor' : 'Cajero'} · @{target.user}</span>
            </div>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>Comisión %</span>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={draft}
                onChange={(event) => setDrafts((current) => ({ ...current, [target.id]: event.target.value }))}
              />
            </label>
            <button type="button" className="btn btn-primary" disabled={savingId === target.id} onClick={() => save(target, draft)}>
              {savingId === target.id ? 'Guardando' : 'Guardar'}
            </button>
          </div>
        );
      })}
    </div>
  );
};
