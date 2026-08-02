# Operational Report Unified Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer que Reporte muestre inmediatamente los datos correctos del período seleccionado, evite consultas repetidas y tenga una jerarquía visual Material 3 más clara sin cambiar resultados financieros.

**Architecture:** La fuente local seguirá construyendo exactamente el mismo `OperationalReportViewState`. La pantalla publicará esa copia primero y solo después ejecutará una actualización condicionada por la política de frescura. La selección de período, rango y operador se confirmará como una sola solicitud inmutable; un identificador de generación impedirá que una respuesta antigua sustituya la selección vigente.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose Material 3 mediante BOM 2025.09.01, repositorios locales existentes, Edge Functions existentes, JUnit 4 y contratos Node.js.

## Global Constraints

- No modificar fórmulas de venta, recarga, comisión, premios, caja, beneficio ni comisión de supervisor.
- No modificar payloads, nombres de Edge Functions, permisos, roles ni alcance de admin/supervisor/cajero.
- No modificar Cuadre, deporte, servicios, videojuegos, lotería Pick ni venta.
- No crear migraciones ni desplegar servidor.
- No agregar polling ni nuevas llamadas periódicas.
- Mantener Hoy, Semana, Quincena, Mes y rango Manual.
- Mantener modo POS y los tamaños de ventana actuales.
- Conservar la ruta offline y la actualización manual.
- No ejecutar Gradle ni `testDebug` hasta que el usuario lo solicite explícitamente.
- No confirmar cambios de rendimiento sin contratos Node y revisión del diff.

---

## File Map

- Create: `app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicy.kt`
  - Tipos puros para representar una solicitud y decidir cuándo consultar el endpoint.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
  - Publicación local inmediata, coordinación de solicitudes, filtros confirmados y jerarquía visual.
- Create: `app/src/test/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicyContractsTest.kt`
  - Contratos para caché, actualización manual y respuestas fuera de orden.
- Modify: `app/src/test/java/com/lotterynet/pro/ui/report/OperationalReportContractsTest.kt`
  - Contratos de fecha, selección única, roles y presentación.
- Create: `tools/qa/operational-report-local-first-contract.node.test.mjs`
  - Garantía estática de que no reaparezcan llamadas incondicionales o controles duplicados.
- Modify: `docs/operational-report-modernization-analysis.md`
  - Resultado final y evidencia de validación.

---

### Task 1: Proteger la identidad de cada solicitud y la política de red

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicy.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicyContractsTest.kt`
- Create: `tools/qa/operational-report-local-first-contract.node.test.mjs`

**Interfaces:**
- Consumes: `FinancePeriodPreset`, `OperationalReportActorFilter`, `FinanceRemoteRefreshDecision`.
- Produces:
  - `OperationalReportRequestSnapshot`
  - `shouldFetchOperationalReportEndpoint(...)`
  - `isOperationalReportRequestCurrent(...)`

- [ ] **Step 1: Escribir contratos de política antes de la implementación**

```kotlin
class OperationalReportLoadPolicyContractsTest {
    @Test
    fun `fresh historical local report does not call endpoint`() {
        assertFalse(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = false,
                    initialMessage = "Datos locales listos",
                ),
                hasLocalReport = true,
            ),
        )
    }

    @Test
    fun `empty local report calls endpoint`() {
        assertTrue(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = false,
                    initialMessage = "Datos locales listos",
                ),
                hasLocalReport = false,
            ),
        )
    }

    @Test
    fun `manual refresh calls endpoint even with cache`() {
        assertTrue(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = true,
                    initialMessage = "Cargando desde servidor...",
                ),
                hasLocalReport = true,
            ),
        )
    }

    @Test
    fun `older response cannot replace active report request`() {
        assertFalse(isOperationalReportRequestCurrent(activeRequestId = 8L, completedRequestId = 7L))
        assertTrue(isOperationalReportRequestCurrent(activeRequestId = 8L, completedRequestId = 8L))
    }
}
```

- [ ] **Step 2: Crear y ejecutar el contrato Node equivalente**

```javascript
import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const policyPath =
  "app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicy.kt";

test("report endpoint policy preserves cache and manual refresh rules", async () => {
  const source = await readFile(policyPath, "utf8");
  assert.match(source, /refreshDecision\.shouldRefreshRemote \|\| !hasLocalReport/);
  assert.match(source, /activeRequestId == completedRequestId/);
  assert.doesNotMatch(
    source,
    /ventas|recargas|comision|premios|cajaDisponible|resolveOperationalReportNet/,
  );
});
```

Run:

```powershell
node --test tools/qa/operational-report-local-first-contract.node.test.mjs
```

Expected: FAIL porque la política todavía no existe.

- [ ] **Step 3: Crear las funciones puras**

```kotlin
package com.lotterynet.pro.core.finance

data class OperationalReportRequestSnapshot(
    val requestId: Long,
    val preset: FinancePeriodPreset,
    val fromDayKey: String?,
    val toDayKey: String?,
    val selectedFilter: OperationalReportActorFilter,
    val forceRemote: Boolean,
)

fun shouldFetchOperationalReportEndpoint(
    refreshDecision: FinanceRemoteRefreshDecision,
    hasLocalReport: Boolean,
): Boolean = refreshDecision.shouldRefreshRemote || !hasLocalReport

fun isOperationalReportRequestCurrent(
    activeRequestId: Long,
    completedRequestId: Long,
): Boolean = activeRequestId == completedRequestId
```

- [ ] **Step 4: Ejecutar el contrato Node y confirmar la implementación**

Run:

```powershell
node --test tools/qa/operational-report-local-first-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Revisar que la política no contiene cálculos financieros**

Run:

```powershell
rg -n "ventas|recargas|comision|premios|cajaDisponible|resolveOperationalReportNet" app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicy.kt
```

Expected: ninguna coincidencia.

- [ ] **Step 6: Guardar el punto de revisión**

No crear commit ni incluir archivos ajenos. Registrar en el plan que Task 1 quedó lista para revisión.

---

### Task 2: Mostrar la copia local primero y actualizar solo cuando corresponde

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `tools/qa/operational-report-local-first-contract.node.test.mjs`
- Test: `app/src/test/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicyContractsTest.kt`

**Interfaces:**
- Consumes: `OperationalReportRequestSnapshot`, `shouldFetchOperationalReportEndpoint`, `isOperationalReportRequestCurrent`.
- Produces: una ejecución local-first de `refreshReport(forceRemote)` que conserva la última selección.

- [ ] **Step 1: Crear el contrato estático de seguridad**

```javascript
import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const activityPath =
  "app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt";

test("Reporte publishes local state before optional remote work", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(source, /val localReport = buildOperationalReportViewState/);
  assert.match(source, /reportState = localReport/);
  assert.match(source, /shouldFetchOperationalReportEndpoint/);
  assert.match(source, /isOperationalReportRequestCurrent/);
});

test("Reporte does not block on cashier limits", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.doesNotMatch(source, /CashierLimitCloudSyncCoordinator/);
});

test("Remote report endpoint is conditional", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(
    source,
    /if \(shouldFetchOperationalReportEndpoint\([\s\S]*?RemoteOperationalReportRepository/,
  );
});
```

- [ ] **Step 2: Capturar la selección antes de iniciar el trabajo**

Agregar a la actividad:

```kotlin
private val reportRequestGeneration = AtomicLong(0L)
```

Al inicio de `refreshReport()`:

```kotlin
val requestId = reportRequestGeneration.incrementAndGet()
val selectedPeriod = selectedPeriodState
val selectedFromDay = fromDayState
val selectedToDay = toDayState
val selectedFilter = selectedFilterState
```

Construir `OperationalReportRequestSnapshot` con esos valores y no volver a leer
los estados mutables dentro de ese trabajo.

- [ ] **Step 3: Publicar una sola construcción local inmediatamente**

Dentro del hilo:

```kotlin
val localReport = buildOperationalReportViewState(
    repository = financeRepository,
    session = session,
    preset = request.preset,
    anchorDayKey = dayKey,
    fromDayKey = request.fromDayKey,
    toDayKey = request.toDayKey,
    filter = safeFilter,
    syncStatus = OperationalReportSyncStatus.CACHED_COPY,
)
```

Publicar `localReport` con `runOnUiThread` solo si
`isOperationalReportRequestCurrent(reportRequestGeneration.get(), requestId)`.
Mantener `loadingState = true` únicamente como indicador discreto mientras exista
trabajo remoto.

- [ ] **Step 4: Eliminar reconstrucciones locales repetidas**

Sustituir los dos usos de `reportCacheProbe()` por:

```kotlin
val localSummary = localReport.summary
```

Después de una sincronización que realmente se ejecutó, construir
`refreshedLocalReport` una sola vez y usar tanto su `summary` como su contenido.
Eliminar `reportCacheProbe()` cuando ya no tenga consumidores.

- [ ] **Step 5: Sacar límites de la ruta crítica**

Eliminar de `OperationalReportActivity.kt`:

```kotlin
CashierLimitCloudSyncCoordinator(...).pullOwner(ownerKey)
```

No modificar `CashierLimitCloudSyncCoordinator` ni el flujo de Límites. Reporte
seguirá sincronizando usuarios, tickets y recargas cuando la política lo requiera.

- [ ] **Step 6: Condicionar el endpoint**

```kotlin
val remoteReport = if (
    shouldFetchOperationalReportEndpoint(
        refreshDecision = decision,
        hasLocalReport = reportSummaryHasData(localReport.summary),
    )
) {
    runCatching {
        remoteReportRepository.getReport(
            session = session,
            filter = safeFilter,
            preset = request.preset,
            range = range,
        )
    }.getOrNull()
} else {
    null
}
```

El reporte final será `remoteReport ?: refreshedLocalReport ?: localReport`.

- [ ] **Step 7: Rechazar respuestas fuera de orden**

Antes de cada escritura final de `reportState`, `messageState` o `loadingState`:

```kotlin
if (!isOperationalReportRequestCurrent(reportRequestGeneration.get(), requestId)) {
    return@runOnUiThread
}
```

- [ ] **Step 8: Ejecutar contratos Node**

Run:

```powershell
node --test tools/qa/operational-report-local-first-contract.node.test.mjs tools/qa/recharge-report-stability-contract.node.test.mjs tools/qa/finance-report-recharges-contract.node.test.mjs tools/qa/finance-report-cashier-alias-contract.node.test.mjs
```

Expected: todas las pruebas pasan; el contrato de recargas confirma que Reporte
continúa usando `NativeRechargeCloudSyncCoordinator`.

---

### Task 3: Aplicar período, fechas y operador como una sola selección

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/report/OperationalReportContractsTest.kt`
- Modify: `tools/qa/operational-report-local-first-contract.node.test.mjs`

**Interfaces:**
- Consumes: `OperationalReportPeriod`, `OperationalReportManualTarget`, `OperationalReportActorFilter`.
- Produces:
  - `OperationalReportFilterSelection`
  - una única callback `onApplyFilters(selection)`

- [ ] **Step 1: Añadir el contrato de selección**

```kotlin
internal data class OperationalReportFilterSelection(
    val period: OperationalReportPeriod,
    val fromDayKey: String,
    val toDayKey: String,
    val actorFilter: OperationalReportActorFilter,
)
```

Agregar prueba:

```kotlin
@Test
fun `report filter selection keeps period range and actor together`() {
    val selection = OperationalReportFilterSelection(
        period = OperationalReportPeriod.MANUAL,
        fromDayKey = "2026-07-01",
        toDayKey = "2026-07-25",
        actorFilter = OperationalReportActorFilter.Cashier("cashier-1", "Cajero 1"),
    )

    assertEquals(OperationalReportPeriod.MANUAL, selection.period)
    assertEquals("2026-07-01", selection.fromDayKey)
    assertEquals("2026-07-25", selection.toDayKey)
    assertEquals("cashier:cashier-1", selection.actorFilter.key)
}
```

- [ ] **Step 2: Convertir el contenido de la hoja en borrador**

En `OperationalReportControls`, al abrir la hoja, copiar los valores activos:

```kotlin
var draftPeriod by remember { mutableStateOf(selectedPeriod) }
var draftFromDay by remember { mutableStateOf(fromDay) }
var draftToDay by remember { mutableStateOf(toDay) }
var draftActorFilter by remember { mutableStateOf(selectedFilter) }
```

Los botones de período, calendario y operador solo actualizan estas variables.
No llaman a `refreshReport()`.

- [ ] **Step 3: Añadir una sola acción Aplicar filtros**

Al final de `OperationalReportFilterSheet`:

```kotlin
CompactActionButton(
    label = "Aplicar filtros",
    onClick = {
        onApplyFilters(
            OperationalReportFilterSelection(
                period = draftPeriod,
                fromDayKey = draftFromDay,
                toDayKey = draftToDay,
                actorFilter = draftActorFilter,
            ),
        )
    },
    modifier = Modifier.fillMaxWidth(),
    tone = ActionTone.Primary,
)
```

La hoja se cierra después de esa callback.

- [ ] **Step 4: Aplicar el estado y cargar una sola vez**

En `OperationalReportActivity.onCreate()`:

```kotlin
onApplyFilters = { selection ->
    selectedPeriodState = selection.period
    fromDayState = selection.fromDayKey
    toDayState = selection.toDayKey
    selectedFilterState = selection.actorFilter
    refreshReport(forceRemote = false)
}
```

Eliminar las callbacks que refrescan individualmente al tocar período u operador.

- [ ] **Step 5: Conservar el calendario actual**

Mantener `ManualReportRangePicker`, `updateOperationalReportManualRange`,
`calendarCells` y el formato ISO `yyyy-MM-dd`. No reemplazar esta lógica en esta
fase; solo se mueve a estado de borrador.

- [ ] **Step 6: Extender el contrato Node**

```javascript
test("Reporte applies period date and actor once", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(source, /label = "Aplicar filtros"/);
  assert.match(source, /onApplyFilters = \{ selection ->/);
  assert.doesNotMatch(
    source,
    /onPeriodSelected = \{ period ->[\s\S]*?refreshReport\(forceRemote = false\)/,
  );
  assert.doesNotMatch(
    source,
    /onFilterSelected = \{ filter ->[\s\S]*?refreshReport\(forceRemote = false\)/,
  );
});
```

- [ ] **Step 7: Ejecutar contratos Node**

Run:

```powershell
node --test tools/qa/operational-report-local-first-contract.node.test.mjs
```

Expected: PASS.

---

### Task 4: Mejoras visuales pequeñas y unificadas

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/report/OperationalReportContractsTest.kt`
- Modify: `tools/qa/operational-report-local-first-contract.node.test.mjs`

**Interfaces:**
- Consumes: `OperationalReportViewState`, `OperationalReportLayoutContract`, componentes comunes existentes.
- Produces:
  - `operationalReportHeaderSubtitle(...)`
  - `OperationalReportStatusRow`
  - `OperationalReportActorList`

- [ ] **Step 1: Definir el texto de contexto sin fecha contradictoria**

```kotlin
internal fun operationalReportHeaderSubtitle(
    bancaName: String,
    report: OperationalReportViewState?,
    fallbackDayKey: String,
): String {
    val range = report?.periodLabel?.takeIf { it.isNotBlank() } ?: fallbackDayKey
    return "$bancaName · $range"
}
```

Prueba:

```kotlin
@Test
fun `historical report header shows selected range instead of today`() {
    val report = OperationalReportViewState(
        periodLabel = "01/07/2026–15/07/2026",
        filter = OperationalReportActorFilter.All,
        syncStatus = OperationalReportSyncStatus.CACHED_COPY,
        summary = FinanceSummary(),
        trend = emptyList(),
        actorRows = emptyList(),
    )

    assertEquals(
        "Banca Norte · 01/07/2026–15/07/2026",
        operationalReportHeaderSubtitle("Banca Norte", report, "2026-07-25"),
    )
}
```

- [ ] **Step 2: Mantener una sola acción Actualizar**

Conservar `ScreenChromeAction` en `AppTopBar`. Debajo del resumen, eliminar el
segundo botón `Actualizar servidor` y conservar únicamente:

```kotlin
CompactActionButton(
    label = "Compartir reporte",
    onClick = onShare,
    icon = Icons.Rounded.Share,
    modifier = Modifier.fillMaxWidth(),
    tone = ActionTone.Primary,
)
```

- [ ] **Step 3: Convertir el estado en una fila discreta**

Crear `OperationalReportStatusRow` con `Surface`, icono, mensaje y color de estado.
No usar otra `CompactPanel` grande. Mientras actualiza, conservar visibles el
resumen y el desglose anterior.

- [ ] **Step 4: Evitar una tarjeta por cada cajero**

Crear:

```kotlin
@Composable
private fun OperationalReportActorList(rows: List<FinanceActorPeriodRow>) {
    CompactPanel {
        rows.forEachIndexed { index, row ->
            OperationalReportActorContent(row)
            if (index < rows.lastIndex) {
                HorizontalDivider(color = rememberLotteryNetVisualSpec().colors.border)
            }
        }
    }
}
```

`OperationalReportActorContent` conserva alias, venta, premio y resultado. Eliminar
el divisor final y la `CompactPanel` individual de `OperationalReportActorRow`.

- [ ] **Step 5: Conservar el resumen actual**

No cambiar `buildOperationalReportMetricSpecs`, `resolveOperationalReportNet`,
`OperationalReportLedgerSummary` ni sus colores semánticos. Solo ajustar el orden:

1. encabezado;
2. período y operador;
3. resumen;
4. estado discreto;
5. desglose;
6. compartir.

- [ ] **Step 6: Mantener POS compacto**

No cambiar `resolveOperationalReportLayout`. Verificar por fuente que:

```kotlin
LotteryNetWindowMode.POS_TIGHT,
LotteryNetWindowMode.POS
```

continúan usando filas densas, resumen inline y `metricPaddingVerticalDp = 5`.

- [ ] **Step 7: Extender el contrato Node visual**

```javascript
test("Reporte keeps one refresh action and one actor surface", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.equal(
    source.match(/contentDescription = "Actualizar servidor"/g)?.length,
    1,
  );
  assert.doesNotMatch(source, /label = "Actualizar servidor"/);
  assert.match(source, /private fun OperationalReportStatusRow/);
  assert.match(source, /private fun OperationalReportActorList/);
});
```

---

### Task 5: Validación de invariantes y cierre

**Files:**
- Modify: `docs/operational-report-modernization-analysis.md`
- Review: all files listed in this plan.

**Interfaces:**
- Consumes: entregables de Tasks 1–4.
- Produces: evidencia de que el resultado financiero y los alcances permanecen intactos.

- [ ] **Step 1: Ejecutar toda la validación Node relacionada**

Run:

```powershell
node --test `
  tools/qa/operational-report-local-first-contract.node.test.mjs `
  tools/qa/recharge-report-stability-contract.node.test.mjs `
  tools/qa/finance-report-recharges-contract.node.test.mjs `
  tools/qa/finance-report-cashier-alias-contract.node.test.mjs `
  tools/qa/admin-monitor-card-actions-contract.node.test.mjs
```

Expected: todas las pruebas pasan.

- [ ] **Step 2: Buscar cambios prohibidos**

Run:

```powershell
git diff -- app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportModels.kt app/src/main/java/com/lotterynet/pro/core/finance/RemoteOperationalReportRepository.kt supabase/functions
```

Expected: sin cambios en fórmulas, payload remoto ni servidor.

- [ ] **Step 3: Verificar contratos visibles y rutas críticas**

Run:

```powershell
rg -n "Hoy|Semana|Quincena|Mes|Manual|Aplicar filtros|Compartir reporte|Actualizar servidor" app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt
rg -n "NativeOperationalSyncCoordinator|NativeRechargeCloudSyncCoordinator|RemoteOperationalReportRepository" app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt
```

Expected: todos los períodos permanecen; tickets, recargas y reporte remoto siguen
disponibles; existe una sola acción visible de actualización.

- [ ] **Step 4: Revisar formato y diff**

Run:

```powershell
git diff --check
git diff --stat
```

Expected: sin errores de espacios ni archivos inesperados del servidor.

- [ ] **Step 5: Actualizar la documentación**

Agregar a `docs/operational-report-modernization-analysis.md`:

- archivos modificados;
- contratos ejecutados;
- cantidad de pruebas aprobadas;
- confirmación de que no se modificaron fórmulas ni payloads;
- aclaración de que Gradle quedó pendiente por instrucción del usuario.

- [ ] **Step 6: Dejar `testDebug` como aprobación final**

No ejecutar automáticamente. Cuando el usuario lo solicite:

```powershell
.\gradlew.bat testDebug
```

Expected: BUILD SUCCESSFUL. Si falla, corregir únicamente errores relacionados con
los archivos de este plan y volver a validar los contratos Node.

---

## Acceptance Criteria

- El reporte local aparece antes de terminar cualquier llamada remota.
- Una caché histórica válida no consulta automáticamente el endpoint.
- Hoy se actualiza según la ventana de frescura existente.
- Actualizar servidor fuerza la consulta como antes.
- Período, fechas y operador generan una sola carga al pulsar Aplicar filtros.
- Una respuesta anterior no puede reemplazar el rango activo.
- Admin, supervisor y cajero conservan exactamente sus filtros.
- Venta, recarga, comisión, premios, caja y beneficio conservan las mismas fórmulas.
- La pantalla muestra el rango activo y una única acción Actualizar.
- El modo POS conserva su densidad actual.
- No existen cambios de servidor ni migraciones.
