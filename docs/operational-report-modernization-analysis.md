# Análisis técnico y UX de la sección Reporte

## Alcance

Esta revisión corresponde a `OperationalReportActivity`, la sección **Reporte**
de lotería normal. No incluye Cuadre, deporte ni el reporte separado de servicios
y videojuegos.

La revisión se realizó sobre el código actual. No se modificaron cálculos,
permisos, payloads, endpoints, sincronización ni interfaz. Tampoco se hizo una
auditoría visual completa porque no se capturó la pantalla en un dispositivo
durante esta fase.

## Lo que ya está bien

- Existe una fuente local capaz de construir el reporte sin depender inicialmente
  de una respuesta de red.
- Los rangos Hoy, Semana, Quincena, Mes y Manual ya están conectados al mismo
  cálculo financiero.
- El rango manual conserva y ordena correctamente las fechas desde/hasta.
- La clave de frescura incluye período, rango y operador.
- El filtro por cajero, supervisor, admin o grupo conserva su alcance.
- La pantalla tiene densidad específica para POS.
- El resumen distingue venta, recarga, comisión, premios, caja y beneficio.
- Si falla el servidor existe una ruta de respaldo con la copia local.

## Hallazgos de carga y velocidad

### 1. La copia local se calcula, pero no se muestra inmediatamente

Al abrir Reporte, `refreshReport()` ejecuta `reportCacheProbe()`, pero usa ese
resultado únicamente para decidir si debe sincronizar. `reportState` no se publica
hasta terminar el resto del trabajo.

**Efecto:** aunque los datos ya estén en el teléfono, la primera entrada puede
parecer lenta.

**Ajuste seguro:** construir una sola vez el `OperationalReportViewState` local y
publicarlo de inmediato. Después, actualizar discretamente desde el servidor.

### 2. El endpoint de reporte se consulta aun cuando la política indica usar caché

`resolveFinanceRemoteRefreshDecision()` puede devolver
`shouldRefreshRemote = false`, pero `RemoteOperationalReportRepository.getReport()`
se ejecuta igualmente.

**Efecto:** entrar, cambiar operador o cambiar período puede hacer una llamada de
red aunque la copia por fecha siga vigente.

**Ajuste seguro:** consultar el endpoint solamente cuando:

- el usuario pulsa Actualizar;
- no existe información local para ese alcance;
- la entrada de hoy está vencida;
- un evento real marca ese alcance como desactualizado.

Los reportes históricos con copia válida deben abrir desde local y evitar una
consulta automática.

### 3. Una actualización puede reconstruir el reporte local varias veces

La ruta actual puede ejecutar:

1. `reportCacheProbe()` antes de sincronizar;
2. otro `reportCacheProbe()` después de sincronizar;
3. `buildOperationalReportViewState()` nuevamente si falla el endpoint.

**Efecto:** se repiten lecturas y agregaciones para la misma fecha y operador.

**Ajuste seguro:** conservar una única instantánea local por solicitud y
recalcularla solo si la sincronización realmente escribió datos nuevos.

### 4. Cambiar filtros puede lanzar trabajos que compiten

Cada período u operador llama inmediatamente a `refreshReport()` mediante un
`thread` nuevo. No existe identificador de solicitud ni cancelación. Si el usuario
cambia rápido de Hoy a Semana o selecciona otro cajero, una respuesta anterior
puede terminar después y reemplazar el reporte más reciente.

**Efecto:** lentitud aparente y riesgo de mostrar momentáneamente el rango u
operador anterior.

**Ajuste seguro:** capturar período, fechas y operador al iniciar la solicitud, y
aceptar el resultado solamente si sigue siendo la solicitud activa. La evolución
recomendada es un `ViewModel` con corrutinas y `StateFlow`, usando cancelación de
la carga anterior.

### 5. El refresco de Reporte incluye tareas que no pertenecen a su ruta crítica

Cuando decide sincronizar, la pantalla espera usuarios, límites de cajero,
tickets y recargas antes de terminar. Los límites no forman parte del cálculo
visible del reporte.

**Efecto:** una operación auxiliar lenta retrasa todo el reporte.

**Ajuste seguro:** mantener tickets y recargas como dependencias del dato, pero
retirar la descarga de límites de la ruta que bloquea la presentación. La lista
de usuarios debe hidratarse una vez o solo cuando esté desactualizada.

## Hallazgos de diseño

### 1. El selector está mejor organizado, pero todavía aplica demasiado pronto

La pantalla usa una tarjeta compacta que abre una hoja de filtros. Sin embargo,
elegir un período inicia la carga mientras la hoja sigue abierta. Si luego se
elige un operador, se inicia otra carga.

**Propuesta:** usar estado temporal dentro de la hoja y un único botón
**Aplicar filtros**. Solo esa confirmación cambia período, fechas y operador.

### 2. El contexto de fecha puede ser más claro

El encabezado muestra `dayKey`, que siempre representa hoy, aun cuando el reporte
visible sea de otra semana, mes o rango manual. La tarjeta sí muestra el período,
pero ambos textos pueden parecer contradictorios.

**Propuesta:** en el encabezado mostrar la banca y el rango activo; dejar “Hoy”
solo cuando el reporte realmente sea del día actual.

### 3. Actualizar servidor aparece dos veces

Existe una acción de actualizar en la barra superior y otra debajo del resumen,
además de una tarjeta de estado.

**Propuesta:** conservar una sola acción de actualización en la barra superior y
una línea compacta de estado/frescura. Compartir puede permanecer como acción
secundaria junto al contenido.

### 4. El resumen debe ser el protagonista

El orden recomendado en teléfono y POS es:

1. encabezado compacto;
2. período y operador activos;
3. beneficio/caja y métricas;
4. desglose de cajeros o tendencia;
5. acciones secundarias.

La carga local debe conservar el resumen visible mientras se actualiza, sin
reemplazarlo por una pantalla vacía.

### 5. Calendario

El calendario manual actual funciona, pero es una implementación propia. Material
3 ofrece `DateRangePicker`, que ya contempla selección desde/hasta y semántica de
accesibilidad.

**Propuesta segura:** verificar primero la versión Material 3 ya instalada. Si es
compatible, sustituir solamente la interfaz del calendario por un
`DateRangePicker` modal y convertir su resultado a los mismos `fromDayState` y
`toDayState`. El cálculo y el payload no cambian.

## Diseño objetivo

```text
Reporte                                      ↻
Banca · rango activo

Período y operador
Semana · 20/07/2026–26/07/2026
Todos los cajeros                         Cambiar

Beneficio                         Caja
$ …                               $ …
Venta      Comisión      Premios
$ …        $ …           $ …

Actualizado hace … / Mostrando copia local

Desglose por cajero o tendencia del período
```

En POS se mantiene una sola columna y métricas densas. En pantallas amplias puede
usarse un panel de apoyo para el desglose, pero no es necesario agregar una
dependencia adaptativa para corregir la velocidad.

## Plan seguro por fases

### Fase 1 — Velocidad sin cambiar resultados

- Capturar una solicitud inmutable: período, fechas y operador.
- Publicar primero el reporte local.
- Evitar el endpoint cuando la caché válida no necesita actualización.
- Evitar reconstrucciones locales repetidas.
- Impedir que una respuesta vieja sustituya la selección actual.

### Fase 2 — Filtros y fecha

- Mantener período, operador y rango como borrador dentro de la hoja.
- Aplicar los tres con una sola acción.
- Mostrar el rango activo en el encabezado.
- Mantener exactamente los mismos valores enviados al servidor.

### Fase 3 — Jerarquía Material 3

- Eliminar la acción duplicada de actualización.
- Reducir el estado de sincronización a una línea compacta.
- Colocar resumen y desglose antes de las acciones secundarias.
- Evaluar `DateRangePicker` después de confirmar compatibilidad.

### Fase 4 — Validación

- Contratos Node para una sola solicitud por aplicación de filtro.
- Pruebas unitarias para caché vigente, caché vacía, actualización manual,
  red lenta y respuestas fuera de orden.
- Comparar local y servidor para Hoy, histórico y rango manual.
- Validar admin, supervisor y cajero sin cambiar sus alcances.
- Ejecutar `testDebug` únicamente cuando el usuario lo solicite.

## Documentación oficial usada

- Android, arquitectura offline-first:
  https://developer.android.com/topic/architecture/data-layer/offline-first
- Android, capa de datos y fuente única de verdad:
  https://developer.android.com/topic/architecture/data-layer
- Android, selectores de fecha y `DateRangePicker`:
  https://developer.android.com/develop/ui/compose/components/datepickers
- Android, estado y `ViewModel` en Compose:
  https://developer.android.com/codelabs/jetpack-compose-state
- Android, patrón adaptativo lista-detalle:
  https://developer.android.com/develop/adaptive-apps/guides/list-detail

## Implementación aplicada

La primera entrega aplica las fases seguras sin cambiar contratos financieros:

- El reporte publica primero la copia local y consulta el endpoint remoto solo
  cuando la política de vigencia lo requiere, no existe información local o el
  usuario solicita una actualización manual.
- Cada carga captura período, rango y operador en una solicitud inmutable. Una
  respuesta anterior ya no puede sustituir una selección más reciente.
- Período, fecha y operador se editan como borrador y se aplican juntos con una
  sola acción.
- El encabezado muestra el rango realmente seleccionado.
- Se conserva una única acción de actualización y una única acción de compartir.
- El estado de actualización ocupa una línea compacta y mantiene el reporte
  visible.
- El desglose por cajero usa una sola superficie con filas y divisores, dejando
  las métricas como contenido principal.
- La sincronización de límites de cajero salió de la ruta crítica del reporte.

No se modificaron fórmulas, payloads, migraciones, permisos ni código del
servidor. La fórmula existente de caja disponible permanece bajo contrato.

### Validación aplicada

- 19 contratos Node superados.
- Contratos específicos para caché vigente, caché vacía, actualización manual,
  respuesta fuera de orden, aplicación unificada de filtros y conservación de
  la fórmula financiera.
- Revisión estática de delimitadores Kotlin y del diff.
- No se ejecutó Gradle ni `testDebug`, respetando la indicación del proyecto.
