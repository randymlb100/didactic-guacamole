# Modo POS compacto para SUNMI V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convertir la pantalla de Venta en una interfaz compacta y segura para SUNMI V2/V2 Pro únicamente cuando el botón de modo POS esté activado.

**Architecture:** Mantener una sola lógica de venta y seleccionar únicamente una capa visual POS mediante `LotteryNetWindowMode.POS_TIGHT`. El modo normal seguirá usando el layout actual. La integración de impresión continuará pasando por la abstracción de impresora existente, guardando el ticket antes de imprimir y evitando crear ventas duplicadas al reintentar.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `WindowSizeClass`/`BoxWithConstraints`, `WindowInsets`, repositorios locales existentes y la integración de impresora SUNMI ya presente en la app.

## Global Constraints

- La implementación solo se aplica cuando `posLiteEnabled`/modo POS está activo.
- Venta normal, límites, SuperPale, loterías, sincronización, payloads y permisos no cambian.
- No se agregan llamadas nuevas al servidor para compactar la pantalla.
- No se reduce el área táctil interactiva por debajo de 48 dp; se compacta el contenido visual y el espaciado.
- El ticket debe guardarse localmente antes de intentar imprimir.
- Reintentar una impresión no puede crear otra venta ni otro ticket.
- El ancho objetivo de impresión del SUNMI V2/V2 Pro es 58 mm; el V2 oficial usa pantalla 1440x720 y Android/SUNMI OS según variante.

---

### Task 1: Formalizar el contrato visual POS

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

**Interfaces:**
- Consumes: `LotteryNetWindowMode`, `liveSystemModeConfig.posLiteEnabled` y los contratos `Venta*LayoutContract` existentes.
- Produces: un contrato visual POS que defina encabezado mínimo, padding horizontal, altura de lista, densidad de teclado y visibilidad de estadísticas.

- [ ] **Step 1: Escribir pruebas de comportamiento visual**

Verificar que `resolveVentaPosLiteContract(..., posLiteEnabled = true)` siempre devuelve `POS_TIGHT`, que el modo normal conserva su ventana original y que el teclado mantiene controles táctiles de al menos 48 dp.

- [ ] **Step 2: Ejecutar solo los tests de Venta y confirmar el estado inicial**

Run: `./gradlew.bat testDebugUnitTest --tests com.lotterynet.pro.ui.sales.SalesUiContractsTest`

Expected: los contratos actuales pasan antes del ajuste.

- [ ] **Step 3: Implementar el contrato sin modificar ventas**

Centralizar en `SalesActivity.kt` los valores POS: encabezado sin subtítulo, padding lateral reducido, lista con prioridad de espacio, estadísticas auxiliares ocultas y teclado con separación cero. No cambiar validadores, drafts, límites ni callbacks.

- [ ] **Step 4: Ejecutar la prueba de contrato**

Run: `./gradlew.bat testDebugUnitTest --tests com.lotterynet.pro.ui.sales.SalesUiContractsTest`

Expected: PASS.

### Task 2: Layout de Venta adaptado a la pantalla SUNMI

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

**Interfaces:**
- Consumes: contrato POS de Task 1 y dimensiones disponibles de `BoxWithConstraints`.
- Produces: una composición POS con tres zonas: entrada, jugadas y acción final.

- [ ] **Step 1: Definir la composición POS**

En `SalesActivity`, cuando `saleModeContract.useTightSellingLayout` sea verdadero:

1. Encabezado mínimo.
2. Bloque de lotería, jugada, monto y límite.
3. Lista de jugadas desplazable.
4. Total y teclado/acciones al pie.

El modo normal debe seguir la rama actual.

- [ ] **Step 2: Compactar solo espaciado y texto auxiliar**

Reducir padding lateral y alturas vacías, ocultar subtítulo de banca/usuario en POS y dar más altura a la lista. No ocultar número, monto, límite, total, imprimir ni confirmar.

- [ ] **Step 3: Proteger áreas táctiles**

Mantener `IconButton`, `Button` o `sizeIn(minWidth = 48.dp, minHeight = 48.dp)` para controles interactivos. Los iconos pueden medir 24 dp, pero el contenedor táctil debe permanecer en 48 dp.

- [ ] **Step 4: Probar modo POS y modo normal**

Verificar en pruebas que POS y normal producen contratos diferentes solo en layout y que `resolveVentaPosLiteContract(..., false)` no activa compactación.

### Task 3: Teclado de venta para operación rápida

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

**Interfaces:**
- Consumes: `resolveVentaKeypadLayout`, `resolveVentaKeyRows` y los callbacks existentes `onApplyKey`, `onPickModeKey` y `onAddPlay`.
- Produces: teclado POS que conserva las mismas teclas y acciones.

- [ ] **Step 1: Mantener las mismas filas y comandos**

No renombrar ni eliminar `OK`, `PRINT`, borrar, SuperPale, Pick o selección de monto. Solo ajustar densidad visual y prioridad.

- [ ] **Step 2: Priorizar acciones**

En POS, mantener visible el total y el botón principal; colocar acciones secundarias en la zona ya existente sin crear una navegación nueva.

- [ ] **Step 3: Añadir pruebas de no-regresión**

Confirmar que el teclado mantiene 4 filas, la última fila tiene 3 teclas, existe un único `PRINT`, un único `OK` y que el callback de agregar jugada no cambia.

### Task 4: Impresión térmica SUNMI de 58 mm

**Files:**
- Inspect/modify only if necessary: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/core/render/`
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

**Interfaces:**
- Consumes: ticket guardado, `SaleThermalPrintTarget`, `resolveSaleThermalPrintResult` y renderer existente.
- Produces: presentación de impresión compacta para rollo de 58 mm sin cambiar el payload del ticket.

- [ ] **Step 1: Confirmar el ancho del renderer**

Verificar que el renderer seleccionado para SUNMI use el template térmico de 58 mm y no el template de WhatsApp o pantalla.

- [ ] **Step 2: Mantener estados de impresión**

Conservar estados `No hay impresora conectada`, `Imprimiendo`, éxito y error. Un error solo debe permitir reintentar; nunca volver a guardar la venta.

- [ ] **Step 3: Añadir prueba de idempotencia visual**

Verificar que reintentar una impresión reutiliza el mismo `ticket.id` y `createdAtEpochMs`.

### Task 5: Insets, orientación y teclado del dispositivo

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Inspect: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`

**Interfaces:**
- Consumes: `WindowInsets.safeDrawing`, IME insets y dimensiones reales del dispositivo.
- Produces: layout que no queda debajo de la barra del sistema ni del teclado virtual.

- [ ] **Step 1: Verificar edge-to-edge existente**

Conservar `WindowInsets.safeDrawing` y añadir solo padding IME donde el teclado pueda tapar el total o el botón principal.

- [ ] **Step 2: Verificar orientación vertical**

No forzar una orientación global de la aplicación. Validar que la pantalla POS se vea correctamente en la orientación configurada por SUNMI.

- [ ] **Step 3: Probar con teclado visible y oculto**

Comprobar foco en número, foco en monto, desplazamiento de la lista y visibilidad del total.

### Task 6: Validación en dispositivo SUNMI y no-regresión

**Files:**
- Test: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`
- Optional documentation: `docs/pos/sunmi-v2-validation.md`

- [ ] **Step 1: Ejecutar pruebas unitarias de Venta**

Run: `./gradlew.bat testDebugUnitTest --tests com.lotterynet.pro.ui.sales.SalesUiContractsTest`

Expected: PASS.

- [ ] **Step 2: Ejecutar la suite Debug completa**

Run: `./gradlew.bat testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Prueba manual con botón POS apagado**

Confirmar que Venta normal mantiene su diseño, navegación, límites, SuperPale, Pick, impresión y sincronización.

- [ ] **Step 4: Prueba manual con botón POS encendido**

En SUNMI V2/V2 Pro validar: venta de quiniela, pale, tripleta, Pick, SuperPale, límites por lotería, ticket guardado, impresión, reintento de impresión y señal lenta.

- [ ] **Step 5: Verificar que no aumentan llamadas**

Comparar logs antes/después: activar POS debe cambiar únicamente UI; no debe crear consultas nuevas a Auth, API Gateway, Realtime o Postgres.

## Referencias oficiales

- [SUNMI V2 oficial](https://file.cdn.sunmi.com/newebsite/downloads/specs/en/v2.pdf)
- [SUNMI V2 Pro oficial](https://www.sunmi.com/en/v2-pro/)
- [Compose window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
- [Compose accessibility and 48 dp targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Compose WindowInsets](https://developer.android.com/develop/ui/compose/system/insets)
