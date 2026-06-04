export interface LotterynetAuthSession {
  accessToken: string;
  refreshToken?: string | null;
  expiresAt?: number | null;
  authUserId?: string | null;
  authEmail?: string | null;
}

export const AUTH_SESSION_STORAGE_KEY = 'lotterynet_supabase_auth';

export function readAuthSession(): LotterynetAuthSession | null {
  try {
    const raw = localStorage.getItem(AUTH_SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<LotterynetAuthSession>;
    if (!parsed.accessToken) return null;
    return {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken || null,
      expiresAt: parsed.expiresAt ?? null,
      authUserId: parsed.authUserId || null,
      authEmail: parsed.authEmail || null,
    };
  } catch {
    return null;
  }
}

export function saveAuthSession(session: LotterynetAuthSession): void {
  localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
}

export function clearAuthSession(): void {
  localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
}

export function getValidAccessToken(): string | null {
  const session = readAuthSession();
  if (!session?.accessToken) return null;
  if (session.expiresAt && Date.now() >= (session.expiresAt * 1000) - 30_000) {
    clearAuthSession();
    return null;
  }
  return session.accessToken;
}
