# Pick Fast Keypad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compact Pick-mode keypad that matches the video workflow: `-` for Straight, `+` for Box, `/` for ligar, and `*` for Straight+Box, while keeping the sale screen readable on small POS devices.

**Architecture:** Keep the change inside the existing sale screen instead of adding a separate Pick screen. Extend the current Pick helpers so the app accepts both existing `S/B` suffixes and fast POS symbols, then compact the Pick keypad with a clear hierarchy: numbers first, Pick commands second, confirm/print last.

**Tech Stack:** Android Kotlin, Jetpack Compose, existing LotteryNet sale contracts, JUnit unit tests, Gradle, Playwright/browser visual verification where available for rendered UI review.

---

## File Structure

- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
  - Update Pick keypad rows.
  - Add Pick symbol parsing helpers.
  - Add compact visual labels for `-`, `+`, `/`, `*`.
  - Wire `/` to existing ligar behavior.
  - Wire `*` to a new Straight+Box shortcut contract.
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt`
  - Adjust quick action labels so Pick mode shows practical actions, not duplicate controls.
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`
  - Cover keypad row order, symbol parsing, and compatibility with existing `S/B`.
- Optional Modify after implementation review: `app/src/main/java/com/lotterynet/pro/core/sales/SaleValidator.kt`
  - Only if Straight+Box needs to resolve as two plays from one entry. If not, keep validation unchanged and make `*` a UI shortcut that stages two normal rows.

---

## Visual Direction

The Pick keypad should look close to the video but cleaner:

```text
7   8   9   ⌫
4   5   6   -
1   2   3   +
0   .   /   *
OK      PRINT
```

Hierarchy:

- Number keys stay white.
- `-` Straight and `+` Box are blue Pick-mode keys.
- `/` Ligar is a secondary blue command.
- `*` S+B is the strongest Pick command after OK, because it creates Straight+Box.
- `OK` stays green and wide.
- `PRINT` stays dark.
- Key height should be slightly smaller in Pick mode so the extra command row fits without pushing the staged plays off screen.

Displayed meaning:

```text
-  Straight
+  Box
/  Ligar
*  S+B
```

Input meaning:

```text
123- = 123S
123+ = 123B
123* = Straight + Box shortcut
123/ = open ligar flow using current number and amount
```

Keep accepting:

```text
123S
123B
```

because existing users may already use it.

---

## Task 1: Add Fast Pick Symbol Contracts

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Add failing tests for symbol parsing**

Add these tests inside `SalesUiContractsTest` near the existing Pick assisted entry tests:

```kotlin
@Test
fun `pick assisted entry accepts minus as straight and plus as box`() {
    val straight = resolvePickAssistedEntry("123-")
    val box = resolvePickAssistedEntry("123+")

    assertEquals("123", straight?.digits)
    assertEquals("Pick3", straight?.lotteryType)
    assertEquals(PickPlayMode.STRAIGHT, straight?.pickMode)
    assertEquals("123", box?.digits)
    assertEquals("Pick3", box?.lotteryType)
    assertEquals(PickPlayMode.BOX, box?.pickMode)
}

@Test
fun `pick assisted entry keeps legacy s and b suffixes`() {
    val straight = resolvePickAssistedEntry("123S")
    val box = resolvePickAssistedEntry("123B")

    assertEquals(PickPlayMode.STRAIGHT, straight?.pickMode)
    assertEquals(PickPlayMode.BOX, box?.pickMode)
}

@Test
fun `pick number sanitizer preserves fast pick symbols`() {
    assertEquals("123-", sanitizeSaleNumberInput("123-", supportsPickModes = true))
    assertEquals("123+", sanitizeSaleNumberInput("123+", supportsPickModes = true))
    assertEquals("123*", sanitizeSaleNumberInput("123*", supportsPickModes = true))
    assertEquals("123/", sanitizeSaleNumberInput("123/", supportsPickModes = true))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"
```

Expected: the new tests fail because only `S` and `B` are recognized today.

- [ ] **Step 3: Implement symbol parsing**

In `SalesActivity.kt`, update `resolvePickAssistedEntry` and `sanitizeSaleNumberInput` to recognize fast symbols:

```kotlin
private fun normalizePickModeSuffix(suffix: Char): PickPlayMode? {
    return when (suffix.uppercaseChar()) {
        'B', '+' -> PickPlayMode.BOX
        'S', '-' -> PickPlayMode.STRAIGHT
        else -> null
    }
}

private fun normalizePickSuffixForInput(suffix: Char): Char? {
    return when (suffix.uppercaseChar()) {
        'B', 'S', '+', '-', '*', '/' -> suffix.uppercaseChar()
        else -> null
    }
}
```

Then use `normalizePickModeSuffix(suffix)` inside `resolvePickAssistedEntry`, and use `normalizePickSuffixForInput` inside `sanitizeSaleNumberInput`.

- [ ] **Step 4: Run tests**

Run the same Gradle command. Expected: new symbol tests pass and old Pick tests still pass.

---

## Task 2: Replace Pick Keypad Rows With Video-Style Symbols

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Update keypad row test**

Replace the existing Pick keypad test expectation with:

```kotlin
@Test
fun `pick keypad uses compact video style command symbols`() {
    val rows = resolveVentaKeyRows(UserRole.ADMIN, pickKeypad = true)
    val keys = rows.flatten()

    assertFalse(keys.contains("SELLER"))
    assertFalse(keys.contains("000"))
    assertFalse(keys.contains("00"))
    assertFalse(keys.contains("S"))
    assertFalse(keys.contains("B"))
    assertTrue(keys.contains("."))
    assertEquals(listOf("7", "8", "9", "⌫"), rows[0])
    assertEquals(listOf("4", "5", "6", "-"), rows[1])
    assertEquals(listOf("1", "2", "3", "+"), rows[2])
    assertEquals(listOf("0", ".", "/", "*"), rows[3])
    assertEquals(listOf("OK", "PRINT"), rows[4])
    assertEquals(2f, resolveVentaKeyWeight("OK", pickKeypad = true), 0.001f)
    assertEquals(1, keys.count { it == "⌫" })
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest.pick keypad uses compact video style command symbols"
```

Expected: FAIL because the current rows use `S`, `B`, and the old bottom row.

- [ ] **Step 3: Update `resolveVentaKeyRows`**

Change the Pick branch to:

```kotlin
if (pickKeypad) {
    return listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "/", "*"),
        listOf("OK", "PRINT"),
    )
}
```

- [ ] **Step 4: Update key weight rules**

Keep OK wide and make print wide enough to avoid text squeeze:

```kotlin
internal fun resolveVentaKeyWeight(key: String, pickKeypad: Boolean = false): Float {
    return when {
        key == "OK" -> 2f
        pickKeypad && key == "PRINT" -> 1.35f
        else -> 1f
    }
}
```

- [ ] **Step 5: Run tests**

Run the same test and then the full sales UI contract test. Expected: PASS.

---

## Task 3: Wire Symbol Keys To Pick Actions

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Add tests for applying Pick symbol keys**

Add:

```kotlin
@Test
fun `pick mode key writes video style suffix without clearing digits`() {
    assertEquals("252-", applyPickModeSymbolToNumber("252", "-"))
    assertEquals("252+", applyPickModeSymbolToNumber("252", "+"))
    assertEquals("252*", applyPickModeSymbolToNumber("252", "*"))
    assertEquals("252/", applyPickModeSymbolToNumber("252", "/"))
}

@Test
fun `pick mode key replaces previous pick suffix`() {
    assertEquals("252+", applyPickModeSymbolToNumber("252-", "+"))
    assertEquals("252-", applyPickModeSymbolToNumber("252B", "-"))
    assertEquals("252*", applyPickModeSymbolToNumber("252S", "*"))
}
```

- [ ] **Step 2: Implement helper**

Add in `SalesActivity.kt` near `applyPickModeKeyToNumber`:

```kotlin
internal fun applyPickModeSymbolToNumber(
    number: String,
    symbol: String,
): String {
    val suffix = symbol.firstOrNull() ?: return number.filter(Char::isDigit)
    val normalized = normalizePickSuffixForInput(suffix) ?: return number.filter(Char::isDigit)
    val digits = number.filter(Char::isDigit)
    return digits + normalized
}
```

- [ ] **Step 3: Wire keys in `VentaKeypad`**

Update the `when (key)` block:

```kotlin
"-" -> onPickModeKey(PickPlayMode.STRAIGHT)
"+" -> onPickModeKey(PickPlayMode.BOX)
"/" -> onApplyKey("/")
"*" -> onApplyKey("*")
```

Then update the existing `onPickModeKey` caller to use the symbol when the selected lottery supports Pick:

```kotlin
onPickModeKey = { nextMode ->
    val symbol = if (nextMode == PickPlayMode.BOX) "+" else "-"
    val nextNumber = applyPickModeSymbolToNumber(number, symbol)
    number = nextNumber
    pickMode = nextMode
    activeInput = resolvePickModeKeyNextInput(nextNumber, nextMode, activeInput)
}
```

- [ ] **Step 4: Make `/` and `*` meaningful**

In the keypad key handling path where `onApplyKey(key)` is processed:

- `/` should call the existing `onOpenLigar` flow when Pick is active.
- `*` should mark the number with `*` and prepare the Straight+Box shortcut.

For the first implementation, `*` can be UI-only and show a message if automatic two-row staging is not implemented in the same task:

```kotlin
feedbackMessage = "S+B listo: agrega Straight y Box con el mismo monto"
```

Do not silently create unclear rows until Task 4 defines the two-row behavior.

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"
```

Expected: PASS.

---

## Task 4: Add Straight+Box Shortcut Behavior

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt`

- [ ] **Step 1: Decide exact behavior**

Use this behavior:

```text
123* + amount 25 + OK
= stage two rows:
123S amount 25
123B amount 25
```

This matches the video meaning of `*` as Straight+Box together, keeps the cashier fast, and keeps the ticket explicit.

- [ ] **Step 2: Add test for recognizing S+B intent**

Add:

```kotlin
@Test
fun `pick straight box shortcut detects three and four digit entries`() {
    assertEquals("123", resolvePickStraightBoxShortcut("123*")?.digits)
    assertEquals("Pick3", resolvePickStraightBoxShortcut("123*")?.lotteryType)
    assertEquals("1234", resolvePickStraightBoxShortcut("1234*")?.digits)
    assertEquals("Pick4", resolvePickStraightBoxShortcut("1234*")?.lotteryType)
    assertNull(resolvePickStraightBoxShortcut("12*"))
    assertNull(resolvePickStraightBoxShortcut("12345*"))
}
```

- [ ] **Step 3: Implement shortcut detector**

Add:

```kotlin
internal data class PickStraightBoxShortcut(
    val digits: String,
    val lotteryType: String,
)

internal fun resolvePickStraightBoxShortcut(raw: String): PickStraightBoxShortcut? {
    val trimmed = raw.trim().uppercase(Locale.US)
    if (!trimmed.endsWith("*")) return null
    val digits = trimmed.dropLast(1).filter(Char::isDigit)
    val lotteryType = when (digits.length) {
        3 -> "Pick3"
        4 -> "Pick4"
        else -> return null
    }
    return PickStraightBoxShortcut(digits = digits, lotteryType = lotteryType)
}
```

- [ ] **Step 4: Stage two rows on OK**

When current number resolves as `PickStraightBoxShortcut`, call the existing validation/staging path twice:

```text
first with number = digits + "S"
then with number = digits + "B"
```

The UI should show two staged rows, not one ambiguous row.

- [ ] **Step 5: Keep duplicate confirmation behavior**

If either Straight or Box already exists in staged rows for the same lottery, reuse the existing duplicate prompt instead of bypassing it.

- [ ] **Step 6: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"
```

Expected: PASS.

---

## Task 5: Compact The Pick Keypad Visuals

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt`

- [ ] **Step 1: Add visual labels for command symbols**

In `VentaKeypad`, render command symbols with a symbol and a small label:

```text
-  STR
+  BOX
/  LIG
*  S+B
```

The visible button can show:

```text
-
STR
```

and:

```text
*
S+B
```

- [ ] **Step 2: Reduce Pick keypad height only**

Adjust `resolveVentaKeypadLayout` so Pick mode can use a smaller key height. If the current contract does not know `pickKeypad`, add a small local height override inside `VentaKeypad`:

```kotlin
val keyHeight = if (pickKeypad && windowMode == LotteryNetWindowMode.POS_TIGHT) {
    (keypadLayout.keyHeightDp - 5).coerceAtLeast(36)
} else if (pickKeypad) {
    (keypadLayout.keyHeightDp - 4).coerceAtLeast(40)
} else {
    keypadLayout.keyHeightDp
}
```

Use `keyHeight.dp` for the key surfaces.

- [ ] **Step 3: Keep text from overflowing**

For command buttons, use short labels only:

```text
STR
BOX
LIG
S+B
```

Do not use full words inside the button on POS-tight screens.

- [ ] **Step 4: Keep the staged list visible**

After the visual change, verify the staged rows still have visible space above the keypad in POS-tight mode. If not, reduce `ModeStrip` vertical spacing before reducing number font size.

---

## Task 6: Verification

**Files:**
- No source edits unless a verification issue is found.

- [ ] **Step 1: Run focused unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.ui.sales.SalesUiContractsTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run sale validator tests if touched**

If `SaleValidator.kt` was changed, run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lotterynet.pro.core.sales.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build the app**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Visual check with Playwright or Android-rendered browser path**

Use Playwright/browser screenshot verification only if the sale surface is available as a web-rendered target. If this native Compose screen is not browser-renderable, use the Android emulator/app screenshot path instead.

Check:

- Pick keypad fits on POS-tight width.
- `-`, `+`, `/`, `*` are visible and not clipped.
- `OK` remains the strongest confirm button.
- `PRINT` does not crowd the Pick commands.
- Staged play list is still visible after the keypad changes.
- Entering `123-`, `123+`, `123*` behaves as expected.

---

## Self-Review

- Spec coverage: The plan covers mode Pick only, symbol keys from the video, smaller keypad sizing, hierarchy, and visual verification before completion.
- Placeholder scan: No implementation task depends on an undefined symbol without defining it in the same task.
- Type consistency: New helpers use existing `PickPlayMode`, `SaleInputTarget`, and `SalesUiContractsTest` patterns.

---

## Execution Choice

Plan complete. Recommended execution is inline with checkpoints because the change is concentrated in one screen and its tests:

1. Implement Tasks 1-3 first and run tests.
2. Stop and visually review the keypad.
3. Implement Task 4 only if the Straight+Box behavior is approved.
4. Finish Task 5 and Task 6 with screenshots.
