# Editor Parity and Tools (Sub-project D+E)

Date: 2026-10-02. Branch `feature/editor-tools`, stacked on `feature/after-upload` (PR #4). Two plans: D (editor) then E (tools).

## Shared: `ImageEffects` (core:common)
- `data class EffectSettings(brightness: Float = 0f /* -1..1 */, contrast: Float = 1f /* 0..2 */, saturation: Float = 1f /* 0..2 */, grayscale: Boolean, sepia: Boolean, invert: Boolean, borderWidth: Int = 0, borderColor: Int, shadow: Boolean, cornerRadius: Int = 0, watermark: Watermark? = null)`
- `data class Watermark(text: String, corner: Corner = BOTTOM_RIGHT, opacity: Float = 0.6f, sizeFraction: Float = 0.04f)`
- `object ImageEffects`:
  - `colorMatrix(settings): FloatArray(20)`: a pure function, unit tested.
  - `apply(bitmap, settings): Bitmap`. Order: color matrix, rounded corners, border, shadow, watermark.
  - `needsAlpha(settings)`: true when there are corners or a shadow, which means PNG output.
- `WatermarkLayout.position(imageW, imageH, textW, textH, corner, margin)`: pure, unit tested.
- `EffectSettings.isIdentity`: when true, export skips effects entirely.

## D. Editor (feature:annotation)
New `Annotation` subtypes, each with a renderer in `canvas/shapes/` and a case in every exhaustive `when` (export, live canvas, hit test, creation, tool title, settings panel, preview), following the existing per-shape pattern:
- `SpeechBalloon(rect, tail: Point, text, color, fill, fontSize)`: rounded rect plus a triangular tail toward `tail`, with the text wrapped inside. Created by dragging; the tail starts below the box and can be dragged.
- `Sticker(rect, imagePath)`: drawn scaled into the rect. The photo picker copies the image into `files/stickers/`. Corner handles keep the aspect ratio.
- `SmartEraser(rect, fillColor)`: on creation `fillColor` is the average of the source bitmap's pixels along the rect border (`EdgeColor.average(pixels)`, pure, unit tested), then the rect is filled with it.
- `HighlighterPen(points, color, strokeWidth = 24f)`: freehand drawn at about 40% alpha with a round cap.
- Toolbar: the four tools are added to the existing tool drawer.

Effects panel:
- An "Effects" button opens a bottom sheet with sliders (brightness, contrast, saturation), switches (grayscale, sepia, invert, shadow), border width and color (reusing `ColorPickerDialog`), a corner radius slider, and watermark fields (text, corner, opacity).
- `AnnotationViewModel` holds `effects: EffectSettings`. The preview applies `ImageEffects` to a downscaled copy, debounced by 150 ms. Export applies annotations first, then effects, and saves a PNG when `needsAlpha`.
- Undo/redo covers annotations only; effects have a "Reset" button.

## E. Tools (new module feature:tools)
Module `feature/tools` depends on core:common, core:domain and core:ui only. `app` adds the navigation routes and a "Tools" icon in Home's bottom bar (next to Home and Cloud) that opens `ToolsScreen`, a grid of four tools:
1. **Resize / convert / watermark.** Pick images (photo picker, multiple). Options: max dimension (Original, 3840, 1920, 1280, 1080), format (Original, PNG, JPEG, WebP), quality (JPEG/WebP), optional watermark (`Watermark`). `BatchImageProcessor` writes to cache, then saves to `Pictures/XerahS` through MediaStore. Results screen: "Saved N images" with "Upload" (opens the existing batch upload with the cached files via an `onUpload(paths)` callback wired in `app`).
2. **Hash checker.** Pick any file (SAF). `FileHasher` gains `computeAll(file)` returning MD5, SHA-1 and SHA-256 (streaming, unit tested with known vectors). A "Compare" field shows Match or No match (case-insensitive, trimmed). Each hash has a copy button.
3. **QR decode.** Pick an image. `QrDecoder.decode(pixels, width, height)` (core:common, ZXing `MultiFormatReader` with TRY_HARDER) returns the text or null. Shows the text with Copy and, for http(s), Open. Unit tested by decoding a matrix made by `QrGenerator`.
4. **Color picker.** Pick an image, shown fit to the screen. Tapping maps to a bitmap pixel and shows a swatch with HEX (`#RRGGBB`) and `rgb(r, g, b)`, plus copy. The last 10 colors are kept in DataStore (`recent_colors`, comma-separated hex). `ColorFormat.hex/rgb` is pure and unit tested.

All bitmap work runs on `Dispatchers.Default`, with images downsampled for display (max 2048 px). Errors (unreadable file, no QR found) show inline.

## Testing
- Unit: `ImageEffects.colorMatrix` (identity, grayscale, invert), `WatermarkLayout`, `EdgeColor.average`, `FileHasher.computeAll` (empty input and "abc" vectors), `QrDecoder` round trip, `ColorFormat`.
- Emulator: each new annotation, effects preview and export (PNG when rounded), each tool end to end, batch result upload. Release gate as before.

## Non-goals
The other ~140 upstream effects, GIF maker, video tools, image combiner/splitter, screen color picker.
