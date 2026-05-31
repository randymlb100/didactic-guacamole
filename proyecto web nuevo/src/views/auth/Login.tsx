import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Eye, EyeOff, ShieldAlert, Lock, User, AlertCircle, HelpCircle } from 'lucide-react';

interface LoginProps {
  onSuccess: () => void;
}

export const Login: React.FC<LoginProps> = ({ onSuccess }) => {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [recoveryMode, setRecoveryMode] = useState(false);
  const [recoveryEmail, setRecoveryEmail] = useState('');
  const [recoverySuccess, setRecoverySuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      setErrorMsg('Por favor, complete todos los campos.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const ok = await login(username, password);
      if (ok) {
        onSuccess();
      }
    } catch (err: any) {
      console.error("ERROR EN LOGIN.TSX handleSubmit:", err);
      if (err.message === 'ACCESO_DENEGADO_CAJERO') {
        setErrorMsg('BLOQUE_CAJERO');
      } else {
        setErrorMsg(err.message || 'Credenciales inválidas. Por favor, intente de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleRecoverySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!recoveryEmail) return;
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setRecoverySuccess(true);
    }, 1500);
  };

  // Drawer Access Block Screen for Cashiers
  if (errorMsg === 'BLOQUE_CAJERO') {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: 'radial-gradient(circle at 10% 20%, hsl(var(--background)) 0%, hsl(var(--surface-hover)) 90%)',
        padding: '20px'
      }}>
        <div className="glass-panel fade-in" style={{
          maxWidth: '480px',
          width: '100%',
          padding: '40px',
          textAlign: 'center',
          boxShadow: 'var(--shadow-xl)',
          border: '1px solid hsl(var(--danger) / 0.2)'
        }}>
          <div style={{
            width: '64px',
            height: '64px',
            borderRadius: '50%',
            backgroundColor: 'hsl(var(--danger) / 0.1)',
            color: 'hsl(var(--danger))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 24px auto'
          }}>
            <ShieldAlert size={32} />
          </div>
          
          <h2 style={{ fontSize: '1.5rem', marginBottom: '12px', color: 'hsl(var(--text-primary))', fontFamily: 'var(--font-display)' }}>
            Acceso Denegado
          </h2>
          
          <p style={{ fontSize: '0.925rem', color: 'hsl(var(--text-secondary))', lineHeight: '1.6', marginBottom: '24px' }}>
            Tu usuario posee el rol de **Cajero**. Este panel web está restringido exclusivamente a funciones gerenciales de **Master, Administrador y Supervisor**.
          </p>

          <div style={{
            backgroundColor: 'hsl(var(--background))',
            borderRadius: 'var(--radius-md)',
            padding: '16px',
            textAlign: 'left',
            marginBottom: '24px',
            fontSize: '0.85rem',
            border: '1px solid hsl(var(--border))'
          }}>
            <strong style={{ display: 'block', marginBottom: '4px', color: 'hsl(var(--text-primary))' }}>
              ¿Qué debes hacer?
            </strong>
            <span style={{ color: 'hsl(var(--text-secondary))' }}>
              Para vender tickets, registrar jugadas (Quiniela, Palé, Tripleta) o pagar premios, debes iniciar sesión desde la **Aplicación Android Móvil** de tu terminal de venta asignada.
            </span>
          </div>

          <button className="btn btn-primary" onClick={() => setErrorMsg(null)} style={{ width: '100%' }}>
            Entendido, volver a Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      background: 'radial-gradient(circle at 10% 20%, hsl(var(--background)) 0%, hsl(var(--surface-hover)) 90%)',
      padding: '20px'
    }}>
      <div className="glass-panel fade-in" style={{
        maxWidth: '440px',
        width: '100%',
        padding: '36px',
        boxShadow: 'var(--shadow-xl)',
        backgroundColor: 'hsl(var(--surface) / 0.85)',
        border: '1px solid hsl(var(--border))'
      }}>
        {/* LOGO AREA */}
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div 
            onClick={() => window.location.href = '/'}
            style={{
              width: '96px',
              height: '96px',
              borderRadius: '50%',
              backgroundColor: 'transparent',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '16px',
              boxShadow: '0 8px 30px hsl(var(--primary) / 0.15)',
              cursor: 'pointer'
            }}
          >
            <svg width="96" height="96" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              {/* Circular Teal/Steel-Blue Background */}
              <circle cx="50" cy="50" r="47" fill="#297286" stroke="#ffffff" strokeWidth="2"/>

              {/* Character Group shifted slightly down for centering */}
              <g transform="translate(0, 2)">
                {/* Back Curly Hair Contour (Deep Charcoal) */}
                <path d="M 28,50 C 24,44 25,36 30,32 C 32,26 39,24 44,26 C 48,22 54,22 58,26 C 63,24 70,26 72,32 C 77,36 78,44 74,50 C 76,56 74,62 70,64 L 32,64 C 28,62 26,56 28,50 Z" fill="#1A2124"/>

                {/* Left Ear */}
                <circle cx="28" cy="49" r="6.5" fill="#FFE1C4" stroke="#1A2124" strokeWidth="1.8"/>
                <path d="M 27,47 C 26,48 26,50 28,51" stroke="#1A2124" strokeWidth="1.2" strokeLinecap="round" fill="none"/>

                {/* Right Ear */}
                <circle cx="72" cy="49" r="6.5" fill="#FFE1C4" stroke="#1A2124" strokeWidth="1.8"/>
                <path d="M 73,47 C 74,48 74,50 72,51" stroke="#1A2124" strokeWidth="1.2" strokeLinecap="round" fill="none"/>

                {/* Main Face Shape (Soft Peach/Cream) */}
                <path d="M 31,42 C 31,32 69,32 69,42 C 69,54 68,64 50,65 C 32,64 31,54 31,42 Z" fill="#FFE1C4" stroke="#1A2124" strokeWidth="2"/>

                {/* Forehead Curls / Hairline Overlay (Deep Charcoal) */}
                <path d="M 31,38 Q 36,34 41,38 Q 45,30 50,34 Q 55,30 59,38 Q 64,34 69,38 L 69,32 C 69,32 31,32 31,32 Z" fill="#1A2124"/>

                {/* Nose */}
                <path d="M 48.5,50 Q 50,52.5 51.5,50" stroke="#1A2124" strokeWidth="1.8" strokeLinecap="round" fill="none"/>

                {/* Friendly Smile */}
                <path d="M 42,57 Q 50,63 58,57" stroke="#1A2124" strokeWidth="2.2" strokeLinecap="round" fill="none"/>

                {/* Round Opaque Glasses Frame */}
                {/* Left Frame */}
                <circle cx="41" cy="44" r="9" fill="#152630" stroke="#1A2124" strokeWidth="2.2"/>
                {/* Left Lens Reflection (Diagonal White Bar) */}
                <path d="M 36,47 L 44,39" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" opacity="0.35"/>
                
                {/* Right Frame */}
                <circle cx="59" cy="44" r="9" fill="#152630" stroke="#1A2124" strokeWidth="2.2"/>
                {/* Right Lens Reflection */}
                <path d="M 54,47 L 62,39" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" opacity="0.35"/>

                {/* Glasses Bridge Link */}
                <path d="M 49.5,44 H 50.5" stroke="#1A2124" strokeWidth="2.2" strokeLinecap="round"/>
                
                {/* Glasses Temples (Sides) */}
                <path d="M 32,44 H 29" stroke="#1A2124" strokeWidth="2.2" strokeLinecap="round"/>
                <path d="M 68,44 H 71" stroke="#1A2124" strokeWidth="2.2" strokeLinecap="round"/>
              </g>
            </svg>
          </div>
          
          <h2 style={{ 
            fontSize: '1.75rem', 
            fontWeight: 800, 
            color: 'hsl(var(--text-primary))', 
            fontFamily: 'var(--font-display)',
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
            margin: '0 0 8px 0'
          }}>
            RLR SYSTEM UP
          </h2>
          
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '2px',
            alignItems: 'center',
            padding: '0 10px',
            marginBottom: '10px'
          }}>
            <p style={{ 
              fontSize: '0.675rem', 
              fontWeight: 700, 
              color: 'hsl(var(--text-secondary))', 
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              lineHeight: '1.4',
              margin: 0
            }}>
              LA TECNOLOGÍA QUE ORGANIZA TU MUNDO,
            </p>
            <p style={{ 
              fontSize: '0.675rem', 
              fontWeight: 700, 
              color: 'hsl(var(--primary))', 
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              lineHeight: '1.4',
              margin: 0
            }}>
              LA GRANDEZA QUE IMPUSA TU ÉXITO
            </p>
          </div>

          {recoveryMode && (
            <p style={{ fontSize: '0.875rem', color: 'hsl(var(--text-secondary))', marginTop: '6px', fontWeight: 600 }}>
              Recuperar Clave
            </p>
          )}
        </div>

        {/* ERROR DISPLAY */}
        {errorMsg && (
          <div style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
            backgroundColor: 'hsl(var(--danger) / 0.08)',
            border: '1px solid hsl(var(--danger) / 0.2)',
            borderRadius: 'var(--radius-md)',
            padding: '12px 14px',
            marginBottom: '20px',
            color: 'hsl(var(--danger))',
            fontSize: '0.85rem'
          }}>
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* RECOVERY MODE SUCCESS */}
        {recoverySuccess && (
          <div style={{
            backgroundColor: 'hsl(var(--success) / 0.08)',
            border: '1px solid hsl(var(--success) / 0.2)',
            borderRadius: 'var(--radius-md)',
            padding: '16px',
            marginBottom: '20px',
            color: 'hsl(var(--success))',
            fontSize: '0.875rem',
            textAlign: 'center'
          }}>
            <span>Hemos enviado las instrucciones a tu correo electrónico. Revisa tu bandeja de entrada.</span>
            <button className="btn btn-secondary" onClick={() => {
              setRecoveryMode(false);
              setRecoverySuccess(false);
              setRecoveryEmail('');
            }} style={{ width: '100%', marginTop: '16px' }}>
              Volver al inicio
            </button>
          </div>
        )}

        {/* LOGIN FORM */}
        {!recoveryMode && (
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="username">Usuario</label>
              <div style={{ position: 'relative' }}>
                <span style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: 'hsl(var(--text-muted))' }}>
                  <User size={18} />
                </span>
                <input
                  id="username"
                  type="text"
                  placeholder="Introduce tu usuario (ej. bancareal)"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="form-input"
                  style={{ paddingLeft: '42px' }}
                  required
                />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <label className="form-label" htmlFor="password" style={{ marginBottom: 0 }}>Contraseña</label>
                <button
                  type="button"
                  onClick={() => setRecoveryMode(true)}
                  style={{ border: 'none', background: 'transparent', fontSize: '0.75rem', color: 'hsl(var(--primary))', cursor: 'pointer', fontWeight: 500 }}
                >
                  ¿La olvidaste?
                </button>
              </div>
              <div style={{ position: 'relative' }}>
                <span style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: 'hsl(var(--text-muted))' }}>
                  <Lock size={18} />
                </span>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="form-input"
                  style={{ paddingLeft: '42px', paddingRight: '42px' }}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute',
                    right: '14px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'transparent',
                    border: 'none',
                    color: 'hsl(var(--text-muted))',
                    cursor: 'pointer'
                  }}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '12px', marginTop: '8px' }} disabled={loading}>
              {loading ? 'Iniciando sesión...' : 'Ingresar al Panel'}
            </button>
          </form>
        )}

        {/* RECOVERY FORM */}
        {recoveryMode && !recoverySuccess && (
          <form onSubmit={handleRecoverySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="recovery-email">Correo Electrónico</label>
              <input
                id="recovery-email"
                type="email"
                placeholder="ejemplo@loteria.com"
                value={recoveryEmail}
                onChange={(e) => setRecoveryEmail(e.target.value)}
                className="form-input"
                required
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '12px' }} disabled={loading}>
              {loading ? 'Procesando...' : 'Enviar enlace de restablecimiento'}
            </button>

            <button type="button" className="btn btn-secondary" onClick={() => setRecoveryMode(false)} style={{ width: '100%' }}>
              Cancelar
            </button>
          </form>
        )}

        {/* FOOTER INFO */}
        <div style={{
          borderTop: '1px solid hsl(var(--border))',
          marginTop: '24px',
          paddingTop: '16px',
          textAlign: 'center',
          fontSize: '0.75rem',
          color: 'hsl(var(--text-muted))',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '12px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <HelpCircle size={14} />
            <span>Soporte: master@multibanca.com</span>
          </div>

          <button
            onClick={() => {
              localStorage.removeItem('lotterynet_session_user');
              localStorage.removeItem('lotterynet_users');
              localStorage.removeItem('lotterynet_tickets');
              localStorage.removeItem('lotterynet_lotteries');
              localStorage.removeItem('lotterynet_audits');
              alert('La caché de sesión y la base de datos simulada local se han borrado y restablecido. La página se recargará automáticamente.');
              window.location.reload();
            }}
            style={{
              padding: '6px 14px',
              backgroundColor: 'hsl(var(--danger) / 0.1)',
              border: '1px solid hsl(var(--danger) / 0.2)',
              borderRadius: 'var(--radius-sm)',
              color: 'hsl(var(--danger))',
              fontSize: '0.75rem',
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              outline: 'none'
            }}
          >
            Borrar Caché y Restablecer Datos
          </button>
        </div>
      </div>
    </div>
  );
};
