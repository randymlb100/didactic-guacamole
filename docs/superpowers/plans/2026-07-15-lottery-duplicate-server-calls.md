# Lottery/Pick Duplicate Server Calls Remediation Plan

**Goal:** Reducir llamadas redundantes al servidor en lotería normal y Pick sin cambiar reglas de venta, sincronización, filtros, premios ni datos visibles.

**Scope:** Solo `SalesActivity`, `TicketSummaryActivity`, `TicketOfficialActivity`, `ResultsActivity`, los coordinadores/remote stores de tickets y resultados, y sus contratos/tests. Deportes queda fuera.

## Regla de producción

- Antes de modificar una llamada, documentar el flujo actual y su resultado esperado.
- Ajustar únicamente duplicaciones demostradas con contadores, pruebas o trazas; no reescribir servicios ni contratos.
- Mantener intactos Auth, RLS, idempotencia, caché local, cola offline, Realtime, fallback y manejo de errores.
- En 401, offline, red lenta y poca señal se pueden añadir mejoras de resiliencia aunque no exista un fallo visible, siempre que sean demostrables, no cambien la lógica ni el resultado del ticket, y eviten duplicados. Toda mejora debe tener una prueba antes/después para timeout, retry, reconexión y recuperación.
- Si una llamada no puede demostrarse duplicada, se conserva.
- Implementar una fase pequeña por vez y validar con smoke tests Node, contratos estáticos y `git diff --check`; no ejecutar Gradle.
- No tocar archivos ni flujos de Deportes.

## Invariantes

- `create-ticket-v2` sigue siendo la única creación server-first del ticket normal/Pick.
- No eliminar la caché local, la cola offline, idempotencia, Realtime ni fallback de auth.
- No reemplazar Auth por claves anónimas en operaciones protegidas.
- No hacer llamadas directas desde Android a Postgres; usar Edge Functions/API gateway existente.
- No convertir Realtime en una segunda hidratación automática: Realtime solo debe disparar una actualización coordinada.
- No ejecutar Gradle durante esta corrección; validar primero con contratos Node, tests estáticos y `git diff --check`.

## Diagnóstico confirmado

1. Después de `create-ticket-v2`, la venta llama `flushTicket()` y luego `syncTicketsForSession(force = true)`. Eso mezcla creación nueva con sincronización legacy y genera lectura/escritura adicional.
2. `TicketSummaryActivity` consulta el sello remoto en foreground y el coordinador lo vuelve a consultar antes de hidratar.
3. Después de sincronizar, el resumen puede hidratar nuevamente el rango visible con otro `fetchSnapshot`.
4. La exposición de límites tiene una precarga en `LaunchedEffect` y otra consulta autoritativa al agregar jugada; pueden solaparse si la primera aún está en vuelo.
5. Hay guardas duplicadas de `AtomicBoolean` y condiciones repetidas de auto-refresh; no son requests por sí mismas, pero dificultan razonar sobre concurrencia.
6. Resultados tiene varios disparadores, pero ya cuenta con governor, `AtomicBoolean` y caché; se debe conservar esa protección.

## Auditoría adicional solicitada: Auth, Postgres, API gateway y Realtime

La auditoría debe contar las llamadas por capa, no solo por función Android:

- Android → `SupabaseEdgeClient` / API gateway.
- Gateway → Edge Function.
- Edge Function → PostgREST/Supabase Admin.
- Postgres → Realtime/WAL.
- Auth → validación JWT y refresh de sesión.

El código de lotería/Pick tiene 106 referencias combinadas a invocaciones, tokens, snapshots, stamps y suscripciones dentro del alcance auditado. Ese número es un inventario de puntos de llamada, no 106 requests por pantalla. La fase de instrumentación debe convertirlo en conteo real por escenario.

Puntos que requieren revisión específica:

- `get-ticket-list` mantiene `verify_jwt = false` por compatibilidad legacy; el handler debe seguir rechazando cualquier acción no pública sin JWT válido.
- `create-ticket-v2`, `pay-ticket`, `void-ticket`, `get-ticket-delta`, `get-ticket-summary` y `get-sale-limit-exposure` mantienen `verify_jwt = true`.
- `freshAccessToken()` no debe ejecutarse una vez por cada llamada paralela; debe existir un proveedor/coalescer de refresh.
- No añadir consultas directas de Android a Postgres; la app debe seguir pasando por Edge Functions/API gateway.
- Realtime debe emitir una señal y deduplicar la hidratación; no debe disparar una consulta por cada evento repetido.
- Las consultas de Postgres de una misma operación deben agruparse en una Edge Function o RPC solo cuando eso reduzca round-trips sin cambiar RLS, ownership ni respuesta.
- Si se usa RPC/transacción, revisar `SECURITY INVOKER/DEFINER`, permisos `EXECUTE`, RLS y políticas `USING/WITH CHECK` antes de desplegar.

## Fase 1 — Instrumentación y contratos

- Añadir un contador/test double para `NativeTicketRemoteStore` que registre endpoint, owner, rango, auth scope, force y request key.
- Cubrir los caminos: startup, resume, refresh manual, Realtime, cambio de fecha y venta normal/Pick.
- Verificar que una venta produce una creación y no una segunda escritura legacy innecesaria.
- Verificar que el token normal se reutiliza y solo se refresca ante 401.

## Fase 2 — Venta sin doble sincronización

- Mantener `create-ticket-v2` y su `clientRequestId`.
- Después de respuesta exitosa, guardar la respuesta oficial localmente.
- Eliminar únicamente el `flushTicket()` posterior cuando el ticket ya fue aceptado por `create-ticket-v2`.
- Mantener una sola confirmación ligera de estado remoto si la UI necesita actualizar exposición/lista.
- No quitar la cola offline para ventas que no alcanzan el servidor.
- Probar ticket normal, Pick, Pick S+B, reintento 401 y doble toque.

## Fase 3 — TicketSummary con sello remoto reutilizado

- Extender `NativeOperationalSyncCoordinator.syncTicketsForSession` para aceptar el sello remoto ya obtenido.
- Si el sello no cambió, evitar `fetchSnapshot`.
- Si cambió, hacer una sola hidratación por owner y devolver los tickets hidratados al caller.
- Evitar que `hydrateVisibleTicketsForSession()` vuelva a pedir el mismo rango cuando el coordinador ya devolvió datos equivalentes.
- Eliminar la segunda llamada duplicada a `compareAndSet` sin cambiar la protección `summarySyncInFlight`.
- Probar startup, resume, polling, Realtime, refresh manual y filtro de fecha exacta.

## Fase 4 — Exposición de límites

- Compartir una operación en vuelo por clave `(owner, day, lottery, playType, number, auth scope)`.
- Si el `LaunchedEffect` ya está consultando, `resolveAuthoritativeCashierLimitError()` debe esperar/reutilizar ese resultado.
- Mantener la segunda validación autoritativa antes de agregar o vender; solo se elimina la consulta paralela redundante.
- Probar normal, Pick, cambio de lotería, cambio de número y actualización después de una venta.

## Fase 5 — Auth, gateway, Postgres y Realtime

- Mantener `Authorization: Bearer <user-jwt>` para funciones de usuario y `verify_jwt = true`.
- Mantener refresh de token solo como retry ante 401, evitando refrescos simultáneos por llamadas paralelas.
- Centralizar la deduplicación en el remote store/API gateway, no en consultas directas a Postgres desde Android.
- Mantener RLS y scopes por owner/cajero.
- Mantener Realtime como señal; al recibir eventos, pasar por governor/coalescer antes de hidratar.
- No abrir una suscripción adicional por cada recomposición o cambio de filtro local.

## Fase 6 — Resultados normal/Pick

- Conservar fallback Edge/Render únicamente cuando la respuesta está vacía o incompleta.
- Eliminar condiciones repetidas de auto-refresh, sin eliminar el governor.
- Evitar que cambio de modo visual Lotería/Pick dispare red: el modo debe filtrar resultados ya cargados.
- Mantener reconciliación de premios y caché por fecha.

## Fase 7 — Verificación

- Smoke Node de contratos de llamadas y aislamiento.
- Tests Kotlin de coalescing, force refresh, auth retry y `clientRequestId`.
- Verificación de que ningún archivo de Deportes sea modificado.
- `rg` para confirmar que no se introducen llamadas directas a Postgres desde Android.
- `git diff --check`.
- Reportar número esperado de llamadas por escenario antes/después.
- Separar métricas de Auth, gateway, Edge Function, PostgREST y Realtime para no confundir una request HTTP con varias operaciones internas.
- Verificar que un refresh por 401 sea el único segundo intento permitido y que no se convierta en loop.

## Documentación oficial consultada

- [Securing Edge Functions](https://supabase.com/docs/guides/functions/auth)
- [Authorization headers y verify_jwt](https://supabase.com/docs/guides/functions/auth-headers)
- [User sessions y refresh tokens](https://supabase.com/docs/guides/auth/sessions)
- [Postgres Changes](https://supabase.com/docs/guides/realtime/postgres-changes)
- [Realtime: Broadcast recomendado para escalabilidad](https://supabase.com/docs/guides/realtime/subscribing-to-database-changes)
- [Edge Functions y API gateway](https://supabase.com/docs/guides/functions)

## Orden recomendado

Fase 1 → Fase 2 → Fase 3 → Fase 4 → Fase 5 → Fase 6 → Fase 7.

Cada fase debe pasar sus contratos antes de iniciar la siguiente. No se debe modificar lógica de negocio mientras se corrigen rutas de coordinación de llamadas.
