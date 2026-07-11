import React, { useState, Component, useEffect } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
const Login = React.lazy(() => import('./views/auth/Login').then(m => ({ default: m.Login })));
const Dashboard = React.lazy(() => import('./views/Dashboard').then(m => ({ default: m.Dashboard })));
import { AppShell } from './components/AppShell';
import { getSafeAdminTab } from './utils/navigationPermissions';
import { clearAuthSession } from './utils/authSession';

import { playTapSound, unlockUiAudio } from './utils/audio';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false
  };

  public static getDerivedStateFromError(_: Error): State {
    return { hasError: true };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught rendering error caught by Boundary:", error, errorInfo);
    // Securely clear cached user session to break boot loops/blank screens
    localStorage.removeItem('lotterynet_session_user');
    clearAuthSession();
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          backgroundColor: 'hsl(var(--background))',
          color: 'hsl(var(--text-primary))',
          padding: '20px',
          textAlign: 'center',
          fontFamily: 'var(--font-sans, sans-serif)'
        }}>
          <div className="glass-panel" style={{ maxWidth: '440px', padding: '36px', border: '1px solid hsl(var(--border))' }}>
            <h3 style={{ marginBottom: '12px', fontSize: '1.25rem', fontWeight: 700 }}>Sesión restablecida</h3>
            <p style={{ fontSize: '0.875rem', color: 'hsl(var(--text-secondary))', lineHeight: '1.6', marginBottom: '24px' }}>
              Se detectó un error de carga de datos en caché. Hemos limpiado tu sesión de forma segura para resolver la incidencia.
            </p>
            <button 
              className="btn btn-primary" 
              onClick={() => {
                localStorage.clear(); // Clear all potential corrupt keys
                window.location.href = '/';
              }} 
              style={{ width: '100%', padding: '12px' }}
            >
              Reingresar al Panel
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

const MainApp: React.FC = () => {
  const { isAuthenticated, user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [isOffline, setIsOffline] = useState(!navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    const handleGlobalClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      // Capture clicks on buttons, tabs, links, sidebars, ticket lists, etc.
      const isInteractive = target.closest(
        'button, a, [role="button"], input, select, textarea, .sidebar-nav-btn, .custom-toggle, .tab-btn, .clickable, .ticket-row, [onClick]'
      );
      if (isInteractive) {
        playTapSound();
      }
    };

    const handleFirstUserGesture = () => unlockUiAudio();

    window.addEventListener('pointerdown', handleFirstUserGesture, { once: true, passive: true });
    window.addEventListener('keydown', handleFirstUserGesture, { once: true, passive: true });
    window.addEventListener('click', handleGlobalClick, { capture: true, passive: true });
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('pointerdown', handleFirstUserGesture);
      window.removeEventListener('keydown', handleFirstUserGesture);
      window.removeEventListener('click', handleGlobalClick, { capture: true });
    };
  }, []);

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        backgroundColor: 'hsl(var(--background))',
        color: 'hsl(var(--text-secondary))'
      }}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
          <div className="shimmer" style={{ width: '48px', height: '48px', borderRadius: '50%' }} />
          <span>Cargando sistema administrativo...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <React.Suspense fallback={
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          width: '100%',
          backgroundColor: 'hsl(var(--background))',
          color: 'hsl(var(--text-secondary))',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <div className="atmospheric-glow-1" />
          <div className="atmospheric-glow-2" />
          <div className="glass-panel-premium" style={{ width: '420px', padding: '40px 36px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '24px' }}>
            <div className="shimmer" style={{ width: '72px', height: '72px', borderRadius: '50%' }} />
            <div className="shimmer" style={{ width: '180px', height: '24px' }} />
            <div className="shimmer" style={{ width: '220px', height: '14px' }} />
            <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '12px' }}>
              <div className="shimmer" style={{ width: '100%', height: '40px' }} />
              <div className="shimmer" style={{ width: '100%', height: '40px' }} />
              <div className="shimmer" style={{ width: '100%', height: '48px', marginTop: '12px' }} />
            </div>
          </div>
        </div>
      }>
        <Login onSuccess={() => setActiveTab('dashboard')} />
      </React.Suspense>
    );
  }

  // Set default tabs based on role if the current tab is invalid
  const getSafeActiveTab = (): string => {
    return getSafeAdminTab(user?.role, activeTab);
  };

  const safeTab = getSafeActiveTab();

  return (
    <AppShell activeTab={safeTab} setActiveTab={setActiveTab}>
      {isOffline && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          height: '4px',
          backgroundColor: 'hsl(var(--danger))',
          boxShadow: '0 0 10px hsl(var(--danger))',
          zIndex: 9999,
          animation: 'pulse 1.5s infinite'
        }} />
      )}
      {isOffline && (
        <div className="glass-panel-premium fade-in" style={{
          position: 'fixed',
          top: '12px',
          left: '50%',
          transform: 'translateX(-50%)',
          padding: '8px 16px',
          borderRadius: 'var(--radius-md)',
          border: '1px solid hsl(var(--danger) / 0.3)',
          backgroundColor: 'rgba(239, 68, 68, 0.15)',
          color: '#ffffff',
          fontWeight: 600,
          fontSize: '0.8rem',
          zIndex: 9998,
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          backdropFilter: 'blur(12px)',
          boxShadow: '0 8px 32px rgba(239, 68, 68, 0.15)'
        }}>
          <span style={{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'hsl(var(--danger))', animation: 'pulse 1.5s infinite' }} />
          Sin conexión a Internet — Operando con datos locales
        </div>
      )}
      <React.Suspense fallback={
        <div style={{
          padding: '24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px',
          width: '100%',
          height: '100%',
          overflow: 'hidden'
        }}>
          {/* Header Skeleton */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div className="shimmer" style={{ width: '240px', height: '32px' }} />
            <div className="shimmer" style={{ width: '120px', height: '40px' }} />
          </div>
          
          {/* Bento Grid Metrics Skeleton */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '24px'
          }}>
            <div className="glass-panel-premium shimmer" style={{ height: '120px' }} />
            <div className="glass-panel-premium shimmer" style={{ height: '120px' }} />
            <div className="glass-panel-premium shimmer" style={{ height: '120px' }} />
            <div className="glass-panel-premium shimmer" style={{ height: '120px' }} />
          </div>

          {/* Body Skeleton */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: '2fr 1fr',
            gap: '24px',
            marginTop: '12px'
          }}>
            <div className="glass-panel-premium shimmer" style={{ height: '400px' }} />
            <div className="glass-panel-premium shimmer" style={{ height: '400px' }} />
          </div>
        </div>
      }>
        <Dashboard activeTab={safeTab} setActiveTab={setActiveTab} />
      </React.Suspense>
    </AppShell>
  );
};

const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <MainApp />
      </AuthProvider>
    </ErrorBoundary>
  );
};

export default App;
