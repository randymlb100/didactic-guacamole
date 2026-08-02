# Prueba Nocturna de Capacidad para 150 Clientes - Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Demostrar, sin arriesgar producción, que el nuevo flujo de tickets soporta 150 clientes simultáneos vendiendo una vez cada 30 segundos —5 ventas por segundo— durante 30 minutos sin bloqueos, timeouts, pérdida ni duplicación.

**Architecture:** La carga se ejecuta contra un proyecto de staging o una rama Supabase aislada, nunca contra `main` de producción. Cada cliente virtual escribe un ticket pequeño e idempotente mediante `create-ticket-v2`; la lectura usa deltas y ninguna operación reconstruye snapshots JSONB completos. La prueba aumenta por escalones y se detiene automáticamente ante errores HTTP o degradación de latencia; PostgreSQL se vigila paralelamente mediante consultas de solo lectura.

**Tech Stack:** Node.js 24, Supabase Edge Functions, Supabase Postgres 17, `pg_stat_activity`, `pg_stat_database`, `pg_stat_statements`, PowerShell.

---

## Decisión de seguridad

La recomendación oficial de Supabase es realizar pruebas de carga preferiblemente en staging. Por tanto:

- La prueba completa de 150 clientes no se ejecutará en el proyecto productivo `unhoulkujbtsypccpirc`.
- Staging debe utilizar la misma migración, versiones de Edge Functions y una capacidad de cómputo comparable.
- Producción solamente recibirá un canary real y gradual después de aprobar staging.
- Los cron 3 y 6 permanecerán apagados durante la prueba.
- La compuerta de emergencia de snapshots permanecerá activa hasta aprobar todas las fases.
- No se ejecutará `EXPLAIN ANALYZE` sobre escrituras.
- No se cambiarán límites de conexiones para ocultar consultas ineficientes.
- No se utilizarán ventas, fechas, usuarios ni sorteos reales.

Documentación de referencia:

- [Supabase Production Checklist](https://supabase.com/docs/guides/deployment/going-into-prod)
- [Supabase Performance Tuning](https://supabase.com/docs/guides/platform/performance)
- [Supabase Connection Management](https://supabase.com/docs/guides/database/connection-management)

## Archivos de la prueba

- Crear: `tools/qa/ticket-sync-150-client-load.mjs`
  - Generador de los 150 clientes virtuales.
  - Control de ritmo, timeout, percentiles y freno automático.
- Crear: `tools/qa/ticket-sync-150-client-load.node.test.mjs`
  - Contratos de seguridad del generador.
- Crear: `tools/qa/ticket-sync-load-monitor.sql`
  - Consultas de solo lectura para conexiones, locks, WAL y checkpoints.
- Crear: `tools/qa/ticket-sync-load-cleanup.sql`
  - Limpieza limitada al `run_id` en staging.
- Crear: `tools/qa/ticket-sync-load-integrity.sql`
  - Verificación de duplicados y items huérfanos para un `run_id`.
- Crear durante cada ejecución: ``tools/qa/load-artifacts/ticket-sync-150-${stamp}.json``
  - Resultado completo y métricas.
- Crear durante cada ejecución: ``tools/qa/load-artifacts/ticket-sync-150-${stamp}.md``
  - Informe legible de aprobación o rechazo.
- Modificar: `package.json`
  - Añadir comandos explícitos para contrato, smoke y carga.

## Carga exacta

| Fase | Clientes | Frecuencia por cliente | Duración | Ventas aproximadas |
| --- | ---: | ---: | ---: | ---: |
| Preflight | 0 | Solo lectura | 2 min | 0 |
| Smoke | 5 | 1 cada 30 s | 2 min | 20 |
| Escalón A | 15 | 1 cada 30 s | 3 min | 90 |
| Escalón B | 30 | 1 cada 30 s | 5 min | 300 |
| Escalón C | 60 | 1 cada 30 s | 5 min | 600 |
| Escalón D | 100 | 1 cada 30 s | 10 min | 2,000 |
| Objetivo | 150 | 1 cada 30 s | 30 min | 9,000 |

Entre fases habrá cinco minutos sin carga para revisar PostgreSQL. Una fase no autoriza automáticamente la siguiente.

## Criterios obligatorios de aprobación

- Venta p50 menor de 400 ms.
- Venta p95 menor de 750 ms.
- Venta p99 menor de 1.5 segundos.
- Lectura delta p95 menor de 500 ms.
- Cero respuestas HTTP 5xx.
- Errores totales menores de 0.1%.
- Cero timeouts de red o SQL.
- Cero deadlocks.
- Ninguna espera de lock superior a 500 ms.
- Conexiones PostgreSQL por debajo del 60% de `max_connections`.
- WAL promedio menor de 100 KB por venta.
- Ningún checkpoint superior a 30 segundos.
- Cero tickets perdidos.
- Cero tickets duplicados por `client_request_id`.
- Cero escrituras en `lotterynet_tickets_by_owner` producidas por la venta.
- SQL de salud responde en menos de tres segundos durante toda la prueba.

Si falla un solo criterio obligatorio, el resultado será `NO APROBADO`.

## Condiciones de parada inmediata

El generador debe detener nuevas ventas cuando ocurra cualquiera de estas condiciones:

- una respuesta 500, 502, 503 o 504;
- tres respuestas no exitosas dentro de una ventana de 30 segundos;
- p95 móvil superior a 1.5 segundos durante 60 segundos;
- una solicitud tarda más de cinco segundos;
- más del 1% de errores en cualquier fase;
- la consulta de salud detecta un lock waiter;
- conexiones iguales o superiores al 60%;
- aparece `canceling statement due to statement timeout`;
- Supabase muestra estado `UNHEALTHY`;
- el operador pierde acceso al SQL Editor o al conector.

Acción de parada:

1. Detener el generador con `Ctrl+C`.
2. No iniciar el siguiente escalón.
3. Mantener cron 3 y 6 apagados.
4. Mantener bloqueada la subida de snapshots completos.
5. Capturar logs y métricas.
6. Esperar cinco minutos y ejecutar solamente la consulta de salud.
7. No reiniciar el servidor salvo que PostgreSQL deje de aceptar conexiones.

### Task 1: Construir el contrato de seguridad

**Files:**
- Create: `tools/qa/ticket-sync-150-client-load.node.test.mjs`
- Test: `tools/qa/ticket-sync-150-client-load.node.test.mjs`

- [ ] **Step 1: Escribir el contrato que inicialmente falle**

El test leerá `ticket-sync-150-client-load.mjs` y comprobará:

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(
  new URL("./ticket-sync-150-client-load.mjs", import.meta.url),
  "utf8",
);

test("la carga completa rechaza producción", () => {
  assert.match(source, /ALLOW_PRODUCTION_LOAD_TEST/);
  assert.match(source, /PROJECT_REF\s*===\s*"unhoulkujbtsypccpirc"/);
  assert.match(source, /throw new Error\(.+producci[oó]n/is);
});

test("cada solicitud tiene timeout y clientRequestId único", () => {
  assert.match(source, /AbortSignal\.timeout/);
  assert.match(source, /clientRequestId/);
  assert.match(source, /runId/);
  assert.match(source, /virtualClientId/);
});

test("la carga tiene freno automático", () => {
  assert.match(source, /STOP_HTTP_STATUSES/);
  assert.match(source, /rollingP95/);
  assert.match(source, /abortController\.abort/);
});

test("la salida calcula p50 p95 y p99", () => {
  assert.match(source, /p50Ms/);
  assert.match(source, /p95Ms/);
  assert.match(source, /p99Ms/);
});
```

- [ ] **Step 2: Ejecutar y confirmar el fallo**

```powershell
node --test tools/qa/ticket-sync-150-client-load.node.test.mjs
```

Expected: FAIL porque el generador todavía no existe.

### Task 2: Implementar el generador con límites estrictos

**Files:**
- Create: `tools/qa/ticket-sync-150-client-load.mjs`
- Modify: `package.json`
- Test: `tools/qa/ticket-sync-150-client-load.node.test.mjs`

- [ ] **Step 1: Definir la configuración obligatoria**

El script utilizará exclusivamente estas variables:

```js
const PROJECT_REF = process.env.LOTTERYNET_LOAD_PROJECT_REF ?? "";
const SUPABASE_URL = process.env.LOTTERYNET_LOAD_SUPABASE_URL ?? "";
const API_KEY = process.env.LOTTERYNET_LOAD_PUBLISHABLE_KEY ?? "";
const CREDENTIAL_FILE = process.env.LOTTERYNET_CREDENTIAL_FILE ?? "";
const CLIENTS = Number(process.env.LOTTERYNET_LOAD_CLIENTS ?? 5);
const DURATION_SECONDS = Number(process.env.LOTTERYNET_LOAD_DURATION_SECONDS ?? 120);
const SALE_INTERVAL_MS = Number(process.env.LOTTERYNET_LOAD_SALE_INTERVAL_MS ?? 30000);
const REQUEST_TIMEOUT_MS = 5000;
const ALLOW_PRODUCTION_LOAD_TEST =
  process.env.LOTTERYNET_ALLOW_PRODUCTION_LOAD_TEST === "I_UNDERSTAND_THIS_CAN_HARM_PRODUCTION";

if (PROJECT_REF === "unhoulkujbtsypccpirc" && !ALLOW_PRODUCTION_LOAD_TEST) {
  throw new Error("Prueba de 150 clientes bloqueada en producción.");
}
```

El plan no autoriza establecer `LOTTERYNET_ALLOW_PRODUCTION_LOAD_TEST`.

- [ ] **Step 2: Modelar clientes virtuales estables**

Cada cliente tendrá:

```js
{
  virtualClientId: `client-${String(index + 1).padStart(3, "0")}`,
  cashierSession: sessions[index % sessions.length],
  nextSaleAt: startedAt + Math.floor(Math.random() * SALE_INTERVAL_MS),
}
```

Los logins se realizan antes de iniciar el cronómetro. Las sesiones se reutilizan; la prueba no mezcla capacidad de Auth con capacidad de venta.

- [ ] **Step 3: Generar IDs idempotentes**

Cada venta usará:

```js
const clientRequestId =
  `${runId}-${virtualClient.virtualClientId}-${String(sequence).padStart(6, "0")}`;
```

No se reutilizará un ID excepto en la comprobación explícita de idempotencia posterior.

- [ ] **Step 4: Distribuir las ventas**

El generador no lanzará ráfagas de 150 solicitudes juntas. Cada cliente tendrá un offset inicial aleatorio dentro de los primeros 30 segundos y después realizará una venta cada 30 segundos.

- [ ] **Step 5: Añadir timeout y freno**

Cada `fetch` usará:

```js
signal: AbortSignal.any([
  AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  abortController.signal,
])
```

Los estados críticos serán:

```js
const STOP_HTTP_STATUSES = new Set([500, 502, 503, 504]);
```

Ante un estado crítico o degradación sostenida:

```js
abortController.abort(
  new Error(`Carga detenida: ${reason}`),
);
```

- [ ] **Step 6: Escribir el informe**

El JSON y Markdown incluirán:

```js
{
  runId,
  projectRef: PROJECT_REF,
  clients: CLIENTS,
  durationSeconds: DURATION_SECONDS,
  attempted,
  succeeded,
  failed,
  throughputPerSecond,
  p50Ms,
  p95Ms,
  p99Ms,
  maxMs,
  statusCounts,
  stopReason,
  startedAt,
  finishedAt,
}
```

- [ ] **Step 7: Añadir scripts de package**

```json
{
  "scripts": {
    "qa:ticket-load:contract": "node --test tools/qa/ticket-sync-150-client-load.node.test.mjs",
    "qa:ticket-load": "node tools/qa/ticket-sync-150-client-load.mjs"
  }
}
```

- [ ] **Step 8: Ejecutar el contrato**

```powershell
npm run qa:ticket-load:contract
```

Expected: PASS, 4 tests, 0 failures.

### Task 3: Crear el monitor PostgreSQL

**Files:**
- Create: `tools/qa/ticket-sync-load-monitor.sql`

- [ ] **Step 1: Guardar la consulta de salud**

```sql
set statement_timeout = '3s';

select
  now() at time zone 'America/Santo_Domingo' as checked_at_rd,
  current_setting('max_connections')::int as max_connections,
  count(*) filter (where backend_type = 'client backend') as client_connections,
  round(
    100.0
      * count(*) filter (where backend_type = 'client backend')
      / current_setting('max_connections')::numeric,
    2
  ) as connection_percent,
  count(*) filter (where wait_event_type = 'Lock') as lock_waiters,
  count(*) filter (where state = 'idle in transaction') as idle_in_transaction,
  coalesce(
    max(extract(epoch from (now() - query_start)))
      filter (
        where state = 'active'
          and backend_type = 'client backend'
          and pid <> pg_backend_pid()
      ),
    0
  ) as longest_business_query_seconds
from pg_stat_activity;
```

- [ ] **Step 2: Guardar el snapshot acumulativo**

```sql
select
  now() at time zone 'America/Santo_Domingo' as captured_at_rd,
  pg_current_wal_lsn() as wal_lsn,
  datname,
  xact_commit,
  xact_rollback,
  blks_read,
  blks_hit,
  temp_files,
  temp_bytes,
  deadlocks
from pg_stat_database
where datname = current_database();

select row_to_json(checkpointer_state) as checkpointer
from pg_stat_checkpointer checkpointer_state;
```

- [ ] **Step 3: Guardar las consultas calientes**

```sql
select
  calls,
  round(total_exec_time::numeric, 2) as total_exec_ms,
  round(mean_exec_time::numeric, 2) as mean_exec_ms,
  rows,
  left(query, 220) as query_preview
from pg_stat_statements
where query ilike '%tickets%'
   or query ilike '%lotterynet_tickets_by_owner%'
order by total_exec_time desc
limit 20;
```

### Task 4: Crear verificación y limpieza limitada a staging

**Files:**
- Create: `tools/qa/ticket-sync-load-cleanup.sql`
- Create: `tools/qa/ticket-sync-load-integrity.sql`

- [ ] **Step 1: Crear la verificación por run**

```sql
select
  count(*) as ticket_rows,
  count(distinct client_request_id) as unique_client_requests,
  count(*) - count(distinct client_request_id) as duplicates
from public.tickets
where client_request_id like (:'run_id' || '-%');

select count(*) as orphan_items
from public.ticket_items ti
left join public.tickets t on t.id = ti.ticket_id
where t.id is null;
```

- [ ] **Step 2: Hacer la limpieza transaccional y verificable**

El archivo recibirá `run_id` mediante una variable de `psql`, tomada del informe JSON:

```sql
begin;

create temporary table qa_ticket_ids on commit drop as
select id
from public.tickets
where client_request_id like (:'run_id' || '-%');

select count(*) as tickets_selected_for_cleanup
from qa_ticket_ids;

delete from public.ticket_items
where ticket_id in (select id from qa_ticket_ids);

delete from public.tickets
where id in (select id from qa_ticket_ids);

commit;
```

La ejecución utilizará:

```powershell
$reportFile = Get-ChildItem "tools/qa/load-artifacts/ticket-sync-150-*.json" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
$report = Get-Content -LiteralPath $reportFile.FullName -Raw | ConvertFrom-Json
psql $env:LOTTERYNET_LOAD_DATABASE_URL `
  -v run_id="$($report.runId)" `
  -f tools/qa/ticket-sync-load-cleanup.sql
```

Esta limpieza está prohibida en producción.

### Task 5: Preparar staging antes de la carga

**Files:**
- Verify: Supabase staging project
- Verify: `supabase/functions/create-ticket-v2/index.ts`
- Verify: `supabase/functions/get-ticket-list/index.ts`
- Verify: `supabase/migrations/20260618210500_restore_bounded_ticket_snapshot_trigger.sql`

- [ ] **Step 1: Confirmar que el destino no es producción**

```powershell
$env:LOTTERYNET_LOAD_PROJECT_REF
```

Expected: un project ref diferente de `unhoulkujbtsypccpirc`.

- [ ] **Step 2: Confirmar versiones y migraciones**

Staging debe contener:

- la misma versión de `create-ticket-v2`;
- `get-ticket-list` con la compuerta de snapshot;
- el trigger acotado sin `active_ticket_identifiers`;
- cron 3 y 6 apagados.

- [ ] **Step 3: Ejecutar contratos locales**

```powershell
npm run qa:ticket-load:contract
node --test tools/qa/sale-idempotency-and-delete-contract.node.test.mjs
node --test tools/qa/ticket-hydrate-readonly-contract.node.test.mjs
node --test tools/qa/ticket-snapshot-write-amplification-contract.node.test.mjs
```

Expected: todos PASS.

- [ ] **Step 4: Capturar baseline PostgreSQL**

Ejecutar las tres secciones de `tools/qa/ticket-sync-load-monitor.sql` y guardar:

- WAL LSN inicial;
- conexiones iniciales;
- deadlocks iniciales;
- checkpointer inicial;
- top queries iniciales.

### Task 6: Ejecutar los escalones

**Files:**
- Run: `tools/qa/ticket-sync-150-client-load.mjs`
- Record: `tools/qa/load-artifacts/`

- [ ] **Step 1: Cargar variables comunes**

Las credenciales de staging se cargarán desde el almacén seguro de la sesión. Después se validarán y se derivará la URL:

```powershell
if (-not $env:LOTTERYNET_LOAD_PROJECT_REF) {
  throw "Falta LOTTERYNET_LOAD_PROJECT_REF"
}
if ($env:LOTTERYNET_LOAD_PROJECT_REF -eq "unhoulkujbtsypccpirc") {
  throw "La prueba completa no puede apuntar a producción"
}
if (-not $env:LOTTERYNET_LOAD_PUBLISHABLE_KEY) {
  throw "Falta LOTTERYNET_LOAD_PUBLISHABLE_KEY"
}
if (-not $env:LOTTERYNET_LOAD_DATABASE_URL) {
  throw "Falta LOTTERYNET_LOAD_DATABASE_URL de staging"
}
$env:LOTTERYNET_LOAD_SUPABASE_URL =
  "https://$($env:LOTTERYNET_LOAD_PROJECT_REF).supabase.co"
$env:LOTTERYNET_CREDENTIAL_FILE =
  "C:\Users\Randy Cordero\Documents\LotteryNet-Secrets\contraseña de prueba.txt"
```

Los valores se tomarán del proyecto staging; no se copiará una clave secreta al repositorio.

- [ ] **Step 2: Preflight sin escrituras**

Ejecutar durante dos minutos:

- lectura de configuración;
- login de cuentas QA;
- lectura delta;
- consulta PostgreSQL de salud cada 30 segundos.

Expected:

- 0 respuestas 5xx;
- salud SQL menor de tres segundos;
- 0 lock waiters.

- [ ] **Step 3: Smoke de 5 clientes**

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "5"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "120"
$env:LOTTERYNET_LOAD_SALE_INTERVAL_MS = "30000"
npm run qa:ticket-load
```

Revisar el informe. Esperar cinco minutos.

- [ ] **Step 4: Escalón de 15 clientes**

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "15"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "180"
npm run qa:ticket-load
```

Revisar todos los criterios. Esperar cinco minutos.

- [ ] **Step 5: Escalón de 30 clientes**

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "30"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "300"
npm run qa:ticket-load
```

Revisar todos los criterios. Esperar cinco minutos.

- [ ] **Step 6: Escalón de 60 clientes**

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "60"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "300"
npm run qa:ticket-load
```

Revisar todos los criterios. Esperar cinco minutos.

- [ ] **Step 7: Escalón de 100 clientes**

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "100"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "600"
npm run qa:ticket-load
```

Revisar todos los criterios. Esperar cinco minutos.

- [ ] **Step 8: Objetivo de 150 clientes**

Solo ejecutar si los cinco escalones anteriores fueron aprobados:

```powershell
$env:LOTTERYNET_LOAD_CLIENTS = "150"
$env:LOTTERYNET_LOAD_DURATION_SECONDS = "1800"
npm run qa:ticket-load
```

Durante esta fase, ejecutar la consulta de salud cada 30 segundos.

### Task 7: Verificar integridad después de cada fase

**Files:**
- Inspect: report JSON from `tools/qa/load-artifacts/`

- [ ] **Step 1: Verificar cantidades e idempotencia**

Extraer el `run_id` del informe más reciente y pasarlo a `psql`:

```powershell
$reportFile = Get-ChildItem "tools/qa/load-artifacts/ticket-sync-150-*.json" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
$report = Get-Content -LiteralPath $reportFile.FullName -Raw | ConvertFrom-Json
```

```sql
select
  count(*) as ticket_rows,
  count(distinct client_request_id) as unique_client_requests,
  count(*) - count(distinct client_request_id) as duplicates
from public.tickets
where client_request_id like (:'run_id' || '-%');
```

Expected: `duplicates = 0`.

Ejecutar mediante:

```powershell
psql $env:LOTTERYNET_LOAD_DATABASE_URL `
  -v run_id="$($report.runId)" `
  -f tools/qa/ticket-sync-load-integrity.sql
```

- [ ] **Step 2: Verificar items huérfanos**

```sql
select count(*) as orphan_items
from public.ticket_items ti
left join public.tickets t on t.id = ti.ticket_id
where t.id is null;
```

Expected: `orphan_items = 0`.

- [ ] **Step 3: Verificar que no se escribieron snapshots**

Capturar `updated_at` de `lotterynet_tickets_by_owner` antes y después. Las ventas de la prueba no deben cambiar esas filas.

- [ ] **Step 4: Calcular WAL por venta**

Mantener una sesión `psql` de monitoreo abierta. Antes de la fase:

```sql
select pg_current_wal_lsn() as wal_lsn \gset before_
```

Después de la fase:

```sql
select pg_current_wal_lsn() as wal_lsn \gset after_
select pg_wal_lsn_diff(
  :'after_wal_lsn'::pg_lsn,
  :'before_wal_lsn'::pg_lsn
) as wal_bytes;
```

Dividir `wal_bytes` entre ventas exitosas de la fase.

Expected: menos de 100 KB por venta.

### Task 8: Emitir decisión y limpiar staging

**Files:**
- Create: ``tools/qa/load-artifacts/ticket-sync-150-${stamp}.md``
- Run: `tools/qa/ticket-sync-load-cleanup.sql`

- [ ] **Step 1: Clasificar el resultado**

El informe final debe comenzar con exactamente una de estas líneas:

```text
RESULTADO: APROBADO PARA CANARY
```

o:

```text
RESULTADO: NO APROBADO
```

- [ ] **Step 2: Documentar evidencia**

El informe incluirá:

- cantidad de clientes;
- duración;
- ventas intentadas/exitosas/fallidas;
- throughput;
- p50, p95, p99 y máximo;
- distribución HTTP;
- conexiones máximas;
- lock wait máximo;
- deadlocks;
- WAL por venta;
- checkpoints;
- tickets perdidos o duplicados;
- causa de parada, si existió.

- [ ] **Step 3: Limpiar únicamente los run IDs aprobados**

Ejecutar `tools/qa/ticket-sync-load-cleanup.sql` una vez por `run_id`.

- [ ] **Step 4: Verificar limpieza**

```sql
select count(*)
from public.tickets
where client_request_id like (:'run_id' || '-%');
```

Expected: `0`.

### Task 9: Canary posterior en producción

**Files:**
- Modify: feature flag only
- Do not run: 150-client synthetic load

- [ ] **Step 1: Activar un solo owner interno**

Observar tráfico real durante 30 minutos.

- [ ] **Step 2: Avanzar a 5%**

Requiere:

- cero 5xx;
- cero locks;
- p95 menor de 750 ms;
- SQL saludable.

- [ ] **Step 3: Avanzar a 25%, 50% y 100%**

Esperar al menos 30 minutos entre etapas. Ante cualquier incumplimiento, desactivar el feature flag inmediatamente.

## Definición de terminado

- El contrato del generador pasa.
- Staging reproduce migraciones y funciones de producción.
- Todos los escalones hasta 150 clientes pasan.
- La fase de 150 mantiene 5 ventas por segundo durante 30 minutos.
- No existen 5xx, timeouts, locks, deadlocks, pérdidas ni duplicados.
- El WAL y las conexiones permanecen dentro del presupuesto.
- La limpieza de staging termina con cero tickets del `run_id`.
- El informe final queda guardado.
- Solamente entonces se autoriza un canary gradual en producción.
