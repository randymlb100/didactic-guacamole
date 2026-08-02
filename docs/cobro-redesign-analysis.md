# Análisis y propuesta de rediseño para Cobro

## Objetivo

Aplicar el mismo criterio de organización usado en Ajustes a la sección **Cobro/Cuadre**:

- El contenido financiero debe ser el protagonista.
- Los controles de fecha, periodo, acciones de exportación y cierre no deben ocupar la mitad de la pantalla.
- En teléfono/POS se muestra una sola superficie principal a la vez.
- En tablet/pantalla ancha se puede usar una composición lista-detalle.
- Se conserva la lógica actual de fechas, rangos, cache, sincronización, exportación, impresión y cierre.

Esta fase es de análisis y propuesta. No cambia código ni servidor.

## Diagnóstico del código actual

La pantalla está en:

`app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`

### Estructura actual observada

Dentro de `FinanceRoute` hay esta jerarquía:

```text
Surface
└── Column
    ├── FinanceCompactHeader
    ├── badge Sincronizado
    ├── CompactSegmentedSelector
    └── LazyColumn
        ├── FinanceHeaderCard
        │   ├── periodo actual
        │   ├── cambiar periodo
        │   ├── selector de fecha
        │   ├── selector de rango
        │   ├── selector mensual
        │   ├── mensaje de acción
        │   └── Exportar
        ├── Resumen del corte
        ├── tarjetas financieras
        ├── ranking
        ├── perfil cajero
        ├── cierre de turno
        └── clasificación de tickets
```

### Por qué la lista queda reducida

El problema no es que `LazyColumn` esté mal usado. El problema es de jerarquía:

1. La pantalla reserva espacio fijo para encabezado, estado y selector.
2. `FinanceHeaderCard` se dibuja siempre como el primer elemento de la lista.
3. Aunque el usuario esté viendo Resumen, los controles de periodo siguen formando parte del primer bloque.
4. La pestaña “Periodo” no se comporta como un destino independiente: los controles siguen dentro del mismo encabezado.
5. Exportar está visualmente cerca del contenido principal aunque se usa ocasionalmente.
6. En POS, la misma estructura conserva demasiados elementos antes de mostrar el resultado que el usuario busca.

El resultado es una pantalla con muchos controles compitiendo con el contenido de Cobro.

## Referencia oficial

Android recomienda:

- una pantalla de resumen con lo más importante;
- dividir categorías y controles en subscreens;
- usar listas y lista-detalle;
- evitar que una sola superficie se estire o acumule demasiados elementos;
- adaptar la presentación según el tamaño de ventana.

Referencias:

- [Android Settings: overview, groups and subscreens](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
- [Android adaptive layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout)
- [Android list-detail layout](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
- [Compose adaptive apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)
- [Compose lazy lists](https://developer.android.com/develop/ui/compose/lists)
- [Material components in Compose](https://developer.android.com/develop/ui/compose/components)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

Android indica que los ajustes y controles deben agruparse y que las categorías extensas deben abrir un subscreen. El mismo principio aplica a Cobro: el resultado principal debe tener su propia superficie y los controles secundarios deben abrirse de manera enfocada.

## Propuesta de arquitectura UX

### Pantalla principal de Cobro

La pantalla principal debe priorizar el resultado financiero:

```text
Cobro
├── Encabezado + estado de sincronización
├── Barra compacta de contexto
│   ├── Fecha/rango actual
│   ├── Filtro visible
│   └── Más acciones
└── Lista principal
    ├── Resultado del corte
    ├── Caja final
    ├── Premios
    ├── Comisiones
    ├── Tickets
    └── Clasificación
```

La pantalla no debe mostrar automáticamente:

- calendario completo;
- selector mensual expandido;
- rango Desde/Hasta;
- cinco botones de exportación;
- historial completo;
- ranking de cajeros si el usuario está en un perfil que no lo necesita.

### Detalle de controles

Al tocar la barra de fecha se abre una pantalla o panel de “Periodo”:

```text
‹ Cobro / Periodo
├── Hoy
├── Ayer
├── Semana
├── Mes
├── Rango personalizado
└── Aplicar
```

En POS compacto, este detalle ocupa toda la pantalla. En tablet, puede aparecer en un panel lateral o pane secundario.

### Acciones de exportación

Mantener un solo botón “Exportar” o un icono de más acciones. Dentro:

- WhatsApp
- Compartir
- Imprimir
- Térmico
- Guardar

No deben ocupar cinco filas visibles en la pantalla principal. Sus callbacks permanecen exactamente iguales.

## Comportamiento por tamaño de pantalla

### POS y teléfono compacto

Usar una sola columna:

```text
┌──────────────────────────────┐
│ Cobro                    ⋮   │
│ Banca · Hoy                  │
├──────────────────────────────┤
│ Hoy · 21 julio          ›   │
│ Sincronizado                 │
├──────────────────────────────┤
│ RESUMEN DEL CORTE            │
│ Venta              $50,000   │
│ Caja final         $49,000   │
│ Premios             $1,000   │
│ Comisiones            $500   │
├──────────────────────────────┤
│ Tickets / clasificación     │
│ ...                          │
└──────────────────────────────┘
```

Reglas:

- El periodo se presenta como una fila compacta, no como formulario permanente.
- El resumen ocupa todo el ancho disponible.
- El selector “Resumen / Periodo / Detalle / Cierre” no debe reservar espacio si puede sustituirse por destinos o acciones contextuales.
- Exportar queda en el menú de acciones.
- El contenido financiero debe empezar antes y tener más área vertical.

### Tablet y pantalla ancha

Usar lista-detalle:

```text
┌────────────────────┬──────────────────────────────────┐
│ COBRO              │ Resumen del corte                │
│                    │                                  │
│ Hoy             ›  │ Venta                   $50,000  │
│ Ayer            ›  │ Caja final              $49,000  │
│ Semana          ›  │ Premios                  $1,000  │
│ Mes              ›  │ Comisiones                 $500  │
│ Rango           ›  │                                  │
│                    │ Tickets y clasificación          │
└────────────────────┴──────────────────────────────────┘
```

La API oficial `NavigableListDetailPaneScaffold` puede considerarse en una fase posterior si el proyecto incorpora las dependencias adaptativas de Material 3. No se debe añadir una dependencia solo por rediseño visual sin revisar compatibilidad y tamaño de APK.

## Mapeo Material 3

| Necesidad | Componente |
|---|---|
| Encabezado | `TopAppBar` o el encabezado nativo ya usado |
| Fecha/rango actual | `ListItem` clicable con icono calendario y flecha |
| Estado de sync | `AssistChip` o texto con icono |
| Lista financiera | `LazyColumn` |
| Cada fila de dinero | `ListItem` o panel de una sola columna |
| Resumen corto | `Card` o `ElevatedCard` |
| Filtros contextuales | `FilterChip` |
| Elección de periodo | `SingleChoice` en diálogo/subscreen |
| Exportación | `DropdownMenu` o bottom sheet de acciones |
| Confirmar cierre | `AlertDialog` o panel de confirmación |
| Feedback | `SnackbarHost` |

## Reglas visuales

- Usar `MaterialTheme.colorScheme`, `typography` y formas existentes.
- No usar un color diferente para cada acción de exportación.
- Verde solo para saldo positivo o sincronización correcta.
- Amarillo para premios pendientes o advertencias.
- Rojo para errores y diferencias negativas.
- La lista debe tener una columna numérica alineada a la derecha.
- Los valores monetarios deben usar el mismo formato y fuente en todas las filas.
- La etiqueta debe estar a la izquierda y el valor a la derecha.
- El contenido principal debe tener ancho completo en POS y un ancho máximo razonable en pantallas grandes.
- No mostrar textos secundarios que repitan el título.
- El estado se comunica con texto e icono, no únicamente con color.

## Qué se conserva sin cambios

El rediseño no debe cambiar:

- `onLoadDaySnapshot`;
- `onLoadPeriodReport`;
- `onRefreshDaySummary`;
- `onSelectDay`;
- `onSelectManualRange`;
- `onSelectMonth`;
- exportación por WhatsApp;
- compartir;
- impresión normal;
- impresión térmica;
- guardado;
- cierre de turno;
- cache local;
- sincronización;
- permisos por rol;
- cálculo de caja, comisión, premios o neto.

La fecha solo cambia el rango consultado. No debe provocar una segunda fuente de datos ni recalcular ventas de forma diferente.

## Plan de implementación seguro

### Fase 1 — Contrato de información

- Definir qué contenido es principal en cada rol.
- Mantener el mismo `FinanceDaySnapshot`.
- Separar visualmente periodo, exportación y resultado.
- No cambiar repositorios ni callbacks.

### Fase 2 — Barra compacta de contexto

- Reemplazar el bloque permanente de controles por una fila “Periodo actual”.
- Abrir el selector actual en una superficie enfocada.
- Mantener calendario y rango existentes.

### Fase 3 — Lista principal

- Hacer que Resumen del corte sea el primer contenido visible.
- Dar ancho completo a la lista.
- Mover exportación al menú.
- Mantener tarjetas financieras, clasificación y ranking con sus permisos.

### Fase 4 — Detalle adaptativo

- Teléfono/POS: un pane por vez.
- Tablet/Wide: evaluar lista-detalle.
- No añadir dependencia adaptativa hasta comprobar el catálogo Gradle actual.

### Fase 5 — Accesibilidad y estados

- Filas completas tocables.
- Iconos con descripción.
- Estados de sincronización anunciables.
- Fuente grande y modo oscuro.
- Evitar que el teclado o las barras del sistema cubran controles.

### Fase 6 — Pruebas

- Periodo Hoy/Ayer/Semana/Mes.
- Rango personalizado.
- Lista visible a ancho completo.
- Exportar no cambia el resumen.
- Cierre no cambia la navegación.
- Roles Master/Admin/Supervisor/Cajero.
- POS compacto.
- Sin llamadas nuevas ni polling.

## Criterios de aceptación

1. La lista o resumen financiero comienza cerca del encabezado.
2. El calendario no ocupa espacio permanente.
3. Exportar está disponible sin dominar la pantalla.
4. La fecha actual se entiende y se puede cambiar fácilmente.
5. La lista usa el ancho disponible.
6. En POS se puede ver más contenido sin desplazarse tanto.
7. En tablet se puede usar una composición lista-detalle si aporta valor.
8. La navegación de Periodo tiene Atrás claro.
9. Se mantienen los mismos resultados y callbacks.
10. No se agregan llamadas de red.

## Evidencia y límites

- El diagnóstico está respaldado por la estructura actual de `FinanceRoute` y `FinanceHeaderCard`.
- Se revisaron las guías oficiales de Android Settings, layouts adaptativos, listas Compose y list-detail.
- No se pudo realizar una captura visual del dispositivo en esta fase; la validación visual debe hacerse con un emulador o teléfono conectado.
- No se ha modificado código en esta fase.

## Ajuste premium aplicado a la lista de ganadores

La pantalla operativa de Cobro ahora conserva una sola superficie de resumen y elimina
las tres tarjetas anidadas que competían con la lista. El orden visible queda:

1. contexto de Cobro y fecha;
2. premios pendientes, pagados y total;
3. filtros mutuamente excluyentes;
4. lista de tickets ganadores.

Cada fila identifica el importe como `Premio`, mantiene el color de estado y abre el
mismo ticket oficial. No se modificaron la validación de premios, la fecha de sorteo,
Realtime, caché, pago ni sincronización.

## Investigación comparativa y guía de componentes

### Qué hacen las pantallas administrativas modernas

El patrón común en productos de caja, pagos y back-office es separar tres niveles:

1. **Contexto:** qué banca, usuario y período se están viendo.
2. **Resultado principal:** la lista o resumen que el usuario consulta repetidamente.
3. **Acciones secundarias:** exportar, imprimir, compartir, guardar y cerrar turno.

El error actual es que el contexto y las acciones ocupan el primer bloque de la lista en cada estado. La recomendación es que el resumen/lista sea el protagonista y que el período sea una fila compacta que abre el selector solo cuando se necesita.

### Componente correcto para cada caso

| Necesidad en Cobro | Componente/patrón recomendado | Motivo |
|---|---|---|
| Venta, caja final, premios y neto | `Card` o `ElevatedCard` con `ListItem`/filas de valor | Jerarquiza métricas sin convertir toda la pantalla en botones. |
| Muchas filas de tickets o movimientos | `LazyColumn` con filas compactas y estados | Es el contenido principal y debe comenzar arriba. |
| Período actual | `ListItem` clicable o `FilterChip` con fecha visible | El usuario entiende qué filtro está activo sin abrir controles. |
| Hoy/Ayer/Semana/Mes | Selector de opción única en `DropdownMenu` o diálogo | Son opciones mutuamente excluyentes; no deben parecer navegación principal. |
| Rango personalizado | `DateRangePicker` dentro de diálogo/modal | El calendario aparece bajo demanda y no consume espacio permanente. |
| Exportar, imprimir y compartir | Menú de tres puntos (`DropdownMenu`) | Son acciones secundarias y no deben competir con el resultado. |
| Sincronizando, sincronizado o pendiente | `AssistChip`, icono y texto de estado | Es información de estado, no una acción primaria. |
| Alertas financieras | Banner/`Card` de alerta con contraste y texto explicativo | Comunica riesgo sin pintar toda la pantalla de rojo. |
| Cerrar turno | Sección separada con acción primaria y confirmación | Es una operación irreversible o sensible, no un filtro. |
| Tablet o pantalla amplia | List-detail o supporting pane | Permite conservar la lista visible mientras se inspecciona un detalle. |
| POS/terminal pequeño | Una sola columna, controles mínimos y filas densas | Prioriza lectura rápida y reduce desplazamiento. |

Android documenta `DateRangePicker` para fechas/rangos, `ModalBottomSheet` para acciones temporales y `DropdownMenu` para listas de opciones que no deben permanecer abiertas. También recomienda que el diseño se adapte por tamaño de ventana: una sola superficie en compacto, dos paneles cuando existe espacio suficiente. Ver [Date pickers](https://developer.android.com/develop/ui/compose/components/datepickers), [Bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets), [Menus](https://developer.android.com/develop/ui/compose/components/menu) y [List-detail layouts](https://developer.android.com/develop/adaptive-apps/guides/list-detail).

### Aplicación exacta al Cobro actual

La estructura propuesta queda así:

```text
Cuadre · Banca/usuario
Estado de sincronización                         ⋮
Período actual: Hoy · 21 jul 2026        Cambiar
--------------------------------------------------
Resumen del corte                                 
Venta       Caja final       Premios       Neto    
--------------------------------------------------
Lista/resumen financiero principal                
--------------------------------------------------
Acciones de cierre, historial y alertas según rol
```

En esta fase no se debe crear una segunda fuente de datos ni modificar payloads. El `FinanceDaySnapshot`, las callbacks actuales, permisos, caché y sincronización permanecen iguales; solo cambia el orden y la superficie visual.

### Decisión técnica

- **Teléfono:** eliminar el `FinanceHeaderCard` como primer elemento permanente de la `LazyColumn`; convertirlo en una barra de contexto compacta.
- **POS:** conservar la misma información, pero ocultar etiquetas redundantes, usar una fila de período y dejar las acciones en overflow.
- **Tablet/Wide:** mantener la lista principal y abrir detalle/acciones en un panel lateral solo si el ancho lo permite; no agregar una dependencia adaptativa sin verificar el catálogo actual.
- **Calendario:** reutilizar el callback existente `onSelectManualRange`; mostrar `DateRangePicker` solo al pulsar “Cambiar período”.
- **Exportación:** reutilizar `onPrint`, `onThermal`, `onWhatsApp`, `onShare` y `onSave` desde un menú único; no alterar el contenido exportado.
- **Cierre:** mantener `onSaveSnapshot` y `onCloseTurno` en una tarjeta/sección propia, con confirmación visible.

La guía local de Compose Material 3 disponible en el entorno es específica de Wear OS, por lo que no se usa como fuente para componentes de teléfono/POS. La fuente normativa para esta pantalla será la documentación oficial de Android Compose Material 3 y adaptive layouts.

## Ajuste de una sola superficie aplicado a Cobro

La revisión final confirmó que `Pendientes`, `Pagados` y `Todos` son opciones
mutuamente excluyentes, no tres acciones principales. Mantenerlas como tres chips
permanentes dentro del resumen repetía el estado del encabezado, ocupaba altura y
retrasaba la lista ganadora.

La pantalla queda organizada así:

1. encabezado con banca, fecha y estado activo;
2. selector persistente `Estado de cobro`, con la opción elegida siempre visible;
3. un único resumen compacto de pendientes, pagados y total;
4. lista de tickets ganadores filtrada.

El selector reutiliza `CurrentScopeDropdownCard` y un `DropdownMenu` Material 3.
No crea rutas, ventanas ni fuentes de datos nuevas. Se conservaron el enum de
filtro, `filterPendingWinnerTickets`, `filterPaidWinnerTickets`, los importes,
la apertura del ticket oficial, Realtime, sincronización y pago.
