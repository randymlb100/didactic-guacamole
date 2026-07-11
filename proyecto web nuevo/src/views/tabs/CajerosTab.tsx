import React, { useMemo, useState } from 'react';
import { ArrowRightLeft, Plus, Lock, Unlock, Edit2, Sliders, Trash2, ChevronDown, Wallet, TrendingUp, BadgePercent, Gamepad2, Save, X } from 'lucide-react';
import type { UserAccount } from '../../types';
import { ActionButton, DataToolbar, Panel, SearchInput, StatusBadge } from '../../components/ui';
import { cn } from '../../utils/classNames';

interface CajerosTabProps {
  user: UserAccount;
  users: UserAccount[];
  cashierSalesTotals: Record<string, number>;
  setRechargeModalOpen: (open: boolean) => void;
  setCajeroModalOpen: (open: boolean) => void;
  handleToggleCashier: (id: string) => Promise<void>;
  handleOpenEditCajero: (cajero: UserAccount) => void;
  handleRenameCashier: (cajero: UserAccount, displayName: string) => Promise<void>;
  handleOpenCashierLimitsModal: (cajero: UserAccount) => void;
  handleDeleteCashier: (id: string) => Promise<void>;
}

export const CajerosTab: React.FC<CajerosTabProps> = ({
  user,
  users,
  cashierSalesTotals,
  setRechargeModalOpen,
  setCajeroModalOpen,
  handleToggleCashier,
  handleOpenEditCajero,
  handleRenameCashier,
  handleOpenCashierLimitsModal,
  handleDeleteCashier,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedCashierCardId, setExpandedCashierCardId] = useState<string | null>(null);
  const [renamingCashierId, setRenamingCashierId] = useState<string | null>(null);
  const [renameDraft, setRenameDraft] = useState('');
  const [savingRenameId, setSavingRenameId] = useState<string | null>(null);

  const cashiers = users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : u.adminId === user.id));
  const getCashierNumber = (cashier: UserAccount): number => {
    const candidates = [cashier.user, cashier.id];
    for (const candidate of candidates) {
      const match = String(candidate || '').match(/(\d+)(?!.*\d)/);
      if (match) return Number(match[1]);
    }
    return Number.MAX_SAFE_INTEGER;
  };

  const filteredCashiers = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return cashiers
      .filter(c =>
        !query ||
        (c.displayName?.toLowerCase() || '').includes(query) ||
        c.user.toLowerCase().includes(query) ||
        String(getCashierNumber(c)).includes(query)
      )
      .sort((a, b) => {
        const numberDiff = getCashierNumber(a) - getCashierNumber(b);
        if (numberDiff !== 0) return numberDiff;
        return a.user.localeCompare(b.user, 'es', { numeric: true, sensitivity: 'base' });
      });
  }, [cashiers, searchQuery]);

  const startRename = (cashier: UserAccount) => {
    setExpandedCashierCardId(cashier.id);
    setRenamingCashierId(cashier.id);
    setRenameDraft(cashier.displayName || cashier.user);
  };

  const cancelRename = () => {
    setRenamingCashierId(null);
    setRenameDraft('');
  };

  const submitRename = async (cashier: UserAccount) => {
    const nextName = renameDraft.trim();
    if (!nextName || nextName === (cashier.displayName || cashier.user)) {
      cancelRename();
      return;
    }
    setSavingRenameId(cashier.id);
    try {
      await handleRenameCashier(cashier, nextName);
      cancelRename();
    } finally {
      setSavingRenameId(null);
    }
  };

  return (
    <div className="fade-in flex flex-col gap-5">
      <DataToolbar>
        <SearchInput
          wrapperClassName="min-w-[240px] flex-1 sm:!w-72 sm:flex-none"
          placeholder="Buscar cajero..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />

        <ActionButton icon={<ArrowRightLeft size={16} />} onClick={() => setRechargeModalOpen(true)} variant="finance">
          Asignar cupo
        </ActionButton>

        <ActionButton icon={<Plus size={16} />} onClick={() => setCajeroModalOpen(true)} variant="primary">
          Crear Cajero
        </ActionButton>
      </DataToolbar>

      <div className="grid items-start gap-4 [grid-template-columns:repeat(auto-fill,minmax(min(100%,360px),1fr))]">
        {filteredCashiers.length === 0 ? (
          <Panel className="col-span-full py-12 text-center text-sm text-ln-text-secondary">
            No hay cajeros asignados a tu banca todavía o no coinciden con la búsqueda.
          </Panel>
        ) : (
          filteredCashiers.map((c) => {
            const salesTotalToday = cashierSalesTotals[c.user] || 0;
            const isExpanded = expandedCashierCardId === c.id;
            const isRenaming = renamingCashierId === c.id;
            const cashierNumber = getCashierNumber(c);
            const toggleExpand = () => setExpandedCashierCardId(isExpanded ? null : c.id);

            return (
              <Panel
                key={c.id}
                data-expanded={isExpanded}
                className={cn(
                  'cashier-card table-row-stagger tap-active group flex cursor-pointer flex-col gap-0 p-0 transition-shadow',
                  isExpanded && 'border-ln-primary/40 shadow-[0_14px_34px_hsl(var(--primary)/0.12)]',
                  !c.active && !isExpanded && 'border-ln-danger/30',
                )}
                onClick={toggleExpand}
              >
                <div className="cashier-card__header flex items-start justify-between gap-3 border-b border-ln-border/70 px-4 py-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="cashier-card__number rounded-full border px-2.5 py-0.5 text-[0.66rem] font-black uppercase tracking-wide">
                        #{cashierNumber === Number.MAX_SAFE_INTEGER ? '--' : cashierNumber.toString().padStart(2, '0')}
                      </span>
                      <span className="rounded-full bg-ln-background px-2 py-0.5 text-[0.66rem] font-bold uppercase tracking-wide text-ln-text-muted">
                        @{c.user}
                      </span>
                      <StatusBadge tone={c.active ? 'success' : 'danger'}>
                        {c.active ? 'Activo' : 'Bloqueado'}
                      </StatusBadge>
                    </div>
                    <div className="mt-2 flex min-w-0 items-center gap-2">
                      <strong className="block truncate text-lg font-extrabold text-ln-text-primary">{c.displayName || c.user}</strong>
                      <button
                        type="button"
                        className="btn-icon !min-h-0 !rounded-md !p-1 text-ln-text-muted hover:text-ln-primary"
                        title="Editar nombre del cajero"
                        onClick={(e) => {
                          e.stopPropagation();
                          startRename(c);
                        }}
                      >
                        <Edit2 size={14} />
                      </button>
                    </div>
                    <span className="block truncate text-sm font-medium text-ln-text-secondary">Orden fijo por usuario: {c.user}</span>
                  </div>

                  <ChevronDown
                    className={cn('mt-1 shrink-0 text-ln-text-muted transition-transform', isExpanded && 'rotate-180')}
                    size={18}
                  />
                </div>

                <div className="px-4 py-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                    <div>
                      <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wide text-ln-text-secondary">
                        <Wallet size={14} />
                        Balance caja
                      </span>
                      <strong className="mt-1 block text-2xl font-black text-ln-text-primary">${c.balance.toFixed(2)}</strong>
                    </div>
                    <div className="cashier-card__stat rounded-lg px-3 py-2 sm:text-right">
                      <span className="flex items-center gap-1 text-[0.66rem] font-bold uppercase tracking-wide text-ln-text-secondary sm:justify-end">
                        <TrendingUp size={12} />
                        Hoy
                      </span>
                      <strong className="block text-sm text-ln-text-primary">
                        ${salesTotalToday.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </strong>
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-1 gap-2 text-xs sm:grid-cols-2">
                    <div className="cashier-card__stat min-w-0 rounded-lg px-3 py-2">
                      <span className="flex items-center gap-1.5 text-[0.66rem] font-bold uppercase tracking-wide text-ln-text-muted">
                        <BadgePercent size={12} />
                        Comisión
                      </span>
                      <strong className="mt-1 block text-sm text-ln-text-primary">
                        {c.commissionRate !== undefined && c.commissionRate !== null ? c.commissionRate : 8.0}%
                      </strong>
                    </div>
                    <div className="cashier-card__stat min-w-0 rounded-lg px-3 py-2">
                      <span className="flex items-center gap-1.5 text-[0.66rem] font-bold uppercase tracking-wide text-ln-text-muted">
                        <Gamepad2 size={12} />
                        Modo
                      </span>
                      <strong className="mt-1 block truncate text-sm text-ln-text-primary">
                        {c.systemModeOverride === 'lottery' ? 'Solo Lotería' :
                         c.systemModeOverride === 'pick' ? 'Solo Pick' :
                         c.systemModeOverride === 'both' ? 'Lotería + Pick' : 'Heredado'}
                      </strong>
                    </div>
                  </div>
                </div>

                <div className={cn('expand-drawer', isExpanded && 'expanded')} onClick={(e) => e.stopPropagation()}>
                  <div className="flex flex-col gap-3 border-t border-ln-border/70 px-4 py-3">
                    {isRenaming && (
                      <div className="rounded-lg border border-ln-primary/25 bg-ln-primary/10 p-3">
                        <label className="mb-2 block text-[0.66rem] font-bold uppercase tracking-wide text-ln-primary">
                          Nombre visible del cajero
                        </label>
                        <div className="flex flex-col gap-2 sm:flex-row">
                          <input
                            value={renameDraft}
                            onChange={(e) => setRenameDraft(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') void submitRename(c);
                              if (e.key === 'Escape') cancelRename();
                            }}
                            className="form-input min-w-0 flex-1"
                            autoFocus
                          />
                          <div className="flex gap-2">
                            <ActionButton
                              icon={<Save size={12} />}
                              onClick={() => void submitRename(c)}
                              disabled={savingRenameId === c.id}
                              variant="success"
                            >
                              Guardar
                            </ActionButton>
                            <ActionButton icon={<X size={12} />} onClick={cancelRename} variant="ghost" />
                          </div>
                        </div>
                      </div>
                    )}

                    <div className="flex items-center justify-between text-sm">
                      <span className="text-ln-text-secondary">Cupo Recargas (FF):</span>
                      <div className="flex items-center gap-2">
                        <strong className="text-ln-text-primary">${c.rechargesBalance.toFixed(2)}</strong>
                        <label className="custom-toggle scale-75">
                          <input type="checkbox" checked={c.rechargesEnabled} disabled readOnly />
                          <span className="custom-toggle-slider" />
                        </label>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-ln-text-muted">
                      <span>Registrado: {c.createdLabel || '14/05/2026'}</span>
                      <span>Zona: <strong className="text-ln-text-primary">{c.territory}</strong></span>
                    </div>

                    <div className="flex flex-wrap gap-2 border-t border-ln-border/40 pt-3">
                      <ActionButton
                        className="flex-[1_1_45%]"
                        icon={c.active ? <Lock size={12} /> : <Unlock size={12} />}
                        onClick={() => handleToggleCashier(c.id)}
                        variant={c.active ? 'danger' : 'success'}
                      >
                        {c.active ? 'Suspender' : 'Activar'}
                      </ActionButton>

                      <ActionButton
                        className="flex-[1_1_45%]"
                        icon={<Edit2 size={12} />}
                        onClick={() => handleOpenEditCajero(c)}
                        variant="info"
                      >
                        Editar
                      </ActionButton>

                      <ActionButton
                        className="flex-[1_1_70%]"
                        icon={<Sliders size={12} />}
                        onClick={() => handleOpenCashierLimitsModal(c)}
                        variant="warning"
                      >
                        Límites
                      </ActionButton>

                      <ActionButton
                        className="flex-[1_1_20%]"
                        icon={<Trash2 size={12} />}
                        onClick={() => handleDeleteCashier(c.id)}
                        title="Eliminar Cajero"
                        variant="danger"
                      />
                    </div>
                  </div>
                </div>
              </Panel>
            );
          })
        )}
      </div>
    </div>
  );
};
