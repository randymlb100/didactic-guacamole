import React, { useState } from 'react';
import { createDrawResult, addAuditLog, STATIC_LOTTERIES } from '../../utils/supabase';
import type { LotteryCatalogItem, DrawResult } from '../../types';

interface ResultadosTabProps {
  user: any;
  lotteries: LotteryCatalogItem[];
  resultsList: DrawResult[];
  setResultsList: React.Dispatch<React.SetStateAction<DrawResult[]>>;
}

export const ResultadosTab: React.FC<ResultadosTabProps> = ({
  user,
  lotteries,
  resultsList,
  setResultsList
}) => {
  const [resultForm, setResultForm] = useState({
    lotteryId: lotteries[0]?.id || '',
    r1: '',
    r2: '',
    r3: '',
    dateKey: new Date().toISOString().slice(0, 10)
  });

  const handleCreateResult = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const targetLottery = lotteries.find((l) => l.id === resultForm.lotteryId);
    const newResult = {
      id: `R-${Math.random().toString(36).substr(2, 6).toUpperCase()}`,
      lotteryId: resultForm.lotteryId,
      lotteryName: targetLottery?.name || 'Lotería',
      dateKey: resultForm.dateKey,
      numbers: `${resultForm.r1}-${resultForm.r2}-${resultForm.r3}`
    };

    try {
      await createDrawResult(newResult);
      setResultsList([newResult, ...resultsList]);

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'CREATE_RESULT',
        `Registrado número ganador manualmente para ${targetLottery?.name} (${resultForm.dateKey}): ${newResult.numbers}`,
        'success'
      );

      setResultForm({
        ...resultForm,
        r1: '',
        r2: '',
        r3: ''
      });
      alert('Resultado de lotería registrado correctamente.');
    } catch (e) {
      console.error(e);
      alert('Error registrando el resultado de lotería.');
    }
  };

  const normalResults = resultsList.filter((r) => {
    const lot = STATIC_LOTTERIES.find((l) => l.id === r.lotteryId) || lotteries.find((l) => l.id === r.lotteryId);
    if (!lot) return !r.lotteryId.startsWith('US-P');
    return lot.type !== 'Pick3' && lot.type !== 'Pick4';
  });

  const pickResults = resultsList.filter((r) => {
    const lot = STATIC_LOTTERIES.find((l) => l.id === r.lotteryId) || lotteries.find((l) => l.id === r.lotteryId);
    if (!lot) return r.lotteryId.startsWith('US-P');
    return lot.type === 'Pick3' || lot.type === 'Pick4';
  });

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Form Manual for Master */}
      {user.role === 'MASTER' && (
        <form
          onSubmit={handleCreateResult}
          className="glass-panel"
          style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}
        >
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Registrar Números Ganadores Manualmente</h3>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Lotería Sorteada</label>
              <select
                className="form-input"
                value={resultForm.lotteryId}
                onChange={(e) => setResultForm({ ...resultForm, lotteryId: e.target.value })}
              >
                {lotteries.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name} ({l.territory})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">1era (Primera)</label>
              <input
                type="text"
                placeholder="ej. 14"
                value={resultForm.r1}
                onChange={(e) => setResultForm({ ...resultForm, r1: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                className="form-input"
                required
                maxLength={2}
              />
            </div>

            <div className="form-group">
              <label className="form-label">2da (Segunda)</label>
              <input
                type="text"
                placeholder="ej. 22"
                value={resultForm.r2}
                onChange={(e) => setResultForm({ ...resultForm, r2: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                className="form-input"
                required
                maxLength={2}
              />
            </div>

            <div className="form-group">
              <label className="form-label">3era (Tercera)</label>
              <input
                type="text"
                placeholder="ej. 05"
                value={resultForm.r3}
                onChange={(e) => setResultForm({ ...resultForm, r3: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                className="form-input"
                required
                maxLength={2}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Fecha del Sorteo</label>
              <input
                type="date"
                value={resultForm.dateKey}
                onChange={(e) => setResultForm({ ...resultForm, dateKey: e.target.value })}
                className="form-input"
                required
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
            <button type="submit" className="btn btn-primary" style={{ padding: '10px 24px' }}>
              Registrar Resultado
            </button>
          </div>
        </form>
      )}

      {/* List of Draw Results */}
      <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '32px' }}>
        {/* Section 1: Traditional Lotteries */}
        <div>
          <h3
            style={{
              fontSize: '1.1rem',
              fontWeight: 700,
              marginBottom: '16px',
              color: 'hsl(var(--primary))',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'hsl(var(--primary))' }} />
            Loterías Tradicionales (Quiniela / Palé / Tripleta)
          </h3>

          {normalResults.length === 0 ? (
            <div
              style={{
                padding: '20px',
                textAlign: 'center',
                color: 'hsl(var(--text-muted))',
                backgroundColor: 'hsl(var(--background))',
                borderRadius: 'var(--radius-md)'
              }}
            >
              No hay resultados tradicionales registrados.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
              {normalResults.map((res) => {
                const catalogEntry =
                  STATIC_LOTTERIES.find((l) => l.id === res.lotteryId) || lotteries.find((l) => l.id === res.lotteryId);
                const logoUrl = catalogEntry?.logoAssetPath || '/favicon.svg';

                return (
                  <div
                    key={res.id}
                    className="glass-panel-premium table-row-stagger"
                    style={{
                      padding: '20px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                      border: '1px solid hsl(var(--primary) / 0.2)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <img
                          src={logoUrl}
                          alt={res.lotteryName}
                          style={{
                            width: '28px',
                            height: '28px',
                            borderRadius: '4px',
                            objectFit: 'contain',
                            backgroundColor: 'rgba(255,255,255,0.05)',
                            padding: '2px'
                          }}
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/favicon.svg';
                          }}
                        />
                        <div>
                          <strong style={{ fontSize: '0.95rem', color: 'hsl(var(--text-primary))', display: 'block' }}>
                            {res.lotteryName}
                          </strong>
                          {catalogEntry?.baseDrawTime && (
                            <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', display: 'block', marginTop: '2px' }}>
                              Sorteo: {catalogEntry.baseDrawTime}
                            </span>
                          )}
                        </div>
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{res.dateKey}</span>
                    </div>

                    {/* Domino style numbered balls */}
                    <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', margin: '6px 0' }}>
                      {res.numbers.split('-').map((num: string, idx: number) => (
                        <div
                          key={idx}
                          style={{
                            width: '42px',
                            height: '42px',
                            borderRadius: '50%',
                            backgroundColor: idx === 0 ? 'hsl(var(--primary))' : 'hsl(var(--surface))',
                            color: idx === 0 ? '#fff' : 'hsl(var(--text-primary))',
                            border: '2px solid hsl(var(--primary))',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            fontSize: '1rem',
                            fontFamily: 'monospace',
                            boxShadow:
                              idx === 0
                                ? '0 0 14px hsl(var(--primary) / 0.8), inset 0 2px 4px rgba(255,255,255,0.2)'
                                : '0 0 8px hsl(var(--primary) / 0.25)'
                          }}
                        >
                          {num}
                        </div>
                      ))}
                    </div>

                    <div
                      style={{
                        textAlign: 'center',
                        fontSize: '0.725rem',
                        color: 'hsl(var(--text-secondary))',
                        borderTop: '1px solid hsl(var(--border) / 0.5)',
                        paddingTop: '8px'
                      }}
                    >
                      Posiciones: 1ra · 2da · 3ra
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Section 2: USA Pick Lotteries */}
        <div>
          <h3
            style={{
              fontSize: '1.1rem',
              fontWeight: 700,
              marginBottom: '16px',
              color: 'hsl(var(--warning))',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'hsl(var(--warning))' }} />
            Loterías Americanas (Pick 3 / Pick 4 USA)
          </h3>

          {pickResults.length === 0 ? (
            <div
              style={{
                padding: '20px',
                textAlign: 'center',
                color: 'hsl(var(--text-muted))',
                backgroundColor: 'hsl(var(--background))',
                borderRadius: 'var(--radius-md)'
              }}
            >
              No hay resultados de sorteos Pick registrados.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
              {pickResults.map((res) => {
                const catalogEntry =
                  STATIC_LOTTERIES.find((l) => l.id === res.lotteryId) || lotteries.find((l) => l.id === res.lotteryId);
                const logoUrl = catalogEntry?.logoAssetPath || '/favicon.svg';

                return (
                  <div
                    key={res.id}
                    className="glass-panel-premium table-row-stagger"
                    style={{
                      padding: '20px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                      border: '1px solid hsl(var(--warning) / 0.2)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <img
                          src={logoUrl}
                          alt={res.lotteryName}
                          style={{
                            width: '28px',
                            height: '28px',
                            borderRadius: '4px',
                            objectFit: 'contain',
                            backgroundColor: 'rgba(255,255,255,0.05)',
                            padding: '2px'
                          }}
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/favicon.svg';
                          }}
                        />
                        <div>
                          <strong style={{ fontSize: '0.95rem', color: 'hsl(var(--text-primary))', display: 'block' }}>
                            {res.lotteryName}
                          </strong>
                          {catalogEntry?.baseDrawTime && (
                            <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', display: 'block', marginTop: '2px' }}>
                              Sorteo: {catalogEntry.baseDrawTime}
                            </span>
                          )}
                        </div>
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{res.dateKey}</span>
                    </div>

                    {/* Domino style numbered balls */}
                    <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', margin: '6px 0' }}>
                      {res.numbers.split('-').map((num: string, idx: number) => (
                        <div
                          key={idx}
                          style={{
                            width: '42px',
                            height: '42px',
                            borderRadius: '50%',
                            backgroundColor: 'hsl(var(--surface-hover))',
                            color: 'hsl(var(--warning))',
                            border: '2px solid hsl(var(--warning))',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            fontSize: '1rem',
                            fontFamily: 'monospace',
                            boxShadow: '0 0 14px hsl(var(--warning) / 0.6), inset 0 2px 4px rgba(255,255,255,0.05)'
                          }}
                        >
                          {num}
                        </div>
                      ))}
                    </div>

                    <div
                      style={{
                        textAlign: 'center',
                        fontSize: '0.725rem',
                        color: 'hsl(var(--text-secondary))',
                        borderTop: '1px solid hsl(var(--border) / 0.5)',
                        paddingTop: '8px'
                      }}
                    >
                      Números Ganadores Registrados
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
