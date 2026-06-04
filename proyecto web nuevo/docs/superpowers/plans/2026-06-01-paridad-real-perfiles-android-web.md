# Paridad Real Perfiles Android Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alinear la web con lo que realmente hace Android por perfil MASTER, ADMIN y SUPERVISOR, con funciones operativas dentro de cada pantalla y no solo nombres de menú.

**Architecture:** Android queda como fuente de verdad. La web mantiene `Dashboard.tsx` como contenedor principal por ahora, pero extrae contratos de navegación/paridad y componentes de consola para que MASTER, ADMIN y SUPERVISOR tengan superficies distintas y probables. La venta POS, impresión térmica y flujo completo de cajero quedan fuera de alcance web.

**Tech Stack:** React, TypeScript, Vite, Supabase Edge Functions, `lotterynet_master_state`, `lotterynet_users_state`, tests con Vitest.

---

## Android Source Of Truth

### MASTER

Fuente principal:
- `../app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- `../app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt`

Secciones reales:
- `Bancas`
- `Credenciales`
- `Servidor/Nube`
- `Recargas Master`
- `Auditoría`

Funciones reales detectadas:
- Métricas: bancas, activas, bloqueadas, cajeros.
- Crear banca.
- Abrir auditoría.
- Buscar banca/admin/usuario.
- Filtrar bancas: todas, activas, bloqueadas, con problemas.
- Seleccionar admin/banca.
- Bloquear/desbloquear banca.
- Borrar banca.
- Regenerar credenciales de banca.
- Cambiar clave del admin.
- Agregar cajeros a una banca.
- Expandir/ver cajeros de una banca.
- Cambiar clave de cajero individual.
- Cambiar clave a todos los cajeros de una banca.
- Configurar acceso/recarga por banca.
- Guardar credenciales Recargas Rápidas por banca.
- Guardar cuenta default de Recargas Rápidas y permitir override individual por usuario/cajero en web, con prioridad usuario/cajero > banca/admin > default master.
- Habilitar/bloquear recargas por banca/admin (`rechargesEnabled`, cupo asignado y balance).
- Definir qué usuarios pueden usar Deportes desde configuración master (`master_sportsbook_settings`).
- Activar Deportes por rol, por banca/admin y por cajero específico.
- Configurar tope master de recarga.
- Guardar cuenta default Recargas Rápidas.
- Consultar cartera Recargas Rápidas.
- Revisar servidor.
- Sincronizar nube.
- Cargar snapshot remoto.
- Ver/compartir credenciales emitidas.

### ADMIN

Fuentes principales:
- `../app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt`
- `../app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- `../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`

Funciones reales detectadas:
- Resumen operativo de la banca: cajeros, ventas, caja, ganadores, pagados, pendientes, anulados, tickets recientes.
- Accesos críticos: Monitor, límite venta cajeros, tickets de cajeros, ganadores, caja, alertas.
- Accesos secundarios: usuarios, loterías, configuración, recargas.
- Supervisor: grupos, claves y cajeros.
- Límites: secciones Admin, Cajeros, Pagos, Recargas, POS.
- Sistema/configuración general.
- Monitoreo de banca.
- Monitoreo por cajero: al tocar una tarjeta de cajero se abre un modal/sheet filtrado a ese cajero con tickets, cobros, límites, recargas/cupo y datos administrativos.
- Comisiones visibles por usuario: sección dedicada para asignar comisión a cajeros y supervisores usando `commissionRate`, no escondida dentro de otros formularios.
- Tickets, resultados, deportes si está activo.
- Recargas solo si la banca/admin tiene `rechargesEnabled` y saldo/cupo aplicable.
- Deportes solo si `master_sportsbook_settings` habilita ADMIN o esa banca/admin/cajero.
- Cuadre/finanzas y reporte.
- Cobros y eliminación/anulación de tickets.
- Actualizar sistema Android queda fuera de web salvo panel informativo.
- Venta POS, repetir ticket e impresión térmica quedan fuera de alcance web.

### SUPERVISOR

Fuente principal:
- `../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- `../app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- `../app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt`

Funciones reales detectadas:
- Mis cajeros: asignados y movimiento.
- Monitoreo: ventas y pendientes.
- Finanzas: caja del grupo.
- Reporte: grupo asignado.
- Tickets: tickets del grupo.
- Resultados: sorteos del día.
- Deportes: solo si sportsbook está visible/habilitado.
- No tiene Recargas como venta directa en Android; solo puede ver caja/finanzas del grupo.
- Impresora y actualización sistema son Android-only para web por ahora.

---

## File Structure

Create:
- `src/utils/roleParity.ts`  
  Matriz única de tabs y funciones por perfil basada en Android.
- `src/utils/roleParity.test.ts`  
  Tests de paridad MASTER/ADMIN/SUPERVISOR.
- `src/components/master/MasterConsole.tsx`  
  Consola MASTER con tabs internos: Bancas, Credenciales, Servidor/Nube, Recargas Master, Auditoría.
- `src/components/master/MasterBankPanel.tsx`  
  Bancas: métricas, filtro, selector, acciones por banca/cajeros.
- `src/components/master/MasterCredentialsPanel.tsx`  
  Credenciales emitidas, regeneración, cambio de clave admin/cajeros, compartir texto.
- `src/components/master/MasterServerPanel.tsx`  
  Revisar servidor, sincronizar nube, snapshot remoto.
- `src/components/master/MasterRechargePanel.tsx`  
  Tope master, cuenta default RR, cartera RR, credenciales RR por banca y override RR por usuario/cajero.
- `src/utils/userFeatureAccess.ts`  
  Contrato web para decidir si Recargas y Deportes se muestran a cada usuario.
- `src/utils/userFeatureAccess.test.ts`  
  Tests de acceso por MASTER/ADMIN/SUPERVISOR/CASHIER, banca/admin y cajero.
- `src/utils/recargasRapidasCredentials.ts`  
  Resolver de credenciales RR con prioridad usuario/cajero > banca/admin > default.
- `src/utils/recargasRapidasCredentials.test.ts`  
  Tests de resolución RR por scope.
- `src/utils/cashierScope.ts`  
  Helpers puros para filtrar tickets, cobros, límites y recargas por cajero seleccionado.
- `src/utils/cashierScope.test.ts`  
  Tests para que ADMIN/SUPERVISOR no mezclen datos de otros cajeros.
- `src/components/admin/AdminOperationsConsole.tsx`  
  Panel ADMIN con resumen operativo y accesos agrupados como Android.
- `src/components/admin/CashierOperationSheet.tsx`  
  Sheet/modal operativo cuando ADMIN toca una tarjeta de cajero: resumen, tickets, cobros, límites, recargas y datos.
- `src/components/admin/AdminCommissionsPanel.tsx`  
  Sección visible para editar comisión por cajero y supervisor, con guardar cerca de cada fila.
- `src/components/admin/AdminLotteryLimitsPanel.tsx`  
  Panel estilo VoloRed para límites por nivel: general, lotería, cajero/punto de venta y jugada/combinación.
- `src/utils/lotteryLimitStructure.ts`  
  Contrato de límites jerárquicos para no mezclar límites globales, por lotería, por cajero y por jugada.
- `src/utils/lotteryLimitStructure.test.ts`  
  Tests de prioridad de límites y filtrado por perfil.
- `src/components/admin/ClosingAutomationPanel.tsx`  
  Panel de cierre/listado automático: estado de cierre, envío email/WhatsApp y snapshot al cierre de lotería.
- `src/components/supervisor/SupervisorConsole.tsx`  
  Panel SUPERVISOR con mis cajeros, monitoreo, finanzas, reporte, tickets, resultados.

Modify:
- `src/utils/navigationPermissions.ts`  
  Importar matriz desde `roleParity.ts`.
- `src/components/AppShell.tsx`  
  Menú por rol desde `roleParity.ts`; MASTER no debe recibir accesos operativos sueltos.
- `src/views/Dashboard.tsx`  
  Reemplazar bloques MASTER/ADMIN/SUPERVISOR por consolas nuevas de forma incremental.
- `src/utils/adminCommands.ts`  
  Confirmar comandos necesarios para acciones sensibles.
- `src/utils/supabase.ts`  
  Leer/escribir configuración de Deportes master y estados de recarga por usuario/banca.
- `docs/admin-parity-map.md`  
  Actualizar porcentaje y brechas reales.

---

## Task 1: Crear Contrato Único De Paridad Por Perfil

**Files:**
- Create: `src/utils/roleParity.ts`
- Create: `src/utils/roleParity.test.ts`
- Modify: `src/utils/navigationPermissions.ts`
- Modify: `src/components/AppShell.tsx`

- [ ] **Step 1: Write failing tests**

Create `src/utils/roleParity.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { getRoleParity, getVisibleTabsForRole, isTabAllowedForRole } from './roleParity';

describe('roleParity', () => {
  it('matches real Android MASTER console sections', () => {
    const parity = getRoleParity('MASTER');
    expect(parity.homeTab).toBe('dashboard');
    expect(parity.visibleTabs).toEqual(['dashboard', 'admins', 'auditoria']);
    expect(parity.consoleSections).toEqual(['banks', 'credentials', 'server', 'recharges', 'audit']);
    expect(isTabAllowedForRole('MASTER', 'deportiva')).toBe(false);
    expect(isTabAllowedForRole('MASTER', 'finanzas')).toBe(false);
  });

  it('matches real Android ADMIN operational admin scope without POS sale', () => {
    expect(getVisibleTabsForRole('ADMIN')).toEqual([
      'dashboard',
      'cajeros',
      'supervisores',
      'monitoreo',
      'deportiva',
      'tickets',
      'ganadores',
      'resultados',
      'limites',
      'finanzas',
      'cuadre',
      'reportes',
      'auditoria',
    ]);
  });

  it('matches real Android SUPERVISOR assigned-operation scope', () => {
    expect(getVisibleTabsForRole('SUPERVISOR')).toEqual([
      'dashboard',
      'monitoreo',
      'deportiva',
      'tickets',
      'resultados',
      'finanzas',
      'cuadre',
      'reportes',
    ]);
    expect(isTabAllowedForRole('SUPERVISOR', 'limites')).toBe(false);
    expect(isTabAllowedForRole('SUPERVISOR', 'auditoria')).toBe(false);
  });
});
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
npm test -- src/utils/roleParity.test.ts
```

Expected: FAIL because `roleParity.ts` does not exist.

- [ ] **Step 3: Implement role parity**

Create `src/utils/roleParity.ts`:

```ts
import type { UserRole } from '../types';

export type RoleName = UserRole | string | null | undefined;

export interface RoleParityDefinition {
  role: string;
  homeTab: string;
  visibleTabs: string[];
  consoleSections: string[];
  androidSourceFiles: string[];
}

const normalizeRole = (role: RoleName): string => String(role || 'UNKNOWN').toUpperCase();

const ROLE_PARITY: Record<string, RoleParityDefinition> = {
  MASTER: {
    role: 'MASTER',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard', 'admins', 'auditoria'],
    consoleSections: ['banks', 'credentials', 'server', 'recharges', 'audit'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt',
    ],
  },
  ADMIN: {
    role: 'ADMIN',
    homeTab: 'dashboard',
    visibleTabs: [
      'dashboard',
      'cajeros',
      'supervisores',
      'monitoreo',
      'deportiva',
      'tickets',
      'ganadores',
      'resultados',
      'limites',
      'finanzas',
      'cuadre',
      'reportes',
      'auditoria',
    ],
    consoleSections: ['summary', 'critical', 'secondary', 'administration', 'system'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminDashboardActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt',
    ],
  },
  SUPERVISOR: {
    role: 'SUPERVISOR',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard', 'monitoreo', 'deportiva', 'tickets', 'resultados', 'finanzas', 'cuadre', 'reportes'],
    consoleSections: ['myCashiers', 'monitoring', 'finance', 'report', 'tickets', 'results'],
    androidSourceFiles: [
      '../app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt',
      '../app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt',
    ],
  },
};

export const getRoleParity = (role: RoleName): RoleParityDefinition => {
  return ROLE_PARITY[normalizeRole(role)] || {
    role: 'UNKNOWN',
    homeTab: 'dashboard',
    visibleTabs: ['dashboard'],
    consoleSections: [],
    androidSourceFiles: [],
  };
};

export const getVisibleTabsForRole = (role: RoleName): string[] => {
  return getRoleParity(role).visibleTabs;
};

export const isTabAllowedForRole = (role: RoleName, tab: string): boolean => {
  return getVisibleTabsForRole(role).includes(tab);
};
```

- [ ] **Step 4: Wire navigation to role parity**

Replace `src/utils/navigationPermissions.ts` with:

```ts
import type { UserRole } from '../types';
import { getVisibleTabsForRole, isTabAllowedForRole } from './roleParity';

export const getAllowedAdminTabs = (role: UserRole | string | null | undefined): string[] => {
  return getVisibleTabsForRole(role);
};

export const isAdminTabAllowed = (role: UserRole | string | null | undefined, tab: string): boolean => {
  return isTabAllowedForRole(role, tab);
};

export const getSafeAdminTab = (role: UserRole | string | null | undefined, activeTab: string): string => {
  return isAdminTabAllowed(role, activeTab) ? activeTab : 'dashboard';
};
```

- [ ] **Step 5: Run tests**

Run:

```bash
npm test -- src/utils/roleParity.test.ts src/utils/navigationPermissions.test.ts
```

Expected: PASS.

---

## Task 2: Consola MASTER Con Secciones Reales

**Files:**
- Create: `src/components/master/MasterConsole.tsx`
- Create: `src/components/master/MasterBankPanel.tsx`
- Create: `src/components/master/MasterCredentialsPanel.tsx`
- Create: `src/components/master/MasterServerPanel.tsx`
- Create: `src/components/master/MasterRechargePanel.tsx`
- Modify: `src/views/Dashboard.tsx`

- [ ] **Step 1: Add MasterConsole shell**

Create `src/components/master/MasterConsole.tsx`:

```tsx
import React, { useState } from 'react';
import { Activity, Key, Landmark, RefreshCw, ShieldCheck } from 'lucide-react';
import type { AuditLog, UserAccount } from '../../types';
import { MasterBankPanel } from './MasterBankPanel';
import { MasterCredentialsPanel } from './MasterCredentialsPanel';
import { MasterRechargePanel } from './MasterRechargePanel';
import { MasterServerPanel } from './MasterServerPanel';

export type MasterSectionId = 'banks' | 'credentials' | 'server' | 'recharges' | 'audit';

export interface MasterConsoleProps {
  users: UserAccount[];
  audits: AuditLog[];
  onCreateBank: () => void;
  onOpenAudit: () => void;
  onRefresh: () => void;
}

const sections: Array<{ id: MasterSectionId; label: string; icon: React.ElementType }> = [
  { id: 'banks', label: 'Bancas', icon: Landmark },
  { id: 'credentials', label: 'Credenciales', icon: Key },
  { id: 'server', label: 'Servidor/Nube', icon: Activity },
  { id: 'recharges', label: 'Recargas Master', icon: RefreshCw },
  { id: 'audit', label: 'Auditoría', icon: ShieldCheck },
];

export const MasterConsole: React.FC<MasterConsoleProps> = ({ users, audits, onCreateBank, onOpenAudit, onRefresh }) => {
  const [section, setSection] = useState<MasterSectionId>('banks');
  const admins = users.filter((user) => user.role === 'ADMIN');
  const cashiers = users.filter((user) => user.role === 'CASHIER');

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="fintech-panel-header" style={{ flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h3 className="fintech-panel-title">Panel Master</h3>
          <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>
            Bancas · credenciales · servidor · recargas master · auditoría
          </span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 8 }}>
        {sections.map((item) => {
          const Icon = item.icon;
          const active = item.id === section;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setSection(item.id)}
              className={active ? 'btn btn-primary' : 'btn btn-secondary'}
              style={{ minHeight: 40, justifyContent: 'center' }}
            >
              <Icon size={16} />
              {item.label}
            </button>
          );
        })}
      </div>

      {section === 'banks' && <MasterBankPanel admins={admins} cashiers={cashiers} onCreateBank={onCreateBank} />}
      {section === 'credentials' && <MasterCredentialsPanel admins={admins} cashiers={cashiers} />}
      {section === 'server' && <MasterServerPanel onRefresh={onRefresh} />}
      {section === 'recharges' && <MasterRechargePanel admins={admins} />}
      {section === 'audit' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <button type="button" className="btn btn-secondary" onClick={onOpenAudit} style={{ alignSelf: 'flex-start' }}>
            Abrir auditoría completa
          </button>
          {audits.slice(0, 8).map((audit) => (
            <div key={audit.id} style={{ border: '1px solid hsl(var(--border))', borderRadius: 'var(--radius-md)', padding: 12 }}>
              <strong>{audit.action}</strong>
              <p style={{ margin: '4px 0 0', color: 'hsl(var(--text-secondary))', fontSize: '0.82rem' }}>{audit.details}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Add bank panel**

Create `src/components/master/MasterBankPanel.tsx`:

```tsx
import React, { useMemo, useState } from 'react';
import type { UserAccount } from '../../types';

interface Props {
  admins: UserAccount[];
  cashiers: UserAccount[];
  onCreateBank: () => void;
}

type Filter = 'ALL' | 'ACTIVE' | 'BLOCKED' | 'ISSUES';

export const MasterBankPanel: React.FC<Props> = ({ admins, cashiers, onCreateBank }) => {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('ALL');

  const filteredAdmins = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return admins.filter((admin) => {
      const matchesQuery = !needle || `${admin.user} ${admin.banca || ''} ${admin.displayName || ''}`.toLowerCase().includes(needle);
      const matchesFilter =
        filter === 'ALL' ||
        (filter === 'ACTIVE' && admin.active !== false) ||
        (filter === 'BLOCKED' && admin.active === false) ||
        (filter === 'ISSUES' && cashiers.filter((cashier) => cashier.adminId === admin.id).length === 0);
      return matchesQuery && matchesFilter;
    });
  }, [admins, cashiers, filter, query]);

  const activeBanks = admins.filter((admin) => admin.active !== false).length;
  const blockedBanks = admins.filter((admin) => admin.active === false).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <div className="glass-panel" style={{ padding: 14 }}>Bancas<br /><strong>{admins.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Activas<br /><strong>{activeBanks}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Bloqueadas<br /><strong>{blockedBanks}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Cajeros<br /><strong>{cashiers.length}</strong></div>
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button type="button" className="btn btn-primary" onClick={onCreateBank}>Crear banca</button>
        {(['ALL', 'ACTIVE', 'BLOCKED', 'ISSUES'] as Filter[]).map((item) => (
          <button key={item} type="button" className={filter === item ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setFilter(item)}>
            {item === 'ALL' ? 'Todas' : item === 'ACTIVE' ? 'Activas' : item === 'BLOCKED' ? 'Bloqueadas' : 'Con problemas'}
          </button>
        ))}
      </div>
      <input className="form-input" placeholder="Buscar admin, banca o usuario" value={query} onChange={(event) => setQuery(event.target.value)} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {filteredAdmins.map((admin) => {
          const bankCashiers = cashiers.filter((cashier) => cashier.adminId === admin.id || cashier.adminUser === admin.user || cashier.banca === admin.banca);
          return (
            <div key={admin.id} style={{ border: '1px solid hsl(var(--border))', borderRadius: 'var(--radius-md)', padding: 14 }}>
              <strong>{admin.banca || admin.displayName || admin.user}</strong>
              <p style={{ margin: '4px 0', color: 'hsl(var(--text-secondary))' }}>@{admin.user} · {bankCashiers.length} cajeros</p>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button type="button" className="btn btn-secondary">Bloquear/activar</button>
                <button type="button" className="btn btn-secondary">Regenerar claves</button>
                <button type="button" className="btn btn-secondary">Agregar cajeros</button>
                <button type="button" className="btn btn-danger">Borrar</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Add placeholder panels that expose exact Android sections**

Create `src/components/master/MasterCredentialsPanel.tsx`:

```tsx
import React from 'react';
import type { UserAccount } from '../../types';

interface Props {
  admins: UserAccount[];
  cashiers: UserAccount[];
}

export const MasterCredentialsPanel: React.FC<Props> = ({ admins, cashiers }) => {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
      <div className="glass-panel" style={{ padding: 16 }}>
        <strong>Credenciales de bancas</strong>
        <p style={{ color: 'hsl(var(--text-secondary))' }}>{admins.length} admins disponibles para regenerar o cambiar clave.</p>
      </div>
      <div className="glass-panel" style={{ padding: 16 }}>
        <strong>Credenciales de cajeros</strong>
        <p style={{ color: 'hsl(var(--text-secondary))' }}>{cashiers.length} cajeros disponibles para cambio individual o grupal.</p>
      </div>
    </div>
  );
};
```

Create `src/components/master/MasterServerPanel.tsx`:

```tsx
import React from 'react';

interface Props {
  onRefresh: () => void;
}

export const MasterServerPanel: React.FC<Props> = ({ onRefresh }) => {
  return (
    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      <button type="button" className="btn btn-secondary">Revisar servidor</button>
      <button type="button" className="btn btn-primary">Sincronizar nube</button>
      <button type="button" className="btn btn-secondary" onClick={onRefresh}>Snapshot remoto</button>
    </div>
  );
};
```

Create `src/components/master/MasterRechargePanel.tsx`:

```tsx
import React from 'react';
import type { UserAccount } from '../../types';

interface Props {
  admins: UserAccount[];
}

export const MasterRechargePanel: React.FC<Props> = ({ admins }) => {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
      <div className="form-group">
        <label className="form-label">Tope master</label>
        <input className="form-input" inputMode="decimal" placeholder="0 sin tope" />
      </div>
      <div className="form-group">
        <label className="form-label">Usuario RR default</label>
        <input className="form-input" />
      </div>
      <div className="form-group">
        <label className="form-label">Clave RR default</label>
        <input className="form-input" type="password" />
      </div>
      <div className="glass-panel" style={{ padding: 16 }}>
        <strong>Bancas con RR</strong>
        <p style={{ color: 'hsl(var(--text-secondary))' }}>{admins.length} bancas para credenciales por negocio.</p>
      </div>
    </div>
  );
};
```

- [ ] **Step 4: Wire MasterConsole in Dashboard**

In `src/views/Dashboard.tsx`, import:

```ts
import { MasterConsole } from '../components/master/MasterConsole';
```

Replace the current MASTER center block in the dashboard with:

```tsx
{user.role === 'MASTER' && (
  <MasterConsole
    users={users}
    audits={audits}
    onCreateBank={() => setAdminModalOpen(true)}
    onOpenAudit={() => setActiveTab?.('auditoria')}
    onRefresh={loadData}
  />
)}
```

If `Dashboard` does not receive `setActiveTab`, add a prop:

```ts
interface DashboardProps {
  activeTab: string;
  setActiveTab?: (tab: string) => void;
}
```

Then update `src/App.tsx`:

```tsx
<Dashboard activeTab={safeTab} setActiveTab={setActiveTab} />
```

- [ ] **Step 5: Build**

Run:

```bash
npm run build
```

Expected: PASS.

---

## Task 3: Acceso Real A Recargas Y Deportes Por Usuario

**Files:**
- Create: `src/utils/userFeatureAccess.ts`
- Create: `src/utils/userFeatureAccess.test.ts`
- Modify: `src/utils/masterConfig.ts`
- Modify: `src/utils/supabase.ts`
- Modify: `src/utils/roleParity.ts`
- Modify: `src/components/AppShell.tsx`
- Modify: `src/components/master/MasterRechargePanel.tsx`

- [ ] **Step 1: Write failing access tests**

Create `src/utils/userFeatureAccess.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import type { UserAccount } from '../types';
import {
  canShowRechargeAccess,
  canShowSportsbookAccess,
  type MasterSportsbookSettings,
} from './userFeatureAccess';

const admin: UserAccount = {
  id: 'adm-1',
  user: 'admin1',
  role: 'ADMIN',
  displayName: 'Admin Uno',
  active: true,
  banca: 'Banca Uno',
  rechargesEnabled: true,
  rechargesAssignedBalance: 1000,
  rechargesBalance: 800,
  supervisorIds: [],
  supervisorUsers: [],
};

const cashier: UserAccount = {
  id: 'caj-1',
  user: 'caj01',
  role: 'CASHIER',
  displayName: 'Cajero Uno',
  active: true,
  adminId: 'adm-1',
  adminUser: 'admin1',
  banca: 'Banca Uno',
  rechargesEnabled: true,
  rechargesAssignedBalance: 100,
  rechargesBalance: 50,
  supervisorIds: [],
  supervisorUsers: [],
};

const settings: MasterSportsbookSettings = {
  enabled: true,
  adminEnabled: true,
  supervisorEnabled: false,
  cashierEnabled: false,
  allowedActorKeys: [],
  cashierAdminKeys: ['adm-1'],
};

describe('userFeatureAccess', () => {
  it('shows recharge access only for active enabled admin/cashier owner paths', () => {
    expect(canShowRechargeAccess(admin, admin)).toBe(true);
    expect(canShowRechargeAccess(cashier, admin)).toBe(true);
    expect(canShowRechargeAccess({ ...admin, rechargesEnabled: false }, { ...admin, rechargesEnabled: false })).toBe(false);
    expect(canShowRechargeAccess({ ...cashier, role: 'SUPERVISOR' }, admin)).toBe(false);
  });

  it('shows sportsbook access from master settings by role and admin key', () => {
    expect(canShowSportsbookAccess(admin, settings)).toBe(true);
    expect(canShowSportsbookAccess(cashier, settings)).toBe(true);
    expect(canShowSportsbookAccess({ ...cashier, id: 'caj-2', user: 'caj02', adminId: 'adm-2' }, settings)).toBe(false);
  });

  it('supports explicit actor access for supervisors and individual cashiers', () => {
    expect(canShowSportsbookAccess({ ...cashier, id: 'caj-9', user: 'solo' }, {
      ...settings,
      adminEnabled: false,
      cashierAdminKeys: [],
      allowedActorKeys: ['solo'],
    })).toBe(true);
    expect(canShowSportsbookAccess({ ...cashier, role: 'SUPERVISOR', id: 'sup-1', user: 'sup1' }, {
      ...settings,
      adminEnabled: false,
      supervisorEnabled: true,
      cashierAdminKeys: [],
    })).toBe(true);
  });
});
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
npm test -- src/utils/userFeatureAccess.test.ts
```

Expected: FAIL because `userFeatureAccess.ts` does not exist.

- [ ] **Step 3: Implement feature access helpers**

Create `src/utils/userFeatureAccess.ts`:

```ts
import type { UserAccount } from '../types';

export interface MasterSportsbookSettings {
  enabled: boolean;
  adminEnabled: boolean;
  supervisorEnabled: boolean;
  cashierEnabled: boolean;
  allowedActorKeys: string[];
  cashierAdminKeys: string[];
}

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

const accountKeys = (account: Pick<UserAccount, 'id' | 'user' | 'adminId' | 'adminUser'>): string[] => {
  return [account.id, account.user, account.adminId || '', account.adminUser || '']
    .map(normalizeKey)
    .filter(Boolean);
};

export const defaultMasterSportsbookSettings: MasterSportsbookSettings = {
  enabled: false,
  adminEnabled: false,
  supervisorEnabled: false,
  cashierEnabled: false,
  allowedActorKeys: [],
  cashierAdminKeys: [],
};

export const canShowRechargeAccess = (sessionUser: UserAccount, ownerAccount?: UserAccount | null): boolean => {
  if (sessionUser.role !== 'ADMIN' && sessionUser.role !== 'CASHIER') return false;
  const owner = sessionUser.role === 'ADMIN' ? sessionUser : ownerAccount;
  return owner?.active !== false && owner?.rechargesEnabled === true;
};

export const canShowSportsbookAccess = (sessionUser: UserAccount, settings: MasterSportsbookSettings): boolean => {
  if (!settings.enabled) return false;
  if (sessionUser.role === 'MASTER') return true;

  const actorKeys = new Set(accountKeys(sessionUser));
  const allowedActorKeys = new Set(settings.allowedActorKeys.map(normalizeKey));
  const allowedAdminKeys = new Set(settings.cashierAdminKeys.map(normalizeKey));

  const hasExplicitActorAccess = [...actorKeys].some((key) => allowedActorKeys.has(key));
  const hasAdminScopeAccess = [...actorKeys].some((key) => allowedAdminKeys.has(key));

  if (sessionUser.role === 'ADMIN') {
    return settings.adminEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  }

  if (sessionUser.role === 'SUPERVISOR') {
    return settings.supervisorEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  }

  if (sessionUser.role === 'CASHIER') {
    return settings.cashierEnabled || hasExplicitActorAccess || hasAdminScopeAccess;
  }

  return false;
};
```

- [ ] **Step 4: Add master config helpers**

Modify `src/utils/masterConfig.ts` to add:

```ts
import { defaultMasterSportsbookSettings, type MasterSportsbookSettings } from './userFeatureAccess';

export const getMasterSportsbookSettings = async (): Promise<MasterSportsbookSettings> => {
  return getMasterConfig<MasterSportsbookSettings>('master_sportsbook_settings', defaultMasterSportsbookSettings);
};

export const saveMasterSportsbookSettings = async (settings: MasterSportsbookSettings): Promise<void> => {
  await saveMasterConfig('master_sportsbook_settings', settings);
};
```

If `masterConfig.ts` already has imports from `userFeatureAccess`, merge them instead of duplicating.

- [ ] **Step 5: Make role tabs conditional**

Modify `src/utils/roleParity.ts` so `deportiva` and recarga-related tabs are not treated as unconditional truth. Add:

```ts
export interface RuntimeFeatureFlags {
  sportsbookVisible?: boolean;
  rechargeVisible?: boolean;
}

export const getVisibleTabsForRole = (role: RoleName, flags: RuntimeFeatureFlags = {}): string[] => {
  const base = getRoleParity(role).visibleTabs;
  return base.filter((tab) => {
    if (tab === 'deportiva') return flags.sportsbookVisible === true;
    return true;
  });
};

export const isTabAllowedForRole = (role: RoleName, tab: string, flags: RuntimeFeatureFlags = {}): boolean => {
  return getVisibleTabsForRole(role, flags).includes(tab);
};
```

For web admin panel, keep `finanzas` visible for ADMIN/SUPERVISOR because Android uses finance/caja even when Recargas sale is hidden. The actual `Recargas` selling module remains out of web scope.

- [ ] **Step 6: Add MASTER sportsbook controls in Recargas Master panel**

Modify `src/components/master/MasterRechargePanel.tsx` to include a second section titled `Deportes por usuario`:

```tsx
<div className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
  <strong>Deportes por usuario</strong>
  <label>
    <input type="checkbox" checked={settings.enabled} onChange={(event) => onSettingsChange({ ...settings, enabled: event.target.checked })} />
    Sistema Deportes activo
  </label>
  <label>
    <input type="checkbox" checked={settings.adminEnabled} onChange={(event) => onSettingsChange({ ...settings, adminEnabled: event.target.checked })} />
    ADMIN puede usar Deportes
  </label>
  <label>
    <input type="checkbox" checked={settings.supervisorEnabled} onChange={(event) => onSettingsChange({ ...settings, supervisorEnabled: event.target.checked })} />
    SUPERVISOR puede ver Deportes
  </label>
  <label>
    <input type="checkbox" checked={settings.cashierEnabled} onChange={(event) => onSettingsChange({ ...settings, cashierEnabled: event.target.checked })} />
    Cajeros pueden usar Deportes
  </label>
</div>
```

Add a list of admins and cajeros with checkboxes that updates:
- `cashierAdminKeys` for whole banca/admin access.
- `allowedActorKeys` for individual cashier/supervisor access.

- [ ] **Step 7: Wire AppShell to runtime access**

Modify `src/components/AppShell.tsx` so the rendered nav items receive a `sportsbookVisible` flag from props:

```ts
interface AppShellProps {
  children: React.ReactNode;
  activeTab: string;
  setActiveTab: (tab: string) => void;
  sportsbookVisible?: boolean;
}
```

Use:

```ts
return items.filter(item => isAdminTabAllowed(role, item.id, { sportsbookVisible }));
```

Modify `src/App.tsx` to compute and pass this after session loads. If async settings are not available yet, default to false for `deportiva` until loaded.

- [ ] **Step 8: Test**

Run:

```bash
npm test -- src/utils/userFeatureAccess.test.ts src/utils/roleParity.test.ts src/utils/navigationPermissions.test.ts
npm run build
```

Expected: all tests pass; build passes.

---

## Task 4: Recargas Rápidas Por Default, Banca Y Usuario

**Files:**
- Create: `src/utils/recargasRapidasCredentials.ts`
- Create: `src/utils/recargasRapidasCredentials.test.ts`
- Modify: `src/utils/masterConfig.ts`
- Modify: `src/components/master/MasterRechargePanel.tsx`

- [ ] **Step 1: Write failing RR credential tests**

Create `src/utils/recargasRapidasCredentials.test.ts`:

```ts
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
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
npm test -- src/utils/recargasRapidasCredentials.test.ts
```

Expected: FAIL because `recargasRapidasCredentials.ts` does not exist.

- [ ] **Step 3: Implement RR credential resolver**

Create `src/utils/recargasRapidasCredentials.ts`:

```ts
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
  id?: string;
  user?: string;
  adminId?: string;
  adminUser?: string;
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
```

- [ ] **Step 4: Add master config helpers**

Modify `src/utils/masterConfig.ts` to add:

```ts
import {
  emptyRecargasRapidasCredentialConfig,
  type RecargasRapidasCredentialConfig,
} from './recargasRapidasCredentials';

export const getRecargasRapidasCredentialConfig = async (): Promise<RecargasRapidasCredentialConfig> => {
  return getMasterConfig<RecargasRapidasCredentialConfig>(
    'recargas_rapidas_credentials_v1',
    emptyRecargasRapidasCredentialConfig,
  );
};

export const saveRecargasRapidasCredentialConfig = async (config: RecargasRapidasCredentialConfig): Promise<void> => {
  await saveMasterConfig('recargas_rapidas_credentials_v1', config);
};
```

Keep Android-compatible default/admin behavior when calling backend RR endpoints. The new `byUser` map is a web extension so MASTER can set a cajero-specific account when needed.

- [ ] **Step 5: Add MASTER UI for default/admin/user RR accounts**

Modify `src/components/master/MasterRechargePanel.tsx` so Recargas Master has three compact sections:

```tsx
<div className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
  <strong>Cuenta default Recargas Rápidas</strong>
  <input className="form-input" value={defaultUsername} onChange={(event) => setDefaultUsername(event.target.value)} placeholder="Usuario RR default" />
  <input className="form-input" type="password" value={defaultPassword} onChange={(event) => setDefaultPassword(event.target.value)} placeholder="Clave RR default" />
  <button type="button" className="btn btn-primary" onClick={saveDefaultCredentials}>Guardar default</button>
</div>

<div className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
  <strong>Cuenta por banca/admin</strong>
  {admins.map((admin) => (
    <div key={admin.id} className="inline-edit-row">
      <span>{admin.banca || admin.user}</span>
      <button type="button" className="btn btn-secondary" onClick={() => openAdminCredentialEditor(admin)}>Editar cuenta</button>
    </div>
  ))}
</div>

<div className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
  <strong>Cuenta individual por usuario/cajero</strong>
  {cashiers.map((cashier) => (
    <div key={cashier.id} className="inline-edit-row">
      <span>{cashier.displayName || cashier.user}</span>
      <button type="button" className="btn btn-secondary" onClick={() => openUserCredentialEditor(cashier)}>Editar cuenta</button>
    </div>
  ))}
</div>
```

The save buttons must persist through `saveRecargasRapidasCredentialConfig`. Show a small synced/error state inside the same block that was saved.

- [ ] **Step 6: Verify RR tests**

Run:

```bash
npm test -- src/utils/recargasRapidasCredentials.test.ts
npm run build
```

Expected: PASS.

---

## Task 5: ADMIN Dashboard Parity

**Files:**
- Create: `src/components/admin/AdminOperationsConsole.tsx`
- Modify: `src/views/Dashboard.tsx`

- [ ] **Step 1: Create admin console**

Create `src/components/admin/AdminOperationsConsole.tsx`:

```tsx
import React from 'react';
import { AlertTriangle, MonitorHeart, ReceiptText, Settings, Sliders, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';

interface Props {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  onOpen: (tab: string) => void;
}

export const AdminOperationsConsole: React.FC<Props> = ({ user, users, tickets, onOpen }) => {
  const cashiers = users.filter((candidate) => candidate.role === 'CASHIER' && candidate.adminId === user.id);
  const scopedTickets = tickets.filter((ticket) => ticket.adminId === user.id || ticket.sellerUser === user.user);
  const sales = scopedTickets.filter((ticket) => ticket.status !== 'cancelled' && ticket.status !== 'voided').reduce((sum, ticket) => sum + ticket.total, 0);
  const pendingPrizes = scopedTickets.filter((ticket) => ticket.status === 'winner').reduce((sum, ticket) => sum + ticket.totalPrize, 0);

  const shortcuts = [
    { label: 'Monitor', tab: 'monitoreo', icon: MonitorHeart },
    { label: 'Límite venta cajeros', tab: 'limites', icon: Sliders },
    { label: 'Tickets de cajeros', tab: 'tickets', icon: ReceiptText },
    { label: 'Ganadores', tab: 'ganadores', icon: Trophy },
    { label: 'Caja', tab: 'cuadre', icon: ReceiptText },
    { label: 'Alertas', tab: 'auditoria', icon: AlertTriangle },
    { label: 'Usuarios', tab: 'cajeros', icon: Users },
    { label: 'Sistema', tab: 'limites', icon: Settings },
  ];

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h3 className="fintech-panel-title">Panel admin</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>{user.banca || 'Banca'} · resumen operativo</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <div className="glass-panel" style={{ padding: 14 }}>Cajeros<br /><strong>{cashiers.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Ventas<br /><strong>${sales.toFixed(2)}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Pendiente<br /><strong>${pendingPrizes.toFixed(2)}</strong></div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 10 }}>
        {shortcuts.map((shortcut) => {
          const Icon = shortcut.icon;
          return (
            <button key={shortcut.label} type="button" className="btn btn-secondary" onClick={() => onOpen(shortcut.tab)} style={{ justifyContent: 'flex-start' }}>
              <Icon size={16} />
              {shortcut.label}
            </button>
          );
        })}
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Wire into Dashboard**

In `src/views/Dashboard.tsx`, import:

```ts
import { AdminOperationsConsole } from '../components/admin/AdminOperationsConsole';
```

Inside dashboard tab, render for ADMIN:

```tsx
{user.role === 'ADMIN' && (
  <AdminOperationsConsole user={user} users={users} tickets={tickets} onOpen={(tab) => setActiveTab?.(tab)} />
)}
```

- [ ] **Step 3: Build**

Run:

```bash
npm run build
```

Expected: PASS.

---

## Task 6: Monitoreo ADMIN Con Sheet Filtrado Por Cajero

**Files:**
- Create: `src/utils/cashierScope.ts`
- Create: `src/utils/cashierScope.test.ts`
- Create: `src/components/admin/CashierOperationSheet.tsx`
- Modify: `src/views/Dashboard.tsx`

- [ ] **Step 1: Write failing scope tests**

Create `src/utils/cashierScope.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import type { TicketRecord, UserAccount } from '../types';
import { canEditCashierFromMonitoring, getCashierScopedTickets } from './cashierScope';

const cashier: UserAccount = {
  id: 'caj-1',
  user: 'caj01',
  role: 'CASHIER',
  displayName: 'Cajero Uno',
  active: true,
  supervisorIds: [],
  supervisorUsers: [],
};

const tickets = [
  { id: 't1', sellerUser: 'caj01', total: 100, status: 'active' },
  { id: 't2', sellerUser: 'caj02', total: 200, status: 'active' },
  { id: 't3', cashierId: 'caj-1', sellerUser: 'otro', total: 300, status: 'winner' },
] as TicketRecord[];

describe('cashierScope', () => {
  it('filters tickets to the selected cashier only', () => {
    expect(getCashierScopedTickets(tickets, cashier).map((ticket) => ticket.id)).toEqual(['t1', 't3']);
  });

  it('lets ADMIN edit the selected cashier operational blocks but keeps SUPERVISOR read-only', () => {
    expect(canEditCashierFromMonitoring('ADMIN')).toBe(true);
    expect(canEditCashierFromMonitoring('SUPERVISOR')).toBe(false);
    expect(canEditCashierFromMonitoring('MASTER')).toBe(false);
  });
});
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
npm test -- src/utils/cashierScope.test.ts
```

Expected: FAIL because `cashierScope.ts` does not exist.

- [ ] **Step 3: Implement cashier scope helpers**

Create `src/utils/cashierScope.ts`:

```ts
import type { TicketRecord, UserAccount, UserRole } from '../types';

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

export const getCashierScopedTickets = (tickets: TicketRecord[], cashier: UserAccount): TicketRecord[] => {
  const cashierKeys = new Set([cashier.id, cashier.user].map(normalizeKey).filter(Boolean));
  return tickets.filter((ticket) => {
    const ticketKeys = [ticket.cashierId, ticket.sellerUser, ticket.user, ticket.cashierUser].map(normalizeKey).filter(Boolean);
    return ticketKeys.some((key) => cashierKeys.has(key));
  });
};

export const getCashierScopedWinners = (tickets: TicketRecord[], cashier: UserAccount): TicketRecord[] => {
  return getCashierScopedTickets(tickets, cashier).filter((ticket) => {
    return ticket.status === 'winner' || ticket.status === 'paid' || Number(ticket.totalPrize || 0) > 0;
  });
};

export const canEditCashierFromMonitoring = (role: UserRole | string | null | undefined): boolean => {
  return String(role || '').toUpperCase() === 'ADMIN';
};
```

- [ ] **Step 4: Create operational sheet**

Create `src/components/admin/CashierOperationSheet.tsx`:

```tsx
import React, { useMemo, useState } from 'react';
import { X } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';
import { canEditCashierFromMonitoring, getCashierScopedTickets, getCashierScopedWinners } from '../../utils/cashierScope';

interface Props {
  role: string;
  cashier: UserAccount | null;
  tickets: TicketRecord[];
  onClose: () => void;
  onOpenLimits: (cashier: UserAccount) => void;
  onOpenRecharge: (cashier: UserAccount) => void;
}

type SheetTab = 'summary' | 'tickets' | 'payouts' | 'limits' | 'recharges' | 'data';

export const CashierOperationSheet: React.FC<Props> = ({ role, cashier, tickets, onClose, onOpenLimits, onOpenRecharge }) => {
  const [tab, setTab] = useState<SheetTab>('summary');
  const scopedTickets = useMemo(() => (cashier ? getCashierScopedTickets(tickets, cashier) : []), [cashier, tickets]);
  const winners = useMemo(() => (cashier ? getCashierScopedWinners(tickets, cashier) : []), [cashier, tickets]);
  const canEdit = canEditCashierFromMonitoring(role);

  if (!cashier) return null;

  const sales = scopedTickets.reduce((sum, ticket) => sum + Number(ticket.total || 0), 0);
  const pendingPayout = winners.filter((ticket) => ticket.status !== 'paid').reduce((sum, ticket) => sum + Number(ticket.totalPrize || 0), 0);

  return (
    <div className="sheet-backdrop" role="dialog" aria-modal="true">
      <section className="operation-sheet">
        <header className="sheet-header">
          <div>
            <h3>{cashier.displayName || cashier.user}</h3>
            <span>{cashier.banca || 'Banca'} · @{cashier.user}</span>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar">
            <X size={18} />
          </button>
        </header>

        <nav className="sheet-tabs">
          {[
            ['summary', 'Resumen'],
            ['tickets', 'Tickets'],
            ['payouts', 'Cobros'],
            ['limits', 'Límites'],
            ['recharges', 'Recargas'],
            ['data', 'Datos'],
          ].map(([id, label]) => (
            <button key={id} type="button" className={tab === id ? 'active' : ''} onClick={() => setTab(id as SheetTab)}>
              {label}
            </button>
          ))}
        </nav>

        {tab === 'summary' && (
          <div className="sheet-grid">
            <div>Tickets<br /><strong>{scopedTickets.length}</strong></div>
            <div>Ventas<br /><strong>${sales.toFixed(2)}</strong></div>
            <div>Pendiente cobro<br /><strong>${pendingPayout.toFixed(2)}</strong></div>
          </div>
        )}

        {tab === 'tickets' && scopedTickets.map((ticket) => (
          <div key={ticket.id} className="sheet-row">
            <strong>{ticket.id}</strong>
            <span>${Number(ticket.total || 0).toFixed(2)} · {ticket.status}</span>
          </div>
        ))}

        {tab === 'payouts' && winners.map((ticket) => (
          <div key={ticket.id} className="sheet-row">
            <strong>{ticket.id}</strong>
            <span>${Number(ticket.totalPrize || 0).toFixed(2)} · {ticket.status}</span>
          </div>
        ))}

        {tab === 'limits' && (
          <div className="sheet-action-block">
            <span>Límites operacionales de este cajero</span>
            <button type="button" className="btn btn-primary" disabled={!canEdit} onClick={() => onOpenLimits(cashier)}>Administrar límites</button>
          </div>
        )}

        {tab === 'recharges' && (
          <div className="sheet-action-block">
            <span>Cupo y acceso de recargas de este cajero</span>
            <button type="button" className="btn btn-primary" disabled={!canEdit} onClick={() => onOpenRecharge(cashier)}>Administrar recargas</button>
          </div>
        )}

        {tab === 'data' && (
          <div className="sheet-row">
            <strong>Estado</strong>
            <span>{cashier.active === false ? 'Bloqueado' : 'Activo'}</span>
          </div>
        )}
      </section>
    </div>
  );
};
```

- [ ] **Step 5: Wire card clicks**

In `src/views/Dashboard.tsx`, when rendering monitoring cashier cards:

```tsx
const [selectedMonitorCashier, setSelectedMonitorCashier] = useState<UserAccount | null>(null);
```

Add to each cashier card:

```tsx
onClick={() => setSelectedMonitorCashier(cashier)}
role="button"
tabIndex={0}
```

Render the sheet near the bottom of the monitoring view:

```tsx
<CashierOperationSheet
  role={user.role}
  cashier={selectedMonitorCashier}
  tickets={tickets}
  onClose={() => setSelectedMonitorCashier(null)}
  onOpenLimits={(cashier) => {
    setSelectedMonitorCashier(null);
    setSelectedCashierForLimits(cashier);
    setActiveTab?.('limites');
  }}
  onOpenRecharge={(cashier) => {
    setSelectedMonitorCashier(null);
    setSelectedCashierForRecharge(cashier);
    setActiveTab?.('finanzas');
  }}
/>
```

If `Dashboard.tsx` uses different state names, map these calls to the existing limits/recargas handlers instead of creating duplicate flows.

- [ ] **Step 6: Verify scope and build**

Run:

```bash
npm test -- src/utils/cashierScope.test.ts
npm run build
```

Expected: PASS.

---

## Task 7: Comisiones Visibles Por Usuario Para ADMIN

**Files:**
- Create: `src/components/admin/AdminCommissionsPanel.tsx`
- Modify: `src/components/admin/AdminOperationsConsole.tsx`
- Modify: `src/views/Dashboard.tsx`
- Modify: `src/utils/adminCommands.ts`
- Test: `src/utils/userMapping.test.ts`

- [ ] **Step 1: Confirm commission mapping test**

Add to `src/utils/userMapping.test.ts`:

```ts
it('keeps user commission as decimal storage and percent UI value', () => {
  expect(toCommissionPercent(0.12)).toBe(12);
  expect(fromCommissionPercent(12)).toBe(0.12);
});
```

If the helper names already differ, use the existing decimal-to-percent helper names from `src/utils/userMapping.ts`.

- [ ] **Step 2: Create commissions panel**

Create `src/components/admin/AdminCommissionsPanel.tsx`:

```tsx
import React, { useMemo, useState } from 'react';
import type { UserAccount } from '../../types';

interface Props {
  admin: UserAccount;
  users: UserAccount[];
  onSaveCommission: (target: UserAccount, commissionRate: number) => Promise<void> | void;
}

const toPercent = (rate: number | null | undefined): string => String(Number(((rate || 0) * 100).toFixed(2)));
const fromPercent = (value: string): number => Math.max(0, Number(value || 0)) / 100;

export const AdminCommissionsPanel: React.FC<Props> = ({ admin, users, onSaveCommission }) => {
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const scopedUsers = useMemo(() => {
    return users.filter((user) => {
      if (user.role !== 'CASHIER' && user.role !== 'SUPERVISOR') return false;
      return user.adminId === admin.id || user.adminUser === admin.user || user.banca === admin.banca;
    });
  }, [admin, users]);

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <h3 className="fintech-panel-title">Comisiones</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Cajeros y supervisores de {admin.banca || admin.user}</span>
      </div>

      {scopedUsers.map((target) => {
        const draft = drafts[target.id] ?? toPercent(target.commissionRate);
        return (
          <div key={target.id} className="inline-edit-row">
            <div>
              <strong>{target.displayName || target.user}</strong>
              <span>{target.role === 'SUPERVISOR' ? 'Supervisor' : 'Cajero'} · @{target.user}</span>
            </div>
            <label>
              <span>Comisión %</span>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={draft}
                onChange={(event) => setDrafts((current) => ({ ...current, [target.id]: event.target.value }))}
              />
            </label>
            <button type="button" className="btn btn-primary" onClick={() => onSaveCommission(target, fromPercent(draft))}>
              Guardar
            </button>
          </div>
        );
      })}
    </div>
  );
};
```

- [ ] **Step 3: Add visible shortcut in ADMIN dashboard**

Modify `src/components/admin/AdminOperationsConsole.tsx` shortcuts:

```ts
{ label: 'Comisiones', tab: 'comisiones', icon: Percent }
```

Import `Percent` from `lucide-react`.

- [ ] **Step 4: Add tab permission**

Modify `src/utils/roleParity.ts` so ADMIN visible tabs include `comisiones` after `supervisores`. MASTER and SUPERVISOR must not include it.

Update `src/utils/roleParity.test.ts` expected ADMIN array to include:

```ts
'comisiones',
```

- [ ] **Step 5: Render commissions tab and save through Edge Function**

In `src/views/Dashboard.tsx`, import:

```ts
import { AdminCommissionsPanel } from '../components/admin/AdminCommissionsPanel';
```

Add render branch:

```tsx
{activeTab === 'comisiones' && user.role === 'ADMIN' && (
  <AdminCommissionsPanel
    admin={user}
    users={users}
    onSaveCommission={async (target, commissionRate) => {
      await runAdminUserCommand(user, 'update_user_commission', target.id, { commissionRate });
      await refreshUsers();
    }}
  />
)}
```

If `Dashboard.tsx` names the refresh function differently, call the existing users reload method used after cashier/supervisor edits.

- [ ] **Step 6: Verify commissions**

Run:

```bash
npm test -- src/utils/userMapping.test.ts src/utils/roleParity.test.ts
npm run build
```

Expected: PASS.

---

## Task 8: Estructura Administrativa Tipo VoloRed

**Files:**
- Create: `src/utils/lotteryLimitStructure.ts`
- Create: `src/utils/lotteryLimitStructure.test.ts`
- Create: `src/components/admin/AdminLotteryLimitsPanel.tsx`
- Create: `src/components/admin/ClosingAutomationPanel.tsx`
- Modify: `src/utils/masterConfig.ts`
- Modify: `src/utils/roleParity.ts`
- Modify: `src/views/Dashboard.tsx`
- Modify: `docs/admin-parity-map.md`

**Source reference:** VoloRed publica como ejes administrativos: administración en tiempo real, multi-lotería, números ganadores automáticos, límites por loterías/punto de venta/general y envío automático de listado al cierre. La web debe tomar esa estructura, pero respetando la jerarquía real de LotteryNet y sin convertir la web en POS.

- [ ] **Step 1: Write failing limit hierarchy tests**

Create `src/utils/lotteryLimitStructure.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import {
  resolveEffectiveLotteryLimit,
  type LotteryLimitStructure,
} from './lotteryLimitStructure';

const limits: LotteryLimitStructure = {
  global: { maxTicketAmount: 1000, maxPlayAmount: 200 },
  byLottery: {
    'lot-nacional': { maxTicketAmount: 700, maxPlayAmount: 100 },
  },
  byCashier: {
    'caj-1': { maxTicketAmount: 500 },
  },
  byPlay: {
    'lot-nacional:quiniela:12': { maxPlayAmount: 25 },
  },
};

describe('lotteryLimitStructure', () => {
  it('resolves limits by priority play > cashier > lottery > global', () => {
    expect(resolveEffectiveLotteryLimit(limits, {
      lotteryId: 'lot-nacional',
      cashierId: 'caj-1',
      playType: 'quiniela',
      playValue: '12',
    })).toEqual({
      maxTicketAmount: 500,
      maxPlayAmount: 25,
    });
  });

  it('falls back to lottery and global when cashier/play are missing', () => {
    expect(resolveEffectiveLotteryLimit(limits, {
      lotteryId: 'lot-nacional',
      cashierId: 'caj-2',
      playType: 'pale',
      playValue: '12-34',
    })).toEqual({
      maxTicketAmount: 700,
      maxPlayAmount: 100,
    });
  });
});
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
npm test -- src/utils/lotteryLimitStructure.test.ts
```

Expected: FAIL because `lotteryLimitStructure.ts` does not exist.

- [ ] **Step 3: Implement limit hierarchy contract**

Create `src/utils/lotteryLimitStructure.ts`:

```ts
export interface LotteryLimitRule {
  maxTicketAmount?: number;
  maxPlayAmount?: number;
  maxPayoutAmount?: number;
  disabled?: boolean;
}

export interface LotteryLimitStructure {
  global: LotteryLimitRule;
  byLottery: Record<string, LotteryLimitRule>;
  byCashier: Record<string, LotteryLimitRule>;
  byPlay: Record<string, LotteryLimitRule>;
}

export interface LotteryLimitLookup {
  lotteryId?: string;
  cashierId?: string;
  playType?: string;
  playValue?: string;
}

export const emptyLotteryLimitStructure: LotteryLimitStructure = {
  global: {},
  byLottery: {},
  byCashier: {},
  byPlay: {},
};

const normalizeKey = (value: string | null | undefined): string => String(value || '').trim().toLowerCase();

export const buildPlayLimitKey = (lotteryId?: string, playType?: string, playValue?: string): string => {
  return [lotteryId, playType, playValue].map(normalizeKey).join(':');
};

export const resolveEffectiveLotteryLimit = (
  limits: LotteryLimitStructure,
  lookup: LotteryLimitLookup,
): LotteryLimitRule => {
  const lotteryRule = limits.byLottery[normalizeKey(lookup.lotteryId)] || {};
  const cashierRule = limits.byCashier[normalizeKey(lookup.cashierId)] || {};
  const playRule = limits.byPlay[buildPlayLimitKey(lookup.lotteryId, lookup.playType, lookup.playValue)] || {};

  return {
    ...limits.global,
    ...lotteryRule,
    ...cashierRule,
    ...playRule,
  };
};
```

- [ ] **Step 4: Add master config helpers**

Modify `src/utils/masterConfig.ts`:

```ts
import { emptyLotteryLimitStructure, type LotteryLimitStructure } from './lotteryLimitStructure';

export const getLotteryLimitStructure = async (): Promise<LotteryLimitStructure> => {
  return getMasterConfig<LotteryLimitStructure>('lottery_limit_structure_v1', emptyLotteryLimitStructure);
};

export const saveLotteryLimitStructure = async (limits: LotteryLimitStructure): Promise<void> => {
  await saveMasterConfig('lottery_limit_structure_v1', limits);
};
```

This key becomes the web contract for VoloRed-style limits. Existing Android limit keys stay readable as fallback until Android is updated to this structure.

- [ ] **Step 5: Create AdminLotteryLimitsPanel**

Create `src/components/admin/AdminLotteryLimitsPanel.tsx`:

```tsx
import React from 'react';
import type { UserAccount } from '../../types';
import type { LotteryLimitStructure } from '../../utils/lotteryLimitStructure';

interface Props {
  admin: UserAccount;
  cashiers: UserAccount[];
  limits: LotteryLimitStructure;
  onChange: (limits: LotteryLimitStructure) => void;
  onSave: () => void;
}

export const AdminLotteryLimitsPanel: React.FC<Props> = ({ admin, cashiers, limits, onChange, onSave }) => {
  const updateGlobal = (field: 'maxTicketAmount' | 'maxPlayAmount', value: string) => {
    onChange({
      ...limits,
      global: { ...limits.global, [field]: Number(value || 0) },
    });
  };

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div>
        <h3 className="fintech-panel-title">Límites de jugadas</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>
          General · lotería · cajero · jugada
        </span>
      </div>

      <section className="glass-panel" style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
        <label>
          <span>Límite ticket general</span>
          <input className="form-input" type="number" value={limits.global.maxTicketAmount || ''} onChange={(event) => updateGlobal('maxTicketAmount', event.target.value)} />
        </label>
        <label>
          <span>Límite jugada general</span>
          <input className="form-input" type="number" value={limits.global.maxPlayAmount || ''} onChange={(event) => updateGlobal('maxPlayAmount', event.target.value)} />
        </label>
        <button type="button" className="btn btn-primary" onClick={onSave}>Guardar límites generales</button>
      </section>

      <section className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <strong>Punto de venta / cajero</strong>
        {cashiers.map((cashier) => (
          <div key={cashier.id} className="inline-edit-row">
            <span>{cashier.displayName || cashier.user}</span>
            <button type="button" className="btn btn-secondary">Editar límites del cajero</button>
          </div>
        ))}
      </section>

      <section className="glass-panel" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <strong>Loterías y jugadas</strong>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.82rem' }}>
          Aquí se editan límites por lotería y por combinación específica sin tocar el límite general.
        </span>
        <button type="button" className="btn btn-secondary">Agregar límite por lotería</button>
        <button type="button" className="btn btn-secondary">Agregar límite por jugada</button>
      </section>
    </div>
  );
};
```

Replace placeholder edit buttons with the existing modal/sheet pattern if `Dashboard.tsx` already has limit editors.

- [ ] **Step 6: Create closing/list automation panel**

Create `src/components/admin/ClosingAutomationPanel.tsx`:

```tsx
import React from 'react';

interface Props {
  emailEnabled: boolean;
  whatsappEnabled: boolean;
  onToggleEmail: (enabled: boolean) => void;
  onToggleWhatsapp: (enabled: boolean) => void;
  onSave: () => void;
}

export const ClosingAutomationPanel: React.FC<Props> = ({ emailEnabled, whatsappEnabled, onToggleEmail, onToggleWhatsapp, onSave }) => {
  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <h3 className="fintech-panel-title">Cierre y listado automático</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>
          Snapshot al cierre de lotería y envío de listado operativo
        </span>
      </div>
      <label className="inline-edit-row">
        <span>Enviar listado por email al cierre</span>
        <input type="checkbox" checked={emailEnabled} onChange={(event) => onToggleEmail(event.target.checked)} />
      </label>
      <label className="inline-edit-row">
        <span>Enviar listado por WhatsApp al cierre</span>
        <input type="checkbox" checked={whatsappEnabled} onChange={(event) => onToggleWhatsapp(event.target.checked)} />
      </label>
      <button type="button" className="btn btn-primary" onClick={onSave}>Guardar automatización</button>
    </div>
  );
};
```

The first implementation only manages configuration. Sending email/WhatsApp requires backend worker or Edge Function and must be tested separately before production.

- [ ] **Step 7: Add ADMIN tabs**

Modify `src/utils/roleParity.ts` ADMIN tabs to include:

```ts
'limites',
'cierres',
```

Keep `limites` as the existing route and add `cierres` near `resultados` or `reportes`.

Update `src/utils/roleParity.test.ts` expected ADMIN array with:

```ts
'cierres',
```

MASTER does not get POS-style closing controls. SUPERVISOR can view reports/cierre results through reportes, not configure automation.

- [ ] **Step 8: Wire panels into Dashboard**

In `src/views/Dashboard.tsx`, render:

```tsx
{activeTab === 'limites' && user.role === 'ADMIN' && (
  <AdminLotteryLimitsPanel
    admin={user}
    cashiers={adminCashiers}
    limits={lotteryLimitStructure}
    onChange={setLotteryLimitStructure}
    onSave={handleSaveLotteryLimitStructure}
  />
)}

{activeTab === 'cierres' && user.role === 'ADMIN' && (
  <ClosingAutomationPanel
    emailEnabled={closingAutomation.emailEnabled}
    whatsappEnabled={closingAutomation.whatsappEnabled}
    onToggleEmail={(emailEnabled) => setClosingAutomation((current) => ({ ...current, emailEnabled }))}
    onToggleWhatsapp={(whatsappEnabled) => setClosingAutomation((current) => ({ ...current, whatsappEnabled }))}
    onSave={handleSaveClosingAutomation}
  />
)}
```

If `Dashboard.tsx` already has a limits branch, embed `AdminLotteryLimitsPanel` at the top and keep legacy limits underneath until migrated.

- [ ] **Step 9: Update parity docs**

Modify `docs/admin-parity-map.md`:

```md
## Referencia VoloRed Aplicada

La web adopta la estructura administrativa publicada por VoloRed para sistemas de lotería: administración en tiempo real, multi-lotería, resultados automáticos, límites por lotería/punto de venta/general y listado automático al cierre.

En LotteryNet esto se implementa por jerarquía:
- MASTER configura bancas, credenciales, servidor, recargas master y auditoría.
- ADMIN configura límites generales, por lotería, por cajero y por jugada, además de cierre/listado.
- SUPERVISOR solo consulta operación de cajeros asignados.
```

- [ ] **Step 10: Verify**

Run:

```bash
npm test -- src/utils/lotteryLimitStructure.test.ts src/utils/roleParity.test.ts
npm run build
```

Expected: PASS.

---

## Task 9: SUPERVISOR Console Parity

**Files:**
- Create: `src/components/supervisor/SupervisorConsole.tsx`
- Modify: `src/views/Dashboard.tsx`

- [ ] **Step 1: Create supervisor console**

Create `src/components/supervisor/SupervisorConsole.tsx`:

```tsx
import React from 'react';
import { Activity, BarChart3, ReceiptText, Trophy, Users } from 'lucide-react';
import type { TicketRecord, UserAccount } from '../../types';

interface Props {
  user: UserAccount;
  users: UserAccount[];
  tickets: TicketRecord[];
  onOpen: (tab: string) => void;
}

export const SupervisorConsole: React.FC<Props> = ({ user, users, tickets, onOpen }) => {
  const assignedCashiers = users.filter((candidate) => candidate.role === 'CASHIER' && candidate.supervisorIds.includes(user.id));
  const assignedUsers = assignedCashiers.map((cashier) => cashier.user);
  const scopedTickets = tickets.filter((ticket) => assignedUsers.includes(ticket.sellerUser || ''));
  const sales = scopedTickets.filter((ticket) => ticket.status !== 'cancelled' && ticket.status !== 'voided').reduce((sum, ticket) => sum + ticket.total, 0);

  const actions = [
    { label: 'Mis cajeros', tab: 'monitoreo', icon: Users },
    { label: 'Monitoreo', tab: 'monitoreo', icon: Activity },
    { label: 'Finanzas', tab: 'finanzas', icon: BarChart3 },
    { label: 'Reporte', tab: 'reportes', icon: BarChart3 },
    { label: 'Tickets', tab: 'tickets', icon: ReceiptText },
    { label: 'Resultados', tab: 'resultados', icon: Trophy },
  ];

  return (
    <div className="fintech-panel fintech-primary-panel" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h3 className="fintech-panel-title">Supervisión</h3>
        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Cajeros asignados y operación del grupo</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <div className="glass-panel" style={{ padding: 14 }}>Mis cajeros<br /><strong>{assignedCashiers.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Tickets grupo<br /><strong>{scopedTickets.length}</strong></div>
        <div className="glass-panel" style={{ padding: 14 }}>Ventas grupo<br /><strong>${sales.toFixed(2)}</strong></div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10 }}>
        {actions.map((action) => {
          const Icon = action.icon;
          return (
            <button key={action.label} type="button" className="btn btn-secondary" onClick={() => onOpen(action.tab)} style={{ justifyContent: 'flex-start' }}>
              <Icon size={16} />
              {action.label}
            </button>
          );
        })}
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Wire into Dashboard**

In `src/views/Dashboard.tsx`, import:

```ts
import { SupervisorConsole } from '../components/supervisor/SupervisorConsole';
```

Inside dashboard tab, render for SUPERVISOR:

```tsx
{user.role === 'SUPERVISOR' && (
  <SupervisorConsole user={user} users={users} tickets={tickets} onOpen={(tab) => setActiveTab?.(tab)} />
)}
```

- [ ] **Step 3: Build**

Run:

```bash
npm run build
```

Expected: PASS.

---

## Task 10: Convertir Acciones MASTER A Comandos Reales

**Files:**
- Modify: `src/components/master/MasterBankPanel.tsx`
- Modify: `src/components/master/MasterCredentialsPanel.tsx`
- Modify: `src/components/master/MasterRechargePanel.tsx`
- Modify: `src/utils/adminCommands.ts`
- Test: `src/utils/adminCommands.test.ts`

- [ ] **Step 1: Extend command tests**

Add tests in `src/utils/adminCommands.test.ts` for these actions:

```ts
it('sends regenerate bank credentials through admin-user-command', async () => {
  await runAdminUserCommand(
    { id: 'master', user: 'master', role: 'MASTER' },
    'regenerate_bank_credentials',
    'admin-1',
    { includeCashiers: true },
  );

  expect(invokeMock).toHaveBeenCalledWith('admin-user-command', expect.objectContaining({
    body: expect.objectContaining({
      action: 'regenerate_bank_credentials',
      targetId: 'admin-1',
      payload: { includeCashiers: true },
    }),
  }));
});

it('sends reset user password through admin-user-command', async () => {
  await runAdminUserCommand(
    { id: 'master', user: 'master', role: 'MASTER' },
    'reset_user_password',
    'cashier-1',
    { password: 'nueva123' },
  );

  expect(invokeMock).toHaveBeenCalledWith('admin-user-command', expect.objectContaining({
    body: expect.objectContaining({
      action: 'reset_user_password',
      targetId: 'cashier-1',
      payload: { password: 'nueva123' },
    }),
  }));
});
```

- [ ] **Step 2: Run tests**

Run:

```bash
npm test -- src/utils/adminCommands.test.ts
```

Expected: PASS if current generic command client already supports payloads.

- [ ] **Step 3: Wire buttons**

In `MasterBankPanel`, button actions must call props instead of doing nothing:

```tsx
onToggleBank(admin)
onRegenerateCredentials(admin)
onAddCashiers(admin)
onDeleteBank(admin)
```

Add these props:

```ts
onToggleBank: (admin: UserAccount) => void;
onRegenerateCredentials: (admin: UserAccount) => void;
onAddCashiers: (admin: UserAccount) => void;
onDeleteBank: (admin: UserAccount) => void;
```

Then wire them from `Dashboard.tsx` to existing handlers already present where possible:
- `handleToggleAdminStatus`
- `handleRegenerateBankCredentials`
- `handleDeleteAdmin`
- existing create/add cashier modal flow

- [ ] **Step 4: Build and smoke test**

Run:

```bash
npm run build
npm test -- src/utils/adminCommands.test.ts src/utils/roleParity.test.ts src/utils/navigationPermissions.test.ts
```

Expected: PASS.

---

## Task 11: QA Por Perfil

**Files:**
- Modify: `docs/admin-parity-map.md`

- [ ] **Step 1: Browser QA MASTER**

Login: `master / pass123`.

Expected visible sidebar:
- Resumen
- Bancas y Admins
- Auditoría

Expected inside Resumen:
- Panel Master
- tabs internos Bancas, Credenciales, Servidor/Nube, Recargas Master, Auditoría

- [ ] **Step 2: Browser QA ADMIN**

Login with ADMIN credential from `C:\Users\Randy Cordero\Documents\LotteryNet-Secrets` if available.

Expected visible sidebar:
- Resumen
- Cajeros & Red
- Supervisores
- Comisiones
- Monitoreo Red
- Venta Deportiva solo si `master_sportsbook_settings` habilita ADMIN, banca/admin o usuario
- Tickets Emitidos
- Cobro de Premios
- Resultados Sorteos
- Límites de Juego
- Cierre y Listado
- Balances y Recargas
- Cuadre de Caja
- Reportes
- Auditoría

Expected dashboard:
- Panel admin
- resumen operativo
- accesos críticos
- accesos secundarios

- [ ] **Step 3: Browser QA SUPERVISOR**

Login with SUPERVISOR credential from secrets if available.

Expected visible sidebar:
- Resumen
- Monitoreo Red
- Venta Deportiva solo si `master_sportsbook_settings` habilita SUPERVISOR o usuario
- Tickets Emitidos
- Resultados Sorteos
- Balances y Recargas
- Cuadre de Caja
- Reportes

Expected dashboard:
- Supervisión
- Mis cajeros
- Monitoreo
- Finanzas
- Reporte
- Tickets
- Resultados

- [ ] **Step 4: Update parity document**

Update `docs/admin-parity-map.md`:

```md
## Paridad Real Revisada

MASTER se alineó a `MasterDashboardActivity`: Bancas, Credenciales, Servidor/Nube, Recargas Master y Auditoría.
ADMIN se alineó a `AdminDashboardActivity`, `AdminLimitsActivity` y Shell admin sin POS web.
ADMIN incluye comisiones visibles por cajero/supervisor y monitoreo accionable por cajero con sheet filtrado.
ADMIN adopta estructura tipo VoloRed para límites por general/lotería/cajero/jugada y cierre/listado automático.
SUPERVISOR se alineó a Shell supervisor con alcance de cajeros asignados.
```

- [ ] **Step 5: Final verification**

Run:

```bash
npm test -- src/utils/roleParity.test.ts src/utils/navigationPermissions.test.ts src/utils/adminCommands.test.ts
npm run build
```

Expected:
- Tests pass.
- Build passes.
- Vite may warn about large chunks; that warning is acceptable.

---

## Residual Gaps After This Plan

- MASTER Recargas Rápidas wallet needs real backend call parity if not already exposed in Supabase.
- MASTER cloud sync should eventually mirror `MasterCloudSyncCoordinator` completely.
- ADMIN `Sistema` needs a dedicated web panel comparable to `AdminConfigActivity`, instead of living partly under Límites.
- SUPERVISOR financial/report views need comparison against `FinanceActivity` calculations with Android tests.
- Deportes must stay conditional per `master_sportsbook_settings`, not hard-coded by role.
- RR por usuario/cajero is a web extension over Android's default/admin scopes; Android must be taught this same `byUser` priority before claiming 100% cross-device parity for individual RR accounts.
- Recargas sales stay Android-only for CASHIER/ADMIN; web should manage permissions/balances, not sell recargas.
- Web still intentionally excludes POS sale, thermal printing, and cashier full workflow.
