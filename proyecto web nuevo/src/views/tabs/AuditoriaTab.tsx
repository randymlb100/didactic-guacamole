import React from 'react';
import { Info } from 'lucide-react';
import type { AuditLog } from '../../types';

interface AuditoriaTabProps {
  audits: AuditLog[];
}

export const AuditoriaTab: React.FC<AuditoriaTabProps> = ({ audits }) => {
  return (
    <div className="fade-in glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <h3 style={{ fontSize: '1.15rem' }}>Bitácora de Auditoría del Sistema</h3>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {audits.map((a) => (
          <div
            key={a.id}
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '16px',
              padding: '16px',
              borderRadius: 'var(--radius-md)',
              border: '1px solid hsl(var(--border))',
              backgroundColor: 'hsl(var(--surface))'
            }}
          >
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '50%',
                backgroundColor: a.status === 'success' ? 'hsl(var(--success) / 0.1)' : 'hsl(var(--warning) / 0.1)',
                color: a.status === 'success' ? 'hsl(var(--success))' : 'hsl(var(--warning))',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}
            >
              <Info size={18} />
            </div>

            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <strong style={{ fontSize: '0.9rem' }}>{a.action}</strong>
                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                  {new Date(a.timestampMs).toLocaleString()}
                </span>
              </div>
              <p style={{ fontSize: '0.85rem', color: 'hsl(var(--text-secondary))', lineHeight: '1.5' }}>
                {a.details}
              </p>
              <div style={{ marginTop: '8px', display: 'flex', gap: '10px', fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                <span>Actor: <strong>@{a.actorUser}</strong> ({a.role})</span>
                <span>•</span>
                <span>IP: {a.ipAddress}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
