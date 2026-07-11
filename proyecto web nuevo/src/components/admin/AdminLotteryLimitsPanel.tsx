import React from 'react';
import type { UserAccount } from '../../types';
import type { LotteryLimitStructure } from '../../utils/lotteryLimitStructure';
import { ActionButton, MetricCard, Panel, PanelHeader } from '../ui';

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
    <Panel tone="primary" className="flex flex-col gap-4">
      <PanelHeader title="Límites de jugadas" subtitle={`${admin.banca || admin.user} · General · lotería · cajero · jugada`} />

      <section className="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ln-text-secondary">Límite ticket general</span>
          <input className="form-input" type="number" value={limits.global.maxTicketAmount || ''} onChange={(event) => updateGlobal('maxTicketAmount', event.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ln-text-secondary">Límite jugada general</span>
          <input className="form-input" type="number" value={limits.global.maxPlayAmount || ''} onChange={(event) => updateGlobal('maxPlayAmount', event.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ln-text-secondary">Límite pago general</span>
          <input className="form-input" type="number" value={limits.global.maxPayoutAmount || ''} onChange={(event) => updateGlobal('maxPayoutAmount', event.target.value)} />
        </label>
        <ActionButton variant="primary" className="self-end" onClick={onSave}>Guardar límites</ActionButton>
      </section>

      <section className="flex flex-col gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
        <strong>Punto de venta / cajero</strong>
        <div className="flex flex-col gap-2">
          {cashiers.map((cashier) => (
            <div key={cashier.id} className="flex items-center justify-between gap-3 rounded-ln-sm border border-ln-border/70 bg-ln-background/40 px-3 py-2">
              <span className="font-medium text-ln-text-primary">{cashier.displayName || cashier.user}</span>
              <span className="text-sm text-ln-text-secondary">
                Ticket: {limits.byCashier[cashier.id]?.maxTicketAmount ?? limits.byCashier[cashier.user]?.maxTicketAmount ?? 'general'}
              </span>
            </div>
          ))}
        </div>
      </section>

      <section className="flex flex-col gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
        <div>
          <strong>Loterías y jugadas</strong>
          <p className="mt-1 text-sm text-ln-text-secondary">
          Estructura preparada para límites por lotería y por combinación específica, sin mezclarlo con el POS.
          </p>
        </div>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-3">
          <MetricCard label="Límites por lotería" value={Object.keys(limits.byLottery).length} />
          <MetricCard label="Límites por jugada" value={Object.keys(limits.byPlay).length} accent="success" />
        </div>
      </section>
    </Panel>
  );
};
