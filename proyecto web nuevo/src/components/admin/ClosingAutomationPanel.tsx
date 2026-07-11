import React from 'react';
import { ActionButton, Panel, PanelHeader, StatusBadge } from '../ui';

interface Props {
  emailEnabled: boolean;
  whatsappEnabled: boolean;
  onToggleEmail: (enabled: boolean) => void;
  onToggleWhatsapp: (enabled: boolean) => void;
  onSave: () => void;
}

export const ClosingAutomationPanel: React.FC<Props> = ({ emailEnabled, whatsappEnabled, onToggleEmail, onToggleWhatsapp, onSave }) => {
  return (
    <Panel tone="primary" className="flex flex-col gap-4">
      <PanelHeader
        title="Cierre y listado automático"
        subtitle="Snapshot al cierre de lotería y envío de listado operativo"
        action={<StatusBadge tone={emailEnabled || whatsappEnabled ? 'success' : 'neutral'}>{emailEnabled || whatsappEnabled ? 'Activo' : 'Manual'}</StatusBadge>}
      />
      <label className="flex items-center justify-between gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
        <span className="text-sm font-semibold text-ln-text-primary">Enviar listado por email al cierre</span>
        <input className="h-5 w-5 accent-ln-primary" type="checkbox" checked={emailEnabled} onChange={(event) => onToggleEmail(event.target.checked)} />
      </label>
      <label className="flex items-center justify-between gap-3 rounded-ln-md border border-ln-border bg-ln-surface/75 p-4">
        <span className="text-sm font-semibold text-ln-text-primary">Enviar listado por WhatsApp al cierre</span>
        <input className="h-5 w-5 accent-ln-primary" type="checkbox" checked={whatsappEnabled} onChange={(event) => onToggleWhatsapp(event.target.checked)} />
      </label>
      <ActionButton variant="primary" onClick={onSave} className="self-start">
        Guardar automatización
      </ActionButton>
    </Panel>
  );
};
