import React from 'react';

interface Props {
  emailEnabled: boolean;
  whatsappEnabled: boolean;
  onToggleEmail: (enabled: boolean) => void;
  onToggleWhatsapp: (enabled: boolean) => void;
  onSave: () => void;
}

export const ClosingAutomationPanel: React.FC<Props> = ({ emailEnabled, whatsappEnabled, onToggleEmail, onToggleWhatsapp, onSave }) => {
  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <h3 className="fintech-panel-title">Cierre y listado automático</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>
          Snapshot al cierre de lotería y envío de listado operativo
        </span>
      </div>
      <label className="glass-panel" style={{ padding: 14, display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
        <span>Enviar listado por email al cierre</span>
        <input type="checkbox" checked={emailEnabled} onChange={(event) => onToggleEmail(event.target.checked)} />
      </label>
      <label className="glass-panel" style={{ padding: 14, display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
        <span>Enviar listado por WhatsApp al cierre</span>
        <input type="checkbox" checked={whatsappEnabled} onChange={(event) => onToggleWhatsapp(event.target.checked)} />
      </label>
      <button type="button" className="btn btn-primary" onClick={onSave} style={{ alignSelf: 'flex-start' }}>Guardar automatización</button>
    </div>
  );
};
