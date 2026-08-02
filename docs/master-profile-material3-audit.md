# Auditoría del Perfil Master: estructura y Material 3

## Mapa funcional

El Perfil Master queda organizado en un solo centro con cinco áreas visibles:

1. **Resumen**: métricas, estado de sincronización y accesos rápidos.
2. **Bancas**: búsqueda, filtro, selección de admin, cajeros, fondos, bloqueo y credenciales.
3. **Módulos**: acceso a Servicios, Videojuegos y Deporte; la configuración detallada se abre desde la misma tarjeta.
4. **Sistema**: límites master, cuenta de Recargas Rápidas, cartera, servidor, nube y snapshot.
5. **Seguridad**: auditoría, credenciales emitidas y acciones sensibles con confirmación.

## Hallazgos corregidos

- El resumen podía mostrar únicamente ceros cuando todavía no existían bancas. Ahora muestra un estado vacío explicativo y el botón **Crear primera banca**.
- Las tarjetas de Servicios, Videojuegos y Deporte mostraban “Configurar” sin una acción clara. Ahora toda la tarjeta abre el editor Master de módulos.
- Se conserva el selector desplegable de área para evitar que el usuario tenga que navegar por muchas pantallas independientes.
- No se modificaron callbacks de guardado, sincronización, credenciales, fondos, permisos ni payloads.

## Componentes aplicados

- `CurrentScopeDropdownCard` para seleccionar el área de administración.
- `MetricStrip` y `CompactStatusBadge` para estado operativo resumido.
- `Surface` + estado vacío accionable para evitar pantallas sin orientación.
- Tarjetas accionables para módulos y acciones explícitas con iconos.
- `AlertDialog` únicamente en operaciones sensibles ya existentes.

## Siguiente mejora segura

En teléfonos se mantiene una sola columna. En tabletas, modo horizontal o ventanas amplias, la evolución recomendada es un diseño **lista-detalle**: la lista de áreas queda a la izquierda y el detalle a la derecha. Esa fase requiere validar la dependencia adaptativa y probar orientación; no se mezcla con cambios de servidor.

## Referencias oficiales

- [Jetpack Compose y Material 3](https://developer.android.com/develop/ui/compose/documentation)
- [Diseño lista-detalle adaptativo](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
- [Layouts canónicos adaptativos](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts)
- [Snackbar para confirmaciones no bloqueantes](https://developer.android.com/develop/ui/compose/components/snackbar)

## Revisión posterior de Servicios y Videojuegos

- Al cambiar de módulo, el catálogo anterior se limpia antes de iniciar la nueva carga para evitar mezclar productos visualmente.
- Después de confirmar una operación, la pantalla muestra una confirmación visible con el resumen devuelto por el proveedor.
- Un producto sin contrato de operación reconocido queda bloqueado para evitar enviar un formulario incompleto.
