# Web Android Admin Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the web admin panel and Android app operate from the same administrative contract for MASTER, ADMIN, and SUPERVISOR features.

**Architecture:** Web must stop being a separate admin surface with mixed storage paths. The shared source of truth should be Supabase Edge Functions plus `lotterynet_master_state`, with web UI and Android both reading/writing the same keys. Sensitive user/account actions should move behind Edge Functions instead of direct table writes.

**Tech Stack:** React 19, TypeScript, Vite, Supabase JS, Supabase Edge Functions, Android Kotlin/Compose, `lotterynet_master_state`, existing Android repositories and sync coordinators.

---

## Source Findings

Web repo:

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\docs\admin-ux-implementation-rules.md`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\components\AppShell.tsx`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\supabase.ts`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\types.ts`

Android/Supabase repo:

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\master\MasterDashboardActivity.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\master\MasterCloudSyncCoordinator.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\master\MasterBankManager.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalUsersRepository.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalCashierSalesLimitRepository.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalCashierPrizePayoutRepository.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalAdminLotteryConfigRepository.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalMasterConfigRepository.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\recharge\RecargasActivity.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\update\UpdateViewModel.kt`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\get-master-config\index.ts`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\update-master-config\index.ts`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\admin-cashier-limits\index.ts`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\create-ticket-v2\index.ts`
- `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\create-sports-ticket\index.ts`

## Phases

1. **Data contract first:** Make web read/write the same keys Android uses.
2. **MASTER visibility:** Expose existing MASTER-capable sections in web navigation.
3. **ADMIN parity:** Add commission, supervisor commission, recharge transaction limits, and shared limit persistence.
4. **MASTER parity:** Add master operations for banks, server/sync, recharge access, sportsbook, alerts, and OTA status.
5. **Security:** Move sensitive writes to Edge Functions.
6. **Verification:** Browser, Supabase contract, and Android operational checks.

## UX Contract For Every Task

Before changing visible UI, read `docs/admin-ux-implementation-rules.md`.

Every editable operational control must include:

- visible label,
- helper text explaining operational impact,
- validation near the field,
- nearby save/sync action,
- loading state on save,
- success/error feedback in the same block,
- role-aware visibility,
- reduced-motion-safe animation.

Do not add passive information blocks without an action when the user is expected to operate the setting.

---

## Task 1: Add Web Test Harness For Shared Contracts

**Files:**

- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\package.json`
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.test.ts`
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\userMapping.test.ts`

- [ ] **Step 1: Add test dependencies and scripts**

Modify `package.json`:

```json
{
  "scripts": {
    "dev": "vite --host",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "devDependencies": {
    "vitest": "^4.0.15",
    "jsdom": "^27.3.0"
  }
}
```

Run:

```powershell
npm install
```

Expected: `package-lock.json` updates and install completes without dependency errors.

- [ ] **Step 2: Add failing tests for master config keys**

Create `src\utils\masterConfig.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { buildMasterConfigKey } from './masterConfig';

describe('buildMasterConfigKey', () => {
  it('builds Android-compatible admin config keys', () => {
    expect(buildMasterConfigKey('cashier_limits', 'admin-1')).toBe('cashier_limits:admin-1');
    expect(buildMasterConfigKey('cashier_prize_payouts', 'bank.user')).toBe('cashier_prize_payouts:bank.user');
    expect(buildMasterConfigKey('system_modes', 'adm_01')).toBe('system_modes:adm_01');
    expect(buildMasterConfigKey('manual_disabled_lotteries', 'adm:01')).toBe('manual_disabled_lotteries:adm:01');
    expect(buildMasterConfigKey('recharge_limits', 'adm-01')).toBe('recharge_limits:adm-01');
    expect(buildMasterConfigKey('admin_operational_limits', 'adm-01')).toBe('admin_operational_limits:adm-01');
  });

  it('builds Android-compatible sportsbook keys', () => {
    expect(buildMasterConfigKey('sportsbook_global')).toBe('sportsbook:global');
    expect(buildMasterConfigKey('sportsbook_admin', 'admin-1')).toBe('sportsbook:admin:admin-1');
    expect(buildMasterConfigKey('sportsbook_actor', 'cashier-1')).toBe('sportsbook:actor:cashier-1');
  });
});
```

Run:

```powershell
npm test -- src/utils/masterConfig.test.ts
```

Expected: FAIL because `src/utils/masterConfig.ts` does not exist yet.

- [ ] **Step 3: Add failing tests for user mapping**

Create `src\utils\userMapping.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { mapRemoteUserToAccount, mapAccountToRemoteUser } from './userMapping';

describe('user mapping', () => {
  it('keeps cashier commission as web percent while preserving Android decimal storage', () => {
    const account = mapRemoteUserToAccount({
      id: 'c1',
      user: 'cajero1',
      pass: '1234',
      role: 'CASHIER',
      commissionRate: 0.075,
      recargaTx: 500,
    });

    expect(account.commissionRate).toBe(7.5);
    expect(account.recargaTxLimit).toBe(500);

    const remote = mapAccountToRemoteUser(account);
    expect(remote.commissionRate).toBe(0.075);
    expect(remote.recargaTx).toBe(500);
  });
});
```

Run:

```powershell
npm test -- src/utils/userMapping.test.ts
```

Expected: FAIL because `src/utils/userMapping.ts` does not exist yet.

---

## Task 2: Create Web Master Config Client

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.test.ts`

- [ ] **Step 1: Implement key builder and typed calls**

Create `src\utils\masterConfig.ts`:

```ts
import { supabase } from './supabase';

export type AdminMasterConfigPrefix =
  | 'cashier_limits'
  | 'cashier_prize_payouts'
  | 'system_modes'
  | 'manual_disabled_lotteries'
  | 'recharge_limits'
  | 'admin_operational_limits';

export type SportsbookMasterConfigPrefix =
  | 'sportsbook_global'
  | 'sportsbook_admin'
  | 'sportsbook_actor';

export type MasterConfigPrefix = AdminMasterConfigPrefix | SportsbookMasterConfigPrefix;

export function buildMasterConfigKey(prefix: AdminMasterConfigPrefix, ownerId: string): string;
export function buildMasterConfigKey(prefix: 'sportsbook_global'): string;
export function buildMasterConfigKey(prefix: 'sportsbook_admin' | 'sportsbook_actor', ownerId: string): string;
export function buildMasterConfigKey(prefix: MasterConfigPrefix, ownerId?: string): string {
  if (prefix === 'sportsbook_global') return 'sportsbook:global';
  if (!ownerId || ownerId.trim().length === 0) {
    throw new Error(`Missing owner id for ${prefix}`);
  }
  if (prefix === 'sportsbook_admin') return `sportsbook:admin:${ownerId}`;
  if (prefix === 'sportsbook_actor') return `sportsbook:actor:${ownerId}`;
  return `${prefix}:${ownerId}`;
}

export async function getMasterConfig<T>(key: string, fallback: T): Promise<T> {
  const { data, error } = await supabase.functions.invoke('get-master-config', {
    body: { key },
  });

  if (error) {
    console.warn(`Failed to fetch master config ${key}`, error);
    return fallback;
  }

  if (!data || typeof data !== 'object' || !('payload' in data) || data.payload == null) {
    return fallback;
  }

  return data.payload as T;
}

export async function saveMasterConfig<T>(key: string, payload: T): Promise<void> {
  const { error } = await supabase.functions.invoke('update-master-config', {
    body: { key, payload },
  });

  if (error) {
    throw new Error(`No se pudo guardar ${key}: ${error.message}`);
  }
}
```

- [ ] **Step 2: Run key tests**

Run:

```powershell
npm test -- src/utils/masterConfig.test.ts
```

Expected: PASS.

---

## Task 3: Extract User Mapping And Preserve `recargaTxLimit`

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\userMapping.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\supabase.ts`
- Test: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\userMapping.test.ts`

- [ ] **Step 1: Implement mapping functions**

Create `src\utils\userMapping.ts`:

```ts
import type { UserAccount } from '../types';

type RemoteUser = Record<string, any>;

export function normalizeCommissionToPercent(value: unknown, fallback = 8): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return fallback;
  return numeric > 1 ? numeric : numeric * 100;
}

export function normalizeCommissionToDecimal(value: unknown, fallback = 0.08): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return fallback;
  return numeric > 1 ? numeric / 100 : numeric;
}

export function readRecargaTxLimit(remote: RemoteUser): number | null {
  const raw = remote.recargaTx ?? remote.recargaTxLimit ?? remote.rechargeTxLimit;
  const numeric = Number(raw);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
}

export function mapRemoteUserToAccount(remote: RemoteUser): UserAccount {
  return {
    id: String(remote.id || remote.user || ''),
    user: String(remote.user || ''),
    pass: String(remote.pass || ''),
    role: String(remote.role || 'CASHIER').toUpperCase() as UserAccount['role'],
    displayName: remote.displayName ?? remote.nombre ?? remote.name ?? remote.user,
    commissionRate: normalizeCommissionToPercent(remote.commissionRate),
    recargaTxLimit: readRecargaTxLimit(remote),
    banca: remote.banca,
    adminId: remote.adminId,
    adminUser: remote.adminUser,
    supervisorId: remote.supervisorId,
    supervisorUser: remote.supervisorUser,
    supervisorIds: Array.isArray(remote.supervisorIds) ? remote.supervisorIds : [],
    supervisorUsers: Array.isArray(remote.supervisorUsers) ? remote.supervisorUsers : [],
    blocked: Boolean(remote.blocked),
    rechargesEnabled: Boolean(remote.rechargesEnabled ?? remote.recargasEnabled),
    rechargesAssignedBalance: Number(remote.rechargesAssignedBalance ?? remote.recargasAssignedBalance ?? 0),
    rechargesBalance: Number(remote.rechargesBalance ?? remote.recargasBalance ?? 0),
  };
}

export function mapAccountToRemoteUser(account: UserAccount): RemoteUser {
  return {
    ...account,
    commissionRate: normalizeCommissionToDecimal(account.commissionRate),
    recargaTx: account.recargaTxLimit ?? null,
    recargaTxLimit: account.recargaTxLimit ?? null,
  };
}
```

- [ ] **Step 2: Wire mapping into `supabase.ts`**

In `src\utils\supabase.ts`, replace inline user mapping inside `fetchUsers` and `saveAllUsers` with:

```ts
import { mapAccountToRemoteUser, mapRemoteUserToAccount } from './userMapping';
```

Use:

```ts
const mappedUsers = rawUsers.map(mapRemoteUserToAccount);
```

And for saving:

```ts
const payload = users.map(mapAccountToRemoteUser);
```

Preserve existing fields that are not part of this plan by spreading the previous payload before overriding `commissionRate`, `recargaTx`, and `recargaTxLimit`.

- [ ] **Step 3: Run tests and build**

Run:

```powershell
npm test -- src/utils/userMapping.test.ts
npm run build
```

Expected: test PASS and build PASS.

---

## Task 4: Move Web Admin Config Saves To `lotterynet_master_state`

**Files:**

- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\supabase.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- Test: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.test.ts`

- [ ] **Step 1: Replace limits write**

Change `saveAdminLimitsPayload(adminId, payload)` so it saves to:

```ts
await saveMasterConfig(
  buildMasterConfigKey('cashier_limits', adminId),
  JSON.parse(payload)
);
```

Keep local user `limitsPayload` only as compatibility cache, not source of truth.

- [ ] **Step 2: Replace prize payout config route**

For admin prize payout functions, write to:

```ts
buildMasterConfigKey('cashier_prize_payouts', adminId)
```

Read from `get-master-config` first. If empty, fallback to old `lotterynet_kv` key.

- [ ] **Step 3: Replace system mode config route**

For system modes, write to:

```ts
buildMasterConfigKey('system_modes', adminId)
```

This matters because `create-ticket-v2` validates `system_modes:<adminKey>` from `lotterynet_master_state`.

- [ ] **Step 4: Replace manual disabled lotteries route**

For manual disabled lotteries, write to:

```ts
buildMasterConfigKey('manual_disabled_lotteries', adminId)
```

This matters because `create-ticket-v2` validates `manual_disabled_lotteries:<adminKey>` from `lotterynet_master_state`.

- [ ] **Step 5: Build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 5: Expose MASTER Sections Already Supported By Web

**Files:**

- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\components\AppShell.tsx`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`

- [ ] **Step 1: Update role navigation**

In `AppShell.tsx`, allow MASTER to see existing supported sections:

```ts
const navItems = [
  { id: 'dashboard', roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
  { id: 'admins', roles: ['MASTER'] },
  { id: 'cajeros', roles: ['MASTER', 'ADMIN'] },
  { id: 'supervisores', roles: ['MASTER', 'ADMIN'] },
  { id: 'monitoreo', roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
  { id: 'deportiva', roles: ['MASTER', 'ADMIN'] },
  { id: 'tickets', roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
  { id: 'ganadores', roles: ['MASTER', 'ADMIN'] },
  { id: 'resultados', roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
  { id: 'limites', roles: ['MASTER', 'ADMIN'] },
  { id: 'finanzas', roles: ['MASTER', 'ADMIN'] },
  { id: 'cuadre', roles: ['MASTER', 'ADMIN', 'SUPERVISOR'] },
  { id: 'auditoria', roles: ['MASTER', 'SUPERVISOR'] },
];
```

Adapt the snippet to the actual local shape of `navItems`.

- [ ] **Step 2: Browser check**

Run:

```powershell
npm run build
```

Expected: PASS.

Open `http://localhost:5174/` as MASTER and confirm the menu exposes those sections.

---

## Task 6: Add ADMIN Commission And Recharge Transaction Controls

**Files:**

- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\types.ts`
- Test: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\userMapping.test.ts`

- [ ] **Step 1: Extend cashier form state**

In `Dashboard.tsx`, add:

```ts
const [cajeroForm, setCajeroForm] = useState({
  user: '',
  displayName: '',
  banca: '',
  territory: '',
  baseBalance: '',
  rechargesEnabled: false,
  rechargesAssignedBalance: '',
  supervisorId: '',
  commissionRate: '8',
  recargaTxLimit: '',
});
```

- [ ] **Step 2: Persist fields on create/edit cashier**

When building the cashier account:

```ts
commissionRate: Number(cajeroForm.commissionRate || 8),
recargaTxLimit: cajeroForm.recargaTxLimit ? Number(cajeroForm.recargaTxLimit) : null,
```

Validate:

```ts
if (Number(cajeroForm.commissionRate) < 0 || Number(cajeroForm.commissionRate) > 100) {
  throw new Error('El porcentaje debe estar entre 0 y 100.');
}
if (cajeroForm.recargaTxLimit && Number(cajeroForm.recargaTxLimit) < 0) {
  throw new Error('El limite de recarga no puede ser negativo.');
}
```

- [ ] **Step 3: Add visible fields in cajero panel**

Add two compact inputs:

```tsx
<label className="form-field">
  <span>Comision cajero (%)</span>
  <input
    type="number"
    min="0"
    max="100"
    step="0.1"
    value={cajeroForm.commissionRate}
    onChange={(event) => setCajeroForm((prev) => ({ ...prev, commissionRate: event.target.value }))}
  />
</label>

<label className="form-field">
  <span>Limite por recarga</span>
  <input
    type="number"
    min="0"
    step="1"
    value={cajeroForm.recargaTxLimit}
    onChange={(event) => setCajeroForm((prev) => ({ ...prev, recargaTxLimit: event.target.value }))}
  />
</label>
```

- [ ] **Step 4: Run build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 7: Add Supervisor Group Commission

**Files:**

- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- Test: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\userMapping.test.ts`

- [ ] **Step 1: Add assignment commission state**

Add:

```ts
const [supervisorGroupCommission, setSupervisorGroupCommission] = useState('8');
```

- [ ] **Step 2: Apply commission when saving assignments**

Inside the supervisor assignment save handler, update assigned cashiers:

```ts
const groupRate = Number(supervisorGroupCommission || 0);
if (!Number.isFinite(groupRate) || groupRate < 0 || groupRate > 100) {
  throw new Error('La comision grupal debe estar entre 0 y 100.');
}

const updatedUsers = users.map((account) => {
  if (account.role !== 'CASHIER') return account;
  const belongsToSupervisor = selectedCashierIds.includes(account.id);
  if (!belongsToSupervisor) return account;
  return {
    ...account,
    supervisorId: selectedSupervisor.id,
    supervisorUser: selectedSupervisor.user,
    supervisorIds: Array.from(new Set([...(account.supervisorIds || []), selectedSupervisor.id])),
    supervisorUsers: Array.from(new Set([...(account.supervisorUsers || []), selectedSupervisor.user])),
    commissionRate: groupRate,
  };
});
```

- [ ] **Step 3: Add UI field near assignment action**

Add:

```tsx
<label className="form-field">
  <span>Comision grupal (%)</span>
  <input
    type="number"
    min="0"
    max="100"
    step="0.1"
    value={supervisorGroupCommission}
    onChange={(event) => setSupervisorGroupCommission(event.target.value)}
  />
</label>
```

- [ ] **Step 4: Build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 8: Add MASTER Operations Panel

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\master\MasterOpsPanel.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\components\AppShell.tsx`

- [ ] **Step 1: Create master ops component**

Create `src\views\master\MasterOpsPanel.tsx`:

```tsx
import type { UserAccount } from '../../types';

type MasterOpsPanelProps = {
  admins: UserAccount[];
  cashiers: UserAccount[];
  onRefresh: () => Promise<void>;
};

export function MasterOpsPanel({ admins, cashiers, onRefresh }: MasterOpsPanelProps) {
  const totalBanks = admins.length;
  const activeBanks = admins.filter((admin) => !admin.blocked).length;
  const totalCashiers = cashiers.length;
  const blockedCashiers = cashiers.filter((cashier) => cashier.blocked).length;

  return (
    <section className="panel-section">
      <header className="section-header">
        <div>
          <h2>Operacion MASTER</h2>
          <p>Estado administrativo general de bancas, cajeros y sincronizacion.</p>
        </div>
        <button type="button" className="primary-button" onClick={() => void onRefresh()}>
          Refrescar
        </button>
      </header>

      <div className="metric-grid">
        <article className="metric-card">
          <span>Bancas</span>
          <strong>{activeBanks}/{totalBanks}</strong>
        </article>
        <article className="metric-card">
          <span>Cajeros</span>
          <strong>{totalCashiers}</strong>
        </article>
        <article className="metric-card">
          <span>Cajeros bloqueados</span>
          <strong>{blockedCashiers}</strong>
        </article>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Mount panel for MASTER**

In `Dashboard.tsx`, render `MasterOpsPanel` when:

```ts
user.role === 'MASTER'
```

Pass current admin/cashier lists and the existing refresh function.

- [ ] **Step 3: Build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 9: Add MASTER Recharge Limits And Access

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\master\MasterRechargePanel.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`

- [ ] **Step 1: Define recharge settings shape**

In `masterConfig.ts`, add:

```ts
export type RechargeLimitPayload = {
  masterPerTx?: number;
  adminPerTx?: number;
  cashierPerTx?: number;
  dailyMax?: number;
  enabled?: boolean;
};
```

- [ ] **Step 2: Add panel component**

Create `src\views\master\MasterRechargePanel.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { buildMasterConfigKey, getMasterConfig, saveMasterConfig, type RechargeLimitPayload } from '../../utils/masterConfig';
import type { UserAccount } from '../../types';

type Props = {
  admin: UserAccount;
};

const emptyRechargeLimits: RechargeLimitPayload = {
  enabled: false,
  masterPerTx: 0,
  adminPerTx: 0,
  cashierPerTx: 0,
  dailyMax: 0,
};

export function MasterRechargePanel({ admin }: Props) {
  const [limits, setLimits] = useState<RechargeLimitPayload>(emptyRechargeLimits);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void getMasterConfig<RechargeLimitPayload>(
      buildMasterConfigKey('recharge_limits', admin.id),
      emptyRechargeLimits
    ).then(setLimits);
  }, [admin.id]);

  async function save() {
    setSaving(true);
    try {
      await saveMasterConfig(buildMasterConfigKey('recharge_limits', admin.id), limits);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel-section">
      <header className="section-header">
        <h3>Recargas - {admin.displayName || admin.user}</h3>
      </header>
      <label className="toggle-row">
        <input
          type="checkbox"
          checked={Boolean(limits.enabled)}
          onChange={(event) => setLimits((prev) => ({ ...prev, enabled: event.target.checked }))}
        />
        <span>Recargas activas</span>
      </label>
      <label className="form-field">
        <span>Limite por transaccion cajero</span>
        <input
          type="number"
          value={limits.cashierPerTx || ''}
          onChange={(event) => setLimits((prev) => ({ ...prev, cashierPerTx: Number(event.target.value || 0) }))}
        />
      </label>
      <button type="button" className="primary-button" disabled={saving} onClick={() => void save()}>
        Guardar recargas
      </button>
    </section>
  );
}
```

- [ ] **Step 3: Mount for MASTER per selected admin**

Render this panel from `Dashboard.tsx` when MASTER selects a bank/admin.

- [ ] **Step 4: Build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 10: Add MASTER Sportsbook Config Parity

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\master\MasterSportsbookPanel.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\masterConfig.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`

- [ ] **Step 1: Add sportsbook type**

In `masterConfig.ts`, add:

```ts
export type SportsbookConfigPayload = {
  enabled?: boolean;
  enabledMarkets?: string[];
  allowedActorKeys?: string[];
  cashierAdminKeys?: string[];
};
```

- [ ] **Step 2: Add global save path**

Use:

```ts
await saveMasterConfig(buildMasterConfigKey('sportsbook_global'), payload);
```

For admin:

```ts
await saveMasterConfig(buildMasterConfigKey('sportsbook_admin', admin.id), payload);
```

For actor:

```ts
await saveMasterConfig(buildMasterConfigKey('sportsbook_actor', cashier.id), payload);
```

- [ ] **Step 3: Build a minimal MASTER sportsbook panel**

Create a panel with:

- global enabled toggle,
- admin enabled toggle,
- market checkboxes for principal markets,
- allowed actors list.

- [ ] **Step 4: Validate against Edge Function contract**

Run:

```powershell
npm run build
```

Expected: PASS.

Then confirm keys match `get-master-config` and `update-master-config` allowed key patterns:

- `sportsbook:global`
- `sportsbook:admin:<adminId>`
- `sportsbook:actor:<actorId>`

---

## Task 11: Add Read-Only Alerts And OTA Status First

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\master\MasterAlertsPanel.tsx`
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\master\MasterOtaPanel.tsx`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`

- [ ] **Step 1: Add alerts panel as read-only**

Create a panel that reads existing alert/audit sources already synced by Android. If table names are uncertain, start with audit logs already exposed in web and label it "Alertas operativas".

Acceptance:

- MASTER can see recent sensitive actions.
- ADMIN can see own alerts in a later task.
- No destructive actions in the first version.

- [ ] **Step 2: Add OTA panel as read-only**

Use existing OTA Edge Functions and logs if available:

- `ota-check-update`
- `ota-log-event`

First version should show:

- current target version if endpoint exposes it,
- last OTA events,
- update failures.

Do not add APK upload in this phase.

- [ ] **Step 3: Build**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 12: Add Secure Admin Command Edge Function

**Files:**

- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\supabase\functions\admin-users-command\index.ts`
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\utils\supabase.ts`

- [ ] **Step 1: Create Edge Function command envelope**

Create `supabase\functions\admin-users-command\index.ts`:

```ts
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type Command =
  | { type: "set_commission"; userId: string; commissionRate: number; recargaTxLimit?: number | null }
  | { type: "set_blocked"; userId: string; blocked: boolean }
  | { type: "assign_supervisor"; supervisorId: string; cashierIds: string[]; commissionRate?: number };

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "Missing auth" }), { status: 401 });
  }

  const command = (await req.json()) as Command;
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  if (command.type === "set_commission") {
    if (command.commissionRate < 0 || command.commissionRate > 1) {
      return new Response(JSON.stringify({ error: "Invalid commission" }), { status: 400 });
    }
  }

  return new Response(JSON.stringify({ ok: true, commandType: command.type }), {
    headers: { "Content-Type": "application/json" },
  });
});
```

This is the first secure envelope. In a follow-up task, wire it to existing user state logic and JWT role checks used by the other admin functions.

- [ ] **Step 2: Add web wrapper**

In web `src\utils\supabase.ts`, add:

```ts
export async function runAdminUserCommand(command: unknown): Promise<void> {
  const { error } = await supabase.functions.invoke('admin-users-command', {
    body: command,
  });
  if (error) throw new Error(error.message);
}
```

- [ ] **Step 3: Build web**

Run:

```powershell
npm run build
```

Expected: PASS.

---

## Task 13: Android Contract Verification

**Files:**

- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalUsersRepository.kt`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalCashierSalesLimitRepository.kt`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalCashierPrizePayoutRepository.kt`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalAdminLotteryConfigRepository.kt`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalMasterConfigRepository.kt`

- [ ] **Step 1: Confirm user fields**

Android must still read these fields after web saves:

```json
{
  "commissionRate": 0.075,
  "recargaTx": 500,
  "recargaTxLimit": 500,
  "supervisorIds": ["sup-1"],
  "supervisorUsers": ["supervisor1"]
}
```

Expected:

- Android commission becomes 7.5% where UI displays percent.
- Android recharge sale rejects amount above `recargaTx`.

- [ ] **Step 2: Confirm master config keys**

After web save, Android/Supabase must see:

```text
cashier_limits:<adminId>
cashier_prize_payouts:<adminId>
system_modes:<adminId>
manual_disabled_lotteries:<adminId>
recharge_limits:<adminId>
admin_operational_limits:<adminId>
sportsbook:global
sportsbook:admin:<adminId>
sportsbook:actor:<cashierId>
```

- [ ] **Step 3: Run Android smoke checks**

Build Android from root:

```powershell
cd "C:\Users\Randy Cordero\Desktop\lotterynet_android"
.\gradlew.bat assembleDebug
```

Expected: build completes.

Manual operational checks:

- Web disables a lottery; Android ticket sale rejects that lottery.
- Web changes normal/Pick mode; Android ticket sale follows it.
- Web changes cashier limit; Android sale limit follows it.
- Web changes commission; Android report/cuadre uses it.
- Web changes recharge transaction limit; Android recarga sale rejects above limit.

---

## Task 14: Browser Verification

**Files:**

- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\views\Dashboard.tsx`
- Verify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo\src\components\AppShell.tsx`

- [ ] **Step 1: Build web**

Run:

```powershell
cd "C:\Users\Randy Cordero\Desktop\lotterynet_android\proyecto web nuevo"
npm run build
```

Expected: PASS.

- [ ] **Step 2: Run local web**

Run:

```powershell
npm run dev -- --port 5174
```

Expected: app opens at `http://localhost:5174/`.

- [ ] **Step 3: Verify roles**

MASTER:

- sees dashboard,
- sees admins/bancas,
- sees cajeros,
- sees supervisores,
- sees monitoreo,
- sees tickets,
- sees ganadores,
- sees limites,
- sees finanzas,
- sees cuadre,
- sees master operations,
- can save master config.

ADMIN:

- sees cajeros/supervisores,
- edits commission,
- edits recharge transaction limit,
- saves limits,
- saves system modes,
- saves disabled lotteries.

SUPERVISOR:

- sees assigned cashier reports,
- sees tickets/monitoring/cuadre,
- cannot change MASTER-only config.

---

## Release Order

1. Ship Task 1-4 first. This fixes the data contract and reduces hidden production risk.
2. Ship Task 5-7 second. This gives MASTER/ADMIN the missing operational controls.
3. Ship Task 8-11 third. This expands MASTER parity without blocking daily admin work.
4. Ship Task 12 after confirming auth/JWT rules. This is security-sensitive.
5. Run Task 13-14 before deployment.

## Self Review

- Spec coverage: Covers web, Android, Supabase, MASTER, ADMIN, SUPERVISOR, comisiones, recargas, limites, sportsbook, alertas, OTA, security, and verification.
- Placeholder scan: No `TBD` or empty implementation steps. Some panels intentionally start read-only to avoid unsafe destructive operations.
- Type consistency: `commissionRate` is percent in web UI and decimal in persisted remote payload. `recargaTxLimit` is web type, `recargaTx` is Android-compatible persisted key.
