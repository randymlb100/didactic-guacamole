// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  invoke: vi.fn(),
  getValidAccessToken: vi.fn(),
}));

vi.mock('../supabaseClient', () => ({
  isSupabaseConfigured: true,
  supabase: {
    functions: {
      invoke: mocks.invoke,
    },
  },
}));

vi.mock('../authSession', () => ({
  getValidAccessToken: mocks.getValidAccessToken,
}));

vi.mock('../userMapping', () => ({
  mapRemoteUserToAccount: (user: any) => user,
}));

vi.mock('./config', () => ({
  initMockDatabase: vi.fn(),
}));

import { clearUsersFetchCache, fetchUsers } from './queries';

describe('Supabase queries noise control', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    mocks.getValidAccessToken.mockReturnValue('token-a');
    clearUsersFetchCache();
  });

  it('dedupes repeated fetchUsers calls while the first request is in flight', async () => {
    mocks.invoke.mockResolvedValue({
      data: {
        payload: {
          users: [
            { id: 'U-1', user: 'master', role: 'MASTER' },
            { id: 'U-2', user: 'admin', role: 'ADMIN' },
          ],
        },
      },
      error: null,
    });

    const [first, second] = await Promise.all([fetchUsers(), fetchUsers()]);

    expect(first).toHaveLength(2);
    expect(second).toHaveLength(2);
    expect(mocks.invoke).toHaveBeenCalledTimes(1);
  });

  it('reuses the short cache for consecutive fetchUsers reads', async () => {
    mocks.invoke.mockResolvedValue({
      data: {
        payload: {
          users: [
            { id: 'U-1', user: 'master', role: 'MASTER' },
          ],
        },
      },
      error: null,
    });

    const first = await fetchUsers();
    const second = await fetchUsers();

    expect(first).toEqual([{ id: 'U-1', user: 'master', role: 'MASTER' }]);
    expect(second).toEqual([{ id: 'U-1', user: 'master', role: 'MASTER' }]);
    expect(mocks.invoke).toHaveBeenCalledTimes(1);
  });
});
