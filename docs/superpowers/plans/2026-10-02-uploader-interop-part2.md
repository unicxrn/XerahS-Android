# Uploader Interop — Part 2: Any-File Uploads & HEIC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept any file type from the Android share sheet, upload non-images straight to compatible destinations (skipping the editor), convert HEIC/HEIF to PNG before upload, and record each upload's MIME type in history.

**Architecture:** `MimeTypes` (from Part 1, `core:common`) classifies files by extension. A pure `DestinationCapabilities` object in `core:domain` decides which destinations/profiles accept a set of files; the Upload screen filters its picker with it. `UploadWorker` only runs image processing on raster images, converts HEIC via `HeicConverter` (`ImageDecoder`, API 28+), passes text content as `{input}` to custom uploaders, and stores `mimeType` in history (Room v3 → v4). Home/Share/History render a `FileTypeTile` for non-images.

**Tech Stack:** Kotlin, Room 2.6.1 (KSP), WorkManager, Jetpack Compose M3, `android.graphics.ImageDecoder`, JUnit 4.

**Prerequisite:** Part 1 (`2026-10-02-uploader-interop-part1.md`) is complete — this plan uses `MimeTypes`, `CustomDestinationType`, `UploadConfig.CustomUploaderConfig`, `settingsRepository.getCustomUploaderConfig()`, and `CustomHttpUploader.upload(…, input = …)`.

**Spec:** `docs/superpowers/specs/2026-10-02-uploader-interop-design.md` §2 "Any-file upload" + "Room", §3 error copy, §4.

**Refinements vs spec:**
- `MigrationTestHelper` needs exported schemas, which this project has never had (`exportSchema = false`). This plan turns schema export on (so future migrations can be tested) and verifies 3→4 with an on-device upgrade install instead.
- WorkManager key **constants** are renamed (`KEY_FILE_PATH(S)`) but keep their old **string values** (`"image_path"`, `"image_paths"`) so work queued by the previous version still runs after the update.
- GIFs skip re-encoding (the existing pipeline would flatten them to a single JPEG frame) — found while planning.

**Commands:** `./gradlew :core:domain:testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew installDebug`.

---

## File structure

- Create: `core/domain/src/main/java/com/xerahs/android/core/domain/model/DestinationCapabilities.kt` (+ test)
- Create: `core/common/src/main/java/com/xerahs/android/core/common/HeicConverter.kt`
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/FileTypeTile.kt`
- Modify: `core/data/build.gradle.kts`, `core/data/.../local/db/{HistoryEntity,XerahSDatabase,Migrations}.kt`, `app/.../di/DatabaseModule.kt`
- Modify: `core/domain/.../model/HistoryItem.kt`
- Modify: `core/data/.../local/datastore/SettingsDataStore.kt`, `core/domain/.../repository/SettingsRepository.kt`, `core/data/.../repository/SettingsRepositoryImpl.kt`
- Modify: `feature/settings/.../SettingsViewModel.kt`, `feature/settings/.../UploadSettingsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/.../ui/MainActivity.kt`
- Modify: `feature/upload/.../worker/UploadWorker.kt`, `feature/upload/.../UploadViewModel.kt`, `feature/upload/.../UploadScreen.kt`
- Modify: `feature/history/.../home/HomeScreen.kt`, `feature/history/.../home/ShareCard.kt`, `feature/history/.../HistoryScreen.kt`

---

### Task 1: History `mimeType` column (Room 3 → 4)

**Files:**
- Modify: `core/data/build.gradle.kts`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/db/HistoryEntity.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/db/XerahSDatabase.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/db/Migrations.kt`
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/model/HistoryItem.kt`
- Modify: `app/src/main/java/com/xerahs/android/di/DatabaseModule.kt:32`

- [ ] **Step 1: Seed an upgrade-test device**

With the Part-1 build installed (`./gradlew installDebug` from the commit before this task), upload two images so history has rows. These must survive the migration.

- [ ] **Step 2: Turn on schema export** — in `core/data/build.gradle.kts`, after the `android { … }` block, add:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

and in `XerahSDatabase.kt` change `exportSchema = false` to `exportSchema = true`.

- [ ] **Step 3: Domain field** — in `HistoryItem.kt` add, after `val fileHash: String? = null`:

```kotlin
    val fileHash: String? = null,
    /** MIME type of the uploaded file; null/"image/*" for rows created before v0.5. */
    val mimeType: String? = null
) {
    val isImage: Boolean
        get() = mimeType == null || com.xerahs.android.core.common.file.MimeTypes.isRasterImage(mimeType)
}
```

(i.e. add a comma after `fileHash`, the new property, and turn the class into one with a body; the closing `)` of the constructor moves accordingly.)

- [ ] **Step 4: Entity** — in `HistoryEntity.kt` add the constructor property `val mimeType: String? = null` after `fileHash`, `mimeType = mimeType` in `toDomain()`, and `mimeType = item.mimeType` in `fromDomain()`.

- [ ] **Step 5: Migration** — append to `Migrations.kt`:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN mimeType TEXT")
        // Everything uploaded before v0.5 was an image.
        db.execSQL("UPDATE history SET mimeType = 'image/*' WHERE mimeType IS NULL")
    }
}
```

In `XerahSDatabase.kt` set `version = 4`. In `DatabaseModule.kt` import `MIGRATION_3_4` and change the builder call to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 6: Build and commit the generated schema**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` and a new file `core/data/schemas/com.xerahs.android.core.data.local.db.XerahSDatabase/4.json`.

- [ ] **Step 7: Verify the upgrade on device**

Run: `./gradlew installDebug` (install over the seeded app — do not uninstall), open the app.
Expected: no crash; the two history rows from Step 1 are still on Home with thumbnails. A crash with `Migration didn't properly handle: history` means the entity and SQL disagree — the column must be `TEXT`, nullable, no default.

- [ ] **Step 8: Commit**

```bash
git add core/data/build.gradle.kts core/data/schemas core/data/src/main/java/com/xerahs/android/core/data/local/db core/domain/src/main/java/com/xerahs/android/core/domain/model/HistoryItem.kt app/src/main/java/com/xerahs/android/di/DatabaseModule.kt
git commit -m "feat(data): add history mimeType column (Room v4) and export schemas"
```

---

### Task 2: Destination capability rules

**Files:**
- Create: `core/domain/src/main/java/com/xerahs/android/core/domain/model/DestinationCapabilities.kt`
- Test: `core/domain/src/test/java/com/xerahs/android/core/domain/model/DestinationCapabilitiesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.sxcu.CustomDestinationType.FILE
import com.xerahs.android.core.common.sxcu.CustomDestinationType.IMAGE
import com.xerahs.android.core.common.sxcu.CustomDestinationType.TEXT
import com.xerahs.android.core.common.sxcu.CustomDestinationType.URL_SHORTENER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationCapabilitiesTest {
    private fun accepts(d: UploadDestination, mime: String, types: Set<com.xerahs.android.core.common.sxcu.CustomDestinationType>? = null) =
        DestinationCapabilities.accepts(d, mime, types)

    @Test fun imgurTakesImagesOnly() {
        assertTrue(accepts(UploadDestination.IMGUR, "image/png"))
        assertFalse(accepts(UploadDestination.IMGUR, "application/pdf"))
        assertFalse(accepts(UploadDestination.IMGUR, "text/plain"))
    }

    @Test fun storageDestinationsTakeAnything() {
        listOf(UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP, UploadDestination.LOCAL).forEach {
            assertTrue(accepts(it, "application/zip"))
        }
    }

    @Test fun customUploaderFollowsDestinationTypes() {
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(IMAGE)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "application/pdf", setOf(IMAGE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "application/pdf", setOf(FILE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(FILE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "text/plain", setOf(TEXT)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "text/plain", setOf(IMAGE)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(URL_SHORTENER)))
    }

    @Test fun unknownCustomTypesAreAllowed() =
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "video/mp4", null))

    @Test fun batchNeedsEveryFileAccepted() {
        val mimes = listOf("image/png", "application/pdf")
        assertFalse(DestinationCapabilities.acceptsAll(UploadDestination.IMGUR, mimes))
        assertTrue(DestinationCapabilities.acceptsAll(UploadDestination.S3, mimes))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests '*DestinationCapabilitiesTest*'`
Expected: FAIL — `Unresolved reference: DestinationCapabilities`

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.sxcu.CustomDestinationType

/** Which destinations can receive which kinds of file. */
object DestinationCapabilities {
    /**
     * @param customTypes the custom uploader's destination types; null when unknown (treated as "accepts").
     */
    fun accepts(
        destination: UploadDestination,
        mimeType: String,
        customTypes: Set<CustomDestinationType>? = null,
    ): Boolean = when (destination) {
        UploadDestination.IMGUR -> MimeTypes.isRasterImage(mimeType)
        UploadDestination.CUSTOM_HTTP -> customTypes == null || when {
            MimeTypes.isRasterImage(mimeType) ->
                CustomDestinationType.IMAGE in customTypes || CustomDestinationType.FILE in customTypes
            MimeTypes.isText(mimeType) ->
                CustomDestinationType.TEXT in customTypes || CustomDestinationType.FILE in customTypes
            else -> CustomDestinationType.FILE in customTypes
        }
        UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP, UploadDestination.LOCAL -> true
    }

    fun acceptsAll(
        destination: UploadDestination,
        mimeTypes: List<String>,
        customTypes: Set<CustomDestinationType>? = null,
    ): Boolean = mimeTypes.all { accepts(destination, it, customTypes) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests '*DestinationCapabilitiesTest*'`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add core/domain/src/main/java/com/xerahs/android/core/domain/model/DestinationCapabilities.kt core/domain/src/test/java/com/xerahs/android/core/domain/model/DestinationCapabilitiesTest.kt
git commit -m "feat(domain): destination capability rules per file type"
```

---

### Task 3: HEIC → PNG converter + setting

**Files:**
- Create: `core/common/src/main/java/com/xerahs/android/core/common/HeicConverter.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/local/datastore/SettingsDataStore.kt`
- Modify: `core/domain/src/main/java/com/xerahs/android/core/domain/repository/SettingsRepository.kt`
- Modify: `core/data/src/main/java/com/xerahs/android/core/data/repository/SettingsRepositoryImpl.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/xerahs/android/feature/settings/UploadSettingsScreen.kt`

- [ ] **Step 1: Converter** (Android-only API; verified on device in Task 5)

```kotlin
package com.xerahs.android.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.io.FileOutputStream

object HeicConverter {
    /**
     * Decodes a HEIC/HEIF file and writes it as PNG into the cache dir.
     * Returns null when unsupported (API < 28) or decoding fails — callers upload the original.
     */
    fun toPng(context: Context, source: File): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source))
            val dir = File(context.cacheDir, "heic_${System.currentTimeMillis()}").apply { mkdirs() }
            val out = File(dir, source.nameWithoutExtension + ".png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            out
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 2: Setting plumbing** — `SettingsDataStore.Keys` add `val CONVERT_HEIC = booleanPreferencesKey("convert_heic_to_png")`, and add:

```kotlin
    fun getConvertHeicToPng(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONVERT_HEIC] ?: true
    }

    suspend fun setConvertHeicToPng(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CONVERT_HEIC] = enabled }
    }
```

`SettingsRepository.kt` add:

```kotlin
    fun getConvertHeicToPng(): Flow<Boolean>
    suspend fun setConvertHeicToPng(enabled: Boolean)
```

`SettingsRepositoryImpl.kt` add:

```kotlin
    override fun getConvertHeicToPng(): Flow<Boolean> = settingsDataStore.getConvertHeicToPng()

    override suspend fun setConvertHeicToPng(enabled: Boolean) = settingsDataStore.setConvertHeicToPng(enabled)
```

- [ ] **Step 3: Settings UI** — in `SettingsViewModel.kt` add `val convertHeicToPng: Boolean = true,` to the UI state next to `stripExif`, a collector next to the `getStripExif()` one:

```kotlin
            launch {
                settingsRepository.getConvertHeicToPng().collect { enabled ->
                    _uiState.value = _uiState.value.copy(convertHeicToPng = enabled)
                }
            }
```

and:

```kotlin
    fun setConvertHeicToPng(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConvertHeicToPng(enabled) }
    }
```

In `UploadSettingsScreen.kt`, directly after the "Strip EXIF metadata" `ListItem`, add:

```kotlin
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                ListItem(
                    headlineContent = { Text("Convert HEIC to PNG") },
                    supportingContent = { Text("Most hosts can't display HEIC photos (Android 9+)") },
                    trailingContent = {
                        Switch(
                            checked = uiState.convertHeicToPng,
                            onCheckedChange = { viewModel.setConvertHeicToPng(it) }
                        )
                    }
                )
```

- [ ] **Step 4: Compile**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/common core/data core/domain feature/settings
git commit -m "feat: HEIC to PNG converter with opt-out setting"
```

---

### Task 4: Share any file into the app

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/xerahs/android/ui/MainActivity.kt`

- [ ] **Step 1: Manifest** — in the two share filters, change `<data android:mimeType="image/*" />` to `<data android:mimeType="*/*" />` and update the comments to `<!-- Receive shared files -->` / `<!-- Receive multiple shared files -->`.

- [ ] **Step 2: Replace the share state** — in `MainActivity`, replace

```kotlin
    private var pendingSharedImagePath by mutableStateOf<String?>(null)
    private var pendingSharedImagePaths by mutableStateOf<List<String>?>(null)
```

with

```kotlin
    private var pendingSharedPaths by mutableStateOf<List<String>?>(null)
```

and the `MainScreen(` arguments

```kotlin
                            sharedImagePath = pendingSharedImagePath,
                            sharedImagePaths = pendingSharedImagePaths,
                            onSharedImageHandled = {
                                pendingSharedImagePath = null
                                pendingSharedImagePaths = null
                            },
```

with

```kotlin
                            sharedPaths = pendingSharedPaths,
                            onSharedHandled = { pendingSharedPaths = null },
```

- [ ] **Step 3: Rewrite intent handling** — replace `handleIncomingIntent` and `copyUriToInternal` entirely with:

```kotlin
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { pendingImportUri = it.toString() }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    val name = displayName(uri)
                    if (isUploaderConfig(name)) {
                        pendingImportUri = uri.toString()
                        return
                    }
                    copyUriToInternal(uri, name)?.let { pendingSharedPaths = listOf(it) }
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                    pendingSharedPaths = listOf(writeSharedText(text))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return
                val paths = uris.mapNotNull { copyUriToInternal(it, displayName(it)) }
                if (paths.isNotEmpty()) pendingSharedPaths = paths
            }
            ACTION_CAPTURE -> pendingLaunchCapture = true
        }
    }

    private fun isUploaderConfig(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase() in setOf("sxcu", "xsdc")

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** Copies a shared item into app storage, keeping its real name (and so its extension). */
    private fun copyUriToInternal(uri: Uri, displayName: String?): String? = try {
        val dir = File(filesDir, "captures/shared_${System.currentTimeMillis()}_${(0..9999).random()}")
            .apply { mkdirs() }
        var name = (displayName ?: "shared").replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (!name.contains('.')) {
            val ext = contentResolver.getType(uri)?.let { MimeTypes.extensionFor(it) } ?: "bin"
            name = "$name.$ext"
        }
        val file = File(dir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { out -> input.copyTo(out) }
        } ?: throw java.io.FileNotFoundException(uri.toString())
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    private fun writeSharedText(text: String): String {
        val dir = File(filesDir, "captures/shared_${System.currentTimeMillis()}").apply { mkdirs() }
        return File(dir, "shared-text.txt").apply { writeText(text) }.absolutePath
    }
```

Add imports: `android.provider.OpenableColumns`, `androidx.core.content.IntentCompat`, `com.xerahs.android.core.common.file.MimeTypes`. (`pendingImportUri` exists from Part 1, Task 13 — delete the old standalone `ACTION_VIEW` `if` block added there, since it is now the first `when` branch.)

- [ ] **Step 4: Route by file type** — in `MainScreen`, replace the parameters

```kotlin
    sharedImagePath: String? = null,
    sharedImagePaths: List<String>? = null,
    onSharedImageHandled: () -> Unit = {},
```

with

```kotlin
    sharedPaths: List<String>? = null,
    onSharedHandled: () -> Unit = {},
```

and replace the two share `LaunchedEffect`s with:

```kotlin
    LaunchedEffect(sharedPaths) {
        val paths = sharedPaths ?: return@LaunchedEffect
        val single = paths.singleOrNull()
        val mime = single?.let { MimeTypes.fromFileName(it) }
        when {
            // Editable raster image → editor (HEIC goes straight to upload; it is converted there).
            single != null && MimeTypes.isRasterImage(mime) && !MimeTypes.isHeic(mime) ->
                navController.navigate(Screen.Annotation.createRoute(single))
            single != null -> navController.navigate(Screen.Upload.createRoute(single))
            else -> navController.navigate(Screen.UploadBatch.createRoute(paths))
        }
        onSharedHandled()
    }
```

(import `com.xerahs.android.core.common.file.MimeTypes` in this file if `MainScreen` is in the same file — it is.)

- [ ] **Step 5: Compile and check on device**

Run: `./gradlew installDebug`. From a file manager share (a) a PNG, (b) a PDF, (c) a text snippet from a notes app, (d) two files of different types.
Expected: (a) opens the editor; (b) opens Upload with no editor; (c) opens Upload for `shared-text.txt`; (d) opens the batch Upload screen. The Upload screen may still show a blank preview for (b)/(c) — Task 6 fixes that.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/xerahs/android/ui/MainActivity.kt
git commit -m "feat: accept any file type and shared text from the share sheet"
```

---

### Task 5: UploadWorker handles any file

**Files:**
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/worker/UploadWorker.kt`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadViewModel.kt` (key renames only)

- [ ] **Step 1: Rename keys, keep values** — in the companion object replace

```kotlin
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_IMAGE_PATHS = "image_paths"
```

with

```kotlin
        // String values kept from the image-only era so work queued by older versions still runs.
        const val KEY_FILE_PATH = "image_path"
        const val KEY_FILE_PATHS = "image_paths"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_TEXT_INPUT_BYTES = 1_000_000L
```

Then replace every `KEY_IMAGE_PATH` with `KEY_FILE_PATH` and `KEY_IMAGE_PATHS` with `KEY_FILE_PATHS` in `UploadWorker.kt` and `UploadViewModel.kt` (both are plain renames).

- [ ] **Step 2: Rewrite the per-file body** — in `doWork()`, replace everything from `val file = prepareFile(originalFile)` through the end of the `if (uploadResult.success) { … } else { … }` block with:

```kotlin
            val originalMime = MimeTypes.fromFileName(originalFile.name)
            val heicPng = if (MimeTypes.isHeic(originalMime) && settingsRepository.getConvertHeicToPng().first()) {
                HeicConverter.toPng(appContext, originalFile)
            } else null
            val source = heicPng ?: originalFile
            val mimeType = if (heicPng != null) "image/png" else originalMime

            val file = if (isProcessableImage(mimeType)) prepareFile(source) else source
            val pattern = settingsRepository.getFileNamingPattern().first()
            val resolvedName = FileNamePattern.resolve(pattern, source.name)
            val textInput = if (MimeTypes.isText(mimeType) && source.length() <= MAX_TEXT_INPUT_BYTES) {
                source.readText()
            } else ""

            val uploadResult = performUpload(file, destination, resolvedName, profileId, inputValues, textInput)

            if (uploadResult.success) {
                val thumbnailPath = if (MimeTypes.isRasterImage(mimeType)) {
                    ThumbnailGenerator.generate(appContext, source)
                } else null
                val itemId = generateId()
                val historyItem = HistoryItem(
                    id = itemId,
                    filePath = path,
                    thumbnailPath = thumbnailPath,
                    url = uploadResult.url,
                    deleteUrl = uploadResult.deleteUrl,
                    uploadDestination = destination,
                    timestamp = generateTimestamp(),
                    fileName = resolvedName,
                    fileSize = originalFile.length(),
                    albumId = albumId,
                    fileHash = fileHash,
                    mimeType = mimeType
                )
                historyRepository.insertHistoryItem(historyItem)
                for (tagId in tagIds) {
                    tagRepository.addTagToHistory(itemId, tagId)
                }
                uploadResult.url?.let { urls.add(it) }
            }

            // Clean up temp files
            if (file != source) file.delete()
            heicPng?.parentFile?.deleteRecursively()

            if (!uploadResult.success) {
                val message = uploadResult.errorMessage ?: "Upload failed"
                postFailureNotification(message)
                return if (runAttemptCount < 3) Result.retry()
                else Result.failure(Data.Builder().putString(KEY_ERROR_MESSAGE, message).build())
            }
```

- [ ] **Step 3: Helpers** — add to the class:

```kotlin
    /** Raster images we can safely re-encode. GIFs would lose animation; HEIC is converted separately. */
    private fun isProcessableImage(mimeType: String): Boolean =
        MimeTypes.isRasterImage(mimeType) && !MimeTypes.isHeic(mimeType) && mimeType != "image/gif"
```

Extend `performUpload`'s signature with `textInput: String = ""` (after `inputValues`) and pass it in the CUSTOM_HTTP branch:

```kotlin
                customHttpUploader.upload(file, config, resolvedName, input = textInput, inputValues = inputValues)
```

Add imports `com.xerahs.android.core.common.HeicConverter` and `com.xerahs.android.core.common.file.MimeTypes`.

- [ ] **Step 4: Surface the error message** — in `UploadViewModel`'s `WorkInfo.State.FAILED` non-duplicate branch, replace both `"Upload failed"` literals with a value read once:

```kotlin
                        } else {
                            val message = workInfo.outputData.getString(UploadWorker.KEY_ERROR_MESSAGE) ?: "Upload failed"
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                result = UploadResult(
                                    success = false,
                                    errorMessage = message,
                                    destination = destination
                                ),
                                batchProgress = null,
                                errorMessage = message
                            )
                        }
```

- [ ] **Step 5: Compile and check on device**

Run: `./gradlew installDebug`. Configure S3 (or Local). Share a PDF → Upload.
Expected: upload succeeds; the history row has `fileName` ending `.pdf`. Share an animated GIF with JPEG/quality < 100 set in settings → uploaded file is still an animated GIF. On an Android 9+ device share a HEIC photo → uploaded object is a `.png`. Point a custom uploader at an invalid host → after retries the Upload screen shows the real error (e.g. `Network error: …`) instead of "Upload failed".

- [ ] **Step 6: Commit**

```bash
git add feature/upload
git commit -m "feat(upload): upload any file type, convert HEIC, keep GIFs, report errors"
```

---

### Task 6: Upload screen — file cards and filtered destinations

**Files:**
- Create: `core/ui/src/main/java/com/xerahs/android/core/ui/FileTypeTile.kt`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadViewModel.kt`
- Modify: `feature/upload/src/main/java/com/xerahs/android/feature/upload/UploadScreen.kt`

- [ ] **Step 1: Shared tile**

```kotlin
package com.xerahs.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun fileTypeIcon(mimeType: String?): ImageVector = when {
    mimeType == null -> Icons.Default.InsertDriveFile
    mimeType.startsWith("image/") -> Icons.Default.Image
    mimeType.startsWith("video/") -> Icons.Default.Movie
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml" ->
        Icons.Default.Description
    mimeType.contains("zip") -> Icons.Default.FolderZip
    else -> Icons.Default.InsertDriveFile
}

/** Placeholder for files that have no image preview. Size it with [modifier]. */
@Composable
fun FileTypeTile(mimeType: String?, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fileTypeIcon(mimeType),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize)
        )
    }
}
```

- [ ] **Step 2: ViewModel state** — in `UploadViewModel.kt` add imports `com.xerahs.android.core.common.file.MimeTypes`, `com.xerahs.android.core.common.sxcu.CustomDestinationType`, `com.xerahs.android.core.domain.model.DestinationCapabilities`. Add to `UploadUiState`:

```kotlin
    val fileMimeTypes: List<String> = emptyList(),
    val globalCustomTypes: Set<CustomDestinationType>? = null,
    val profileCustomTypes: Map<String, Set<CustomDestinationType>> = emptyMap(),
```

and give `UploadUiState` a body (after its constructor `)`):

```kotlin
{
    fun allows(destination: UploadDestination): Boolean = fileMimeTypes.isEmpty() ||
        DestinationCapabilities.acceptsAll(
            destination, fileMimeTypes,
            if (destination == UploadDestination.CUSTOM_HTTP) globalCustomTypes else null
        )

    fun allows(profile: UploadProfile): Boolean = fileMimeTypes.isEmpty() ||
        DestinationCapabilities.acceptsAll(profile.destination, fileMimeTypes, profileCustomTypes[profile.id])
}
```

In `init`, change the default-destination launch to:

```kotlin
        viewModelScope.launch {
            val defaultDest = settingsRepository.getDefaultDestination().first()
            _uiState.value = _uiState.value.copy(
                selectedDestination = defaultDest,
                globalCustomTypes = settingsRepository.getCustomUploaderConfig().spec.destinationTypes
            )
            ensureAllowedSelection()
        }
```

and the profiles collector to:

```kotlin
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                val customTypes = profiles
                    .filter { it.destination == UploadDestination.CUSTOM_HTTP }
                    .associate { p ->
                        p.id to ((profileRepository.getProfileConfig(p.id, UploadDestination.CUSTOM_HTTP)
                            as? UploadConfig.CustomUploaderConfig)?.spec?.destinationTypes ?: emptySet())
                    }
                _uiState.value = _uiState.value.copy(profiles = profiles, profileCustomTypes = customTypes)
                ensureAllowedSelection()
            }
        }
```

Add:

```kotlin
    fun setFiles(paths: List<String>) {
        val mimes = paths.map { MimeTypes.fromFileName(it) }
        if (mimes == _uiState.value.fileMimeTypes) return
        _uiState.value = _uiState.value.copy(fileMimeTypes = mimes)
        ensureAllowedSelection()
    }

    /** If the current destination/profile can't take these files, fall back to the first one that can. */
    private fun ensureAllowedSelection() {
        val s = _uiState.value
        val profile = s.profiles.find { it.id == s.selectedProfileId }
        val ok = if (profile != null) s.allows(profile) else s.allows(s.selectedDestination)
        if (!ok) {
            val fallback = UploadDestination.entries.firstOrNull { s.allows(it) } ?: UploadDestination.LOCAL
            _uiState.value = s.copy(selectedProfileId = null, selectedDestination = fallback)
        }
    }
```

- [ ] **Step 3: Screen** — in `UploadScreen.kt` (import `com.xerahs.android.core.common.file.MimeTypes`, `com.xerahs.android.core.ui.FileTypeTile`, and `androidx.compose.material3.TextButton` if missing):

(a) Right after `val isBatch = imagePaths.size > 1` add:

```kotlin
    LaunchedEffect(imagePath, imagePaths) {
        viewModel.setFiles(if (isBatch) imagePaths else listOf(imagePath))
    }
    val mimeType = remember(imagePath) { MimeTypes.fromFileName(imagePath) }
    val isImage = MimeTypes.isRasterImage(mimeType)
```

(b) Guard decoding: change `BitmapFactory.decodeFile(imagePath)` (the `bitmap` remember) to `if (isImage) BitmapFactory.decodeFile(imagePath) else null`.

(c) Batch header: `"${imagePaths.size} images selected"` → `"${imagePaths.size} files selected"`. In the batch `items(imagePaths) { path -> … }`, replace the `thumb?.let { Image(…) }` block with:

```kotlin
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Selected file",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        FileTypeTile(MimeTypes.fromFileName(path), Modifier.fillMaxSize(), iconSize = 40.dp)
                                    }
```

and change `val thumb = remember(path) { BitmapFactory.decodeFile(path) }` to `val thumb = remember(path) { if (MimeTypes.isRasterImage(MimeTypes.fromFileName(path))) BitmapFactory.decodeFile(path) else null }`.

(d) Single preview: replace the `bitmap?.let { Image(…) }` block with:

```kotlin
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FileTypeTile(
                                    mimeType,
                                    Modifier.size(96.dp).clip(MaterialTheme.shapes.large),
                                    iconSize = 48.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = java.io.File(imagePath).name,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
```

(e) Copyable error: directly after the failure `StatusBanner(…)`, add:

```kotlin
                uiState.result?.errorMessage?.let { message ->
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(message)) }) {
                        Text("Copy error details")
                    }
                }
```

(f) Destination sheet filtering: in `DestinationSheetContent`, change `UploadDestination.entries.forEach { dest ->` to `UploadDestination.entries.filter { uiState.allows(it) }.forEach { dest ->`, and change

```kotlin
        val destProfiles = uiState.profiles.filter { it.destination == uiState.selectedDestination }
```

to

```kotlin
        val destProfiles = uiState.profiles.filter { it.destination == uiState.selectedDestination && uiState.allows(it) }
```


- [ ] **Step 4: Compile and check on device**

Run: `./gradlew installDebug`. Share a PDF.
Expected: Upload screen shows a PDF tile + file name; the destination sheet does not list Imgur, nor custom profiles that are Image-only; Text-only custom profiles appear when sharing text. Share a PNG: all destinations listed, image preview as before.

- [ ] **Step 5: Commit**

```bash
git add core/ui feature/upload
git commit -m "feat(upload): file cards and type-aware destination picker"
```

---

### Task 7: File tiles in Home, Share card and History

**Files:**
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/HomeScreen.kt:190-198`
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/home/ShareCard.kt:118-127`
- Modify: `feature/history/src/main/java/com/xerahs/android/feature/history/HistoryScreen.kt:171-180`

- [ ] **Step 1: Home row** — replace the `AsyncImage(model = item.thumbnailPath ?: item.filePath, …)` call with:

```kotlin
        val thumbModifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
        if (item.isImage) {
            AsyncImage(
                model = item.thumbnailPath ?: item.filePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier
            )
        } else {
            FileTypeTile(item.mimeType, thumbModifier)
        }
```

- [ ] **Step 2: Share card** — replace its thumbnail `AsyncImage(…)` with:

```kotlin
            val thumbModifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
            if (item.isImage) {
                AsyncImage(
                    model = item.thumbnailPath ?: item.filePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = thumbModifier
                )
            } else {
                FileTypeTile(item.mimeType, thumbModifier, iconSize = 56.dp)
            }
```

- [ ] **Step 3: History full-screen pager** — wrap the pager's `AsyncImage(model = pageItem.filePath, …)` so non-images show a tile:

```kotlin
                    if (!pageItem.isImage) {
                        FileTypeTile(pageItem.mimeType, Modifier.fillMaxSize(), iconSize = 96.dp)
                    } else AsyncImage(
```

(keep the existing `AsyncImage(` arguments unchanged after `else`). The history list already shows a placeholder when `thumbnailPath` is null, which is the case for non-images.

Add `import com.xerahs.android.core.ui.FileTypeTile` to all three files (feature:history already depends on core:ui — confirm with `grep core:ui feature/history/build.gradle.kts`).

- [ ] **Step 4: Compile and check on device**

Run: `./gradlew installDebug`
Expected: the PDF and text uploads from Task 5/6 show file-type tiles on Home and in their Share card; image rows unchanged.

- [ ] **Step 5: Commit**

```bash
git add feature/history
git commit -m "feat(history): show file-type tiles for non-image uploads"
```

---

### Task 8: Final verification, release build, project memory

- [ ] **Step 1: Tests, lint, release**

Run: `./gradlew testDebugUnitTest lint assembleRelease`
Expected: `BUILD SUCCESSFUL`, no new lint errors.

- [ ] **Step 2: Release smoke test** (R8 + Room v4 + new intent filters)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Expected, on the release build: app starts; existing history intact; share a PDF → uploads to S3; import a `.sxcu` from Files → profile created; upload through it succeeds; HEIC converts on Android 9+.

- [ ] **Step 3: Bump version** — in `app/build.gradle.kts` set `versionName = "0.5.0"` and increment `versionCode` by 1.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 0.5.0"
```

- [ ] **Step 5: Save project memory** — run the project's `/save` workflow: session log in `xerahs-android/logs/`, new/updated notes for `pipeline/custom-uploaders.md` (sxcu model, syntax engine, client, storage + legacy migration), `data/room-migrations.md` (v4 `mimeType`, schema export now on), `features/uploader-interop.md`, and update `roadmap/redesign-roadmap.md` (A done; next B: new destinations).
