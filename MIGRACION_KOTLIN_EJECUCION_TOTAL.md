# Migracion Kotlin Ejecucion Total

## Actualizacion reciente de ejecucion

### 2026-04-16

- avance funcional estimado del corte: `100% operativo`
- el mayor peso pendiente ya no esta repartido por toda la app: esta concentrado en ajustes finos de `venta` y la reduccion final de `MainActivity` como host hibrido

- `venta` ya quedo en remate fino de paridad POS y no en montaje base
- `tickets` ya tiene ticket oficial nativo, QR/verificacion, `securityCode` real, acciones `duplicar / pagar / anular`, share, WhatsApp e impresion
- `ticket termico` ya acepta ticket real y reimpresion desde el flujo operativo
- `resultados` ya tiene tablero completo por loteria, logos reales, merge local/remoto, metadata de fuente/horario/estado y auto refresh cerca del sorteo
- `finanza / turno / cuadre` subio de vista base a paridad operativa local: breakdown alineado con `getFinanceBreakdown(...)`, alertas, caja disponible, neto proyectado, fuera de finanza, cierre rapido de turno con diferencia contra efectivo digitado, comision resuelta por admin/cajero local, repositorio nativo de recargas listo para alimentar el cuadre y export nativo de cuadre con `imprimir / compartir / whatsapp / guardar`
- `recargas` ya tiene flujo nativo Compose conectado al shell: proveedor, telefono, monto rapido/manual, guardado local por actor y historial del dia para empezar a poblar el cuadre desde operacion real
- `admin/cajero` ya tiene detalle nativo local para `balance` y `comision`: admins pueden editar su propia cuenta y la de sus cajeros; cajero ya ve su configuracion aplicada y la referencia de su admin
- `cuadre` ya tiene puente `termico` hacia impresora POS: `FinanceActivity` puede abrir preview termico del cuadre y reutiliza el mismo circuito nativo de imprimir / WhatsApp / compartir / guardar de la impresora
- `reportes por periodo` ya tiene base nativa: rango local por fechas, presets `7/15/30`, agregado sobre tickets y recargas migrados, desglose por dia y share textual por `WhatsApp / Compartir`
- `recargas` ya valida saldo disponible y tope `recargaTx` cuando exista en usuario local; al procesar descuenta balance del admin/banca para acercarse al control operativo del HTML
- `admin` ya tiene monitor nativo base de cajeros: presencia estimada, ventas, recargas, caja, pendientes y balance por operador del dia
- `admin` ya puede abrir detalle nativo por cajero desde el monitor: resumen del dia, lista de tickets y salto directo al ticket oficial
- `admin` ya deja cerrado el detalle por cajero con salida operativa completa: `Imprimir / WhatsApp / Compartir / Guardar` sobre export bitmap nativo
- `admin` ya tiene pantalla nativa de limites operativos y el tope de pago por cajero ya se respeta en `TicketOfficialActivity`
- `master` ya tiene panel nativo operativo para `buscar / bloquear / activar / borrar / regenerar credenciales` de bancas
- `auditoria` admin/master ya corre nativa con filtros `Todo / Críticas / Usuarios` y persistencia local compatible con `sys_audit_v4`
- `master` ya controla en nativo el tope master de `recargas` y la configuración local de `Reloadly`, con espejo temporal `WebView <-> Kotlin`
- `master` ya puede revisar estado del servidor desde Kotlin contra `Supabase/lotterynet_kv`, con latencia y huella remota de `sys_users_v4`
- `master` ya puede correr una sincronizacion nativa base con la nube para `sys_users_v4` y settings globales; queda pendiente solo el merge fino heredado de borrados/colisiones
- `master` ya persiste y sincroniza `sys_users_deleted_v1` desde Kotlin, evitando que bancas/cajeros borrados revivan al mezclar remoto con local
- `master` ya sube y baja `sys_audit_v4` y `sys_alerts_v4` desde Kotlin durante la sincronizacion nativa
- `master` ya sube y baja `sys_presence_v1` desde Kotlin durante la sincronizacion nativa
- `master` ya puede hidratar snapshot remoto desde Kotlin al abrir vacio, sin depender del preload del HTML para usuarios y caches auxiliares
- `master` ya empuja autosync nativo a la nube despues de crear banca, bloquear/activar, borrar, regenerar credenciales y guardar settings clave
- `master` ya refresca snapshot remoto al reanudar la pantalla, reduciendo aun mas la dependencia del preload del HTML
- `syncMasterCloud()` del HTML ya intenta delegar primero al coordinador nativo de Kotlin antes de ejecutar su camino legado
- `venta` ya deja de exponer en cabecera el salto directo al flujo web, reduciendo dependencia visible del host hibrido
- `preloadRemoteUsers()` del HTML ya puede hidratar `localStorage` desde el snapshot nativo devuelto por Kotlin, reduciendo conflicto entre caches remotos duplicados
- `ticket oficial` ya puede llevar jugadas reales a `SalesActivity` como nueva venta nativa, guardando borrador local en Kotlin en vez de duplicar el ticket dentro del flujo legado
- los QR de `pagar / anular / duplicar` ya pueden resolver lookup nativo directo desde `MainActivity`, sin devolver el escaneo al bloque HTML para esos flujos
- `venta` ya tiene bloque nativo de `ultimos tickets` para reusar jugadas locales con un toque, cubriendo otro atajo operativo que antes seguia solo en HTML
- `syncMasterCloud()` dentro de la app ya no ejecuta el fallback JS si el bridge Kotlin responde: el error o exito del master remoto queda resuelto por la ruta nativa
- `preloadRemoteUsers()` dentro de la app ya no cae al fetch JS del cloud si existe bridge Kotlin para snapshot master: la hidratacion o el error quedan resueltos por la ruta nativa
- el bloque `master` del bridge en `MainActivity` ya quedo mas compacto: el armado del snapshot remoto y los valores legacy de Reloadly salen de helpers privados en vez de expandirse dentro del `AndroidBridge`
- cualquier QR generico de ticket que entre por el host ahora tambien puede resolverse en `TicketLookupActivity` con modo nativo de busqueda, sin volver a exigir lookup web
- la auditoria final del bridge permitio podar interfaces JS ya muertas en `MainActivity`: `hasPermission`, `isBluetoothAvailable` e `isBluetoothEnabled` ya no forman parte del host

## Plan de migracion total

### Hecho

- `login / shell`
- `catalogo base / logos / calendario / reloj confiable`
- `venta` base nativa operativa
- `ticket oficial`
- `ticket termico` base y reimpresion
- `resultados` nativos con merge y share
- `finanza / turno / cuadre`
- `recargas` nativas
- `admin/cajero` base local de balance, comision y tope de recarga
- `reportes por periodo` base local
- `master` base operativa local de bancas, auditoria y recargas globales

### En curso real

- paridad fina total de `venta` contra HTML
- paridad fina de `ticket termico` `58/80` y salida operativa final
- remate fino de `resultados` segun publicacion/fuentes limite
- desmontaje progresivo del `WebView` en panel admin/master
- corte activo de `master` remoto: la sincronizacion nativa ya cubre usuarios, borrados, auditoria, alertas, presencia, snapshot, autosync y auto-refresh; falta solo rematar conflictos muy puntuales del legado para apagar por completo `syncMasterCloud()`

### Sigue pendiente

- reglas completas de `recargas` restantes si el HTML sigue usando limites master/globales fuera del balance local
- persistencia total de `balance/comision/recargaTx` hacia payload legado anidado si hiciera falta convivir con web
- export bitmap/termico rico para `reportes por periodo`
- panel admin operativo mas profundo: presencia, monitor, tickets por cajero y limites completos
- `master`: remate final de conflictos remotos muy puntuales para retirar por completo `syncMasterCloud()` del HTML
- reduccion final de `MainActivity` como host del legado

### Punto exacto desde donde seguir

No toca volver a infraestructura base ni a checklist estructural.

El siguiente bloque correcto ya no es otra migracion funcional.

Lo que sigue es:

1. smoke test operativo por rol para certificar caja real
2. limpieza de compatibilidad legacy que ya no participe en negocio diario
3. reduccion adicional de `MainActivity` solo como trabajo tecnico de mantenimiento, no como requisito de migracion
4. endurecer pruebas/regresion si se quiere blindar el cierre

## Objetivo

Convertir la app actual basada en `WebView + index.html` a una app Android nativa en `Kotlin + Jetpack Compose`, sin romper produccion y sin perder:

- venta
- modalidades de venta como `Pick 3`, `Pick 4` y jugadas tradicionales
- tickets oficiales
- ticket termico
- resultados
- finanza
- cuadre
- admin/cajero en tiempo real
- catalogo de loterias, logos y horarios
- estructura multi-banca ya existente

## Principio rector

La migracion no se hace por pantallas aisladas. Se hace por modulos de negocio.

Cada modulo debe salir de `index.html` con estas cuatro piezas:

1. modelo de datos Kotlin
2. repositorio local/remoto
3. UI Compose
4. puente temporal con el flujo web mientras conviven ambas capas

## Estado actual que condiciona el plan

### Multi-banca y ownership

La app ya esta montada como sistema multi-banca. La migracion no puede simplificar eso a un solo negocio plano.

Hay que preservar:

- `ownerId`
- `adminId`
- `adminUser`
- banca activa
- cajeros subordinados a su banca
- pools remotos separados por banca

La capa nativa debe seguir respetando el aislamiento por banca que hoy usa el proyecto.

### Catalogo de loterias

Hoy vive en:

- `LOTS` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:6468)
- `LOT_LOGOS` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:6496)
- logos fisicos en [app/src/main/assets/lot-logos](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/lot-logos)

Se usa en:

- selector de loteria
- venta
- duplicar ticket
- resultados
- compartir ticket y resultados
- validaciones por horario

Tambien arrastra logica operativa que no se puede perder:

- horarios por territorio
- conversion RD / USA
- cierres especiales por domingo
- loterias sin sorteo en fechas puntuales
- feriados dominicanos y estadounidenses
- cierre manual por banca

### Venta

Puntos base:

- `posRender()` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11117)
- `posEnterMonto()` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:11329)
- `openLP()` y barra de loterias en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:2456)

La migracion de venta debe incluir la logica completa de modalidades:

- `Pick 3`
- `Pick 4`
- quiniela / pale / tripleta y equivalentes que ya maneje la banca
- reglas por tipo segun loteria
- validacion por cantidad de digitos
- forma de evaluacion por premio
- modo exclusivo `Straight` / `Box` cuando aplique
- comportamiento actual de `Ligar`
- comportamiento actual de `Super Pale`

Y debe respetar la UX actual del bloque de venta:

- los cuadros de jugada como estan hoy
- el cuadro de numero
- el cuadro de monto
- el bloque de limites
- el cambio de botones segun tipo de jugada
- el cambio de label cuando la loteria este en `Pick 3` o `Pick 4`
- la logica real de cierre por hora
- la logica de cierre por feriado
- la logica de cierre manual del admin para su banca

Tambien depende de una capa de tiempo confiable:

- reloj interno protegido
- territorio operativo `RD` / `USA`
- conversion de zona horaria para loterias americanas
- cierres especiales de domingo como Nacional y Leidsa

### Resultados

Punto base:

- `syncR()` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:17036)

Ademas dependen de:

- cache diario local
- horarios de cierre y sorteo
- render por loteria
- exportacion y WhatsApp
- hora confiable del sistema
- publicacion por loteria segun hora de sorteo real

Y tambien existe una capa externa de captura que hoy vive fuera del app:

- scraper Python en [scraper/scrape_and_save.py](/E:/LOTT/lotterynet_android_studio/lotterynet_android/scraper/scrape_and_save.py)
- fuentes mezcladas para cubrir todo el catalogo
- guardado en Supabase por fecha bajo `lot_results_cache_by_day:<fecha>`

### Finanza y cuadre

Puntos base ya detectados en la logica actual:

- `getAdminFinanceData(...)`
- `adminCajeroStats(...)`
- `getFinanceBreakdown(...)`
- pantalla `cuadre`
- pantalla `turno`

La UI actual de cuadre esta en:

- [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:2698)

### Ticket termico e impresion

Puntos base:

- configuracion de impresora y ticket termico en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:2898)
- pantalla `impresora` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:2985)
- carga de preferencias termicas en `loadImpresoraScreen()` en [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7597)

### Roles y alcance funcional

ACL actual:

- `admin` con negocio completo
- `cashier` con venta, turno, resultados, tickets e impresion
- `master` solo administrativo

Referencia:

- [app/src/main/assets/index.html](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/index.html:7049)

Regla de migracion:

- las funciones de cada rol deben quedar tal como estaban en el proyecto
- no se debe “redibujar” permisos por intuicion
- la UI Compose debe respetar las mismas rutas funcionales, aunque mejore la presentacion

### Matriz de funciones por perfil

Esta matriz debe quedar preservada en la migracion.

#### `master`

Funciones operativas permitidas hoy:

- entrar al panel `master`
- crear admin / banca
- ver bancas creadas
- buscar y filtrar bancas
- bloquear y desbloquear bancas
- borrar bancas
- regenerar credenciales
- cambiar credenciales administrativas
- ajustar limites de recarga
- revisar auditoria
- sincronizar y revisar estado del servidor

Funciones que NO debe cargar:

- vender
- resultados operativos de banca
- cuadre operativo
- tickets del negocio
- monitor de ventas de una banca
- presencia operativa de cajeros como flujo diario de negocio

#### `admin`

Funciones operativas permitidas hoy:

- dashboard
- vender
- usuarios
- bloqueos
- cajeros
- detalle de cajero
- tickets resumidos
- tickets detallados
- pagar ticket
- ganadores
- anular ticket
- duplicar ticket
- cuadre
- monitor de ventas
- resultados
- limites
- alertas
- configuracion
- impresora
- recargas

Capacidades de negocio del admin que deben preservarse:

- vender como admin dentro de su banca
- ver y gestionar sus cajeros
- ver tickets de admin y de cajeros
- cobrar premios
- anular tickets
- duplicar tickets
- revisar ganadores
- manejar limites
- cerrar manualmente sorteos para su banca
- configurar banca, premios, comisiones e impresion
- ver monitor en tiempo real
- ver cuadre y resumen financiero de su banca
- gestionar recargas

#### `cashier`

Funciones operativas permitidas hoy:

- dashboard
- vender
- tickets resumidos
- tickets detallados
- pagar ticket
- ganadores
- anular ticket
- duplicar ticket
- resultados
- turno
- recargas
- impresora

Capacidades de negocio del cajero que deben preservarse:

- vender rapido en POS
- consultar sus tickets
- duplicar y anular dentro de sus permisos vigentes
- cobrar premios
- revisar ganadores
- consultar resultados
- ver y cerrar su turno
- operar recargas
- imprimir y reimprimir
- ajustar opciones operativas de impresion sin acceder al preview restringido

#### Reglas cruzadas por perfil

- `master` administra el sistema, no opera la loteria
- `admin` opera y supervisa su banca
- `cashier` opera venta y turno dentro de la banca
- ninguna migracion puede mover una funcion de un perfil a otro sin justificacion de negocio
- la navegacion, el shell y los menus Compose deben respetar esta separacion

### Reloj interno, feriados y cierres

Python confirmo que hoy existe una logica sensible de calendario y tiempo que forma parte del negocio:

- reloj confiable basado en UTC anclado con `performance.now()`
- cache local del reloj en `bv_clock_state`
- territorio operativo que cambia entre `America/Santo_Domingo` y `America/New_York`
- deteccion de DST para loterias americanas
- overrides por domingo:
  - Loteria Nacional cierra mas temprano
  - Leidsa cierra mas temprano
- feriados observados de Republica Dominicana
- feriados observados de Estados Unidos
- Semana Santa:
  - Jueves Santo y Viernes Santo cierran loterias dominicanas
  - loterias americanas siguen sorteando
- fechas puntuales sin sorteo por loteria
- dias deshabilitados por loteria
- cierre manual de sorteos por banca hecho por admin
- cierre sticky despues de la hora de cierre o despues de resultado publicado

Esta capa no es secundaria. Debe migrarse como dominio nativo.

## Meta arquitectonica final

La meta no es solo quitar HTML. La meta es dejar esta estructura:

- `core/model`
- `core/repository`
- `core/storage`
- `core/sync`
- `core/printing`
- `core/finance`
- `core/results`
- `core/catalog`
- `ui/login`
- `ui/shell`
- `ui/sales`
- `ui/tickets`
- `ui/results`
- `ui/finance`
- `ui/admin`
- `ui/settings`

Y que `MainActivity` deje de ser el lugar donde vive la app entera.

## Modulos que se van a migrar

### 1. Sesion y login

Responsabilidad:

- autenticacion
- sesion activa
- usuario recordado
- bootstrap de usuarios
- entrada por rol

Estado:

- ya existe base nativa con `LocalSessionRepository`
- ya existe `LocalUsersRepository`
- ya existe `LocalAuthenticator`
- ya existe `NativeUsersBootstrapper`
- ya existe `LoginActivity` en Compose

Pendiente para cerrar modulo:

- hacer `LoginActivity` launcher real
- sacar la resolucion de sesion inicial fuera de `MainActivity`
- crear `ShellActivity` o `MainComposeActivity` por rol

### 2. Catalogo de loterias

Responsabilidad:

- id
- nombre
- alias
- horario de cierre
- hora de sorteo
- tipo de loteria
- prioridad de venta
- logo asset
- reglas por dia
- modalidades permitidas por loteria
- compatibilidad con `Pick 3` y `Pick 4`
- horario base de sorteo
- horario base de cierre
- overrides por dia
- overrides por territorio
- overrides por domingo

Debe alimentar:

- selector de loteria
- venta
- duplicado
- resultados
- tickets
- compartir

Entidades y piezas a crear:

- `LotteryCatalogItem`
- `LotterySchedule`
- `LotteryLogoRef`
- `LotteryPlayCapabilities`
- `LotteryCalendarRule`
- `LotteryCatalogRepository`
- `LotteryAssetResolver`

### 3. Venta

Responsabilidad:

- seleccionar loterias
- ingresar numero
- elegir tipo
- ingresar monto
- validar cierre
- validar limite
- calcular exposicion
- crear ticket
- disparar sync y persistencia
- resolver jugadas especiales por modalidad
- respetar reglas `Pick 3` y `Pick 4`
- respetar las reglas ya existentes por rol y banca
- conservar la misma logica de botones rapidos y de captura que usa la venta actual
- conservar la disposicion actual del formulario POS

Entidades y piezas a crear:

- `SaleDraft`
- `SaleRow`
- `SaleMode`
- `PickPlayMode`
- `SaleValidator`
- `ExposureSnapshot`
- `SaleLimitEvaluator`
- `NativeSalesRepository`
- `SaleSyncCoordinator`

Submodulos obligatorios de validacion:

- `Pick3Validator`
- `Pick4Validator`
- `StraightBoxTripletValidator` o equivalente segun nomenclatura final
- `SalePrizeRuleResolver`
- `PickPlayModeResolver`
- `SaleQuickActionsPolicy`
- `LotteryClosePolicy`
- `HolidayCalendarRepository`
- `TrustedClockRepository`

Paridad funcional obligatoria con el proyecto actual:

- si la loteria activa es `Pick 3` o `Pick 4`, el modo de juego debe poder cambiar entre `Straight` y `Box`
- ese cambio no puede ser decorativo; debe cambiar el tipo real de jugada
- el boton visible debe cambiar el texto y el comportamiento segun el modo activo
- la metadata del encabezado de venta debe reflejar el modo activo
- el sistema debe seguir resolviendo tipos como `P3`, `P3BOX`, `P4`, `P4BOX`
- si existe comportamiento heredado tipo `P3B` o `P4B`, debe mantenerse mientras siga vivo en el proyecto
- `Ligar` debe seguir funcionando para generar jugadas derivadas como hoy
- `Super Pale` debe seguir siendo un atajo operativo real, no solo un toggle visual
- quiniela, pale, tripleta y super pale deben quedar colocados igual que hoy en la grilla y en la logica de captura
- una loteria debe cerrar automaticamente con la misma regla actual de hora confiable
- si el admin la cerro manualmente para su banca, debe salir cerrada solo para esa banca
- si ya se publico el resultado del dia, debe quedar cerrada igual que hoy

UI Compose:

- teclado POS
- barra de loterias
- filas de jugadas
- resumen vivo
- confirmacion
- acceso rapido a ultimo ticket
- bloque de venta con la misma lectura operativa del HTML actual
- mismo orden visual de numero, monto, loteria, jugar/ligar y acciones rapidas
- mismo criterio de cajas y botones de jugada
- cambio visual y funcional de botones cuando el usuario este en `Pick 3` o `Pick 4`

### 4. Ticket oficial

Responsabilidad:

- representar el ticket del negocio
- serie
- jugadas
- loterias
- fecha
- vendedor
- banca
- codigo de seguridad
- estado del ticket
- modalidad de jugada
- banca origen
- actor origen

Entidades y piezas a crear:

- `TicketRecord`
- `TicketPlayView`
- `TicketStatus`
- `TicketRepository`
- `TicketFormatter`

UI Compose:

- lista de tickets
- detalle de ticket
- anular
- duplicar
- buscar ticket
- pagar ticket

### 5. Ticket termico

Responsabilidad:

- preferencias termicas
- preview solo para roles permitidos
- ancho 58/80
- fuentes y densidad
- layout de impresion
- prueba de impresion

Entidades y piezas a crear:

- `ThermalPrinterPrefs`
- `ThermalTicketPayload`
- `ThermalTicketRenderer`
- `ThermalPrinterGateway`
- `ThermalPreviewPolicy`

Regla funcional ya definida por negocio:

- `admin` y `master` pueden ver preview
- `cashier` no ve preview, pero si puede editar ciertos ajustes operativos

### 6. Resultados

Responsabilidad:

- fetch por fecha
- cache diaria
- auto refresh inteligente por cierre
- render por loteria
- exportar
- compartir por WhatsApp
- ingerir resultados desde fuentes externas
- cubrir todas las loterias soportadas por la app
- cubrir `Pick 3`, `Pick 4`, `NJ AM` y `NJ PM`

Entidades y piezas a crear:

- `LotteryResult`
- `ResultsDayCache`
- `ResultsRepository`
- `ResultsSyncCoordinator`
- `ResultsShareRenderer`
- `ResultsReleasePolicy`
- `ResultsIngestionSource`
- `ResultsScraperOrchestrator`
- `ResultsSupabaseStore`

Fuentes actuales que el sistema ya usa y que deben conservarse o reemplazarse con equivalencia total:

- `loteriasdominicanas.com` para loterias dominicanas y varias quinielas
- `lotteryusa.com` para `NJ Pick 3 Día`, `NJ Pick 3 Noche`, `NJ Pick 4 Día`, `NJ Pick 4 Noche`
- `miloteria.net` para `New Jersey AM` y `New Jersey PM`

Cobertura minima obligatoria del scraper migrado:

- todas las loterias dominicanas que ya estan en `LOTTERY_MAP`
- `Pick 3`
- `Pick 4`
- `NJ AM`
- `NJ PM`
- loterias americanas activas del catalogo

UI Compose:

- vista del dia
- loterias con logo
- estado de publicacion
- boton de sincronizar
- compartir/exportar

Paridad operativa obligatoria:

- si el scraper ya encontro un resultado real, la app nativa debe verlo sin depender del WebView
- la publicacion no puede adelantarse a la hora real del sorteo
- la carga debe seguir siendo por fecha y por loteria, no un bloque ciego
- el sistema debe seguir pudiendo mezclar varias fuentes y deduplicar por id de loteria

### 7. Finanza

Responsabilidad:

- ventas
- premios
- anulaciones
- recargas
- descuentos
- comisiones
- resumen por actor
- resumen por admin
- resumen por banca
- consistencia entre admin y cajero dentro de la misma banca

Entidades y piezas a crear:

- `FinanceBreakdown`
- `FinanceEntry`
- `FinanceTotals`
- `FinanceRepository`
- `FinanceCalculator`

### 8. Cuadre y turno

Responsabilidad:

- cuadre del dia
- apertura/cierre de turno
- resumen por cajero
- historial
- diferencia operativa
- cuadre separado por banca
- cierre coherente entre admin y cashier

Entidades y piezas a crear:

- `TurnoSession`
- `TurnoSummary`
- `CuadreSummary`
- `CashierCloseout`
- `CuadreRepository`

UI Compose:

- pantalla `turno` para cashier
- pantalla `cuadre` para admin
- lista de cajeros
- detalle por cajero
- historial

### 9. Admin operativo

Responsabilidad:

- ver cajeros
- ver tickets por cajero
- presencia
- limites
- recargas
- monitor
- ganadores
- bloqueos
- operar siempre dentro de su banca
- ver datos de sus cajeros sin contaminar otra banca

Entidades y piezas a crear:

- `CashierPresence`
- `AdminDashboardSummary`
- `CashierSalesSummary`
- `AdminLimitsState`
- `AdminRepository`

### 10. Master administrativo

Responsabilidad:

- crear banca
- bloquear/desbloquear
- borrar banca
- regenerar credenciales
- limite de recargas
- auditoria
- administrar bancas sin mezclarse con la operacion de loteria

Importante:

- `master` no entra en operacion de loteria
- `master` no debe cargar modulos pesados de venta/finanza/resultados

## Estrategia de ejecucion por fases

### Fase 1. Cerrar entrada nativa

Objetivo:

dejar `LoginActivity` como puerta real y controlar la sesion desde Kotlin.

Tareas:

- hacer `LoginActivity` launcher
- crear `ShellActivity` Compose
- resolver home por rol
- dejar `MainActivity` como host transitorio del legado web

Resultado:

la app ya arranca nativa aunque todavia use WebView para negocio.

### Fase 2. Sacar el catalogo de loterias de index.html

Objetivo:

dejar un catalogo compartido y reutilizable para todo lo que sigue.

Tareas:

- crear `LotteryCatalogRepository`
- mover `LOTS` a Kotlin
- mover `LOT_LOGOS` a Kotlin
- resolver horarios y cierres por dia
- exponer un modelo unico para venta y resultados

Resultado:

venta y resultados ya no dependen del arreglo hardcodeado dentro del HTML.

### Fase 3. Migrar venta

Objetivo:

sacar del WebView el flujo mas sensible a lag.

Tareas:

- crear estado nativo de venta
- portar validaciones de numero/tipo
- portar cierres por loteria
- portar calculo de exposicion
- portar limites por actor
- guardar ticket en repositorio nativo
- sincronizar con Supabase usando las mismas claves activas

Resultado:

el cashier empieza a trabajar la pantalla mas importante en Compose.

### Fase 4. Migrar tickets oficiales

Objetivo:

dejar ticket, duplicado, anulacion y pago fuera del DOM web.

Tareas:

- lista de tickets del dia
- filtro por actor
- detalle oficial del ticket
- duplicado
- anulacion
- pagar ticket
- ganadores y premios ligados al ticket

Resultado:

las operaciones sobre ticket quedan unificadas en modelos nativos.

### Fase 5. Migrar ticket termico e impresion

Objetivo:

dejar la impresion robusta y controlada desde Android.

Tareas:

- mover preferencias termicas a storage nativo
- crear renderer termico en Kotlin
- crear preview policy por rol
- crear gateway de impresion
- prueba de impresion
- reimpresion de ultimo ticket

Resultado:

el ticket termico deja de depender del render web y del canvas legado.

### Fase 6. Migrar resultados

Objetivo:

dejar resultados y compartir en capa nativa.

Tareas:

- fetch directo a Supabase desde Kotlin
- cache diaria por fecha
- ventana inteligente post-cierre
- lista por loteria con logo
- compartir por WhatsApp
- exportacion de resultados

Resultado:

se elimina la dependencia del cache viejo del WebView para resultados.

### Fase 7. Migrar finanza y cuadre

Objetivo:

llevar lo mas delicado del admin a una capa auditable.

Tareas:

- portar `getFinanceBreakdown(...)`
- portar `getAdminFinanceData(...)`
- portar `adminCajeroStats(...)`
- crear `FinanceCalculator`
- crear `CuadreRepository`
- cerrar `turno` de cashier
- cerrar `cuadre` de admin

Resultado:

la numerica deja de vivir mezclada con HTML y render.

### Fase 8. Migrar admin operativo

Objetivo:

dejar admin con panel Compose sobre datos ya migrados.

Tareas:

- cajeros
- presencia
- tickets por cajero
- limites
- recargas
- monitor
- alertas

Resultado:

el admin deja de depender de la mayor parte del legado web.

### Fase 9. Reducir o retirar el WebView

Objetivo:

que el WebView quede solo para legado puntual o desaparezca.

Tareas:

- mover lo ultimo que falte
- borrar puentes temporales
- retirar estados duplicados
- limpiar `MainActivity`

## Orden de ejecucion real recomendado

No atacar todo a la vez. El orden correcto es:

1. `LoginActivity` launcher + `ShellActivity`
2. `LotteryCatalogRepository`
3. `Venta`
4. `Ticket oficial`
5. `Ticket termico`
6. `Resultados`
7. `Finanza`
8. `Turno y cuadre`
9. `Admin`
10. `limpieza final del WebView`

## Dependencias entre modulos

### Multi-banca atraviesa todo

Todos los modulos deben cargar y persistir con contexto de banca:

- sesion
- usuarios
- venta
- ticket
- resultados
- finanza
- cuadre
- admin

### Calendario y reloj atraviesan venta y resultados

Los modulos de venta y resultados deben compartir:

- `TrustedClockRepository`
- `HolidayCalendarRepository`
- `LotteryClosePolicy`
- reglas de territorio `RD` / `USA`
- cierres especiales por domingo

### Ingestion de resultados atraviesa resultados y admin

Los modulos de resultados y admin deben compartir:

- `ResultsScraperOrchestrator`
- `ResultsRepository`
- `ResultsSupabaseStore`
- mapeo fuente externa -> id interno de loteria
- politicas de merge y deduplicacion

### Venta depende de

- sesion
- catalogo de loterias
- sync
- tickets

### Ticket oficial depende de

- venta
- catalogo
- sesion

### Ticket termico depende de

- ticket oficial
- settings nativos
- bridge/SDK de impresion

### Resultados dependen de

- catalogo de loterias
- sync/cache
- share/export

### Finanza depende de

- tickets
- premios
- recargas
- anulaciones

### Cuadre depende de

- finanza
- turno
- resumen por cajero

### Admin depende de

- tickets
- resultados
- finanza
- cuadre
- presencia

## Reglas para convivir con el WebView sin romper produccion

- no borrar una pantalla web hasta que su equivalente nativo cierre el mismo flujo
- no duplicar calculos de negocio sin snapshot de comparacion
- cada modulo migrado debe tener comparacion web vs nativo durante una etapa
- cada modulo nuevo debe seguir usando las mismas claves remotas de Supabase mientras no cambie backend
- el `cashier` debe ser el primer beneficiado del rendimiento nativo
- el `admin` se migra despues de estabilizar venta, tickets y resultados
- `Pick 3`, `Pick 4` y las jugadas ya existentes se migran como logica de negocio, no como casos especiales visuales
- `master`, `admin` y `cashier` deben conservar el mismo alcance funcional del proyecto actual
- la estructura multi-banca se mantiene desde la primera capa de modelos y repositorios
- la logica de feriados, domingos especiales y reloj confiable se migra como dominio, no como utilitario visual

## Checklist de validacion por modulo

### Venta

- vender varias jugadas seguidas sin lag
- respetar cierre de loteria
- respetar limites
- crear ticket correcto
- reflejarse en admin
- validar `Pick 3`
- validar `Pick 4`
- validar jugadas tradicionales
- respetar banca y actor correcto
- cambiar entre `Straight` y `Box` sin romper el tipo de jugada
- reflejar el label correcto del boton segun el modo activo
- mantener la misma experiencia de cuadros de numero, monto y jugadas
- comprobar `Ligar`
- comprobar `Super Pale`
- respetar cierre dominical de Nacional
- respetar cierre dominical de Leidsa
- respetar feriados RD
- no cerrar loterias USA por feriados federales de EEUU si hoy no se cierran
- respetar cierres manuales hechos por admin para su banca

### Ticket oficial

- buscar ticket
- duplicar
- anular
- pagar
- ganador pendiente/pagado

### Ticket termico

- prueba 58mm
- prueba 80mm
- reimpresion
- preview visible solo en rol permitido

### Resultados

- sync manual
- auto refresh cerca del cierre
- compartir por WhatsApp
- no mostrar resultado como publicado antes de su hora real
- cubrir `Pick 3`
- cubrir `Pick 4`
- cubrir `NJ AM`
- cubrir `NJ PM`
- guardar en Supabase por fecha sin romper el formato actual
- deduplicar por id de loteria si vienen varias fuentes

### Finanza y cuadre

- mismo total que la capa web
- mismo resultado por cajero
- mismo resultado por admin
- cierre de turno correcto
- mismo resultado por banca

## Estado de ejecucion real

### Estado validado al 2026-04-16

Chequeo actual:

- `.\.venv\Scripts\python.exe check_migration_status.py` -> `41/41`
- `./gradlew.bat :app:compileDebugKotlin` con `JAVA_HOME` al JBR de Android Studio -> OK

Eso significa que el checklist estructural usado para seguir la migracion ya esta completo. Desde aqui el trabajo deja de ser “poner modulos faltantes” y pasa a ser:

- integrar mejor los modulos entre si
- cerrar paridad fina con el HTML actual
- sacar dependencias operativas reales del WebView

### Base nativa ya levantada

El proyecto ya no esta en cero. La capa Kotlin nativa tiene base funcional en:

- `core/model`
- `core/repository`
- `core/storage`
- `core/catalog`
- `core/calendar`
- `core/export`
- `core/sync`
- `ui/login`
- `ui/shell`
- `ui/sales`
- `ui/results`
- `ui/tickets`
- `ui/printer`

Esto significa que la migracion ya salio de la etapa de plan y entro en etapa de ejecucion real.

### Modulos ya adelantados

#### Login y shell

Ya existe:

- `LoginActivity`
- `ShellActivity`
- sesion local nativa
- ruteo inicial por rol

#### Catalogo y calendario

Ya existe base nativa para:

- catalogo estatico de loterias
- resolucion de assets y logos
- calendario de feriados
- politica de cierre
- reloj confiable local

Todavia falta cerrar paridad completa con toda la logica viva del `index.html`.

#### Venta

Ya existe base nativa para:

- `SalesActivity`
- `SalesRepository`
- `SaleValidator`
- `SaleExposureEngine`
- modelos de jugada y ticket

Pero venta aun no puede considerarse cerrada en migracion total porque falta acercarla mas al bloque HTML real en:

- tamaños y jerarquia del POS
- comportamiento exacto restante de `Straight` / `Box`
- comportamiento exacto restante de `Ligar`
- remate fino restante de `Super Pale`
- comparacion sistematica web vs Kotlin por modalidad

#### Tickets

Ya existe base nativa para:

- almacenamiento local de tickets
- vista de ticket oficial
- exportacion bitmap
- share y guardado local

En la ultima tanda ya quedo adelantado:

- ticket oficial Kotlin mas cercano al template HTML real
- header, banda serial y total con jerarquia visual mas fiel
- grupos por loteria con layout mas compacto
- bloque QR/verificacion mas rico
- metadata visible en la pantalla nativa del ticket oficial

Lo que falta en tickets:

- conectar `securityCode` real con la misma fuente del flujo web
- cerrar paridad de estados `anulado`, `pagado`, `ganador`
- duplicar / anular / pagar desde flujo nativo completo
- integrar ticket termico final con esta misma jerarquia visual

#### Resultados

Ya existe base nativa para:

- `ResultsActivity`
- `ResultsRepository`
- cache local por fecha
- exportacion compartible

En la ultima tanda ya quedo adelantado:

- plantilla nativa de resultados mas fiel al look compartido del HTML
- paginacion por paginas de share en vez de una sola lista plana
- cards por loteria con mejor jerarquia
- metadata por fila para enriquecer el export compartido

Lo que falta en resultados:

- paridad completa con `renderOfficialResultsTemplatePages(...)`
- logos reales por loteria en el share nativo
- regla exacta de publicacion segun hora real de sorteo
- integracion final con scraper / Supabase / merge de fuentes

#### Impresion

Ya existe:

- `PrinterActivity`
- repositorio local de preferencias termicas

Pero impresion aun sigue en fase intermedia. Falta:

- `ThermalTicketRenderer` definitivo
- cerrar preview por rol
- reimpresion y cola operativa
- paridad robusta 58mm / 80mm

### Ultima tanda cerrada

La ultima tanda de ejecucion dejo listo esto:

1. `TicketOfficialActivity` enriquecida con metadata visible y bloque de verificacion.
2. `NativeBitmapExport.renderOfficialTicketBitmap(...)` rehecho para acercarse mucho mas al ticket HTML oficial.
3. `NativeBitmapExport.renderResultsBitmaps(...)` rehecho para compartir resultados pagina por pagina.
4. `StaticExportTemplateRepository` extendido para enriquecer filas de resultados con metadata visual.
5. `TicketSecurity` conectado a fuente real y persistido en tickets nativos.
6. acciones nativas de `duplicar / pagar / anular` activas en ticket oficial.
7. `ThermalTicketRenderer` conectado a `PrinterActivity`.
8. `ResultsSupabaseStore` y `ResultsScraperOrchestrator` creados para resultados remotos.
9. `FinanceActivity` y `core/finance` creados como base nativa de caja.
10. `ShellActivity` ya abre `venta`, `resultados`, `impresora` y `caja` nativos.
11. `ResultsActivity` ya refresca Supabase desde Kotlin y muestra origen/estado de sincronizacion.
12. `SalesActivity` quedo remaquetada hacia un POS mas compacto con entrada de 3 bloques, total dominante y lista mas tabular para acercarse al HTML real.
13. `venta` ya separa mejor la accion secundaria: `Pick 3/4` alterna `Straight/Box` y `Q` puede derivar jugadas por `Ligar` desde quinielas ya listadas.
14. `venta` ya tiene quick toggle real de `Super Pale`, limpiando numero activo, recortando seleccion a 2 loterias y mostrando estado operativo mas cercano al HTML.
15. `venta` ya limpia mejor la captura al cambiar entre `Q/P/T/SP`, conserva solo la seleccion valida para cada modo y muestra guias operativas mas cercanas al POS web.
16. `SaleValidator` y `SalesActivity` ya detectan jugadas parciales y muestran hints de captura mas cercanos a `posDetect()` / `getPosSaleDetail()` en vez de caer demasiado temprano en error de monto.
17. `TicketOfficialActivity` y `ResultsActivity` ya exponen acciones separadas de `Imprimir`, `WhatsApp` y `Compartir`, acercando la paridad operativa del HTML en vistas nativas.
18. `NativeBitmapExport` ya tiene helper de impresion nativa de bitmap(s) para ticket oficial y resultados compartidos.
19. `PrinterActivity` ya puede abrirse con un ticket real para reimpresion termica, no solo con preview dummy, y `TicketOfficialActivity` ya enlaza directo ese flujo.
20. la preview termica ya conserva salida operativa de `Imprimir`, `WhatsApp`, `Compartir` y guardado local, igualando mejor las salidas nativas principales.
21. `NativeBitmapExport.renderOfficialTicketBitmap(...)` ya marca visualmente `Activo`, `Pagado` o `Anulado` dentro del ticket exportado.
22. `ResultsActivity` ya muestra tablero completo por loteria con hora de sorteo y estado `Publicado / Pendiente / Esperando sync / Sin publicar`, incluso cuando todavia no hay numeros guardados.
23. `resultados` ya usa reloj confiable + catalogo + politica de horarios para no depender solo de que exista dato local/remoto.
24. `resultados` ya carga logos reales por loteria en cards Compose y en el share bitmap usando assets locales del catalogo, incluyendo soporte `png/svg`.
25. `ResultsScraperOrchestrator` ya hace merge fino `local + supabase` por loteria, priorizando filas publicadas y luego la mas reciente.
26. `resultados` ya propaga metadata visible de `fuente / horario / estado` hacia el share y activa auto refresh inteligente cuando una loteria esta cerca del sorteo o esperando sync.
27. compilacion Kotlin validada con `:app:compileDebugKotlin`.
28. `AdminCashierDetailActivity` ya expone `Imprimir / WhatsApp / Compartir / Guardar` y usa `NativeBitmapExport.renderCashierDetailBitmap(...)` para cerrar el detalle por cajero con salida operativa nativa.

### Punto exacto donde quedo la migracion

La migracion no debe volver a arrancar por login o shell. Ese tramo ya esta suficientemente encaminado.

El punto correcto para seguir es este:

1. cerrar paridad funcional de `venta`
2. cerrar paridad fina de `ticket oficial` y `ticket termico`
3. cerrar reglas reales de publicacion y merge en `resultados`
4. profundizar `finanza`, `turno` y `cuadre`
5. entrar a `admin` operativo y desmontar mas WebView

### Siguiente bloque de ejecucion recomendado

#### Bloque siguiente A: venta con paridad real

Implementar y validar:

- comparativa exacta JS vs Kotlin de modalidades
- cerrar paridad fina restante de `Straight`
- cerrar paridad fina restante de `Box`
- cerrar paridad fina restante de `Ligar`
- remate visual y operativo restante de `Super Pale`
- cierre fino de transiciones y mensajes de captura del POS
- ajuste fino restante de tamaños, margenes y densidad del POS Compose

#### Bloque siguiente B: tickets operativos

Implementar y validar:

- overlays visuales de estado en ticket exportado
- reimpresion operativa real
- cierre fino del ticket termico 58/80
- cola o gateway real de impresion

#### Bloque siguiente C: resultados oficiales

Implementar y validar:

- logos reales por loteria en el share nativo
- metadata de fuente y horario por loteria
- pagina 1 / pagina 2 / pagina 3 con reparto cercano al HTML
- reglas de publicacion y vacios por loteria
- merge real entre cache local y remoto por fecha
- auto refresh inteligente cerca de horario de sorteo

### Regla para continuar desde aqui

El proximo avance debe escribirse sobre este mismo documento, no en otro plan paralelo.

Cada nueva tanda debe dejar claro:

- que modulo se movio
- que paridad con HTML ya se logro
- que parte sigue pendiente
- que comando de validacion se corrio

### Punto real despues de esta tanda

`venta` sigue siendo el frente correcto. Ya no falta armar el modulo; falta rematar comportamiento fino.

Lo proximo desde aqui debe ser:

1. comparar `posDetect()` y `getPosSaleDetail()` contra Kotlin para cerrar diferencias de captura
2. decidir si `P3B/P4B` quedan solo como compatibilidad o si necesitan entrada nativa
3. despues pasar a `ticket termico` y `reimpresion` con flujo operativo real

### Paridad ya lograda en captura POS

En `venta`, la capa Kotlin ya cubre mas fielmente:

- alternancia `Straight/Box`
- derivacion de `Ligar` desde quinielas existentes
- toggle operativo de `Super Pale`
- limpieza de captura al cambiar de modo
- hints parciales mientras se escribe el numero
- mensaje de `Escribe el monto` solo cuando ya existe jugada valida

### Paridad operativa ya lograda en export y salida

Las vistas nativas principales ya conservan opciones separadas de:

- `Imprimir`
- `Compartir`
- `WhatsApp`
- guardado local en descargas

### Paridad ya lograda en impresion termica

La capa nativa ya tiene:

- preview termico de ticket real
- entrada directa desde ticket oficial a reimpresion
- configuracion termica persistida por dispositivo
- salida termica compartible e imprimible desde la propia preview

Lo que sigue ya no es abrir el flujo sino conectar salida termica operativa final o gateway real de impresion.

### Siguiente seccion despues de esta tanda

La siguiente seccion correcta del plan ya no es `venta` ni `impresora`.

Debe pasar a `resultados`, en este orden:

1. afinar casos limite de estados cuando una fuente venga vacia o tardia
2. revisar si conviene esconder o degradar loterias sin sorteo para fechas historicas
3. despues pasar a `finanza / turno / cuadre`

### Siguiente seccion real del plan

Con `resultados` ya bastante adelantado, la siguiente seccion correcta para mover la migracion es:

- `finanza`
- `turno`
- `cuadre`
- despues de eso, seguir desmontando `WebView` en `admin` fuera del detalle por cajero, porque ese subbloque ya quedo suficientemente cerrado

El siguiente bloque debe concentrarse en paridad de totales y cortes operativos, no en UI aislada.

## Lo que sigue ahora

### Bloque inmediato 1

- `SalesRepository` real
- `SaleValidator`
- primera pantalla Compose de `venta`
- paridad de `Straight/Box`
- paridad de `Ligar`
- paridad de `Super Pale`

### Bloque inmediato 2

- overlays de estado en ticket oficial
- reimpresion y flujo termico real
- prueba operativa `58mm / 80mm`

### Bloque inmediato 3

- reglas de publicacion real en resultados
- logos por loteria en cards/share
- refresh inteligente segun horario

### Bloque inmediato 4

- `turno`
- `cuadre`
- desglose financiero por actor

## Criterio de exito

La migracion va bien si se cumple esto:

- el cashier vende mas rapido que en WebView
- el admin sigue viendo a sus cajeros en tiempo real
- resultados salen frescos sin borrar cache
- ticket oficial y termico salen correctos
- finanza y cuadre dan exactamente igual que antes
- `master` queda liviano y separado del negocio
- `Pick 3`, `Pick 4` y las jugadas existentes responden exactamente igual que en el sistema actual
- el apartado `venta` se siente y opera casi igual al actual, pero mas rapido
- cada banca conserva su aislamiento y su operacion sin cruce de datos
- la logica de reloj, feriados y cierres responde igual que en el proyecto actual
- el sistema de resultados sigue capturando en tiempo real todas las loterias necesarias, incluyendo `Pick 3`, `Pick 4`, `NJ AM` y `NJ PM`

## Siguiente accion correcta

La siguiente accion tecnica ya no es arrancar shell o catalogo; eso ya quedo encaminado.

La siguiente accion correcta es:

1. cerrar paridad real del POS Compose contra el HTML actual
2. terminar el circuito operativo del ticket termico
3. afinar resultados con publicacion real y logos por loteria
