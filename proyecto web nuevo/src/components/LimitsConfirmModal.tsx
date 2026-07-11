import { Settings, RefreshCw, CheckCircle, Info } from 'lucide-react';
import { ActionButton, ModalCard, ModalShell, PanelHeader } from './ui';

interface LimitsConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  limitsSaving: boolean;
  selectedScope: 'ADMIN_SELF' | 'CASHIER_DEFAULTS' | 'CASHIER_SPECIFIC';
  selectedCashierUsername: string;
  currentLimitsForm: any;
  systemModeConfig: any;
}

export const LimitsConfirmModal: React.FC<LimitsConfirmModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  limitsSaving,
  selectedScope,
  selectedCashierUsername,
  currentLimitsForm,
  systemModeConfig
}) => {
  if (!isOpen) return null;

  return (
    <ModalShell align="bottom">
      <ModalCard sheet size="md" className="flex flex-col gap-5">
        <div className="flex items-center justify-between gap-3">
          <PanelHeader
            title="Confirmar Guardar Límites"
            subtitle="Revisa los límites operativos y la escala de premios antes de sincronizar."
            action={<Settings size={22} className="text-ln-primary" />}
            className="flex-1"
          />
          <button
            type="button"
            onClick={onClose}
            className="rounded-full px-3 py-1 text-2xl leading-none text-ln-text-secondary hover:bg-ln-surface-hover"
            aria-label="Cerrar"
          >
            &times;
          </button>
        </div>

        <div className="flex flex-col gap-3 rounded-ln-md border border-ln-border bg-ln-background/45 p-4">
          <div className="flex justify-between gap-3 text-sm">
            <span className="text-ln-text-secondary">Alcance / Destinatario:</span>
            <strong className="text-right text-ln-primary">
              {selectedScope === 'ADMIN_SELF' ? 'Banca / Propios' : selectedScope === 'CASHIER_DEFAULTS' ? 'Todos los Cajeros (Defecto)' : `Cajero @${selectedCashierUsername}`}
            </strong>
          </div>

          <div className="border-t border-ln-border pt-3">
            <span className="mb-2 block text-sm font-semibold text-ln-text-primary">Topes de Venta Diarios</span>
            <div className="grid grid-cols-1 gap-2 text-sm text-ln-text-secondary sm:grid-cols-2">
              <div>Venta Máxima: <strong>${currentLimitsForm.daySale || 'Sin límite'}</strong></div>
              <div>Tope Pago Premios: <strong>${currentLimitsForm.payout || 'Sin tope'}</strong></div>
              {systemModeConfig.lotteryModeEnabled !== false && (
                <>
                  <div>Quiniela Tope: <strong>${currentLimitsForm.q}</strong></div>
                  <div>Palé Tope: <strong>${currentLimitsForm.pale}</strong></div>
                  <div>Super Palé: <strong>${currentLimitsForm.sp}</strong></div>
                  <div>Tripleta Tope: <strong>${currentLimitsForm.t}</strong></div>
                </>
              )}
              {systemModeConfig.pickModeEnabled !== false && (
                <>
                  <div>Pick 3 Straight: <strong>${currentLimitsForm.p3}</strong></div>
                  <div>Pick 3 Box: <strong>${currentLimitsForm.p3box}</strong></div>
                  <div>Pick 4 Straight: <strong>${currentLimitsForm.p4}</strong></div>
                  <div>Pick 4 Box: <strong>${currentLimitsForm.p4box}</strong></div>
                </>
              )}
            </div>
          </div>

          {(selectedScope === 'ADMIN_SELF' || selectedScope === 'CASHIER_SPECIFIC') && (
            <div className="border-t border-ln-border pt-3 text-sm">
              <span className="text-ln-text-secondary">Modo de Juego: </span>
              <strong className="text-ln-text-primary">
                {currentLimitsForm.systemModeOverride === 'lottery' ? 'Solo Lotería' : 
                 currentLimitsForm.systemModeOverride === 'pick' ? 'Solo Pick' : 
                 currentLimitsForm.systemModeOverride === 'both' ? 'Lotería + Pick' : 'Heredado'}
              </strong>
            </div>
          )}
        </div>

        <div className="flex items-start gap-3 rounded-ln-sm border border-ln-warning/20 bg-ln-warning/10 px-4 py-3 text-sm text-ln-warning">
          <Info size={16} className="mt-0.5 shrink-0" />
          <div>
            <strong>Guardado Seguro:</strong> Los cajeros de red recibirán estas configuraciones al instante mediante Supabase Realtime la próxima vez que abran wagers o actualicen su terminal.
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_1fr]">
          <ActionButton
            variant="warning"
            onClick={onConfirm}
            disabled={limitsSaving}
            icon={limitsSaving ? <RefreshCw size={16} className="animate-spin" /> : <CheckCircle size={16} />}
          >
            {limitsSaving ? 'Sincronizando con Servidor...' : 'Confirmar y Sincronizar'}
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
