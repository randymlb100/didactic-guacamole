import React, { useState, Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
const Login = React.lazy(() => import('./views/auth/Login').then(m => ({ default: m.Login })));
const Dashboard = React.lazy(() => import('./views/Dashboard').then(m => ({ default: m.Dashboard })));
import { AppShell } from './components/AppShell';
import { getSafeAdminTab } from './utils/navigationPermissions';
import { clearAuthSession } from './utils/authSession';

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
