# Mapa Administrativo Web vs LotteryNet Android

Fecha: 2026-06-01

## Estado Actual

Paridad administrativa estimada despues de esta fase: 93%.

No esta al 100% todavia. La web ya cubre gran parte de MASTER, ADMIN y SUPERVISOR,
pero falta probar comandos reversibles con ADMIN real, validar Android leyendo los cambios
hechos desde web y terminar de mover algunos guardados legacy a `lotterynet_master_state`.

## Estado Del Mapa

- Web: `.understand-anything/knowledge-graph.json`
  - 91 nodos, 133 relaciones.
  - Analizado el 2026-05-30.
  - Sigue siendo pequeno y debe regenerarse para reflejar el dashboard actual.
- Android/Supabase: `../.understand-anything/knowledge-graph.json`
  - 2567 nodos, 2648 relaciones.
  - Analizado el 2026-05-30.
  - Se uso como referencia para contratos administrativos, usuarios, limites, recargas y sync.

## Implementado En Esta Fase

- Navegacion por perfil corregida en `src/utils/navigationPermissions.ts` usando `MasterDashboardActivity` como fuente para MASTER.
  MASTER ya no recibe pantallas operativas sueltas que Android no le da como consola principal.
  ADMIN mantiene operacion completa de banca y recupera Reportes.
  SUPERVISOR recupera Finanzas, Reportes y Deportes con alcance de cajeros asignados.
- Contrato `src/utils/masterConfig.ts` ampliado para:
  - `recharge_limits:<adminId>`
  - `admin_operational_limits:<adminId>`
  - `sys_alerts_v4`
- Panel MASTER agregado en `src/views/Dashboard.tsx`:
  - prueba de servidor y latencia,
  - refrescar snapshot,
  - topes global/master de recarga,
  - limite operacional de pago cajero,
  - alertas con filtros y marcar como leida.
- Acciones sensibles principales movidas a `supabase/functions/admin-user-command`:
  - bloquear/desbloquear banca,
  - eliminar banca con cascada,
  - regenerar credenciales,
  - resetear clave,
  - actualizar comision,
  - asignar cajeros a supervisor.
- Cliente web `src/utils/adminCommands.ts` para invocar comandos administrativos de servidor.
- Login web conectado a `auth-legacy-login` para obtener JWT real de Supabase.
- `admin-user-command` desplegado en Supabase real `unhoulkujbtsypccpirc` con `verify_jwt=true`.
- Pruebas agregadas para permisos y claves nuevas de master config.
- Contratos nuevos para estructura administrativa tipo VoloRed:
  - `src/utils/roleParity.ts`
  - `src/utils/userFeatureAccess.ts`
  - `src/utils/recargasRapidasCredentials.ts`
  - `src/utils/lotteryLimitStructure.ts`
  - `src/utils/cashierScope.ts`
- ADMIN ahora tiene secciones visibles para:
  - Comisiones por cajero/supervisor.
  - Cierre y listado automatico.
  - Limites por estructura general/loteria/cajero/jugada.
  - Monitoreo de cajeros con sheet filtrado por cajero.

## Arquitectura Web Relevante

- `src/components/AppShell.tsx`
  - Controla menu por rol desde la misma matriz que `src/App.tsx`.
  - No contiene selector de perfil demo ni cambio rapido de usuarios.
  - MASTER, ADMIN y SUPERVISOR muestran pantallas distintas segun jerarquia Android.
- `src/views/Dashboard.tsx`
  - Sigue monolitico.
  - Maneja usuarios, cajeros, supervisores, tickets, ganadores, resultados, limites,
    recargas, deportes, finanzas, cuadre, auditoria y ahora panel MASTER.
- `src/utils/supabase.ts`
  - Servicio de datos web principal.
  - Aun contiene rutas directas/legacy que deben reducirse.
- `src/utils/masterConfig.ts`
  - Fuente web para contratos compartidos de `lotterynet_master_state`.
- `src/utils/adminCommands.ts`
  - Entrada web para acciones sensibles por Edge Function.
- `src/components/admin/AdminCommissionsPanel.tsx`
  - Edicion visible de comisiones por usuario del admin.
- `src/components/admin/CashierOperationSheet.tsx`
  - Detalle operativo filtrado por cajero desde Monitoreo.
- `src/components/admin/AdminLotteryLimitsPanel.tsx`
  - Estructura de limites tipo VoloRed.
- `src/components/admin/ClosingAutomationPanel.tsx`
  - Configuracion de cierre/listado automatico.

## Brechas Pendientes Por Perfil

### MASTER

- Android `MasterDashboardActivity` organiza MASTER en: Bancas, Credenciales, Servidor/Nube, Recargas Master y Auditoria.
- La web refleja esa jerarquia desde Resumen/Centro MASTER, Bancas y Admins, y Auditoria.
- La web ya tiene centro MASTER basico, pero no replica completo `MasterCloudSyncCoordinator`.
- La prueba de servidor mide llamada/latencia, pero falta salud tecnica profunda.
- Falta panel completo de credenciales/proveedor de recargas rapidas si ese control debe vivir en web.
- Falta control maestro completo de sportsbook si se decide exponerlo dentro del Centro MASTER, no como acceso operativo suelto.
- Falta OTA/versiones Android desde web si se quiere operacion maestra total.
- `admin-user-command` esta desplegado y exige JWT; falta probar flujo exitoso con credenciales reales.

### ADMIN

- Android ADMIN expone operacion de banca: ventas en Android, tickets, resultados, recargas,
  cuadre/finanzas, reportes, cobros, monitoreo, supervisores, limites, sistema y auditoria.
- La web cubre la parte administrativa sin POS: cajeros, supervisores, monitoreo, deportes,
  tickets, ganadores, resultados, limites, finanzas, cuadre, reportes y auditoria.
- Ya existen comision visible por usuario, `recargaTxLimit`, asignacion supervisor, resultados normal/Pick y varias rutas de config.
- Se agrego estructura tipo VoloRed para limites general/loteria/cajero/jugada y cierre/listado automatico.
- Monitoreo de cajeros ya abre detalle filtrado al cajero seleccionado para tickets, cobros, limites, recargas y datos.
- Falta confirmar que todos los limites, pagos, modos y loterias deshabilitadas se escriben siempre
  en la fuente moderna que Android consume.
- Falta centro de alertas dedicado a ADMIN.
- Falta separar una pantalla `Sistema` propia de ADMIN comparable a `AdminConfigActivity`;
  hoy parte de esa configuracion vive en Limites y en el dashboard.
- Algunos flujos de crear/editar usuarios aun deben moverse a Edge Functions.

### SUPERVISOR

- Android SUPERVISOR expone Mis cajeros, Monitoreo, Finanzas, Reporte, Tickets,
  Resultados, Deportes si esta habilitado e Impresora.
- Web cubre: monitoreo, deportes, tickets, resultados, finanzas, cuadre y reportes.
- Falta alertas del grupo supervisado.
- Falta reforzar reportes por cajeros asignados con el mismo criterio operativo de Android
  para comisiones, recargas y deportes.

### CAJERO

Fuera de alcance confirmado. La web no se esta convirtiendo en POS/cajero.
Venta de tickets, impresion y flujo completo de cajero siguen en Android.

## Contratos Compartidos

Fuente moderna objetivo: `lotterynet_master_state`.

Claves principales:

- `cashier_limits:<adminId>`
- `cashier_prize_payouts:<adminId>`
- `system_modes:<adminId>`
- `manual_disabled_lotteries:<adminId>`
- `recharge_limits:<adminId>`
- `admin_operational_limits:<adminId>`
- `sys_alerts_v4`
- `recargas_rapidas_credentials_v1`
- `lottery_limit_structure_v1`
- `closing_automation:<adminId>`

Regla objetivo:

- Leer fallback legacy solo para migracion.
- Escribir siempre por funciones/contrato moderno.
- Evitar que la web modifique `lotterynet_users_state` directo para acciones sensibles.

## Seguridad

`admin-user-command` centraliza los comandos mas sensibles y escribe auditoria.
En produccion esta configurada con `verify_jwt=true`; el actor se resuelve desde JWT de Supabase
y no desde `actorRole` enviado por el navegador. La compatibilidad sin JWT queda solo para entorno local
si se define `LOTTERYNET_ALLOW_LEGACY_ADMIN_COMMANDS=true` y se sirve la funcion sin verificacion JWT.

## Pruebas Ejecutadas

- `npm test -- src/utils/masterConfig.test.ts src/utils/userMapping.test.ts src/utils/navigationPermissions.test.ts src/utils/authSession.test.ts src/utils/adminCommands.test.ts`
  - 16 pruebas pasaron.
- `npm run build`
  - Compilo correctamente.
  - Queda solo advertencia de chunk grande de Vite.
- `npm test -- src/utils/roleParity.test.ts src/utils/navigationPermissions.test.ts src/utils/userFeatureAccess.test.ts src/utils/recargasRapidasCredentials.test.ts src/utils/lotteryLimitStructure.test.ts src/utils/cashierScope.test.ts`
  - 17 pruebas pasaron.
- QA navegador local ADMIN por sesion inyectada:
  - Menu muestra Comisiones y Cierre y Listado.
  - Dashboard muestra Panel admin.
  - Comisiones abre panel de cajeros/supervisores.
  - Cierre y Listado abre panel de cierre/listado automatico.
- `npx --no-install supabase functions deploy admin-user-command --project-ref unhoulkujbtsypccpirc --use-api`
  - Despliegue completado.
- Prueba sin JWT a `admin-user-command`
  - Responde 401, correcto para produccion.
- Login real MASTER con `auth-legacy-login`
  - Responde 200 y entrega JWT.
- Prueba segura de `admin-user-command` con JWT MASTER y destino falso
  - Responde 404 `Usuario destino no encontrado`, correcto: autenticacion/autorizacion pasaron sin modificar datos.

## Pruebas Pendientes

- Probar `admin-user-command` con una accion reversible sobre una banca QA o usuario de prueba.
- Probar login ADMIN real y permisos de bloqueo por rol.
- Probar navegador por perfil:
  - MASTER: resumen/centro master, bancas/credenciales, servidor/nube, recargas master y auditoria.
  - ADMIN: cajeros, supervisores, monitoreo, deportes, tickets, ganadores, resultados normal/Pick, limites, finanzas, cuadre, reportes y auditoria.
  - SUPERVISOR: monitoreo, deportes, tickets, resultados normal/Pick, finanzas, cuadre y reportes.
- Confirmar en Android que cambios web se reflejan:
  - tope de recarga,
  - limite cajero,
  - modo normal/Pick,
  - loteria deshabilitada,
  - comision.
- Ejecutar pruebas Android relevantes:
  - `UserAccountsFormattingTest`
  - `RecargasUiContractsTest`
  - `OperationalReportContractsTest`
  - `SportsbookActivityContractsTest`
  - `./gradlew testDebugUnitTest`

## Siguiente Orden Recomendado

1. Probar login real MASTER/ADMIN y ejecutar un comando reversible.
2. Confirmar desde Android cada clave compartida que la web escribe.
3. Regenerar mapa `understand` web y actualizar este documento con nodos reales.
4. Migrar los ultimos guardados directos de usuarios/config a comandos de servidor.
5. Separar `Dashboard.tsx` por modulos antes de seguir agregando pantallas.
