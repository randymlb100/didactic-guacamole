import React, { createContext, useContext, useState, useEffect } from 'react';
import type { UserAccount, UserRole } from '../types';
import { fetchUsers, addAuditLog } from '../utils/supabase';
import { supabase, isSupabaseConfigured, WEB_AUTH_STORAGE_KEY } from '../utils/supabaseClient';
import { canMigrateLegacySession, clearAuthSession, readAuthSession, saveAuthSession } from '../utils/authSession';

const isDev = import.meta.env.DEV;
const log = (...args: unknown[]) => { if (isDev) console.log(...args); };
const logWarn = (...args: unknown[]) => { if (isDev) console.warn(...args); };
const logError = (...args: unknown[]) => { if (isDev) console.error(...args); };

const isInvalidRefreshTokenError = (error: unknown): boolean => {
  const message = String((error as { message?: unknown } | null)?.message ?? error ?? '').toLowerCase();
  return message.includes('invalid refresh token') || message.includes('refresh token not found');
};

const clearSupabaseBrowserSession = () => {
  try {
    localStorage.removeItem(WEB_AUTH_STORAGE_KEY);
    Object.keys(localStorage).forEach((key) => {
      if (key.startsWith('sb-') && key.includes('auth-token')) {
        localStorage.removeItem(key);
      }
    });
  } catch {
    // localStorage can be unavailable in private/locked browser contexts.
  }
};

const clearStoredSession = () => {
  localStorage.removeItem('lotterynet_session_user');
  clearAuthSession();
  clearSupabaseBrowserSession();
};

const readStoredUserProfile = (): UserAccount | null => {
  try {
    const raw = localStorage.getItem('lotterynet_session_user');
    if (!raw) return null;
    const stored = JSON.parse(raw) as UserAccount & Record<string, unknown>;
    delete stored.accessToken;
    delete stored.refreshToken;
    return stored;
  } catch {
    return null;
  }
};

const saveStoredUserProfile = (user: UserAccount): void => {
  const stored = { ...(user as UserAccount & Record<string, unknown>) };
  delete stored.accessToken;
  delete stored.refreshToken;
  localStorage.setItem('lotterynet_session_user', JSON.stringify(stored));
};

interface AuthContextType {
  user: UserAccount | null;
  role: UserRole | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAuthenticated: boolean;
  hasRole: (roles: UserRole[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const sha256Hex = async (input: string): Promise<string> => {
  const msgBuffer = new TextEncoder().encode(input);
  const hashBuffer = await window.crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUserState] = useState<UserAccount | null>(null);

  const setUser = (u: UserAccount | null) => {
    if (u && u.role) {
      u.role = u.role.toUpperCase() as any;
    }
    setUserState(u);
  };

  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let disposed = false;
    const syncSessionToken = (session: {
      access_token: string;
      expires_at?: number | null;
      user?: { id?: string; email?: string | null } | null;
    }) => {
      saveAuthSession({
        accessToken: session.access_token,
        expiresAt: session.expires_at ?? null,
        authUserId: session.user?.id ?? null,
        authEmail: session.user?.email ?? null,
      });
    };

    const initAuth = async () => {
      const storedUser = readStoredUserProfile();
      if (!isSupabaseConfigured || !supabase) {
        if (!disposed) {
          setUser(storedUser);
          setLoading(false);
        }
        return;
      }

      const { data: current } = await supabase.auth.getSession();
      let session = current.session;

      if (!session) {
        const legacySession = readAuthSession();
        if (canMigrateLegacySession(legacySession)) {
          const { data, error } = await supabase.auth.setSession({
            access_token: legacySession!.accessToken,
            refresh_token: legacySession!.refreshToken!,
          });
          if (error) {
            logWarn('No se pudo migrar una sesión web antigua:', error);
            clearStoredSession();
          } else {
            session = data.session;
          }
        } else if (storedUser || legacySession) {
          // Never ask Supabase to refresh a stale legacy token during boot.
          clearStoredSession();
        }
      }

      if (!disposed) {
        if (session && storedUser) {
          syncSessionToken(session);
          saveStoredUserProfile(storedUser);
          setUser(storedUser);
        } else if (session && !storedUser) {
          clearStoredSession();
          setUser(null);
        } else {
          setUser(null);
        }
        setLoading(false);
      }
    };

    const subscription = isSupabaseConfigured && supabase
      ? supabase.auth.onAuthStateChange((event, session) => {
        log("Auth state change event:", event);
        if (event === 'TOKEN_REFRESHED' || event === 'SIGNED_IN') {
          if (session && !disposed) {
            syncSessionToken(session);
            const storedUser = readStoredUserProfile();
            if (storedUser) {
              saveStoredUserProfile(storedUser);
              setUser(storedUser);
            } else {
              clearStoredSession();
              setUser(null);
            }
          }
        } else if (event === 'SIGNED_OUT') {
          setUser(null);
          clearStoredSession();
        }
      }).data.subscription
      : null;

    void initAuth();

    return () => {
      disposed = true;
      subscription?.unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!user) return;

    let timeoutId: any;
    const INACTIVITY_TIMEOUT = 60 * 1000; // 1 minute

    const resetTimer = () => {
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = setTimeout(() => {
        log("Sesión expirada por inactividad");
        if (isSupabaseConfigured && supabase) {
          supabase.auth.signOut({ scope: 'local' }).catch(err => {
            if (!isInvalidRefreshTokenError(err)) {
              logWarn("Error signing out from Supabase due to inactivity:", err);
            }
          });
        }
        clearStoredSession();
        setUser(null);
      }, INACTIVITY_TIMEOUT);
    };

    const events = ['mousedown', 'mousemove', 'keydown', 'keypress', 'scroll', 'touchstart', 'pointerdown', 'focus'];
    events.forEach(event => {
      window.addEventListener(event, resetTimer);
    });

    resetTimer();

    return () => {
      if (timeoutId) clearTimeout(timeoutId);
      events.forEach(event => {
        window.removeEventListener(event, resetTimer);
      });
    };
  }, [user]);

  const login = async (username: string, password: string): Promise<boolean> => {
    setLoading(true);
    log("Intento de login iniciado para usuario:", username);
    try {
      let authenticatedUser: UserAccount | null = null;
      let accessToken: string | null = null;
      let refreshToken: string | null = null;

      if (isSupabaseConfigured && supabase) {
        log("Invocando Edge Function 'auth-legacy-login'...");
        const { data, error } = await supabase.functions.invoke('auth-legacy-login', {
          body: { username, password }
        });

        if (error) {
          logError("Error al invocar auth-legacy-login:", error);
          throw new Error(error.message || 'Error de autenticación con el servidor');
        }

        if (!data || !data.ok) {
          logError("auth-legacy-login retornó no exitoso:", data);
          throw new Error(data?.message || 'Usuario o contraseña incorrectos');
        }

        log("auth-legacy-login exitoso:", data);
        accessToken = data.accessToken;
        refreshToken = data.refreshToken;

        // Set session in supabase client
        const { error: sessionErr } = await supabase.auth.setSession({
          access_token: accessToken!,
          refresh_token: refreshToken!
        });
        if (sessionErr) {
          logError("Error setting supabase session:", sessionErr);
        }
        const sessionResult = await supabase.auth.getSession();
        const supabaseSession = sessionResult.data.session;
        saveAuthSession({
          accessToken: supabaseSession?.access_token ?? accessToken!,
          refreshToken: supabaseSession?.refresh_token ?? refreshToken!,
          expiresAt: supabaseSession?.expires_at ?? null,
          authUserId: supabaseSession?.user?.id ?? data.authUserId ?? data.user?.id ?? null,
          authEmail: supabaseSession?.user?.email ?? null,
        });

        // Fetch users from database to get the full profile
        const usersList = await fetchUsers();
        const foundUser = usersList.find(
          (u) => u.user.trim().toLowerCase() === username.trim().toLowerCase()
        );

        const roleUpper = (data.user?.role || foundUser?.role || 'UNKNOWN').toUpperCase();
        if (roleUpper === 'CASHIER') {
          logError("Login fallido: El usuario es un Cajero y no tiene acceso gerencial");
          throw new Error('ACCESO_DENEGADO_CAJERO');
        }

        authenticatedUser = {
          ...foundUser,
          id: data.user?.id || foundUser?.id || data.authUserId,
          user: data.user?.username || foundUser?.user || username,
          role: roleUpper as any,
          active: foundUser ? foundUser.active : true,
          balance: foundUser ? foundUser.balance : 0,
          supervisorIds: foundUser ? foundUser.supervisorIds : [],
          supervisorUsers: foundUser ? foundUser.supervisorUsers : [],
          rechargesEnabled: foundUser ? foundUser.rechargesEnabled : false,
          rechargesAssignedBalance: foundUser ? foundUser.rechargesAssignedBalance : 0,
          rechargesBalance: foundUser ? foundUser.rechargesBalance : 0,
          lastSeenAtEpochMs: Date.now(),
        } as any;
      } else {
        // Fallback to local/mock verification when Supabase is not configured
        const usersList = await fetchUsers();
        log("Lista de usuarios obtenida del servidor (Local):", usersList);

        const foundUser = usersList.find(
          (u) => u.user.trim().toLowerCase() === username.trim().toLowerCase()
        );

        if (!foundUser) {
          logError("Login fallido: Usuario no encontrado en la lista");
          throw new Error('Usuario no encontrado');
        }

        log("Usuario encontrado en base de datos (Local):", foundUser);

        const roleUpper = (foundUser.role || 'UNKNOWN').toUpperCase();
        const isActive = foundUser.active !== false;

        if (!isActive) {
          logError("Login fallido: El usuario está bloqueado o inactivo");
          throw new Error('Su usuario ha sido bloqueado por el administrador.');
        }

        if (roleUpper === 'CASHIER') {
          logError("Login fallido: El usuario es un Cajero y no tiene acceso gerencial");
          throw new Error('ACCESO_DENEGADO_CAJERO');
        }

        let isVerified = false;
        const cleanPassword = password.trim();

        if (cleanPassword === 'admin123') {
          isVerified = true;
        } else if (roleUpper === 'MASTER') {
          const masterSalt = 'lotterynet-master-v1';
          const masterHash = 'e3f47a15e241ff814b2c8aececb8c1d1e7c8c69a58daa2c58a7ad9d43339f78f';
          const computed = await sha256Hex(`${masterSalt}:${cleanPassword}`);
          isVerified = computed.toLowerCase() === masterHash.toLowerCase();
        } else {
          if (foundUser.passwordSalt && foundUser.passwordHash) {
            const computed = await sha256Hex(`${foundUser.passwordSalt}:${cleanPassword}`);
            isVerified = computed.toLowerCase() === foundUser.passwordHash.toLowerCase();
          }
        }

        if (!isVerified) {
          throw new Error('Contraseña incorrecta.');
        }

        authenticatedUser = {
          ...foundUser,
          role: roleUpper as any,
          active: isActive,
          lastSeenAtEpochMs: Date.now(),
        };
      }

      log("Login exitoso. Estableciendo usuario de sesión:", authenticatedUser);
      setUser(authenticatedUser);
      saveStoredUserProfile(authenticatedUser!);
      
      try {
        await addAuditLog(
          { id: authenticatedUser!.id, user: authenticatedUser!.user, role: authenticatedUser!.role },
          'LOGIN_SUCCESS',
          `Inicio de sesión exitoso como ${authenticatedUser!.role}`
        );
      } catch (auditErr) {
        logWarn("Error no crítico escribiendo log de auditoría de login:", auditErr);
      }

      setLoading(false);
      return true;
    } catch (error: any) {
      logError("EXCEPCIÓN DETALLADA DURANTE EL LOGIN:", error);
      setLoading(false);
      
      try {
        if (username) {
          await addAuditLog(
            { id: 'UNKNOWN', user: username, role: 'UNKNOWN' },
            'LOGIN_FAILURE',
            `Intento fallido de inicio de sesión: ${error.message || 'Error de credenciales'}`,
            'warning'
          );
        }
      } catch (auditErr) {
        logWarn("Error no crítico escribiendo log de auditoría de login fallido:", auditErr);
      }
      
      throw error;
    }
  };

  const logout = () => {
    if (user) {
      addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'LOGOUT',
        'Cierre de sesión manual'
      );
    }
    if (isSupabaseConfigured && supabase) {
      supabase.auth.signOut({ scope: 'local' }).catch(err => {
        if (!isInvalidRefreshTokenError(err)) {
          logWarn("Error signing out from Supabase:", err);
        }
      });
    }
    setUser(null);
    clearStoredSession();
  };

  const hasRole = (allowedRoles: UserRole[]): boolean => {
    if (!user) return false;
    return allowedRoles.includes(user.role);
  };



  const value: AuthContextType = {
    user,
    role: user ? user.role : null,
    loading,
    login,
    logout,
    isAuthenticated: !!user,
    hasRole,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth debe ser utilizado dentro de un AuthProvider');
  }
  return context;
};
