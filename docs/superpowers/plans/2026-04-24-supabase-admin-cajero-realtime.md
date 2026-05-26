# Plan: Supabase admin/cajero inmediato multi-banca

## Resumen de auditoria

El flujo nativo ya tiene una base correcta para multi-banca: el cajero guarda tickets con `adminId`, `adminUser`, `sellerId`, `sellerUser` y `banca`, y la sincronizacion remota usa `owner_key = adminId`. Esto permite que todos los tickets de una banca/admin vivan bajo el mismo dueño remoto.

El problema principal es que hoy la app funciona por hidratacion/push puntual, no por escucha inmediata. `SalesActivity` sube en segundo plano despues de vender, `ShellActivity` hidrata al entrar, `FinanceActivity` hidrata cuando se refresca el cuadre, pero `AdminDashboardActivity`, `TicketSummaryActivity`, `AdminCashierDetailActivity`, `AdminWinnersActivity`, `AdminMonitorActivity` y `AdminLotteryMonitorActivity` leen principalmente de local sin subscribirse a cambios remotos. No aparece uso de Supabase Realtime, `postgres_changes`, canales, ni polling continuo.

## Evidencia relevante

- `SalesActivity` crea el ticket con `adminId = session.adminId ?: session.userId`, lo guarda en `LocalSalesRepository`, lo encola y ejecuta `NativeTicketCloudSyncCoordinator.flushOwner(...)` en un hilo.
- `NativeTicketCloudSyncCoordinator` hace `fetchTickets(ownerKey)`, mezcla local/remoto/pendiente y escribe un JSON completo en `lotterynet_tickets_by_owner`.
- `NativeTicketRemoteStore` usa `SupabaseJsonTableClient` REST contra `lotterynet_tickets_by_owner` y fallback legacy `bv_t3_<ownerKey>`.
- `SupabaseJsonTableClient` solo implementa GET/POST/upsert REST; no implementa Realtime.
- `NativeUsersBootstrapper` solo descarga usuarios desde Supabase cuando no hay usuarios locales o cuando se fuerza refresh. Eso significa que cambios admin/cajero no siempre entran durante una sesion abierta.
- `AdminDashboardActivity`, `TicketSummaryActivity`, `AdminCashierDetailActivity` y varias pantallas admin calculan desde `LocalSalesRepository` al abrir, sin garantia de refresco remoto inmediato.

## Critico

1. Crear una fuente unica de sync operativo admin/cajero.
   - Nuevo componente sugerido: `NativeOperationalSyncCoordinator`.
   - Responsabilidad: resolver `ownerKey`, hidratar tickets/remotos, subir pendientes, refrescar usuarios/config cuando toque y devolver estado para UI.
   - Debe envolver `NativeTicketCloudSyncCoordinator`, `NativeRechargeCloudSyncCoordinator` y `NativeUsersBootstrapper` sin duplicar reglas por pantalla.

2. Hacer que la venta del cajero tenga confirmacion de sync real.
   - Reemplazar el hilo suelto `native-ticket-flush` por una llamada centralizada que marque el ticket como `synced`, `pending_sync` o `sync_error`.
   - Mantener venta rapida: guardar local primero, imprimir/mostrar ticket, y subir inmediatamente en background con feedback visible.
   - Si falla internet, dejarlo en cola y mostrar "Guardado local, pendiente de sincronizar".

3. Dar refresco inmediato al admin.
   - Opcion A, preferida: agregar Supabase Realtime para `lotterynet_tickets_by_owner` filtrado por `owner_key`.
   - Opcion B, si el SDK/dependencias actuales no soportan Realtime: implementar polling ligero con `fetchUpdatedAt(ownerKey)` cada 8-15 segundos mientras una pantalla admin esta activa.
   - En ambos casos, al detectar cambio remoto, hacer `hydrateOwner(ownerKey, banca)` y actualizar estados Compose.

4. Corregir pantallas admin para que no dependan solo de snapshot local inicial.
   - `AdminDashboardActivity`: refrescar resumen y ultimos tickets despues de hidratacion/remoto.
   - `TicketSummaryActivity`: hidratar al abrir, al volver y por evento/poll; mostrar estado "Sincronizado / Pendiente / Sin conexion".
   - `AdminCashierDetailActivity`: antes de calcular resumen del cajero, hidratar owner admin; actualizar tickets del cajero cuando llegue remoto.
   - `AdminWinnersActivity`, `AdminMonitorActivity`, `AdminLotteryMonitorActivity`: usar el mismo feed sincronizado antes de calcular ganadores, ventas por cajero y loterias.

5. Propagar cambios de estado de tickets.
   - Anular, pagar, marcar ganador, duplicado/consulta deben llamar al mismo sync central.
   - Cada cambio local debe encolarse/subirse bajo el mismo `ownerKey`.
   - El merge remoto debe preferir el estado mas reciente para no revivir tickets anulados o pagados.

## Importante

1. Endurecer identidad admin/cajero.
   - Validar que todo cajero tenga `adminId`, `adminUser` y `banca` despues de cargar usuarios desde Supabase.
   - Si el cajero viene anidado dentro del admin, mantener fallback actual.
   - Si viene top-level sin `adminId`, bloquear operacion o mostrar alerta clara, porque no puede sincronizar correctamente con el admin.

2. Separar "banca" de "owner".
   - `ownerKey` debe ser estable: preferir `adminId`, luego `adminUser`.
   - `banca` debe servir para mostrar y filtrar, no para reemplazar identidad.
   - Multi-banca debe aislar tickets por owner y ademas verificar banca cuando exista.

3. Evitar sobrescritura completa peligrosa.
   - Hoy `upsertTickets(ownerKey, merged)` sube un payload completo. Con dos dispositivos escribiendo casi al mismo tiempo, hay riesgo de perder una actualizacion si ambos partieron de snapshots distintos.
   - Plan minimo: antes de subir, siempre volver a leer remoto y mezclar por `ticket.id`.
   - Plan robusto: migrar de JSON por owner a filas por ticket (`tickets` con `ticket_id`, `owner_key`, `updated_at`) para upserts por registro.

4. Estado visible de sincronizacion.
   - Añadir un estado comun: `Sincronizado`, `Pendiente`, `Sin conexion`, `Error al sincronizar`.
   - Mostrarlo en Venta, Dashboard admin, Tickets, Cuadre y Detalle cajero.

## Mejoras visuales/operativas

1. En dashboard admin, agregar una linea pequeña: "Ultima sync: hh:mm:ss" y un badge si hay pendientes.
2. En lista de tickets, marcar tickets pendientes de subir sin romper jerarquia visual.
3. En detalle de cajero, poner boton "Actualizar" que use el mismo sync central.
4. En cuadre, el refresh debe decir si trajo tickets remotos o solo recalculo local.

## Tests de contrato

1. `NativeUsersBootstrapperContractsTest`
   - Cajero anidado hereda `adminId`, `adminUser`, `banca`.
   - Cajero top-level sin admin queda detectado como invalido para operacion multi-banca.

2. `NativeTicketCloudSyncCoordinatorContractsTest`
   - Ticket vendido por cajero se sube bajo `ownerKey = adminId`.
   - Admin hidrata y recibe ticket de cajero.
   - Dos tickets desde dos dispositivos no se pisan al mezclar remoto/local.
   - Estado `voided`/`paid` no se pierde despues de sync.

3. `AdminOperationalSyncContractsTest`
   - Dashboard admin recalcula resumen despues de hydrate.
   - TicketSummary muestra tickets remotos de cajeros.
   - Finance/cuadre incluye tickets remotos despues de refresh.

4. `LocalFinanceRepositoryContractsTest`
   - Scope admin incluye tickets de cajeros de su banca/admin.
   - No incluye tickets de otra banca/admin.

## Validacion manual

1. Dispositivo A: login admin.
2. Dispositivo B: login cajero del mismo admin/banca.
3. Cajero vende ticket.
4. Admin debe ver el ticket en dashboard/lista/cuadre sin cerrar sesion.
5. Cajero anula o marca/paga ticket.
6. Admin debe ver estado actualizado.
7. Repetir sin internet en cajero: ticket aparece como pendiente; al volver internet, sube y admin lo recibe.

## Orden de implementacion recomendado

1. Agregar contratos de identidad y sync de tickets.
2. Crear `NativeOperationalSyncCoordinator` con polling por `updated_at` como primera version segura.
3. Conectar dashboard admin, ticket summary, detalle cajero y finance al coordinador.
4. Propagar anular/pagar/ganador por el mismo coordinador.
5. Agregar estado visual comun de sync.
6. Evaluar migracion posterior a tabla por ticket o Supabase Realtime real.
