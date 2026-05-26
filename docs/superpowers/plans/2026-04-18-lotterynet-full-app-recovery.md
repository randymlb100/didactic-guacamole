# LotteryNet Full App Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reparar la app completa para que `master`, `admin` y `cashier` entren sin cierres, tengan navegación funcional y usen una arquitectura coherente entre nativo y legado.

**Architecture:** La recuperación se hará en dos capas. Primero se estabiliza el enrutamiento y las pantallas de entrada para que ningún rol quede atrapado en un crash loop. Luego se reorganizan las superficies de navegación y se define una sola estrategia por pantalla: nativa estable o legado WebView, sin rebotes ambiguos entre ambas.

**Tech Stack:** Android Activities, Jetpack Compose, WebView assets (`index.html`), SharedPreferences repos, Native crash reporter, LotteryNet product rules, Figma-guided information architecture.

---

## File Map

- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/diagnostics/NativeCrashReporter.kt`
- Modify: `app/src/main/assets/index.html`
- Create: `docs/superpowers/specs/2026-04-18-lotterynet-recovery-design.md`
- Create: `docs/superpowers/plans/2026-04-18-lotterynet-full-app-recovery.md`

### Task 1: Freeze Crash Loops And Restore Safe Entry

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/diagnostics/NativeCrashReporter.kt`

- [ ] **Step 1: Normalize post-login role routing**

Define one safe resolver:
- `MASTER` -> menu/shell safe mode
- `ADMIN` -> safe operational home
- `CASHIER` -> safe operational home
- if the last crash came from a target screen, route to shell/menu instead of reopening that screen immediately

- [ ] **Step 2: Add a persistent crash quarantine flag**

Store:
- last crashing activity
- timestamp
- whether the app should bypass direct reopen

Expected behavior:
- if `SalesActivity` crashed, next login does not auto-open `SalesActivity`
- if `ResultsActivity` crashed, next open from menu falls back to a safe route

- [ ] **Step 3: Make `ShellActivity` the recovery hub**

Rules:
- `MASTER` must always be able to land in shell
- `ADMIN` and `CASHIER` can be redirected to shell when there is a crash quarantine
- `ShellActivity` must never bounce immediately to a crashing screen

- [ ] **Step 4: Verify build**

Run:
```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\Downloads\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64'
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected:
- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt app/src/main/java/com/lotterynet/pro/core/diagnostics/NativeCrashReporter.kt
git commit -m "fix: prevent native crash loops on role entry"
```

### Task 2: Rebuild Navigation Ownership

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`

- [ ] **Step 1: Define a single navigation matrix**

Document and encode:
- which screens are native
- which screens are legacy WebView
- which screens can fall back from native to web

Initial recommended matrix:
- `Venta` -> legacy-safe until native is proven stable
- `Resultados` -> legacy-safe until native is proven stable
- `Master menu` -> native shell
- `Legacy console` -> explicit web mode only

- [ ] **Step 2: Remove ambiguous target routing**

Eliminate patterns where:
- shell button says native but opens web indirectly
- web target says web but redirects back into native
- bottom nav destination depends on hidden side effects

- [ ] **Step 3: Introduce explicit navigation helpers**

Add helpers like:
- `openSafeSales(context, role)`
- `openSafeResults(context, role)`
- `openMasterHome(context)`
- `openLegacyConsole(context, target, forceWeb)`

All buttons and tabs must call helpers, not ad-hoc `Intent(...)`.

- [ ] **Step 4: Verify role-by-role navigation**

Manual matrix:
- `MASTER`: all primary buttons open real destinations
- `ADMIN`: menu, venta, resultados, caja, recargas work
- `CASHIER`: menu, venta, resultados, caja work

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/main/java/com/lotterynet/pro/MainActivity.kt
git commit -m "refactor: centralize safe app navigation"
```

### Task 3: Repair Master Experience

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`

- [ ] **Step 1: Audit all master buttons**

Check every master action:
- panel master
- crear banca
- soporte
- abrir legado
- logout

Each must either:
- open a working destination
- be temporarily disabled with clear copy
- or route to legacy safely

- [ ] **Step 2: Reorganize master information architecture**

Target sections:
- `Operación master`
- `Bancas`
- `Monitoreo`
- `Compatibilidad`
- `Sesión`

Remove vague labels like:
- `Soporte` without clear action meaning
- dead cards with no destination

- [ ] **Step 3: Add safe empty states**

Every master screen must show one of:
- list content
- setup CTA
- explicit “sin bancas todavía”
- explicit “modo legado requerido”

No screen should look blank or broken.

- [ ] **Step 4: Verify master flow**

Manual checks:
- login as `MASTER`
- enter shell
- open master panel
- open create bank
- return to menu
- open legacy mode deliberately

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/main/java/com/lotterynet/pro/ui/master/MasterDashboardActivity.kt app/src/main/java/com/lotterynet/pro/ui/master/MasterCreateBankActivity.kt app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt
git commit -m "fix: restore master navigation and layout structure"
```

### Task 4: Reorganize The Full Shell And App Layout

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/assets/index.html`
- Create: `docs/superpowers/specs/2026-04-18-lotterynet-recovery-design.md`

- [ ] **Step 1: Define one app map**

Sections:
- `Venta`
- `Tickets`
- `Resultados`
- `Caja`
- `Recargas`
- `Usuarios`
- `Impresora`
- `Master`
- `Modo web`

Every entry must have:
- role visibility
- destination type
- fallback behavior

- [ ] **Step 2: Simplify shell presentation**

Use compact grouped actions instead of mixed cards with unclear status.
Shell must answer:
- where am I
- what can I do
- what is primary
- what is legacy fallback

- [ ] **Step 3: Rename confusing entries**

Examples:
- `Abrir legado` -> `Modo web`
- `Consola completa` -> `Panel web`
- `Soporte` -> remove or replace with real technical action

- [ ] **Step 4: Prepare Figma-aligned structure**

Use Figma as layout reference for a later pass:
- compact mobile-first master menu
- compact cashier/admin launcher
- clear separation between primary actions and compatibility tools

This task does not require writing to Figma yet; it prepares the screen map and grouping model.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt app/src/main/assets/index.html docs/superpowers/specs/2026-04-18-lotterynet-recovery-design.md
git commit -m "design: reorganize shell and app information architecture"
```

### Task 5: Validate Recovery On Device

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/MainActivity.kt`

- [ ] **Step 1: Validate safe launch behavior**

Scenarios:
- fresh login as `MASTER`
- fresh login as `ADMIN`
- fresh login as `CASHIER`
- open shell
- open sale
- open results
- reopen app after background/foreground

- [ ] **Step 2: Validate crash fallback**

Expected:
- app does not get trapped in an auto-crash reopen loop
- login remains accessible
- shell remains reachable
- legacy-safe screens remain usable

- [ ] **Step 3: Validate old broken buttons**

Explicitly test:
- buttons in master menu
- bottom nav buttons
- open menu buttons
- `Venta`
- `Resultados`
- `Consola completa` / `Modo web`

- [ ] **Step 4: Build release candidate debug**

Run:
```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\Downloads\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64'
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected:
- `BUILD SUCCESSFUL`
- updated APK at `app/build/outputs/apk/debug/lotterynet-kotlin-v1.0.2-kotlin-debug.apk`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt app/src/main/java/com/lotterynet/pro/MainActivity.kt
git commit -m "test: stabilize full app recovery flows"
```

## Notes

- `MASTER` is currently a priority-1 recovery path because it is supposed to be the fallback admin surface and is also broken.
- `Venta` and `Resultados` should remain on legacy-safe routing until native stability is proven by repeated device testing.
- The next visual phase should use Figma to formalize the shell/menu hierarchy after functional recovery is complete.
