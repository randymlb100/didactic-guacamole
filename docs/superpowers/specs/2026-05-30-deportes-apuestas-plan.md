# LotteryNet Deportes - Plan para Implementacion Futura

Fecha: 2026-05-30

## Estado de ejecucion

- Fase 1 iniciada.
- Supabase ya tiene las tablas nuevas `sports_*` aplicadas en produccion.
- RLS quedo activo en todas las tablas deportivas.
- Se agregaron indices para las llaves foraneas deportivas que marco el advisor de Supabase.
- Android ya tiene modelo base `Sportsbook*`, pantalla `SportsbookActivity` y control local/remoto de activacion.
- Master ya ve entrada `Deportes` para configurar el modulo.
- Admin, supervisor y cajero solo ven `Deportes` si Master lo activa para su rol.
- El permiso se guarda local y en la llave remota `sportsbook:global`.
- Se agrego la ruta servidor `sports-get-board` para leer juegos/cuotas cacheadas desde `sports_events`, `sports_markets` y `sports_odds`.
- Se agrego el job servidor `sports-sync-odds` para traer eventos y cuotas desde odds-api.net usando `ODDS_API_KEY` en servidor.
- Se agrego cache servidor `sports_team_assets` y job `sports-sync-team-assets` para descargar logos desde TheSportsDB con `THESPORTSDB_API_KEY`.
- `sports-get-board` ahora puede entregar `homeTeamLogoUrl` y `awayTeamLogoUrl` sin llamar TheSportsDB en cada lectura.
- Android muestra logos de equipos con cache de imagen del dispositivo y fallback por iniciales si falta el logo.
- Android ya tiene `SportsbookBoardRemoteStore` para leer el tablero sin llamar Odds API directo desde la app.
- La pantalla Deportes muestra estado real del tablero cacheado, pero todavia no vende tickets deportivos.
- Se agrego la base servidor `create-sports-ticket` en codigo, con validacion de sesion, rol vendedor, feature flag, cuota abierta/no vencida, juego no iniciado, limites, congelado de cuota e idempotencia por `clientRequestId`.
- `create-sports-ticket` todavia no esta conectado a Android ni desplegado para venta real.
- Se agrego examen Node `tools/qa/sportsbook-ui-contract.node.test.mjs` para validar rapido contratos de UI/roles/servidor.
- No se toca Venta, tickets de loteria, resultados, recargas ni finanza existente.

## Objetivo

Agregar una seccion nueva de apuestas deportivas dentro de LotteryNet, oculta por defecto y habilitable solo por Master, usando `odds-api.net` como proveedor inicial de juegos, cuotas y resultados.

La seccion debe funcionar como un negocio separado dentro de la misma app: no debe mezclar tickets, premios, resultados ni finanza con loteria o recargas.

## Decision de arquitectura

Se implementara dentro de la misma app LotteryNet, no como app separada.

Motivos:

- Reutiliza login, roles, cajeros, bancas, permisos, impresion, WhatsApp y reportes.
- Evita duplicar usuarios, caja, soporte y configuraciones.
- Permite que Finanza muestre un total general, pero con desglose separado.
- Mantiene el modulo oculto hasta que Master lo habilite.

Regla principal: mismo sistema, pero modulo aislado.

## Alcance inicial

El MVP sera solo pre-juego. No incluye apuestas en vivo.

Mercados iniciales:

- Moneyline: gana equipo A o equipo B.
- Runline / Spread: ventaja o desventaja del equipo.
- Total: alta/baja de puntos o carreras.
- Mitad / F5: primera mitad o primeras 5 entradas cuando el deporte lo soporte.
- Parlay simple: varias selecciones en un mismo ticket.

## Modos de juego para poner mas tarde

Esta lista queda como pendiente. No debe activarse completa de una vez hasta probar cobertura, claridad visual, limites y liquidacion por deporte.

### Base que ya puede venderse primero

- Moneyline: gana equipo local, visitante o empate cuando aplique.
- Spread / Runline: equipo con ventaja o desventaja.
- Alta/Baja: total del juego.
- Parlay: varias selecciones combinadas en un ticket.

### Baseball

- Moneyline del juego completo.
- Runline -1.5 / +1.5.
- Total carreras del juego.
- Primeras 5 entradas: moneyline, runline y total.
- Total carreras por equipo.
- Primera entrada si anota / no anota.

### Basketball

- Moneyline del juego completo.
- Spread del juego completo.
- Total puntos del juego.
- Primera mitad: moneyline, spread y total.
- Primer cuarto: moneyline, spread y total.
- Total puntos por equipo.

### Football americano

- Moneyline del juego completo.
- Spread del juego completo.
- Total puntos del juego.
- Primera mitad: moneyline, spread y total.
- Primer cuarto: moneyline, spread y total.
- Total puntos por equipo.

### Futbol

- 1X2: local, empate o visitante.
- Doble oportunidad: 1X, 12, X2.
- Empate no accion.
- Alta/Baja de goles.
- Ambos equipos anotan.
- Primer tiempo: 1X2 y alta/baja.
- Total goles por equipo.

### Reglas para activar modos nuevos

- Cada modo debe tener nombre claro para cajero y cliente.
- Cada modo debe tener liquidacion comprobada en servidor antes de venderse.
- Si el proveedor devuelve props o lineas alternativas sucias, no se muestran por defecto.
- Los modos avanzados deben poder apagarse por Master/Admin sin afectar los modos basicos.
- La impresion termica, WhatsApp, ticket oficial, cobro y finanza deben entender el modo antes de activar venta real.

No incluir al inicio:

- Cashout.
- Player props.
- Futures.
- Apuestas en vivo.
- Analytics avanzadas como CLV, sharp money o line movement visual.
- Integracion con un sportsbook completo de pago tipo SporbetSoft.

## Proveedor de datos

Proveedor inicial: `odds-api.net`.

Motivos:

- Plan gratis para pruebas.
- Primer plan pago mas barato que otras opciones revisadas.
- GitHub con SDK/contratos utiles.
- Soporta eventos, odds, resultados y streams.

Uso previsto:

- La app Android nunca debe llamar la API directa.
- La API key vive en servidor.
- Render/Supabase ejecutan jobs de sincronizacion.
- Supabase guarda cache de juegos y cuotas.
- Los cajeros leen desde Supabase.

Proveedor de logos: TheSportsDB.

Reglas:

- TheSportsDB no se usa para cuotas ni pagos, solo imagen/identidad visual de equipos.
- La app Android no llama TheSportsDB directo.
- `sports-sync-team-assets` descarga logos y los guarda en `sports_team_assets`.
- `sports-get-board` lee esa cache y manda URLs ya resueltas al tablero.
- Si falta logo, la app muestra iniciales del equipo para no romper la venta ni el tablero.
- No se guardan logos como assets del APK porque equipos/liga cambian; meterlos en el APK obligaria a build nuevo cada vez. La cache vive en servidor y el telefono la cachea al mostrarla.

Estrategia de consumo:

- No refrescar cada pocos segundos.
- Sincronizar juegos del dia pocas veces al dia.
- Refrescar cuotas solo de juegos activos y cercanos.
- Aumentar frecuencia cerca del inicio del juego.
- Cerrar automaticamente mercados cuando el juego empieza.

## Permisos y activacion

Master controla la seccion.

Configuraciones:

- Activar/desactivar Deportes global.
- Activar por admin.
- Activar por supervisor.
- Activar por cajero/banca.
- Activar o apagar mercados individuales.
- Configurar limites deportivos separados.

Si Deportes esta apagado, no aparece en menus ni navegacion.

Si un cajero no tiene permiso, no puede ver ni vender apuestas deportivas.

Regla jerarquica obligatoria:

- Master administra el modulo, pero no ve el negocio operativo de los admin.
- Master puede activar/desactivar Deportes globalmente y decidir que admins tienen acceso al modulo.
- Master puede configurar reglas globales del sistema: proveedor, sincronizacion, mercados permitidos por defecto y estado general del modulo.
- Master no ve tickets, cobros, reportes, ventas, premios, anulaciones ni cajeros internos de cada admin.
- Admin puede vender igual que un cajero y tambien administra su propio negocio: supervisores, cajeros, tickets, cobros, reportes, limites y anulaciones de su red.
- Supervisor ve solo sus cajeros asignados.
- Cajero ve solo su venta, sus tickets y sus cobros.
- Toda accion sensible debe guardar `actor_role`, `actor_key`, `admin_key`, `cashier_key` y `owner_key`, pero la auditoria operativa de negocio la ve el admin dueno de esa red, no Master.

Esta regla tambien aplica a la activacion: Master habilita el modulo al admin, pero lo que pase dentro del negocio del admin queda dentro de ese admin.

## Navegacion y UX

La seccion aparecera como entrada independiente, similar a Recarga.

Tabs internas:

- Juegos
- Ticket
- Apuestas
- Cobros
- Finanza
- Resultados
- Reportes
- Control
- Configuracion

Ubicacion por rol:

- Master: entrada `Deportes` en el menu principal solo para administracion global del modulo.
- Admin: entrada `Deportes` solo si Master se la habilita; puede vender como cajero y administrar solo su red.
- Supervisor: entrada `Deportes` solo si Admin/Master se la habilita, con vista limitada a sus cajeros.
- Cajero: entrada `Deportes` solo para vender/cobrar si esta habilitado.

La seccion no debe entrar dentro de Venta de loteria. Debe ser una seccion independiente para no mezclar teclado, tickets, premios ni finanza.

Principios visuales:

- Nada apretado.
- Controles grandes para POS.
- Botones/chips para cuotas, no dropdown para jugar.
- Dropdown solo para filtros: deporte, liga, fecha, estado.
- Modal bottom sheet para detalle de juego y seleccion de mercado.
- Tabs separan areas grandes: juegos, ticket, cobros, finanza, reportes y control.
- Finanza usa lenguaje fintech: vendido, premios, pendiente, ganancia/perdida y neto deportivo.
- Ganancia, perdida y pendiente deben tener color propio y texto explicito; nunca depender solo del color.
- Montos importantes van en negrita, alineados y con formato tabular.
- Bottom sheet de juego muestra equipos, hora, mercados, cuota, limite y boton de agregar al ticket.
- Dropdown de filtros debe ser compacto, ordenado y con seleccion visible; no usarlo para elegir jugadas.
- Layout POS: botones tactiles grandes, separacion clara y sin texto cortado.
- Estados claros: abierto, cerrado, suspendido, iniciado, finalizado.
- Numeros con formato tabular para cuotas, montos y pago posible.

Base tecnica UI:

- Jetpack Compose `DropdownMenu` para filtros cortos y scroll si hay muchas opciones.
- Material 3 `ModalBottomSheet` para detalles de juego, confirmaciones y acciones rapidas.
- Tabs/segmentos para secciones principales, evitando mezclar venta con reportes o finanza.
- Animaciones ligeras de estado presionado/expandido; nada pesado en POS.
- Mantener accesibilidad: objetivos tactiles grandes, contraste suficiente y texto visible.

Flujo de venta para admin o cajero:

1. Abrir Deportes.
2. Elegir deporte/liga o ver juegos abiertos.
3. Tocar un juego.
4. Ver bottom sheet con mercados.
5. Tocar una cuota.
6. La seleccion entra al ticket.
7. Ingresar monto.
8. Ver pago posible.
9. Vender.
10. Imprimir o enviar por WhatsApp.

## Ticket deportivo

El ticket deportivo sera distinto al ticket de loteria.

Debe mostrar:

- Codigo del ticket.
- Banca/cajero.
- Fecha y hora.
- Tipo: directa o parlay.
- Cada seleccion:
  - deporte/liga
  - juego
  - mercado
  - seleccion
  - linea si aplica
  - cuota bloqueada
- Monto apostado.
- Pago posible.
- Estado: pendiente, ganado, perdido, anulado, pagado.

Regla critica: la cuota se congela al vender. Si luego cambia, el ticket mantiene la cuota original.

## Finanza separada

Finanza no debe mezclar Deportes con Loteria.

Estructura:

- Loteria
  - ventas
  - premios
  - neto
- Recargas
  - recargas
  - comision
  - neto
- Deportes
  - apuestas vendidas
  - premios pagados
  - apuestas pendientes
  - anulados/push
  - ganancia/perdida
  - neto deportivo
- Total general

Reportes por rol:

- Cajero ve sus ventas/cobros deportivos.
- Supervisor ve solo sus cajeros.
- Admin ve sus bancas, vende como cajero y administra limites, cobros, anulaciones y reportes de su red.
- Master no ve reportes operativos de negocios de admin.

Vista Master:

- Estado global del modulo Deportes.
- Activar/desactivar el modulo completo.
- Habilitar o deshabilitar Deportes por admin.
- Configurar proveedor de cuotas/resultados.
- Ver salud tecnica: ultima sincronizacion, errores del cron, consumo de API y mercados disponibles.
- No mostrar tickets, ventas, cobros, premios, cajeros, supervisores ni reportes privados de cada admin.

Vista Admin:

- Resumen solo de su red.
- Filtro por supervisor/cajero/banca.
- Venta deportiva habilitada para el propio admin, igual que en Loteria.
- Controles administrativos equivalentes a Loteria: cajeros, limites, cobros, anulaciones, cuadre, reportes y auditoria.
- No puede ver ni tocar datos de otro admin.

## Datos nuevos

Tablas sugeridas:

- `sports_feature_flags`
- `sports_admin_config`
- `sports_events`
- `sports_markets`
- `sports_odds`
- `sports_odds_snapshots`
- `sports_tickets`
- `sports_ticket_legs`
- `sports_results`
- `sports_settlements`
- `sports_limits`
- `sports_audit_log`

No reutilizar `TicketRecord` de loteria como modelo principal. Si se necesita mostrar todo junto en una pantalla general, crear un adaptador de lectura con `productType = LOTTERY | RECHARGE | SPORTS`.

## Funciones servidor

Funciones nuevas sugeridas:

- `sports-sync-events`
- `sports-sync-odds`
- `sports-get-board`
- `create-sports-ticket`
- `sports-settle-results`
- `sports-pay-ticket`
- `sports-void-ticket`
- `sports-admin-config`

Implementado en Fase 1:

- `sports-sync-odds`: sincroniza eventos y cuotas cacheadas desde odds-api.net, protegido por `LOTTERYNET_ADMIN_SHARED_SECRET`.
- `sports-get-board`: entrega el tablero cacheado a Android sin exponer la llave de odds-api.net.
- `create-sports-ticket`: base de venta deportiva segura en codigo, pendiente de despliegue y conexion Android.

Validaciones obligatorias en `create-sports-ticket`:

- Usuario autenticado.
- Deportes habilitado para su cuenta.
- Mercado abierto.
- Juego no iniciado.
- Cuota no vencida.
- Monto mayor que cero.
- Limite por ticket.
- Limite por evento/seleccion.
- Pago posible dentro del maximo permitido.
- Idempotencia para evitar doble venta por timeout.

## Resultados y liquidacion

La liquidacion debe correr en servidor.

Estados:

- `pending`
- `won`
- `lost`
- `push`
- `void`
- `paid`

Reglas:

- No liquidar desde la app.
- No confiar en reloj del telefono.
- Si falta resultado, dejar pendiente.
- Si el proveedor tiene datos dudosos, marcar para revision admin.
- Mantener auditoria de cambios manuales.

## Controles de riesgo

Configuraciones por Master/Admin:

- Maximo por ticket.
- Maximo por seleccion.
- Maximo por evento.
- Maximo por cajero por dia.
- Maximo pago posible.
- Cierre manual de juego.
- Suspension manual de mercado.
- Override manual de cuota antes de abrir venta.
- Anulacion controlada con auditoria.

## Implementacion por fases

### Fase 1 - Base oculta

- Feature flag Master.
- Permisos por rol/cuenta.
- Entrada "Deportes" oculta.
- Configuracion inicial sin vender.

### Fase 2 - Sincronizacion de juegos y cuotas

- Integrar `odds-api.net` desde servidor.
- Guardar eventos y cuotas en Supabase.
- Cache y control de requests.
- Pruebas con mock antes de consumir API real.

### Fase 3 - UI Juegos

- Pantalla de juegos.
- Filtros por deporte/liga/fecha.
- Cards de juegos.
- Bottom sheet de mercados.
- Estados visuales claros.

### Fase 4 - Venta deportiva

- Ticket directo.
- Parlay simple.
- Validacion servidor.
- Cuota congelada.
- Idempotencia para timeout.

### Fase 5 - Ticket, impresion y WhatsApp

- Plantilla termica deportiva.
- Snapshot oficial deportivo.
- WhatsApp compacto.
- Reimpresion y busqueda.

### Fase 6 - Resultados y cobros

- Job de resultados.
- Liquidacion automatica.
- Cobro desde seccion Deportes.
- Auditoria de pagos.

### Fase 7 - Finanzas y reportes

- Reporte Deportes separado.
- Integracion en total general.
- Export/snapshot con desglose.

### Fase 8 - Pruebas completas

- Node tests de servidor.
- Pruebas de venta directa.
- Pruebas de parlay.
- Pruebas de cuota vencida.
- Pruebas de juego cerrado.
- Pruebas de cajero sin permiso.
- Pruebas de cobro masivo.
- Pruebas de finanza separada.

## Pruebas clave antes de produccion

- No aparece Deportes si Master no lo habilita.
- Cajero sin permiso no puede vender.
- Ticket con cuota vencida se rechaza.
- Juego iniciado se rechaza.
- Timeout no duplica ticket.
- Parlay calcula pago correctamente.
- Push/anulado recalcula parlay correctamente.
- Finanza de loteria no cambia.
- Finanza de recarga no cambia.
- Reporte general muestra Deportes separado.
- Impresion y WhatsApp no se congelan con ticket grande.

## Riesgos

- Consumo excesivo de requests de Odds API.
- Cuotas viejas si el job falla.
- Resultados incorrectos o tardios.
- Complejidad de parlay con push/anulados.
- Confusion visual si se mezcla con loteria.
- Riesgo legal/regulatorio segun operacion real.

Mitigacion:

- Cache fuerte.
- Jobs con monitoreo.
- Auditoria.
- Separacion de modulo.
- MVP pre-juego.
- Revision manual para resultados dudosos.

## Recomendacion final

Construir Deportes dentro de LotteryNet, oculto por Master y completamente separado de Loteria/Recarga.

Empezar pequeno:

- Moneyline.
- Runline/spread.
- Total.
- Mitad/F5 si la API lo ofrece de forma estable.
- Ticket directo.
- Parlay simple.

No pagar plataforma sportsbook completa por ahora. Usar `odds-api.net` y construir la logica propia para que quede integrada al sistema actual.

## Estado de despliegue - 2026-05-30

- Supabase produccion tiene aplicada la base de datos de Deportes:
  - `sports_feature_flags`
  - `sports_events`
  - `sports_markets`
  - `sports_odds`
  - `sports_odds_snapshots`
  - `sports_tickets`
  - `sports_ticket_legs`
  - `sports_settlements`
  - `sports_limits`
  - `sports_audit_log`
- Supabase produccion tiene desplegadas estas funciones:
  - `sports-get-board`
  - `sports-sync-odds`
- Codigo local tiene agregada la funcion pendiente de despliegue:
  - `create-sports-ticket`
- `sports-get-board` fue probado en produccion y responde correctamente.
- La respuesta actual de `sports-get-board` trae `games: 0` porque todavia no se ha llenado la cache de eventos/cuotas.
- `sports-sync-odds` queda listo para llenar la cache desde odds-api.net, pero necesita que Supabase tenga configurada la llave `ODDS_API_KEY` y que la llamada use `LOTTERYNET_ADMIN_SHARED_SECRET`.
- Render MCP quedo seleccionado en `Lapiz's workspace`.
- Render cron creado:
  - Nombre: `lotterynet-sports-odds-sync`
  - ID: `crn-d8dlp30js32c73fmhcsg`
  - Schedule: `*/5 8-23 * * *`
  - Plan: `starter`
  - Start command: `python tools/render/sync_sports_odds.py`

Pendiente inmediato:

- Poner `LOTTERYNET_ADMIN_SHARED_SECRET` en el cron `lotterynet-sports-odds-sync` si Render no lo heredo desde otro servicio.
- Verificar que Supabase Edge Functions tenga configurado `ODDS_API_KEY`.
- Ejecutar una sincronizacion manual y confirmar que `sports-get-board` devuelve juegos reales.

### Estado 2026-05-30 tarde

- `sports-sync-odds` quedo desplegado en Supabase version 8.
- La sincronizacion manual con `baseball,basketball` guardo 10 eventos, 33 mercados y 162 cuotas sin errores.
- `sports-get-board` ya devuelve 10 juegos y los 10 tienen cuotas disponibles para la app.
- El cron de Render `lotterynet-sports-odds-sync` esta desplegado en vivo con el script `tools/render/sync_sports_odds.py`.
- Ajuste aplicado: si Odds API devuelve vacio cuando se pide con filtro de mercados, el servidor vuelve a pedir la foto completa y filtra localmente `moneyline`, `total`, `spread`, `first_half` y `first_five`.
- Para no gastar el sandbox de Odds API durante pruebas, el cron quedo apagado por ambiente con `SPORTS_ODDS_SYNC_ENABLED=false`.
- El script de Render quedo en modo opt-in: si `SPORTS_ODDS_SYNC_ENABLED` no esta activo, termina sin llamar a Odds API; en modo `smart` solo sincroniza en horas UTC permitidas por `SPORTS_ODDS_SYNC_UTC_HOURS`.
