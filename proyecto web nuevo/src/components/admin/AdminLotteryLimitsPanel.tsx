import React from 'react';
import type { UserAccount } from '../../types';
import type { LotteryLimitStructure } from '../../utils/lotteryLimitStructure';

interface Props {
  admin: UserAccount;
  cashiers: UserAccount[];
  limits: LotteryLimitStructure;
  onChange: (limits: LotteryLimitStructure) => void;
  onSave: () => void;
}

export const AdminLotteryLimitsPanel: React.FC<Props> = ({ admin, cashiers, limits, onChange, onSave }) => {
  const updateGlobal = (field: 'maxTicketAmount' | 'maxPlayAmount' | 'maxPayoutAmount', value: string) => {
    onChange({
      ...limits,
      global: { ...limits.global, [field]: Number(value || 0) },
    });
  };

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div>
        <h3 className="fintech-panel-title">Límites de jugadas</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>
          {admin.banca || admin.user} · General · lotería · cajero · jugada
        </span>
      </div>

      <section className="glass-panel" style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
        <label>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>Límite ticket general</span>
          <input className="form-input" type="number" value={limits.global.maxTicketAmount || ''} onChange={(event) => updateGlobal('maxTicketAmount', event.target.value)} />
        </label>
        <label>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>Límite jugada general</span>
          <input className="form-input" type="number" value={limits.global.maxPlayAmount || ''} onChange={(event) => updateGlobal('maxPlayAmount', event.target.value)} />
        </label>
        <label>
          <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>Límite pago general</span>
          <input className="form-input" type="number" value={limits.global.maxPayoutAmount || ''} onChange={(event) => updateGlobal('maxPayoutAmount', event.target.value)} />
        </label>
        <button type="button" className="btn btn-primary" onClick={onSave}>Guardar límites</button>
      </section>

      <section className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <strong>Punto de venta / cajero</strong>
        {cashiers.map((cashier) => (
          <div key={cashier.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
            <span>{cashier.displayName || cashier.user}</span>
            <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>
              Ticket: {limits.byCashier[cashier.id]?.maxTicketAmount ?? limits.byCashier[cashier.user]?.maxTicketAmount ?? 'general'}
            </span>
          </div>
        ))}
      </section>

      <section className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <strong>Loterías y jugadas</strong>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.82rem' }}>
          Estructura preparada para límites por lotería y por combinación específica, sin mezclarlo con el POS.
        </span>
        <span style={{ fontSize: '0.8rem' }}>Límites por lotería: {Object.keys(limits.byLottery).length}</span>
        <span style={{ fontSize: '0.8rem' }}>Límites por jugada: {Object.keys(limits.byPlay).length}</span>
      </section>
    </div>
  );
};
