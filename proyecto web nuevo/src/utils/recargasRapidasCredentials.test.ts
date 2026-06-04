import { describe, expect, it } from 'vitest';
import { resolveRecargasRapidasCredentials, type RecargasRapidasCredentialConfig } from './recargasRapidasCredentials';

const config: RecargasRapidasCredentialConfig = {
  default: { username: 'default_rr', password: 'default_pass', updatedAt: 1710000000000 },
  byAdmin: {
    'adm-1': { username: 'admin_rr', password: 'admin_pass', updatedAt: 1710000001000 },
  },
  byUser: {
    'caj-1': { username: 'cashier_rr', password: 'cashier_pass', updatedAt: 1710000002000 },
  },
};

describe('recargasRapidasCredentials', () => {
  it('uses individual user credentials before bank/admin default', () => {
    expect(resolveRecargasRapidasCredentials(config, { id: 'caj-1', user: 'caj01', adminId: 'adm-1', adminUser: 'admin1' })?.username).toBe('cashier_rr');
  });

  it('uses admin credentials when user has no override', () => {
    expect(resolveRecargasRapidasCredentials(config, { id: 'caj-2', user: 'caj02', adminId: 'adm-1', adminUser: 'admin1' })?.username).toBe('admin_rr');
  });

  it('uses master default when neither user nor admin has override', () => {
    expect(resolveRecargasRapidasCredentials(config, { id: 'caj-3', user: 'caj03', adminId: 'adm-2', adminUser: 'admin2' })?.username).toBe('default_rr');
  });
});
