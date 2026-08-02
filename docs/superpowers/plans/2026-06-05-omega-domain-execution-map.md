# Omega Domain Execution Map

## Objetivo

Ejecutar cambios grandes sin leer todo el proyecto ni mezclar dinero. Cada dominio tiene una entrada pequeña, archivos concretos y una prueba mínima. El mapa sale del `knowledge-graph.json` de Understand y de la auditoría por subagentes.

## Fuentes Técnicas

- Android state holders / UI state: https://developer.android.com/topic/architecture/ui-layer/stateholders
- Android WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager/index
- Android Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- Firebase Cloud Messaging: https://firebase.google.com/docs/cloud-messaging/android/receive
- Supabase Broadcast: https://supabase.com/docs/guides/realtime/broadcast/
- Supabase Cron: https://supabase.com/docs/guides/cron
- Supabase Edge Function secrets: https://supabase.com/docs/guides/functions/secrets

## Dominio: Venta

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- `app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt`
- `app/src/main/java/com/lotterynet/pro/core/sales/SaleExposureEngine.kt`
- `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`
- `tools/qa/admin-badge-and-cashier-global-limit-contract.node.test.mjs`

Regla: venta, límites, borrado y pago siguen server-first. Cache solo ayuda a mostrar datos, nunca decide dinero.

Prueba mínima:

- Doble print mantiene `clientRequestId`.
- Badge de admin no fuerza `Sin tope` si hay límite propio.
- Límite global de cajero comparte bolsa por admin, lotería, juego y número.

## Dominio: Tickets

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`
- `supabase/functions/get-ticket-list/index.ts`

Regla: Realtime/Broadcast solo avisa. La pantalla pide `updated-at`/delta y evita snapshots paralelos.

Prueba mínima:

- Entrar a Tickets 20 veces no dispara 20 snapshots completos.
- Admin puede borrar sus tickets; cajero respeta ventana de borrado.
- Duplicado viejo no bloquea eliminación administrativa.

## Dominio: Ganadores

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/results/TicketPrizeReconciler.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt`
- `app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt`
- `supabase/migrations/20260603171000_stable_result_hash_and_reconcile_watchdog.sql`

Regla: ganador se reconcilia por id canónico de admin/cajero y resultado real, no por alias visible ni timestamp del scraper.

Prueba mínima:

- Ticket ganador aparece en Tickets, Ganadores y Finanzas.
- Reentrar a la pantalla no duplica notificaciones.
- Ticket pagado conserva monto de cobro.

## Dominio: Resultados

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/results/ResultsSupabaseStore.kt`
- `app/src/main/java/com/lotterynet/pro/core/results/SupabaseResultsRemoteStore.kt`
- `supabase/functions/results-server-refresh/index.ts`
- `tools/qa/results-migration-contract.node.test.mjs`

Regla: `source_hash` ignora `lastSeenAt`, `firstSeenAt` y timestamps del scraper. Solo cambio real de sorteo/número/estado crea trabajo.

Prueba mínima:

- Misma respuesta dos veces no crea nuevos jobs.
- Hoy/ayer se hidratan al iniciar app.
- Cambiar Hoy/Ayer/Anteayer no re-renderiza en loop.

## Dominio: Finanzas

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/finance/LocalFinanceRepository.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpCoordinator.kt`
- `app/src/main/java/com/lotterynet/pro/core/results/TicketPrizeReconciler.kt`

Regla: finanzas no calcula sola la verdad; refleja ventas, borrados, pagos y premios ya reconciliados.

Prueba mínima:

- Si cambia un premio, finanzas refresca aunque el usuario no abra Finanzas.
- Borrado confirmado por servidor descuenta local.
- Pago ganador no aparece como venta nueva.

## Dominio: Admin/Cajero

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- `app/src/main/java/com/lotterynet/pro/ui/admin/AdminConfigActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt`
- `supabase/functions/update-master-config/index.ts`
- `supabase/functions/get-master-config/index.ts`

Regla: admin y cajero se resuelven por identidad canónica. Alias visual nunca decide permisos.

Prueba mínima:

- Instalación limpia hidrata modo y límites guardados.
- Admin no pisa límites de sus cajeros al editar su propio tope.
- Cajero hereda la bolsa global que el admin configuró.

## Dominio: Deportes

Archivos objetivo:

- `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/sportsbook/SportsbookTicketRemoteStore.kt`
- `supabase/functions/create-sports-ticket/index.ts`
- `supabase/functions/pay-sports-ticket/index.ts`
- `supabase/functions/settle-sports-ticket/index.ts`

Regla: deportes queda separado de lotería. Board puede cachearse; venta, pago y liquidación no.

Prueba mínima:

- Venta no deja ticket sin selecciones.
- Pago concurrente solo paga una vez.
- Liquidación concurrente no sobrescribe estado.
- Usuario de otra banca no lee ni paga ticket ajeno.

## Dominio: Supabase Jobs, Broadcast, Redis y Sentry

Archivos objetivo:

- `supabase/migrations/20260604072458_realtime_broadcast_redis_sentry_foundation.sql`
- `supabase/functions/_shared/lotterynet-admin.ts`
- `supabase/functions/_shared/upstash-redis.ts`
- `supabase/functions/_shared/sentry-edge.ts`
- `tools/qa/broadcast-redis-sentry-contract.node.test.mjs`

Regla: Broadcast es señal, Redis es cache auxiliar, Sentry no guarda secretos ni tickets completos.

Prueba mínima:

- Broadcast privado solo emite `owner/day changed`.
- Redis apagado no rompe funciones.
- Sentry filtra tokens, claves, payloads y jugadas.

## Orden De Implementación

1. Supabase/jobs y contratos Node.
2. SyncCenter Android.
3. Tickets/Resultados/Ganadores.
4. Finanzas.
5. Deportes producción.
6. FCM.
7. Baseline Profile.

## Checklist De Cierre Por Fase

- Cambio exacto aplicado.
- Archivo tocado.
- Prueba ejecutada.
- Riesgo restante.
- Si toca dinero: prueba Node o Kotlin obligatoria antes de build release.
