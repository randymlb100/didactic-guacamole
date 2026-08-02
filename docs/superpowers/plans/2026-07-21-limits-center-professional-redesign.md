# Centro de límites profesional — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar la sección Límites como un centro administrativo profesional, separando claramente pool, cajeros, admin, cobro/recargas y POS sin cambiar las reglas, payloads ni el flujo de venta existente.

**Architecture:** Mantener `AdminLimitsActivity` como coordinador de carga/guardado y separar la UI en una pantalla resumen más subpantallas de edición. Mantener `CashierSalesLimitInputs`, `AdminOperationalLimits`, `RechargeLimitSettings`, `CashierLimitCloudSyncCoordinator` y `SaleExposureEngine` como contratos de datos y reglas sin cambios. La UI mostrará el alcance efectivo, validará entradas localmente y guardará cada bloque con el mismo payload actual.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Window Size Classes existentes, repositorios locales actuales, Supabase remote store existente, JUnit de contratos y pruebas Node.js estáticas cuando sea necesario.

## Global Constraints

- No cambiar la fórmula de exposición de `SaleExposureEngine`.
- No cambiar claves remotas, nombres de payload ni precedencia de límites.
- No agregar endpoints, migraciones ni cambios de servidor.
- Pool sigue siendo exposición compartida por lotería + número + tipo de jugada.
- Límite de cajero sigue siendo por usuario y por día/tipo de jugada.
- Límite propio del admin no hereda límites base de cajeros.
- Cobro y recargas siguen separados de ventas.
- Modo POS cambia presentación, no reglas ni valores.
- `0` o campo vacío conserva el significado actual de “sin tope” en cada bloque.
- No ejecutar `gradlew` durante la implementación sin autorización explícita; usar diff, pruebas Node y revisión estática. El usuario ejecutará `testDebug` al final.
- No reemplazar el pool actual por límites configurables por lotería sin una decisión independiente de modelo de datos.

## Investigación y decisiones de diseño

Android recomienda que las configuraciones extensas tengan una vista general, grupos relacionados y subpantallas; también recomienda que la etiqueta que abre un grupo coincida con el título de la subpantalla. [Android Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings).

Los patrones comparados de Shopify POS, Toast POS y Stripe muestran una separación consistente entre alcance, permisos, resumen y edición:

- Shopify separa permisos por contexto de tienda y POS: [Shopify POS permissions](https://help.shopify.com/en/manual/your-account/users/roles/permissions/pos-permissions).
- Toast organiza capacidades por roles de trabajo y evita que un usuario administre más permisos de los que posee: [Toast permissions](https://support.toasttab.com/en/article/Access-Permissions-Reference).
- Stripe separa balance, transacciones y roles del equipo: [Stripe Dashboard](https://docs.stripe.com/dashboard/basics), [Stripe roles](https://docs.stripe.com/get-started/account/teams/roles).

### Resultado visual objetivo

```text
Centro de límites                         ⋮
Admin · Banca actual                 Sincronizado

Resumen de reglas activas
Pool       Cajeros       Admin       Caja       POS

Pool de banca                              >
Compartido por lotería, número y jugada
10 reglas activas · Último cambio hoy

Límites de cajeros                         >
Venta diaria y topes por jugada
Base: $10,000 · 4 cajeros con configuración propia

Límite propio del admin                    >
Solo aplica cuando el admin vende

Cobros y recargas                           >
Pagos de premios y fondos

Modo POS                                   >
Solo cambia la interfaz
```

En teléfono/POS se muestra una subpantalla por bloque. En tablet/wide se puede usar lista-detalle: categorías a la izquierda y editor a la derecha. No se agregará una dependencia adaptativa nueva sin revisar el catálogo Gradle.

## Mapa de archivos

- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt` — coordinación de pantalla, carga inicial, guardado remoto existente y navegación entre resumen/editor.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsContracts.kt` — contratos de sección, copy, alcance y estado efectivo.
- Create: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsUiModels.kt` — modelos puros para tarjetas, categorías de jugadas, estado y errores de edición.
- Create: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsComponents.kt` — componentes Compose de resumen, filas, editor de dinero, grupos y estados.
- Modify: `app/src/main/java/com/lotterynet/pro/core/storage/LocalCashierSalesLimitRepository.kt` — solo si se necesita exponer una función pura de precedencia/estado; no cambiar serialización.
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt` — contratos de alcance, copy, `0`, precedencia y navegación.
- Create: `tools/qa/admin-limits-professional-ux-contract.node.test.mjs` — verificación estática de separación visual y preservación de contratos.
- Modify: `docs/limits-section-critical-analysis.md` — marcar decisiones implementadas y conservar la explicación del modelo actual.

## Modelo de información

### Categorías visibles

```kotlin
enum class AdminLimitsDestination {
    POOL,
    CASHIERS,
    ADMIN_SELF,
    CASH_AND_RECHARGES,
    POS,
}
```

Cada categoría debe tener:

```kotlin
data class AdminLimitsOverviewItem(
    val destination: AdminLimitsDestination,
    val title: String,
    val summary: String,
    val effectiveValue: String,
    val tone: AdminLimitsTone,
)
```

### Reglas de copy

- Pool: “Compartido por banca. Se evalúa por lotería, número y jugada.”
- Cajero: “Límite del usuario. Se evalúa por día y tipo de jugada.”
- Admin: “Solo ventas hechas por la cuenta admin.”
- Cobro: “Tope de pago por cajero; no limita la venta.”
- Recarga: “Tope por operación; no altera el pool.”
- POS: “Solo compacta la interfaz; no cambia límites.”
- Valor cero: “Sin tope configurado”, nunca mostrar campo vacío sin explicación.

## Fases de implementación

### Task 1: Congelar contratos y documentar el alcance

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsContracts.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`
- Modify: `docs/limits-section-critical-analysis.md`

**Interfaces:**
- Produce `AdminLimitsDestination`, `AdminLimitsOverviewItem` y funciones puras de copy/estado.
- Consume los valores actuales de `CashierSalesLimitInputs`, `AdminOperationalLimits` y `RechargeLimitSettings`.

- [ ] **Step 1: Escribir contratos fallidos** para comprobar que existen las cinco categorías y que sus descripciones separan pool, cajero, admin, caja y POS.

```kotlin
@Test
fun `overview exposes five independent limit scopes`() {
    assertEquals(
        listOf("POOL", "CASHIERS", "ADMIN_SELF", "CASH_AND_RECHARGES", "POS"),
        adminLimitsOverviewItems().map { it.destination.name },
    )
}
```

- [ ] **Step 2: Implementar contratos puros** sin tocar payloads ni repositorios.
- [ ] **Step 3: Añadir pruebas de `0`**: cada resumen debe mostrar “Sin tope configurado”.
- [ ] **Step 4: Revisar el diff** con `git diff --check`; no ejecutar Gradle.

### Task 2: Crear el modelo de estado de edición

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsUiModels.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

**Interfaces:**
- Produce `AdminLimitsEditorState`, `AdminLimitsFieldState`, `AdminLimitsValidationError` y `AdminLimitsDestination`.
- No produce llamadas de red ni escribe preferencias.

```kotlin
internal data class AdminLimitsFieldState(
    val raw: String,
    val value: Double?,
    val error: String? = null,
)

internal data class AdminLimitsEditorState(
    val destination: AdminLimitsDestination,
    val fields: Map<String, AdminLimitsFieldState>,
    val dirty: Boolean,
    val saving: Boolean,
    val message: String? = null,
)
```

- [ ] **Step 1: Probar entradas válidas**: `0`, entero, decimal y vacío.
- [ ] **Step 2: Probar entradas inválidas**: texto, doble punto, monto negativo y valor no finito.
- [ ] **Step 3: Implementar sanitización pura** reutilizando la regla actual `sanitizeLimit` y agregando error visible para valores no válidos.
- [ ] **Step 4: Probar que editar un bloque no cambia los demás**.

### Task 3: Implementar el centro resumen

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Create/Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsComponents.kt`

**Interfaces:**
- Consume `adminLimitsOverviewItems()`.
- Produce `AdminLimitsOverview` con callback `onOpen(destination: AdminLimitsDestination)`.

- [ ] **Step 1: Reemplazar la navegación basada en cuatro botones** por una lista de cinco `ListItem` o filas equivalentes.
- [ ] **Step 2: Mostrar en cada fila título, supporting text, valor efectivo, estado y chevron.
- [ ] **Step 3: Mantener el encabezado actual de regreso, banca y rol, pero eliminar el resumen repetido de campos.
- [ ] **Step 4: Mantener POS compacto usando la misma columna; en tablet permitir ancho máximo para evitar estirar campos.
- [ ] **Step 5: Verificar por diff que no se removieron callbacks `onSave`, `onSavePosMode` ni `hydrateAdminLimitsFromServer`.

### Task 4: Separar el editor de Pool

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsComponents.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

**Scope:** solo presentación; el pool conserva los mismos ocho valores y el mismo payload.

- [ ] **Step 1: Crear encabezado de alcance:** “Compartido por banca; cada valor se evalúa por lotería + número + jugada”.
- [ ] **Step 2: Agrupar campos en `Quiniela`, `Pale`, `Super Pale`, `Tripleta`, `Pick 3` y `Pick 4`.
- [ ] **Step 3: Mostrar “Sin tope” cuando el valor sea cero.
- [ ] **Step 4: Mostrar advertencia antes de guardar un valor que reduzca el límite actual.
- [ ] **Step 5: Guardar mediante `pushPoolLimitsServiceFirst` con el mismo `CashierSalesLimitInputs` y probar round-trip del payload.

### Task 5: Separar editor de Cajeros y editor del Admin

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsContracts.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsComponents.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

- [ ] **Step 1: Mostrar selector de alcance:** “Todos los cajeros”, “Cajero específico” y “Mi cuenta admin”.
- [ ] **Step 2: En Cajeros mostrar venta diaria, cobro y límites por jugada con copy de usuario.
- [ ] **Step 3: En Admin mostrar únicamente `adminSelf`, indicando que no hereda defaults de cajeros.
- [ ] **Step 4: Mantener la precedencia actual: límite específico de cajero sobre default; pago específico sobre fallback global.
- [ ] **Step 5: Probar el ejemplo de negocio:** pool 10.000, cajero A 2.000, cajero B 10.000; ningún editor debe presentar esos valores como si fueran la misma regla.

### Task 6: Separar Cobros/Recargas y Modo POS

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsComponents.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

- [ ] **Step 1: Crear editor de Cobros** con `cashierPayoutLimit` y texto “no limita ventas”.
- [ ] **Step 2: Crear editor de Recargas** con `globalPerTx` y `masterPerTx` claramente separados.
- [ ] **Step 3: Mantener guardado de ambos valores en las claves remotas existentes.
- [ ] **Step 4: Cambiar Modo POS a una fila con `Switch`/estado y confirmación protegida; no mostrarlo como límite monetario.
- [ ] **Step 5: Probar que activar POS no cambia ningún valor de venta, pool, cobro o recarga.

### Task 7: Corregir el guardado por bloque sin cambiar payloads

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/admin/AdminLimitsActivity.kt`
- Test: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`

- [ ] **Step 1: Mantener una única función de ensamblaje del payload final para compatibilidad.
- [ ] **Step 2: Hacer que cada editor muestre “Guardar cambios” únicamente para su bloque.
- [ ] **Step 3: Al guardar, enviar todos los valores actuales internamente para no borrar campos de otros bloques, pero comunicar al usuario el bloque editado.
- [ ] **Step 4: Mostrar estados `Guardando`, `Guardado en servidor`, `Guardado local` y `No se pudo guardar`.
- [ ] **Step 5: No cerrar la pantalla si el servidor falla; conservar los valores editados y permitir reintento explícito.

### Task 8: Pruebas de contrato y validación estática

**Files:**
- Modify: `app/src/test/java/com/lotterynet/pro/ui/admin/AdminLimitsContractsTest.kt`
- Create: `tools/qa/admin-limits-professional-ux-contract.node.test.mjs`
- Modify: `docs/limits-section-critical-analysis.md`

- [ ] **Step 1: Probar categorías y copy de alcance.
- [ ] **Step 2: Probar que pool, cajero, admin y caja no comparten etiquetas ambiguas.
- [ ] **Step 3: Probar serialización/deserialización sin pérdida de `q`, `pale`, `sp`, `t`, `p3`, `p3box`, `p4`, `p4box`.
- [ ] **Step 4: Probar precedencia de límites específicos y fallback.
- [ ] **Step 5: Probar `0` y vacío.
- [ ] **Step 6: Probar que no se agregan endpoints, migraciones ni llamadas nuevas.
- [ ] **Step 7: Ejecutar únicamente validaciones permitidas:

```powershell
git diff --check
node --test tools/qa/admin-limits-professional-ux-contract.node.test.mjs
```

Resultado esperado: diff sin errores y todos los contratos UX en verde. La compilación `testDebug` queda como paso manual posterior del usuario.

### Task 9: Smoke test funcional controlado

**Files:**
- Test: `tools/qa/admin-controls-real-flow-smoke.mjs` — reutilizar el flujo existente, sin cambiar datos de producción.
- Test: `tools/qa/cashier-pool-multi-smoke.mjs` — reutilizar el escenario de pool por lotería.

- [ ] **Step 1: Leer configuración actual sin modificarla.
- [ ] **Step 2: Verificar pool por una lotería y número.
- [ ] **Step 3: Verificar cajero A con límite 2.000 y cajero B con límite 10.000.
- [ ] **Step 4: Verificar que el pool compartido reduce el restante para ambos cajeros.
- [ ] **Step 5: Verificar cobro y recarga como reglas separadas.
- [ ] **Step 6: Restaurar cualquier dato temporal y emitir reporte de lectura.

Este smoke test no debe activar cambios de producción ni modificar el servidor; solo puede ejecutarse si el entorno QA está autorizado.

## Criterios de aceptación

1. Un administrador identifica en menos de una pantalla qué controla cada categoría.
2. Pool, cajero, admin, cobro/recarga y POS no aparecen mezclados.
3. La pantalla explica que el pool se aplica por lotería + número + jugada.
4. “Sin tope” es visible y consistente en todos los campos.
5. Editar una categoría no borra visualmente ni remotamente otra.
6. Guardar un bloque conserva los mismos payloads y claves remotas.
7. El modo POS no altera ninguna regla monetaria.
8. Los valores específicos de cajero conservan precedencia sobre defaults.
9. No se agregan llamadas de red ni polling.
10. Las pruebas estáticas y de contrato pasan antes de que el usuario ejecute `testDebug`.

## Riesgos y decisiones fuera de este plan

- Configurar un pool diferente por lotería requeriría ampliar el modelo remoto; no se incluye.
- Mostrar “vendido/restante” por número requiere datos de exposición y una pantalla de monitor; se puede añadir como lectura posterior, no como cambio del motor.
- Cambiar la contraseña fija del modo POS no forma parte de este rediseño.
- Añadir permisos granulares por rol para editar límites sería un plan separado de autorización.

## Handoff

Plan completo guardado en `docs/superpowers/plans/2026-07-21-limits-center-professional-redesign.md`. La implementación debe ejecutarse por tareas, con revisión de diff entre cada fase y sin desplegar servidor.
