# Editor Parity (Plan D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add speech balloon, sticker, smart eraser and highlighter pen tools plus an image effects panel to the editor.

**Architecture:** Effects are pure settings (`EffectSettings`) with pure color-matrix math in `core:common/image`, and Android bitmap operations in `ImageEffects`; the editor keeps effects separate from annotations and applies them at export. New tools follow the editor's per-shape pattern: an `Annotation` subtype, a renderer object in `canvas/shapes/`, and a branch in each exhaustive `when`.

**Tech Stack:** Kotlin, Android Canvas/ColorMatrix/StaticLayout, Compose M3, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-editor-tools-design.md` (part D and the shared `ImageEffects`).

**Changes vs spec:**
- The live canvas preview applies only the color adjustments, because border/shadow change the image size and annotations use bitmap coordinates. The effects sheet shows a thumbnail with every effect, and export applies all of them.
- Export already writes PNG, so `needsAlpha` is only used by the batch tool (plan E).
- The balloon tail is placed automatically (below the box, at 25% of its width). It is not draggable.
- Stickers keep their aspect ratio by drawing fit-center inside the dragged box.

**Environment (all gradle commands):**
```bash
cd /home/damnox/Documents/Playground/XerahS/XerahS-Android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/sdk ANDROID_SDK_ROOT=$HOME/Android/sdk
export PATH=$JAVA_HOME/bin:$PATH
```
Commit messages are plain, with no attribution trailers. Never `git add` PLAN.md, docs/plans/, graphify-out/. Never paste literal BOM characters. KDoc must not contain `/*`. Line numbers for existing files are approximate. `FA` = `feature/annotation/src/main/java/com/xerahs/android/feature/annotation`.

---

### Task 1: Effect settings and color math (core:common)

**Files:** Create `core/common/src/main/java/com/xerahs/android/core/common/image/{EffectSettings,ColorMatrices,WatermarkLayout,EdgeColor}.kt`; tests in `core/common/src/test/java/com/xerahs/android/core/common/image/`.

- [ ] **Step 1: Failing tests** (`ImageMathTest.kt`)

```kotlin
package com.xerahs.android.core.common.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun defaultSettingsAreIdentity() {
        assertTrue(EffectSettings().isIdentity)
        assertArrayEquals(ColorMatrices.identity(), ColorMatrices.forSettings(EffectSettings()), 0.0001f)
    }

    @Test fun invertFlipsWhiteToBlack() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(invert = true)), 0xFFFFFFFF.toInt())
        assertEquals(0, r(out)); assertEquals(0, g(out)); assertEquals(0, b(out))
    }

    @Test fun grayscaleEqualisesChannels() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(grayscale = true)), 0xFFFF0000.toInt())
        assertEquals(r(out), g(out)); assertEquals(g(out), b(out))
        assertEquals(54, r(out))
    }

    @Test fun brightnessClampsAt255() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(brightness = 1f)), 0xFF808080.toInt())
        assertEquals(255, r(out))
    }

    @Test fun watermarkCorners() {
        assertEquals(10f to 10f, WatermarkLayout.position(1000, 500, 200f, 40f, Corner.TOP_LEFT, 10f))
        assertEquals(790f to 450f, WatermarkLayout.position(1000, 500, 200f, 40f, Corner.BOTTOM_RIGHT, 10f))
    }

    @Test fun edgeColorAveragesBorderOnly() {
        val red = 0xFFFF0000.toInt(); val blue = 0xFF0000FF.toInt()
        val pixels = IntArray(9) { red }.also { it[4] = blue }
        assertEquals(red, EdgeColor.average(pixels, 3, 3))
    }
}
```

Run `./gradlew :core:common:testDebugUnitTest --tests '*ImageMathTest*'` → FAIL.

- [ ] **Step 2: Implement**

`EffectSettings.kt`:
```kotlin
package com.xerahs.android.core.common.image

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class Watermark(
    val text: String,
    val corner: Corner = Corner.BOTTOM_RIGHT,
    val opacity: Float = 0.6f,
    // Text height as a fraction of the image's shorter side.
    val sizeFraction: Float = 0.04f,
)

data class EffectSettings(
    val brightness: Float = 0f,      // -1..1
    val contrast: Float = 1f,        // 0..2
    val saturation: Float = 1f,      // 0..2
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val invert: Boolean = false,
    val borderWidth: Int = 0,
    val borderColor: Int = 0xFF000000.toInt(),
    val shadow: Boolean = false,
    val cornerRadius: Int = 0,
    val watermark: Watermark? = null,
) {
    val hasColorChange: Boolean
        get() = brightness != 0f || contrast != 1f || saturation != 1f || grayscale || sepia || invert

    val isIdentity: Boolean
        get() = !hasColorChange && borderWidth == 0 && !shadow && cornerRadius == 0 && watermark?.text.isNullOrBlank()
}
```

`ColorMatrices.kt` (4x5 Android color matrices, row-major):
```kotlin
package com.xerahs.android.core.common.image

object ColorMatrices {
    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    // Returns a matrix that applies [first] and then [then].
    fun concat(then: FloatArray, first: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += then[row * 5 + k] * first[k * 5 + col]
                if (col == 4) sum += then[row * 5 + 4]
                out[row * 5 + col] = sum
            }
        }
        return out
    }

    fun saturation(s: Float) = floatArrayOf(
        LR * (1 - s) + s, LG * (1 - s), LB * (1 - s), 0f, 0f,
        LR * (1 - s), LG * (1 - s) + s, LB * (1 - s), 0f, 0f,
        LR * (1 - s), LG * (1 - s), LB * (1 - s) + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun contrast(c: Float): FloatArray {
        val t = (1 - c) * 128f
        return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
    }

    fun brightness(b: Float): FloatArray {
        val o = b * 255f
        return floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f)
    }

    val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    val INVERT = floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)

    // Order: saturation, contrast, brightness, grayscale, sepia, invert.
    fun forSettings(s: EffectSettings): FloatArray {
        var m = identity()
        if (s.saturation != 1f) m = concat(saturation(s.saturation), m)
        if (s.contrast != 1f) m = concat(contrast(s.contrast), m)
        if (s.brightness != 0f) m = concat(brightness(s.brightness), m)
        if (s.grayscale) m = concat(saturation(0f), m)
        if (s.sepia) m = concat(SEPIA, m)
        if (s.invert) m = concat(INVERT, m)
        return m
    }

    fun applyToPixel(m: FloatArray, argb: Int): Int {
        val a = (argb ushr 24) and 0xFF; val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a + m[row * 5 + 4])
            .toInt().coerceIn(0, 255)
        return (ch(3) shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }
}
```

`WatermarkLayout.kt`:
```kotlin
package com.xerahs.android.core.common.image

object WatermarkLayout {
    // Top-left of the text box.
    fun position(imageW: Int, imageH: Int, textW: Float, textH: Float, corner: Corner, margin: Float): Pair<Float, Float> {
        val x = when (corner) { Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> margin; else -> imageW - textW - margin }
        val y = when (corner) { Corner.TOP_LEFT, Corner.TOP_RIGHT -> margin; else -> imageH - textH - margin }
        return x to y
    }
}
```

`EdgeColor.kt`:
```kotlin
package com.xerahs.android.core.common.image

object EdgeColor {
    // Average opaque colour of the pixels on the border of a width x height block.
    fun average(pixels: IntArray, width: Int, height: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until height) for (x in 0 until width) {
            if (x != 0 && y != 0 && x != width - 1 && y != height - 1) continue
            val c = pixels[y * width + x]
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        if (n == 0L) return 0xFFFFFFFF.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
```

Run → PASS (6). Commit: `git add core/common && git commit -m "feat(common): effect settings and colour math"`

---

### Task 2: `ImageEffects` bitmap operations (core:common)

**Files:** Create `core/common/src/main/java/com/xerahs/android/core/common/image/ImageEffects.kt`.

- [ ] **Step 1: Implement** (Android-only; verified on device in Task 5)

```kotlin
package com.xerahs.android.core.common.image

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

object ImageEffects {
    fun needsAlpha(s: EffectSettings) = s.cornerRadius > 0 || s.shadow

    fun applyColor(src: Bitmap, s: EffectSettings): Bitmap {
        if (!s.hasColorChange) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(ColorMatrices.forSettings(s))) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    // Order: colour, rounded corners, border, shadow, watermark. Never recycles [src].
    fun apply(src: Bitmap, s: EffectSettings): Bitmap {
        var bmp = applyColor(src, s)
        if (s.cornerRadius > 0) bmp = roundCorners(bmp, s.cornerRadius.toFloat())
        if (s.borderWidth > 0) bmp = addBorder(bmp, s.borderWidth, s.borderColor, s.cornerRadius.toFloat())
        if (s.shadow) bmp = addShadow(bmp)
        s.watermark?.takeIf { it.text.isNotBlank() }?.let { bmp = drawWatermark(bmp, it) }
        return bmp
    }

    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }

    private fun addBorder(src: Bitmap, width: Int, color: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width + 2 * width, src.height + 2 * width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val outer = if (radius > 0) radius + width else 0f
        canvas.drawRoundRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), outer, outer, paint)
        canvas.drawBitmap(src, width.toFloat(), width.toFloat(), null)
        return out
    }

    private fun addShadow(src: Bitmap): Bitmap {
        val pad = maxOf(8, minOf(src.width, src.height) / 40)
        val out = Bitmap.createBitmap(src.width + 2 * pad, src.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            setShadowLayer(pad * 0.6f, 0f, pad * 0.3f, 0x66000000)
        }
        canvas.drawRect(pad.toFloat(), pad.toFloat(), (pad + src.width).toFloat(), (pad + src.height).toFloat(), shadowPaint)
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), null)
        return out
    }

    private fun drawWatermark(src: Bitmap, wm: Watermark): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val size = (minOf(out.width, out.height) * wm.sizeFraction).coerceAtLeast(12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            alpha = (wm.opacity.coerceIn(0f, 1f) * 255).toInt()
            setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0x99000000.toInt())
        }
        val textW = paint.measureText(wm.text)
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent
        val (x, top) = WatermarkLayout.position(out.width, out.height, textW, textH, wm.corner, size * 0.6f)
        canvas.drawText(wm.text, x, top - fm.ascent, paint)
        return out
    }
}
```

- [ ] **Step 2: Verify and commit** — `./gradlew :core:common:assembleDebug` → PASS. `git add core/common && git commit -m "feat(common): bitmap effects (colour, corners, border, shadow, watermark)"`

---

### Task 3: New annotation types and renderers

**Files:** Modify `core/domain/.../model/Annotation.kt`; Create `$FA/canvas/shapes/{SpeechBalloonRenderer,StickerRenderer,SmartEraserRenderer,HighlighterPenRenderer}.kt`, `$FA/canvas/SmartEraserSampler.kt`; Modify `$FA/engine/AnnotationEngine.kt`, `$FA/canvas/AnnotationCanvas.kt`.

- [ ] **Step 1: Annotation subtypes** — append inside `sealed class Annotation` (same five overrides as the others):

```kotlin
    data class SpeechBalloon(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0xFF000000.toInt(),
        override val strokeWidth: Float = 3f,
        override val opacity: Float = 1f,
        val fillColor: Int = 0xFFFFFFFF.toInt(),
        val startX: Float, val startY: Float, val endX: Float, val endY: Float,
        val tailX: Float, val tailY: Float,
        val text: String,
        val fontSize: Float = 32f
    ) : Annotation()

    data class Sticker(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0x00000000,
        override val strokeWidth: Float = 0f,
        override val opacity: Float = 1f,
        val startX: Float, val startY: Float, val endX: Float, val endY: Float,
        val imagePath: String
    ) : Annotation()

    data class SmartEraser(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0x00000000,
        override val strokeWidth: Float = 0f,
        override val opacity: Float = 1f,
        val startX: Float, val startY: Float, val endX: Float, val endY: Float,
        val fillColor: Int
    ) : Annotation()

    data class HighlighterPen(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0xFFFFEB3B.toInt(),
        override val strokeWidth: Float = 24f,
        override val opacity: Float = 0.4f,
        val points: List<Pair<Float, Float>>
    ) : Annotation()
```

- [ ] **Step 2: Renderers**

`SpeechBalloonRenderer.kt`:
```kotlin
package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import com.xerahs.android.core.domain.model.Annotation

object SpeechBalloonRenderer {
    fun draw(canvas: Canvas, b: Annotation.SpeechBalloon) {
        val l = minOf(b.startX, b.endX); val t = minOf(b.startY, b.endY)
        val r = maxOf(b.startX, b.endX); val bottom = maxOf(b.startY, b.endY)
        val w = r - l; val h = bottom - t
        if (w < 4f || h < 4f) return
        val radius = minOf(w, h) * 0.2f
        val baseX = l + w * 0.2f
        val path = Path().apply {
            addRoundRect(RectF(l, t, r, bottom), radius, radius, Path.Direction.CW)
            moveTo(baseX, bottom - 1f)
            lineTo(b.tailX, b.tailY)
            lineTo(baseX + w * 0.15f, bottom - 1f)
            close()
        }
        val alpha = (b.opacity * 255).toInt()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.fillColor; this.alpha = alpha })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = b.strokeColor; style = Paint.Style.STROKE; strokeWidth = b.strokeWidth; this.alpha = alpha
        })
        val pad = minOf(w, h) * 0.12f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = b.strokeColor; textSize = b.fontSize; this.alpha = alpha }
        val layout = StaticLayout.Builder.obtain(b.text, 0, b.text.length, textPaint, maxOf(1, (w - 2 * pad).toInt())).build()
        canvas.save()
        canvas.clipRect(l, t, r, bottom)
        canvas.translate(l + pad, t + pad)
        layout.draw(canvas)
        canvas.restore()
    }
}
```

`StickerRenderer.kt`:
```kotlin
package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.xerahs.android.core.domain.model.Annotation

object StickerRenderer {
    private val cache = LruCache<String, Bitmap>(8)

    private fun load(path: String): Bitmap? = cache.get(path) ?: BitmapFactory.decodeFile(path)?.also { cache.put(path, it) }

    // Draws the sticker fit-center inside its box, keeping the aspect ratio.
    fun draw(canvas: Canvas, s: Annotation.Sticker) {
        val bmp = load(s.imagePath) ?: return
        val l = minOf(s.startX, s.endX); val t = minOf(s.startY, s.endY)
        val w = maxOf(s.startX, s.endX) - l; val h = maxOf(s.startY, s.endY) - t
        if (w < 2f || h < 2f) return
        val scale = minOf(w / bmp.width, h / bmp.height)
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val dst = RectF(l + (w - dw) / 2, t + (h - dh) / 2, l + (w + dw) / 2, t + (h + dh) / 2)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (s.opacity * 255).toInt() })
    }
}
```

`SmartEraserRenderer.kt`:
```kotlin
package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

object SmartEraserRenderer {
    fun draw(canvas: Canvas, e: Annotation.SmartEraser) {
        canvas.drawRect(
            minOf(e.startX, e.endX), minOf(e.startY, e.endY), maxOf(e.startX, e.endX), maxOf(e.startY, e.endY),
            Paint().apply { color = e.fillColor; style = Paint.Style.FILL }
        )
    }
}
```

`HighlighterPenRenderer.kt`:
```kotlin
package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.xerahs.android.core.domain.model.Annotation

object HighlighterPenRenderer {
    fun draw(canvas: Canvas, p: Annotation.HighlighterPen) {
        if (p.points.size < 2) return
        val path = Path().apply {
            moveTo(p.points[0].first, p.points[0].second)
            p.points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.strokeColor
            alpha = (p.opacity * 255).toInt()
            style = Paint.Style.STROKE
            strokeWidth = p.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
}
```

`SmartEraserSampler.kt`:
```kotlin
package com.xerahs.android.feature.annotation.canvas

import android.graphics.Bitmap
import com.xerahs.android.core.common.image.EdgeColor

object SmartEraserSampler {
    // Average colour along the edge of the rect (bitmap coordinates), clamped to the bitmap.
    fun sample(bitmap: Bitmap, startX: Float, startY: Float, endX: Float, endY: Float): Int {
        val l = minOf(startX, endX).toInt().coerceIn(0, bitmap.width - 1)
        val t = minOf(startY, endY).toInt().coerceIn(0, bitmap.height - 1)
        val r = maxOf(startX, endX).toInt().coerceIn(l + 1, bitmap.width)
        val b = maxOf(startY, endY).toInt().coerceIn(t + 1, bitmap.height)
        val w = r - l; val h = b - t
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, l, t, w, h)
        return EdgeColor.average(pixels, w, h)
    }
}
```

- [ ] **Step 3: Render and hit-test branches**
  - `AnnotationEngine.renderAnnotations` and `AnnotationCanvas.drawAnnotation`: add
    ```kotlin
                is Annotation.SpeechBalloon -> SpeechBalloonRenderer.draw(canvas, annotation)
                is Annotation.Sticker -> StickerRenderer.draw(canvas, annotation)
                is Annotation.SmartEraser -> SmartEraserRenderer.draw(canvas, annotation)
                is Annotation.HighlighterPen -> HighlighterPenRenderer.draw(canvas, annotation)
    ```
  - `AnnotationCanvas.hitTest`: SpeechBalloon, Sticker and SmartEraser use the same rect check as Highlight (`startX..endX`, `startY..endY`); HighlighterPen:
    ```kotlin
            is Annotation.HighlighterPen -> {
                val pad = annotation.strokeWidth
                annotation.points.isNotEmpty() &&
                    x in (annotation.points.minOf { it.first } - pad)..(annotation.points.maxOf { it.first } + pad) &&
                    y in (annotation.points.minOf { it.second } - pad)..(annotation.points.maxOf { it.second } + pad)
            }
    ```

- [ ] **Step 4: Verify and commit** — this task does not touch `AnnotationTool`, so only the `when (annotation)` blocks above need new branches: `./gradlew assembleDebug` → PASS. `git add core/domain feature/annotation && git commit -m "feat(editor): speech balloon, sticker, smart eraser and highlighter pen shapes"`

---

### Task 4: Tools in the editor UI

**Files:** Modify `$FA/AnnotationViewModel.kt`, `$FA/AnnotationScreen.kt`, `$FA/toolbar/AnnotationToolbar.kt`.

- [ ] **Step 1: Tool enum and state** — `AnnotationTool` gets `SPEECH_BALLOON, STICKER, SMART_ERASER, HIGHLIGHTER_PEN` appended. `AnnotationUiState` gets `val balloonText: String = "Note", val pendingStickerPath: String? = null`. Add `fun setBalloonText(text: String)` and `fun setStickerImage(path: String)` (simple copies).

- [ ] **Step 2: Creation in the ViewModel**
  - Change `addAnnotation(startX, startY, endX, endY)` to `addAnnotation(startX: Float, startY: Float, endX: Float, endY: Float, sampledColor: Int? = null)`.
  - Before the `when`, return early for `AnnotationTool.HIGHLIGHTER_PEN` (it uses the freehand path), and for `AnnotationTool.STICKER` when `state.pendingStickerPath == null`.
  - Add branches:
    ```kotlin
            AnnotationTool.SPEECH_BALLOON -> {
                val l = minOf(startX, endX); val r = maxOf(startX, endX); val b = maxOf(startY, endY)
                Annotation.SpeechBalloon(
                    id = generateId(), zIndex = state.annotations.size,
                    strokeColor = state.strokeColor, strokeWidth = state.strokeWidth,
                    startX = startX, startY = startY, endX = endX, endY = endY,
                    tailX = l + (r - l) * 0.25f, tailY = b + maxOf(24f, (b - minOf(startY, endY)) * 0.4f),
                    text = state.balloonText, fontSize = state.fontSize
                )
            }
            AnnotationTool.STICKER -> Annotation.Sticker(
                id = generateId(), zIndex = state.annotations.size,
                startX = startX, startY = startY, endX = endX, endY = endY,
                imagePath = state.pendingStickerPath!!
            )
            AnnotationTool.SMART_ERASER -> Annotation.SmartEraser(
                id = generateId(), zIndex = state.annotations.size,
                startX = startX, startY = startY, endX = endX, endY = endY,
                fillColor = sampledColor ?: 0xFFFFFFFF.toInt()
            )
    ```
  - In `addFreehandAnnotation(points)`, when `selectedTool == AnnotationTool.HIGHLIGHTER_PEN` create `Annotation.HighlighterPen(id = generateId(), zIndex = state.annotations.size, strokeColor = state.strokeColor, strokeWidth = maxOf(state.strokeWidth, 12f) * 3f, points = points)` instead of `Freehand`.

- [ ] **Step 3: Screen**
  - `ToolButtons` (and the legacy list in `AnnotationToolbar.kt`, kept in sync): append
    ```kotlin
        Triple(Icons.Default.ChatBubbleOutline, "Balloon", AnnotationTool.SPEECH_BALLOON),
        Triple(Icons.Default.EmojiEmotions, "Sticker", AnnotationTool.STICKER),
        Triple(Icons.Default.AutoFixOff, "Erase", AnnotationTool.SMART_ERASER),
        Triple(Icons.Default.BorderColor, "Marker", AnnotationTool.HIGHLIGHTER_PEN)
    ```
  - Everywhere the screen special-cases `AnnotationTool.FREEHAND` for drag handling (around lines 308–346), treat `AnnotationTool.HIGHLIGHTER_PEN` the same way.
  - Where the drag end calls `viewModel.addAnnotation(start.x, start.y, end.x, end.y)`, pass `sampledColor = if (uiState.selectedTool == AnnotationTool.SMART_ERASER) SmartEraserSampler.sample(bitmap, start.x, start.y, end.x, end.y) else null`.
  - Sticker picker:
    ```kotlin
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) coroutineScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "stickers").apply { mkdirs() }
                    val out = File(dir, "sticker_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.absolutePath
                }.getOrNull()
            }
            path?.let(viewModel::setStickerImage)
        }
    }
    ```
    When the Sticker tool button is selected and `uiState.pendingStickerPath == null`, launch `stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))`.
  - `ToolOptionsSheet` tool title: `SPEECH_BALLOON -> "Speech balloon"`, `STICKER -> "Sticker"`, `SMART_ERASER -> "Smart eraser"`, `HIGHLIGHTER_PEN -> "Highlighter pen"`.
  - Settings panel: hide the colour row for `SMART_ERASER` and `STICKER` (extend `showColor`). Branches:
    ```kotlin
            AnnotationTool.SPEECH_BALLOON -> {
                OutlinedTextField(value = uiState.balloonText, onValueChange = onBalloonTextChanged, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                LabeledSlider("Size", uiState.fontSize, 12f..96f, onFontSizeChanged)
            }
            AnnotationTool.STICKER -> {
                Button(onClick = onPickSticker) { Text(if (uiState.pendingStickerPath == null) "Choose image" else "Change image") }
            }
            AnnotationTool.SMART_ERASER -> {
                Text("Drag over text or UI on a plain background. The area is filled with the colour around it.", style = MaterialTheme.typography.bodySmall)
            }
            AnnotationTool.HIGHLIGHTER_PEN -> {
                LabeledSlider("Width", uiState.strokeWidth, 1f..20f, onStrokeWidthChanged)
            }
    ```
    Thread the new callbacks (`onBalloonTextChanged = viewModel::setBalloonText`, `onPickSticker = { stickerPicker.launch(...) }`, and the existing font-size / stroke-width setters by their actual names) through `ToolOptionsSheet`'s parameters.
  - `createInProgressAnnotation`: add
    ```kotlin
        AnnotationTool.SPEECH_BALLOON -> Annotation.SpeechBalloon(
            id = "in_progress", strokeColor = strokeColor, strokeWidth = strokeWidth,
            startX = start.x, startY = start.y, endX = current.x, endY = current.y,
            tailX = minOf(start.x, current.x) + kotlin.math.abs(current.x - start.x) * 0.25f,
            tailY = maxOf(start.y, current.y) + 24f, text = ""
        )
        AnnotationTool.STICKER -> null
        AnnotationTool.SMART_ERASER -> Annotation.SmartEraser(
            id = "in_progress", startX = start.x, startY = start.y, endX = current.x, endY = current.y,
            fillColor = 0x80808080.toInt()
        )
        AnnotationTool.HIGHLIGHTER_PEN -> null
    ```
    (Sticker has no drag preview; the box appears on release.)

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS. `git add feature/annotation && git commit -m "feat(editor): add balloon, sticker, smart eraser and highlighter tools"`

---

### Task 5: Effects panel

**Files:** Create `$FA/effects/EffectsSheet.kt`; Modify `AnnotationViewModel.kt`, `AnnotationScreen.kt`.

- [ ] **Step 1: ViewModel** — state `val effects: EffectSettings = EffectSettings()`; add
```kotlin
    fun updateEffects(change: (EffectSettings) -> EffectSettings) {
        _uiState.value = _uiState.value.copy(effects = change(_uiState.value.effects))
    }

    fun resetEffects() { _uiState.value = _uiState.value.copy(effects = EffectSettings()) }
```

- [ ] **Step 2: Sheet** (`EffectsSheet.kt`)

```kotlin
package com.xerahs.android.feature.annotation.effects

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.image.Corner
import com.xerahs.android.core.common.image.EffectSettings
import com.xerahs.android.core.common.image.ImageEffects
import com.xerahs.android.core.common.image.Watermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EffectsSheet(
    effects: EffectSettings,
    thumbnail: Bitmap,
    onChange: ((EffectSettings) -> EffectSettings) -> Unit,
    onPickBorderColor: () -> Unit,
    onReset: () -> Unit,
) {
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(effects, thumbnail) {
        delay(150)
        preview = withContext(Dispatchers.Default) { ImageEffects.apply(thumbnail, effects) }
    }
    Column(
        Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Effects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Reset") }
        }
        preview?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
        }
        Slider3("Brightness", effects.brightness, -1f..1f) { v -> onChange { it.copy(brightness = v) } }
        Slider3("Contrast", effects.contrast, 0f..2f) { v -> onChange { it.copy(contrast = v) } }
        Slider3("Saturation", effects.saturation, 0f..2f) { v -> onChange { it.copy(saturation = v) } }
        Toggle("Grayscale", effects.grayscale) { v -> onChange { it.copy(grayscale = v) } }
        Toggle("Sepia", effects.sepia) { v -> onChange { it.copy(sepia = v) } }
        Toggle("Invert", effects.invert) { v -> onChange { it.copy(invert = v) } }
        Toggle("Drop shadow", effects.shadow) { v -> onChange { it.copy(shadow = v) } }
        Slider3("Rounded corners", effects.cornerRadius.toFloat(), 0f..96f) { v -> onChange { it.copy(cornerRadius = v.toInt()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider3("Border", effects.borderWidth.toFloat(), 0f..64f, Modifier.weight(1f)) { v -> onChange { it.copy(borderWidth = v.toInt()) } }
            TextButton(onClick = onPickBorderColor) { Text("Colour") }
        }
        OutlinedTextField(
            value = effects.watermark?.text.orEmpty(),
            onValueChange = { text -> onChange { it.copy(watermark = if (text.isEmpty()) null else (it.watermark ?: Watermark("")).copy(text = text)) } },
            label = { Text("Watermark text") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        effects.watermark?.let { wm ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Corner.entries.forEach { c ->
                    FilterChip(selected = wm.corner == c, onClick = { onChange { it.copy(watermark = wm.copy(corner = c)) } },
                        label = { Text(c.name.lowercase().replace('_', ' ')) })
                }
            }
            Slider3("Watermark opacity", wm.opacity, 0.1f..1f) { v -> onChange { it.copy(watermark = wm.copy(opacity = v)) } }
        }
    }
}

@Composable
private fun Slider3(label: String, value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

- [ ] **Step 3: Screen wiring**
  - Top bar: before the crop button add `IconButton(onClick = { showEffects = true }) { Icon(Icons.Default.AutoFixHigh, contentDescription = "Effects") }`, with `var showEffects by remember { mutableStateOf(false) }` and `var showBorderColor by remember { mutableStateOf(false) }`.
  - Thumbnail for the sheet: `val effectsThumb = remember(bitmap) { val s = 512f / maxOf(bitmap.width, bitmap.height); if (s >= 1f) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true) }`.
  - Sheet (same `ModalBottomSheet` pattern as the OCR sheet): `if (showEffects) ModalBottomSheet(onDismissRequest = { showEffects = false }, …) { EffectsSheet(uiState.effects, effectsThumb, viewModel::updateEffects, onPickBorderColor = { showBorderColor = true }, onReset = viewModel::resetEffects) }`, and `if (showBorderColor) ColorPickerDialog(initialColor = uiState.effects.borderColor, onColorSelected = { c -> viewModel.updateEffects { it.copy(borderColor = c) }; showBorderColor = false }, onDismiss = { showBorderColor = false })`.
  - Live colour preview on the canvas:
    ```kotlin
    var displayBitmap by remember(bitmap) { mutableStateOf(bitmap) }
    LaunchedEffect(bitmap, uiState.effects) {
        delay(150)
        displayBitmap = withContext(Dispatchers.Default) { ImageEffects.applyColor(bitmap, uiState.effects) }
    }
    ```
    Pass `displayBitmap` to `AnnotationCanvas` instead of `bitmap` (same size, so annotation coordinates still match).
  - Export: after `val annotatedBitmap = AnnotationEngine.renderAnnotations(bitmap, uiState.annotations)` use
    ```kotlin
                                val finalBitmap = if (uiState.effects.isIdentity) annotatedBitmap
                                    else ImageEffects.apply(annotatedBitmap, uiState.effects)
    ```
    export `finalBitmap`, and recycle `annotatedBitmap` and `finalBitmap` (if different) after writing.

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS. `git add feature/annotation && git commit -m "feat(editor): effects panel with live preview"`

---

### Task 6: Verification (D)
- [ ] `./gradlew clean testDebugUnitTest lint assembleRelease` → PASS.
- [ ] Emulator: draw each new tool (balloon text wraps, sticker picked from gallery, smart eraser matches the background, highlighter is translucent); open Effects, move sliders (canvas colour updates, thumbnail shows border/shadow/corners/watermark); export and check the uploaded file shows all effects with transparent rounded corners.
