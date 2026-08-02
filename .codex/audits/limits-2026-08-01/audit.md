# Auditoría UX — Límites

## Resultado

La lógica visible está presente, pero la arquitectura de información mezcla administración de cuentas, navegación y edición de límites en una sola columna. En teléfono compacto, el usuario debe interpretar demasiados controles antes de llegar a la tarea principal.

## Recorrido observado

1. Entrada a Límites — Estado: confuso
   - Conserva métricas, búsqueda, filtros por rol, actualización manual y navegación de Cajeros aunque el usuario ya eligió Límites.
   - Hay demasiadas acciones con el mismo peso visual.
   - La edición comienza debajo del pliegue.

2. Edición global — Estado: funcional, pero denso
   - El alcance se entiende y la separación por tipo de jugada es útil.
   - El resumen repite valores que aparecen inmediatamente en el formulario.
   - Todos los campos se muestran al mismo tiempo y no existe una jerarquía clara entre venta diaria, premios y límites por jugada.

## Estructura recomendada

1. Inicio de Límites
   - Base global
   - Pool por lotería y número
   - Límites personales por cajero

2. Detalle de Base global
   - Resumen compacto
   - Secciones expandibles: General, Lotería normal, Pick
   - Una sola acción Guardar

3. Detalle de Pool
   - Elegir lotería
   - Elegir tipo de jugada
   - Buscar número y editar exposición

4. Detalle personal
   - Elegir cajero mediante hoja modal
   - Mostrar qué hereda y qué tiene personalizado
   - Editar únicamente excepciones

## Componentes Material 3

- `Scaffold` y `TopAppBar` para la estructura de cada destino.
- `ListItem` o tarjetas navegables para los tres tipos de límites.
- `ModalBottomSheet` solo para selecciones cortas, como cajero o lotería.
- Secciones expandibles para reducir densidad, no para ocultar el estado actual.
- Acción Guardar única y estable al final o en una barra inferior.
- `NavigableListDetailPaneScaffold` opcional para adaptar teléfono y pantalla grande.

## Límites de la evidencia

Las capturas y la lectura estática no permiten confirmar TalkBack, orden de foco, teclado, estados de error, persistencia al rotar ni la respuesta real del servidor. No se cambió lógica, payload, Supabase ni servidor.
