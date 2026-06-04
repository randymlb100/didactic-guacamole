# LotteryNet Multi-Banca Engineering Playbook

Fecha local: 2026-06-02

## Proposito

Este playbook define como investigar, disenar, implementar y verificar cambios en LotteryNet cuando el cambio afecta venta, tickets, premios, resultados, pagos, finanzas, usuarios, realtime, cache o Supabase.

La regla principal: **no arreglar solo el sintoma visible**. Cada cambio debe proteger el flujo real de una app multi-banca con dinero:

- muchos cajeros vendiendo al mismo tiempo;
- admins viendo tickets de todos sus cajeros;
- resultados entrando por jobs;
- ganadores apareciendo sin refresco manual;
- app en segundo plano perdiendo websocket;
- snapshots/cache viejos intentando pisar datos correctos;
- nombres de usuarios cambiando sin mover tickets historicos;
- red lenta, doble toque y timeouts.

## Regla De Fuente De Verdad

### Server-first para dinero

Supabase es la fuente final para:

- crear ticket;
- validar limites;
- validar cierre de loteria;
- validar jugada bloqueada;
- borrar/anular ticket;
- pagar premio;
- calcular premio oficial;
- finanzas y movimientos;
- permisos de admin/cajero/supervisor.

La app Android no debe inventar un ticket oficial, premio, pago o borrado cuando el servidor no lo confirmo.

### Cache-first para lectura

Android puede mostrar cache local primero para:

- lista de tickets;
- detalle de ticket;
- resultados;
- snapshots;
- reportes visuales preliminares.

Pero debe reconciliar con servidor cuando:

- entra a la app;
- vuelve de segundo plano;
- recibe realtime;
- usuario toca refrescar;
- detecta sello `updatedAt` nuevo;
- hay ticket ganador/pagado/borrado.

## Identidad Canonica

Nunca usar nombre visible como identidad principal.

Orden de verdad:

1. `business_key` / negocio / tenant, si existe.
2. `admin_key`.
3. `cashier_key`.
4. `supervisor_key`.
5. `auth_user_id`.
6. aliases legacy: username, display name, banca name, ids viejos.

Todo ticket debe poder responder:

- quien es el admin canonico;
- quien es el cajero canonico;
- que aliases deben recibir snapshot;
- que reportes/finanzas lo incluyen;
- quien puede borrarlo/pagarlo/verlo.

Cambiar el nombre de un cajero no debe mover tickets viejos.

## Realtime No Es Garantia Final

Realtime es una senal, no la fuente de verdad.

Si Android pierde websocket al estar en segundo plano, al volver debe hacer catch-up:

1. revisar estado de canal/reconectar;
2. pedir stamps/versiones de resultados y tickets;
3. bajar solo lo que cambio;
4. mergear sin pisar estados terminales.

Estados terminales que local nunca debe degradar:

- `BORRADO`;
- `ANULADO`;
- `INVALIDADO`;
- `GANADOR`;
- `PAGADO`.

## Jobs Y Colas

No procesar "todo el dia" en una sola llamada si puede crecer.

Jobs deben ser pequenos:

- fecha;
- loteria;
- resultado;
- tipo de trabajo;
- dedupe key.

Cada job debe tener:

- `pending`;
- `processing`;
- `completed`;
- `failed`;
- `attempts`;
- `retry_after`;
- `last_error`;
- timestamps.

Cada worker debe tener limites:

- max jobs por corrida;
- max tickets por job;
- max milisegundos;
- cortar antes de timeout;
- retry con backoff;
- idempotencia.

Si hay cola grande, procesar mas tandas pequenas, no una tanda gigante.

## Como Investigar Un Bug

Antes de tocar codigo, identificar el tipo de bug:

| Sintoma | Revisar tambien |
| --- | --- |
| Ticket duplicado | idempotencia, doble toque, timeout, retry, `clientRequestId`, snapshot |
| Ganador no aparece | resultados, cola, ticket_items, payout, owner aliases, realtime catch-up |
| Admin no ve ticket de cajero | identidad canonica, owner aliases, snapshot admin/cajero, filtro local |
| Cajero no ve su ticket | cashier_key, usuario renombrado, cache local, deletedIds, get-ticket-list |
| Boton refrescar tarda | call volume, stamp checks, realtime, foreground catch-up, throttling |
| Finanzas mal | pagos, movimientos_balance, status terminal, reportes por key canonica |
| App vuelve de segundo plano vieja | lifecycle, websocket reconnect, WorkManager, stamp comparison |
| Server lento | full scans, indices, jobs sin limite, cola acumulada, logs por endpoint |

## Documentacion Obligatoria

Antes de cambiar una parte nueva, buscar documentacion actual.

### Android

- Offline-first data layer.
- Data layer/source of truth.
- WorkManager para sync confiable.
- Lifecycle/ProcessLifecycleOwner para foreground-background.
- Compose state y evitar recomposiciones que disparen llamadas.

### Supabase

- Realtime y limites.
- Edge Functions background tasks.
- Cron/pg_cron.
- Queues/PGMQ.
- RLS y Auth app_metadata.
- Postgres indexes y query plans.

### Postgres

- indices parciales;
- `FOR UPDATE SKIP LOCKED`;
- idempotencia por unique keys;
- multi-tenant shared schema;
- evitar scans por `coalesce(column::text, ...)` en hot paths.

## Pruebas Minimas Por Cambio Critico

No basta una prueba Node aislada.

### SQL/contract

- funciones existen;
- indices existen;
- limites de job existen;
- no hay loops sin limite;
- owner aliases correctos;
- delete/pago/idempotencia correctos.

### Node real QA

Con credenciales de prueba:

- login admin;
- login cajero;
- venta real QA;
- doble envio mismo `clientRequestId`;
- ticket aparece en admin y cajero;
- delete/pago QA;
- cleanup solo de tickets creados por la prueba.

### Kotlin unit

- merge local/remoto;
- estados terminales no bajan;
- foreground catch-up;
- throttling;
- sale state;
- no doble tap.

### Instrumentation/manual POS

- app abre con resultados sin refrescar;
- app vuelve de segundo plano y actualiza;
- imprimir/WhatsApp;
- red lenta;
- POS viejo/lento;
- ticket grande.

## Gates Antes De Release

No hacer build release final si falta uno de estos:

- tests criticos pasan;
- prueba real QA pasa;
- no hay duplicado por doble envio;
- app actualiza tras segundo plano;
- ganador aparece en admin y cajero;
- pago no duplica movimiento;
- delete no revive ticket;
- logs no muestran rafagas repetidas;
- jobs no quedan con cola creciente sin procesar.

## Como Debe Pensar Codex/Subagentes

Cuando el usuario reporte un fallo, el agente debe preguntar internamente:

1. Esto afecta dinero o solo lectura?
2. Que fuente de verdad manda?
3. Puede fallar por segundo plano/realtime perdido?
4. Puede fallar por id/nombre/alias?
5. Puede fallar por cola acumulada?
6. Puede fallar por doble toque/timeout/retry?
7. Puede fallar por cache viejo pisando servidor?
8. Puede escalar mal con muchos tickets/cajeros?
9. Que documentacion oficial aplica?
10. Que prueba real demuestra que ya no falla?

El arreglo correcto debe responder esas preguntas, no solo cambiar una linea para el caso reportado.
