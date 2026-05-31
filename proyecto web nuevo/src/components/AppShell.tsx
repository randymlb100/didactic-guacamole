import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutDashboard, 
  Users, 
  TrendingUp, 
  Sliders, 
  History, 
  LogOut, 
  Menu, 
  X, 
  Sun, 
  Moon, 
  User, 
  Bell, 
  Settings,
  Layers,
  Activity,
  DollarSign,
  Trophy
} from 'lucide-react';

interface AppShellProps {
  children: React.ReactNode;
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

export const AppShell: React.FC<AppShellProps> = ({ children, activeTab, setActiveTab }) => {
  const { user, logout, switchUserByRole } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);

  // Initialize theme
  useEffect(() => {
    const savedTheme = localStorage.getItem('lotterynet_theme') as 'light' | 'dark';
    if (savedTheme) {
      setTheme(savedTheme);
      document.documentElement.setAttribute('data-theme', savedTheme);
    } else {
      document.documentElement.setAttribute('data-theme', 'dark');
    }
  }, []);

  const toggleTheme = () => {
    const nextTheme = theme === 'light' ? 'dark' : 'light';
    setTheme(nextTheme);
    document.documentElement.setAttribute('data-theme', nextTheme);
    localStorage.setItem('lotterynet_theme', nextTheme);
  };

  if (!user) return null;

  const role = (user.role || 'UNKNOWN').toUpperCase();

  // Define navigation based on role
  const getNavItems = () => {
    const items = [
      { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
      { id: 'admins', label: 'Bancas y Admins', icon: Layers, roles: ['MASTER'] },
      { id: 'cajeros', label: 'Cajeros & Red', icon: Users, roles: ['ADMIN'] },
      { id: 'supervisores', label: 'Supervisores', icon: Users, roles: ['ADMIN'] },
      { id: 'monitoreo', label: 'Monitoreo Red', icon: Activity, roles: ['ADMIN', 'SUPERVISOR'] },
      { id: 'deportiva', label: 'Venta Deportiva', icon: Trophy, roles: ['ADMIN', 'MASTER'] },
      { id: 'tickets', label: 'Tickets Emitidos', icon: History, roles: ['ADMIN', 'SUPERVISOR'] },
      { id: 'ganadores', label: 'Cobro de Premios', icon: TrendingUp, roles: ['ADMIN'] },
      { id: 'resultados', label: 'Resultados Sorteos', icon: Sliders, roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
      { id: 'limites', label: 'Límites de Juego', icon: Sliders, roles: ['ADMIN'] },
      { id: 'finanzas', label: 'Balances y Recargas', icon: DollarSign, roles: ['ADMIN'] },
      { id: 'cuadre', label: 'Cuadre de Caja', icon: DollarSign, roles: ['ADMIN', 'SUPERVISOR'] },
      { id: 'auditoria', label: 'Auditoría', icon: History, roles: ['MASTER', 'SUPERVISOR'] },
    ];
    return items.filter(item => item.roles.map(r => r.toUpperCase()).includes(role));
  };


  const navItems = getNavItems();

  const handleNavClick = (tabId: string) => {
    setActiveTab(tabId);
    setSidebarOpen(false);
  };

  const activeItem = navItems.find(item => item.id === activeTab) || navItems[0] || { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: 'hsl(var(--background))', position: 'relative', overflow: 'hidden' }}>
      {/* Premium Ambient Radial Glows */}
      <div className="atmospheric-glow-1"></div>
      <div className="atmospheric-glow-2"></div>
      
      {/* SIDEBAR - DESKTOP & MOBILE */}
      <aside className={`glass-panel ${sidebarOpen ? 'sidebar-open' : ''}`} style={{
        position: 'fixed',
        top: 0,
        left: 0,
        height: '100vh',
        width: 'var(--sidebar-width)',
        zIndex: 50,
        borderRadius: 0,
        borderRight: '1px solid hsl(var(--border))',
        borderLeft: 'none',
        borderTop: 'none',
        borderBottom: 'none',
        display: 'flex',
        flexDirection: 'column',
        padding: '24px 16px',
        transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)',
        transition: 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        backgroundColor: 'hsl(var(--surface))'
      }} id="app-sidebar">
        
        {/* Sidebar Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '32px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '42px',
              height: '42px',
              borderRadius: '50%',
              backgroundColor: 'transparent',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 4px 15px hsl(var(--primary) / 0.15)'
            }}>
              <svg width="42" height="42" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
                {/* Circular Teal Background */}
                <circle cx="50" cy="50" r="48" fill="#2A859A" stroke="#ffffff" strokeWidth="2"/>
                
                {/* Face/Head */}
                <circle cx="50" cy="54" r="22" fill="#FFE0B2"/>
                
                {/* Curly Hair (Clustered Circles) */}
                <circle cx="50" cy="32" r="10" fill="#263238"/>
                <circle cx="40" cy="34" r="9" fill="#263238"/>
                <circle cx="60" cy="34" r="9" fill="#263238"/>
                <circle cx="33" cy="40" r="8" fill="#263238"/>
                <circle cx="67" cy="40" r="8" fill="#263238"/>
                <circle cx="31" cy="48" r="7" fill="#263238"/>
                <circle cx="69" cy="48" r="7" fill="#263238"/>
                
                {/* Forehead curls */}
                <circle cx="45" cy="38" r="6" fill="#263238"/>
                <circle cx="55" cy="38" r="6" fill="#263238"/>
                
                {/* Smile */}
                <path d="M44 62C44 65.3 46.7 68 50 68C53.3 68 56 65.3 56 62" stroke="#263238" strokeWidth="2.5" strokeLinecap="round"/>
                
                {/* Glasses */}
                <circle cx="41" cy="52" r="7.5" fill="#B2EBF2" stroke="#263238" strokeWidth="2.5"/>
                <circle cx="41" cy="52" r="2" fill="#263238"/>
                
                <circle cx="59" cy="52" r="7.5" fill="#B2EBF2" stroke="#263238" strokeWidth="2.5"/>
                <circle cx="59" cy="52" r="2" fill="#263238"/>
                
                <path d="M48.5 52H51.5" stroke="#263238" strokeWidth="2.5" strokeLinecap="round"/>
                
                <path d="M33.5 52H30.5" stroke="#263238" strokeWidth="2" strokeLinecap="round"/>
                <path d="M66.5 52H69.5" stroke="#263238" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <div>
              <span style={{ fontSize: '1.15rem', fontWeight: 800, fontFamily: 'var(--font-display)', letterSpacing: '0.02em', display: 'block', textTransform: 'uppercase', color: 'hsl(var(--text-primary))' }}>
                RLR SYSTEM UP
              </span>
              <span style={{ fontSize: '0.625rem', color: 'hsl(var(--primary))', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.03em', display: 'block', lineHeight: '1.2' }}>
                Organiza tu mundo
              </span>
            </div>
          </div>
          <button className="btn-icon mobile-only" onClick={() => setSidebarOpen(false)} style={{ border: 'none', background: 'transparent' }}>
            <X size={20} />
          </button>
        </div>

        {/* Navigation list */}
        <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = item.id === activeTab;
            return (
              <button
                key={item.id}
                onClick={() => handleNavClick(item.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  width: '100%',
                  padding: '12px 14px',
                  borderRadius: 'var(--radius-md)',
                  border: 'none',
                  background: isActive ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                  color: isActive ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                  cursor: 'pointer',
                  fontWeight: isActive ? 600 : 500,
                  fontSize: '0.9rem',
                  textAlign: 'left',
                  transition: 'var(--transition-fast)'
                }}
                className="sidebar-nav-btn"
              >
                <Icon size={18} strokeWidth={isActive ? 2.2 : 1.8} />
                {item.label}
              </button>
            );
          })}
        </nav>

        {/* Sidebar Footer / User Profile Summary */}
        <div style={{
          borderTop: '1px solid hsl(var(--border))',
          paddingTop: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              backgroundColor: 'hsl(var(--primary) / 0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'hsl(var(--primary))',
              fontWeight: 600
            }}>
              {(user?.displayName || user?.user || 'A').charAt(0).toUpperCase()}
            </div>
            <div style={{ flex: 1, overflow: 'hidden' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, display: 'block', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>
                {user?.displayName || user?.user || 'Admin'}
              </span>
              <span className={`badge ${
                role === 'MASTER' ? 'badge-primary' : role === 'ADMIN' ? 'badge-success' : 'badge-warning'
              }`} style={{ fontSize: '0.625rem', marginTop: '2px' }}>
                {role}
              </span>
            </div>
          </div>

          <button onClick={logout} style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            width: '100%',
            padding: '10px 12px',
            borderRadius: 'var(--radius-md)',
            border: '1px solid hsl(var(--border))',
            backgroundColor: 'hsl(var(--surface-hover))',
            color: 'hsl(var(--danger))',
            cursor: 'pointer',
            fontWeight: 500,
            fontSize: '0.85rem',
            justifyContent: 'center',
            transition: 'var(--transition-fast)'
          }}>
            <LogOut size={16} />
            Cerrar Sesión
          </button>
        </div>
      </aside>

      {/* MOBILE SIDEBAR OVERLAY BACKGROUND */}
      {sidebarOpen && (
        <div 
          onClick={() => setSidebarOpen(false)} 
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            zIndex: 40,
            backdropFilter: 'blur(4px)'
          }}
          className="mobile-only"
        />
      )}

      {/* MAIN CONTAINER */}
      <div style={{
        flex: 1,
        marginLeft: 'var(--sidebar-width)',
        display: 'flex',
        flexDirection: 'column',
        minHeight: '100vh',
        width: '100%',
        transition: 'margin 0.3s ease'
      }} className="main-content-layout">
        
        {/* TOPBAR */}
        <header className="glass-panel" style={{
          position: 'sticky',
          top: 0,
          zIndex: 30,
          borderRadius: 0,
          borderTop: 'none',
          borderLeft: 'none',
          borderRight: 'none',
          borderBottom: '1px solid hsl(var(--border))',
          height: '70px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          backgroundColor: 'hsl(var(--surface) / 0.8)'
        }}>
          {/* Topbar Left */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <button className="btn-icon mobile-only" onClick={() => setSidebarOpen(true)}>
              <Menu size={20} />
            </button>
            
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <h1 style={{ fontSize: '1.25rem', color: 'hsl(var(--text-primary))', fontFamily: 'var(--font-display)', fontWeight: 600 }}>
                {activeItem.label}
              </h1>
              {user.banca && (
                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', fontWeight: 500 }}>
                  Red: {user.banca}
                </span>
              )}
            </div>
          </div>

          {/* Topbar Right Quick Actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            
            {/* Test Profile Quick Switcher Dropdown */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginRight: '8px' }}>
              <span className="desktop-only" style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Perfil de Prueba:
              </span>
              <select
                value={role || ''}
                onChange={async (e) => {
                  const targetRole = e.target.value as any;
                  if (targetRole) {
                    await switchUserByRole(targetRole);
                    setActiveTab('dashboard');
                  }
                }}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'hsl(var(--surface-hover))',
                  border: '1px solid hsl(var(--border))',
                  color: 'hsl(var(--text-primary))',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  outline: 'none',
                  boxShadow: 'var(--shadow-sm)',
                  transition: 'all 0.2s ease'
                }}
              >
                <option value="ADMIN">Administrador (Juan Pérez)</option>
                <option value="MASTER">Master (Randy Cordero)</option>
                <option value="SUPERVISOR">Supervisor (Carlos Gómez)</option>
              </select>
            </div>

            {/* Theme Toggle */}
            <button className="btn-icon" onClick={toggleTheme} title="Cambiar tema">
              {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
            </button>

            {/* Notifications */}
            <button className="btn-icon" style={{ position: 'relative' }}>
              <Bell size={18} />
              <span style={{
                position: 'absolute',
                top: '4px',
                right: '4px',
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                backgroundColor: 'hsl(var(--danger))'
              }} />
            </button>

            {/* Profile Dropdown Trigger */}
            <div style={{ position: 'relative' }}>
              <button 
                onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '4px 8px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid hsl(var(--border))',
                  backgroundColor: 'hsl(var(--surface))',
                  cursor: 'pointer'
                }}
              >
                <div style={{
                  width: '26px',
                  height: '26px',
                  borderRadius: '50%',
                  backgroundColor: 'hsl(var(--primary) / 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'hsl(var(--primary))',
                  fontWeight: 600,
                  fontSize: '0.8rem'
                }}>
                  {(user?.user || 'A').charAt(0).toUpperCase()}
                </div>
                <span className="desktop-only" style={{ fontSize: '0.85rem', fontWeight: 500, color: 'hsl(var(--text-primary))' }}>
                  @{user?.user || 'admin'}
                </span>
              </button>

              {/* Profile Dropdown Card */}
              {profileDropdownOpen && (
                <>
                  <div 
                    onClick={() => setProfileDropdownOpen(false)}
                    style={{ position: 'fixed', inset: 0, zIndex: 10 }}
                  />
                  <div className="glass-panel" style={{
                    position: 'absolute',
                    top: '110%',
                    right: 0,
                    width: '200px',
                    padding: '8px',
                    zIndex: 20,
                    boxShadow: 'var(--shadow-lg)'
                  }}>
                    <button style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      width: '100%',
                      padding: '10px 12px',
                      border: 'none',
                      background: 'transparent',
                      textAlign: 'left',
                      fontSize: '0.875rem',
                      color: 'hsl(var(--text-primary))',
                      cursor: 'pointer',
                      borderRadius: 'var(--radius-sm)'
                    }} onClick={() => { setActiveTab('dashboard'); setProfileDropdownOpen(false); }}>
                      <User size={16} />
                      Mi Perfil
                    </button>
                    <button style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      width: '100%',
                      padding: '10px 12px',
                      border: 'none',
                      background: 'transparent',
                      textAlign: 'left',
                      fontSize: '0.875rem',
                      color: 'hsl(var(--text-primary))',
                      cursor: 'pointer',
                      borderRadius: 'var(--radius-sm)'
                    }} onClick={() => { setProfileDropdownOpen(false); }}>
                      <Settings size={16} />
                      Configuración
                    </button>
                    <hr style={{ border: 'none', borderTop: '1px solid hsl(var(--border))', margin: '4px 0' }} />
                    <button style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      width: '100%',
                      padding: '10px 12px',
                      border: 'none',
                      background: 'transparent',
                      textAlign: 'left',
                      fontSize: '0.875rem',
                      color: 'hsl(var(--danger))',
                      cursor: 'pointer',
                      borderRadius: 'var(--radius-sm)'
                    }} onClick={() => { logout(); setProfileDropdownOpen(false); }}>
                      <LogOut size={16} />
                      Cerrar Sesión
                    </button>
                  </div>
                </>
              )}
            </div>

          </div>
        </header>

        {/* MAIN BODY SCROLLABLE */}
        <main className="fade-in" style={{
          flex: 1,
          padding: '24px',
          overflowY: 'auto',
          maxWidth: '1600px',
          width: '100%',
          margin: '0 auto'
        }}>
          {children}
        </main>
      </div>

      {/* Dynamic Responsive Styles Injection */}
      <style>{`
        @media (max-width: 1024px) {
          .main-content-layout {
            margin-left: 0 !important;
          }
          #app-sidebar:not(.sidebar-open) {
            transform: translateX(-100%) !important;
          }
          #app-sidebar.sidebar-open {
            transform: translateX(0) !important;
          }
          .desktop-only {
            display: none !important;
          }
        }
        @media (min-width: 1025px) {
          .mobile-only {
            display: none !important;
          }
          #app-sidebar {
            transform: translateX(0) !important;
          }
        }
      `}</style>
    </div>
  );
};
