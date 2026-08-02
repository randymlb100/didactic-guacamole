# Auditoría Material 3: Supervisores, Ajustes y Perfil Master

## Criterio aplicado

- Mantener las listas y el estado operativo como contenido principal.
- Usar `CurrentScopeDropdownCard` para cambiar de área o vista cuando hay varias opciones.
- Reservar `ModalBottomSheet` para filtros y acciones secundarias; no cambiar contratos ni payloads.
- Usar `FilterChip` para filtros de estado, no una fila permanente de botones cuando el espacio es limitado.
- Mantener acciones destructivas separadas y confirmadas.

## Cambios aplicados en esta fase

- Supervisores: la selección entre resumen, grupo, credenciales y creación ahora es un selector desplegable compacto.
- Perfil Master: Bancas, Módulos, Sistema y Seguridad ahora se seleccionan desde un único alcance desplegable.
- Ajustes: el hub conserva su búsqueda y ahora permite filtrar categorías por `Todas`, `Operación`, `Caja` o `Sistema` con chips horizontales.
- Ajustes: el hub muestra el total de categorías encontradas y ofrece `Limpiar filtros` solo cuando hay una búsqueda o grupo activo; una línea divisoria separa navegación de indicadores.
- Se conservaron los mismos identificadores, estados, callbacks y opciones existentes.

## Siguiente fase de Ajustes

- Mantener el hub de áreas como entrada principal.
- Convertir filtros o subopciones permanentes en chips o selectores contextuales solo donde ocupen espacio sin aportar información continua.
- Revisar cada área por separado antes de mover controles para no alterar el flujo de guardado y sincronización.

## Mejoras aplicadas después de la auditoría

- La fila resaltada coincide con el supervisor cuyo detalle está visible.
- El encabezado usa `IconButton` con descripciones accesibles para volver y actualizar.
- El estado de cada supervisor se representa con badge; no se muestra un interruptor deshabilitado que parezca editable.
- El filtro de cajeros usa un `FilterChip` y `ModalBottomSheet`; la búsqueda, selección masiva y asignación siguen usando los mismos callbacks.

## Límites de esta auditoría

Esta revisión de código no sustituye una captura en dispositivo. Deben validarse después el tamaño de texto, orientación horizontal, teclado, accesibilidad y comportamiento de las hojas en el teléfono real.

## Referencias oficiales

- [Material 3 Compose components](https://developer.android.com/develop/ui/compose/components)
- [Material 3 bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
- [Material 3 chips](https://developer.android.com/develop/ui/compose/components/chip)
- [Material 3 menus](https://developer.android.com/develop/ui/compose/components/menu)
- [Material 3 accessibility](https://developer.android.com/develop/ui/compose/accessibility)
