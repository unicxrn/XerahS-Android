# Uploader Interop — Part 1: Custom Uploader Engine & Import — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single "Custom HTTP" config with a full ShareX-compatible custom uploader (`.sxcu` model + syntax engine), and let users import `.sxcu` and `.xsdc` files as upload profiles.

**Architecture:** Pure-Kotlin model, parser/writer and `ShareXSyntax` engine live in `core:common/sxcu` (JVM-testable, Gson only). An OkHttp-based `CustomUploaderClient` in `core:data` executes a spec; `feature:upload`'s `CustomHttpUploader` and the URL shortener delegate to it. Configs are stored as `.sxcu` JSON text in `SecureCredentialStore` (global + per-profile), with lazy in-place conversion of legacy `custom_http_*` keys. Import goes through a domain `UploaderImportRepository` (impl in `core:data`, bound in `app/di`) and a new Import screen in `feature:settings`.

**Tech Stack:** Kotlin 1.9.22, Gson 2.10.1, OkHttp 4.12.0 (+ MockWebServer for tests), javax.crypto (PBKDF2/AES-GCM), javax.xml.xpath, Jetpack Compose M3, Hilt/KSP, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-uploader-interop-design.md` (sections 1, 2 "Import", 3). Part 2 (`…-part2.md`) covers any-file upload, HEIC, Room v4.

**Refinements vs spec (decided while planning):**
- `ShareXSyntax` + spec model live in `core:common` (not `core:domain`): `core:common` already has Gson + JUnit, and `core:domain` depends on it. `core:domain` switches to `api(project(":core:common"))` so the model type is visible downstream.
- Legacy `CustomHttpConfig` is converted **in place** (global stays global, each profile stays that profile) on first read, instead of being turned into a new profile — no behaviour change for users, no DataStore flag needed (conversion deletes the legacy keys, so it is naturally idempotent).
- Blank `URL` template → `{response}` for every destination type (ShareX behaviour), not `{responseurl}`.
- Upstream `.xsdc` currently only ever contains `amazons3` entries (`DestinationConfigExportService.BuildPayload`). We map `amazons3` (AccessKeys auth) and report any other provider as skipped.
- Editing a custom uploader in-app is done as `.sxcu` JSON text (validated on save), in both the global screen and the profile editor. A field-by-field form is out of scope.
- Adds ShareX's `{input}` function (text body for text uploaders / long URL for URL shorteners) — needed for URL-shortener profiles.

---

## File structure

**Create — `core/common/src/main/java/com/xerahs/android/core/common/`**
- `sxcu/CustomUploaderSpec.kt` — model + enums (`CustomDestinationType`, `CustomBodyType`)
- `sxcu/ShareXSyntax.kt` — template engine, `SyntaxContext`, `SyntaxResponse`, `InputPrompt`, `SyntaxEvaluationException`
- `sxcu/SyntaxSelectors.kt` — `JsonPath`, `XmlPath`, `RegexSelect` (internal)
- `sxcu/LegacySyntax.kt` — `$json:…$` → `{json:…}` converter (internal)
- `sxcu/SxcuWriter.kt` — spec → `.sxcu` JSON
- `sxcu/LegacyCustomHttp.kt` — old CustomHttpConfig fields → spec
- `file/MimeTypes.kt` — extension ↔ MIME helpers (used by client now, by Part 2 later)

**Modify — core:common:** `sxcu/SxcuParser.kt` (rewrite), tests under `core/common/src/test/.../sxcu/` and `.../file/`.

**Create — `core/data/src/main/java/com/xerahs/android/core/data/`**
- `remote/custom/CustomUploaderClient.kt` — executes a spec (`CustomUploadInput`, `CustomUploadOutcome`)
- `remote/custom/CustomRequestBuilder.kt` — spec → OkHttp `Request` (internal)
- `remote/custom/CustomResponseParser.kt` — response → outcome (internal)
- `importer/XsdcDecoder.kt` — decrypt `.xsdc`
- `importer/UploaderImportParsing.kt` — detect file kind, map to `ImportDraft`s
- `repository/UploaderImportRepositoryImpl.kt`

**Create — core:domain:** `model/UploaderImport.kt` (`UploaderFileKind`, `ImportDraft`, `XsdcImportResult`, `INSECURE_HTTP_WARNING`), `repository/UploaderImportRepository.kt`.

**Create — feature:settings:** `importer/UploaderImportViewModel.kt`, `importer/UploaderImportScreen.kt`.

**Modify:** `gradle/libs.versions.toml`, `core/domain/build.gradle.kts`, `core/data/build.gradle.kts`, `UploadConfig.kt`, `HistoryItem.kt` (displayName only), `SettingsRepository.kt`, `SettingsRepositoryImpl.kt`, `SettingsDataStore.kt`, `SecureCredentialStore.kt`, `UrlShortenerRepositoryImpl.kt`, `CustomHttpUploader.kt`, `UploadWorker.kt`, `UploadViewModel.kt`, `UploadScreen.kt`, `SettingsViewModel.kt`, `ExportImportManager.kt`, `ProfileManagementViewModel.kt`, `ProfileEditorScreen.kt`, `CustomHttpConfigViewModel.kt`, `CustomHttpConfigScreen.kt` (rewrite), `UploadSettingsScreen.kt`, `app/.../di/AppModule.kt`, `NavGraph.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/proguard-rules.pro` (verify only).

**Commands used throughout** (run from repo root):
- Unit tests for a module: `./gradlew :core:common:testDebugUnitTest`, `./gradlew :core:data:testDebugUnitTest`
- Compile everything: `./gradlew assembleDebug`

---

### Task 1: Build setup

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/domain/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`

- [ ] **Step 1: Add MockWebServer to the version catalog**

In `gradle/libs.versions.toml`, under `[libraries]` directly after the `okhttp-logging = …` line, add:

```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

- [ ] **Step 2: Expose core:common through core:domain and add JUnit**

In `core/domain/build.gradle.kts` replace `implementation(project(":core:common"))` with:

```kotlin
    api(project(":core:common"))
```

and add inside `dependencies { … }`:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 3: Add MockWebServer to core:data tests**

In `core/data/build.gradle.kts`, after `testImplementation(libs.junit)` add:

```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 4: Verify the build still compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml core/domain/build.gradle.kts core/data/build.gradle.kts
git commit -m "build: add mockwebserver, expose core:common via core:domain"
```

---

### Task 2: Custom uploader model + MimeTypes

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/CustomUploaderSpec.kt`
- Create: `core/common/src/main/java/com/xerahs/android/core/common/file/MimeTypes.kt`
- Test: `core/common/src/test/java/com/xerahs/android/core/common/file/MimeTypesTest.kt`

- [ ] **Step 1: Write the failing MimeTypes test**

```kotlin
package com.xerahs.android.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypesTest {
    @Test fun mapsKnownExtensionsCaseInsensitively() {
        assertEquals("image/png", MimeTypes.fromFileName("Shot.PNG"))
        assertEquals("image/jpeg", MimeTypes.fromFileName("a.jpeg"))
        assertEquals("application/pdf", MimeTypes.fromFileName("doc.pdf"))
        assertEquals("text/plain", MimeTypes.fromFileName("notes.txt"))
    }

    @Test fun unknownOrMissingExtensionIsOctetStream() {
        assertEquals(MimeTypes.OCTET_STREAM, MimeTypes.fromFileName("archive.xyz"))
        assertEquals(MimeTypes.OCTET_STREAM, MimeTypes.fromFileName("README"))
    }

    @Test fun extensionForPrefersCanonicalExtension() {
        assertEquals("jpg", MimeTypes.extensionFor("image/jpeg"))
        assertEquals("txt", MimeTypes.extensionFor("text/plain"))
        assertEquals(null, MimeTypes.extensionFor("application/x-unknown"))
    }

    @Test fun classifiesImagesAndText() {
        assertTrue(MimeTypes.isRasterImage("image/png"))
        assertTrue(MimeTypes.isRasterImage("image/*"))
        assertFalse(MimeTypes.isRasterImage("image/svg+xml"))
        assertFalse(MimeTypes.isRasterImage("video/mp4"))
        assertTrue(MimeTypes.isHeic("image/heic"))
        assertTrue(MimeTypes.isText("application/json"))
        assertTrue(MimeTypes.isText("text/markdown"))
        assertFalse(MimeTypes.isText("application/pdf"))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*MimeTypesTest*'`
Expected: FAIL — compilation error `Unresolved reference: MimeTypes`

- [ ] **Step 3: Implement MimeTypes**

```kotlin
package com.xerahs.android.core.common.file

/** Extension-based MIME helpers. Pure Kotlin so it is unit-testable (no android.webkit). */
object MimeTypes {
    const val OCTET_STREAM = "application/octet-stream"

    // Order matters: the first extension listed for a MIME type is its canonical one.
    private val byExtension = linkedMapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "mp3" to "audio/mpeg", "ogg" to "audio/ogg",
        "wav" to "audio/wav", "m4a" to "audio/mp4",
        "txt" to "text/plain", "log" to "text/plain", "md" to "text/markdown",
        "csv" to "text/csv", "html" to "text/html", "json" to "application/json",
        "xml" to "application/xml", "pdf" to "application/pdf", "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive",
    )

    fun fromFileName(name: String): String =
        byExtension[name.substringAfterLast('.', "").lowercase()] ?: OCTET_STREAM

    fun extensionFor(mimeType: String): String? =
        byExtension.entries.firstOrNull { it.value == mimeType }?.key

    fun isRasterImage(mimeType: String?): Boolean =
        mimeType != null && mimeType.startsWith("image/") && mimeType != "image/svg+xml"

    fun isHeic(mimeType: String?): Boolean = mimeType == "image/heic" || mimeType == "image/heif"

    fun isText(mimeType: String?): Boolean = mimeType != null &&
        (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml")
}
```

- [ ] **Step 4: Create the spec model** (no test of its own — exercised by later tasks)

```kotlin
package com.xerahs.android.core.common.sxcu

enum class CustomDestinationType(val sxcuName: String) {
    IMAGE("ImageUploader"),
    TEXT("TextUploader"),
    FILE("FileUploader"),
    URL_SHORTENER("URLShortener");

    companion object {
        fun fromSxcu(name: String): CustomDestinationType? =
            entries.firstOrNull { it.sxcuName.equals(name.trim(), ignoreCase = true) }
    }
}

enum class CustomBodyType(val sxcuName: String) {
    NONE("None"),
    MULTIPART_FORM_DATA("MultipartFormData"),
    FORM_URL_ENCODED("FormURLEncoded"),
    JSON("JSON"),
    XML("XML"),
    BINARY("Binary");

    companion object {
        fun fromSxcu(name: String): CustomBodyType? =
            entries.firstOrNull { it.sxcuName.equals(name.trim(), ignoreCase = true) }
    }
}

/** Full ShareX custom uploader (.sxcu) definition. Template fields may contain ShareX syntax. */
data class CustomUploaderSpec(
    val name: String = "Custom uploader",
    val destinationTypes: Set<CustomDestinationType> =
        setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE),
    val requestMethod: String = "POST",
    val requestURL: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: CustomBodyType = CustomBodyType.MULTIPART_FORM_DATA,
    val arguments: Map<String, String> = emptyMap(),
    val fileFormName: String = "file",
    val data: String = "",
    val url: String = "",
    val thumbnailURL: String = "",
    val deletionURL: String = "",
    val errorMessage: String = "",
) {
    /** Templates evaluated before the request is sent (where {inputbox} prompts can appear). */
    fun requestTemplates(): List<String> =
        listOf(requestURL, data) + parameters.values + headers.values + arguments.values
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*MimeTypesTest*'`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/sxcu/CustomUploaderSpec.kt core/common/src/main/java/com/xerahs/android/core/common/file/MimeTypes.kt core/common/src/test/java/com/xerahs/android/core/common/file/MimeTypesTest.kt
git commit -m "feat(common): add custom uploader spec model and MimeTypes"
```

---

### Task 3: ShareXSyntax engine — parsing, escaping, request-side functions

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/ShareXSyntax.kt`
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/SyntaxSelectors.kt` (stubbed here, filled in Task 4)
- Test: `core/common/src/test/java/com/xerahs/android/core/common/sxcu/ShareXSyntaxInputTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShareXSyntaxInputTest {
    private val ctx = SyntaxContext(fileName = "shot.png", input = "hello", random = Random(7))

    @Test fun plainTextPassesThrough() =
        assertEquals("https://x.test/upload", ShareXSyntax.evaluate("https://x.test/upload", ctx))

    @Test fun filenameAndInput() =
        assertEquals("shot.png:hello", ShareXSyntax.evaluate("{filename}:{input}", ctx))

    @Test fun functionNamesAreCaseInsensitive() =
        assertEquals("shot.png", ShareXSyntax.evaluate("{FileName}", ctx))

    @Test fun escapedBracesAndPipesAreLiteral() =
        assertEquals("{a|b}", ShareXSyntax.evaluate("""\{a\|b\}""", ctx))

    @Test fun backslashBeforeOrdinaryCharIsKept() =
        assertEquals("""\d+""", ShareXSyntax.evaluate("""\d+""", ctx))

    @Test fun randomPicksOneOption() =
        assertTrue(ShareXSyntax.evaluate("{random:a|b|c}", ctx) in setOf("a", "b", "c"))

    @Test fun selectIsNonInteractiveFirstOption() =
        assertEquals("x", ShareXSyntax.evaluate("{select:x|y}", ctx))

    @Test fun inputboxUsesSuppliedValueElseDefault() {
        val withValue = ctx.copy(inputValues = mapOf("Token" to "abc"))
        assertEquals("abc", ShareXSyntax.evaluate("{inputbox:Token|def}", withValue))
        assertEquals("def", ShareXSyntax.evaluate("{inputbox:Token|def}", ctx))
    }

    @Test fun base64KeepsColonsInArgument() =
        assertEquals("dXNlcjpwYXNz", ShareXSyntax.evaluate("{base64:user:pass}", ctx))

    @Test fun nestedCallsEvaluateInsideOut() =
        assertEquals("c2hvdC5wbmc=", ShareXSyntax.evaluate("{base64:{filename}}", ctx))

    @Test fun unknownFunctionBracesStayLiteral() =
        assertEquals("{nope}", ShareXSyntax.evaluate("{nope}", ctx))

    @Test fun rawJsonBodyKeepsBracesAndEvaluatesInnerCalls() =
        assertEquals("""{"url":"hello"}""", ShareXSyntax.evaluate("""{"url":"{input}"}""", ctx))

    @Test fun unbalancedBraceStaysLiteral() =
        assertEquals("{filename", ShareXSyntax.evaluate("{filename", ctx))

    @Test(expected = SyntaxEvaluationException::class)
    fun responseFunctionWithoutResponseFails() { ShareXSyntax.evaluate("{response}", ctx) }

    @Test fun encoderAppliesOnlyToTopLevelResults() {
        val jsonCtx = ctx.copy(input = "say \"hi\"", resultEncoder = { it.replace("\"", "\\\"") })
        assertEquals("""{"text":"say \"hi\""}""",
            ShareXSyntax.evaluate("""\{"text":"{input}"\}""", jsonCtx))
    }

    @Test fun collectsInputPromptsFromTemplates() {
        val prompts = ShareXSyntax.inputPrompts(
            listOf("https://x.test/{inputbox:Album|main}", "Bearer {inputbox:Token}", "{inputbox:Token}")
        )
        assertEquals(listOf(InputPrompt("Album", "main"), InputPrompt("Token", "")), prompts)
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*ShareXSyntaxInputTest*'`
Expected: FAIL — `Unresolved reference: SyntaxContext`

- [ ] **Step 3: Create the selector stubs** (`SyntaxSelectors.kt`, real bodies in Task 4)

```kotlin
package com.xerahs.android.core.common.sxcu

internal object JsonPath {
    fun select(json: String, path: String): String? = null
}

internal object XmlPath {
    fun select(xml: String, expression: String): String? = null
}

internal object RegexSelect {
    fun select(input: String, pattern: String, group: String?): String? = null
}
```

- [ ] **Step 4: Implement the engine** (`ShareXSyntax.kt`)

```kotlin
package com.xerahs.android.core.common.sxcu

import java.util.Base64
import kotlin.random.Random

class SyntaxEvaluationException(val expression: String, reason: String) :
    Exception("Couldn't evaluate {$expression}: $reason")

data class SyntaxResponse(
    val body: String,
    val url: String,
    val headers: Map<String, List<String>> = emptyMap(),
)

data class InputPrompt(val title: String, val default: String)

data class SyntaxContext(
    val fileName: String = "",
    /** Text body for text uploaders, or the long URL for URL shorteners. */
    val input: String = "",
    /** Values for {inputbox:Title}, keyed by title. */
    val inputValues: Map<String, String> = emptyMap(),
    val response: SyntaxResponse? = null,
    val random: Random = Random.Default,
    /** Applied to each top-level function result (e.g. JSON-escaping inside a JSON body). */
    val resultEncoder: (String) -> String = { it },
    internal val promptCollector: MutableList<InputPrompt>? = null,
)

/**
 * ShareX custom-uploader syntax: `{name}` / `{name:arg1|arg2}`, nestable, with `\` escaping
 * `{ } | \`. A backslash before any other character is kept literally (so regex `\d` survives).
 * Braces that don't start a known function (e.g. a raw JSON body) or never close are kept literally.
 */
object ShareXSyntax {
    private val ESCAPABLE = setOf('{', '}', '|', '\\')
    private val FUNCTIONS = setOf(
        "filename", "input", "random", "select", "inputbox", "base64",
        "response", "responseurl", "header", "json", "xml", "regex",
    )

    fun evaluate(template: String, ctx: SyntaxContext): String = render(template, ctx, topLevel = true)

    /** Lists every {inputbox} prompt in [templates] (deduplicated by title, first wins). */
    fun inputPrompts(templates: List<String>): List<InputPrompt> {
        val collected = mutableListOf<InputPrompt>()
        val ctx = SyntaxContext(promptCollector = collected)
        templates.forEach { runCatching { evaluate(it, ctx) } }
        return collected.distinctBy { it.title }
    }

    private fun render(template: String, ctx: SyntaxContext, topLevel: Boolean): String {
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            when {
                c == '\\' && i + 1 < template.length && template[i + 1] in ESCAPABLE -> {
                    out.append(template[i + 1]); i += 2
                }
                c == '{' -> {
                    val end = findClosing(template, i)
                    val inner = end?.let { template.substring(i + 1, it) }
                    if (inner == null || functionName(inner) !in FUNCTIONS) {
                        out.append(c); i++ // literal brace; keep scanning inside it
                    } else {
                        val result = call(inner, ctx)
                        out.append(if (topLevel) ctx.resultEncoder(result) else result)
                        i = end + 1
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun functionName(inner: String): String =
        (if (inner.indexOf(':') < 0) inner else inner.substring(0, inner.indexOf(':'))).trim().lowercase()

    private fun findClosing(s: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '\\' -> if (i + 1 < s.length && s[i + 1] in ESCAPABLE) i++
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    /** Splits on top-level unescaped '|', leaving escapes and nested calls untouched. */
    private fun splitArgs(raw: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length && raw[i + 1] in ESCAPABLE) {
                cur.append(c).append(raw[i + 1]); i += 2; continue
            }
            if (c == '{') depth++
            if (c == '}') depth--
            if (c == '|' && depth == 0) {
                parts.add(cur.toString()); cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        parts.add(cur.toString())
        return parts
    }

    private fun call(inner: String, ctx: SyntaxContext): String {
        val colon = inner.indexOf(':')
        val name = functionName(inner)
        val args = if (colon < 0) emptyList()
        else splitArgs(inner.substring(colon + 1)).map { render(it, ctx, topLevel = false) }

        return when (name) {
            "filename" -> ctx.fileName
            "input" -> ctx.input
            "random" -> args.ifEmpty { fail(inner, "needs at least one option") }.random(ctx.random)
            "select" -> args.firstOrNull() ?: fail(inner, "needs at least one option")
            "inputbox" -> {
                val title = args.getOrElse(0) { "" }
                val default = args.getOrElse(1) { "" }
                ctx.promptCollector?.add(InputPrompt(title, default))
                ctx.inputValues[title] ?: default
            }
            "base64" -> Base64.getEncoder()
                .encodeToString(args.getOrElse(0) { "" }.toByteArray(Charsets.UTF_8))
            "response" -> response(inner, ctx).body
            "responseurl" -> response(inner, ctx).url
            "header" -> {
                val key = args.getOrElse(0) { fail(inner, "needs a header name") }
                response(inner, ctx).headers.entries
                    .firstOrNull { it.key.equals(key, ignoreCase = true) }
                    ?.value?.firstOrNull() ?: fail(inner, "header not present in response")
            }
            "json" -> {
                val (input, path) = inputAndPath(inner, args, ctx)
                JsonPath.select(input, path) ?: fail(inner, "path not found in response")
            }
            "xml" -> {
                val (input, path) = inputAndPath(inner, args, ctx)
                XmlPath.select(input, path) ?: fail(inner, "XPath matched nothing in response")
            }
            "regex" -> {
                val pattern = args.getOrElse(0) { fail(inner, "needs a pattern") }
                RegexSelect.select(response(inner, ctx).body, pattern, args.getOrNull(1))
                    ?: fail(inner, "no match in response")
            }
            else -> fail(inner, "unknown function '$name'")
        }
    }

    private fun inputAndPath(expr: String, args: List<String>, ctx: SyntaxContext): Pair<String, String> =
        when (args.size) {
            0 -> fail(expr, "needs a path")
            1 -> response(expr, ctx).body to args[0]
            else -> args[0] to args[1]
        }

    private fun response(expr: String, ctx: SyntaxContext): SyntaxResponse =
        ctx.response ?: fail(expr, "only available after the upload response")

    private fun fail(expr: String, reason: String): Nothing = throw SyntaxEvaluationException(expr, reason)
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*ShareXSyntaxInputTest*'`
Expected: PASS (16 tests)

- [ ] **Step 6: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/sxcu/ShareXSyntax.kt core/common/src/main/java/com/xerahs/android/core/common/sxcu/SyntaxSelectors.kt core/common/src/test/java/com/xerahs/android/core/common/sxcu/ShareXSyntaxInputTest.kt
git commit -m "feat(common): add ShareX syntax engine with request-side functions"
```

---

### Task 4: ShareXSyntax response functions (json / xml / regex / header)

**Files:**
- Modify: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/SyntaxSelectors.kt`
- Test: `core/common/src/test/java/com/xerahs/android/core/common/sxcu/ShareXSyntaxResponseTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareXSyntaxResponseTest {
    private fun ctx(body: String, headers: Map<String, List<String>> = emptyMap()) = SyntaxContext(
        response = SyntaxResponse(body = body, url = "https://api.test/upload", headers = headers)
    )
    private val json = """{"data":{"link":"https://i.test/a.png","id":"a"},"files":[{"url":"https://f.test/1"}],"ok":true}"""

    @Test fun responseAndResponseUrl() {
        assertEquals("raw", ShareXSyntax.evaluate("{response}", ctx("raw")))
        assertEquals("https://api.test/upload", ShareXSyntax.evaluate("{responseurl}", ctx("raw")))
    }

    @Test fun headerLookupIsCaseInsensitive() =
        assertEquals("https://l.test", ShareXSyntax.evaluate("{header:location}",
            ctx("", mapOf("Location" to listOf("https://l.test")))))

    @Test fun jsonDotPath() = assertEquals("https://i.test/a.png", ShareXSyntax.evaluate("{json:data.link}", ctx(json)))
    @Test fun jsonArrayIndex() = assertEquals("https://f.test/1", ShareXSyntax.evaluate("{json:files[0].url}", ctx(json)))
    @Test fun jsonDollarPrefix() = assertEquals("a", ShareXSyntax.evaluate("{json:$.data.id}", ctx(json)))
    @Test fun jsonBoolean() = assertEquals("true", ShareXSyntax.evaluate("{json:ok}", ctx(json)))
    @Test fun jsonTemplateComposition() =
        assertEquals("https://cdn.test/a.png", ShareXSyntax.evaluate("https://cdn.test/{json:data.id}.png", ctx(json)))
    @Test fun jsonTwoArgFormUsesFirstArgAsInput() =
        assertEquals("a", ShareXSyntax.evaluate("{json:{response}|data.id}", ctx(json)))

    @Test(expected = SyntaxEvaluationException::class)
    fun jsonMissingPathFails() { ShareXSyntax.evaluate("{json:data.nope}", ctx(json)) }

    @Test fun xpath() = assertEquals("https://x.test/1",
        ShareXSyntax.evaluate("{xml:/rsp/url}", ctx("<rsp><url>https://x.test/1</url></rsp>")))

    @Test fun regexWholeMatchNumberedAndNamedGroups() {
        val c = ctx("""Uploaded: https://r.test/abc ok""")
        assertEquals("https://r.test/abc", ShareXSyntax.evaluate("""{regex:https://\S+}""", c))
        assertEquals("abc", ShareXSyntax.evaluate("""{regex:https://r\.test/(\w+)|1}""", c))
        assertEquals("abc", ShareXSyntax.evaluate("""{regex:https://r\.test/(?<id>\w+)|id}""", c))
    }

    @Test(expected = SyntaxEvaluationException::class)
    fun regexNoMatchFails() { ShareXSyntax.evaluate("{regex:zzz}", ctx("abc")) }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*ShareXSyntaxResponseTest*'`
Expected: FAIL — json/xml/regex tests throw `SyntaxEvaluationException` (stubs return null); response/header tests pass.

- [ ] **Step 3: Implement the selectors** (replace the whole of `SyntaxSelectors.kt`)

```kotlin
package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

/** Minimal JSONPath: `a.b`, `a[0].b`, `$.a`, `a['key with dots']`. */
internal object JsonPath {
    private val TOKEN = Regex("""\[(\d+)]|\[['"]([^'"]+)['"]]|([^.\[\]]+)""")

    fun select(json: String, path: String): String? = try {
        var el: JsonElement = JsonParser.parseString(json)
        val p = path.trim().removePrefix("$").removePrefix(".")
        for (m in TOKEN.findAll(p)) {
            val index = m.groupValues[1]
            el = if (index.isNotEmpty()) {
                val arr = el as? JsonArray ?: return null
                val i = index.toInt()
                if (i >= arr.size()) return null
                arr[i]
            } else {
                val key = m.groupValues[2].ifEmpty { m.groupValues[3] }
                (el as? JsonObject)?.get(key) ?: return null
            }
        }
        when {
            el.isJsonNull -> null
            el.isJsonPrimitive -> el.asString
            else -> el.toString()
        }
    } catch (e: Exception) {
        null
    }
}

internal object XmlPath {
    fun select(xml: String, expression: String): String? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        XPathFactory.newInstance().newXPath().evaluate(expression, doc).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}

internal object RegexSelect {
    fun select(input: String, pattern: String, group: String?): String? {
        val match = Regex(pattern).find(input) ?: return null
        if (group.isNullOrBlank()) return match.value
        group.trim().toIntOrNull()?.let { return match.groups[it]?.value }
        return (match.groups as? MatchNamedGroupCollection)?.get(group.trim())?.value
    }
}
```

- [ ] **Step 4: Run all syntax tests**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*ShareXSyntax*'`
Expected: PASS (all tests in both classes)

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/sxcu/SyntaxSelectors.kt core/common/src/test/java/com/xerahs/android/core/common/sxcu/ShareXSyntaxResponseTest.kt
git commit -m "feat(common): add json/xml/regex response selectors to ShareX syntax"
```

---

### Task 5: SxcuParser rewrite, legacy syntax, SxcuWriter

**Files:**
- Modify (rewrite): `core/common/src/main/java/com/xerahs/android/core/common/sxcu/SxcuParser.kt`
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/LegacySyntax.kt`
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/SxcuWriter.kt`
- Test (rewrite): `core/common/src/test/java/com/xerahs/android/core/common/sxcu/SxcuParserTest.kt`
- Create fixtures: `core/common/src/test/resources/sxcu/*.sxcu`

Note: the current `SxcuParser` returns a lossy `SxcuConfig` used by `CustomHttpConfigScreen`. That screen is rewritten in Task 8. Until then, `CustomHttpConfigScreen.kt` will not compile — so this task only runs `:core:common` tests, and Task 8 restores the full build. Do not run `assembleDebug` between Task 5 and Task 8.

- [ ] **Step 1: Add fixtures** (real-world shapes)

`core/common/src/test/resources/sxcu/multipart_image.sxcu`:
```json
{
  "Version": "13.7.0",
  "Name": "My Host",
  "DestinationType": "ImageUploader, FileUploader",
  "RequestMethod": "POST",
  "RequestURL": "https://up.example.com/api/upload",
  "Headers": { "Authorization": "Bearer abc123" },
  "Body": "MultipartFormData",
  "Arguments": { "album": "{inputbox:Album|main}" },
  "FileFormName": "image",
  "URL": "{json:data.url}",
  "ThumbnailURL": "{json:data.thumb}",
  "DeletionURL": "{json:data.delete}",
  "ErrorMessage": "{json:error.message}"
}
```

`core/common/src/test/resources/sxcu/text_urlencoded.sxcu`:
```json
{
  "Name": "Paste",
  "DestinationType": "TextUploader",
  "RequestMethod": "POST",
  "RequestURL": "https://paste.example.com/api",
  "Body": "FormURLEncoded",
  "Arguments": { "content": "{input}", "format": "text" },
  "URL": "{response}"
}
```

`core/common/src/test/resources/sxcu/shortener_json.sxcu`:
```json
{
  "Name": "Short",
  "DestinationType": "URLShortener",
  "RequestMethod": "POST",
  "RequestURL": "https://s.example.com/shorten",
  "Body": "JSON",
  "Data": "{\"url\":\"{input}\"}",
  "URL": "{json:short}"
}
```

`core/common/src/test/resources/sxcu/binary_put.sxcu`:
```json
{
  "name": "Binary PUT",
  "destinationtype": "FileUploader",
  "requestmethod": "put",
  "requesturl": "https://b.example.com/{filename}",
  "parameters": { "ttl": "3600" },
  "body": "Binary",
  "url": "https://b.example.com/{filename}"
}
```

`core/common/src/test/resources/sxcu/legacy_v12.sxcu`:
```json
{
  "Name": "Old Host",
  "DestinationType": "ImageUploader",
  "RequestType": "POST",
  "RequestURL": "https://old.example.com/upload.php",
  "FileFormName": "file",
  "Arguments": { "key": "k1" },
  "ResponseType": "Text",
  "RegexList": [ "\"url\":\"(.+?)\"" ],
  "URL": "$regex:1,1$",
  "DeletionURL": "$json:delete$"
}
```

- [ ] **Step 2: Write the failing tests** (replace the whole of `SxcuParserTest.kt`)

```kotlin
package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SxcuParserTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("sxcu/$name")!!.readText()

    @Test fun parsesMultipartImageUploader() {
        val spec = SxcuParser.parse(fixture("multipart_image.sxcu")).getOrThrow()
        assertEquals("My Host", spec.name)
        assertEquals(setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE), spec.destinationTypes)
        assertEquals("POST", spec.requestMethod)
        assertEquals("https://up.example.com/api/upload", spec.requestURL)
        assertEquals(mapOf("Authorization" to "Bearer abc123"), spec.headers)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals(mapOf("album" to "{inputbox:Album|main}"), spec.arguments)
        assertEquals("image", spec.fileFormName)
        assertEquals("{json:data.url}", spec.url)
        assertEquals("{json:data.thumb}", spec.thumbnailURL)
        assertEquals("{json:data.delete}", spec.deletionURL)
        assertEquals("{json:error.message}", spec.errorMessage)
    }

    @Test fun parsesTextUploader() {
        val spec = SxcuParser.parse(fixture("text_urlencoded.sxcu")).getOrThrow()
        assertEquals(setOf(CustomDestinationType.TEXT), spec.destinationTypes)
        assertEquals(CustomBodyType.FORM_URL_ENCODED, spec.body)
        assertEquals("", spec.fileFormName)
    }

    @Test fun parsesJsonShortener() {
        val spec = SxcuParser.parse(fixture("shortener_json.sxcu")).getOrThrow()
        assertEquals(setOf(CustomDestinationType.URL_SHORTENER), spec.destinationTypes)
        assertEquals(CustomBodyType.JSON, spec.body)
        assertEquals("""{"url":"{input}"}""", spec.data)
    }

    @Test fun keysAreCaseInsensitiveAndMethodUppercased() {
        val spec = SxcuParser.parse(fixture("binary_put.sxcu")).getOrThrow()
        assertEquals("Binary PUT", spec.name)
        assertEquals("PUT", spec.requestMethod)
        assertEquals(CustomBodyType.BINARY, spec.body)
        assertEquals(mapOf("ttl" to "3600"), spec.parameters)
    }

    @Test fun convertsLegacyRequestTypeAndSyntax() {
        val spec = SxcuParser.parse(fixture("legacy_v12.sxcu")).getOrThrow()
        assertEquals("POST", spec.requestMethod)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals("""{regex:"url":"(.+?)"|1}""", spec.url)
        assertEquals("{json:delete}", spec.deletionURL)
    }

    @Test fun nameFallsBackToHost() {
        val spec = SxcuParser.parse("""{"RequestURL":"https://h.example.com/u"}""").getOrThrow()
        assertEquals("h.example.com", spec.name)
        assertEquals(setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE), spec.destinationTypes)
        assertEquals(CustomBodyType.NONE, spec.body)
    }

    @Test fun rejectsInvalidInput() {
        assertTrue(SxcuParser.parse("not json").isFailure)
        assertTrue(SxcuParser.parse("""{"Name":"no url"}""").isFailure)
        assertTrue(SxcuParser.parse("""{"RequestURL":"https://x","Body":"Carrier pigeon"}""").isFailure)
    }

    @Test fun writerRoundTrips() {
        listOf("multipart_image.sxcu", "text_urlencoded.sxcu", "shortener_json.sxcu", "binary_put.sxcu").forEach {
            val spec = SxcuParser.parse(fixture(it)).getOrThrow()
            assertEquals(it, spec, SxcuParser.parse(SxcuWriter.write(spec)).getOrThrow())
        }
    }

    @Test fun writerKeepsEmptyRequestUrlParseable() {
        val spec = CustomUploaderSpec(requestURL = "")
        assertEquals(spec, SxcuParser.parse(SxcuWriter.write(spec)).getOrThrow())
    }
}
```

- [ ] **Step 3: Run them to make sure they fail**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*SxcuParserTest*'`
Expected: FAIL — compile errors (`getOrThrow()` returns `SxcuConfig`, `SxcuWriter` unresolved)

- [ ] **Step 4: Implement LegacySyntax**

```kotlin
package com.xerahs.android.core.common.sxcu

/** Converts pre-13 ShareX `$name:arg$` syntax to `{name:arg}`. */
internal object LegacySyntax {
    private val TOKEN = Regex(
        """\$(json|xml|regex|responseurl|response|header|filename|random|select|inputbox|base64)(?::([^$]*))?\$""",
        RegexOption.IGNORE_CASE
    )

    fun convert(template: String, regexList: List<String>): String = TOKEN.replace(template) { m ->
        val name = m.groupValues[1].lowercase()
        val arg = m.groupValues[2]
        when {
            name == "regex" -> {
                val parts = arg.split(',')
                val index = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val group = parts.getOrNull(1)?.trim().orEmpty()
                val pattern = index?.let { regexList.getOrNull(it - 1) } ?: return@replace m.value
                "{regex:" + escape(pattern) + (if (group.isNotEmpty()) "|$group" else "") + "}"
            }
            arg.isEmpty() -> "{$name}"
            else -> "{$name:$arg}"
        }
    }

    private fun escape(s: String) = buildString {
        s.forEach { c -> if (c == '{' || c == '}' || c == '|') append('\\'); append(c) }
    }
}
```

- [ ] **Step 5: Rewrite SxcuParser** (replace the whole file)

```kotlin
package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI

class SxcuFormatException(message: String) : Exception(message)

/** Parses ShareX .sxcu JSON (any version, case-insensitive keys) into a [CustomUploaderSpec]. */
object SxcuParser {
    fun parse(text: String): Result<CustomUploaderSpec> = runCatching {
        val element = try {
            JsonParser.parseString(text.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            throw SxcuFormatException("Not valid JSON")
        }
        if (!element.isJsonObject) throw SxcuFormatException("Not a ShareX custom uploader")
        val root = element.asJsonObject

        fun field(key: String): JsonElement? = root.entrySet()
            .firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.takeIf { !it.isJsonNull }
        fun str(key: String): String? = field(key)?.takeIf { it.isJsonPrimitive }?.asString
        fun map(key: String): Map<String, String> = field(key)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()?.associate { (k, v) -> k to (if (v.isJsonPrimitive) v.asString else v.toString()) }
            .orEmpty()

        val regexList = field("RegexList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
        fun tpl(value: String) = LegacySyntax.convert(value, regexList)

        val requestUrl = str("RequestURL") ?: throw SxcuFormatException("Missing RequestURL")
        val method = (str("RequestMethod") ?: str("RequestType") ?: "POST").trim().uppercase()
        val fileFormName = str("FileFormName").orEmpty()
        val arguments = map("Arguments")
        val body = str("Body")?.let {
            CustomBodyType.fromSxcu(it) ?: throw SxcuFormatException("Unsupported body type: $it")
        } ?: when {
            fileFormName.isNotEmpty() -> CustomBodyType.MULTIPART_FORM_DATA
            arguments.isNotEmpty() && method != "GET" -> CustomBodyType.FORM_URL_ENCODED
            else -> CustomBodyType.NONE
        }
        val types = str("DestinationType")?.split(',')
            ?.mapNotNull { CustomDestinationType.fromSxcu(it) }?.toSet().orEmpty()

        CustomUploaderSpec(
            name = str("Name")?.takeIf { it.isNotBlank() } ?: hostOf(requestUrl) ?: "Custom uploader",
            destinationTypes = types.ifEmpty { setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE) },
            requestMethod = method,
            requestURL = tpl(requestUrl),
            parameters = map("Parameters").mapValues { tpl(it.value) },
            headers = map("Headers").mapValues { tpl(it.value) },
            body = body,
            arguments = arguments.mapValues { tpl(it.value) },
            fileFormName = fileFormName,
            data = str("Data")?.let(::tpl).orEmpty(),
            url = str("URL")?.let(::tpl).orEmpty(),
            thumbnailURL = str("ThumbnailURL")?.let(::tpl).orEmpty(),
            deletionURL = str("DeletionURL")?.let(::tpl).orEmpty(),
            errorMessage = str("ErrorMessage")?.let(::tpl).orEmpty(),
        )
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
```

- [ ] **Step 6: Implement SxcuWriter**

```kotlin
package com.xerahs.android.core.common.sxcu

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

/** Serialises a [CustomUploaderSpec] to ShareX .sxcu JSON (also our storage format). */
object SxcuWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun write(spec: CustomUploaderSpec): String {
        val o = JsonObject()
        o.addProperty("Version", "17.0.0")
        o.addProperty("Name", spec.name)
        o.addProperty(
            "DestinationType",
            spec.destinationTypes.sortedBy { it.ordinal }.joinToString(", ") { it.sxcuName }
        )
        o.addProperty("RequestMethod", spec.requestMethod)
        o.addProperty("RequestURL", spec.requestURL)
        if (spec.parameters.isNotEmpty()) o.add("Parameters", spec.parameters.toJson())
        if (spec.headers.isNotEmpty()) o.add("Headers", spec.headers.toJson())
        o.addProperty("Body", spec.body.sxcuName)
        if (spec.arguments.isNotEmpty()) o.add("Arguments", spec.arguments.toJson())
        if (spec.fileFormName.isNotEmpty()) o.addProperty("FileFormName", spec.fileFormName)
        if (spec.data.isNotEmpty()) o.addProperty("Data", spec.data)
        listOf(
            "URL" to spec.url, "ThumbnailURL" to spec.thumbnailURL,
            "DeletionURL" to spec.deletionURL, "ErrorMessage" to spec.errorMessage,
        ).filter { it.second.isNotEmpty() }.forEach { (k, v) -> o.addProperty(k, v) }
        return gson.toJson(o)
    }

    private fun Map<String, String>.toJson() = JsonObject().also { o -> forEach { (k, v) -> o.addProperty(k, v) } }
}
```

Note: `writerKeepsEmptyRequestUrlParseable` relies on `Name` = "Custom uploader" (default) being written — the parser only falls back to the host when `Name` is blank.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*SxcuParserTest*'`
Expected: PASS (9 tests)

- [ ] **Step 8: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/sxcu/ core/common/src/test/java/com/xerahs/android/core/common/sxcu/SxcuParserTest.kt core/common/src/test/resources/sxcu/
git commit -m "feat(common): full .sxcu parser with legacy syntax support and writer"
```

---

### Task 6: Legacy Custom HTTP converter

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/sxcu/LegacyCustomHttp.kt`
- Test: `core/common/src/test/java/com/xerahs/android/core/common/sxcu/LegacyCustomHttpTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCustomHttpTest {
    @Test fun mapsOldFieldsToMultipartSpec() {
        val spec = LegacyCustomHttp.toSpec(
            url = "https://h.test/up", method = "put",
            headers = mapOf("X-Key" to "1"), responseUrlJsonPath = "data.url", formFieldName = "img"
        )
        assertEquals("Custom HTTP", spec.name)
        assertEquals("PUT", spec.requestMethod)
        assertEquals("https://h.test/up", spec.requestURL)
        assertEquals(mapOf("X-Key" to "1"), spec.headers)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals("img", spec.fileFormName)
        assertEquals("{json:data.url}", spec.url)
    }

    @Test fun blankValuesFallBackToDefaults() {
        val spec = LegacyCustomHttp.toSpec("https://h.test", "", emptyMap(), "", "")
        assertEquals("POST", spec.requestMethod)
        assertEquals("file", spec.fileFormName)
        assertEquals("", spec.url)
    }

    @Test fun parsesHeaderLines() = assertEquals(
        mapOf("A" to "1", "B" to "x=y"),
        LegacyCustomHttp.parseHeaderLines("A=1\nnot a header\nB=x=y")
    )
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*LegacyCustomHttpTest*'`
Expected: FAIL — `Unresolved reference: LegacyCustomHttp`

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.common.sxcu

/** Converts the pre-0.5 single "Custom HTTP" config into a [CustomUploaderSpec]. */
object LegacyCustomHttp {
    fun toSpec(
        url: String,
        method: String,
        headers: Map<String, String>,
        responseUrlJsonPath: String,
        formFieldName: String,
    ) = CustomUploaderSpec(
        name = "Custom HTTP",
        destinationTypes = setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE),
        requestMethod = method.ifBlank { "POST" }.uppercase(),
        requestURL = url,
        headers = headers,
        body = CustomBodyType.MULTIPART_FORM_DATA,
        fileFormName = formFieldName.ifBlank { "file" },
        url = if (responseUrlJsonPath.isBlank()) "" else "{json:${responseUrlJsonPath.trim()}}",
    )

    /** Old storage format: `key=value` per line. */
    fun parseHeaderLines(text: String): Map<String, String> = text.lines()
        .filter { it.contains('=') }
        .associate { line -> line.split('=', limit = 2).let { (k, v) -> k.trim() to v } }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:common:testDebugUnitTest`
Expected: PASS (all core:common tests)

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/sxcu/LegacyCustomHttp.kt core/common/src/test/java/com/xerahs/android/core/common/sxcu/LegacyCustomHttpTest.kt
git commit -m "feat(common): convert legacy custom HTTP config to sxcu spec"
```

---

### Task 7: CustomUploaderClient (core:data)

**Files:**
- Create: `core/data/src/main/java/com/xerahs/android/core/data/remote/custom/CustomRequestBuilder.kt`
- Create: `core/data/src/main/java/com/xerahs/android/core/data/remote/custom/CustomResponseParser.kt`
- Create: `core/data/src/main/java/com/xerahs/android/core/data/remote/custom/CustomUploaderClient.kt`
- Test: `core/data/src/test/java/com/xerahs/android/core/data/remote/custom/CustomUploaderClientTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomBodyType
import com.xerahs.android.core.common.sxcu.CustomDestinationType
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomUploaderClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val client = CustomUploaderClient(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun file(name: String = "shot.png", content: String = "PNGDATA") =
        tmp.newFile(name).apply { writeText(content) }

    @Test fun multipartUploadParsesJsonUrlsAndSendsArgsHeadersParams() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"url":"https://i.test/a.png","del":"https://i.test/d/a"}}"""))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/up").toString(),
            parameters = mapOf("ttl" to "60"),
            headers = mapOf("Authorization" to "Bearer {inputbox:Token}"),
            arguments = mapOf("name" to "{filename}"),
            fileFormName = "image",
            url = "{json:data.url}",
            deletionURL = "{json:data.del}",
        )
        val outcome = client.execute(spec, CustomUploadInput(file(), "shot.png", inputValues = mapOf("Token" to "t1")))

        assertEquals(CustomUploadOutcome.Success("https://i.test/a.png", null, "https://i.test/d/a"), outcome)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("60", req.requestUrl!!.queryParameter("ttl"))
        assertEquals("Bearer t1", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"shot.png\""))
        assertTrue(body.contains("Content-Type: image/png"))
        assertTrue(body.contains("name=\"name\"") && body.contains("shot.png"))
        assertTrue(body.contains("PNGDATA"))
    }

    @Test fun formUrlEncodedTextUploaderUsesInput() = runBlocking {
        server.enqueue(MockResponse().setBody("https://p.test/xyz"))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/paste").toString(),
            destinationTypes = setOf(CustomDestinationType.TEXT),
            body = CustomBodyType.FORM_URL_ENCODED,
            arguments = mapOf("content" to "{input}"),
            fileFormName = "",
        )
        val outcome = client.execute(spec, CustomUploadInput(null, "note.txt", input = "a b&c"))
        assertEquals(CustomUploadOutcome.Success("https://p.test/xyz", null, null), outcome)
        assertEquals("content=a%20b%26c", server.takeRequest().body.readUtf8())
    }

    @Test fun jsonBodyEscapesInput() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"short":"https://s.test/1"}"""))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/s").toString(),
            body = CustomBodyType.JSON,
            data = """{"url":"{input}"}""",
            url = "{json:short}",
        )
        client.execute(spec, CustomUploadInput(null, "", input = "https://x.test/?q=\"a\""))
        val req = server.takeRequest()
        assertEquals("""{"url":"https://x.test/?q=\"a\""}""", req.body.readUtf8())
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test fun binaryPutSendsRawFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val spec = CustomUploaderSpec(
            requestMethod = "PUT",
            requestURL = server.url("/b/").toString() + "{filename}",
            body = CustomBodyType.BINARY,
            url = "https://b.test/{filename}",
        )
        val outcome = client.execute(spec, CustomUploadInput(file("doc.pdf", "PDF"), "doc.pdf"))
        assertEquals(CustomUploadOutcome.Success("https://b.test/doc.pdf", null, null), outcome)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/b/doc.pdf", req.path)
        assertEquals("PDF", req.body.readUtf8())
        assertEquals("application/pdf", req.getHeader("Content-Type"))
    }

    @Test fun httpErrorUsesErrorMessageTemplate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Bad key"}}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), errorMessage = "{json:error.message}")
        assertEquals(CustomUploadOutcome.Failure("Bad key"), client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun httpErrorWithoutTemplateReportsCodeAndBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString())
        assertEquals(CustomUploadOutcome.Failure("HTTP 500: boom"), client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun missingJsonPathIsAFailureNotAnEmptyUrl() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"other":1}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), url = "{json:data.link}")
        val outcome = client.execute(spec, CustomUploadInput(file(), "shot.png"))
        assertTrue(outcome is CustomUploadOutcome.Failure)
        assertTrue((outcome as CustomUploadOutcome.Failure).message.contains("json:data.link"))
    }

    @Test fun blankUrlTemplateFallsBackToResponseBody() = runBlocking {
        server.enqueue(MockResponse().setBody("  https://r.test/1\n"))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString())
        assertEquals(CustomUploadOutcome.Success("https://r.test/1", null, null),
            client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun brokenThumbnailTemplateDoesNotFailUpload() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"url":"https://i.test/1"}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), url = "{json:url}", thumbnailURL = "{json:thumb}")
        assertEquals(CustomUploadOutcome.Success("https://i.test/1", null, null),
            client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*CustomUploaderClientTest*'`
Expected: FAIL — `Unresolved reference: CustomUploaderClient`

- [ ] **Step 3: Implement CustomRequestBuilder**

```kotlin
package com.xerahs.android.core.data.remote.custom

import com.google.gson.JsonPrimitive
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.sxcu.CustomBodyType
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SyntaxContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

internal object CustomRequestBuilder {
    private val BODY_REQUIRED = setOf("POST", "PUT", "PATCH")

    fun build(spec: CustomUploaderSpec, file: File?, fileName: String, ctx: SyntaxContext): Request {
        fun eval(t: String) = ShareXSyntax.evaluate(t, ctx)

        val url = eval(spec.requestURL).trim().toHttpUrl().newBuilder().apply {
            spec.parameters.forEach { (k, v) -> addQueryParameter(k, eval(v)) }
        }.build()
        val builder = Request.Builder().url(url)
        spec.headers.forEach { (k, v) -> builder.header(k, eval(v)) }

        val args = spec.arguments.mapValues { eval(it.value) }
        val fileType = MimeTypes.fromFileName(fileName).toMediaType()
        val body: RequestBody? = when (spec.body) {
            CustomBodyType.NONE -> null
            CustomBodyType.MULTIPART_FORM_DATA -> {
                val attachFile = file != null && spec.fileFormName.isNotBlank()
                if (args.isEmpty() && !attachFile) null
                else MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                    args.forEach { (k, v) -> addFormDataPart(k, v) }
                    if (attachFile) addFormDataPart(spec.fileFormName, fileName, file!!.asRequestBody(fileType))
                }.build()
            }
            CustomBodyType.FORM_URL_ENCODED ->
                FormBody.Builder().apply { args.forEach { (k, v) -> add(k, v) } }.build()
            CustomBodyType.JSON -> ShareXSyntax
                .evaluate(spec.data, ctx.copy(resultEncoder = ::jsonEscape))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            CustomBodyType.XML -> ShareXSyntax
                .evaluate(spec.data, ctx.copy(resultEncoder = ::xmlEscape))
                .toRequestBody("application/xml; charset=utf-8".toMediaType())
            CustomBodyType.BINARY ->
                (file ?: throw IllegalArgumentException("Binary body needs a file")).asRequestBody(fileType)
        }

        val method = spec.requestMethod.trim().uppercase()
        builder.method(method, body ?: if (method in BODY_REQUIRED) ByteArray(0).toRequestBody(null) else null)
        return builder.build()
    }

    private fun jsonEscape(s: String): String = JsonPrimitive(s).toString().let { it.substring(1, it.length - 1) }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
```

Note: `JsonElement.toString()` uses a non-HTML-safe `JsonWriter`, so only `"`, `\` and control characters are escaped — exactly what the `jsonBodyEscapesInput` test expects.

- [ ] **Step 4: Implement CustomResponseParser**

```kotlin
package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.common.sxcu.SyntaxResponse

internal object CustomResponseParser {
    fun parse(spec: CustomUploaderSpec, code: Int, response: SyntaxResponse, ctx: SyntaxContext): CustomUploadOutcome {
        val rctx = ctx.copy(response = response)
        if (code !in 200..299) {
            val templated = spec.errorMessage.takeIf { it.isNotBlank() }
                ?.let { runCatching { ShareXSyntax.evaluate(it, rctx) }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
            return CustomUploadOutcome.Failure(templated ?: "HTTP $code: ${response.body.take(300)}")
        }
        val url = ShareXSyntax.evaluate(spec.url.ifBlank { "{response}" }, rctx).trim()
        if (url.isEmpty()) return CustomUploadOutcome.Failure("The uploader returned an empty URL")
        return CustomUploadOutcome.Success(
            url = url,
            thumbnailUrl = optional(spec.thumbnailURL, rctx),
            deletionUrl = optional(spec.deletionURL, rctx),
        )
    }

    private fun optional(template: String, ctx: SyntaxContext): String? =
        template.takeIf { it.isNotBlank() }
            ?.let { runCatching { ShareXSyntax.evaluate(it, ctx).trim() }.getOrNull() }
            ?.ifBlank { null }
}
```

- [ ] **Step 5: Implement CustomUploaderClient**

```kotlin
package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.common.sxcu.SyntaxEvaluationException
import com.xerahs.android.core.common.sxcu.SyntaxResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class CustomUploadInput(
    val file: File?,
    val fileName: String,
    /** Text content (text uploaders) or long URL (URL shorteners); exposed as {input}. */
    val input: String = "",
    /** {inputbox:Title} answers keyed by title. */
    val inputValues: Map<String, String> = emptyMap(),
)

sealed interface CustomUploadOutcome {
    data class Success(val url: String, val thumbnailUrl: String?, val deletionUrl: String?) : CustomUploadOutcome
    data class Failure(val message: String) : CustomUploadOutcome
}

/** Executes a ShareX-style custom uploader request. Never throws; failures are [CustomUploadOutcome.Failure]. */
@Singleton
class CustomUploaderClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun execute(spec: CustomUploaderSpec, input: CustomUploadInput): CustomUploadOutcome =
        withContext(Dispatchers.IO) {
            try {
                val ctx = SyntaxContext(fileName = input.fileName, input = input.input, inputValues = input.inputValues)
                val request = CustomRequestBuilder.build(spec, input.file, input.fileName, ctx)
                okHttpClient.newCall(request).execute().use { resp ->
                    val response = SyntaxResponse(
                        body = resp.body?.string().orEmpty(),
                        url = resp.request.url.toString(),
                        headers = resp.headers.toMultimap(),
                    )
                    CustomResponseParser.parse(spec, resp.code, response, ctx)
                }
            } catch (e: SyntaxEvaluationException) {
                CustomUploadOutcome.Failure(e.message ?: "Custom uploader syntax error")
            } catch (e: IOException) {
                CustomUploadOutcome.Failure("Network error: ${e.message}")
            } catch (e: IllegalArgumentException) {
                CustomUploadOutcome.Failure("Invalid request: ${e.message}")
            }
        }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*CustomUploaderClientTest*'`
Expected: PASS (9 tests). If `multipartUploadParsesJsonUrlsAndSendsArgsHeadersParams` fails on the `Content-Type: image/png` assertion, check that `MimeTypes.fromFileName` is used with `fileName` (not `file.name`).

- [ ] **Step 7: Commit**

```bash
git add core/data/src/main/java/com/xerahs/android/core/data/remote/custom/ core/data/src/test/java/com/xerahs/android/core/data/remote/custom/
git commit -m "feat(data): add ShareX-compatible CustomUploaderClient"
```

---

### Task 8: Switch the app to CustomUploaderConfig

Replaces `UploadConfig.CustomHttpConfig` everywhere. The build is broken from Task 5 until the end of this task; finish all steps before compiling.

**Files:**
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/model/UploadConfig.kt`
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/model/HistoryItem.kt` (displayName)
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/repository/SettingsRepository.kt:60-61`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/repository/SettingsRepositoryImpl.kt:122-126`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/datastore/SecureCredentialStore.kt:140-168, 214-231, 276-283`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/uploader/CustomHttpUploader.kt` (rewrite)
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/worker/UploadWorker.kt:185-189`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/SettingsViewModel.kt:167`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/ExportImportManager.kt:72-82, 176-190, 299-311, 410-424`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/profiles/ProfileManagementViewModel.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/profiles/ProfileEditorScreen.kt:516-559`
- Modify (rewrite): `feature/settings/src/main/java/com/xerahs/android/feature/settings/destinations/CustomHttpConfigViewModel.kt`
- Modify (rewrite): `feature/settings/src/main/java/com/xerahs/android/feature/settings/destinations/CustomHttpConfigScreen.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/UploadSettingsScreen.kt:228-233` (labels)

- [ ] **Step 1: Domain model**

In `UploadConfig.kt` replace the whole `data class CustomHttpConfig(...) : UploadConfig()` block with:

```kotlin
    /** ShareX-compatible custom uploader; stored as .sxcu JSON. */
    data class CustomUploaderConfig(
        val spec: CustomUploaderSpec = CustomUploaderSpec()
    ) : UploadConfig()
```

and add `import com.xerahs.android.core.common.sxcu.CustomUploaderSpec` under the package line.

In `HistoryItem.kt` change `CUSTOM_HTTP("Custom HTTP"),` to `CUSTOM_HTTP("Custom uploader"),` (enum name unchanged — it is persisted in Room and DataStore).

- [ ] **Step 2: Repository API**

`SettingsRepository.kt` lines 60-61 become:

```kotlin
    suspend fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig
    suspend fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig)
```

`SettingsRepositoryImpl.kt` lines 122-126 become:

```kotlin
    override suspend fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig =
        secureCredentialStore.getCustomUploaderConfig()

    override suspend fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig) =
        secureCredentialStore.saveCustomUploaderConfig(config)
```

- [ ] **Step 3: SecureCredentialStore**

Add imports:

```kotlin
import com.xerahs.android.core.common.sxcu.LegacyCustomHttp
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
```

Replace the whole `// Custom HTTP` section (`getCustomHttpConfig` + `saveCustomHttpConfig`, lines 140-168) with:

```kotlin
    // Custom uploader (.sxcu JSON). Prefix "" = global config, "profile_<id>_" = profile config.
    fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig = readCustomUploader("")

    fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig) {
        prefs.edit().putString("custom_uploader_sxcu", SxcuWriter.write(config.spec)).apply()
    }

    private fun readCustomUploader(p: String): UploadConfig.CustomUploaderConfig {
        prefs.getString("${p}custom_uploader_sxcu", null)?.let { stored ->
            SxcuParser.parse(stored).getOrNull()?.let { return UploadConfig.CustomUploaderConfig(it) }
        }
        val legacyUrl = prefs.getString("${p}custom_http_url", null)
        if (legacyUrl.isNullOrEmpty()) return UploadConfig.CustomUploaderConfig()

        // One-time in-place migration of the pre-0.5 Custom HTTP keys.
        val spec = LegacyCustomHttp.toSpec(
            url = legacyUrl,
            method = prefs.getString("${p}custom_http_method", "POST") ?: "POST",
            headers = LegacyCustomHttp.parseHeaderLines(prefs.getString("${p}custom_http_headers", "") ?: ""),
            responseUrlJsonPath = prefs.getString("${p}custom_http_json_path", "url") ?: "url",
            formFieldName = prefs.getString("${p}custom_http_form_field", "file") ?: "file",
        )
        prefs.edit().apply {
            putString("${p}custom_uploader_sxcu", SxcuWriter.write(spec))
            listOf("url", "method", "headers", "json_path", "form_field")
                .forEach { remove("${p}custom_http_$it") }
            apply()
        }
        return UploadConfig.CustomUploaderConfig(spec)
    }
```

In `getProfileConfig`, replace the whole `UploadDestination.CUSTOM_HTTP -> { … }` branch with:

```kotlin
            UploadDestination.CUSTOM_HTTP -> readCustomUploader(p)
```

In `saveProfileConfig`, replace the `is UploadConfig.CustomHttpConfig -> { … }` branch with:

```kotlin
                is UploadConfig.CustomUploaderConfig -> {
                    putString("${p}custom_uploader_sxcu", SxcuWriter.write(config.spec))
                }
```

- [ ] **Step 4: CustomHttpUploader delegates to the client** (replace the whole file)

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.data.remote.custom.CustomUploadInput
import com.xerahs.android.core.data.remote.custom.CustomUploadOutcome
import com.xerahs.android.core.data.remote.custom.CustomUploaderClient
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomHttpUploader @Inject constructor(
    private val client: CustomUploaderClient
) {
    suspend fun upload(
        file: File,
        config: UploadConfig.CustomUploaderConfig,
        remoteFileName: String? = null,
        input: String = "",
        inputValues: Map<String, String> = emptyMap(),
    ): UploadResult {
        val fileName = remoteFileName ?: file.name
        return when (val outcome = client.execute(config.spec, CustomUploadInput(file, fileName, input, inputValues))) {
            is CustomUploadOutcome.Success -> UploadResult(
                success = true,
                url = outcome.url,
                deleteUrl = outcome.deletionUrl,
                thumbnailUrl = outcome.thumbnailUrl,
                destination = UploadDestination.CUSTOM_HTTP
            )
            is CustomUploadOutcome.Failure -> UploadResult(
                success = false,
                errorMessage = outcome.message,
                destination = UploadDestination.CUSTOM_HTTP
            )
        }
    }
}
```

- [ ] **Step 5: UploadWorker branch**

In `UploadWorker.performUpload`, replace the `UploadDestination.CUSTOM_HTTP -> { … }` branch with:

```kotlin
            UploadDestination.CUSTOM_HTTP -> {
                val config = (profileConfig as? UploadConfig.CustomUploaderConfig)
                    ?: settingsRepository.getCustomUploaderConfig()
                customHttpUploader.upload(file, config, resolvedName)
            }
```

- [ ] **Step 6: SettingsViewModel**

Line 167 becomes:

```kotlin
            UploadDestination.CUSTOM_HTTP -> settingsRepository.getCustomUploaderConfig().spec.requestURL.isNotBlank()
```

- [ ] **Step 7: ExportImportManager**

Add imports:

```kotlin
import com.xerahs.android.core.common.sxcu.LegacyCustomHttp
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.domain.model.UploadConfig
```

(skip any import that already exists). Add this private helper at the bottom of the class, next to `maskSecret`:

```kotlin
    /** Reads the custom uploader from an export: new `customUploader` (.sxcu text) or legacy `customHttp` object. */
    private fun importedCustomUploader(json: JsonObject): UploadConfig.CustomUploaderConfig? {
        json.get("customUploader")?.takeIf { it.isJsonPrimitive }?.asString?.let { text ->
            return SxcuParser.parse(text).getOrNull()?.let { UploadConfig.CustomUploaderConfig(it) }
        }
        val ch = json.getAsJsonObject("customHttp") ?: return null
        val url = ch.get("url")?.asString.orEmpty()
        if (url.isBlank()) return null
        return UploadConfig.CustomUploaderConfig(
            LegacyCustomHttp.toSpec(
                url = url,
                method = ch.get("method")?.asString.orEmpty(),
                headers = ch.getAsJsonObject("headers")?.entrySet()?.associate { (k, v) -> k to v.asString }.orEmpty(),
                responseUrlJsonPath = ch.get("responseUrlJsonPath")?.asString ?: "url",
                formFieldName = ch.get("formFieldName")?.asString.orEmpty(),
            )
        )
    }
```

Export (lines 72-82): replace the `val customHttpConfig = … json.add("customHttp", customHttp)` block with:

```kotlin
        json.addProperty("customUploader", SxcuWriter.write(settingsRepository.getCustomUploaderConfig().spec))
```

Full import (lines 176-190): replace the `json.getAsJsonObject("customHttp")?.let { … }` block with:

```kotlin
        importedCustomUploader(json)?.let { settingsRepository.saveCustomUploaderConfig(it) }
```

Preview (lines 299-311): replace the `// Custom HTTP section` block with:

```kotlin
        // Custom uploader section
        importedCustomUploader(json)?.let { imported ->
            val current = settingsRepository.getCustomUploaderConfig().spec
            val cur = "${current.name} — ${current.requestURL}"
            val imp = "${imported.spec.name} — ${imported.spec.requestURL}"
            sections.add(
                ImportSection(
                    "Custom uploader",
                    listOf(ImportField("customUploader", "Uploader", cur, imp, cur != imp))
                )
            )
        }
```

Selective apply (lines 410-424): replace the `// Custom HTTP` block with:

```kotlin
        // Custom uploader
        if ("customUploader" in accepted) {
            importedCustomUploader(json)?.let { settingsRepository.saveCustomUploaderConfig(it) }
        }
```

- [ ] **Step 8: Profile editor ViewModel**

In `ProfileManagementViewModel.kt`:

Add imports:

```kotlin
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
```

Replace the five `// Custom HTTP` state fields with:

```kotlin
    // Custom uploader (.sxcu JSON text)
    val customUploaderSxcu: String = SxcuWriter.write(CustomUploaderSpec()),
    val customUploaderError: String? = null,
```

Replace the five `updateCustomHttp*` setters with:

```kotlin
    fun updateCustomUploaderSxcu(v: String) {
        _editorState.value = _editorState.value.copy(customUploaderSxcu = v, customUploaderError = null)
    }
```

In `saveProfile`, immediately after `val state = _editorState.value` insert:

```kotlin
            if (state.destination == UploadDestination.CUSTOM_HTTP) {
                val parsed = SxcuParser.parse(state.customUploaderSxcu)
                if (parsed.isFailure) {
                    _editorState.value = state.copy(
                        isSaving = false,
                        customUploaderError = parsed.exceptionOrNull()?.message ?: "Not a valid custom uploader"
                    )
                    return@launch
                }
            }
```

In `applyConfig`, replace the `is UploadConfig.CustomHttpConfig -> copy(…)` branch with:

```kotlin
            is UploadConfig.CustomUploaderConfig -> copy(
                customUploaderSxcu = SxcuWriter.write(config.spec)
            )
```

In `toConfig`, replace the `UploadDestination.CUSTOM_HTTP -> { … }` branch with:

```kotlin
            UploadDestination.CUSTOM_HTTP -> UploadConfig.CustomUploaderConfig(
                SxcuParser.parse(customUploaderSxcu).getOrThrow() // validated in saveProfile
            )
```

- [ ] **Step 9: Profile editor UI**

In `ProfileEditorScreen.kt` replace the whole `CustomHttpFields` function body (keep its signature) with:

```kotlin
    OutlinedTextField(
        value = state.customUploaderSxcu,
        onValueChange = { viewModel.updateCustomUploaderSxcu(it) },
        label = { Text("Custom uploader (.sxcu JSON)") },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        isError = state.customUploaderError != null,
        supportingText = {
            Text(state.customUploaderError ?: "Paste or edit a ShareX custom uploader definition")
        },
        minLines = 10,
        modifier = Modifier.fillMaxWidth()
    )
```

Add `import androidx.compose.ui.text.font.FontFamily` if not present (`MaterialTheme` is already imported).

- [ ] **Step 10: Global custom uploader ViewModel** (replace the whole of `CustomHttpConfigViewModel.kt`)

```kotlin
package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class CustomHttpConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadSxcu(): String = SxcuWriter.write(settingsRepository.getCustomUploaderConfig().spec)

    /** Saves [sxcu] if valid. Returns null on success, otherwise the error message. */
    suspend fun save(sxcu: String): String? = SxcuParser.parse(sxcu).fold(
        onSuccess = { settingsRepository.saveCustomUploaderConfig(UploadConfig.CustomUploaderConfig(it)); null },
        onFailure = { it.message ?: "Not a valid custom uploader" }
    )

    /** Validates [sxcu] and returns it re-formatted, or null if invalid. */
    fun normalize(sxcu: String): String? = SxcuParser.parse(sxcu).getOrNull()?.let(SxcuWriter::write)

    suspend fun testConnection(sxcu: String): String = withContext(Dispatchers.IO) {
        val spec = SxcuParser.parse(sxcu).getOrElse { return@withContext "Fix the definition first: ${it.message}" }
        val url = runCatching { ShareXSyntax.evaluate(spec.requestURL, SyntaxContext(fileName = "test.png")) }
            .getOrElse { return@withContext "Request URL can't be evaluated: ${it.message}" }
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                if (response.code in 200..499) "Endpoint reachable (HTTP ${response.code})"
                else "Endpoint returned HTTP ${response.code}"
            }
        } catch (e: java.net.UnknownHostException) {
            "Connection failed: could not resolve host."
        } catch (e: java.net.SocketTimeoutException) {
            "Connection timed out."
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}
```

- [ ] **Step 11: Global custom uploader screen** (replace the whole of `CustomHttpConfigScreen.kt`; `onImportAsProfile` is wired in Task 12, default no-op keeps NavGraph compiling now)

```kotlin
package com.xerahs.android.feature.settings.destinations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHttpConfigScreen(
    onBack: () -> Unit,
    onImportAsProfile: () -> Unit = {},
    viewModel: CustomHttpConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var sxcu by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { sxcu = viewModel.loadSxcu() }

    fun replaceWith(text: String?) {
        val normalized = text?.let(viewModel::normalize)
        if (normalized == null) {
            scope.launch { snackbarHostState.showSnackbar("Not a valid .sxcu custom uploader") }
        } else {
            sxcu = normalized
            error = null
            scope.launch { snackbarHostState.showSnackbar("Loaded — review and tap Save") }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            replaceWith(runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom uploader") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Load")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }) { Text("Open .sxcu") }
                OutlinedButton(onClick = { replaceWith(clipboard.getText()?.text) }) { Text("Paste") }
            }
            TextButton(onClick = onImportAsProfile, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Import as a new profile instead…")
            }

            SectionHeader("Definition")
            SettingsGroupCard {
                OutlinedTextField(
                    value = sxcu,
                    onValueChange = { sxcu = it; error = null },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    isError = error != null,
                    supportingText = { Text(error ?: "ShareX .sxcu JSON — supports {json:}, {regex:}, {xml:}, {inputbox:} …") },
                    minLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = viewModel.save(sxcu)
                            busy = false
                            if (error == null) snackbarHostState.showSnackbar("Saved")
                        }
                    }
                ) { Text("Save") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = viewModel.testConnection(sxcu)
                            busy = false
                            snackbarHostState.showSnackbar(result)
                        }
                    }
                ) { Text("Test endpoint") }
            }
        }
    }
}
```

- [ ] **Step 12: Labels in Upload settings**

In `UploadSettingsScreen.kt` change the Custom HTTP `DestinationItem` to:

```kotlin
                        DestinationItem(
                            icon = Icons.Default.Http,
                            title = "Custom uploader",
                            subtitle = "ShareX-compatible (.sxcu) uploader",
                            onClick = onNavigateToCustomHttpConfig
                        )
```

- [ ] **Step 13: Compile and run all unit tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. If compilation fails, search for leftovers: `grep -rn "CustomHttpConfig\b\|getCustomHttpConfig\|customHttpUrl" --include='*.kt' . | grep -v /build/` — the only remaining hits should be `CustomHttpConfigScreen`/`CustomHttpConfigViewModel`/`Screen.CustomHttpConfig` (names kept on purpose).

- [ ] **Step 14: On-device legacy migration check**

Before installing this build, the device should have the current release installed with a Custom HTTP config saved (any URL). Then:

Run: `./gradlew installDebug` and open Settings → Uploads → Custom uploader.
Expected: the editor shows the old URL as `RequestURL`, `"FileFormName"` = the old form field, `"URL": "{json:url}"` (or the old JSON path).

- [ ] **Step 15: Commit**

```bash
git add -A core/domain core/data feature/upload feature/settings
git commit -m "feat: replace Custom HTTP config with ShareX-compatible custom uploader"
```

---

### Task 9: Custom URL shortener

**Files:**
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/datastore/SettingsDataStore.kt`
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/repository/SettingsRepository.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/repository/SettingsRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/repository/UrlShortenerRepositoryImpl.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/UploadSettingsScreen.kt`

- [ ] **Step 1: DataStore key** — in `SettingsDataStore.Keys` add:

```kotlin
        val SHORTENER_PROFILE_ID = stringPreferencesKey("shortener_profile_id")
```

and at the end of the class (before the closing brace) add:

```kotlin
    fun getShortenerProfileId(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHORTENER_PROFILE_ID]
    }

    suspend fun setShortenerProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[Keys.SHORTENER_PROFILE_ID] = id else prefs.remove(Keys.SHORTENER_PROFILE_ID)
        }
    }
```

- [ ] **Step 2: Repository** — `SettingsRepository.kt` add:

```kotlin
    /** Custom-uploader profile used for URL shortening; null = built-in is.gd. */
    fun getShortenerProfileId(): Flow<String?>
    suspend fun setShortenerProfileId(id: String?)
```

`SettingsRepositoryImpl.kt` add:

```kotlin
    override fun getShortenerProfileId(): Flow<String?> = settingsDataStore.getShortenerProfileId()

    override suspend fun setShortenerProfileId(id: String?) = settingsDataStore.setShortenerProfileId(id)
```

- [ ] **Step 3: Shortener delegation** — replace the `UrlShortenerRepositoryImpl` class (keep `buildIsGdRequestUrl` unchanged) with:

```kotlin
class UrlShortenerRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
    private val customUploaderClient: CustomUploaderClient,
) : UrlShortenerRepository {
    override suspend fun shorten(longUrl: String): Result<String> {
        val profileId = settingsRepository.getShortenerProfileId().first()
        val config = profileId?.let {
            profileRepository.getProfileConfig(it, UploadDestination.CUSTOM_HTTP) as? UploadConfig.CustomUploaderConfig
        }
        return if (config != null && config.spec.requestURL.isNotBlank()) shortenCustom(config, longUrl)
        else shortenIsGd(longUrl)
    }

    private suspend fun shortenCustom(config: UploadConfig.CustomUploaderConfig, longUrl: String): Result<String> =
        when (val outcome = customUploaderClient.execute(config.spec, CustomUploadInput(null, "", input = longUrl))) {
            is CustomUploadOutcome.Success -> Result.success(outcome.url)
            is CustomUploadOutcome.Failure -> Result.failure(IllegalStateException(outcome.message))
        }

    private suspend fun shortenIsGd(longUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(buildIsGdRequestUrl(longUrl)).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                if (resp.isSuccessful && body.startsWith("http")) Result.success(body)
                else Result.failure(IllegalStateException("Shorten failed: ${resp.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

with added imports:

```kotlin
import com.xerahs.android.core.data.remote.custom.CustomUploadInput
import com.xerahs.android.core.data.remote.custom.CustomUploadOutcome
import com.xerahs.android.core.data.remote.custom.CustomUploaderClient
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import kotlinx.coroutines.flow.first
```

- [ ] **Step 4: Settings state** — in `SettingsViewModel.kt`:

Add to the UI state data class (next to `stripExif`):

```kotlin
    val shortenerProfileId: String? = null,
    val shortenerProfiles: List<UploadProfile> = emptyList(),
```

(import `com.xerahs.android.core.domain.model.UploadProfile` and `com.xerahs.android.core.common.sxcu.CustomDestinationType`, `com.xerahs.android.core.domain.model.UploadConfig`). Inject `private val profileRepository: UploadProfileRepository` into the constructor (import `com.xerahs.android.core.domain.repository.UploadProfileRepository`). In `init`, next to the `getStripExif()` collector `launch { … }`, add:

```kotlin
            launch {
                settingsRepository.getShortenerProfileId().collect { id ->
                    _uiState.value = _uiState.value.copy(shortenerProfileId = id)
                }
            }
            launch {
                profileRepository.getProfilesForDestination(UploadDestination.CUSTOM_HTTP).collect { profiles ->
                    val shorteners = profiles.filter { p ->
                        (profileRepository.getProfileConfig(p.id, UploadDestination.CUSTOM_HTTP) as? UploadConfig.CustomUploaderConfig)
                            ?.spec?.destinationTypes?.contains(CustomDestinationType.URL_SHORTENER) == true
                    }
                    _uiState.value = _uiState.value.copy(shortenerProfiles = shorteners)
                }
            }
```

and add:

```kotlin
    fun setShortenerProfileId(id: String?) {
        viewModelScope.launch { settingsRepository.setShortenerProfileId(id) }
    }
```

- [ ] **Step 5: Settings UI** — in `UploadSettingsScreen.kt`, directly after the "Strip EXIF metadata" `ListItem` (inside the same `SettingsGroupCard`), add:

```kotlin
                if (uiState.shortenerProfiles.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    var shortenerMenu by remember { mutableStateOf(false) }
                    val current = uiState.shortenerProfiles.find { it.id == uiState.shortenerProfileId }
                    ListItem(
                        headlineContent = { Text("URL shortener") },
                        supportingContent = { Text(current?.name ?: "is.gd (built-in)") },
                        modifier = Modifier.clickable { shortenerMenu = true },
                        trailingContent = {
                            DropdownMenu(expanded = shortenerMenu, onDismissRequest = { shortenerMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("is.gd (built-in)") },
                                    onClick = { viewModel.setShortenerProfileId(null); shortenerMenu = false }
                                )
                                uiState.shortenerProfiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = { viewModel.setShortenerProfileId(p.id); shortenerMenu = false }
                                    )
                                }
                            }
                        }
                    )
                }
```

Add any missing imports (`androidx.compose.foundation.clickable`, `androidx.compose.material3.DropdownMenu`, `androidx.compose.material3.DropdownMenuItem`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`).

- [ ] **Step 6: Compile and test**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add core/data core/domain feature/settings
git commit -m "feat: allow a custom uploader profile as the URL shortener"
```

---

### Task 10: `.xsdc` decoder

**Files:**
- Create: `core/data/src/main/java/com/xerahs/android/core/data/importer/XsdcDecoder.kt`
- Test: `core/data/src/test/java/com/xerahs/android/core/data/importer/XsdcDecoderTest.kt`

- [ ] **Step 1: Write the failing test** (includes an encrypt helper that mirrors upstream's writer)

```kotlin
package com.xerahs.android.core.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class XsdcDecoderTest {
    companion object {
        /** Builds an .xsdc envelope exactly like upstream DestinationConfigExportService. */
        fun encrypt(payload: String, passphrase: String, iterations: Int = 1000): ByteArray {
            val rnd = SecureRandom()
            val salt = ByteArray(16).also(rnd::nextBytes)
            val nonce = ByteArray(12).also(rnd::nextBytes)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val out = cipher.doFinal(payload.toByteArray())
            val cipherText = out.copyOfRange(0, out.size - 16)
            val tag = out.copyOfRange(out.size - 16, out.size)
            val b64 = Base64.getEncoder()
            val envelope = JsonObject().apply {
                addProperty("Format", "XerahS.DestinationConfig")
                addProperty("FormatVersion", 1)
                add("Encryption", JsonObject().apply {
                    addProperty("Method", "Passphrase"); addProperty("Kdf", "PBKDF2-HMAC-SHA256")
                    addProperty("Iterations", iterations); addProperty("Salt", b64.encodeToString(salt))
                    addProperty("Cipher", "AES-256-GCM"); addProperty("Nonce", b64.encodeToString(nonce))
                    addProperty("Tag", b64.encodeToString(tag))
                })
                addProperty("Payload", b64.encodeToString(cipherText))
            }
            return envelope.toString().toByteArray()
        }

        fun s3Payload(): String = JsonObject().apply {
            addProperty("Format", "XerahS.DestinationConfig.Payload")
            addProperty("FormatVersion", 1)
            add("Destinations", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ProviderId", "amazons3"); addProperty("DisplayName", "Desktop S3")
                    addProperty("IsDefault", true)
                    add("Config", JsonObject().apply {
                        addProperty("AuthMode", "AccessKeys"); addProperty("AccessKeyId", "AK")
                        addProperty("SecretAccessKey", "SK"); addProperty("BucketName", "b1")
                        addProperty("Region", "eu-west-1"); addProperty("Endpoint", "")
                        addProperty("UsePathStyle", false); addProperty("UseCustomDomain", true)
                        addProperty("CustomDomain", "https://cdn.test"); addProperty("SetPublicAcl", true)
                    })
                })
                add(JsonObject().apply {
                    addProperty("ProviderId", "dropbox"); addProperty("DisplayName", "DB")
                    add("Config", JsonObject())
                })
            })
        }.toString()
    }

    @Test fun decryptsDestinations() {
        val dests = XsdcDecoder.decode(encrypt(s3Payload(), "correct horse"), "correct horse".toCharArray())
        assertEquals(2, dests.size)
        assertEquals("amazons3", dests[0].providerId)
        assertEquals("Desktop S3", dests[0].displayName)
        assertTrue(dests[0].isDefault)
        assertEquals("b1", dests[0].config.get("BucketName").asString)
    }

    @Test fun wrongPassphraseIsReported() {
        val e = runCatching { XsdcDecoder.decode(encrypt(s3Payload(), "right"), "wrong".toCharArray()) }.exceptionOrNull()
        assertTrue(e is XsdcException)
        assertEquals("Wrong passphrase or damaged file.", e!!.message)
    }

    @Test fun rejectsNonXsdcJson() {
        val e = runCatching { XsdcDecoder.decode("""{"Format":"Other"}""".toByteArray(), "x".toCharArray()) }.exceptionOrNull()
        assertTrue(e is XsdcException)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*XsdcDecoderTest*'`
Expected: FAIL — `Unresolved reference: XsdcDecoder`

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.data.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class XsdcException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class XsdcDestination(
    val providerId: String,
    val displayName: String,
    val isDefault: Boolean,
    val config: JsonObject,
)

/**
 * Decrypts a XerahS destination config (.xsdc): JSON envelope, PBKDF2-HMAC-SHA256 key,
 * AES-256-GCM payload with the 16-byte tag stored separately. Implemented from the format;
 * the passphrase is never stored.
 */
object XsdcDecoder {
    fun decode(bytes: ByteArray, passphrase: CharArray): List<XsdcDestination> {
        val envelope = try {
            JsonParser.parseString(String(bytes, Charsets.UTF_8).trim().removePrefix("﻿")).asJsonObject
        } catch (e: Exception) {
            throw XsdcException("The .xsdc file is not valid JSON.", e)
        }
        if (envelope.str("Format") != "XerahS.DestinationConfig" || envelope.int("FormatVersion") != 1) {
            throw XsdcException("This is not a XerahS destination config.")
        }
        val enc = envelope.get("Encryption")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw XsdcException("The .xsdc file is missing encryption metadata.")
        if (enc.str("Method") != "Passphrase" || enc.str("Kdf") != "PBKDF2-HMAC-SHA256" ||
            enc.str("Cipher") != "AES-256-GCM"
        ) throw XsdcException("This .xsdc encryption method is not supported.")
        val iterations = enc.int("Iterations")?.takeIf { it > 0 }
            ?: throw XsdcException("The .xsdc file has invalid encryption metadata.")

        val plain = try {
            val b64 = Base64.getDecoder()
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase, b64.decode(enc.str("Salt").orEmpty()), iterations, 256))
                .encoded
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, b64.decode(enc.str("Nonce").orEmpty())))
            cipher.doFinal(b64.decode(envelope.str("Payload").orEmpty()) + b64.decode(enc.str("Tag").orEmpty()))
        } catch (e: Exception) {
            throw XsdcException("Wrong passphrase or damaged file.", e)
        }

        val payload = try {
            JsonParser.parseString(String(plain, Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            throw XsdcException("The decrypted .xsdc payload is invalid.", e)
        }
        return payload.get("Destinations")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject }
            ?.map { d ->
                XsdcDestination(
                    providerId = d.str("ProviderId").orEmpty(),
                    displayName = d.str("DisplayName").orEmpty(),
                    isDefault = d.get("IsDefault")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                    config = d.get("Config")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject(),
                )
            }.orEmpty()
    }

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive }?.asInt
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*XsdcDecoderTest*'`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/xerahs/android/core/data/importer/XsdcDecoder.kt core/data/src/test/java/com/xerahs/android/core/data/importer/XsdcDecoderTest.kt
git commit -m "feat(data): decrypt XerahS .xsdc destination configs"
```

---

### Task 11: Uploader import repository

**Files:**
- Create: `core/domain/src/main/java/com/xerahs/android/core/domain/model/UploaderImport.kt`
- Create: `core/domain/src/main/java/com/xerahs/android/core/domain/repository/UploaderImportRepository.kt`
- Create: `core/data/src/main/java/com/xerahs/android/core/data/importer/UploaderImportParsing.kt`
- Create: `core/data/src/main/java/com/xerahs/android/core/data/repository/UploaderImportRepositoryImpl.kt`
- Modify: `app/src/main/java/com/xerahs/android/di/AppModule.kt`
- Test: `core/data/src/test/java/com/xerahs/android/core/data/importer/UploaderImportParsingTest.kt`

- [ ] **Step 1: Domain types**

`UploaderImport.kt`:

```kotlin
package com.xerahs.android.core.domain.model

enum class UploaderFileKind { SXCU, XSDC, UNKNOWN }

const val INSECURE_HTTP_WARNING = "Sends your files over unencrypted HTTP"

/** A destination config ready to be saved as an upload profile. */
data class ImportDraft(
    val name: String,
    val destination: UploadDestination,
    val config: UploadConfig,
    val warnings: List<String> = emptyList(),
    val makeDefault: Boolean = false,
)

data class XsdcImportResult(
    val drafts: List<ImportDraft>,
    /** Human-readable "Name (reason)" entries that could not be imported. */
    val skipped: List<String>,
)
```

`UploaderImportRepository.kt`:

```kotlin
package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult

interface UploaderImportRepository {
    fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind
    fun parseSxcu(bytes: ByteArray): Result<ImportDraft>
    fun parseXsdc(bytes: ByteArray, passphrase: CharArray): Result<XsdcImportResult>
    /** Saves each draft as a new upload profile. Returns how many were saved. */
    suspend fun import(drafts: List<ImportDraft>): Int
}
```

- [ ] **Step 2: Write the failing parsing test**

```kotlin
package com.xerahs.android.core.data.importer

import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploaderFileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploaderImportParsingTest {
    @Test fun detectsByExtensionThenContent() {
        assertEquals(UploaderFileKind.SXCU, UploaderImportParsing.detect(ByteArray(0), "a.SXCU"))
        assertEquals(UploaderFileKind.XSDC, UploaderImportParsing.detect(ByteArray(0), "a.xsdc"))
        assertEquals(UploaderFileKind.SXCU, UploaderImportParsing.detect("""{"requesturl":"https://x"}""".toByteArray(), null))
        assertEquals(UploaderFileKind.XSDC, UploaderImportParsing.detect("""{"Format":"XerahS.DestinationConfig"}""".toByteArray(), "download.bin"))
        assertEquals(UploaderFileKind.UNKNOWN, UploaderImportParsing.detect("hello".toByteArray(), "notes.txt"))
    }

    @Test fun sxcuDraftWarnsAboutPlainHttp() {
        val draft = UploaderImportParsing.sxcuDraft("""{"Name":"H","RequestURL":"http://h.test/u"}""".toByteArray()).getOrThrow()
        assertEquals("H", draft.name)
        assertEquals(UploadDestination.CUSTOM_HTTP, draft.destination)
        assertTrue(draft.config is UploadConfig.CustomUploaderConfig)
        assertEquals(listOf(INSECURE_HTTP_WARNING), draft.warnings)
    }

    @Test fun mapsS3AndSkipsUnsupported() {
        val dests = XsdcDecoder.decode(XsdcDecoderTest.encrypt(XsdcDecoderTest.s3Payload(), "p"), "p".toCharArray())
        val result = UploaderImportParsing.xsdcDrafts(dests)

        assertEquals(1, result.drafts.size)
        val draft = result.drafts.single()
        assertEquals("Desktop S3", draft.name)
        assertEquals(UploadDestination.S3, draft.destination)
        assertTrue(draft.makeDefault)
        assertEquals(
            UploadConfig.S3Config(
                accessKeyId = "AK", secretAccessKey = "SK", region = "eu-west-1", bucket = "b1",
                endpoint = null, customUrl = "https://cdn.test", acl = "public-read", usePathStyle = false
            ),
            draft.config
        )
        assertEquals(listOf("DB (dropbox isn't supported on Android yet)"), result.skipped)
    }
}
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*UploaderImportParsingTest*'`
Expected: FAIL — `Unresolved reference: UploaderImportParsing`

- [ ] **Step 4: Implement parsing**

```kotlin
package com.xerahs.android.core.data.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult

object UploaderImportParsing {
    fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind {
        when (fileName?.substringAfterLast('.', "")?.lowercase()) {
            "sxcu" -> return UploaderFileKind.SXCU
            "xsdc" -> return UploaderFileKind.XSDC
        }
        val obj = runCatching {
            JsonParser.parseString(String(bytes, Charsets.UTF_8).trim().removePrefix("﻿")).asJsonObject
        }.getOrNull() ?: return UploaderFileKind.UNKNOWN
        return when {
            obj.get("Format")?.takeIf { it.isJsonPrimitive }?.asString == "XerahS.DestinationConfig" -> UploaderFileKind.XSDC
            obj.keySet().any { it.equals("RequestURL", ignoreCase = true) } -> UploaderFileKind.SXCU
            else -> UploaderFileKind.UNKNOWN
        }
    }

    fun sxcuDraft(bytes: ByteArray): Result<ImportDraft> =
        SxcuParser.parse(String(bytes, Charsets.UTF_8)).map { spec ->
            ImportDraft(
                name = spec.name,
                destination = UploadDestination.CUSTOM_HTTP,
                config = UploadConfig.CustomUploaderConfig(spec),
                warnings = warningsFor(spec),
            )
        }

    fun warningsFor(spec: CustomUploaderSpec): List<String> = buildList {
        if (spec.requestURL.trim().startsWith("http://", ignoreCase = true)) add(INSECURE_HTTP_WARNING)
    }

    fun xsdcDrafts(destinations: List<XsdcDestination>): XsdcImportResult {
        val drafts = mutableListOf<ImportDraft>()
        val skipped = mutableListOf<String>()
        destinations.forEach { d ->
            val label = d.displayName.ifBlank { d.providerId }
            when {
                !d.providerId.equals("amazons3", ignoreCase = true) ->
                    skipped.add("$label (${d.providerId} isn't supported on Android yet)")
                !d.config.str("AuthMode").equals("AccessKeys", ignoreCase = true) ->
                    skipped.add("$label (only access-key S3 auth is supported)")
                else -> drafts.add(
                    ImportDraft(
                        name = d.displayName.ifBlank { "Amazon S3" },
                        destination = UploadDestination.S3,
                        config = s3Config(d.config),
                        makeDefault = d.isDefault,
                    )
                )
            }
        }
        return XsdcImportResult(drafts, skipped)
    }

    private fun s3Config(c: JsonObject) = UploadConfig.S3Config(
        accessKeyId = c.str("AccessKeyId").orEmpty(),
        secretAccessKey = c.str("SecretAccessKey").orEmpty(),
        region = c.str("Region")?.ifBlank { null } ?: "us-east-1",
        bucket = c.str("BucketName").orEmpty(),
        endpoint = c.str("Endpoint")?.ifBlank { null },
        customUrl = c.str("CustomDomain")?.ifBlank { null }?.takeIf { c.bool("UseCustomDomain") },
        acl = if (c.bool("SetPublicAcl")) "public-read" else "",
        usePathStyle = c.bool("UsePathStyle"),
    )

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.bool(k: String): Boolean = get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*UploaderImportParsingTest*'`
Expected: PASS (3 tests)

- [ ] **Step 6: Repository implementation**

```kotlin
package com.xerahs.android.core.data.repository

import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.data.importer.UploaderImportParsing
import com.xerahs.android.core.data.importer.XsdcDecoder
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.core.domain.repository.UploaderImportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploaderImportRepositoryImpl @Inject constructor(
    private val profileRepository: UploadProfileRepository,
) : UploaderImportRepository {

    override fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind =
        UploaderImportParsing.detect(bytes, fileName)

    override fun parseSxcu(bytes: ByteArray): Result<ImportDraft> = UploaderImportParsing.sxcuDraft(bytes)

    override fun parseXsdc(bytes: ByteArray, passphrase: CharArray): Result<XsdcImportResult> =
        runCatching { UploaderImportParsing.xsdcDrafts(XsdcDecoder.decode(bytes, passphrase)) }

    override suspend fun import(drafts: List<ImportDraft>): Int {
        drafts.forEach { draft ->
            val profile = UploadProfile(
                id = generateId(),
                name = draft.name,
                destination = draft.destination,
                isDefault = draft.makeDefault,
                createdAt = generateTimestamp(),
            )
            profileRepository.createProfile(profile, draft.config)
            if (draft.makeDefault) profileRepository.setDefault(profile.id, draft.destination)
        }
        return drafts.size
    }
}
```

- [ ] **Step 7: Bind in Hilt** — in `app/src/main/java/com/xerahs/android/di/AppModule.kt`, next to the other `@Binds` methods, add:

```kotlin
    @Binds
    @Singleton
    abstract fun bindUploaderImportRepository(impl: UploaderImportRepositoryImpl): UploaderImportRepository
```

with imports `com.xerahs.android.core.data.repository.UploaderImportRepositoryImpl` and `com.xerahs.android.core.domain.repository.UploaderImportRepository` (match the annotation style of the neighbouring bindings — if they don't use `@Singleton`, omit it).

- [ ] **Step 8: Compile**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add core/domain core/data app/src/main/java/com/xerahs/android/di/AppModule.kt
git commit -m "feat: add uploader import repository for .sxcu and .xsdc"
```

---

### Task 12: Import screen + Settings entry

**Files:**
- Create: `feature/settings/src/main/java/com/xerahs/android/feature/settings/importer/UploaderImportViewModel.kt`
- Create: `feature/settings/src/main/java/com/xerahs/android/feature/settings/importer/UploaderImportScreen.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/UploadSettingsScreen.kt`
- Modify: `app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt`

- [ ] **Step 1: ViewModel**

```kotlin
package com.xerahs.android.feature.settings.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.repository.UploaderImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SelectableDraft(val draft: ImportDraft, val selected: Boolean = true)

sealed interface UploaderImportState {
    data object Idle : UploaderImportState
    data object Loading : UploaderImportState
    data class NeedsPassphrase(val error: String? = null) : UploaderImportState
    data class Preview(val items: List<SelectableDraft>, val skipped: List<String> = emptyList()) : UploaderImportState
    data class Done(val count: Int) : UploaderImportState
    data class Error(val message: String) : UploaderImportState
}

@HiltViewModel
class UploaderImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: UploaderImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UploaderImportState>(UploaderImportState.Idle)
    val state: StateFlow<UploaderImportState> = _state.asStateFlow()

    private var pendingXsdc: ByteArray? = null

    init {
        savedStateHandle.get<String>("uri")?.takeIf { it.isNotBlank() }?.let { loadUri(Uri.parse(it)) }
    }

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    bytes to name
                }
            }
            read.fold(
                onSuccess = { (bytes, name) -> handle(bytes, name) },
                onFailure = { _state.value = UploaderImportState.Error("Couldn't read the file.") }
            )
        }
    }

    fun loadText(text: String?) {
        if (text.isNullOrBlank()) {
            _state.value = UploaderImportState.Error("The clipboard is empty.")
            return
        }
        handle(text.toByteArray(), null)
    }

    private fun handle(bytes: ByteArray, name: String?) {
        if (bytes.size > MAX_BYTES) {
            _state.value = UploaderImportState.Error("That file is too large to be an uploader config.")
            return
        }
        _state.value = when (repository.detect(bytes, name)) {
            UploaderFileKind.SXCU -> repository.parseSxcu(bytes).fold(
                onSuccess = { UploaderImportState.Preview(listOf(SelectableDraft(it))) },
                onFailure = { UploaderImportState.Error(it.message ?: "Not a valid .sxcu file.") }
            )
            UploaderFileKind.XSDC -> {
                pendingXsdc = bytes
                UploaderImportState.NeedsPassphrase()
            }
            UploaderFileKind.UNKNOWN -> UploaderImportState.Error("This isn't a .sxcu or .xsdc file.")
        }
    }

    fun submitPassphrase(passphrase: CharArray) {
        val bytes = pendingXsdc ?: return
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            val result = withContext(Dispatchers.Default) { repository.parseXsdc(bytes, passphrase) }
            passphrase.fill('\u0000')
            _state.value = result.fold(
                onSuccess = {
                    if (it.drafts.isEmpty()) UploaderImportState.Error(
                        "No destinations in this file can be used on Android." +
                            if (it.skipped.isNotEmpty()) " Skipped: ${it.skipped.joinToString()}" else ""
                    ) else UploaderImportState.Preview(it.drafts.map { d -> SelectableDraft(d) }, it.skipped)
                },
                onFailure = { UploaderImportState.NeedsPassphrase(it.message) }
            )
        }
    }

    fun toggleSelected(index: Int) = updateItem(index) { it.copy(selected = !it.selected) }

    fun toggleDefault(index: Int) = updateItem(index) { it.copy(draft = it.draft.copy(makeDefault = !it.draft.makeDefault)) }

    private fun updateItem(index: Int, change: (SelectableDraft) -> SelectableDraft) {
        val preview = _state.value as? UploaderImportState.Preview ?: return
        _state.value = preview.copy(items = preview.items.mapIndexed { i, item -> if (i == index) change(item) else item })
    }

    /** True when any selected draft sends data over plain HTTP — the UI must confirm first. */
    fun needsInsecureConfirmation(): Boolean =
        (_state.value as? UploaderImportState.Preview)?.items
            ?.any { it.selected && INSECURE_HTTP_WARNING in it.draft.warnings } == true

    fun import() {
        val preview = _state.value as? UploaderImportState.Preview ?: return
        val drafts = preview.items.filter { it.selected }.map { it.draft }
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            _state.value = UploaderImportState.Done(repository.import(drafts))
        }
    }

    fun reset() {
        pendingXsdc = null
        _state.value = UploaderImportState.Idle
    }

    private companion object {
        const val MAX_BYTES = 1_000_000
    }
}
```

- [ ] **Step 2: Screen**

```kotlin
package com.xerahs.android.feature.settings.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploaderImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: UploaderImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadUri)
    }
    var confirmInsecure by remember { mutableStateOf(false) }

    if (confirmInsecure) {
        AlertDialog(
            onDismissRequest = { confirmInsecure = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unencrypted connection") },
            text = { Text("At least one selected uploader sends files over plain HTTP. Anyone on the network could read them. Import anyway?") },
            confirmButton = { TextButton(onClick = { confirmInsecure = false; viewModel.import() }) { Text("Import anyway") } },
            dismissButton = { TextButton(onClick = { confirmInsecure = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import uploader") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val s = state) {
                UploaderImportState.Idle -> {
                    Text("Import a ShareX custom uploader (.sxcu) or a XerahS destination config (.xsdc). Each one becomes an upload profile.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose file") }
                        OutlinedButton(onClick = { viewModel.loadText(clipboard.getText()?.text) }) { Text("Paste .sxcu") }
                    }
                }
                UploaderImportState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UploaderImportState.NeedsPassphrase -> {
                    var passphrase by remember { mutableStateOf("") }
                    Text("This .xsdc file is encrypted. Enter the passphrase used when it was exported.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = s.error != null,
                        supportingText = s.error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = passphrase.isNotEmpty(),
                        onClick = { viewModel.submitPassphrase(passphrase.toCharArray()); passphrase = "" }
                    ) { Text("Decrypt") }
                }
                is UploaderImportState.Preview -> {
                    s.items.forEachIndexed { index, item ->
                        SettingsGroupCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.selected, onCheckedChange = { viewModel.toggleSelected(index) })
                                    Text(item.draft.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(item.draft.describe(), style = MaterialTheme.typography.bodySmall)
                                item.draft.warnings.forEach { w ->
                                    Text("⚠ $w", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                FilterChip(
                                    selected = item.draft.makeDefault,
                                    onClick = { viewModel.toggleDefault(index) },
                                    label = { Text("Default for ${item.draft.destination.displayName}") }
                                )
                            }
                        }
                    }
                    if (s.skipped.isNotEmpty()) {
                        Text("Skipped: ${s.skipped.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = s.items.any { it.selected },
                        onClick = { if (viewModel.needsInsecureConfirmation()) confirmInsecure = true else viewModel.import() }
                    ) { Text("Import") }
                }
                is UploaderImportState.Done -> {
                    Text(if (s.count == 1) "Imported 1 profile." else "Imported ${s.count} profiles.")
                    Button(onClick = onDone) { Text("Done") }
                }
                is UploaderImportState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::reset) { Text("Try another file") }
                }
            }
        }
    }
}

private fun com.xerahs.android.core.domain.model.ImportDraft.describe(): String = when (val c = config) {
    is UploadConfig.CustomUploaderConfig -> {
        val host = runCatching { java.net.URI(c.spec.requestURL).host }.getOrNull() ?: c.spec.requestURL
        "${c.spec.destinationTypes.joinToString { it.sxcuName }} · ${c.spec.requestMethod} $host · ${c.spec.body.sxcuName}"
    }
    is UploadConfig.S3Config -> "Amazon S3 · bucket ${c.bucket} · ${c.region}"
    else -> destination.displayName
}
```

- [ ] **Step 3: Settings entry** — in `UploadSettingsScreen.kt` add parameter `onNavigateToImportUploader: () -> Unit,` after `onNavigateToCustomHttpConfig`, and directly after the Custom uploader `DestinationItem` (inside the same card) add:

```kotlin
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DestinationItem(
                            icon = Icons.Default.FileDownload,
                            title = "Import uploader",
                            subtitle = "ShareX .sxcu or XerahS .xsdc file",
                            onClick = onNavigateToImportUploader
                        )
```

(import `androidx.compose.material.icons.filled.FileDownload`).

- [ ] **Step 4: Navigation** — in `NavGraph.kt`:

Add to `sealed class Screen` (next to `CustomHttpConfig`):

```kotlin
    data object UploaderImport : Screen("settings/import-uploader?uri={uri}") {
        fun createRoute(uri: String? = null) =
            if (uri == null) "settings/import-uploader"
            else "settings/import-uploader?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
```

In the `UploadSettingsScreen(` call add:

```kotlin
                onNavigateToImportUploader = { navController.navigate(Screen.UploaderImport.createRoute()) },
```

In the `CustomHttpConfigScreen(onBack = …)` call add:

```kotlin
                    onImportAsProfile = { navController.navigate(Screen.UploaderImport.createRoute()) },
```

Add a destination next to the CustomHttpConfig composable:

```kotlin
        composable(
            route = Screen.UploaderImport.route,
            arguments = listOf(navArgument("uri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            BiometricGate(navController) {
                UploaderImportScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
        }
```

(import `com.xerahs.android.feature.settings.importer.UploaderImportScreen`). Navigation decodes the query argument automatically, so the ViewModel's `savedStateHandle["uri"]` is the raw URI string.

- [ ] **Step 5: Compile**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: On-device check**

`adb push core/common/src/test/resources/sxcu/multipart_image.sxcu /sdcard/Download/` then `./gradlew installDebug`. Settings → Uploads → Import uploader → Choose file → pick it.
Expected: preview card "My Host · ImageUploader, FileUploader · POST up.example.com · MultipartFormData"; Import → "Imported 1 profile."; Upload Profiles lists "My Host" under Custom uploader. Also copy the same JSON to the clipboard and use "Paste .sxcu" → same preview.

- [ ] **Step 7: Commit**

```bash
git add feature/settings app/src/main/java/com/xerahs/android/ui/navigation/NavGraph.kt
git commit -m "feat(settings): import screen for .sxcu and .xsdc uploaders"
```

---

### Task 13: Open `.sxcu` / `.xsdc` from other apps

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/xerahs/android/ui/MainActivity.kt`

- [ ] **Step 1: Manifest** — inside the `MainActivity` `<activity>`, after the SEND_MULTIPLE filter, add:

```xml
            <!-- Open ShareX .sxcu / XerahS .xsdc files (file managers usually report these as octet-stream or JSON) -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:mimeType="application/octet-stream" />
                <data android:mimeType="application/json" />
            </intent-filter>
```

- [ ] **Step 2: MainActivity** — add state next to `pendingLaunchCapture`:

```kotlin
    private var pendingImportUri by mutableStateOf<String?>(null)
```

In `handleIncomingIntent`, add a new first branch:

```kotlin
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingImportUri = intent.data?.toString()
            return
        }
```

Pass it to `MainScreen(…)`:

```kotlin
                            importUri = pendingImportUri,
                            onImportHandled = { pendingImportUri = null },
```

Add parameters to `MainScreen`:

```kotlin
    importUri: String? = null,
    onImportHandled: () -> Unit = {},
```

and next to the other `LaunchedEffect`s:

```kotlin
    LaunchedEffect(importUri) {
        if (importUri != null) {
            navController.navigate(Screen.UploaderImport.createRoute(importUri))
            onImportHandled()
        }
    }
```

The import screen itself rejects anything that isn't `.sxcu`/`.xsdc` ("This isn't a .sxcu or .xsdc file."), so a mis-routed JSON file is harmless.

- [ ] **Step 3: Compile and check on device**

Run: `./gradlew installDebug`, then in the Files app open `Download/multipart_image.sxcu` → choose XerahS.
Expected: the Import uploader screen opens straight to the preview.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/xerahs/android/ui/MainActivity.kt
git commit -m "feat: open .sxcu/.xsdc files from other apps into the importer"
```

---

### Task 14: `{inputbox}` prompts before upload

**Files:**
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/worker/UploadWorker.kt`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadViewModel.kt`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadScreen.kt`
- Test: `feature/upload/src/test/java/com/xerahs/android/feature/upload/worker/InputValuesCodecTest.kt`
- Modify: `feature/upload/build.gradle.kts` (add `testImplementation(libs.junit)`)

- [ ] **Step 1: Add JUnit to feature:upload** — in `feature/upload/build.gradle.kts` `dependencies { … }` add `testImplementation(libs.junit)`.

- [ ] **Step 2: Write the failing codec test**

```kotlin
package com.xerahs.android.feature.upload.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class InputValuesCodecTest {
    @Test fun roundTripsIncludingSeparatorsInValues() {
        val values = mapOf("Token" to "a|b=c", "Album" to "", "Multi\nline" to "x\ny")
        assertEquals(values, UploadWorker.decodeInputValues(UploadWorker.encodeInputValues(values)))
    }

    @Test fun nullOrBlankDecodesToEmpty() {
        assertEquals(emptyMap<String, String>(), UploadWorker.decodeInputValues(null))
        assertEquals(emptyMap<String, String>(), UploadWorker.decodeInputValues(""))
    }
}
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `./gradlew :feature:upload:testDebugUnitTest --tests '*InputValuesCodecTest*'`
Expected: FAIL — `Unresolved reference: encodeInputValues`

- [ ] **Step 4: Worker codec + plumbing** — in `UploadWorker.companion object` add:

```kotlin
        const val KEY_INPUT_VALUES = "input_values"

        private const val RECORD_SEP = '\u001E'
        private const val UNIT_SEP = '\u001F'

        /** WorkManager Data can't hold maps; encode {inputbox} answers with ASCII separators. */
        fun encodeInputValues(values: Map<String, String>): String =
            values.entries.joinToString(RECORD_SEP.toString()) { "${it.key}$UNIT_SEP${it.value}" }

        fun decodeInputValues(encoded: String?): Map<String, String> =
            if (encoded.isNullOrEmpty()) emptyMap()
            else encoded.split(RECORD_SEP).associate { it.substringBefore(UNIT_SEP) to it.substringAfter(UNIT_SEP, "") }
```

In `doWork()`, after `val profileId = …` add:

```kotlin
        val inputValues = decodeInputValues(inputData.getString(KEY_INPUT_VALUES))
```

Change `performUpload(file, destination, resolvedName, profileId)` to `performUpload(file, destination, resolvedName, profileId, inputValues)`, add the parameter `inputValues: Map<String, String> = emptyMap()` to `performUpload`, and in its CUSTOM_HTTP branch call:

```kotlin
                customHttpUploader.upload(file, config, resolvedName, inputValues = inputValues)
```

- [ ] **Step 5: Run the codec test**

Run: `./gradlew :feature:upload:testDebugUnitTest --tests '*InputValuesCodecTest*'`
Expected: PASS (2 tests)

- [ ] **Step 6: ViewModel prompts** — in `UploadViewModel.kt`:

Add imports `com.xerahs.android.core.common.sxcu.InputPrompt`, `com.xerahs.android.core.common.sxcu.ShareXSyntax`, `com.xerahs.android.core.domain.model.UploadConfig`.

Add to `UploadUiState`:

```kotlin
    val pendingPrompts: List<InputPrompt> = emptyList(),
```

Add fields to the ViewModel:

```kotlin
    private var pendingPaths: List<String> = emptyList()
    private var lastInputValues: Map<String, String> = emptyMap()
```

In `uploadAnyway()` change the enqueue call to reuse the answers:

```kotlin
        enqueueUpload(listOf(dupInfo.imagePath), skipDuplicateCheck = true, inputValues = lastInputValues)
```

Replace `upload` and `uploadBatch` with:

```kotlin
    fun upload(imagePath: String) = startUpload(listOf(imagePath))

    fun uploadBatch(imagePaths: List<String>) = startUpload(imagePaths)

    /** Asks for {inputbox} values first when the selected custom uploader needs them. */
    private fun startUpload(paths: List<String>) {
        viewModelScope.launch {
            val prompts = customUploaderPrompts()
            if (prompts.isEmpty()) {
                enqueueUpload(paths)
            } else {
                pendingPaths = paths
                _uiState.value = _uiState.value.copy(pendingPrompts = prompts)
            }
        }
    }

    private suspend fun customUploaderPrompts(): List<InputPrompt> {
        if (_uiState.value.selectedDestination != UploadDestination.CUSTOM_HTTP) return emptyList()
        val config = _uiState.value.selectedProfileId
            ?.let { profileRepository.getProfileConfig(it, UploadDestination.CUSTOM_HTTP) }
            as? UploadConfig.CustomUploaderConfig
            ?: settingsRepository.getCustomUploaderConfig()
        return ShareXSyntax.inputPrompts(config.spec.requestTemplates())
    }

    fun submitPromptValues(values: Map<String, String>) {
        lastInputValues = values
        _uiState.value = _uiState.value.copy(pendingPrompts = emptyList())
        enqueueUpload(pendingPaths, inputValues = values)
    }

    fun cancelPrompts() {
        _uiState.value = _uiState.value.copy(pendingPrompts = emptyList())
    }
```

Change `enqueueUpload`'s signature to:

```kotlin
    private fun enqueueUpload(
        imagePaths: List<String>,
        skipDuplicateCheck: Boolean = false,
        inputValues: Map<String, String> = emptyMap()
    ) {
```

and directly after the `_uiState.value.selectedProfileId?.let { … }` block add:

```kotlin
            if (inputValues.isNotEmpty()) {
                inputDataBuilder.putString(UploadWorker.KEY_INPUT_VALUES, UploadWorker.encodeInputValues(inputValues))
            }
```

- [ ] **Step 7: Prompt dialog** — in `UploadScreen.kt`, right after the duplicate-detection dialog block, add:

```kotlin
    if (uiState.pendingPrompts.isNotEmpty()) {
        val answers = remember(uiState.pendingPrompts) {
            mutableStateMapOf<String, String>().apply { uiState.pendingPrompts.forEach { put(it.title, it.default) } }
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPrompts() },
            title = { Text("Uploader needs input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.pendingPrompts.forEach { prompt ->
                        OutlinedTextField(
                            value = answers[prompt.title].orEmpty(),
                            onValueChange = { answers[prompt.title] = it },
                            label = { Text(prompt.title.ifBlank { "Value" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.submitPromptValues(answers.toMap()) }) { Text("Upload") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelPrompts() }) { Text("Cancel") } }
        )
    }
```

Add any missing imports (`androidx.compose.runtime.mutableStateMapOf`, `androidx.compose.material3.OutlinedTextField`, `androidx.compose.material3.TextButton`, `androidx.compose.foundation.layout.Arrangement`).

- [ ] **Step 8: Compile, test, check on device**

Run: `./gradlew assembleDebug testDebugUnitTest`, then `./gradlew installDebug`.
Set the global custom uploader to a spec whose headers contain `{inputbox:Token}` and RequestURL `https://httpbin.org/post`, URL `{json:headers.Authorization}`, header `Authorization: Bearer {inputbox:Token}`. Upload an image.
Expected: a dialog asks for "Token"; after entering `abc` the upload succeeds and the result URL is `Bearer abc`.

- [ ] **Step 9: Commit**

```bash
git add feature/upload
git commit -m "feat(upload): prompt for {inputbox} values before custom uploads"
```

---

### Task 15: Redact secrets from HTTP logs

`app/.../di/NetworkModule.kt:26` installs `HttpLoggingInterceptor` at `HEADERS` level in **all** builds, so `Authorization` (and now arbitrary custom-uploader secret headers/query keys) reach logcat. Redact them, and only log in debug builds.

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/net/LogRedactor.kt`
- Test: `core/common/src/test/java/com/xerahs/android/core/common/net/LogRedactorTest.kt`
- Modify: `app/src/main/java/com/xerahs/android/di/NetworkModule.kt:26-28`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.xerahs.android.core.common.net

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactorTest {
    @Test fun redactsSensitiveHeaders() {
        assertEquals("Authorization: ██", LogRedactor.redact("Authorization: Bearer abc"))
        assertEquals("X-API-Key: ██", LogRedactor.redact("X-API-Key: 123"))
        assertEquals("x-amz-security-token: ██", LogRedactor.redact("x-amz-security-token: t"))
        assertEquals("Set-Cookie: ██", LogRedactor.redact("Set-Cookie: s=1"))
    }

    @Test fun keepsHarmlessHeaders() {
        assertEquals("Content-Type: image/png", LogRedactor.redact("Content-Type: image/png"))
        assertEquals("Keep-Alive: timeout=5", LogRedactor.redact("Keep-Alive: timeout=5"))
    }

    @Test fun redactsSensitiveQueryParametersInRequestLine() = assertEquals(
        "--> POST https://h.test/up?api_key=██&ttl=60&X-Amz-Signature=██",
        LogRedactor.redact("--> POST https://h.test/up?api_key=k1&ttl=60&X-Amz-Signature=sig")
    )
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*LogRedactorTest*'`
Expected: FAIL — `Unresolved reference: LogRedactor`

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.common.net

/** Masks secrets in OkHttp log lines: sensitive header values and query parameter values. */
object LogRedactor {
    private val SENSITIVE = Regex("(?i)(auth|token|secret|passw|apikey|api[-_]?key|x-api|cookie|session|signature|credential)")
    private val QUERY_PARAM = Regex("""([?&])([^=&\s]+)=([^&\s]*)""")
    private const val MASK = "██"

    fun redact(line: String): String {
        val colon = line.indexOf(": ")
        if (colon > 0 && !line.startsWith("-->") && !line.startsWith("<--") &&
            SENSITIVE.containsMatchIn(line.substring(0, colon))
        ) return line.substring(0, colon) + ": " + MASK
        return QUERY_PARAM.replace(line) { m ->
            if (SENSITIVE.containsMatchIn(m.groupValues[2])) "${m.groupValues[1]}${m.groupValues[2]}=$MASK"
            else m.value
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*LogRedactorTest*'`
Expected: PASS (3 tests)

- [ ] **Step 5: Use it** — in `NetworkModule.kt` replace

```kotlin
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
```

with

```kotlin
        .addInterceptor(HttpLoggingInterceptor { line ->
            android.util.Log.d("OkHttp", LogRedactor.redact(line))
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        })
```

with imports `com.xerahs.android.BuildConfig` and `com.xerahs.android.core.common.net.LogRedactor`. (If `BuildConfig` is unresolved, add `buildFeatures { buildConfig = true }` to `app/build.gradle.kts`'s `android { }` block.)

- [ ] **Step 6: Compile and commit**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

```bash
git add core/common/src/main/java/com/xerahs/android/core/common/net core/common/src/test/java/com/xerahs/android/core/common/net app/src/main/java/com/xerahs/android/di/NetworkModule.kt app/build.gradle.kts
git commit -m "fix(security): redact secrets from HTTP logs and disable logging in release"
```

---

### Task 16: Part 1 verification

- [ ] **Step 1: Full test + lint + release build**

Run: `./gradlew testDebugUnitTest lint assembleRelease`
Expected: `BUILD SUCCESSFUL`. Lint must report no new errors in touched files.

- [ ] **Step 2: R8 check** — the new parsing code uses Gson's tree API (`JsonParser`/`JsonObject`) only, not reflection, so no new keep rules are needed. Confirm by installing the release APK and running one end-to-end custom upload:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Expected: app starts; Settings → Custom uploader shows the saved definition; an upload through an imported profile succeeds (use `https://httpbin.org/post` with `"URL": "{json:url}"`).

- [ ] **Step 3: Commit any lint fixes**

```bash
git add -A && git commit -m "chore: lint fixes for uploader interop part 1"
```

(skip if nothing changed)
