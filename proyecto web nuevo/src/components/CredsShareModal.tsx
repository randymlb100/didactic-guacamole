import React from 'react';
import { ActionButton, ModalCard, ModalShell, PanelHeader } from './ui';

interface CredsShareModalProps {
  isOpen: boolean;
  onClose: () => void;
  shareText: string;
}

export const CredsShareModal: React.FC<CredsShareModalProps> = ({
  isOpen,
  onClose,
  shareText
}) => {
  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(shareText);
    alert('Credenciales copiadas al portapapeles.');
  };

  const handleShareWhatsApp = () => {
    const url = `https://api.whatsapp.com/send?text=${encodeURIComponent(shareText)}`;
    window.open(url, '_blank');
  };

  return (
    <ModalShell>
      <ModalCard className="flex flex-col gap-4" size="md">
        <PanelHeader title="Credenciales Generadas con Éxito" />
        
        <textarea
          readOnly
          value={shareText}
          className="form-input h-60 w-full resize-none rounded-ln-sm border border-ln-border bg-ln-background p-3 font-mono text-sm leading-relaxed text-ln-text-primary"
        />

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_1fr_auto]">
          <ActionButton variant="primary" onClick={handleCopy}>
            Copiar Texto
          </ActionButton>
          <ActionButton variant="info" onClick={handleShareWhatsApp}>
            Compartir por WhatsApp
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cerrar
          </ActionButton>
        </div>
      </ModalCard>
    </ModalShell>
  );
};
