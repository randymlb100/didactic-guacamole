import React from 'react';
import { AlertTriangle, Share2 } from 'lucide-react';
import { shareTicket, shareTicketTextWhatsApp } from '../utils/shareTicket';
import type { TicketRecord, SportsTicketRecord } from '../types';
import { ActionButton, ModalCard, ModalShell, PanelHeader, StatusBadge } from './ui';

// --- ANNUL TICKET CONFIRMATION MODAL ---
interface AnnulTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  ticket: TicketRecord | null;
  annulTimer: number;
  user: any;
}

export const AnnulTicketModal: React.FC<AnnulTicketModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  ticket,
  annulTimer,
  user
}) => {
  if (!isOpen || !ticket) return null;

  const ticketTime = Number(ticket.createdAtEpochMs);
  const elapsed = annulTimer - ticketTime;
  const isTimeLimitExceeded = elapsed > 120000;
  const remainingSecs = Math.max(0, Math.floor((120000 - elapsed) / 1000));
  const canBypass = user.role === 'ADMIN' || user.role === 'MASTER' || user.role === 'SUPERVISOR';

  return (
    <ModalShell>
      <ModalCard className="flex flex-col gap-4 border-ln-danger/25">
        <PanelHeader
          title="Anulación de Ticket"
          action={<AlertTriangle size={24} className="text-ln-danger" />}
        />
        
        <p className="text-sm leading-6 text-ln-text-secondary">
          ¿Está seguro que desea anular el ticket <strong className="text-ln-text-primary">{ticket.serial || ticket.id}</strong>?
          Esta acción es irreversible y restablecerá el balance de caja del cajero <strong className="text-ln-text-primary">@{ticket.sellerUser}</strong> devolviendo <strong className="text-ln-primary">${ticket.total.toFixed(2)}</strong>.
        </p>

        <div className="rounded-ln-md border border-ln-border bg-ln-background/55 p-3 text-sm">
          <TicketInfoRow label="Emisor" value={`@${ticket.sellerUser}`} />
          <TicketInfoRow label="Monto" value={`$${ticket.total.toFixed(2)}`} />
          <TicketInfoRow label="Hora Emisión" value={new Date(ticket.createdAtEpochMs).toLocaleTimeString()} />
          
          <div className="mt-3 flex flex-col gap-2 border-t border-ln-border pt-3">
            {!canBypass ? (
              isTimeLimitExceeded ? (
                <StatusBadge tone="danger">Límite de 2 minutos superado. Bloqueado.</StatusBadge>
              ) : (
                <StatusBadge tone="primary">Ventana de anulación activa: {remainingSecs}s restantes.</StatusBadge>
              )
            ) : (
              <StatusBadge tone="success">Permiso gerencial ({user.role}): Omitiendo límite de 2m.</StatusBadge>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton
            variant="danger"
            disabled={!canBypass && isTimeLimitExceeded}
            onClick={onConfirm}
          >
            Confirmar Anulación
          </ActionButton>
          <ActionButton onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
      </ModalCard>
    </ModalShell>
  );
};

// --- DELETE TICKET PHYSICAL CONFIRMATION ---
interface DeleteTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  ticket: TicketRecord | null;
  isDeleting: boolean;
}

export const DeleteTicketModal: React.FC<DeleteTicketModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  ticket,
  isDeleting
}) => {
  if (!isOpen || !ticket) return null;

  return (
    <ModalShell>
      <ModalCard className="flex flex-col gap-4 border-2 border-ln-danger/40">
        <PanelHeader
          title="¡ELIMINACIÓN FÍSICA CRÍTICA!"
          action={<AlertTriangle size={24} className="text-ln-danger" />}
        />
        
        <p className="text-sm leading-6 text-ln-text-secondary">
          ¿Está completamente seguro que desea <strong className="text-ln-danger">ELIMINAR FÍSICAMENTE</strong> el ticket <strong className="text-ln-text-primary">{ticket.serial || ticket.id}</strong> del servidor?
          <br/><br/>
          <span className="font-bold text-ln-danger">ADVERTENCIA: Esta acción es 100% irreversible.</span> Se borrará del registro de Supabase y recalculará la caja disponible y fianza a cero si es necesario.
        </p>

        <div className="rounded-ln-md border border-ln-border bg-ln-background/55 p-3 text-sm">
          <TicketInfoRow label="Emisor" value={`@${ticket.sellerUser}`} />
          <TicketInfoRow label="Monto" value={`$${ticket.total.toFixed(2)}`} />
          <TicketInfoRow label="Hora" value={new Date(ticket.createdAtEpochMs).toLocaleTimeString()} />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ActionButton
            variant="danger"
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? 'Eliminando...' : 'Eliminar Permanentemente'}
          </ActionButton>
          <ActionButton
            onClick={onClose}
            disabled={isDeleting}
          >
            Cancelar
          </ActionButton>
        </div>
      </ModalCard>
    </ModalShell>
  );
};

const TicketInfoRow = ({ label, value }: { label: string; value: string }) => (
  <div className="mb-1.5 flex justify-between gap-3 last:mb-0">
    <span className="text-ln-text-secondary">{label}:</span>
    <strong className="text-right text-ln-text-primary">{value}</strong>
  </div>
);

// --- TICKET DETAIL MODAL (THERMAL RECEIPT) ---
interface TicketDetailModalProps {
  ticket: TicketRecord | null;
  onClose: () => void;
}

export const TicketDetailModal: React.FC<TicketDetailModalProps> = ({
  ticket,
  onClose
}) => {
  if (!ticket) return null;

  const uniqueLots = new Set<string>();
  ticket.plays.forEach(p => {
    if (p.lotteryName) {
      p.lotteryName.split(/[\/,]+/).forEach(part => {
        const trimmed = part.trim();
        if (trimmed) uniqueLots.add(trimmed);
      });
    }
  });

  const statusColor = ticket.status === 'paid' ? '#10b981'
    : ticket.status === 'cancelled' || ticket.status === 'voided' ? '#ef4444'
    : ticket.status === 'winner' ? '#f59e0b' : '#3b82f6';

  return (
    <div 
      className="ticket-detail-modal-overlay"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 102,
        backdropFilter: 'blur(6px)'
      }}
    >
      <div className="ticket-detail-modal-card fade-in" style={{
        maxWidth: '380px',
        width: '100%',
        backgroundColor: '#ffffff',
        color: '#111111',
        fontFamily: '"Courier New", Courier, monospace',
        padding: '24px',
        boxShadow: '0 20px 25px -5px rgb(0 0 0 / 0.3), 0 8px 10px -6px rgb(0 0 0 / 0.3)',
        borderRadius: '8px',
        display: 'block',
        position: 'relative',
        maxHeight: '90vh',
        overflowY: 'auto'
      }}>
        {/* Watermark of status */}
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%) rotate(-30deg)',
          fontSize: '3rem',
          fontWeight: 900,
          opacity: 0.12,
          pointerEvents: 'none',
          width: '100%',
          textAlign: 'center',
          color: statusColor,
          border: `6px double ${statusColor}`
        }}>
          {ticket.status === 'paid' ? 'COBRADO' 
           : ticket.status === 'cancelled' || ticket.status === 'voided' ? 'ANULADO' 
           : ticket.status === 'winner' ? 'PREMIO PENDIENTE' : 'ACTIVO'}
        </div>

        {/* Thermal Header */}
        <div style={{ textAlign: 'center', borderBottom: '1px dashed #111111', paddingBottom: '12px', marginBottom: '12px' }}>
          <h4 style={{ margin: '0 0 4px 0', fontSize: '1.1rem', fontWeight: 'bold', fontFamily: 'sans-serif' }}>BANCA EL FUERTE</h4>
          <p style={{ margin: 0, fontSize: '0.75rem', textTransform: 'uppercase' }}>Consorcio de Loterías RD</p>
          <p style={{ margin: '4px 0 0 0', fontSize: '0.75rem' }}>Cajero: @{ticket.sellerUser}</p>
        </div>

        {/* Ticket Info */}
        <div style={{ fontSize: '0.75rem', marginBottom: '12px', display: 'flex', flexDirection: 'column', gap: '2px' }}>
          <div>FECHA: {new Date(ticket.createdAtEpochMs).toLocaleString('es-DO', { timeZone: 'America/Santo_Domingo' })}</div>
          <div>SERIAL: {ticket.serial || ticket.id}</div>
          <div>TICKET ID: {ticket.id}</div>
          <div style={{ borderBottom: '1px dashed #111111', margin: '6px 0' }} />
          <div>
            <strong>LOTERÍAS:</strong>{' '}
            {Array.from(uniqueLots).join(' / ')}
          </div>
        </div>

        {/* Plays Table Header */}
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', fontSize: '0.8rem', fontWeight: 'bold', borderBottom: '1px solid #111111', paddingBottom: '4px', marginBottom: '4px' }}>
          <span>JUGADA</span>
          <span style={{ textAlign: 'center' }}>TIPO</span>
          <span style={{ textAlign: 'right' }}>MONTO</span>
        </div>

        {/* Plays Table Body */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.75rem', minHeight: '60px' }}>
          {ticket.plays.map((p, idx) => (
            <div key={idx} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr' }}>
              <span style={{ fontWeight: 'bold' }}>{p.number}</span>
              <span style={{ textAlign: 'center' }}>{p.playType.toUpperCase()}</span>
              <span style={{ textAlign: 'right' }}>${p.amount.toFixed(2)}</span>
            </div>
          ))}
        </div>

        {/* Total Footer */}
        <div style={{ borderTop: '1px dashed #111111', marginTop: '12px', paddingTop: '8px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem' }}>
            <span>SUBTOTAL:</span>
            <span>${ticket.total.toFixed(2)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem' }}>
            <span>DESCUENTO:</span>
            <span>$0.00</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem', fontWeight: 'bold', borderTop: '1px solid #111111', paddingTop: '4px' }}>
            <span>TOTAL APOSTADO:</span>
            <span>${ticket.total.toFixed(2)}</span>
          </div>
          {ticket.totalPrize > 0 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem', fontWeight: 'bold', color: '#dc2626' }}>
              <span>PREMIO ACUMULADO:</span>
              <span>${ticket.totalPrize.toFixed(2)}</span>
            </div>
          )}
        </div>

        {/* Simulated Barcode */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: '20px', gap: '4px' }}>
          <div style={{
            height: '40px',
            width: '100%',
            background: 'repeating-linear-gradient(90deg, #111 0px, #111 2px, transparent 2px, transparent 6px, #111 6px, #111 7px, transparent 7px, transparent 10px)',
            opacity: 0.8
          }} />
          <span style={{ fontSize: '0.65rem' }}>*{ticket.id.substring(0, 18).toUpperCase()}*</span>
        </div>

        {/* Buttons for actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '24px', fontFamily: 'sans-serif' }} className="no-print">
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              className="btn"
              style={{ flex: 1, backgroundColor: '#25D366', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', fontWeight: 'bold' }}
              onClick={() => shareTicket(ticket, false)}
            >
              <Share2 size={14} />
              Compartir Imagen
            </button>
            <button
              className="btn"
              style={{ flex: 1, backgroundColor: '#128C7E', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', fontWeight: 'bold' }}
              onClick={() => shareTicketTextWhatsApp(ticket, false)}
            >
              WhatsApp Texto
            </button>
          </div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              className="btn btn-primary"
              style={{ flex: 1, backgroundColor: '#111111', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px' }}
              onClick={() => window.print()}
            >
              Imprimir
            </button>
            <button
              className="btn btn-secondary"
              style={{ flex: 1, border: '1px solid #111111', color: '#111111', background: '#ffffff', fontSize: '0.8rem', padding: '8px' }}
              onClick={onClose}
            >
              Cerrar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

// --- SPORTS TICKET DETAIL MODAL (THERMAL RECEIPT) ---
interface SportsTicketDetailModalProps {
  ticket: SportsTicketRecord | null;
  onClose: () => void;
}

export const SportsTicketDetailModal: React.FC<SportsTicketDetailModalProps> = ({
  ticket,
  onClose
}) => {
  if (!ticket) return null;

  const statusBg = ticket.status === 'paid' ? 'rgba(16, 185, 129, 0.15)'
    : ticket.status === 'void' ? 'rgba(107, 114, 128, 0.15)'
    : ticket.status === 'lost' ? 'rgba(239, 68, 68, 0.15)'
    : ticket.status === 'won' ? 'rgba(59, 130, 246, 0.15)' : 'rgba(245, 158, 11, 0.15)';

  const statusColor = ticket.status === 'paid' ? '#10b981'
    : ticket.status === 'void' ? '#6b7280'
    : ticket.status === 'lost' ? '#ef4448'
    : ticket.status === 'won' ? '#3b82f6' : '#f59e0b';

  return (
    <div 
      className="ticket-detail-modal-overlay"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 102,
        backdropFilter: 'blur(6px)'
      }}
    >
      <div className="ticket-detail-modal-card fade-in" style={{
        maxWidth: '380px',
        width: '100%',
        backgroundColor: '#ffffff',
        color: '#111111',
        fontFamily: '"Courier New", Courier, monospace',
        padding: '24px',
        boxShadow: '0 20px 25px -5px rgb(0 0 0 / 0.3), 0 8px 10px -6px rgb(0 0 0 / 0.3)',
        borderRadius: '8px',
        display: 'block',
        position: 'relative',
        maxHeight: '90vh',
        overflowY: 'auto'
      }}>
        {/* Watermark of status */}
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%) rotate(-30deg)',
          fontSize: '3rem',
          fontWeight: 900,
          color: statusBg,
          border: `6px double ${statusColor}`,
          padding: '10px 20px',
          borderRadius: '8px',
          pointerEvents: 'none',
          zIndex: 1,
          whiteSpace: 'nowrap'
        }}>
          {ticket.status === 'paid' ? 'COBRADO'
           : ticket.status === 'void' ? 'ANULADO'
           : ticket.status === 'lost' ? 'PERDIDO'
           : ticket.status === 'won' ? 'PREMIO PENDIENTE' : 'PENDIENTE'}
        </div>

        {/* Thermal Header */}
        <div style={{ textAlign: 'center', borderBottom: '1px dashed #111111', paddingBottom: '12px', marginBottom: '12px' }}>
          <h4 style={{ margin: '0 0 4px 0', fontSize: '1.1rem', fontWeight: 'bold', fontFamily: 'sans-serif' }}>BANCA EL FUERTE</h4>
          <span style={{ fontSize: '0.8rem', display: 'block', textTransform: 'uppercase' }}>{ticket.bancaName || 'BANCA DEPORTIVA'}</span>
          <p style={{ margin: '4px 0 0 0', fontSize: '0.75rem' }}>Cajero: @{ticket.sellerUsername}</p>
        </div>

        {/* Ticket Info */}
        <div style={{ fontSize: '0.75rem', marginBottom: '12px', display: 'flex', flexDirection: 'column', gap: '2px' }}>
          <div>FECHA: {new Date(ticket.soldAt).toLocaleString('es-DO')}</div>
          <div>TICKET ID: {ticket.ticketCode}</div>
          <div>TIPO: {ticket.ticketType.toUpperCase()}</div>
          <div style={{ borderBottom: '1px dashed #111111', margin: '6px 0' }} />
        </div>

        {/* Legs Body */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '0.75rem', borderBottom: '1px dashed #111111', paddingBottom: '12px' }}>
          {(ticket.legs || []).map((leg, idx) => (
            <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '2px', borderBottom: idx < ticket.legs.length - 1 ? '1px dashed #e5e7eb' : 'none', paddingBottom: '8px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 'bold' }}>
                <span>{leg.eventLabel}</span>
                <span>x{Number(leg.decimalOdds).toFixed(2)}</span>
              </div>
              <div style={{ color: '#4b5563', fontSize: '0.75rem' }}>
                Mercado: {leg.marketTitle} | Selección: {leg.selectionLabel}
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: '#9ca3af' }}>
                <span>Resultado: {leg.status.toUpperCase()}</span>
              </div>
            </div>
          ))}
        </div>

        {/* Total Footer */}
        <div style={{ marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem' }}>
            <span>MONTO APOSTADO:</span>
            <strong>${Number(ticket.stake).toFixed(2)}</strong>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem' }}>
            <span>PROBABILIDAD:</span>
            <strong>x{Number(ticket.decimalOdds).toFixed(2)}</strong>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem', fontWeight: 'bold', borderTop: '1px solid #111111', paddingTop: '4px' }}>
            <span>POTENCIAL PAGO:</span>
            <span style={{ color: '#10b981' }}>${Number(ticket.potentialPayout).toFixed(2)}</span>
          </div>
        </div>

        {/* Simulated Barcode */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: '20px', gap: '4px' }}>
          <div style={{
            height: '40px',
            width: '100%',
            background: 'repeating-linear-gradient(90deg, #111 0px, #111 2px, transparent 2px, transparent 6px, #111 6px, #111 7px, transparent 7px, transparent 10px)',
            opacity: 0.8
          }} />
          <span style={{ fontSize: '0.65rem' }}>*{ticket.ticketCode.substring(0, 18).toUpperCase()}*</span>
        </div>

        {/* Buttons for actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '24px', fontFamily: 'sans-serif' }} className="no-print">
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              className="btn"
              style={{ flex: 1, backgroundColor: '#25D366', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', fontWeight: 'bold' }}
              onClick={() => shareTicket(ticket, true)}
            >
              <Share2 size={14} />
              Compartir Imagen
            </button>
            <button
              className="btn"
              style={{ flex: 1, backgroundColor: '#128C7E', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', fontWeight: 'bold' }}
              onClick={() => shareTicketTextWhatsApp(ticket, true)}
            >
              WhatsApp Texto
            </button>
          </div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              className="btn btn-primary"
              style={{ flex: 1, backgroundColor: '#111111', color: '#ffffff', border: 'none', fontSize: '0.8rem', padding: '8px' }}
              onClick={() => window.print()}
            >
              Imprimir
            </button>
            <button
              className="btn btn-secondary"
              style={{ flex: 1, border: '1px solid #111111', color: '#111111', background: '#ffffff', fontSize: '0.8rem', padding: '8px' }}
              onClick={onClose}
            >
              Cerrar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
