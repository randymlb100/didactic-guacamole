import React from 'react';
import { Settings, RefreshCw, CheckCircle } from 'lucide-react';
import type { UserAccount } from '../types';
import { ActionButton, ModalCard, ModalShell, PanelHeader } from './ui';
import { cn } from '../utils/classNames';

interface LimitsEditorProps {
  editingCashierLimits: UserAccount | null;
  onClose: () => void;
  modalLimitsTab: 'limits' | 'payouts';
  setModalLimitsTab: (tab: 'limits' | 'payouts') => void;
  modalLimitsForm: any;
  setModalLimitsForm: React.Dispatch<React.SetStateAction<any>>;
  modalPayoutsForm: any;
  setModalPayoutsForm: React.Dispatch<React.SetStateAction<any>>;
  onSave: () => void;
  limitsSaving: boolean;
}

export const LimitsEditor: React.FC<LimitsEditorProps> = ({
  editingCashierLimits,
  onClose,
  modalLimitsTab,
  setModalLimitsTab,
  modalLimitsForm,
  setModalLimitsForm,
  modalPayoutsForm,
  setModalPayoutsForm,
  onSave,
  limitsSaving
}) => {
  if (!editingCashierLimits) return null;

  return (
    <ModalShell>
      <ModalCard size="md" className="flex max-h-[90vh] max-w-[640px] flex-col gap-4 overflow-y-auto">
        <div className="border-b border-ln-border pb-3">
          <PanelHeader
            title={`Límites de Cajero: @${editingCashierLimits.user}`}
            subtitle={`${editingCashierLimits.displayName} • Banca: ${editingCashierLimits.banca}`}
            action={<Settings size={20} className="text-ln-primary" />}
          />
          <button
            onClick={onClose}
            className="absolute right-5 top-4 rounded-full px-3 py-1 text-2xl leading-none text-ln-text-secondary hover:bg-ln-surface-hover"
            aria-label="Cerrar"
          >
            &times;
          </button>
        </div>

        {/* Modal Sub-tabs */}
        <div className="flex flex-wrap gap-2 border-b border-ln-border pb-3">
          <button
            className={cn(
              'rounded-ln-md border px-3 py-2 text-sm font-semibold transition-colors',
              modalLimitsTab === 'limits'
                ? 'border-ln-primary bg-ln-primary text-white'
                : 'border-ln-border bg-ln-surface text-ln-text-primary hover:bg-ln-surface-hover',
            )}
            onClick={() => setModalLimitsTab('limits')}
          >
            Topes y Límites Diarios
          </button>
          <button
            className={cn(
              'rounded-ln-md border px-3 py-2 text-sm font-semibold transition-colors',
              modalLimitsTab === 'payouts'
                ? 'border-ln-primary bg-ln-primary text-white'
                : 'border-ln-border bg-ln-surface text-ln-text-primary hover:bg-ln-surface-hover',
            )}
            onClick={() => setModalLimitsTab('payouts')}
          >
            Premios y Multiplicadores
          </button>
        </div>

        {modalLimitsTab === 'limits' ? (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="form-group">
                <label className="form-label">Tope de Venta Diaria ($)</label>
                <input
                  type="number"
                  className="form-input"
                  value={modalLimitsForm.daySale}
                  onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, daySale: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Tope Pago Premios ($)</label>
                <input
                  type="number"
                  className="form-input"
                  value={modalLimitsForm.payout}
                  onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, payout: e.target.value })}
                />
              </div>
            </div>

            <div className="border-t border-ln-border pt-3">
              <h4 className="mb-3 text-sm font-semibold text-ln-primary">Límites de Lotería Tradicional ($)</h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="form-group">
                  <label className="form-label">Quiniela</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.q}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, q: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Palé</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.pale}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, pale: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Super Palé</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.sp}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, sp: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Tripleta</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.t}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, t: e.target.value })}
                  />
                </div>
              </div>
            </div>

            <div className="border-t border-ln-border pt-3">
              <h4 className="mb-3 text-sm font-semibold text-ln-primary">Límites de Picks (USA) ($)</h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="form-group">
                  <label className="form-label">Pick 3 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.p3}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, p3: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 3 Box</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.p3box}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, p3box: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.p4}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, p4: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Box</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalLimitsForm.p4box}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, p4box: e.target.value })}
                  />
                </div>
              </div>
            </div>

            <div className="border-t border-ln-border pt-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="form-group">
                  <label className="form-label">Comisión de Ventas (%)</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    max="100"
                    className="form-input"
                    value={modalLimitsForm.commissionRate}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, commissionRate: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Modo de Juego</label>
                  <select
                    className="form-input"
                    value={modalLimitsForm.systemModeOverride || ''}
                    onChange={(e) => setModalLimitsForm({ ...modalLimitsForm, systemModeOverride: e.target.value })}
                  >
                    <option value="">Por Defecto (Heredado)</option>
                    <option value="lottery">Solo Lotería</option>
                    <option value="pick">Solo Pick</option>
                    <option value="both">Lotería + Pick (Ambos)</option>
                  </select>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div>
              <h4 className="mb-3 text-sm font-semibold text-ln-primary">Premios Lotería Tradicional</h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <div className="form-group">
                  <label className="form-label">1ra ($ por $1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.q1}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, q1: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">2da ($ por $1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.q2}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, q2: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">3ra ($ por $1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.q3}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, q3: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Palé 1ra y 2da</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.pale}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, pale: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Tripleta (3 aciertos)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.tripleta}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, tripleta: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Super Palé</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.superPale}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, superPale: e.target.value })}
                  />
                </div>
              </div>
            </div>

            <div className="border-t border-ln-border pt-3">
              <h4 className="mb-3 text-sm font-semibold text-ln-primary">Premios Picks (USA)</h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="form-group">
                  <label className="form-label">Pick 3 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.pick3Straight}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, pick3Straight: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={modalPayoutsForm.pick4Straight}
                    onChange={(e) => setModalPayoutsForm({ ...modalPayoutsForm, pick4Straight: e.target.value })}
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Actions footer */}
        <div className="grid grid-cols-1 gap-3 border-t border-ln-border pt-4 sm:grid-cols-2">
          <ActionButton
            variant="warning"
            onClick={onSave}
            disabled={limitsSaving}
            icon={limitsSaving ? <RefreshCw size={16} className="animate-spin" /> : <CheckCircle size={16} />}
          >
            {limitsSaving ? 'Guardando...' : 'Guardar Límites'}
          </ActionButton>
          <ActionButton
            onClick={onClose}
            disabled={limitsSaving}
          >
            Cancelar
          </ActionButton>
        </div>
      </ModalCard>
    </ModalShell>
  );
};
