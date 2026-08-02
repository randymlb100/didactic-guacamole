# Modern Operational Sheets UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar visualmente las secciones operativas de LotteryNet Android para que Cuadre, Reporte, Monitoreo, Limites, Sistema y Deportiva usen pantallas limpias y `ModalBottomSheet` para filtros/opciones secundarias, sin cambiar reglas de negocio.

**Architecture:** Crear componentes Compose compartidos para sheets, filtros, seleccion de usuario, acciones y resumen. Cada pantalla conserva sus repositorios, callbacks, permisos, keys, alias, sincronizacion y calculos actuales; solo se cambia la composicion visual, agrupacion y jerarquia de controles.

**Tech Stack:** Kotlin, Jetpack Compose Material3, `ModalBottomSheet`, `LazyColumn`, `FilterChip`, `rememberSaveable`, state hoisting, componentes actuales de LotteryNet (`CompactPanel`, `CompactActionButton`, `CompactTextInput`, `CompactSegmentedSelector`, `InfoStrip`, `MetricStrip`).

---

## Brief De Diseño

Producto: app Android POS/operativa de loteria, finanzas, cajeros y administracion.

Problema: varias secciones mezclan navegacion, filtros, acciones y datos dentro de la misma pantalla. Eso hace que el usuario vea demasiados botones, dropdowns largos y opciones abiertas al mismo tiempo.

Direccion: mantener una pantalla principal simple y mover opciones secundarias a modal sheets. El flujo debe sentirse rapido para cajero/admin y seguro para configuracion.

Interactividad esperada: full working UI, pero por fases y sin tocar Supabase ni reglas de negocio en esta etapa.

## Documentacion Y Criterios Usados

- Android Material3 indica que los bottom sheets sirven como alternativa movil a menus inline/dialogos cuando hay listas largas de acciones o items con iconos/descripciones; `ModalBottomSheet` maneja ventana modal, scrim y foco automaticamente.
  - https://developer.android.com/reference/kotlin/androidx/compose/material3/BottomSheet.composable
- Android state hoisting recomienda que el estado de pantalla tenga una fuente de verdad clara, y que los composables hijos reciban estado/callbacks en vez de ViewModel directo.
  - https://developer.android.com/develop/ui/compose/state-hoisting
- Material tabs deben usarse para organizar contenido de alto nivel, no para acciones como `Tickets` o `Reporte`.
  - https://m1.material.io/components/tabs.html
- Material settings recomienda mostrar lo mas importante arriba, agrupar opciones secundarias y mover grupos largos a otra pantalla/sheet.
  - https://m1.material.io/patterns/settings.html
- Android chips: `FilterChip` es apropiado para filtros seleccionables y compactos.
  - https://developer.android.google.cn/develop/ui/compose/components/chip
- Android Lazy lists: usar `LazyColumn` para listas grandes porque solo compone lo visible.
  - https://developer.android.com/develop/ui/compose/lists
- Ejemplo de estructura de bottom sheet: grabber, header, content y footer; util para mantener sheets consistentes.
  - https://watson.docplanner.design/latest/watson-mobile/components/bottom-sheet/usage-guidelines-P3vZtUCQ

## Regla UX Principal

La pantalla principal debe mostrar solo:

1. Donde estoy.
2. Que periodo/alcance esta activo.
3. El resumen mas importante.
4. La lista o contenido principal.
5. Una accion primaria clara.

Todo lo secundario va en sheet:

- Fechas.
- Periodo.
- Cajero.
- Filtros.
- Ordenar.
- Acciones extra.
- Confirmaciones peligrosas.
- Ayuda breve.

## No Tocar En Esta Reorganizacion

- Supabase.
- Pagos y premios.
- Tickets termicos.
- Plantillas de impresion.
- Validacion de venta.
- Calculo de limites.
- Calculo de reportes.
- Sincronizacion.
- `ownerId`, alias, user, ids ni keys.
- Tablas deportivas ni datos deportivos de produccion.

---

## File Structure Propuesta

### Crear

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\OperationalSheets.kt`
  - Componentes compartidos de sheets: header, footer, action rows, searchable lists, filter chips.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\OperationalSectionCards.kt`
  - Tarjetas compactas reutilizables: alcance actual, periodo actual, resumen de filtro, peligro/confirmacion.

### Modificar

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\finance\FinanceActivity.kt`
  - Cuadre: periodo y acciones en sheet.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\report\OperationalReportActivity.kt`
  - Reporte: periodo, cajero y filtros en sheet.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\admin\AdminMonitorActivity.kt`
  - Monitoreo: tabs solo para vistas; acciones por cajero en sheet.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\users\UserAccountsActivity.kt`
  - Limites: alcance/cajero en sheet, formulario agrupado.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\NativeChrome.kt`
  - Sistema: ordenar destinos y copy visible si hace falta.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\admin\AdminConfigActivity.kt`
  - Sistema/configuracion: agrupar controles importantes primero.

- `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\sportsbook\SportsbookActivity.kt`
  - Deportiva: solo plan visual/futuro; no activar flujo operativo ni tocar datos.

---

## Phase 0: Componentes Base Para Sheets

**Objetivo:** tener una base unica para todos los sheets, evitando que cada pantalla invente su estilo.

**Files:**
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\OperationalSheets.kt`
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\OperationalSectionCards.kt`

**Estado 2026-06-09:** implementado.
- Hecho: `OperationalModalSheet` centraliza titulo, subtitulo, cierre, contenido scrollable opcional y footer de accion primaria.
- Hecho: `SearchableOptionSheet` usa busqueda y `LazyColumn` con keys estables.
- Hecho: `CurrentScopeCard` deja reutilizable la tarjeta compacta `Aplicando a` / `Periodo` / `Filtro`.
- Hecho: `DangerConfirmSheet` deja un flujo comun para cambios peligrosos.

- [x] **Step 1: Crear `OperationalModalSheet`**

Contrato:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalModalSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

Debe contener:

- drag handle.
- header con titulo y cierre.
- contenido scrollable si pasa de pantalla.
- footer fijo si hay accion primaria.
- `WindowInsets.safeDrawing` o padding inferior seguro.

- [x] **Step 2: Crear `SearchableOptionSheet`**

Uso:

- seleccionar cajero.
- seleccionar supervisor.
- seleccionar loteria.
- seleccionar mercado deportivo.
- seleccionar periodo si hay lista larga.

Reglas:

- `LazyColumn`.
- `key = option.id`.
- buscador arriba.
- fila con icono, titulo, subtitulo y check.

- [x] **Step 3: Crear `CurrentScopeCard`**

Tarjeta compacta reutilizable:

- titulo: `Periodo`, `Aplicando a`, `Filtro`, `Sistema`.
- valor grande.
- subtitulo de estado.
- boton `Cambiar`.

- [x] **Step 4: Crear `DangerConfirmSheet`**

Para acciones peligrosas:

- bloquear/desbloquear.
- aplicar limites globales.
- borrar/anular.
- reiniciar sync.
- forzar actualizacion.

Debe exigir texto claro y boton rojo solo en footer.

---

## Phase 1: Cuadre

**Objetivo:** que Cuadre deje de mostrar todos los controles de periodo abiertos y se lea como caja operativa.

**Estado 2026-06-09:** parcialmente implementado en `FinanceActivity.kt`.
- Hecho: el selector de periodo ya no se despliega dentro de la pantalla; ahora abre `ModalBottomSheet` con Hoy/Ayer, período, rango manual y mes completo.
- Hecho: el resumen financiero queda fuera del sheet y sigue visible en su sección.
- Hecho: acciones de salida (`WhatsApp`, `Compartir`, `Imprimir`, `Térmico`, `Guardar`) pasan a sheet `Acciones de cuadre`.
- Pendiente: revisar visualmente en celular la altura real del sheet de período y ajustar copy si el calendario manual se siente cargado.
- Pendiente: extraer `FinanceModalSheet` a componente común cuando Reporte use el mismo patrón.

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\finance\FinanceActivity.kt`
- Reuse: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\finance\FinancePeriodUi.kt`

**Pantalla principal:**

- Header: `Cuadre`.
- Card de periodo: `Semana · lun 8 a dom 14`.
- Boton: `Cambiar`.
- Tabs reales: `Resumen`, `Detalle`, `Cierre`.
- Acciones secundarias en sheet: imprimir, compartir, cambiar fecha, cambiar mes.

- [x] **Step 1: Mover selector de periodo a `FinancePeriodSheet`**

El sheet debe contener:

- Hoy.
- Semana lunes-domingo.
- Quincena.
- Mes.
- Manual.
- Selector de fecha solo si manual.

- [x] **Step 2: Mantener resumen siempre visible**

No esconder:

- Venta.
- Recarga.
- Premio.
- Caja.
- Beneficio.

- [x] **Step 3: Sacar acciones extra de la pantalla principal**

Mover a sheet `Acciones de cuadre`:

- Compartir.
- Imprimir.
- Copiar resumen.
- Refrescar servidor.

---

## Phase 2: Reporte

**Objetivo:** que Reporte use el mismo patron de periodo/filtro que Cuadre para que el usuario no aprenda dos interfaces distintas.

**Estado 2026-06-09:** parcialmente implementado en `OperationalReportActivity.kt`.
- Hecho: la pantalla principal muestra una tarjeta compacta de periodo/operador y un boton `Filtros`.
- Hecho: periodo, rango manual y operador/cajero se movieron a `ModalBottomSheet`.
- Hecho: el dropdown viejo de operador se quito de Reporte.
- Hecho: el label visible usa `report.periodLabel` cuando existe para no perder el rango real de semana/mes.
- Hecho: los cajeros del filtro se construyen con `sortCashierAccountsNatural` y `cashierDisplayLabel`, respetando numero natural antes que nombre visible.
- Pendiente: verificar visualmente en celular que la lista de operadores no tape el resumen y que el orden natural siga igual.

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\report\OperationalReportActivity.kt`

**Pantalla principal:**

- Header: `Reporte`.
- Card de periodo/filtro: `Semana · Todos`.
- Boton: `Filtros`.
- Resumen financiero.
- Lista de cajeros.

- [x] **Step 1: Crear `OperationalReportFilterSheet`**

Contenido:

- periodo.
- rango manual.
- cajero/todos.
- aplicar.

- [x] **Step 2: Mantener semana lunes-domingo**

La etiqueta visible debe dejar claro:

```text
Semana: lun 8 a dom 14
```

No usar `Semana` si el rango es rolling 7 dias.

- [x] **Step 3: Ordenar cajeros por orden natural**

Usar nombres visibles:

- `3- Banca moreno`.
- subtitulo tecnico: `cancay20` o id solo si hace falta.

---

## Phase 3: Monitoreo

**Objetivo:** que Monitoreo sea una pantalla de lectura rapida, no un grupo de botones que parecen secciones mezcladas.

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\admin\AdminMonitorActivity.kt`
- Optional Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\admin\AdminLotteryMonitorActivity.kt`

**Regla nueva:**

- Tabs solo para vistas de contenido:
  - `Cajeros`
  - `Ranking`
  - `Alertas`
- No usar tabs para `Tickets` ni `Reporte`.
- `Tickets`, `Reporte`, `Editar`, `Cuadre`, `Cobros` van en sheet por cajero.

**Estado 2026-06-09:** parcialmente implementado en `AdminMonitorActivity.kt`.
- Hecho: tabs superiores de admin/supervisor quedan solo en `Cajeros` y `Ranking`; `Tickets` y `Reporte` ya no aparecen como tabs.
- Hecho: las acciones `Tickets`, `Reporte`, `Editar`, `Cuadre` y `Cobros` se mantienen en `CashierQuickActionSheet` por cajero.
- Hecho: `CashierQuickActionSheet` usa `OperationalModalSheet`, manteniendo las mismas acciones.
- Hecho: el panel de ranking muestra `Sin ranking para este periodo` cuando no hay combinaciones visibles.
- Pendiente: crear `MonitorFilterSheet` para filtros/orden avanzado sin cargar la pantalla.

- [x] **Step 1: Estandarizar `CashierQuickActionSheet`**

Ya existe base de sheet por cajero. Convertirlo al componente comun `OperationalModalSheet`.

- [ ] **Step 2: Crear `MonitorFilterSheet`**

Filtros:

- activos/bloqueados.
- con venta/sin venta.
- ganancia/perdida.
- orden por venta, premio, caja, riesgo.

- [x] **Step 3: Ranking de numeros**

La lista de ranking debe ser contenido principal o sub-seccion, no boton suelto.

Si no hay datos:

```text
Sin ranking para este periodo
```

No mostrar vacio que parezca bug.

---

## Phase 4: Limites

**Objetivo:** administrar limites de cajero y limites propios sin que el admin se pierda entre global, cajero especifico, jugada y premios.

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\users\UserAccountsActivity.kt`
- Keep: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\sync\CashierLimitCloudSyncCoordinator.kt`
- Keep: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalCashierSalesLimitRepository.kt`

**Pantalla principal:**

- Card: `Aplicando a`.
- Valor: `Todos los cajeros`, `Mi limite admin`, o `3- Banca moreno`.
- Boton: `Cambiar`.
- Tabs internos:
  - `Venta`
  - `Jugadas`
  - `Premios`
- Boton claro: `Guardar servidor`.

**Estado 2026-06-09:** parcialmente implementado en `UserAccountsActivity.kt`.
- Hecho: la sección `Límites` ya muestra una tarjeta `Aplicando a` con botón `Cambiar`.
- Hecho: el alcance se elige en `ModalBottomSheet` con búsqueda y lista `LazyColumn`, usando la misma selección existente.
- Hecho: el dropdown superior de usuario se oculta en `Límites` para evitar controles duplicados.
- Hecho: los campos del editor se agrupan visualmente en `Venta`, `Premios`, `Jugadas` y `Picks`.
- Hecho: guardar `Valores globales` ahora abre confirmación antes de aplicar.
- Hecho: no se cambió `CashierSalesLimitInputs`, guardado local, push servidor ni reglas de límites.

- [x] **Step 1: Sheet para elegir alcance**

Opciones:

- `Todos los cajeros`.
- `Mi limite admin`.
- lista de cajeros ordenada natural.

- [x] **Step 2: Separar campos por grupos**

Venta:

- venta diaria.
- pago maximo.

Jugadas:

- quiniela.
- pale.
- super pale.
- tripleta.
- Pick 3 directo/caja.
- Pick 4 directo/caja.

Premios:

- tabla de pago.
- payout.

- [x] **Step 3: Confirmar cambios globales**

Cuando `Todos los cajeros` este activo, mostrar confirmacion antes de guardar.

Texto:

```text
Aplicar limite global
Esto cambia la base para cajeros sin configuracion personalizada.
```

- [x] **Step 4: No cambiar reglas actuales**

El objeto `CashierSalesLimitInputs` debe conservar todos sus campos aunque el usuario edite solo una pestaña.

---

## Phase 5: Sistema / Configuracion Operativa

**Objetivo:** ordenar la seccion `Sistema` real de la app, que hoy mezcla modo de venta, bloqueo de jugadas, bloqueo de loterias y guardado al servidor. No convertirla en diagnostico tecnico; dejar diagnostico como area secundaria.

**Estado 2026-06-09:** parcialmente implementado en `AdminConfigActivity.kt`.
- Hecho: jerarquia visual renombrada a `Bloqueo de loterías`, `Bloqueo de jugadas` y `Modo de venta`.
- Hecho: `Bloqueo de loterías` ya no usa dropdown largo; ahora usa `ModalBottomSheet` con buscador y lista `LazyColumn`.
- Hecho: `Bloqueo de jugadas` ya no muestra chips/input siempre abiertos; ahora usa sheet de confirmacion de tipo y numero.
- Hecho: `Modo de venta` queda separado en `Pantalla`, `Admin`, `Cajeros` y `Sync y servidor`, con perfil visible.
- Hecho: cambios de modo admin/cajero piden confirmacion antes de aplicar.
- Hecho: los sheets locales de Sistema usan el componente comun `OperationalModalSheet`.

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\admin\AdminConfigActivity.kt`
- Modify if labels/navigation need cleanup: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\NativeChrome.kt`
- Keep business logic: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalAdminLotteryConfigRepository.kt`
- Keep business logic: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\sales\SalesActivity.kt`

**Estado actual identificado en codigo:**

- `AdminConfigActivity.kt` muestra secciones:
  - `Operación`
  - `Caja`
  - `Bloqueo de lotería`
  - `Control de venta`
  - `Sistema`
- `Control de venta` maneja `blockedSalePlays`: bloqueo de numeros exactos por tipo de jugada.
- `Bloqueo de lotería` maneja loterias manualmente bloqueadas/habilitadas.
- `Sistema` maneja:
  - `Modo POS Lite`
  - modo disponible para admin: `Lotería`, `Pick`, `Lotería + Pick`
  - modo por defecto de cajero: `Lotería`, `Pick`, `Lotería + Pick`
  - guardar/sincronizar modo al servidor.

**Nueva estructura recomendada:**

La pantalla debe llamarse visualmente `Sistema` o `Configuración operativa`, pero internamente organizarse por impacto:

1. `Modo de venta`
   - Modo POS Lite.
   - Admin: Loteria / Pick / Loteria + Pick.
   - Cajeros por defecto: Loteria / Pick / Loteria + Pick.
   - Estado de sync del modo.

2. `Bloqueo de jugadas`
   - Bloquear numero exacto: Q, P, SP, T, Pick 3, Pick 4.
   - Lista de jugadas bloqueadas.
   - Accion quitar bloqueo.

3. `Bloqueo de loterias`
   - Loterias abiertas.
   - Loterias bloqueadas por banca.
   - Loterias cerradas por calendario.
   - Elegir loteria con sheet buscable, no dropdown largo.

4. `Caja e impresora`
   - Logo banca.
   - Impresora.
   - Ajustes de ticket.

5. `Sync y servidor`
   - Estado de guardado del modo.
   - Boton guardar servidor.
   - Solo acciones de sync relacionadas a esta pantalla.

6. `Diagnostico`
   - Solo como entrada secundaria para soporte.
   - No debe competir con modo de venta ni bloqueos.

- [x] **Step 1: Renombrar jerarquia visual sin cambiar rutas**

Mantener rutas existentes, pero ordenar el contenido asi:

```text
Sistema
  Modo de venta
  Bloqueo de jugadas
  Bloqueo de loterías
  Caja e impresora
  Sync y servidor
  Diagnóstico
```

Si se conserva el titulo `Sistema`, agregar subtitulo:

```text
Modo, bloqueos y sincronización de la banca
```

- [x] **Step 2: Convertir `Bloqueo de lotería` a sheet buscable**

Problema actual:

- usa dropdown largo.
- puede cortarse en pantallas pequenas.

Solucion:

- `CurrentScopeCard`: muestra loteria elegida o `Elegir lotería`.
- `SearchableOptionSheet`: lista loterias con buscador.
- cada fila: nombre, hora, tipo, estado.
- acciones visibles solo al elegir loteria:
  - `Bloquear`
  - `Habilitar`
  - `Limpiar selección`

- [x] **Step 3: Convertir `Bloqueo de jugadas` a flujo por sheet**

Pantalla principal muestra:

- total bloqueadas.
- estado `Libre` o `Activo`.
- lista compacta de bloqueadas.
- boton `Bloquear jugada`.

Sheet `Bloquear jugada`:

- tipo de jugada con chips: Q, P, SP, T, P3, P3B, P4, P4B.
- campo numero.
- ejemplo segun tipo.
- boton `Bloquear`.

Esto evita mostrar todos los chips y el input siempre abiertos.

- [x] **Step 4: Reorganizar `Modo de venta`**

Separar visualmente:

- `Admin vende`: Loteria / Pick / Loteria + Pick.
- `Cajero entra en`: Loteria / Pick / Loteria + Pick.
- `Pantalla compacta`: POS Lite on/off.
- `Servidor`: estado y guardar.

Regla:

- cambiar modo puede guardar local como hoy.
- sincronizar servidor debe seguir usando `onSyncSystemModeConfig`.
- no cambiar `applyAdminModeSegment`.
- no cambiar `applyCashierDefaultModeSegment`.

- [x] **Step 5: Agregar confirmacion donde afecta venta activa**

Para cambios que pueden afectar ventas actuales:

- bloquear loteria con tickets del dia.
- anular/borrar/pasar tickets por bloqueo.
- bloquear jugada exacta.
- cambiar modo admin/cajero.

Usar `DangerConfirmSheet` o sheet de confirmacion con texto concreto.

No pedir confirmacion para:

- abrir sheet.
- buscar loteria.
- cambiar seleccion visual.

- [x] **Step 6: Perfil y permisos visuales**

Admin:

- puede ver `Modo de venta`, `Bloqueo de jugadas`, `Bloqueo de loterias`, `Caja e impresora`, `Sync y servidor` de su banca.
- no ve herramientas globales master.

Master:

- puede ver configuracion global aparte, pero no mezclarla con la configuracion operativa de admin.

Cajero/Supervisor:

- no deberian ver esta configuracion operativa salvo lectura minima si ya existe ruta especifica.

- [x] **Step 7: Copy claro**

Evitar nombres genericos:

- `Sistema` solo.
- `Configurar`.
- `Gestionar`.
- `Otros`.

Usar nombres operativos:

- `Modo de venta`.
- `Bloqueo de jugadas`.
- `Bloqueo de loterías`.
- `Guardar modo en servidor`.
- `Loterías disponibles hoy`.
- `Jugada bloqueada para todos`.

---

## Phase 6: Deportiva

**Objetivo:** dejar una guia visual para Deportiva sin activar ni tocar operacion real hasta que el modulo empiece formalmente.

**Files:**
- Review only first: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\sportsbook\SportsbookActivity.kt`
- Review only first: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\storage\LocalMasterConfigRepository.kt`
- Review only first: `C:\Users\Randy Cordero\Desktop\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\sportsbook\SportsbookBoardRemoteStore.kt`

**No tocar por ahora:**

- odds.
- tickets deportivos.
- edge functions deportivas.
- pagos deportivos.
- configuracion global real.

**Estructura visual futura:**

- Header: `Deportiva`.
- Card de estado: `No operativa` o `Activa`.
- Tabs reales:
  - `Cartelera`
  - `Tickets`
  - `Config`
- Sheet de filtros:
  - deporte.
  - liga.
  - mercado.
  - fecha.
- Sheet de jugada:
  - seleccion.
  - monto.
  - confirmacion.

**Estado 2026-06-09:** documentado en `docs/superpowers/sportsbook-ui-blueprint.md`.
- Hecho: se dejo blueprint visual futuro sin tocar odds, tickets, pagos ni servidor deportivo.
- Hecho: se verifico que el tablero deportivo solo carga datos si `canLoadSportsbookBoard` permite abrir el modulo.

- [x] **Step 1: Solo documentar UI futura**

No implementar hasta autorizacion separada.

- [x] **Step 2: Mantener feature gate**

Si Deportiva esta apagada, mostrar estado claro y no cargar datos innecesarios.

---

## Phase 7: Validacion Visual Y Performance

**Objetivo:** asegurar que el rediseño no cree lag, dropdowns cortados ni listas que saltan.

**Files:**
- Manual QA on connected Android device.
- Optional unit tests for pure helpers.

**Estado 2026-06-09:** pendiente de build/debug manual para prueba visual en celular. Codex no ejecuta Gradle por la lentitud de la PC.

- [ ] **Step 1: Verificar pantallas**

Revisar:

- Cuadre.
- Reporte.
- Monitoreo.
- Limites.
- Sistema.
- Deportiva apagada.

- [ ] **Step 2: Verificar sheets**

Cada sheet debe:

- abrir por accion explicita.
- cerrar con X/backdrop/swipe.
- no tapar botones importantes sin footer.
- tener lista con scroll interno.
- mantener footer visible si hay `Aplicar` o `Guardar`.

- [ ] **Step 3: Verificar listas**

Cada lista larga debe usar:

- `LazyColumn`.
- key estable.
- buscador si hay mas de 8 items.
- estado vacio claro.

- [ ] **Step 4: Verificar rendimiento Compose**

Evitar:

- filtrar/ordenar listas grandes en cada recomposicion.
- crear lambdas/listas pesadas dentro de filas.
- rows sin keys.
- animaciones decorativas pesadas.

Usar:

- `remember`.
- `derivedStateOf` solo para calculos no triviales.
- componentes pequenos.
- motion corto y discreto.

- [ ] **Step 5: Prueba manual sin Gradle desde Codex**

Como la PC se pone pesada, Codex no ejecuta Gradle salvo autorizacion directa.

El usuario puede hacer build manual y luego se valida en celular conectado:

- abrir Cuadre.
- cambiar periodo.
- abrir Reporte.
- cambiar cajero.
- abrir Monitoreo.
- abrir acciones de cajero.
- abrir Limites.
- cambiar alcance.
- abrir Sistema.
- revisar Deportiva apagada.

---

## Orden De Ejecucion Recomendado

1. Componentes comunes de sheets.
2. Cuadre.
3. Reporte.
4. Monitoreo.
5. Limites.
6. Sistema.
7. Deportiva solo como blueprint visual.

Motivo: Cuadre y Reporte son menos peligrosos que Limites y Sistema, pero comparten el patron principal. Primero se prueba ahi el componente comun; luego se aplica a las secciones delicadas.

## Resultado Esperado

- Menos botones visibles al mismo tiempo.
- Dropdowns largos reemplazados por sheets con busqueda.
- Periodos claros, especialmente semana lunes-domingo.
- Cajeros ordenados por numero/nombre visible.
- Acciones peligrosas protegidas con confirmacion.
- Monitoreo mas rapido de leer.
- Sistema mas claro para soporte.
- Deportiva preparada visualmente sin afectar produccion.
