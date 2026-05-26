# Migracion Kotlin Tecnico 02

## Objetivo

Definir el primer modelo de datos Kotlin y la primera capa de repositorios para empezar la migracion sin seguir acoplando todo a `index.html`.

## Entidades base

### Roles

- `MASTER`
- `ADMIN`
- `CASHIER`
- `UNKNOWN`

Esto reemplaza comparaciones repetidas con strings en JS.

### Usuario

Base comun:

- `id`
- `user`
- `role`
- `displayName`
- `active`
- `adminId`
- `adminUser`
- `banca`
- `territory`
- `phone`
- `lastSeenAtEpochMs`

### Sesion

- `savedLogin`
  - usuario recordado
  - bandera `remember`
- `activeSession`
  - `role`
  - `userId`
  - `username`
  - `adminId`
  - `adminUser`
  - `banca`
  - `startedAtEpochMs`
- `sessionSnapshot`
  - `activeSession`
  - `currentScreen`
  - `turnoStartEpochMs`
  - `lastSyncEpochMs`
  - `isOnline`

### Venta

#### Jugada

- `number`
- `playType`
- `amount`
- `lotteryId`
- `lotteryName`
- `straightDigits`
- `boxDigits`

#### Ticket

- `id`
- `serial`
- `sellerId`
- `sellerUser`
- `adminId`
- `adminUser`
- `role`
- `createdAtEpochMs`
- `plays`
- `subtotal`
- `discount`
- `total`
- `status`

### Resultados

- `lotteryResult`
  - `lotteryId`
  - `lotteryName`
  - `date`
  - `first`
  - `second`
  - `third`
  - `pick3`
  - `pick4`
  - `source`
  - `fetchedAtEpochMs`

### Presencia

- `presenceState`
  - `role`
  - `user`
  - `adminId`
  - `online`
  - `lastSeenAtEpochMs`

## Repositorios iniciales

### `SessionRepository`

Responsable de:

- leer y guardar usuario recordado
- leer y guardar sesion activa
- leer y guardar snapshot de sesion
- limpiar sesion local

### `UsersRepository`

Responsable de:

- devolver admins y cajeros
- buscar por `id` o `user`
- guardar cache local de usuarios

### `SalesRepository`

Responsable de:

- guardar ticket
- listar tickets del dia
- filtrar por actor
- calcular exposicion local

### `ResultsRepository`

Responsable de:

- cache local por fecha
- lectura remota
- invalidacion de cache

### `PresenceRepository`

Responsable de:

- guardar presencia
- consultar presencia actual de un usuario

### `SyncRepository`

Responsable de:

- pull inicial
- push critico
- refresh live
- marcar ultimo sync

## Reglas de implementacion inicial

- no mover UI todavia
- no duplicar reglas de negocio dentro de `MainActivity`
- crear modelos puros, sin dependencias de WebView
- permitir que los repositorios usen `SharedPreferences` en la primera iteracion
- dejar listo el camino para reemplazar ese storage local despues

## Primera ejecucion real

En esta iteracion se crea:

- paquete `core.model`
- paquete `core.repository`
- paquete `core.storage`

Con eso se prepara el suelo para:

1. login nativo
2. shell nativo por rol
3. migracion del flujo de venta

## Siguiente paso

Despues de esta base, el siguiente bloque a implementar debe ser:

- `LocalUsersRepository`
- `LocalSalesRepository`
- `LocalResultsRepository`

Y luego la primera pantalla nativa:

- login
