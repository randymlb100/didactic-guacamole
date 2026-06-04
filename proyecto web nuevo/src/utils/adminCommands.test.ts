// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAuthSession, saveAuthSession } from './authSession';

const invokeMock = vi.fn();

vi.mock('./supabaseClient', () => ({
  supabase: {
    functions: {
      invoke: invokeMock,
    },
  },
}));

describe('runAdminUserCommand', () => {
  beforeEach(() => {
    localStorage.clear();
    invokeMock.mockReset();
  });

  it('requires a Supabase access token before invoking sensitive commands', async () => {
    const { runAdminUserCommand } = await import('./adminCommands');

    await expect(
      runAdminUserCommand({ id: 'master', user: 'master', role: 'MASTER' }, 'toggle_bank', 'admin-1')
    ).rejects.toThrow('Sesion Supabase requerida');
    expect(invokeMock).not.toHaveBeenCalled();
  });

  it('sends the Authorization header with the command', async () => {
    const { runAdminUserCommand } = await import('./adminCommands');
    saveAuthSession({ accessToken: 'jwt-123', expiresAt: Math.floor(Date.now() / 1000) + 3600 });
    invokeMock.mockResolvedValue({
      data: { ok: true, message: 'ok', data: { active: false }, auditId: 'AUD-1' },
      error: null,
    });

    await runAdminUserCommand({ id: 'master', user: 'master', role: 'MASTER' }, 'toggle_bank', 'admin-1');

    expect(invokeMock).toHaveBeenCalledWith('admin-user-command', expect.objectContaining({
      headers: { Authorization: 'Bearer jwt-123' },
    }));
  });

  it('does not reuse tokens after clearing the session', async () => {
    const { runAdminUserCommand } = await import('./adminCommands');
    saveAuthSession({ accessToken: 'jwt-123' });
    clearAuthSession();

    await expect(
      runAdminUserCommand({ id: 'master', user: 'master', role: 'MASTER' }, 'toggle_bank', 'admin-1')
    ).rejects.toThrow('Sesion Supabase requerida');
  });
});
