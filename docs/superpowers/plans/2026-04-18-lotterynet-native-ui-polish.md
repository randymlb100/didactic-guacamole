# LotteryNet Native UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the native LotteryNet Android UI into a compact, sales-first POS app with reliable navigation, smaller controls, tighter typography, and a coherent color system.

**Architecture:** Keep the current native activity structure, but push visual and navigation decisions into shared Compose chrome so improvements propagate across sale, tickets, results, recargas, finance, and menu screens. Treat `NativeChrome.kt` as the shared design system boundary and keep `SalesActivity` as the primary home for cashier/admin roles.

**Tech Stack:** Kotlin, Android Activities, Jetpack Compose Material 3, shared Compose chrome in `ui/common`, LotteryNet native repositories and session storage.

---

### Task 1: Audit And Stabilize Shared Navigation

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- Test: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Test: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Test: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`

- [ ] **Step 1: Document the current broken navigation map**

Record these flows before editing:

```text
LoginActivity -> ShellActivity
ShellActivity -> SalesActivity / ResultsActivity / RecargasActivity / FinanceActivity / Admin screens
Top app bars -> ShellActivity
BottomNavBar -> openBottomTab(...)
```

Expected findings:

```text
- cashier/admin do not land on the sales screen first
- menu handlers are inconsistent
- BottomNavBar and top-bar menu do not share one route helper
```

- [ ] **Step 2: Make sales the default home for cashier/admin**

Update `LoginActivity` and active-session restore behavior so:

```kotlin
private fun homeIntentFor(role: UserRole): Intent = when (role) {
    UserRole.MASTER -> Intent(this, ShellActivity::class.java)
    UserRole.ADMIN, UserRole.CASHIER -> Intent(this, SalesActivity::class.java)
    UserRole.UNKNOWN -> Intent(this, ShellActivity::class.java)
}
```

- [ ] **Step 3: Add one shared helper for opening the menu explicitly**

In `NativeChrome.kt`, centralize menu launch logic:

```kotlin
fun openShellMenu(context: Context) {
    context.startActivity(
        Intent(context, ShellActivity::class.java).apply {
            putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
        },
    )
}
```

- [ ] **Step 4: Update all menu buttons to use the shared helper**

Replace direct `Intent(context, ShellActivity::class.java)` launches in:

```text
SalesActivity
ResultsActivity
RecargasActivity
FinanceActivity
FinanceReportsActivity
TicketSummaryActivity
TicketLookupActivity
TicketDetailActivity
```

With:

```kotlin
onOpenMenu = { openShellMenu(context) }
```

- [ ] **Step 5: Make `ShellActivity` redirect only when it was not opened intentionally**

Add:

```kotlin
companion object {
    const val EXTRA_FORCE_MENU = "force_menu"
}
```

Then guard the redirect:

```kotlin
if (session.role != UserRole.MASTER && intent?.getBooleanExtra(EXTRA_FORCE_MENU, false) != true) {
    startActivity(Intent(this, SalesActivity::class.java))
    finish()
    return
}
```

- [ ] **Step 6: Build and manually verify the navigation loop**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\Downloads\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64'
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected:

```text
BUILD SUCCESSFUL
```

Manual checks:

```text
- login opens Sales for cashier/admin
- bottom tab "Mas/Menu" opens ShellActivity
- top-left menu buttons open ShellActivity without bouncing back out
```

---

### Task 2: Rebuild The Shared Visual System

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/theme/Theme.kt`

- [ ] **Step 1: Replace the washed-out gray/blue background system**

Rework `LotteryNetStatusColors` in `NativeChrome.kt` around LotteryNet’s product palette:

```kotlin
colors = LotteryNetStatusColors(
    background = Color(0xFFF7F2E8),
    gain = Color(0xFF0F9F66),
    loss = Color(0xFFB91C1C),
    warning = Color(0xFFEA580C),
    neutral = Color(0xFF6B7280),
    ink = Color(0xFF0F172A),
    muted = Color(0xFF475569),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFF0E7D8),
    border = Color(0xFFD9CBB8),
)
```

- [ ] **Step 2: Align Compose theme surfaces with the shared chrome**

Review `Theme.kt` and confirm app-wide `background`, `surface`, `surfaceVariant`, and `outline` do not fight `NativeChrome.kt`.

Use this target:

```kotlin
background = CanvasSand
surface = Paper
surfaceVariant = SkySurface // only if still useful after NativeChrome cleanup
outline = Line
```

If `SkySurface` still reads too cold or gray, replace it with a warmer light surface token.

- [ ] **Step 3: Remove oversized chrome defaults**

In `NativeChrome.kt`, tighten:

```kotlin
panelRadius
screenPaddingH
screenPaddingV
panelContentGap
```

Success criteria:

```text
- less dead space
- no oversized rounded blocks
- UI reads like a compact POS, not a tablet dashboard
```

- [ ] **Step 4: Rebuild status accents so they scan faster**

Keep semantic accent usage constrained:

```text
green = success and active sale flow
orange = warnings and sorteo timing
red = blocking states
navy/ink = default content
```

Avoid decorative accent colors in routine cards and containers.

- [ ] **Step 5: Verify the shared chrome visually**

Manual visual checklist:

```text
- backgrounds are warm and clean, not gray or gloomy
- card borders are subtle but readable
- text contrast is strong
- spacing feels compact on 5.5" screens
```

---

### Task 3: Compact The Bottom Navigation For POS

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`

- [ ] **Step 1: Increase touchable height and add safe-area padding**

Update `LotteryNetSizeProfile` values:

```kotlin
POS_TIGHT: bottomNavHeight = 58.dp, bottomNavIcon = 18.dp, bottomNavLabel = 9.sp
POS: bottomNavHeight = 62.dp, bottomNavIcon = 20.dp, bottomNavLabel = 10.sp
TABLET/WIDE: bottomNavHeight = 64.dp, bottomNavIcon = 20.dp, bottomNavLabel = 10.sp
```

Wrap the nav surface with:

```kotlin
modifier = modifier
    .fillMaxWidth()
    .navigationBarsPadding()
```

- [ ] **Step 2: Tighten the inner layout without shrinking usability**

Adjust:

```kotlin
.padding(horizontal = 4.dp, vertical = 4.dp)
```

And enlarge icon hit boxes:

```kotlin
UserRole.CASHIER -> if (selected) 36.dp else 34.dp
UserRole.ADMIN -> if (selected) 36.dp else 34.dp
else -> 34.dp
```

- [ ] **Step 3: Revisit labels**

Review current labels:

```text
Venta
Lista/Tickets
Caja/Panel
Sorteos
Mas/Menu
```

Prefer the compact set that fits narrow screens with minimal wrapping. If any label truncates, shorten it rather than shrinking typography further.

- [ ] **Step 4: Manual device check**

Manual checks:

```text
- bottom nav is no longer hidden behind system gesture area
- labels stay one line
- each tab is easy to hit one-handed
- no accidental overlap with keyboard or content
```

---

### Task 4: Redesign The Login Screen For Compact POS Use

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`

- [ ] **Step 1: Remove decorative weight and long copy**

Keep only:

```text
brand row
short title
one supporting sentence
credentials card
enter button
secondary legacy/web button
```

Remove:

```text
marketing-style footer copy
non-essential status badge
duplicate headings
oversized spacing
```

- [ ] **Step 2: Tighten spacing and scale**

Use these targets:

```kotlin
panel padding = 12.dp / 14.dp / 16.dp by density
title uses titleLarge, not headlineSmall
supporting text uses bodySmall
vertical arrangement centers the layout
```

- [ ] **Step 3: Keep bootstrap status useful but secondary**

Bootstrap feedback remains, but should not dominate the screen. Preserve:

```kotlin
AnimatedVisibility(visible = !status.isNullOrBlank())
```

Do not allow it to become a large permanent block when the form is idle.

- [ ] **Step 4: Verify on narrow screens**

Manual checks:

```text
- login fits without feeling like a long list
- enter button remains in easy reach
- no oversized logo, heading, or footer
- the screen reads as a POS login, not a landing page
```

---

### Task 5: Normalize Screen Density Across Core Flows

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/recharge/RecargasActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/finance/FinanceActivity.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt`

- [ ] **Step 1: Review top-level section order per screen**

For each screen, ensure the first visible sections support operation immediately:

```text
Sales: lotteries -> entry -> staged list -> keypad
Results: current draws -> latest resolved entries
Recargas: provider -> amount -> recent actions
Finance: current numbers -> key actions -> detailed lists
Tickets: filters -> metrics -> list
```

- [ ] **Step 2: Remove card overload where plain layout is enough**

If a section is only spacing and labels, flatten it. Keep `CompactPanel` only where it improves grouping or touch behavior.

- [ ] **Step 3: Reduce oversized text**

Audit all uses of:

```text
headline*
titleLarge in repeated rows
large badges
```

Replace with tighter tokens where the content is operational rather than promotional.

- [ ] **Step 4: Make icon-first action rows where practical**

For repeated action surfaces, prefer:

```text
small icon
short title
one compact description
```

Avoid oversized text-heavy blocks when the action can be scanned faster visually.

- [ ] **Step 5: Manual walkthrough**

Walk through:

```text
login
sales
results
recargas
finance
tickets
menu
```

Check:

```text
- no screen feels oversized or sparse
- no giant text or giant buttons without a reason
- hierarchy is compact and consistent
```

---

### Task 6: Build, Device QA, And Regression Pass

**Files:**
- Test: `app/build/outputs/apk/debug/lotterynet-kotlin-v1.0.2-kotlin-debug.apk`

- [ ] **Step 1: Resolve local Gradle lock before building**

Current known issue:

```text
Timeout waiting to lock journal cache
Owner PID: 6240
```

Fix by closing the stale Gradle/IDE process or stopping daemons:

```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\Downloads\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64'
.\gradlew.bat --stop
```

- [ ] **Step 2: Build fresh debug APK**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Randy Cordero\Downloads\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64\java-21-openjdk-21.0.4.0.7-1.win.jdk.x86_64'
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Install and verify on the target phone**

Manual QA checklist:

```text
- app opens to sale screen
- menu opens intentionally and does not bounce away
- bottom nav is touchable
- no button exits unexpectedly
- primary screens look compact, warm, and readable
```

- [ ] **Step 4: Capture before/after screenshots**

Take screenshots for:

```text
login
sales
menu
results
```

Use them as the visual acceptance gate before any further polish work.

