# Filtros compactos para Tickets y Cobros

## Diagnóstico actual

- `TicketSummaryActivity` (sección **Tickets**) todavía muestra de forma fija el buscador y los selectores de periodo/estado. También tiene un botón `Filtros` que abre el mismo conjunto de controles, por lo que la pantalla termina duplicando la interfaz.
- `TicketLookupActivity` (sección **Cobros**) ya usa `FilterChip` con menús temporales para cajero, fecha y estado. Ese es el patrón correcto para reutilizar.
- El chip de cajero puede recibir la misma cuenta dos veces: una por id técnico (`CAJ-*`) y otra por username. La lista visual debe agrupar por identidad legible y conservar una sola opción.
- El problema es visual y de composición; no requiere cambiar filtros, consultas, permisos, sincronización ni reglas de negocio.

## Patrón recomendado

La pantalla debe tener tres niveles:

1. **Lista como contenido principal.** Después de la barra superior se muestra el contador y la lista de tickets.
2. **Acciones compactas.** Dos chips principales: `Buscar` y `Filtros`. Permanecen visibles porque son acciones, no paneles de controles.
3. **Filtros bajo demanda.** Al tocar `Filtros`, se muestra una fila desplazable de `FilterChip`:
   - `Periodo`: Hoy, Ayer, Este mes.
   - `Fecha`: fecha exacta mediante `DatePickerDialog`.
   - `Estado`: Todos, Ganadores/Pendientes, Pagados según la sección.
   - `Cajero`: Todos o un cajero concreto, solo para roles autorizados.
   - `Lotería`: Todas o una lotería concreta.

Cada chip abre un `DropdownMenu` temporal. Al seleccionar una opción, el menú se cierra y se conserva el mismo callback de filtro existente. Al tocar fuera, también se cierra el menú. No se agrega un botón “Aplicar” porque los filtros actuales ya se resuelven al seleccionar.

## Contrato visual propuesto

```text
Barra superior
Contador de resultados                         [Actualizar]
[ Buscar ] [ Filtros 2 ]
[Fecha: Hoy] [Estado: Todos] [Cajero: Todos]  <- solo cuando Filtros está abierto
------------------------------------------------
Lista de tickets (ocupa el espacio restante)
```

Cuando el panel está cerrado, solo se conservan los chips activos como resumen compacto, por ejemplo `Fecha: Ayer` y `Estado: Pagados`. La lista no debe bajar por controles vacíos.

## Reglas para Tickets

- Reemplazar los controles fijos `SearchBox`, `Tiempo` y `Estado` por los chips desplegables del patrón de Cobros.
- Mantener exactamente los callbacks existentes: `onPeriodChange`, `onMonthChange`, `onDateChange`, `onStatusBucketChange`, `onCashierChange`, `onLotteryFilterChange` y `onQueryChange`.
- `Buscar` abre/cierra el campo de texto; no debe quedar siempre ocupando altura.
- La fecha exacta continúa usando el calendario actual.
- El resultado y los totales siguen calculándose con `filterSummaryTickets`; solo cambia la visibilidad de los controles.
- Las opciones de cajero se deduplican por username en Tickets y por etiqueta visible en Cobros; esto solo corrige la presentación, no el valor usado para consultar.

## Reglas para Cobros

- Conservar el flujo que ya funciona con `FilterChip` y `DropdownMenu`.
- Mantener `Cajero`, `Fecha` y `Estado` como chips independientes.
- Mantener `Paga todo` exclusivamente dentro del modo Cobros; no debe aparecer en Tickets.
- La lista de ganadores/pagos sigue siendo la protagonista.

## Implementación Material 3

Usar `FilterChip` para filtros seleccionables y `DropdownMenu`/`DropdownMenuItem` para sus opciones. Android documenta que `FilterChip` está destinado a filtrar contenido y que el estado seleccionado debe mantenerse explícitamente. Los menús deben usar `expanded` y `onDismissRequest` para abrirse y cerrarse al tocar fuera.

Para un selector editable con texto se reserva `ExposedDropdownMenuBox`; no es necesario para cajero, estado o periodo porque esos valores son opciones cerradas. La selección de fecha continúa con `DatePickerDialog`.

## Verificación sin Gradle

- Revisar por diff que no cambien repositorios, payloads, permisos ni callbacks.
- Ejecutar únicamente los contratos Node.js de UI/filtros existentes.
- Validar manualmente que Tickets y Cobros compartan el mismo patrón visual, pero con acciones propias.
- No ejecutar ventas, pagos, migraciones ni cambios en servidor para esta mejora.

Fuentes oficiales:

- [FilterChip en Jetpack Compose](https://developer.android.com/develop/ui/compose/components/chip)
- [DropdownMenu en Jetpack Compose](https://developer.android.com/develop/ui/compose/components/menu)
- [ExposedDropdownMenuBox API](https://developer.android.com/reference/kotlin/androidx/compose/material3/ExposedDropdownMenuBox.composable)
- [Material 3 e insets](https://developer.android.com/develop/ui/compose/system/material-insets)
