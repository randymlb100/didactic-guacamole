// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthSession, getValidAccessToken, readAuthSession, saveAuthSession } from './authSession';

describe('authSession', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useRealTimers();
  });

  it('stores and reads the Supabase access token', () => {
    saveAuthSession({
      accessToken: 'token-123',
      refreshToken: 'refresh-123',
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      authUserId: 'auth-1',
      authEmail: 'user@example.com',
    });

    expect(readAuthSession()?.accessToken).toBe('token-123');
    expect(getValidAccessToken()).toBe('token-123');
  });

  it('clears expired tokens before sensitive commands use them', () => {
    saveAuthSession({
      accessToken: 'expired-token',
      expiresAt: Math.floor(Date.now() / 1000) - 10,
    });

    expect(getValidAccessToken()).toBeNull();
    expect(readAuthSession()).toBeNull();
  });

  it('removes stored sessions', () => {
    saveAuthSession({ accessToken: 'token-123' });
    clearAuthSession();

    expect(readAuthSession()).toBeNull();
  });
});
