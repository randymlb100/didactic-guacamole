# Premium Master Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convertir el perfil Master en un centro administrativo premium, conectado y fácil de recorrer, conservando exactamente los contratos actuales de usuarios, fondos, credenciales, permisos, servidor y auditoría.

**Architecture:** `MasterDashboardActivity` seguirá siendo el punto de entrada y de composición de dependencias. La navegación visible, el estado de pantalla y las secciones se separarán en archivos Compose pequeños; los casos de uso y repositorios existentes seguirán siendo la fuente de verdad. Servicios y Videojuegos se integrarán como una sección del centro Master, manteniendo `MasterServicesGamesActivity` como envoltorio compatible durante la transición.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AppCompat, coroutines, repositorios locales existentes, Edge Functions/Supabase existentes, JUnit 4 y pruebas contractuales actuales.

## Global Constraints

- No cambiar tablas, Edge Functions, payloads, claves remotas ni reglas de negocio.
- No tocar la lógica de venta, lotería, Pick, deporte, recargas, premios ni reportes.
- No convertir silenciosamente un error remoto en éxito local.
- Mantener `NativeDestination.MASTER_SERVICES_GAMES` para compatibilidad y navegación segura.
- Mantener soporte para teléfono, POS compacto, tablet y pantalla ancha.
- Usar colores y tipografía de `MaterialTheme`; no introducir colores de acción arbitrarios.
- Acciones destructivas conservan confirmación explícita.
- El estado visible debe distinguir `guardando`, `confirmado`, `pendiente`, `offline` y `error`.
- Cada fase debe compilar y poder liberarse de forma independiente.

---

## File Map

### Files to create

- `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardModels.kt`
  - Destinos internos, estados de sincronización, resúmenes y acciones de UI.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardViewModel.kt`
  - Estado observable, carga de usuarios y coordinación de eventos de pantalla.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardScreen.kt`
  - Scaffold, encabezado, navegación interna y estados generales.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterOverviewSection.kt`
  - Resumen operativo y accesos prioritarios.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterBanksSection.kt`
  - Lista/búsqueda/filtros de bancas.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterBankDetailSection.kt`
  - Detalle de una banca, cajeros y acciones administrativas.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterModulesSection.kt`
  - Resumen y acceso a Servicios, Videojuegos y Deporte.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterModuleAccessSection.kt`
  - Editor reutilizable de permisos por admin y cajero.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterSystemSection.kt`
  - Recargas Master, conexión, servidor y sincronización agrupados.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterSecuritySection.kt`
  - Credenciales emitidas y cambios de contraseña.
- `app/src/test/java/com/lotterynet/pro/ui/master/MasterDashboardNavigationTest.kt`
  - Contratos de jerarquía, destinos, Back y accesibilidad de módulos.
- `app/src/test/java/com/lotterynet/pro/ui/master/MasterModuleAccessContractsTest.kt`
  - Contratos de permisos por admin/cajero y limpieza de selecciones.

### Files to modify

- `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
  - Reducir a composición de dependencias y conexión con el nuevo estado.
- `app/src/main/java/com/lotterynet/pro/ui/master/MasterServicesGamesActivity.kt`
  - Reutilizar `MasterModuleAccessSection` y refrescar usuarios al reanudar.
- `app/src/main/java/com/lotterynet/pro/ui/navigation/NavigationSafety.kt`
  - Conservar destinos externos y documentar el destino interno preferido.
- `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`
  - Actualizar el contrato de secciones y mantener pruebas actuales.
- `docs/services-games-integration.md`
  - Documentar el acceso desde el centro Master y el flujo de confirmación.

---

## Target Information Architecture

La navegación principal tendrá cinco áreas:

1. **Resumen**
   - Bancas activas, bloqueadas, cajeros y alertas.
   - Acciones prioritarias: crear banca, revisar alertas y módulos.
2. **Bancas**
   - Buscar y filtrar.
   - Seleccionar una banca.
   - Administrar cajeros, fondo, credenciales y estado dentro del detalle.
3. **Módulos**
   - Servicios, Videojuegos y Deporte.
   - Estado global, alcance y última confirmación.
   - Admin primero; luego solo aparecen sus cajeros.
4. **Sistema**
   - Recargas Master.
   - Cuenta default y cuentas por admin.
   - Estado del servidor, sincronización y snapshot remoto.
5. **Seguridad**
   - Claves, credenciales emitidas y acceso a auditoría.

En teléfono/POS se mostrará una sección a la vez. En tablet/ancho se usará lista-detalle: navegación o lista a la izquierda y contenido seleccionado a la derecha.

---

### Task 1: Freeze Current Business Contracts

**Files:**
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`
- Create: `app/src/test/java/com/lotterynet/pro/ui/master/MasterDashboardNavigationTest.kt`

**Interfaces:**
- Consumes: funciones públicas/internas actuales de `MasterDashboardActivity.kt`.
- Produces: contrato de navegación `MasterDestination` y lista canónica de secciones.

- [ ] **Step 1: Add the failing section hierarchy test**

```kotlin
@Test
fun `master center exposes five administrative areas`() {
    assertEquals(
        listOf("Resumen", "Bancas", "Módulos", "Sistema", "Seguridad"),
        masterPrimaryDestinations().map { it.label },
    )
}
```

- [ ] **Step 2: Add the failing Services/Games reachability test**

```kotlin
@Test
fun `master modules destination exposes services and games`() {
    assertEquals(
        setOf("services", "video_games", "sportsbook"),
        masterModuleEntries().map { it.id }.toSet(),
    )
}
```

- [ ] **Step 3: Run the focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterDashboardNavigationTest" --console=plain
```

Expected: FAIL because the new contracts do not exist.

- [ ] **Step 4: Record existing mutation tests as the regression baseline**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.master.*" --console=plain
```

Expected: all current Master business tests PASS before UI refactoring.

---

### Task 2: Introduce Typed Navigation and UI State

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardModels.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardViewModel.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterDashboardNavigationTest.kt`

**Interfaces:**
- Produces:
  - `enum class MasterDestination`
  - `data class MasterDashboardUiState`
  - `sealed interface MasterDashboardEvent`
  - `enum class MasterSyncState`

- [ ] **Step 1: Define the destination contract**

```kotlin
internal enum class MasterDestination(
    val id: String,
    val label: String,
) {
    OVERVIEW("overview", "Resumen"),
    BANKS("banks", "Bancas"),
    MODULES("modules", "Módulos"),
    SYSTEM("system", "Sistema"),
    SECURITY("security", "Seguridad"),
}

internal fun masterPrimaryDestinations(): List<MasterDestination> =
    MasterDestination.entries
```

- [ ] **Step 2: Define explicit synchronization state**

```kotlin
internal enum class MasterSyncState {
    IDLE,
    LOADING,
    CONFIRMED,
    PENDING,
    OFFLINE,
    ERROR,
}
```

- [ ] **Step 3: Define screen state without duplicating business entities**

```kotlin
internal data class MasterDashboardUiState(
    val destination: MasterDestination = MasterDestination.OVERVIEW,
    val admins: List<UserAccount> = emptyList(),
    val cashiers: List<UserAccount> = emptyList(),
    val selectedAdminId: String? = null,
    val searchQuery: String = "",
    val bankFilter: MasterBankFilter = MasterBankFilter.ALL,
    val syncState: MasterSyncState = MasterSyncState.IDLE,
    val statusMessage: String? = null,
    val lastConfirmedAtEpochMs: Long? = null,
)
```

- [ ] **Step 4: Move transient navigation and filtering state into the ViewModel**

The ViewModel must expose:

```kotlin
val uiState: StateFlow<MasterDashboardUiState>
fun selectDestination(destination: MasterDestination)
fun selectAdmin(adminId: String?)
fun updateSearch(query: String)
fun updateBankFilter(filter: MasterBankFilter)
fun refreshLocalUsers()
```

`refreshLocalUsers()` reads only `LocalUsersRepository`; it must not call the network.

- [ ] **Step 5: Run focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterDashboardNavigationTest" --console=plain
```

Expected: PASS.

---

### Task 3: Build the Premium Master Scaffold

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardScreen.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`

**Interfaces:**
- Consumes: `MasterDashboardUiState`, existing `LotteryNetWindowMode`.
- Produces: `MasterDashboardScreen(...)`.

- [ ] **Step 1: Preserve adaptive behavior in a named layout contract**

```kotlin
internal data class MasterCenterLayout(
    val showTwoPanes: Boolean,
    val compactNavigation: Boolean,
    val compactActions: Boolean,
)
```

Map POS and POS_TIGHT to one pane with compact actions. Map TABLET and WIDE to two panes.

- [ ] **Step 2: Replace the six-button segmented grid with five stable destinations**

Phone/POS:

- compact top app bar;
- horizontally scrollable primary destination row;
- one content surface at a time.

Tablet/Wide:

- persistent navigation list on the left;
- selected content on the right.

- [ ] **Step 3: Apply Material 3 hierarchy**

- Use `surface`, `surfaceContainerLow` and `surfaceContainer`.
- Use `ListItem` for navigation and ordinary rows.
- Use cards only for independently actionable objects such as one banca or one module.
- Keep every touch target at least `48.dp`.
- Use one primary action per section.
- Use red only for destructive actions and confirmed errors.

- [ ] **Step 4: Implement predictable Back behavior**

```kotlin
internal fun masterBackDestination(
    current: MasterDestination,
    hasSelectedAdmin: Boolean,
): MasterBackResult
```

Rules:

- From a selected banca detail: clear selection.
- From any primary section: return to Resumen.
- From Resumen: leave Master dashboard.

- [ ] **Step 5: Run UI contracts**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterUiContractsTest" --console=plain
```

Expected: PASS with the new five-area contract and all existing fund/credential assertions intact.

---

### Task 4: Make the Overview Useful and Honest

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterOverviewSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardScreen.kt`

**Interfaces:**
- Consumes: confirmed local/remote state already exposed to Master.
- Produces: summary cards and navigation actions only.

- [ ] **Step 1: Show four business metrics**

- Bancas activas.
- Bancas bloqueadas.
- Cajeros activos.
- Configuraciones que requieren atención.

- [ ] **Step 2: Replace generic “Sincronizado” with evidence-based status**

Display:

- `Confirmado · HH:mm` only after a confirmed remote operation.
- `Cambios pendientes` when local save exists without remote confirmation.
- `Sin conexión` when the latest request failed due to connectivity.
- `Error de servidor` for authenticated/server failures.

- [ ] **Step 3: Add only three priority actions**

- Crear banca.
- Administrar módulos.
- Revisar alertas/auditoría.

- [ ] **Step 4: Verify no summary action mutates data**

All overview actions navigate; none calls save, sync, password or fund callbacks directly.

---

### Task 5: Separate Bank List from Bank Detail

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterBanksSection.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterBankDetailSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`

**Interfaces:**
- Consumes: existing callbacks for block, delete, add cashier, passwords, credentials and recharge credentials.
- Produces: a selected-bank workflow without changing callback signatures.

- [ ] **Step 1: Keep the bank list focused**

The list shows:

- banca/admin display name;
- active/blocked badge;
- cashier count;
- fund/connection warning only when relevant;
- one action: `Administrar`.

- [ ] **Step 2: Move all bank mutations into the detail**

Group the detail into:

- Identidad y estado.
- Cajeros.
- Fondos y recargas.
- Credenciales.
- Zona de riesgo.

- [ ] **Step 3: Keep dialogs only for bounded actions**

Use a modal sheet for:

- agregar cajeros;
- cambiar una contraseña;
- asignar fondo.

Use `AlertDialog` for:

- bloquear/desbloquear;
- regenerar todas las credenciales;
- borrar banca.

- [ ] **Step 4: Keep server-first confirmation**

The UI must continue calling the existing callbacks from `MasterDashboardActivity`. It must update visible lists only from the returned `MasterDashboardMutation`.

- [ ] **Step 5: Verify dangerous actions remain visually separated**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterUiContractsTest.master dashboard separates dangerous actions" --console=plain
```

Expected: PASS.

---

### Task 6: Connect and Simplify Module Administration

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterModulesSection.kt`
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterModuleAccessSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterServicesGamesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Create: `app/src/test/java/com/lotterynet/pro/ui/master/MasterModuleAccessContractsTest.kt`

**Interfaces:**
- Consumes: `MasterServicesGamesSettings`, `servicesGamesRemoteKey()`, current remote store.
- Produces:
  - `MasterModuleEntry`
  - `MasterModuleAccessState`
  - `updateModuleAdminAccess(...)`
  - `updateModuleCashierAccess(...)`

- [ ] **Step 1: Add the missing visible entry point**

The Módulos section must expose:

- Servicios.
- Videojuegos.
- Deporte.

Each row shows active/inactive, number of admins, number of cajeros and last confirmation.

- [ ] **Step 2: Enforce admin-first selection**

The editor order is:

1. Seleccionar módulo.
2. Activar/desactivar globalmente.
3. Seleccionar admin.
4. Habilitar admin.
5. Mostrar únicamente los cajeros que pertenecen a ese admin.

- [ ] **Step 3: Remove stale cashier permission ambiguity**

```kotlin
internal fun disableAllCashiersForAdmin(
    settings: MasterServicesGamesSettings,
    adminKey: String,
    cashierKeysForAdmin: Set<String>,
): MasterServicesGamesSettings {
    return settings.copy(
        cashierAdminKeys = settings.cashierAdminKeys - adminKey,
        allowedCashierKeys = settings.allowedCashierKeys - cashierKeysForAdmin,
    )
}
```

Add tests proving cashiers from another admin are never removed.

- [ ] **Step 4: Refresh local users on screen resume**

Replace:

```kotlin
remember { users.getAdmins() }
remember { users.getCashiers() }
```

with ViewModel/state-holder refresh triggered by lifecycle `ON_RESUME` and explicit local repository change events. This refresh must not add polling.

- [ ] **Step 5: Preserve the compatibility Activity**

`MasterServicesGamesActivity` renders the same reusable module editor so old routes continue to work. The preferred path is the Módulos section inside Master.

- [ ] **Step 6: Preserve remote save semantics**

Save sequence remains:

1. Normalize.
2. Save local.
3. Upsert the existing remote key.
4. Mark confirmed only when remote upsert succeeds.

- [ ] **Step 7: Run module tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.MasterModuleAccessContractsTest" --console=plain
```

Expected: PASS.

---

### Task 7: Consolidate System, Recargas and Cloud Status

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterSystemSection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`

**Interfaces:**
- Consumes: existing callbacks for limits, Recargas Rápidas credentials, wallet, server probe, sync and snapshot.
- Produces: grouped system UI; no new backend calls.

- [ ] **Step 1: Divide System into three groups**

- Fondos y topes.
- Conexión Recargas Rápidas.
- Servidor y sincronización.

- [ ] **Step 2: Label technical actions by consequence**

- `Comprobar conexión`.
- `Enviar cambios pendientes`.
- `Descargar estado del servidor`.

Keep developer details collapsed under `Detalles técnicos`.

- [ ] **Step 3: Prevent concurrent duplicate actions**

Disable only the action currently running. Keep existing request governors and coalescing; do not add timers or polling.

- [ ] **Step 4: Preserve exact fund language**

Display assigned, remaining and consumed separately. Never label assigned fund as current available balance.

---

### Task 8: Consolidate Security and Audit Entry

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/ui/master/MasterSecuritySection.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`

**Interfaces:**
- Consumes: existing password and credential callbacks.
- Produces: security section and credential-result sheet.

- [ ] **Step 1: Separate single-user and group operations**

- Cambiar clave individual.
- Cambiar clave de todos los cajeros de una banca.
- Regenerar credenciales completas.

- [ ] **Step 2: Show generated credentials as a temporary result**

Use a modal sheet with:

- affected user count;
- copy/share;
- explicit close;
- warning that old passwords no longer work.

- [ ] **Step 3: Keep audit as a destination, not a duplicate panel**

The Security section links to `AdminAuditActivity`. The Overview may show an alert count, but must not duplicate the full history.

---

### Task 9: Accessibility, POS and Visual Polish

**Files:**
- Modify: all new Master Compose section files.
- Modify: `app/src/test/java/com/lotterynet/pro/ui/master/MasterUiContractsTest.kt`

**Interfaces:**
- Consumes: `LotteryNetWindowMode`, `MaterialTheme`.
- Produces: consistent presentation across supported devices.

- [ ] **Step 1: Apply semantic action labels**

Every icon-only control receives a content description. Abbreviated POS labels retain a full semantic description.

- [ ] **Step 2: Enforce touch and text constraints**

- Minimum touch size: `48.dp`.
- Body typography: Material theme body styles.
- No information conveyed only by color.
- Long banca and user names use ellipsis plus detail view.

- [ ] **Step 3: Validate compact layouts**

Add contract tests for:

- POS one-pane navigation.
- POS short action labels.
- Wide list-detail mode.
- Credential rows that do not clip.

- [ ] **Step 4: Check dark and light theme colors**

Use theme roles for primary, warning and danger actions. Do not hard-code white text unless it comes from the corresponding `on*` color.

---

### Task 10: Controlled Verification and Release Gate

**Files:**
- Modify: `docs/services-games-integration.md`
- Create: `docs/master-profile-premium-qa.md`

**Interfaces:**
- Consumes: completed UI and unchanged business callbacks.
- Produces: release evidence.

- [ ] **Step 1: Run focused unit tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.master.*" --console=plain
```

Expected: PASS.

- [ ] **Step 2: Run navigation safety tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.navigation.NavigationSafetyContractsTest" --console=plain
```

Expected: PASS, including `MASTER_SERVICES_GAMES`.

- [ ] **Step 3: Run the complete Debug build**

```powershell
.\gradlew.bat assembleDebug --console=plain --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run non-monetary QA smoke**

Using Master and `podero02` QA data:

- open Master center;
- enter every primary area;
- select an admin and verify only its cashiers appear;
- open Servicios and Videojuegos without confirming a paid operation;
- toggle a QA permission, save, reopen and verify confirmed state;
- restore the QA permission to its original value;
- verify Master, admin and cashier visibility;
- verify no lottery sale, sports bet, recharge or service payment is submitted.

- [ ] **Step 5: Run mutation smoke only against QA**

- create one temporary QA cashier;
- verify it appears without restarting;
- change its password;
- verify the server confirms the change;
- delete the temporary QA cashier;
- verify audit entries and final user list.

- [ ] **Step 6: Inspect request behavior**

Confirm from logs:

- one remote configuration read per module entry/loading cycle;
- one write per explicit save;
- no periodic Master polling;
- no duplicate Auth, PostgREST or Edge request caused by recomposition;
- lifecycle resume refreshes local state and uses existing request governors for remote state.

- [ ] **Step 7: Document release evidence**

Record:

- test commands and results;
- APK version;
- QA users used;
- timestamps;
- remote keys changed;
- confirmation that production monetary flows were not exercised.

---

## Recommended Delivery Order

1. Tasks 1–3: navigation and scaffold, no business mutation changes.
2. Tasks 4–5: overview and banca list/detail.
3. Task 6: connect modules and correct stale permission presentation.
4. Tasks 7–8: system and security consolidation.
5. Tasks 9–10: accessibility, POS, tests and QA evidence.

Each group is independently reviewable and can be stopped without leaving server changes pending.

## Acceptance Criteria

- Master reaches Servicios, Videojuegos and Deporte from a visible Módulos area.
- The user never needs to guess whether a row is navigation, a toggle or a destructive action.
- Selecting an admin shows only that admin and its cajeros.
- A newly created admin/cajero appears without closing the app.
- Local save and remote confirmation are visibly distinct.
- Existing funds, passwords, user creation and module payloads remain unchanged.
- No new polling or duplicated backend calls are introduced.
- Phone/POS uses one pane; tablet/wide uses list-detail.
- All Master and navigation contract tests pass.
- `assembleDebug` finishes successfully.
