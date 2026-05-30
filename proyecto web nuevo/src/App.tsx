import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Login } from './views/auth/Login';
import { Dashboard } from './views/Dashboard';
import { AppShell } from './components/AppShell';

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
    return <Login onSuccess={() => setActiveTab('dashboard')} />;
  }

  // Set default tabs based on role if the current tab is invalid
  const getSafeActiveTab = (): string => {
    const roleUpper = (user?.role || 'UNKNOWN').toUpperCase();
    if (roleUpper === 'MASTER' && ['cajeros', 'supervisores', 'monitoreo', 'finanzas'].includes(activeTab)) {
      return 'dashboard';
    }
    if (roleUpper === 'ADMIN' && ['admins', 'monitoreo', 'auditoria'].includes(activeTab)) {
      return 'dashboard';
    }
    if (roleUpper === 'SUPERVISOR' && ['admins', 'cajeros', 'supervisores', 'limites', 'finanzas'].includes(activeTab)) {
      return 'dashboard';
    }
    return activeTab;
  };

  const safeTab = getSafeActiveTab();

  return (
    <AppShell activeTab={safeTab} setActiveTab={setActiveTab}>
      <Dashboard activeTab={safeTab} />
    </AppShell>
  );
};

const App: React.FC = () => {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
};

export default App;
