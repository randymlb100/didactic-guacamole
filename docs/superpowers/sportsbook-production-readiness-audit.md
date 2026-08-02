# Deportes - Auditoria Pendiente Para Produccion

Fecha: 2026-06-04

Este documento deja guardado el analisis de la seccion Deportes para retomarlo despues. No se hicieron cambios de codigo en este analisis.

## Estado Actual

La seccion Deportes ya tiene una base separada de loteria:

- Tablas `sports_*` aisladas.
- Tablero de juegos y cuotas.
- Venta de ticket deportivo.
- Listado de tickets deportivos.
- Pago de cobro deportivo.
- Anulacion.
- Liquidacion manual.
- Sincronizacion de cuotas.
- Control Master para activar Deportes por admin/cajero.

Archivos principales:

- `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookTicketRemoteStore.kt`
- `app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookBoardRemoteStore.kt`
- `app/src/main/java/com/lotterynet/pro/core/model/SportsbookModels.kt`
- `supabase/functions/create-sports-ticket/index.ts`
- `supabase/functions/get-sports-tickets/index.ts`
- `supabase/functions/pay-sports-ticket/index.ts`
- `supabase/functions/settle-sports-ticket/index.ts`
- `supabase/functions/settle-sports-tickets/index.ts`
- `supabase/functions/void-sports-ticket/index.ts`
- `supabase/functions/sports-get-board/index.ts`
- `supabase/functions/sports-sync-odds/index.ts`
- `supabase/migrations/20260530152811_sports_betting_foundation.sql`

## Riesgos Reales

### 1. Venta No Atomica

`create-sports-ticket` crea primero el ticket y despues crea las jugadas del ticket.

Riesgo: si falla la insercion de jugadas, puede quedar un ticket deportivo creado sin selecciones.

Arreglo recomendado:

- Convertir venta a RPC/transaccion SQL.
- O compensar: si fallan las jugadas, borrar/anular el ticket creado.

### 2. Pago No Atomico

`pay-sports-ticket` lee el ticket ganador y luego lo actualiza a `paid`.

Riesgo: dos toques rapidos o dos dispositivos pueden intentar pagar el mismo ticket.

Arreglo recomendado:

- Hacer update condicional:
  - pagar solo si `status = 'won'`;
  - retornar error si ya estaba `paid`.
- Crear settlement/auditoria solo despues de confirmar que el update cambio una fila.

### 3. Liquidacion No Atomica

La liquidacion cambia jugadas y ticket en pasos separados.

Riesgo: si falla a mitad, las jugadas pueden quedar en un estado y el ticket en otro.

Arreglo recomendado:

- Liquidar legs y ticket juntos en una operacion transaccional.
- No permitir cambiar tickets ya `paid`.

### 4. Identidad Permisiva

Las funciones comparan el token con `actorKey`, `adminKey` y `cashierKey`, pero si la metadata del token viene vacia, acepta el actor enviado por la app.

Riesgo: en produccion eso es flojo para dinero.

Arreglo recomendado:

- No aceptar metadata vacia en rutas de venta, pago, anulacion y liquidacion.
- Resolver actor/admin/cajero desde el token o desde una tabla confiable del servidor.

### 5. Limites Incompletos

La tabla tiene campos como `max_selection_stake` y `max_event_exposure`, pero la venta valida principalmente monto por ticket y premio posible.

Falta:

- Limite por seleccion.
- Limite por evento.
- Limite por cajero/admin.
- Exposicion acumulada por evento/mercado.

### 6. Tablero Deportivo Muy Abierto

`sports-get-board` parece leer tablero sin exigir sesion.

Puede estar bien si solo muestra cuotas publicas, pero si Deportes esta oculto por Master, tambien debe respetar permiso.

### 7. RLS Cerrado Sin Politicas Directas

Las tablas `sports_*` tienen RLS activo. Si toda la app usa Edge Functions con service role, esta bien. Si alguna pantalla lee directo desde Supabase, puede quedar bloqueada.

## Que Falta Antes De Produccion Fuerte

- Venta atomica.
- Pago atomico.
- Liquidacion atomica.
- Identidad estricta por token.
- Limites reales de exposicion.
- Confirmar si tablero requiere sesion.
- Confirmar RLS/policies o dejar documentado que todo va por Edge Functions.
- Pruebas de doble toque, doble pago, ticket sin legs y usuario de otra banca.

## Pruebas Minimas

- Venta exitosa con cuota abierta.
- Venta rechazada con cuota vencida.
- Venta rechazada con evento iniciado.
- Venta rechazada por Deportes deshabilitado.
- Venta rechazada por limite.
- Repetir el mismo `clientRequestId` y confirmar que no duplica.
- Simular falla al crear legs y confirmar que no queda ticket huerfano.
- Pagar el mismo ticket dos veces al mismo tiempo y confirmar un solo pago.
- Liquidar ticket despues de `paid` y confirmar que falla.
- Actor de otra banca intentando leer o pagar ticket ajeno.

## Decision Recomendada

Deportes puede seguir en prueba controlada, pero no deberia abrirse fuerte con clientes hasta cerrar venta/pago/liquidacion atomicos y permisos estrictos.

## Verificacion de cierre 2026-07-19

La validacion actual se ejecuto sin Gradle:

- `sportsbook-ui-contract.node.test.mjs`: 20 pruebas aprobadas.
- `sportsbook-live-flow-smoke.mjs`: flujo real con `podero02` y `bancae01` aprobado.
- Venta, ticket pendiente, liquidacion, pago, finanzas e idempotencia fueron comprobados.
- El servidor ahora rechaza una cuota repetida y mas de una seleccion del mismo mercado del mismo juego; la regla no depende solo de Android.
- `create-sports-ticket` fue desplegada con `verify_jwt = true` en la version activa 8.
- La creación de ticket y sus jugadas usa la RPC `create_sports_ticket_atomic`; la exposición por evento usa bloqueo transaccional por banca/evento.
- Pago y liquidación usan RPC con actualización condicional de fila; el smoke concurrente confirmó un único cobro efectivo (`alreadyPaid` para el segundo intento).
- El cron de resultados deportivos permanece definido en `render.yaml` pero desactivado por diseño (`SPORTS_RESULTS_SYNC_ENABLED=false`); Render todavia no tiene creado el servicio `lotterynet-sports-results-sync`. Activarlo requiere una decisión operativa separada.
- La prueba one-off en Render confirmó que el repositorio `main` desplegado todavía no contiene `tools/render/sync_sports_results.py`; por eso el job terminó con `python: can't open file`. El archivo existe localmente, pero aún no está publicado en `origin/main`.

Pendientes reales antes de declarar producción fuerte:

- Crear y habilitar el cron de resultados cuando se autorice operar Render.
- Completar pruebas de concurrencia con dos cajeros y de acceso entre bancas.
