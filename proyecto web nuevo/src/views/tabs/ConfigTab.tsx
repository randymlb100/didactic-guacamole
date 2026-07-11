import React from 'react';
import { DollarSign, Layers, Activity, Settings, TrendingUp, Trophy, Lock, X, CheckCircle } from 'lucide-react';
import type { UserAccount, LotteryCatalogItem, BlockedSalePlay } from '../../types';
import { STATIC_LOTTERIES } from '../../utils/supabase';
import { ActionButton, CompactSelect, MoneyInput } from '../../components/ui';

type ConfigScope = 'ADMIN_SELF' | 'CASHIER_DEFAULTS' | 'CASHIER_SPECIFIC';
interface CurrentLimitsForm {
  daySale: number;
  payout: number;
  q: number;
  pale: number;
  sp: number;
  t: number;
  p3: number;
  p3box: number;
  p4: number;
  p4box: number;
  systemModeOverride: string;
}

interface SystemModeConfig {
  lotteryModeEnabled?: boolean;
  pickModeEnabled?: boolean;
  sportsbookEnabled?: boolean;
  cashierLotteryModeEnabled?: boolean;
  cashierPickModeEnabled?: boolean;
}

interface CurrentPayoutsForm {
  q1: number;
  q2: number;
  q3: number;
  pale: number;
  superPale: number;
  tripleta: number;
  tripleta2: number;
  pick3Straight: number;
  pick3BackPair: number;
  pick3Box3: number;
  pick3Box6: number;
  pick4Straight: number;
  pick4BackPair: number;
  pick4Box4: number;
  pick4Box6: number;
  pick4Box12: number;
  pick4Box24: number;
}

interface SportsLimitsForm {
  max_ticket_stake: number;
  max_potential_payout: number;
  enabled_markets: string[];
}

interface BlockedPlayForm {
  playType: string;
  number: string;
}

interface ConfigTabProps {
  user: UserAccount;
  users: UserAccount[];
  lotteries: LotteryCatalogItem[];
  saveSuccessNotification: boolean;
  selectedScope: ConfigScope;
  setSelectedScope: (scope: ConfigScope) => void;
  selectedCashierUsername: string;
  setSelectedCashierUsername: (username: string) => void;
  currentLimitsForm: CurrentLimitsForm;
  setCurrentLimitsForm: React.Dispatch<React.SetStateAction<CurrentLimitsForm>>;
  systemModeConfig: SystemModeConfig;
  setSystemModeConfig: React.Dispatch<React.SetStateAction<SystemModeConfig>>;
  currentPayoutsForm: CurrentPayoutsForm;
  setCurrentPayoutsForm: React.Dispatch<React.SetStateAction<CurrentPayoutsForm>>;
  sportsLimitsForm: SportsLimitsForm;
  setSportsLimitsForm: React.Dispatch<React.SetStateAction<SportsLimitsForm>>;
  blockedPlayForm: BlockedPlayForm;
  setBlockedPlayForm: React.Dispatch<React.SetStateAction<BlockedPlayForm>>;
  blockedSalePlays: BlockedSalePlay[];
  manualDisabledLotteryIds: string[];
  setLimitsConfirmOpen: (open: boolean) => void;
  handleAddBlockedPlay: (e: React.FormEvent) => void;
  handleRemoveBlockedPlay: (play: BlockedSalePlay) => void;
  handleToggleManualDisabledLottery: (lotteryId: string) => void;
}

export const ConfigTab: React.FC<ConfigTabProps> = ({
  user,
  users,
  lotteries,
  saveSuccessNotification,
  selectedScope,
  setSelectedScope,
  selectedCashierUsername,
  setSelectedCashierUsername,
  currentLimitsForm,
  setCurrentLimitsForm,
  systemModeConfig,
  setSystemModeConfig,
  currentPayoutsForm,
  setCurrentPayoutsForm,
  sportsLimitsForm,
  setSportsLimitsForm,
  blockedPlayForm,
  setBlockedPlayForm,
  blockedSalePlays,
  manualDisabledLotteryIds,
  setLimitsConfirmOpen,
  handleAddBlockedPlay,
  handleRemoveBlockedPlay,
  handleToggleManualDisabledLottery,
}) => {
  return (
    <div className="fade-in config-compact" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* TOP BAR / SCOPE SEGMENT CONTROLS */}
      <div className="glass-panel" style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>
              Configuración de Límites Administrativos
            </h3>
            <p style={{ fontSize: '0.825rem', color: 'hsl(var(--text-secondary))' }}>
              Define los montos máximos permitidos para ventas diarias, pagos y jugadas individuales.
            </p>
          </div>

          {saveSuccessNotification && (
            <div className="fade-in" style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 16px',
              borderRadius: 'var(--radius-md)',
              backgroundColor: 'hsl(var(--success) / 0.1)',
              color: 'hsl(var(--success))',
              fontSize: '0.875rem',
              fontWeight: 500,
              border: '1px solid hsl(var(--success) / 0.2)'
            }}>
              <CheckCircle size={16} />
              ¡Límites guardados correctamente!
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', borderBottom: '1px solid hsl(var(--border))', paddingBottom: '16px' }}>
          <label style={{ fontSize: '0.85rem', fontWeight: 600, color: 'hsl(var(--text-secondary))' }}>
            Ámbito de Configuración (Alcance)
          </label>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
            <CompactSelect
              value={selectedScope}
              onChange={(e) => setSelectedScope(e.target.value as ConfigScope)}
              style={{ maxWidth: '360px', fontWeight: 700, cursor: 'pointer' }}
            >
              <option value="ADMIN_SELF">⚙️ Mi Cuenta (Límites de Banca y Propios)</option>
              <option value="CASHIER_DEFAULTS">Todos los cajeros (valores estándar)</option>
              <option value="CASHIER_SPECIFIC">👤 Por Cajero (Configuración Personalizada)</option>
            </CompactSelect>
            
            {selectedScope === 'CASHIER_SPECIFIC' && (
              <div className="fade-in" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <CompactSelect
                  value={selectedCashierUsername}
                  onChange={(e) => setSelectedCashierUsername(e.target.value)}
                  style={{ minWidth: '220px', cursor: 'pointer' }}
                >
                  {users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : u.adminId === user.id)).length === 0 ? (
                    <option value="">No tienes cajeros registrados</option>
                  ) : (
                    users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : u.adminId === user.id)).map(c => (
                      <option key={c.id} value={c.user}>
                        👤 {c.displayName || c.user} (@{c.user})
                      </option>
                    ))
                  )}
                </CompactSelect>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* LIMIT SECTIONS CARDS GRID */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
        
        {/* CARD 1: DAILY LIMIT & PAYOUT */}
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <DollarSign size={18} color="var(--primary)" />
              {selectedScope === 'ADMIN_SELF' ? 'Límites de Venta y Cobro Propios' : 'Límites Diarios del Cajero'}
            </h4>
            <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
              {selectedScope === 'ADMIN_SELF' 
                ? 'Establece el dinero máximo diario y cobros permitidos para tu propia cuenta.' 
                : 'Define el cupo total diario de ventas y el tope de pago de premios.'}
            </p>
          </div>

          <div className="form-group">
            <label className="form-label" style={{ fontWeight: 600 }}>
              {selectedScope === 'ADMIN_SELF' ? 'Mi Venta Diaria Máxima' : 'Dinero Máximo de Venta por Día'}
            </label>
            <MoneyInput
              value={currentLimitsForm.daySale}
              onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, daySale: Number(e.target.value) })}
              min={0}
            />
            <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '4px', display: 'block' }}>
              {selectedScope === 'ADMIN_SELF' 
                ? 'Deja en 0 para vender sin límites propios.' 
                : '0 deja al cajero sin límites de venta diarios.'}
            </span>
          </div>

          <div className="form-group">
            <label className="form-label" style={{ fontWeight: 600 }}>
              Límite Máximo de Pago de Premio (Pagos)
            </label>
            <MoneyInput
              value={currentLimitsForm.payout}
              onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, payout: Number(e.target.value) })}
              min={0}
            />
            <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '4px', display: 'block' }}>
              Controla el monto máximo que un cajero puede pagar directamente por ticket.
            </span>
          </div>
        </div>

        {/* CARD 2: TRADITIONAL PLAY LIMITS */}
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={18} color="var(--primary)" />
              Loterías Tradicionales (Tope por Jugada)
            </h4>
            <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
              Control de riesgo por combinaciones tradicionales de la lotería dominicana.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Quiniela</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.q}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, q: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Palé</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.pale}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, pale: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Super Palé</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.sp}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, sp: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Tripleta</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.t}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, t: Number(e.target.value) })}
                min={0}
              />
            </div>
          </div>
        </div>

        {/* CARD 3: PICK 3 / PICK 4 PLAY LIMITS */}
        <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Activity size={18} color="var(--primary)" />
              Loterías Americanas (Pick 3 / Pick 4)
            </h4>
            <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
              Topes por tipo de jugada para sorteos en el territorio USA.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Pick 3 Straight</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.p3}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p3: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Pick 3 Box</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.p3box}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p3box: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Pick 4 Straight</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.p4}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p4: Number(e.target.value) })}
                min={0}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Pick 4 Box</label>
              <input
                type="number"
                className="form-input"
                value={currentLimitsForm.p4box}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p4box: Number(e.target.value) })}
                min={0}
              />
            </div>
          </div>
        </div>

        {/* CARD 4: SYSTEM CONFIGURATION (POS MODE OVERRIDE) */}
        {(selectedScope === 'ADMIN_SELF' || selectedScope === 'CASHIER_SPECIFIC') && (
          <div className="glass-panel fade-in" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Settings size={18} color="var(--primary)" />
                Configuración del Sorteo POS
              </h4>
              <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                Ajusta el modo de juego habilitado en la terminal.
              </p>
            </div>

            <div className="form-group">
              <label className="form-label" style={{ fontWeight: 600 }}>
                {selectedScope === 'ADMIN_SELF' ? 'Mi Modo de Sorteo' : 'Modo de Juego del Cajero'}
              </label>
              <CompactSelect
                value={currentLimitsForm.systemModeOverride}
                onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, systemModeOverride: e.target.value })}
              >
                <option value="">Por Defecto (Heredado)</option>
                <option value="lottery">Solo Lotería</option>
                <option value="pick">Solo Pick</option>
                <option value="both">Lotería + Pick (Ambos)</option>
              </CompactSelect>
            </div>

            <div style={{ borderTop: '1px solid hsl(var(--border))', paddingTop: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <h5 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'hsl(var(--text-primary))' }}>
                {selectedScope === 'ADMIN_SELF' ? 'Modos Habilitados para la Banca' : 'Permisos de Juego para el Cajero'}
              </h5>
              
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid hsl(var(--border))' }}>
                <div>
                  <span style={{ fontSize: '0.85rem', fontWeight: 600, display: 'block' }}>Lotería Tradicional</span>
                  <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-secondary))' }}>Quiniela, Palé, Super Palé, Tripleta</span>
                </div>
                <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={selectedScope === 'ADMIN_SELF' ? (systemModeConfig.lotteryModeEnabled !== false) : (systemModeConfig.cashierLotteryModeEnabled !== false)}
                    onChange={(e) => {
                      if (selectedScope === 'ADMIN_SELF') {
                        setSystemModeConfig({ ...systemModeConfig, lotteryModeEnabled: e.target.checked });
                      } else {
                        setSystemModeConfig({ ...systemModeConfig, cashierLotteryModeEnabled: e.target.checked });
                      }
                    }}
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--primary)' }}
                  />
                </label>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid hsl(var(--border))' }}>
                <div>
                  <span style={{ fontSize: '0.85rem', fontWeight: 600, display: 'block' }}>USA Pick</span>
                  <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-secondary))' }}>Pick 3 y Pick 4 (Straight, Box, Pair)</span>
                </div>
                <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={selectedScope === 'ADMIN_SELF' ? (systemModeConfig.pickModeEnabled !== false) : (systemModeConfig.cashierPickModeEnabled !== false)}
                    onChange={(e) => {
                      if (selectedScope === 'ADMIN_SELF') {
                        setSystemModeConfig({ ...systemModeConfig, pickModeEnabled: e.target.checked });
                      } else {
                        setSystemModeConfig({ ...systemModeConfig, cashierPickModeEnabled: e.target.checked });
                      }
                    }}
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--primary)' }}
                  />
                </label>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* CARD: PAGA DE PREMIOS (MULTIPLICADORES DE PAYOUT) */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '24px' }}>
        <div>
          <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TrendingUp size={18} color="var(--primary)" />
            Paga de Premios (Multiplicadores de Payout)
          </h4>
          <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
            {selectedScope === 'ADMIN_SELF' || selectedScope === 'CASHIER_DEFAULTS' 
              ? 'Configura la escala de premios estándar para tu red de cajeros (multiplicador x cada $1 apostado).' 
              : `Personaliza la paga de premios exclusiva para el cajero @${selectedCashierUsername}.`}
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '24px' }}>
          {/* Traditional Payouts */}
          {systemModeConfig.lotteryModeEnabled !== false && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h5 style={{ fontSize: '0.9rem', fontWeight: 600, borderBottom: '1px solid hsl(var(--border))', paddingBottom: '8px', color: 'var(--primary)' }}>
                Loterías Tradicionales (Escala RD)
              </h5>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label className="form-label">Quiniela 1ra (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.q1}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, q1: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Quiniela 2da (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.q2}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, q2: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Quiniela 3ra (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.q3}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, q3: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Palé (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pale}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pale: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Super Palé (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.superPale}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, superPale: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Tripleta (x1)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.tripleta}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, tripleta: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group" style={{ gridColumn: 'span 2' }}>
                  <label className="form-label">Tripleta 2 Aciertos (Consolación)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.tripleta2}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, tripleta2: Number(e.target.value) })}
                    min={0}
                  />
                </div>
              </div>
            </div>
          )}

          {/* USA Pick Payouts */}
          {systemModeConfig.pickModeEnabled !== false && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h5 style={{ fontSize: '0.9rem', fontWeight: 600, borderBottom: '1px solid hsl(var(--border))', paddingBottom: '8px', color: 'var(--primary)' }}>
                Loterías USA (Pick 3 / Pick 4)
              </h5>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label className="form-label">Pick 3 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick3Straight}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick3Straight: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 3 Back Pair</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick3BackPair}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick3BackPair: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 3 Box 3-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick3Box3}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick3Box3: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 3 Box 6-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick3Box6}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick3Box6: Number(e.target.value) })}
                    min={0}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Pick 4 Straight</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4Straight}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4Straight: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Back Pair</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4BackPair}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4BackPair: Number(e.target.value) })}
                    min={0}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Pick 4 Box 4-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4Box4}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4Box4: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Box 6-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4Box6}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4Box6: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Box 12-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4Box12}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4Box12: Number(e.target.value) })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Pick 4 Box 24-Way</label>
                  <input
                    type="number"
                    className="form-input"
                    value={currentPayoutsForm.pick4Box24}
                    onChange={(e) => setCurrentPayoutsForm({ ...currentPayoutsForm, pick4Box24: Number(e.target.value) })}
                    min={0}
                  />
                </div>
              </div>
            </div>
          )}

          {/* SPORTSBOOK LIMITS CONFIGURATION */}
          <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Trophy size={18} color="var(--primary)" />
                Límites de Apuestas Deportivas
              </h4>
              <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                Establece topes máximos para apuestas y cobros deportivos.
              </p>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600 }}>
                  Apuesta Máxima por Ticket ($)
                </label>
                <MoneyInput
                  value={sportsLimitsForm.max_ticket_stake}
                  onChange={(e) => setSportsLimitsForm({ ...sportsLimitsForm, max_ticket_stake: Number(e.target.value) })}
                  min={0}
                />
              </div>

              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600 }}>
                  Pago Máximo por Ticket ($)
                </label>
                <MoneyInput
                  value={sportsLimitsForm.max_potential_payout}
                  onChange={(e) => setSportsLimitsForm({ ...sportsLimitsForm, max_potential_payout: Number(e.target.value) })}
                  min={0}
                />
              </div>

              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600, marginBottom: '8px', display: 'block' }}>
                  Mercados Deportivos Autorizados
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                  {[
                    { id: 'moneyline', label: 'Moneyline (Ganador)' },
                    { id: 'spread', label: 'Spread (Handicap)' },
                    { id: 'total', label: 'Alta/Baja (Totals)' },
                    { id: 'runline', label: 'Runline (Handicap)' },
                    { id: 'first_half', label: 'Primera Mitad' },
                    { id: 'first_five', label: 'Primeras 5 Entradas' }
                  ].map((mkt) => {
                    const isChecked = sportsLimitsForm.enabled_markets.includes(mkt.id);
                    return (
                      <label key={mkt.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8rem', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={(e) => {
                            let nextMkts = [...sportsLimitsForm.enabled_markets];
                            if (e.target.checked) {
                              if (!nextMkts.includes(mkt.id)) nextMkts.push(mkt.id);
                            } else {
                              nextMkts = nextMkts.filter(m => m !== mkt.id);
                            }
                            setSportsLimitsForm({ ...sportsLimitsForm, enabled_markets: nextMkts });
                          }}
                        />
                        {mkt.label}
                      </label>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>

      {/* CARD 5: SPECIFIC PLAYS / NUMBERS BLOCK CONTROL */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '24px' }}>
        <div>
          <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', color: 'hsl(var(--danger))' }}>
            <Lock size={18} />
            Bloqueo de Jugadas y Números Específicos
          </h4>
          <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
            Bloquea jugadas específicas para impedir su venta en los cajeros de tu red.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px' }}>
          {/* Form to Add Blocked Play */}
          <form onSubmit={handleAddBlockedPlay} style={{ display: 'flex', flexDirection: 'column', gap: '16px', borderRight: '1px solid hsl(var(--border))', paddingRight: '24px' }}>
            <h5 style={{ fontSize: '0.9rem', fontWeight: 600 }}>Agregar Nuevo Bloqueo</h5>
            
            <div className="form-group">
              <label className="form-label">Tipo de Jugada</label>
              <CompactSelect
                value={blockedPlayForm.playType}
                onChange={(e) => setBlockedPlayForm({ ...blockedPlayForm, playType: e.target.value })}
              >
                <option value="Q">Quiniela</option>
                <option value="P">Palé</option>
                <option value="SP">Super Palé</option>
                <option value="T">Tripleta</option>
                <option value="P3">Pick 3 Straight</option>
                <option value="P3BOX">Pick 3 Box</option>
                <option value="P4">Pick 4 Straight</option>
                <option value="P4BOX">Pick 4 Box</option>
              </CompactSelect>
            </div>

            <div className="form-group">
              <label className="form-label">Número(s) a Bloquear</label>
              <input
                type="text"
                className="form-input"
                placeholder={
                  blockedPlayForm.playType === 'Q' ? 'Ej. 14 (2 dígitos)' :
                  blockedPlayForm.playType === 'P' || blockedPlayForm.playType === 'SP' ? 'Ej. 1422 (4 dígitos)' :
                  blockedPlayForm.playType === 'T' ? 'Ej. 142205 (6 dígitos)' :
                  blockedPlayForm.playType === 'P3' || blockedPlayForm.playType === 'P3BOX' ? 'Ej. 123 (3 dígitos)' : 'Ej. 1234 (4 dígitos)'
                }
                value={blockedPlayForm.number}
                onChange={(e) => setBlockedPlayForm({ ...blockedPlayForm, number: e.target.value })}
                maxLength={blockedPlayForm.playType === 'T' ? 6 : blockedPlayForm.playType === 'Q' ? 2 : blockedPlayForm.playType === 'P3' || blockedPlayForm.playType === 'P3BOX' ? 3 : 4}
                required
              />
            </div>

            <ActionButton type="submit" variant="danger" style={{ width: '100%', marginTop: '8px' }}>
              Bloquear Jugada
            </ActionButton>
          </form>

          {/* List of Blocked Plays */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h5 style={{ fontSize: '0.9rem', fontWeight: 600, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Jugadas Bloqueadas Actualmente</span>
              <span className="badge" style={{ backgroundColor: 'hsl(var(--danger) / 0.1)', color: 'hsl(var(--danger))', fontSize: '0.7rem' }}>
                {blockedSalePlays.length} Bloqueo(s)
              </span>
            </h5>

            {blockedSalePlays.length === 0 ? (
              <div style={{ padding: '24px', textAlign: 'center', color: 'hsl(var(--text-muted))', backgroundColor: 'hsl(var(--background))', borderRadius: 'var(--radius-md)', border: '1px dashed hsl(var(--border))' }}>
                No tienes jugadas bloqueadas en esta banca.
              </div>
            ) : (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', maxHeight: '280px', overflowY: 'auto', paddingRight: '8px' }}>
                {blockedSalePlays.map((play, index) => {
                  const displayType = 
                    play.playType === 'Q' ? 'Quin' :
                    play.playType === 'P' ? 'Palé' :
                    play.playType === 'SP' ? 'S.Palé' :
                    play.playType === 'T' ? 'Trip' :
                    play.playType === 'P3' ? 'P3 Str' :
                    play.playType === 'P3BOX' ? 'P3 Box' :
                    play.playType === 'P4' ? 'P4 Str' : 'P4 Box';
                  
                  return (
                    <div key={index} className="glass-panel" style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '6px 12px',
                      borderRadius: '20px',
                      border: '1px solid hsl(var(--danger) / 0.2)',
                      fontSize: '0.8rem',
                      backgroundColor: 'hsl(var(--danger) / 0.03)',
                      boxShadow: 'none'
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontWeight: 700, color: 'hsl(var(--text-primary))', fontFamily: 'monospace' }}>
                          {play.number}
                        </span>
                        <span style={{ fontSize: '0.65rem', color: 'hsl(var(--text-muted))', backgroundColor: 'hsl(var(--surface-hover))', padding: '2px 6px', borderRadius: '4px' }}>
                          {displayType}
                        </span>
                      </div>
                      <button
                        onClick={() => handleRemoveBlockedPlay(play)}
                        style={{
                          border: 'none',
                          background: 'transparent',
                          color: 'hsl(var(--text-muted))',
                          cursor: 'pointer',
                          padding: '2px',
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          transition: 'all 0.2s',
                          width: '18px',
                          height: '18px'
                        }}
                      >
                        <X size={12} />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* CARD 6: LOTTERY STATUS TOGGLES — SPLIT INTO TRADITIONAL & PICKS */}
      {(() => {
        const pickTypes = new Set(['Pick3', 'Pick4']);
        const classicLotteries = lotteries.filter(l => {
          const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === l.id);
          return !pickTypes.has(catalogEntry?.type ?? '');
        });
        const pickLotteries = lotteries.filter(l => {
          const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === l.id);
          return pickTypes.has(catalogEntry?.type ?? '');
        });

        const renderLotteryCard = (l: typeof lotteries[0]) => {
          const isDisabled = manualDisabledLotteryIds.includes(l.id);
          const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === l.id);
          const logoUrl = catalogEntry?.logoAssetPath || l.logoAssetPath || '/favicon.svg';
          return (
            <div key={l.id} className="glass-panel" style={{
              padding: '16px',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
              border: '1px solid ' + (isDisabled ? 'hsl(var(--danger) / 0.3)' : 'hsl(var(--border))'),
              backgroundColor: isDisabled ? 'hsl(var(--danger) / 0.02)' : 'hsl(var(--surface-glow))',
              boxShadow: 'none'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <img
                  src={logoUrl}
                  alt={l.name}
                  style={{ width: '28px', height: '28px', borderRadius: '4px', objectFit: 'contain' }}
                  onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                />
                <div>
                  <strong style={{ fontSize: '0.85rem', display: 'block' }}>{l.name}</strong>
                  <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>{l.territory}</span>
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid hsl(var(--border))', paddingTop: '10px' }}>
                <span style={{ fontSize: '0.75rem', color: isDisabled ? 'hsl(var(--danger))' : 'hsl(var(--success))', fontWeight: 600 }}>
                  {isDisabled ? '⛔ Cerrada/Bloqueada' : '✅ Venta Abierta'}
                </span>
                <label style={{ display: 'inline-flex', alignItems: 'center', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={!isDisabled}
                    onChange={() => handleToggleManualDisabledLottery(l.id)}
                    style={{ width: '16px', height: '16px', cursor: 'pointer', accentColor: 'var(--success)' }}
                  />
                </label>
              </div>
            </div>
          );
        };

        return (
          <>
            {/* ── Lotería Tradicional ── */}
            <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '24px' }}>
              <div>
                <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Settings size={18} color="var(--primary)" />
                  Cierres Manuales — Lotería Tradicional
                </h4>
                <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                  Deshabilita sorteos clásicos (RD y USA tradicional) de forma temporal o definitiva.
                </p>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
                {classicLotteries.map(renderLotteryCard)}
              </div>
            </div>

            {/* ── Picks USA (Pick 3 / Pick 4) ── */}
            <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '16px' }}>
              <div>
                <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Settings size={18} color="hsl(var(--warning, 38 92% 50%))" />
                  Cierres Manuales — Picks USA (Pick 3 / Pick 4)
                </h4>
                <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                  Controla individualmente cada sorteo de Pick 3 y Pick 4 de los estados de EE.UU.
                </p>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
                {pickLotteries.map(renderLotteryCard)}
              </div>
            </div>
          </>
        );
      })()}

      {/* SAVE BUTTON ACTION BAR */}
      <div className="glass-panel" style={{ padding: '20px 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
        <ActionButton
          variant="success"
          onClick={() => setLimitsConfirmOpen(true)}
          style={{
            padding: '12px 32px',
            fontSize: '0.95rem',
            fontWeight: 600,
            boxShadow: 'var(--shadow-glow)'
          }}
        >
          Guardar Cambios
        </ActionButton>
      </div>

    </div>
  );
};
