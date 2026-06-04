# Solucion Definitiva Resultados Y Premios Servidor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer que resultados, tickets ganadores, pagos y finanzas salgan de una fuente de verdad normalizada, no de payloads viejos o caches JSON.

**Architecture:** Mantener `lotterynet_kv` y payloads como cache/compatibilidad, pero mover la autoridad a tablas normalizadas: resultados por sorteo, detalles de premio por item, ledger de pagos y una cola de reconciliacion. El servidor escribe de forma idempotente; la base calcula premios una sola vez por version de resultado y no recalcula tickets pagados.

**Tech Stack:** Supabase Postgres, RLS, SQL functions, triggers controlados, Realtime Broadcast/Postgres Changes, Render Python scraper, Android Kotlin client.

---

## Hallazgos

- Hoy `lotterynet_results_by_day` y `lotterynet_pick_results_by_day` guardan `payload jsonb`. Eso sirve para mostrar rapido, pero no es ideal como autoridad para dinero.
- `lotterynet_reconcile_ticket_prize()` reconstruye el ticket con `lotterynet_ticket_json_from_tables()` y calcula contra payloads JSON. Si el payload cambia de formato, nombre, id, fecha o compatibilidad vieja, el premio puede variar.
- El trigger `lotterynet_reconcile_tickets_for_results_day()` recorre tickets por dia cuando cambia un resultado. Ya tiene protecciones, pero sigue siendo un recalculo grande por fecha.
- `lotterynet_sync_results_by_day_from_kv()` sincroniza desde `lotterynet_kv`, pero usa `skip_result_reconcile`; eso evita ciertos recaculos repetidos, aunque tambien deja caminos donde cache y resultado autoritativo pueden desalinearse.
- Finanzas suma `tickets.payout_amount`. Eso esta bien si `payout_amount` es definitivo, pero no si fue calculado desde payload viejo o recalculado luego.

## Documentacion usada

- Supabase recomienda RLS en tablas expuestas y separar permisos de Data API: https://supabase.com/docs/guides/api/securing-your-api
- Supabase Realtime recomienda Broadcast para escalabilidad/seguridad cuando hay cambios que deben llegar en vivo: https://supabase.com/docs/guides/realtime/subscribing-to-database-changes
- Supabase Database Webhooks son triggers con `pg_net` y son asincronos, utiles para avisos externos sin bloquear la base: https://supabase.com/docs/guides/database/webhooks
- Supabase Postgres triggers ejecutan funciones automaticamente ante INSERT/UPDATE/DELETE: https://supabase.com/docs/guides/database/postgres/triggers
- PostgreSQL generated columns pueden derivar valores declarativos de otras columnas cuando convenga: https://www.postgresql.org/docs/current/ddl-generated-columns.html

## Propuesta

### 1. Crear tabla autoritativa de resultados normalizados

Nueva tabla propuesta:

```sql
create table public.result_draws (
  id uuid primary key default gen_random_uuid(),
  source text not null,
  result_date date not null,
  result_day_key text not null,
  lottery_legacy_id text not null,
  lottery_name text not null,
  game text not null default 'normal',
  draw_name text not null default '',
  number_raw text not null,
  number_digits text not null,
  status text not null default 'published',
  source_payload jsonb not null default '{}'::jsonb,
  source_hash text not null,
  first_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (result_day_key, lottery_legacy_id, game, draw_name)
);
```

Regla: el scraper escribe aqui primero. `lotterynet_results_by_day`, `lotterynet_pick_results_by_day` y `lotterynet_kv` se generan desde aqui, no al reves.

### 2. Crear tabla autoritativa de premios por item

Nueva tabla propuesta:

```sql
create table public.ticket_prize_items (
  ticket_item_id uuid primary key references public.ticket_items(id) on delete cascade,
  ticket_id uuid not null references public.tickets(id) on delete cascade,
  result_draw_id uuid references public.result_draws(id),
  is_winner boolean not null default false,
  payout_amount numeric not null default 0,
  hit_position text not null default '',
  result_number text not null default '',
  payout_config_snapshot jsonb not null default '{}'::jsonb,
  result_hash text not null default '',
  calculated_at timestamptz not null default now()
);
```

Regla: `ticket_items.payout_amount` puede quedarse por compatibilidad, pero la fuente de verdad para detalles es `ticket_prize_items`.

### 3. Congelar configuracion de premio al vender

Al crear ticket, guardar snapshot de la tabla de pago usada:

- `ticket_items.payout_config_snapshot jsonb`
- o en `ticket_prize_items.payout_config_snapshot`

Asi un ticket viejo no cambia si el admin modifica premios despues. El premio se calcula con la configuracion vigente al momento de vender.

### 4. Reconciliacion idempotente por cola, no por recalculo grande

Nueva tabla:

```sql
create table public.result_reconcile_jobs (
  id uuid primary key default gen_random_uuid(),
  result_day_key text not null,
  lottery_legacy_id text,
  game text,
  status text not null default 'pending',
  attempts int not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  locked_at timestamptz,
  completed_at timestamptz
);
```

Cuando entra resultado nuevo, se crea un job. Una funcion procesa solo tickets afectados por esa loteria/juego, no todo el dia completo.

### 5. Estado definitivo del ticket

Reglas:

- `PAGADO` nunca se recalcula hacia abajo.
- `ANULADO`, `BORRADO`, `INVALIDADO` nunca entra a premio.
- `GANADOR` puede actualizarse solo si el resultado cambia oficialmente y el ticket no esta pagado.
- Cada calculo guarda `result_hash`; si el hash no cambio, no se toca el ticket.

### 6. Cache y realtime limpios

- `result_draws` es autoridad.
- `lotterynet_kv` es cache para Android viejo y lectura rapida.
- Render `/system-results` lee primero cache generado, no raspa cada vez.
- Realtime debe escuchar una sola senal liviana: cambio en cache/result version, no payload gigante cada rato.
- Para escalabilidad, usar Broadcast o Postgres Changes filtrado por llave/fecha, segun el cliente.

### 7. Pruebas obligatorias antes de produccion

- Ticket viejo con payload viejo calcula igual que antes si su snapshot existe.
- Ticket pagado no cambia aunque cambie resultado.
- Ticket ganador normal muestra loteria, numero ganador, jugada, posicion y monto.
- Pick straight y box calculan separados.
- Super pale usa las dos loterias correctas y no mezcla nombres.
- Resultado repetido con mismo hash no dispara recaculo.
- Finanzas lee `tickets.payout_amount` sincronizado desde `ticket_prize_items`.

## Orden recomendado

### Task 1: Auditoria sin riesgo

- [ ] Crear consultas para comparar `tickets.payout_amount` contra suma de `ticket_items.payout_amount`.
- [ ] Crear consulta para detectar tickets `GANADOR/PAGADO` sin `winningDetails` o sin item ganador.
- [ ] Crear reporte de resultados cache vs tablas `lotterynet_results_by_day` y `lotterynet_pick_results_by_day`.

### Task 2: Tablas nuevas sin activar flujo

- [x] Crear `result_draws`.
- [x] Crear `ticket_prize_items`.
- [x] Crear indices por fecha, loteria, ticket y estado.
- [x] Activar RLS y permisos minimos.

### Task 3: Normalizador de resultados

- [x] Crear funcion que convierta payload normal/Pick en filas `result_draws`.
- [x] Mantener compatibilidad generando payload JSON desde `result_draws`.
- [x] Probar con resultados de hoy y dias viejos.

### Task 4: Motor de premios v2

- [x] Crear `lotterynet_calculate_ticket_prize_v2(ticket_id)`.
- [x] Calcular desde `result_draws`, no desde JSON.
- [x] Guardar detalle en `ticket_prize_items`.
- [x] Sincronizar `tickets.payout_amount`, `ticket_items.is_winner`, `ticket_items.payout_amount`.

### Task 5: Cola de reconciliacion

- [x] Crear `result_reconcile_jobs`.
- [x] Cuando cambia `result_draws`, crear job solo para esa fecha/loteria/game.
- [x] Procesar por lotes para no trabar Supabase.
- [x] Marcar errores sin romper inserts de resultados.

### Task 6: Cambiar Render scraper

- [ ] Render escribe `result_draws`.
- [ ] Render genera cache despues de escribir normalizado.
- [ ] `/system-results` no debe raspar en cada lectura normal.
- [ ] Agregar endpoint de health que compare cache vs autoridad.

### Task 7: Android y visual

- [ ] Ticket oficial lee detalles claros desde servidor cuando existan.
- [ ] Snapshot ganador muestra: loteria, numero ganador, jugada, tipo, posicion y monto.
- [ ] Si no existe detalle v2, cae a compatibilidad vieja.

## Decision recomendada

Implementar por fases, sin apagar lo actual hasta que v2 compare igual o mejor que lo viejo durante varios dias. La clave es que el dinero no dependa mas del payload de cache: el payload queda para mostrar, y la tabla normalizada decide premios.

## Ejecucion 2026-05-29

- Backup local creado en `backups/before-result-prize-v2-20260528-222708`.
- Backup Supabase creado en `lotterynet_backup.backup_manifest_20260529_0227` con copias de tickets, items, pagos, movimientos, resultados y cache KV.
- Migracion `result_draws_prize_v2_foundation` aplicada en produccion.
- Migracion `result_reconcile_queue_guard` aplicada en produccion para diferir trabajos del backfill y evitar trabajos activos duplicados.
- Verificacion: `result_draws` tiene 5758+ filas normalizadas; el dia `28-05-2026` devuelve 169 filas y el payload generado devuelve 169 resultados.
- Verificacion de premios: muestra de 30 tickets ganadores/pagados comparo igual entre calculo viejo y v2.
- No se ejecuto build Android; el cambio principal fue Supabase/SQL y queda compatible con el flujo actual.
