# Production Call Volume Hardening

Fecha local: 2026-05-18

## Evidencia Observada

- `get-ticket-list` aparecio en rafagas de varias llamadas por segundos para el mismo flujo. Todas respondian `200`, pero cada una podia tardar cientos de milisegundos y repetir la misma descarga grande.
- API REST tambien mostro lecturas repetidas a `lotterynet_tickets_by_owner`, `tickets?limit=300` y `ticket_items?ticket_id=in.(...)`.
- Realtime no debe cargar datos completos: debe avisar que algo cambio y luego la app hace una sola descarga controlada.
- `results-server-refresh` corre cada minuto y responde `200`; el problema principal no era el cron de resultados, sino la forma en que las pantallas pedian tickets/resultados al abrir, refrescar o recibir realtime.

## Documentacion Usada

- Supabase Realtime rate limits: https://supabase.com/docs/guides/realtime/rate-limits
- Supabase Realtime reports: https://supabase.com/docs/guides/realtime/reports
- Supabase recomienda Broadcast para escala frente a Postgres Changes: https://supabase.com/docs/guides/realtime/subscribing-to-database-changes
- Supabase logs: https://supabase.com/docs/guides/platform/logs
- Android offline-first data layer: https://developer.android.com/topic/architecture/data-layer/offline-first
- PostgREST pagination/ranges: https://docs.postgrest.org/en/v14/references/api/pagination_count.html

## Cambios Implementados En Codigo

- `SyncGovernor` centraliza la compuerta de sync de tickets por `ownerKey`.
- Manual refresh, entrada a pantalla y eventos realtime ahora comparten la misma proteccion porque todos pasan por `NativeOperationalSyncCoordinator`.
- Se mantiene una sola hidratacion activa por `ownerKey`.
- Una hidratacion normal no se repite hasta pasar 30 segundos.
- Una hidratacion forzada puede saltar la ventana de 30 segundos, pero no puede duplicarse si ya hay otra del mismo `ownerKey` corriendo.
- El fallback de exposicion de ventas sin realtime subio de 30 a 60 segundos.
- El cache de `updated-at` de tickets ahora guarda tambien respuestas vacias por 60 segundos, para no pedir otra vez cuando el servidor no tiene sello nuevo.

## Nuevas APIs Preparadas

- `get-ticket-delta(ownerKey, sinceCursor)`: devuelve solo tickets cambiados desde un cursor y sus items.
- `get-ticket-summary(ownerKey, dayKey)`: devuelve conteos y totales del dia sin traer todos los tickets.
- `get-results-status(dayKey)`: devuelve version/fecha y conteos de resultados antes de bajar datos completos.
- `client-health-batch`: recibe eventos agrupados para telemetria cada 10-15 minutos, no una llamada por cada evento.

Estas funciones quedaron con `verify_jwt = true` en `supabase/config.toml`. No se desplegaron remotamente en este cambio.

## Siguiente Cierre Recomendado

- Cambiar pantallas de dashboard/reportes para usar `get-ticket-summary` antes de descargar tickets completos.
- Cambiar resultados para consultar `get-results-status` y solo bajar resultados cuando cambie la version.
- Migrar Realtime de `Postgres Changes` a `Broadcast` por topicos `owner:{ownerKey}` y `results:{dayKey}` cuando se haga el ajuste de servidor.
- En produccion, revisar logs 24h/7d y confirmar: llamadas por endpoint, usuario/banca, minuto, status y duracion.

## Criterios De Exito

- Abrir ventas/admin/resultados no debe disparar rafagas repetidas de `get-ticket-list`.
- Maximo una descarga grande normal por `ownerKey` cada 30 segundos.
- Realtime y refresco manual al mismo tiempo deben terminar en una sola descarga efectiva por ventana.
- Resultado publicado debe actualizar la pantalla sin polling agresivo.
- Reduccion esperada de 70-80% en llamadas grandes cuando haya 20-50 dispositivos.
