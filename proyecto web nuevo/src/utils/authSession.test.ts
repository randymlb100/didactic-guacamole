// @vitest-environment jsdom

import authContextSource from '../context/AuthContext.tsx?raw';
import supabaseClientSource from './supabaseClient.ts?raw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as authSession from './authSession';
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

  it('keeps a still-valid token usable until its actual expiry', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-11T12:00:00Z'));
    saveAuthSession({
      accessToken: 'still-valid-token',
      expiresAt: Math.floor(Date.now() / 1000) + 5,
    });

    expect(getValidAccessToken()).toBe('still-valid-token');
  });

  it('does not persist refresh tokens in web-owned storage', () => {
    saveAuthSession({
      accessToken: 'token-123',
      refreshToken: 'refresh-token-that-must-not-be-reused',
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
    });

    expect(readAuthSession()?.refreshToken).toBeNull();
  });

  it('migrates a legacy refresh token only while the access token has a safe lifetime left', () => {
    const lifecycle = authSession as typeof authSession & {
      canMigrateLegacySession?: (session: {
        accessToken?: string | null;
        refreshToken?: string | null;
        expiresAt?: number | null;
      } | null, nowMs?: number) => boolean;
    };
    const canMigrate = lifecycle.canMigrateLegacySession;
    const nowMs = Date.parse('2026-07-11T12:00:00Z');

    expect(canMigrate).toBeTypeOf('function');
    expect(canMigrate?.({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresAt: Math.floor((nowMs + 120_000) / 1000),
    }, nowMs)).toBe(true);
    expect(canMigrate?.({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresAt: Math.floor((nowMs + 15_000) / 1000),
    }, nowMs)).toBe(false);
  });

  it('lets Supabase persist the browser session instead of manually rehydrating every tab', () => {
    expect(supabaseClientSource).toContain('persistSession: true');
    expect(supabaseClientSource).toContain('storageKey: WEB_AUTH_STORAGE_KEY');
  });

  it('ends only the current web session so a browser logout cannot revoke Android terminals', () => {
    expect(authContextSource).toContain("signOut({ scope: 'local' })");
  });

  it('removes stored sessions', () => {
    saveAuthSession({ accessToken: 'token-123' });
    clearAuthSession();

    expect(readAuthSession()).toBeNull();
  });
});
