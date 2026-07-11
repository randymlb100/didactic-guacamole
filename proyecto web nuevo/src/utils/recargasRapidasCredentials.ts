export interface RecargasRapidasCredentialEntry {
  username: string;
  password?: string;
  updatedAt: number;
  updatedBy?: string;
}

export interface RecargasRapidasCredentialConfig {
  default?: RecargasRapidasCredentialEntry;
  byAdmin: Record<string, RecargasRapidasCredentialEntry>;
  byUser: Record<string, RecargasRapidasCredentialEntry>;
}

export interface RecargasRapidasCredentialActor {
  id?: string | null;
  user?: string | null;
  adminId?: string | null;
  adminUser?: string | null;
}

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

const keys = (...values: Array<string | null | undefined>): string[] => values.map(normalizeKey).filter(Boolean);

export const emptyRecargasRapidasCredentialConfig: RecargasRapidasCredentialConfig = {
  byAdmin: {},
  byUser: {},
};

export const resolveRecargasRapidasCredentials = (
  config: RecargasRapidasCredentialConfig,
  actor: RecargasRapidasCredentialActor,
): RecargasRapidasCredentialEntry | null => {
  for (const key of keys(actor.id, actor.user)) {
    if (config.byUser[key]) return config.byUser[key];
  }

  for (const key of keys(actor.adminId, actor.adminUser)) {
    if (config.byAdmin[key]) return config.byAdmin[key];
  }

  return config.default || null;
};
