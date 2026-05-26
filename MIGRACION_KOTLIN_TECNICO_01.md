# Migracion Kotlin Tecnico 01

## Alcance

Este documento resume el estado actual de la app web embebida para preparar la Fase 1 de migracion a Kotlin.

Se enfoca en:

- login y sesion
- venta
- almacenamiento local
- sync con Supabase

Archivo fuente principal revisado:

- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html)

## Hallazgos base

- `index.html` sigue siendo el nucleo operativo principal.
- El login, la sesion, la venta, la presencia, el realtime y los resultados viven en el mismo archivo.
- Python confirmo estas funciones clave:
  - `doLogin()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8427)
  - `lsSaveSession()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8749)
  - `preloadRemoteAdminState()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:5003)
  - `runLiveRemoteRefresh()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8885)
  - `markCurrentUserPresence()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:10298)
  - `posRender()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11117)
  - `posEnterMonto()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11329)
  - `syncR()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:17036)
  - `sbUpsert()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4597)

## Login y sesion actual

### Claves principales de sesion

Definidas en:

- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7832)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7833)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7834)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7835)

Valores:

- `LOGIN_STORE_KEY = lot_login_saved_v1`
- `ACTIVE_SESSION_KEY = lot_active_session_v1`
- `SESSION_STATE_KEY = lot_session_state_v1`
- `TURNO_START_KEY = lot_turno_start_v1`

### Flujo actual de login

Punto principal:

- [doLogin()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8427)

Lo que hace hoy:

- inicia reloj confiable
- carga usuarios locales
- intenta migraciones de secretos/credenciales
- baja `sys_users_v4` desde Supabase
- hace merge entre cloud y local
- prioriza local para varias ramas de datos
- valida usuario y clave
- arma contexto por rol
- persiste sesion local
- deja listo el estado para render y sync

### Persistencia de sesion

Puntos visibles:

- guardar usuario recordado en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8035)
- guardar sesion activa en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8043)
- leer sesion activa en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8101)
- guardar turno en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8127)
- guardar `SESSION_STATE_KEY` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8144)
- limpiar sesion en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8162)
- guardar snapshot general de sesion en [lsSaveSession()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8749)

### Implicacion para Kotlin

La migracion no debe empezar por UI sola. Antes hay que modelar:

- `Session`
- `SavedLogin`
- `ActiveSession`
- `SessionState`
- `TurnoState`

Esos modelos deben vivir en Kotlin y luego decidir que parte sigue en `SharedPreferences`, que parte va a almacenamiento seguro y que parte queda solo en memoria.

## Almacenamiento local detectado

Python encontro estas claves explicitas de `localStorage`:

- `bv_boot`
- `bv_clock_state`
- `lot_active_session_v1`
- `lot_error_log_v1`
- `sys_dop_rate`
- `sys_presence_v1`
- `sys_users_backup_deleted_v1`
- `sys_users_backup_v1`
- `sys_users_v4`

Ademas, por lectura del archivo se confirmaron claves relevantes no listadas en el barrido simple de literales:

- `lot_login_saved_v1`
- `lot_session_state_v1`
- `lot_turno_start_v1`
- `lot_results_cache_by_day`

Referencias visibles:

- `lot_results_cache_by_day` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4002)
- `sys_users_v4` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4236)
- `sys_users_backup_v1` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4860)
- `sys_presence_v1` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4867)

### Implicacion para Kotlin

Hay que separar el almacenamiento en tres grupos:

1. sesion y autenticacion
2. cache funcional
3. datos de negocio y sync

Si se migra sin separar eso, se va a duplicar caos entre JS y Kotlin.

## Sync con Supabase

### Entrada remota

Funcion:

- [preloadRemoteAdminState()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:5003)

Uso:

- obtiene claves remotas del admin
- baja estado remoto detallado antes de refrescar sesion

### Pull live

Funcion:

- [runLiveRemoteRefresh()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8885)

Comportamiento:

- corre para `admin` y `cashier`
- usa `ownerId`
- evita refrescos paralelos
- hace timeout corto
- recarga estado de sesion
- repinta pantallas vivas o agenda refresh de venta

### Push live

Funcion relacionada:

- `sbSyncUp()` en [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:8926)

Claves remotas confirmadas:

- `bv_t3_<ownerId>` para pool de tickets
- `bv_r3_<ownerId>` para recargas
- `bv_p3_<ownerId>` para premios
- `bv_lim_<ownerId>` para limites

Referencias de prefijos:

- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4602)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4882)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4883)
- [index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4884)

Persistencia remota base:

- [sbUpsert()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:4597)

### Presencia

Funcion:

- [markCurrentUserPresence()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:10298)

Comportamiento:

- solo para `admin` y `cashier`
- guarda presencia con `online`, `lastSeenAt`, `adminId` y `role`

### Implicacion para Kotlin

La capa Kotlin debe tener desde el inicio:

- `SyncRepository`
- `PresenceRepository`
- `UsersRepository`
- `TicketsRepository`

No conviene empezar migrando pantalla nativa sin tener esos contratos.

## Venta actual

### Render de venta

Funcion:

- [posRender()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11117)

Estado actual:

- ya se optimizo para usar caches de DOM
- evita `innerHTML` y `textContent` directos repetidos
- calcula exposicion y limites por jugada
- actualiza info contextual y caja de monto/numero

### Confirmacion de venta

Funcion:

- [posEnterMonto()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11329)

Estado actual:

- ya usa cache diario para el total vendido por actor
- ya no depende del barrido bruto de `allTkts` para ese tope

### Implicacion para Kotlin

La primera pantalla de negocio a migrar debe ser `venta`, pero no como copia visual solamente. Debe incluir:

- modelos de jugada
- validador de numero/tipo
- calculo de limites
- cache de exposicion del dia
- confirmacion de venta
- disparo de sync critico

## Resultados

Funcion principal detectada:

- [syncR()](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:17036)

Dato importante:

- resultados usan cache local por dia
- ya existe lectura fresca desde Supabase y logica de auto-refresh

Esto no debe ser lo primero en migrar. Conviene moverlo despues de login y venta.

## Orden tecnico recomendado

### Paso 1

Crear modelos Kotlin:

- `UserAccount`
- `AdminAccount`
- `CashierAccount`
- `SessionState`
- `TurnoState`
- `Ticket`
- `Play`
- `Lottery`
- `Result`
- `PresenceState`

### Paso 2

Crear repositorios Kotlin:

- `SessionRepository`
- `UsersRepository`
- `SyncRepository`
- `PresenceRepository`
- `ResultsRepository`
- `SalesRepository`

### Paso 3

Mover primero:

- login nativo
- shell nativo por rol

### Paso 4

Mover despues:

- venta

## Proximo documento

El siguiente documento tecnico debe detallar:

- modelo de datos de `Ticket`
- modelo de `Admin` y `Cashier`
- dependencias exactas de `venta`
- pasos para crear `SessionRepository` en Kotlin
