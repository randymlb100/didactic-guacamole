# Rediseño de Límites con Material 3

## Alcance

La pantalla conserva los repositorios, payloads, contratos y reglas actuales. El cambio es de organización visual.

## Componentes usados

- `Scaffold` y encabezado compacto para la estructura de la pantalla.
- `FilterChip` para seleccionar el área activa: resumen, pool, cajeros, admin, cobros/recargas y POS.
- `ModalBottomSheet` para cambiar de área sin llenar la pantalla de controles.
- Paneles y filas de resumen para mantener visible la diferencia entre pool global y límite individual.
- Campos existentes para editar montos sin cambiar su conversión ni el guardado.

## Reglas de UX

- Pool, cajeros, admin y caja permanecen separados.
- La lista/resumen es la vista principal.
- La selección de área es temporal y se cierra al elegir una opción.
- No se agregan rutas, llamadas ni cambios de servidor.

Referencias oficiales: [FilterChip](https://developer.android.com/develop/ui/compose/components/chip), [menús](https://developer.android.com/develop/ui/compose/components/menu), [Dialog](https://developer.android.com/develop/ui/compose/components/dialog) y [Material 3 insets](https://developer.android.com/develop/ui/compose/system/material-insets).
