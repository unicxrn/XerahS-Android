# Lumen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the whole XerahS Android app in the Lumen look (spec: `docs/superpowers/specs/2026-10-04-lumen-redesign-design.md`). The look must work in light, dark and true black, with every accent source.

**Architecture:**
- A pure-Kotlin `LumenPalette` in `core:common` derives every colour role from one accent seed by mixing in OkLab, and it is unit-tested.
- `colorSchemeForAccent` maps those roles onto the Material 3 `ColorScheme`.
- A `LumenTokens` CompositionLocal in `core:ui` carries the extras M3 lacks (tint, ink, hairline, glow, nav container, shadow, fonts). It is computed from the final scheme's primary, so Material You works too.
- Shared Lumen composables live in `core:ui/lumen`. Screens are restyled on top of them.
- Behaviour, routes, settings keys and data stay unchanged.

**Tech Stack:**
- Kotlin 1.9.22, Compose BOM 2024.02.00, Material 3
- Hilt, Navigation Compose
- JUnit 4 for the unit tests

**House rules for every task:**
- Commit messages are plain one-liners. No co-author trailers and no tool attribution.
- No literal BOM characters in Kotlin sources. Run `grep -rlP '\x{FEFF}' --include='*.kt' .` before committing; it must print nothing.
- Do not use KDoc containing `image/*`; use `//` comments.
- Build env: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/sdk`.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `core/common/src/main/java/com/xerahs/android/core/common/theme/LumenPalette.kt` | create | OkLab mixing and role derivation (pure Kotlin) |
| `core/common/src/test/java/com/xerahs/android/core/common/theme/LumenPaletteTest.kt` | create | Mixing, roles and contrast tests |
| `app/src/main/java/com/xerahs/android/ui/theme/Color.kt` | modify | `colorSchemeForAccent` uses `LumenPalette` |
| `app/src/main/res/font/inter_tight_variable.ttf` | add | Display font |
| `app/src/main/java/com/xerahs/android/ui/theme/Type.kt` | modify | Inter Tight display styles |
| `app/src/main/java/com/xerahs/android/ui/theme/Shape.kt` | modify | Lumen radii |
| `app/src/main/java/com/xerahs/android/ui/theme/Theme.kt` | modify | Provide `LocalLumen` |
| `app/build.gradle.kts`, `feature/annotation/build.gradle.kts`, `core/ui/build.gradle.kts` | modify | Module dependencies |
| `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenTokens.kt` | create | CompositionLocal |
| `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenSurfaces.kt` | create | LumenCard, BezelCard, IconTile, AccentGlow, CaptureCorners |
| `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenControls.kt` | create | PillCta, Eyebrow, HostChip, LumenSwitch, SegmentedTiles, LumenTopBar, CircleIconButton |
| `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenNavPill.kt` | create | Floating bottom nav |
| `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/HostColors.kt` | create | `UploadDestination.hostColor()` (moved from history) |
| `core/ui/src/main/java/com/xerahs/android/core/ui/XerahSComponents.kt` | modify | Restyle existing shared components |
| `app/src/main/java/com/xerahs/android/ui/MainActivity.kt` | modify | Nav pill replaces BottomAppBar and FAB |
| `feature/history/.../home/HomeScreen.kt`, `ShareCard.kt`, `Destinations.kt` | modify | Home and Uploaded |
| `feature/upload/.../UploadScreen.kt` | modify | Upload sheet and result |
| `feature/tools/.../ToolsScreen.kt` and tool screens | modify | Tools bento |
| `feature/annotation/.../AnnotationScreen.kt` | modify | Editor chrome |
| `feature/settings/.../AppearanceSettingsScreen.kt`, `ThemeEditorScreen.kt` | modify | Appearance |
| All remaining `*Screen.kt` | modify | Sweep to `LumenTopBar` and cards |

---

### Task 1: LumenPalette (colour maths)

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/theme/LumenPalette.kt`
- Test: `core/common/src/test/java/com/xerahs/android/core/common/theme/LumenPaletteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.xerahs.android.core.common.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumenPaletteTest {
    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()
    private val presets = listOf(
        0xFFB8F23A, 0xFF2BE0E0, 0xFF6D3BF0, 0xFF3B82F6, 0xFFFFB020, 0xFFFF5C8A, 0xFF34D17A,
        0xFF808080, 0xFFFFFF00, 0xFF0000FF
    ).map { it.toInt() }

    private fun assertClose(expected: Int, actual: Int) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected ushr shift) and 0xFF
            val a = (actual ushr shift) and 0xFF
            assertTrue("channel $shift: $e vs $a", kotlin.math.abs(e - a) <= 1)
        }
    }

    @Test fun mixZeroIsBase() = assertClose(WHITE, LumenPalette.mix(0xFFFF0000.toInt(), 0f, WHITE))
    @Test fun mixOneIsColor() = assertClose(0xFF6D3BF0.toInt(), LumenPalette.mix(0xFF6D3BF0.toInt(), 1f, WHITE))
    @Test fun mixHalfGreyIsBetween() {
        val m = LumenPalette.mix(BLACK, 0.5f, WHITE)
        val r = (m ushr 16) and 0xFF
        assertTrue(r in 90..140)
    }

    @Test fun withAlphaKeepsRgb() {
        val c = LumenPalette.withAlpha(0xFF123456.toInt(), 0.5f)
        assertEquals(0x123456, c and 0xFFFFFF)
        assertEquals(0x80, (c ushr 24) and 0xFF)
    }

    @Test fun trueBlackBackgroundIsBlack() {
        presets.forEach { assertEquals(BLACK, LumenPalette.derive(it, LumenMode.TRUE_BLACK).background) }
    }

    @Test fun lightSurfaceIsWhite() {
        presets.forEach { assertEquals(WHITE, LumenPalette.derive(it, LumenMode.LIGHT).surface) }
    }

    @Test fun textContrastHoldsEverywhere() {
        for (seed in presets) for (mode in LumenMode.values()) {
            val r = LumenPalette.derive(seed, mode)
            val tag = "seed=${Integer.toHexString(seed)} mode=$mode"
            assertTrue("$tag onSurface/bg", AccentDerivation.contrastRatio(r.onSurface, r.background) >= 4.5)
            assertTrue("$tag variant/surface", AccentDerivation.contrastRatio(r.onSurfaceVariant, r.surface) >= 4.5)
            assertTrue("$tag variant/bg", AccentDerivation.contrastRatio(r.onSurfaceVariant, r.background) >= 4.5)
            assertTrue("$tag onAccent/accent", AccentDerivation.contrastRatio(r.onAccent, r.accent) >= 4.5)
            assertTrue("$tag ink/surface", AccentDerivation.contrastRatio(r.ink, r.surface) >= 4.5)
            assertTrue("$tag ink/tint", AccentDerivation.contrastRatio(r.ink, r.tint) >= 4.5)
        }
    }

    @Test fun lightBackgroundIsTintedNotWhite() {
        val r = LumenPalette.derive(0xFF6D3BF0.toInt(), LumenMode.LIGHT)
        assertTrue(r.background != WHITE)
        assertTrue(AccentDerivation.relativeLuminance(r.background) > 0.85)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*LumenPaletteTest*'`
Expected: compilation FAIL, `Unresolved reference: LumenPalette`.

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.common.theme

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

enum class LumenMode { LIGHT, DARK, TRUE_BLACK }

/** All Lumen colour roles for one accent seed and mode. ARGB ints. */
data class LumenRoles(
    val accent: Int,
    val onAccent: Int,
    val ink: Int,               // accent used as text/icon on surfaces and on [tint]
    val tint: Int,              // accent-tinted container: chips, selected rows, icon tiles
    val background: Int,
    val surface: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val hairline: Int,          // translucent, for 1dp outlines
    val glow: Int,              // translucent accent, for radial glows
    val navContainer: Int,      // translucent nav pill fill
    val shadow: Int,            // shadow colour for soft elevation
)

/** OkLab colour mixing (same maths as CSS color-mix(in oklab, ...)) and Lumen role derivation. */
object LumenPalette {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    private fun toLinear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun toSrgb(c: Double): Int {
        val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
        return (v.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    private fun toOklab(argb: Int): DoubleArray {
        val r = toLinear((argb ushr 16) and 0xFF)
        val g = toLinear((argb ushr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromOklab(lab: DoubleArray): Int {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]).pow(3)
        val r = toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
        val g = toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
        val b = toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Mix [amount] (0..1) of [color] into [base], like color-mix(in oklab, color X%, base). */
    fun mix(color: Int, amount: Float, base: Int): Int {
        val t = amount.coerceIn(0f, 1f).toDouble()
        val a = toOklab(color)
        val b = toOklab(base)
        return fromOklab(DoubleArray(3) { b[it] + (a[it] - b[it]) * t })
    }

    fun withAlpha(argb: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (argb and 0xFFFFFF)

    /** Mix white into [color] until it reaches [target] contrast against [against]. */
    private fun lightenToContrast(color: Int, against: Int, target: Double): Int {
        var out = color
        var i = 0
        while (AccentDerivation.contrastRatio(out, against) < target && i < 24) {
            out = mix(WHITE, 0.15f, out)
            i++
        }
        return out
    }

    fun derive(seed: Int, mode: LumenMode): LumenRoles = when (mode) {
        LumenMode.LIGHT -> light(seed)
        LumenMode.DARK -> dark(seed, trueBlack = false)
        LumenMode.TRUE_BLACK -> dark(seed, trueBlack = true)
    }

    private fun light(seed: Int): LumenRoles {
        val accent = AccentDerivation.deriveAccent(seed, dark = false).accent
        val tint = mix(accent, 0.13f, WHITE)
        val ink = AccentDerivation.darkenToContrast(mix(accent, 0.82f, BLACK), tint, 4.5)
        val variant = 0xFF62606C.toInt()
        return LumenRoles(
            accent = accent,
            onAccent = WHITE,
            ink = ink,
            tint = tint,
            background = mix(accent, 0.05f, 0xFFF5F5F8.toInt()),
            surface = WHITE,
            surfaceContainerLow = mix(accent, 0.04f, WHITE),
            surfaceContainer = mix(accent, 0.07f, WHITE),
            surfaceContainerHigh = mix(accent, 0.10f, 0xFFF7F7FA.toInt()),
            surfaceContainerHighest = mix(accent, 0.13f, 0xFFF1F1F5.toInt()),
            onSurface = 0xFF131218.toInt(),
            onSurfaceVariant = variant,
            outline = mix(accent, 0.10f, 0xFFC7C7CF.toInt()),
            outlineVariant = mix(accent, 0.06f, 0xFFE4E3EA.toInt()),
            hairline = withAlpha(0xFF131218.toInt(), 0.07f),
            glow = withAlpha(accent, 0.30f),
            navContainer = withAlpha(WHITE, 0.92f),
            shadow = withAlpha(mix(accent, 0.35f, 0xFF131218.toInt()), 0.35f),
        )
    }

    private fun dark(seed: Int, trueBlack: Boolean): LumenRoles {
        val base = if (trueBlack) BLACK else 0xFF0D0D11.toInt()
        val s1 = if (trueBlack) 0xFF0B0B0E.toInt() else 0xFF16161B.toInt()
        val s2 = if (trueBlack) 0xFF111115.toInt() else 0xFF1C1C22.toInt()
        val surface = mix(seed, if (trueBlack) 0.05f else 0.06f, s1)
        val tint = mix(seed, if (trueBlack) 0.20f else 0.24f, s1)
        val ink = lightenToContrast(mix(seed, 0.58f, WHITE), tint, 4.5)
        return LumenRoles(
            accent = seed,
            onAccent = AccentDerivation.onColorFor(seed),
            ink = ink,
            tint = tint,
            background = if (trueBlack) BLACK else mix(seed, 0.06f, base),
            surface = surface,
            surfaceContainerLow = mix(seed, 0.07f, s1),
            surfaceContainer = mix(seed, if (trueBlack) 0.09f else 0.10f, s2),
            surfaceContainerHigh = mix(seed, 0.12f, 0xFF232329.toInt()),
            surfaceContainerHighest = mix(seed, 0.14f, 0xFF2A2A31.toInt()),
            onSurface = 0xFFF4F3F8.toInt(),
            onSurfaceVariant = 0xFF9C99A8.toInt(),
            outline = mix(seed, 0.12f, 0xFF34343C.toInt()),
            outlineVariant = mix(seed, 0.08f, 0xFF24242A.toInt()),
            hairline = withAlpha(WHITE, 0.07f),
            glow = withAlpha(seed, if (trueBlack) 0.28f else 0.40f),
            navContainer = withAlpha(mix(seed, 0.06f, if (trueBlack) 0xFF121216.toInt() else 0xFF1E1E24.toInt()), 0.94f),
            shadow = withAlpha(BLACK, 0.80f),
        )
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*LumenPaletteTest*' --tests '*AccentDerivationTest*'`
Expected: PASS.

If `textContrastHoldsEverywhere` fails for `variant/bg` in light mode, darken `variant` in `light()` (try `0xFF5C5A66`) until it passes. Do not loosen the test.

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/theme/LumenPalette.kt core/common/src/test/java/com/xerahs/android/core/common/theme/LumenPaletteTest.kt
git commit -m "Add Lumen palette derivation"
```

---

### Task 2: Colour scheme from LumenPalette

**Files:**
- Modify: `app/src/main/java/com/xerahs/android/ui/theme/Color.kt` (function `colorSchemeForAccent` at the end of the file)

- [ ] **Step 1: Replace `colorSchemeForAccent`**

Replace the whole `colorSchemeForAccent` function (keep its KDoc line and signature) with:

```kotlin
/**
 * Build the Lumen Material 3 scheme from a user accent [seedArgb]: accent-tinted neutrals,
 * contrast-safe text. See [LumenPalette].
 */
fun colorSchemeForAccent(seedArgb: Int, dark: Boolean, trueBlack: Boolean = true): ColorScheme {
    val mode = when {
        !dark -> LumenMode.LIGHT
        trueBlack -> LumenMode.TRUE_BLACK
        else -> LumenMode.DARK
    }
    val r = LumenPalette.derive(seedArgb, mode)
    fun c(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = c(r.accent), onPrimary = c(r.onAccent),
            primaryContainer = c(r.tint), onPrimaryContainer = c(r.ink),
            secondary = c(r.accent), onSecondary = c(r.onAccent),
            secondaryContainer = c(r.tint), onSecondaryContainer = c(r.ink),
            tertiary = c(r.ink), onTertiary = c(r.background),
            background = c(r.background), onBackground = c(r.onSurface),
            surface = c(r.surface), onSurface = c(r.onSurface),
            surfaceVariant = c(r.surfaceContainer), onSurfaceVariant = c(r.onSurfaceVariant),
            surfaceContainerLowest = c(r.background),
            surfaceContainerLow = c(r.surfaceContainerLow),
            surfaceContainer = c(r.surfaceContainer),
            surfaceContainerHigh = c(r.surfaceContainerHigh),
            surfaceContainerHighest = c(r.surfaceContainerHighest),
            outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        )
    } else {
        lightColorScheme(
            primary = c(r.accent), onPrimary = c(r.onAccent),
            primaryContainer = c(r.tint), onPrimaryContainer = c(r.ink),
            secondary = c(r.accent), onSecondary = c(r.onAccent),
            secondaryContainer = c(r.tint), onSecondaryContainer = c(r.ink),
            tertiary = c(r.ink), onTertiary = Color.White,
            background = c(r.background), onBackground = c(r.onSurface),
            surface = c(r.surface), onSurface = c(r.onSurface),
            surfaceVariant = c(r.surfaceContainer), onSurfaceVariant = c(r.onSurfaceVariant),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(r.surfaceContainerLow),
            surfaceContainer = c(r.surfaceContainer),
            surfaceContainerHigh = c(r.surfaceContainerHigh),
            surfaceContainerHighest = c(r.surfaceContainerHighest),
            outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        )
    }
}
```

Add these imports at the top of `Color.kt`:

```kotlin
import com.xerahs.android.core.common.theme.LumenMode
import com.xerahs.android.core.common.theme.LumenPalette
```

Delete the `// --- Signal-on-Black neutral tokens ---` block (`InkOnLight` through `DarkOutline`) if nothing else uses it. Check first with `grep -n "InkOnLight\|VariantOnLight\|LightBg\|LightSurface\|LightLow\|LightMed\|LightHigh\|LightOutline\|TextOnDark\|VariantOnDark\|DarkOutline" app/src/main/java/com/xerahs/android/ui/theme/Color.kt`. Keep `SIGNAL_LIME`.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If you see "unused import" warnings for `AccentDerivation`, remove that import.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xerahs/android/ui/theme/Color.kt
git commit -m "Build color scheme from Lumen palette"
```

---

### Task 3: Inter Tight, typography and shapes

**Files:**
- Add: `app/src/main/res/font/inter_tight_variable.ttf`
- Modify: `app/src/main/java/com/xerahs/android/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/xerahs/android/ui/theme/Shape.kt`

- [ ] **Step 1: Download the font (OFL, Google Fonts repo)**

```bash
curl -fL -o app/src/main/res/font/inter_tight_variable.ttf "https://github.com/google/fonts/raw/main/ofl/intertight/InterTight%5Bwght%5D.ttf"
file app/src/main/res/font/inter_tight_variable.ttf
```

Expected: `TrueType Font data`, about 0.5–0.6 MB.

- [ ] **Step 2: Add the display family in `Type.kt`**

Under `val JetBrainsMonoFamily = ...`, add:

```kotlin
@OptIn(ExperimentalTextApi::class)
val InterTightFamily = FontFamily(
    Font(R.font.inter_tight_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_tight_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
```

Imports: `androidx.compose.ui.text.ExperimentalTextApi`, `androidx.compose.ui.text.font.FontVariation`, `androidx.compose.ui.unit.em`.

- [ ] **Step 3: Switch the display styles in `XerahSTypography`**

Replace these entries. Leave all other styles as they are.

```kotlin
    displayLarge = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-0.045).em),
    displayMedium = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.045).em),
    displaySmall = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.045).em),
    headlineLarge = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.04).em),
    headlineMedium = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.04).em),
    headlineSmall = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.03).em),
    titleLarge = TextStyle(fontFamily = InterTightFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em),
```

- [ ] **Step 4: Replace `XerahSShapes` in `Shape.kt`**

```kotlin
val XerahSShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
)
```

- [ ] **Step 5: Build and commit**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL. If `ExperimentalTextApi` is reported as not needed, drop the `@OptIn` line.

```bash
git add app/src/main/res/font/inter_tight_variable.ttf app/src/main/java/com/xerahs/android/ui/theme/Type.kt app/src/main/java/com/xerahs/android/ui/theme/Shape.kt
git commit -m "Add Inter Tight display type and Lumen shapes"
```

---

### Task 4: LumenTokens and theme wiring

**Files:**
- Modify: `core/ui/build.gradle.kts`, `app/build.gradle.kts`, `feature/annotation/build.gradle.kts`
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenTokens.kt`
- Modify: `app/src/main/java/com/xerahs/android/ui/theme/Theme.kt`

- [ ] **Step 1: Dependencies**

- In `core/ui/build.gradle.kts` `dependencies { }`, add `implementation(project(":core:common"))` and `implementation(project(":core:domain"))`.
- In `app/build.gradle.kts`, next to the other `project(":core:...")` lines, add `implementation(project(":core:ui"))`.
- In `feature/annotation/build.gradle.kts`, add `implementation(project(":core:ui"))`.

- [ ] **Step 2: Create `LumenTokens.kt`**

```kotlin
package com.xerahs.android.core.ui.lumen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Lumen extras that Material 3's ColorScheme has no slot for. Provided by XerahSTheme. */
@Immutable
data class LumenTokens(
    val tint: Color,
    val ink: Color,
    val hairline: Color,
    val glow: Color,
    val navContainer: Color,
    val shadow: Color,
    val isDark: Boolean,
    val display: FontFamily,
    val mono: FontFamily,
)

val LocalLumen = staticCompositionLocalOf {
    LumenTokens(
        tint = Color(0xFFEDE7FD), ink = Color(0xFF3A1C8C), hairline = Color(0x12131218),
        glow = Color(0x4D6D3BF0), navContainer = Color(0xEBFFFFFF), shadow = Color(0x59131218),
        isDark = false, display = FontFamily.Default, mono = FontFamily.Monospace,
    )
}

object Lumen {
    val tokens: LumenTokens
        @Composable @ReadOnlyComposable get() = LocalLumen.current
}
```

- [ ] **Step 3: Provide it in `XerahSTheme`**

In `Theme.kt`, after `val colorScheme = when { ... }`, add the following and replace the `MaterialTheme(...)` call:

```kotlin
    val mode = when {
        !darkTheme -> LumenMode.LIGHT
        oledBlack -> LumenMode.TRUE_BLACK
        else -> LumenMode.DARK
    }
    // Derived from the final primary so Material You and custom seeds get the same extras.
    val roles = LumenPalette.derive(colorScheme.primary.toArgb(), mode)
    val tokens = LumenTokens(
        tint = colorScheme.primaryContainer,
        ink = colorScheme.onPrimaryContainer,
        hairline = Color(roles.hairline),
        glow = Color(roles.glow),
        navContainer = Color(roles.navContainer),
        shadow = Color(roles.shadow),
        isDark = darkTheme,
        display = InterTightFamily,
        mono = JetBrainsMonoFamily,
    )

    CompositionLocalProvider(LocalLumen provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XerahSTypography,
            shapes = XerahSShapes,
            content = content
        )
    }
```

Imports: `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.graphics.toArgb`, `com.xerahs.android.core.common.theme.LumenMode`, `com.xerahs.android.core.common.theme.LumenPalette`, `com.xerahs.android.core.ui.lumen.LocalLumen`, `com.xerahs.android.core.ui.lumen.LumenTokens`.

Note: `tint` and `ink` come from the scheme, so a Material You scheme uses its own container colours.

- [ ] **Step 4: Build and commit**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

```bash
git add core/ui/build.gradle.kts app/build.gradle.kts feature/annotation/build.gradle.kts core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenTokens.kt app/src/main/java/com/xerahs/android/ui/theme/Theme.kt
git commit -m "Provide Lumen tokens from the theme"
```

---

### Task 5: Lumen surfaces

**Files:**
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenSurfaces.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.xerahs.android.core.ui.lumen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Surface card: hairline outline plus a soft, accent-tinted shadow. */
@Composable
fun LumenCard(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    contentPadding: Dp = 0.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = Lumen.tokens
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(if (t.isDark) 0.dp else 14.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
            .clip(shape)
            .background(color)
            .border(1.dp, t.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Double-bezel card: tinted 5dp shell around an inner [LumenCard]. */
@Composable
fun BezelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shell = RoundedCornerShape(30.dp)
    Box(
        modifier
            .clip(shell)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, shell)
            .padding(5.dp)
    ) {
        LumenCard(radius = 25.dp, onClick = onClick, content = content)
    }
}

/** Rounded-square tinted icon holder. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    container: Color = Lumen.tokens.tint,
    tint: Color = Lumen.tokens.ink,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Radial accent glow, placed behind a screen's header. Purely decorative. */
@Composable
fun AccentGlow(modifier: Modifier = Modifier, size: Dp = 360.dp) {
    val glow = Lumen.tokens.glow
    Box(
        modifier.size(size).background(
            Brush.radialGradient(listOf(glow, Color.Transparent))
        )
    )
}

/** Screenshot-style corner brackets over an image preview. */
@Composable
fun BoxScope.CaptureCorners(color: Color = Color.White.copy(alpha = 0.9f), inset: Dp = 10.dp, arm: Dp = 16.dp) {
    Canvas(Modifier.matchParentSize().padding(inset)) {
        val a = arm.toPx()
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        fun corner(p: Path) = drawPath(p, color, style = stroke)
        corner(Path().apply { moveTo(0f, a); lineTo(0f, 0f); lineTo(a, 0f) })
        corner(Path().apply { moveTo(w - a, 0f); lineTo(w, 0f); lineTo(w, a) })
        corner(Path().apply { moveTo(w, h - a); lineTo(w, h); lineTo(w - a, h) })
        corner(Path().apply { moveTo(a, h); lineTo(0f, h); lineTo(0f, h - a) })
    }
}

/** Placeholder art for files with no thumbnail: accent gradient with a hill line. */
@Composable
fun AccentArt(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = Lumen.tokens.tint
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(tint, accent), start = Offset.Zero, end = Offset(size.width, size.height)))
        val p = Path().apply {
            moveTo(0f, size.height * 0.78f)
            lineTo(size.width * 0.25f, size.height * 0.62f)
            lineTo(size.width * 0.5f, size.height * 0.74f)
            lineTo(size.width * 0.75f, size.height * 0.52f)
            lineTo(size.width, size.height * 0.68f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(p, Color.Black.copy(alpha = 0.22f))
    }
}
```

- [ ] **Step 2: Build and commit**

Run: `./gradlew :core:ui:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

```bash
git add core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenSurfaces.kt
git commit -m "Add Lumen surface components"
```

---

### Task 6: Lumen controls and host colours

**Files:**
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenControls.kt`
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/HostColors.kt`
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/Destinations.kt` (delegate `dotColor()`)

- [ ] **Step 1: Create `HostColors.kt`**

```kotlin
package com.xerahs.android.core.ui.lumen

import androidx.compose.ui.graphics.Color
import com.xerahs.android.core.domain.model.UploadDestination

/** Brand-ish host colours, used only as small dots and chip tints. Not themed. */
fun UploadDestination.hostColor(): Color = when (this) {
    UploadDestination.IMGUR -> Color(0xFF1BB76E)
    UploadDestination.S3 -> Color(0xFFFF9900)
    UploadDestination.FTP -> Color(0xFF2E86DE)
    UploadDestination.SFTP -> Color(0xFF6B7C93)
    UploadDestination.CUSTOM_HTTP -> Color(0xFF8E8E93)
    UploadDestination.LOCAL -> Color(0xFF8E8E93)
    UploadDestination.NEXTCLOUD -> Color(0xFF0082C9)
    UploadDestination.IMMICH -> Color(0xFF4250AF)
    UploadDestination.GITHUB_GIST -> Color(0xFF6E7681)
}
```

If the `when` reports a missing branch, read `UploadDestination` and add the missing entries with `Color(0xFF8E8E93)`.

In `Destinations.kt`, replace the body of `dotColor()` with `= hostColor()` and add `import com.xerahs.android.core.ui.lumen.hostColor`.

- [ ] **Step 2: Create `LumenControls.kt`**

```kotlin
package com.xerahs.android.core.ui.lumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Small uppercase mono label above a section or heading. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontFamily = Lumen.tokens.mono, fontSize = 11.sp, letterSpacing = 0.06.em, fontWeight = FontWeight.Medium),
        color = color
    )
}

/** Mono text style for links, sizes and timestamps. */
@Composable
fun monoStyle(size: Int = 13): TextStyle =
    TextStyle(fontFamily = Lumen.tokens.mono, fontSize = size.sp, fontWeight = FontWeight.Medium)

/** Full-width accent pill with the trailing icon nested in its own circle. Presses scale to 0.98. */
@Composable
fun PillCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ArrowForward,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.98f else 1f, spring(), label = "cta-scale")
    val nudge by animateDpAsState(if (pressed) 2.dp else 0.dp, spring(), label = "cta-nudge")
    Row(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(start = 24.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = content, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(46.dp).offset(x = nudge, y = -nudge / 2).clip(CircleShape).background(content.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

/** Host name with its brand dot. */
@Composable
fun HostChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.height(24.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Accent switch: 50x30 track, white knob that springs across. */
@Composable
fun LumenSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "switch-track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.7f), label = "switch-knob")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .border(1.dp, Lumen.tokens.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

data class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector)

/** Icon + label segmented picker inside a tinted track; the selected tile lifts to a card. */
@Composable
fun <T> SegmentedTiles(options: List<SegmentOption<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(24.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { o ->
            val on = o.value == selected
            Column(
                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(o.value) }
                    .semantics { contentDescription = o.label + if (on) ", selected" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(o.icon, null, tint = if (on) Lumen.tokens.ink else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(o.label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 44dp circular icon button on a surface card. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Lumen.tokens.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(20.dp))
    }
}

/**
 * Lumen screen header: circular back button (when [onBack] is set), a big Inter Tight title,
 * and trailing actions. Handles the status bar inset itself; use with Scaffold(topBar = ...).
 */
@Composable
fun LumenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
```

`Spacer` and `width` imports may be unused; remove whatever the compiler flags.

- [ ] **Step 3: Build and commit**

Run: `./gradlew :core:ui:compileDebugKotlin :feature:history:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

```bash
git add core/ui/src/main/java/com/xerahs/android/core/ui/lumen/ feature/history/src/main/java/com/xerahs/android/feature/history/home/Destinations.kt
git commit -m "Add Lumen controls and host colors"
```

---

### Task 7: Restyle the shared XerahSComponents

**Files:**
- Modify: `core/ui/src/main/java/com/xerahs/android/core/ui/XerahSComponents.kt`

Keep every public signature. Change the bodies only.

- [ ] **Step 1: `SectionHeader`**: use the eyebrow look.

```kotlin
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Eyebrow(title, modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
}
```

- [ ] **Step 2: `SettingsGroupCard`**

```kotlin
@Composable
fun SettingsGroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LumenCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
}
```

- [ ] **Step 3: `GradientBorderCard`**: becomes a bezel.

```kotlin
@Composable
fun GradientBorderCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BezelCard(modifier = modifier.fillMaxWidth(), content = content)
}
```

- [ ] **Step 4: `StatusBanner`**: keep the parameters. Replace `Card(...)` with a `LumenCard(modifier = modifier.fillMaxWidth(), color = containerColor)`. Replace the 32dp icon with `IconTile(icon, size = 40.dp, container = contentColor.copy(alpha = 0.12f), tint = contentColor)`.

- [ ] **Step 5: `StatCard`**: use `LumenCard(modifier, radius = 22.dp, contentPadding = 16.dp)`. The value is `Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)` and the label is `Eyebrow(label)` below it.

- [ ] **Step 6: `EmptyState`**: replace the 72dp icon with `IconTile(icon, size = 72.dp)`. Use `MaterialTheme.typography.titleLarge` for the title and keep the subtitle.

- [ ] **Step 7: Remove unused imports, then build and commit**

Imports to add: `com.xerahs.android.core.ui.lumen.BezelCard`, `Eyebrow`, `IconTile`, `LumenCard`.

Run: `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL.

```bash
git add core/ui/src/main/java/com/xerahs/android/core/ui/XerahSComponents.kt
git commit -m "Restyle shared components in Lumen"
```

---

### Task 8: Floating nav pill

**Files:**
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenNavPill.kt`
- Modify: `app/src/main/java/com/xerahs/android/ui/MainActivity.kt` (the `Scaffold` around line 346)

- [ ] **Step 1: Create `LumenNavPill.kt`**

```kotlin
package com.xerahs.android.core.ui.lumen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class NavPillItem(val key: String, val label: String, val icon: ImageVector)

/** Floating bottom nav. The selected item expands into an accent pill with its label. */
@Composable
fun LumenNavPill(items: List<NavPillItem>, selectedKey: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = Lumen.tokens
    Row(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = t.shadow, spotColor = t.shadow)
            .clip(CircleShape)
            .background(t.navContainer)
            .border(1.dp, t.hairline, CircleShape)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val on = item.key == selectedKey
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, spring(), label = "nav-bg")
            val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.height(48.dp).widthIn(min = 48.dp).clip(CircleShape).background(bg)
                    .clickable(role = Role.Tab) { onSelect(item.key) }
                    .semantics { contentDescription = item.label; selected = on }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(item.icon, null, tint = fg, modifier = Modifier.size(21.dp))
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Replace the bottom bar in `MainActivity`**

Change `showBottomBar` to include Settings:

```kotlin
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.S3Explorer.route,
        Screen.Tools.route,
        Screen.Settings.route
    )
```

Replace the whole `Scaffold(...) { innerPadding -> XerahSNavGraph(...) }` block with:

```kotlin
    Box(Modifier.fillMaxSize()) {
        XerahSNavGraph(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = showBottomBar,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            val items = buildList {
                add(NavPillItem(Screen.Home.route, "Home", Icons.Outlined.Home))
                if (s3Configured) add(NavPillItem(Screen.S3Explorer.route, "Cloud", Icons.Outlined.Cloud))
                add(NavPillItem(Screen.Tools.route, "Tools", Icons.Outlined.GridView))
                add(NavPillItem(Screen.Settings.route, "Settings", Icons.Outlined.Tune))
            }
            LumenNavPill(items = items, selectedKey = currentRoute, onSelect = { route ->
                if (route == currentRoute) return@LumenNavPill
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
    }
```

Add the imports the compiler asks for: `AnimatedVisibility`, `fadeIn/fadeOut`, `slideInVertically/slideOutVertically`, `Box`, `navigationBarsPadding`, `Icons.Outlined.*`, `NavPillItem`, `LumenNavPill`. Remove the now-unused `BottomAppBar`, `FloatingActionButton`, `IconButton` and `Scaffold` imports.

- [ ] **Step 3: Keep content clear of the pill**

Every top-level screen (Home, S3 explorer, Tools, Settings) must leave 96dp of bottom padding in its scrolling content so the last item is not hidden behind the pill. Home already uses `contentPadding = PaddingValues(bottom = 96.dp)`. Add the same to `ToolsScreen`, `SettingsScreen` and `S3ExplorerScreen` in their list or column. Those files are reworked in later tasks; for now just add the padding.

Because the outer Scaffold is gone, each screen's own Scaffold now handles the status bar inset. Run the app and check that no top bar is drawn under the status bar.

- [ ] **Step 4: Build, run on the emulator, commit**

Run: `./gradlew :app:installDebug`. Then on `xerahs_pixel`, check: the pill shows on Home, Tools and Settings, switching tabs keeps their state, and Back from Tools returns to Home.

```bash
git add core/ui/src/main/java/com/xerahs/android/core/ui/lumen/LumenNavPill.kt app/src/main/java/com/xerahs/android/ui/MainActivity.kt feature
git commit -m "Replace bottom bar with Lumen nav pill"
```

---

### Task 9: Home

**Files:**
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/HomeScreen.kt`
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/HomeViewModel.kt` (add `todayCount`)
- Modify: `app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt` (pass `onStats`)

- [ ] **Step 1: Add `todayCount` to `HomeUiState`**

In `HomeUiState`, add `val todayCount: Int = 0`. In `rebuild()`, where the state is built from the items, compute:

```kotlin
val startOfDay = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
val todayCount = allItems.count { it.timestamp >= startOfDay }
```

`allItems` stands for the unfiltered list `rebuild()` already holds; read the function and use its actual variable. Set it in the copied state.

- [ ] **Step 2: New `HomeScreen` signature**

```kotlin
fun HomeScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
)
```

In `NavGraph.kt`, pass `onStats = { navController.navigate(Screen.Statistics.route) }`.

- [ ] **Step 3: Replace the Scaffold body**

Keep the `LaunchedEffect` message collector. Replace the `Scaffold(...) { ... }` with a single `LazyColumn` on `MaterialTheme.colorScheme.background`, behind an `AccentGlow` aligned top-end at `offset(x = 120.dp, y = (-140).dp)` inside a `Box(Modifier.fillMaxSize())`. Search visibility is local state: `var searching by rememberSaveable { mutableStateOf(uiState.query.isNotEmpty()) }`.

Items, in order:

1. **Header row.** `windowInsetsPadding(WindowInsets.statusBars)`, padding 20dp horizontally and 12dp top. It holds a `BrandMark()` and `CircleIconButton(Icons.Outlined.Settings, "Settings", onSettings)`.
2. **Hero.**
   - `Eyebrow("● ${uiState.todayCount} uploads today")`.
   - Then:
     ```kotlin
     Text(buildAnnotatedString {
         append("Capture. Upload.\n")
         withStyle(SpanStyle(color = Lumen.tokens.ink)) { append("Share in a tap.") }
     }, style = MaterialTheme.typography.displaySmall)
     ```
3. **Bento.**
   - A `Row(Modifier.height(158.dp), spacedBy 10dp)`.
   - Left, weight 1.15: an accent tile. It is a `Box` with radius 26, background `colorScheme.primary`, clickable `onCreate`, and padding 16. Inside: a 42dp circle in `onPrimary.copy(alpha=.2f)` with `Icons.Outlined.FileUpload`, a "Upload" titleLarge and an "Any file, any host" bodySmall at 0.8 alpha, all in `onPrimary`.
   - Right, weight 1: a Column of two `LumenCard(radius = 22.dp, onClick = …)` tiles, each with weight 1 and holding a Row of icon plus label.
     - "Search" (`Icons.Outlined.Search`): toggles `searching`.
     - "Stats" (`Icons.Outlined.BarChart`): calls `onStats`.
4. **Search field.** Only when `searching`. Reuse the existing `OutlinedTextField` with `shape = CircleShape` and no indicator lines.
5. **Latest upload.** Only when the timeline is non-empty and the query is empty: a `BezelCard(onClick = { onOpen(latest.id) })`. `latest` is the first id of the first section, looked up in `itemsById`.
   - Preview: a 108dp-tall `Box` clipped to 20dp, with padding 6dp. If the item `isImage` and the thumbnail or file exists, show `AsyncImage`. The timeline row already loads thumbnails; use the same loader it uses in `TimelineRow`. Otherwise show `AccentArt()`. Overlay `CaptureCorners()` and a "2 min ago"-style chip (`relativeTime(latest.timestamp)`) in mono on `Color.Black.copy(alpha=.35f)`.
   - Footer row: the link in `monoStyle(14)` with `maxLines = 1` and ellipsis, `HostChip(latest.uploadDestination.displayName, latest.uploadDestination.hostColor())` under it, and a 46dp circle copy button on `Lumen.tokens.tint` with `Icons.Outlined.ContentCopy` in `Lumen.tokens.ink`. The button copies via `LocalClipboardManager` and shows a "Link copied" toast.
6. **Recent list.** The existing sections and `TimelineRow` items stay. Restyle the sticky header as `Eyebrow(section.label)` on the background, and drop the `HorizontalDivider`. Change `TimelineRow` so the thumbnail is a 44dp square with 14dp radius, the title uses `titleSmall`, the meta line uses `monoStyle(11)` in `onSurfaceVariant`, and a trailing `HostChip` replaces the old dot.
7. **Empty state.** When there is no history, keep the header and hero, then show `EmptyState(Icons.Outlined.Image, "Pick an image to get your first link")` under the bento.

Add this private composable in the same file:

```kotlin
@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text("XerahS", style = MaterialTheme.typography.titleLarge)
    }
}
```

Use `contentPadding = PaddingValues(bottom = 112.dp)` and apply `Modifier.padding(horizontal = 20.dp)` to each item instead of horizontal content padding, so sticky headers still span the full width. Leave 20dp between hero blocks.

- [ ] **Step 4: Build, check on the emulator, commit**

Run: `./gradlew :app:installDebug`. Check:
- Light and dark mode.
- Empty history and history with items.
- Search toggle filtering.
- Copy from the latest card.
- Stats navigation.

```bash
git add feature/history app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt
git commit -m "Lumen home screen"
```

---

### Task 10: Uploaded (share card)

**Files:**
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/ShareCard.kt`
- Modify: `app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt` (ShareResult block: drop the centred `Box` padding)

Keep the `ShareCard` signature and all callbacks. Replace the layout with a vertical `Column(Modifier.fillMaxSize().verticalScroll(...).windowInsetsPadding(WindowInsets.systemBars).padding(20.dp), spacedBy 18dp)` over an `AccentGlow` aligned top-end.

1. **Top row.**
   - `CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onDone)`.
   - On the right, keep the existing delete-from-host action if `ShareCard` already has one. If it doesn't, leave nothing there; do not add the feature.
2. **Badge and headline.**
   - Badge: a 58dp primary circle with `Icons.Default.Check` in `onPrimary`. Keep the existing `circleScale` animation and surround the circle with an 8dp `Lumen.tokens.tint` ring (outer `Box` 74dp with tint background).
   - Headline:
     ```kotlin
     Text(buildAnnotatedString {
         append("Uploaded.\n")
         withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Link ready.") }
     }, style = MaterialTheme.typography.displaySmall)
     ```
3. **Link card.**
   - A `BezelCard` holding a row: `Eyebrow(if (shortUrl != null) "Short link" else "Link")` above `effectiveLink` in `monoStyle(20)` with `maxLines = 2`.
   - On the right, a 52dp primary circle copy button calling `onCopy(effectiveLink)`.
   - If the existing card shows a shorten action, keep it as a `TextButton("Shorten")` under the link. It calls `onShorten` and shows a small progress indicator when `isShortening`.
4. **Action tiles.**
   - Three `LumenCard(radius = 22.dp, onClick = …)` tiles, each `weight(1f)` and 72dp tall, holding an icon in `Lumen.tokens.ink` over a label:
     - Share (`onShare`)
     - Open (`ACTION_VIEW` on the link; reuse the existing open code if present, otherwise `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveLink)))` wrapped in `runCatching`)
     - QR code (`showQr = true`)
5. **Details.** Rows separated by 1dp `Lumen.tokens.hairline` lines. Each row has an `onSurfaceVariant` bodyMedium label on the left and the value in `monoStyle(12)` on the right:
   - Host: a `HostChip`
   - Size: `formatFileSize(item.fileSize)`
   - Time: `relativeTime`, or a formatted timestamp if `relativeTime` is not accessible
   - Delete URL: "saved" when `item.deleteUrl != null`, otherwise skip the row
6. **Done.** `PillCta("Done", onDone, icon = Icons.Default.Check)` at the bottom.

Keep the QR `AlertDialog` as it is.

In `NavGraph.kt`, change the ShareResult `Box` to `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))` with no padding and no centring, so the card fills the screen.

- [ ] **Build, check on the emulator (open a share from Home), commit**

```bash
git add feature/history app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt
git commit -m "Lumen share result screen"
```

---

### Task 11: Upload screen

**Files:**
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadScreen.kt`

Do not touch view-model calls, dialogs (duplicate, prompts), `LaunchedEffect`s or the after-upload handling. Restyle only the `Scaffold` content (around lines 286–720), `UploadProgressRow`, `UrlResultCard` and `DestinationSheetContent`.

- [ ] **Step 1: Top bar.** Replace the `TopAppBar` with `LumenTopBar(title = if (isBatch) "Upload ${imagePaths.size} files" else "Upload", onBack = onBack)`.

- [ ] **Step 2: File header.** Replace the preview `Card` (around line 310) with a row:
  - A 62dp thumbnail clipped to 18dp. Use the existing image preview for images; otherwise `IconTile` with the file-type icon the screen already uses via `FileTypeTile`, or `AccentArt()`.
  - Next to it, the file name in `titleLarge` with `maxLines = 1`.
  - Below the name, metadata in `monoStyle(11)` in `onSurfaceVariant`: the MIME type and size the screen already computes. If the size is not computed, use `java.io.File(imagePath).length()` with `formatFileSize` (copy the private helper from history's `Destinations.kt` into this file as `private`).

- [ ] **Step 3: "Upload to" card.** Replace the destination row (around lines 648–700) with `Eyebrow("Upload to")` followed by a `LumenCard(contentPadding = 5.dp)`:
  - The card holds one 64dp row for the active profile or destination: a 40dp rounded-13 box tinted `hostColor().copy(alpha=.16f)` with a 12dp dot, the name in `titleSmall`, the destination display name in `monoStyle(11)`, and a trailing "Change" `TextButton` that opens the existing sheet.
  - Keep `enabled = !uiState.isUploading`.

- [ ] **Step 4: Album and tags.** Keep the logic. Render the album and tag `FilterChip`s with `shape = CircleShape`. Selected chips use `selectedContainerColor = Lumen.tokens.tint` and `selectedLabelColor = Lumen.tokens.ink`. Unselected chips have a 1dp `Lumen.tokens.hairline` border.

- [ ] **Step 5: Primary action.** Replace the upload `Button` with `PillCta(text = actionLabel, onClick = <existing onClick>, icon = Icons.Default.NorthEast, loading = uiState.isUploading, enabled = <existing enabled>)`.

- [ ] **Step 6: Progress.** In `UploadProgressRow`, use a `LumenCard(contentPadding = 16.dp)` with a `LinearProgressIndicator` (`trackColor = Lumen.tokens.tint`, `strokeCap = StrokeCap.Round`, height 6dp) and the percentage in `monoStyle(12)`.

- [ ] **Step 7: Success state.** When `isSuccess`:
  - Replace the `StatusBanner` with the same badge-and-headline block used in Task 10 ("Uploaded." / "Link copied." when `COPY_URL` was among the actions, otherwise "Link ready.").
  - In `UrlResultCard`, use `BezelCard` with the URL in `monoStyle(16)` and a 46dp tint copy button.
  - Replace "View history" with `PillCta("Done", onUploadComplete, icon = Icons.Default.Check)`.

- [ ] **Step 8: Error state.** Keep `StatusBanner` with error colours. Replace "Retry" with `PillCta("Retry", <existing>, icon = Icons.Default.Refresh)`.

- [ ] **Step 9: Destination sheet.** In `DestinationSheetContent`:
  - Each row is 64dp with a 20dp radius. Selected rows get a `Lumen.tokens.tint` background, a host-dot box as in Step 3, the name and mono subtitle, and a `RadioButton` coloured `primary`.
  - Disabled rows (destination not allowed) keep their current disabled handling at 0.38 alpha.
  - Add `containerColor = MaterialTheme.colorScheme.background` to the `ModalBottomSheet` and `shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)`.

- [ ] **Step 10: Build, check on the emulator, commit**

Check a single image, a batch of 2, a non-image file, the success state and the error state (for example S3 with bad credentials).

```bash
git add feature/upload
git commit -m "Lumen upload screen"
```

---

### Task 12: Tools

**Files:**
- Modify: `feature/tools/src/main/java/com/xerahs/android/feature/tools/ToolsScreen.kt`
- Modify: `BatchToolScreen.kt`, `ColorPickerToolScreen.kt`, `QrScreen.kt`, `HashScreen.kt` (top bar and cards)

- [ ] **Step 1: Replace `ToolsScreen`**

Read the `ToolId` enum first. The hero tool is the batch one; use its actual constant, named `BATCH` below.

```kotlin
@Composable
fun ToolsScreen(onOpen: (ToolId) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccentGlow(Modifier.align(Alignment.TopEnd).offset(x = 140.dp, y = (-120).dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tools", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 8.dp))
            ToolHero(ToolId.BATCH) { onOpen(ToolId.BATCH) }
            ToolId.values().filter { it != ToolId.BATCH }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        LumenCard(Modifier.weight(1f).heightIn(min = 136.dp), contentPadding = 16.dp, onClick = { onOpen(tool) }) {
                            IconTile(tool.icon)
                            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                            Text(tool.title, style = MaterialTheme.typography.titleSmall)
                            Text(tool.subtitle, style = monoStyle(11), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolHero(tool: ToolId, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(30.dp)).padding(5.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.35f))))
                .clickable(onClick = onClick).padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Batch", color = on.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(on.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NorthEast, null, tint = on, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(tool.title, style = MaterialTheme.typography.headlineSmall, color = on)
            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
        }
    }
}
```

`lerp` is `androidx.compose.ui.graphics.lerp`.

- [ ] **Step 2: Tool screens.** In each of the four tool screens:
  - Replace `TopAppBar(...)` with `LumenTopBar(title = <same title>, onBack = <same back lambda>)`.
  - Replace `Card(...)` and `OutlinedCard(...)` with `LumenCard(contentPadding = 16.dp)`, keeping the content.
  - Replace the primary `Button`s with `PillCta`. Leave secondary buttons as `OutlinedButton(shape = CircleShape)`.
  - Show hex, hash and decoded-QR output in `monoStyle(13)`.

- [ ] **Step 3: Build, check, commit**

```bash
git add feature/tools
git commit -m "Lumen tools screens"
```

---

### Task 13: Editor chrome

**Files:**
- Modify: `feature/annotation/src/main/java/com/xerahs/android/feature/annotation/AnnotationScreen.kt`

Only change the chrome. Canvas drawing, tools and colours chosen by the user stay the same.

- [ ] **Step 1: Top bar** (the translucent `Surface` near line 457). Replace it with a transparent `Row` with status-bar insets, horizontal padding 14dp and spacing 8dp. It holds:
  - `CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { if (uiState.isCropMode) viewModel.setCropMode(false) else onBack() }`.
  - `Text(java.io.File(imagePath).name, style = monoStyle(12), color = onSurfaceVariant, maxLines = 1, overflow = Ellipsis, modifier = Modifier.weight(1f))`.
  - In crop mode: keep the "Apply Crop" action as `PillCta`-styled `Button(shape = CircleShape)`.
  - Otherwise:
    - An Undo/Redo pill: a `Row` with 44dp height, a `CircleShape` surface background and a hairline border, holding two 38dp `IconButton`s with the existing enabled logic.
    - A 44dp tint circle for Effects (`Lumen.tokens.tint` background, `AutoFixHigh` icon in `Lumen.tokens.ink`).
    - `CircleIconButton`s for Crop and Extract text, with the existing progress handling.

- [ ] **Step 2: Canvas frame.** Wrap the canvas container in a bezel. It gets padding `top = 64.dp + statusBar`, horizontal 14dp, and bottom to sit above the toolbar (keep whatever the current layout uses to avoid overlap). Use `Modifier.clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(30.dp)).padding(5.dp).clip(RoundedCornerShape(25.dp))`. Gesture coordinates must still map correctly, so put the bezel on the parent `Box`, not inside the canvas's coordinate space.

- [ ] **Step 3: Tool bar** (the `Surface` near line 530):
  - Use `shape = CircleShape`, `color = MaterialTheme.colorScheme.surface` and a hairline border, at 62dp height.
  - Restyle `CompactToolButton` as a 50dp circle. Selected tools get a `primary` background and `onPrimary` icon. Unselected tools get a transparent background and an `onSurfaceVariant` icon. Keep the label as `contentDescription` and drop the visible text label.
  - The primary export action below it becomes `PillCta("Continue to upload", <existing onClick>, loading = <existing exporting flag if any>)`.

- [ ] **Step 4: Swatches.** In `ColorSwatchRow`, use 26dp circles. The selected swatch gets `border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)` with a 3dp gap (an outer 34dp box). Add the stroke width in `monoStyle(11)` at the end of the row if the row has room. In tool option sheets, set `ModalBottomSheet(containerColor = MaterialTheme.colorScheme.background)`.

- [ ] **Step 5: Build, check (draw, undo/redo, crop, effects, export), commit**

```bash
git add feature/annotation
git commit -m "Lumen editor chrome"
```

---

### Task 14: Appearance and theme editor

**Files:**
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/AppearanceSettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/ThemeEditorScreen.kt`

Keep `accentPresets`, `CustomAccentPickerDialog` and every view-model call.

- [ ] **Step 1: Layout.** Replace the Scaffold with a `Scaffold(topBar = { LumenTopBar("Appearance", onBack = onBack) })`. Its content is a scrolling `Column(padding horizontal 20dp, spacedBy 18dp, bottom 32dp)`:
  1. `BezelCard { ThemePreviewCard() }`. Restyle the existing `ThemePreviewCard` to a row showing:
     - the brand mark,
     - an accent "Upload" pill (40dp, `primary`/`onPrimary`),
     - a tint "Copy link" pill (`Lumen.tokens.tint`/`ink`),
     - the sample link `is.gd/xR4m2` in `monoStyle(12)` coloured `Lumen.tokens.ink`,
     - all over `colorScheme.background` with an `AccentGlow` in the corner.
  2. `Eyebrow("Theme")`, then:
     ```kotlin
     SegmentedTiles(
         options = listOf(
             SegmentOption(ThemeMode.SYSTEM, "System", Icons.Outlined.Monitor),
             SegmentOption(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
             SegmentOption(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
         ),
         selected = uiState.themeMode,
         onSelect = viewModel::setThemeMode
     )
     ```
  3. `Eyebrow(if (uiState.dynamicColor) "Accent · overridden by system colors" else "Accent")`, then a `FlowRow` of 40dp swatches. Each swatch shows a check in `AccentDerivation.onColorFor` colour when selected, and a ring drawn as `border(2.dp, color, CircleShape)` around a 3dp background gap. The custom swatch goes last. When `uiState.dynamicColor` is on, the whole row gets `alpha(0.45f)` and is disabled; this replaces the old `AnimatedVisibility`.
  4. A `LumenCard` with two toggle rows separated by a hairline:
     - "System colors" ("Match your wallpaper (Material You)", or "Requires Android 12 or higher") with `LumenSwitch(uiState.dynamicColor, viewModel::setDynamicColor, enabled = SDK >= S)`.
     - "True black" ("Pure black dark theme for OLED") with `LumenSwitch(uiState.oledBlack, viewModel::setOledBlack)`. This row stays visible in Light mode but is disabled at 0.45 alpha, with the subtitle "Applies in dark mode".
     - Each row is 64dp high: an `IconTile(size = 38.dp)` (Palette or DarkMode), the title in `titleSmall` and subtitle in `bodySmall` in `onSurfaceVariant`, then the switch. The whole row is clickable and toggles.
  5. If the screen links to the theme editor today, keep that entry as a `LumenCard` row with a chevron.

- [ ] **Step 2: Theme editor.**
  - Use `LumenTopBar("Custom theme", onBack = …)`.
  - The name field gets `shape = RoundedCornerShape(20.dp)`.
  - Use the same swatch composable as Appearance: move `AccentSwatch` to `internal` in a new file `feature/settings/.../AccentSwatches.kt` and reuse it.
  - Use `Eyebrow` labels for the hue and saturation sliders, coloured with `SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = Lumen.tokens.tint)`.
  - The preview becomes a `BezelCard`.
  - Save becomes a `PillCta("Save theme", …)` at the bottom instead of the extended FAB.

- [ ] **Step 3: Build, check every combination on the emulator, commit**

Check every combination: System/Light/Dark × True black on/off × each preset × custom × Material You on. All text must stay readable.

```bash
git add feature/settings
git commit -m "Lumen appearance and theme editor"
```

---

### Task 15: Settings hub and sub-screens

**Files:**
- Modify: `SettingsScreen.kt`, `UploadSettingsScreen.kt`, `SecuritySettingsScreen.kt`, `StorageSettingsScreen.kt`, `BackupSettingsScreen.kt`, `StatisticsScreen.kt`, `AppUpdateScreen.kt`, `ConflictResolutionScreen.kt`, all in `feature/settings/src/main/java/com/xerahs/android/feature/settings/`

Apply the same mechanical rules to each file:

1. Replace `TopAppBar(title = { Text("X") }, navigationIcon = { IconButton(onClick = back) { … } }, actions = { … })` with `LumenTopBar(title = "X", onBack = back) { <same actions as CircleIconButton> }`. A `LargeTopAppBar` or `MediumTopAppBar` with a scroll behaviour gets the same replacement; drop the `nestedScroll(scrollBehavior…)` modifier and the `scrollBehavior` variable.
2. Replace every `Switch(checked, onCheckedChange, enabled)` with `LumenSwitch(checked, onCheckedChange, enabled = enabled)`.
3. `ListItem` leading icons become `IconTile(icon, size = 38.dp)`. Set `ListItemDefaults.colors(containerColor = Color.Transparent)` so rows sit on the `LumenCard`.
4. Raw `Card` and `OutlinedCard` become `LumenCard(contentPadding = 16.dp)` (or no padding if the card holds `ListItem`s).
5. The single primary `Button` on a screen (for example "Back up now", "Check for updates") becomes `PillCta`. Other buttons become `OutlinedButton(shape = CircleShape)` or `TextButton`.
6. Values like versions, paths, sizes, counts and hashes go in `monoStyle(12)`.
7. `HorizontalDivider` between rows inside a card uses `color = Lumen.tokens.hairline`.

`SettingsScreen` is a top-level tab now: no `onBack` in `LumenTopBar`, and it needs 112dp bottom content padding. Its group headers use `SectionHeader` (already restyled). Each entry is a row inside `SettingsGroupCard` with an `IconTile`, title, subtitle and a `Icons.AutoMirrored.Filled.KeyboardArrowRight` chevron in `onSurfaceVariant`.

- [ ] **Step 1:** Apply the rules to `SettingsScreen.kt`, then build: `./gradlew :feature:settings:compileDebugKotlin`.
- [ ] **Step 2:** Apply the rules to `UploadSettingsScreen.kt` and `AfterUploadActionChips.kt`. The after-upload chips use pill `FilterChip`s styled as in Task 11 Step 4. Build.
- [ ] **Step 3:** Apply the rules to `SecuritySettingsScreen.kt`, `StorageSettingsScreen.kt` and `BackupSettingsScreen.kt`. Build.
- [ ] **Step 4:** Apply the rules to `StatisticsScreen.kt` (charts keep working, with `primary` as the series colour), `AppUpdateScreen.kt` and `ConflictResolutionScreen.kt`. Build.
- [ ] **Step 5:** Install, then open every settings screen in light and dark. Commit:

```bash
git add feature/settings
git commit -m "Lumen settings screens"
```

---

### Task 16: Destinations, profiles and uploader import

**Files:**
- Modify: everything in `feature/settings/src/main/java/com/xerahs/android/feature/settings/destinations/`, `profiles/` and `importer/`, including `ImgurConfigScreen.kt`, `S3ConfigScreen.kt`, `FtpConfigScreen.kt`, `CustomHttpConfigScreen.kt`, `NativeDestinationConfigScreen.kt`, `ProfileManagementScreen.kt`, `ProfileEditorScreen.kt` and `UploaderImportScreen.kt`

- [ ] **Step 1:** Apply the seven rules from Task 15 to every screen in these folders.
- [ ] **Step 2:** Text fields: every `OutlinedTextField` gets `shape = RoundedCornerShape(18.dp)`. Credential fields keep their visual transformation. Do not change what is stored or where (SecureCredentialStore stays the only home for secrets).
- [ ] **Step 3:** In profile lists, each profile row shows a `HostChip(destination.displayName, destination.hostColor())` instead of a plain destination label. The default profile shows `Eyebrow("Default")` in `Lumen.tokens.ink`.
- [ ] **Step 4:** The "Test connection" and "Save" primary actions become `PillCta`. Keep their loading flags.
- [ ] **Step 5:** Build, check that each config screen opens and saves on the emulator, then commit:

```bash
git add feature/settings
git commit -m "Lumen destination and profile screens"
```

---

### Task 17: History, S3, capture, onboarding

**Files:**
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/HistoryScreen.kt`
- Modify: `feature/s3explorer/.../S3ExplorerScreen.kt`, `S3StatsScreen.kt`
- Modify: `feature/capture/.../CaptureScreen.kt` (only if it draws visible UI)
- Modify: `app/src/main/java/com/xerahs/android/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: HistoryScreen.** Apply the Task 15 rules. Grid and list items become `LumenCard(radius = 20.dp)` with the thumbnail clipped to 16dp and a `HostChip`. Filter and sort chips are pills as in Task 11 Step 4. Keep the search field with `shape = CircleShape`.
- [ ] **Step 2: S3 explorer.** This is a top-level tab: `LumenTopBar("Cloud")` with no back button and 112dp bottom padding. Folder and file rows sit on `LumenCard`s, keys and sizes use `monoStyle`, and the breadcrumb uses `Eyebrow`. `S3StatsScreen` uses `StatCard`, which is already restyled, plus `LumenTopBar`.
- [ ] **Step 3: Capture.** If `CaptureScreen` renders any visible chrome (buttons, a loading state), centre a `CircularProgressIndicator(color = primary)` and use `PillCta` for any visible action. If it only launches pickers, leave it.
- [ ] **Step 4: Onboarding.**
  - Use the background colour with `AccentGlow` top-end.
  - Each page title uses `displaySmall` with the last word in `Lumen.tokens.ink` via `buildAnnotatedString`.
  - Page icons use `IconTile(size = 72.dp)`.
  - The page indicator dots are 8dp. The active dot is a 24dp-wide `primary` pill and inactive dots are `Lumen.tokens.tint`.
  - Next and Get started use `PillCta`, Skip uses `TextButton`.
  - Any destination picker in onboarding uses the host-dot rows from Task 11 Step 9.
- [ ] **Step 5:** Build, then check on the emulator. To see onboarding, clear app data, then share an image into the app during onboarding to confirm the share still survives. Commit:

```bash
git add feature app/src/main/java/com/xerahs/android/ui/onboarding
git commit -m "Lumen history, cloud and onboarding"
```

---

### Task 18: Sweep, verify, release gate

**Files:** whatever the sweep finds.

- [ ] **Step 1: Find leftovers**

```bash
grep -rn "TopAppBar(\|LargeTopAppBar(\|MediumTopAppBar(\|[^a-zA-Z]Switch(\|BottomAppBar(\|FloatingActionButton(" --include='*.kt' app feature core | grep -v /build/
```

Expected: no matches, except:
- dialogs, where a `TopAppBar` inside a full-screen dialog is fine;
- the `Switch` inside `LumenSwitch`'s own file, if any.

Convert anything else using the Task 15 rules.

- [ ] **Step 2: BOM and lint**

```bash
grep -rlP '\x{FEFF}' --include='*.kt' . ; ./gradlew clean testDebugUnitTest lint assembleRelease
```

Expected: no BOM files and BUILD SUCCESSFUL. Fix any lint errors, such as missing `contentDescription`, unused resources or an obsolete-SDK check on the font.

- [ ] **Step 3: Release startup**

Install `app/build/outputs/apk/release/*.apk` on the emulator and launch it. Confirm:
- No crash on start.
- Fonts render (headings in Inter Tight).
- Theme changes in Appearance apply live.

R8 must not strip font resources; if headings fall back to the default font, add `-keep class **.R$font { *; }` to `app/proguard-rules.pro`.

- [ ] **Step 4: Screenshot matrix**

On `xerahs_pixel`, capture Home, Upload, Uploaded, Tools, Editor and Appearance in each of these configurations:
1. Light with Signal Lime.
2. Dark with Violet.
3. True black with Pink.
4. Light with Material You.

Save them to the scratchpad and review contrast by eye. Fix anything unreadable, then commit:

```bash
git add -A app feature core
git commit -m "Lumen sweep and fixes"
```
