# Plan De Migracion A Kotlin

## Estado actual

- La app funciona hoy con un `WebView` grande y un `index.html` central.
- `index.html` concentra venta, resultados, tickets, finanza, roles, impresion y sync.
- Esta base ya sirvio para sacar producto, pero el costo de mantenerla y optimizarla sigue creciendo.

## Objetivo

Migrar de forma gradual hacia una app Android en `Kotlin` nativo, sin apagar la version actual ni romper:

- flujo de venta
- resultados
- impresion termica
- sync con Supabase
- roles `master`, `admin` y `cashier`

## Principios

- No rehacer todo de golpe.
- Mantener la app actual operativa mientras se migra.
- Mover primero las pantallas donde mas se nota rendimiento y uso diario.
- Separar logica de negocio de UI antes de mover demasiadas pantallas.
- Dejar coexistir `WebView` y pantallas nativas por etapas.

## Orden recomendado

### Fase 0: Congelar y ordenar la base actual

Objetivo: reducir riesgo antes de migrar.

- mantener backup del estado actual
- seguir usando chequeos `Node` y release check
- documentar modulos y responsabilidades del `index.html`
- identificar funciones criticas:
  - login y restauracion de sesion
  - venta
  - tickets
  - resultados
  - impresion
  - sync y presencia
  - finanza

Salida esperada:

- mapa funcional del `index.html`
- lista de dependencias entre UI, storage y sync

### Fase 1: Crear arquitectura nativa sin mover todo todavia

Objetivo: preparar la base Kotlin.

- definir capas:
  - `ui`
  - `domain`
  - `data`
  - `printing`
  - `sync`
- introducir modelos Kotlin para:
  - usuario
  - ticket
  - jugada
  - loteria
  - resultado
  - presencia
  - recarga
- crear repositorios base:
  - `SessionRepository`
  - `LotteryRepository`
  - `TicketRepository`
  - `ResultsRepository`
  - `SyncRepository`
- centralizar acceso a Supabase desde Kotlin
- mantener `WebView` para lo que aun no se haya migrado

Salida esperada:

- proyecto Android listo para recibir pantallas nativas
- contratos claros para negocio y sync

### Fase 2: Migrar primero login y shell principal

Objetivo: sacar del `WebView` la entrada de la app.

- pantalla nativa de login
- guardado seguro de credenciales y sesion
- enrutado inicial por rol:
  - `master`
  - `admin`
  - `cashier`
- contenedor principal nativo para menu y navegacion

Por que empezar aqui:

- reduce dependencia del HTML desde el arranque
- ordena sesion y seguridad
- simplifica la migracion del resto

### Fase 3: Migrar venta

Objetivo: mover la pantalla mas sensible al lag.

- teclado de venta nativo
- seleccion de loteria nativa
- jugadas y monto nativos
- validaciones y limites diarios en Kotlin
- cache de exposicion y tickets recientes en repositorios nativos
- conservar integracion con el backend actual

Por que esta fase va primero entre las pantallas de negocio:

- es donde mas se siente rendimiento
- es el flujo mas importante para el cajero

### Fase 4: Migrar tickets e impresion

Objetivo: hacer robusta la salida termica.

- lista de tickets nativa
- detalle de ticket nativo
- preview solo para roles permitidos
- impresion termica y formatos desde Kotlin
- cola local de reintentos de impresion si aplica

### Fase 5: Migrar resultados

Objetivo: quitar del `WebView` la lectura y render de resultados.

- lista de loterias y horarios
- resultados del dia y dias recientes
- auto refresh inteligente por cierre
- cache fresca sin depender de `localStorage`

### Fase 6: Migrar admin y finanza

Objetivo: mover la capa mas compleja despues de estabilizar cajero.

- resumen por cajero
- tickets por cajero
- finanza y cuadre
- recargas y limites
- vistas operativas del admin

### Fase 7: Reducir el WebView a legado o retirarlo

Objetivo: dejar solo lo que no valga la pena migrar o apagarlo por completo.

- mover las ultimas pantallas pendientes
- apagar dependencias de `index.html`
- retirar logica duplicada

## Primera iteracion recomendada

La primera iteracion real deberia atacar esto:

1. documentar el flujo actual de login, venta y sync
2. crear modelos Kotlin y repositorios base
3. construir login nativo
4. construir shell nativo por rol
5. dejar `venta` como siguiente modulo a migrar

## Riesgos

- duplicar logica entre JS y Kotlin durante la transicion
- diferencias de validacion entre venta web y venta nativa
- dependencia fuerte de `localStorage` en algunas rutas actuales
- impresion y preview con reglas por rol
- sync en vivo entre admin y cajero si no se centraliza bien

## Reglas para no romper produccion

- no borrar el flujo web hasta que exista el equivalente nativo estable
- migrar por modulo, no por archivos
- probar cada fase con `release` real
- mantener test/checklist para:
  - login
  - venta
  - ticket
  - resultados
  - impresion
  - sync admin/cajero

## Siguiente paso inmediato

Empezar con un documento tecnico corto que describa:

- que guarda hoy `localStorage`
- que rutas usa Supabase
- que funciones forman el flujo de login
- que funciones forman el flujo de venta

Despues de eso, crear el primer paquete Kotlin para `session`, `models` y `repositories`.
