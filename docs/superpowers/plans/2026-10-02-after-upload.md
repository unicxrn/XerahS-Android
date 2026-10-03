# After-upload Actions, Delete from Host, Encrypted Backup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-profile after-upload actions, deleting uploads from S3/FTP/SFTP/Nextcloud/custom hosts, and passphrase-encrypted backups that include upload profiles.

**Architecture:** Actions are a domain enum stored in DataStore and resolved by the worker (shorten) and the UI/notification (copy, share, open). Uploads record `remoteKey` and `profileId` in history (Room v5); a domain `RemoteDeleteRepository` (impl in core:data) deletes by destination. Backups reuse the `.xsdc` crypto through a new `PassphraseEnvelope` in core:common; `ExportImportManager` adds profiles to the payload.

**Tech Stack:** Kotlin, Room 2.6.1, DataStore, WorkManager, OkHttp + MockWebServer, commons-net, JSch, Compose M3, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-after-upload-design.md`

**Changes vs spec (decided while planning):**
- Local is left out of delete-from-host: a Local "upload" only stores a (often temporary) file path, so there is nothing remote to delete.
- `PassphraseEnvelope` lives in core:common, not core:data: feature:settings has no core:data dependency.
- "Delete from host" is added to the Home timeline row menu (the main list). The legacy History screen keeps its existing delete.
- The completion notification always keeps its Copy button; Share and Open buttons are added when those actions are selected.
- A backup profile whose name and destination match an existing profile shows as a conflict in the import preview (default: keep current), so nothing is replaced silently.

**Environment (all gradle commands):**
```bash
cd /home/damnox/Documents/Playground/XerahS/XerahS-Android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/sdk ANDROID_SDK_ROOT=$HOME/Android/sdk
export PATH=$JAVA_HOME/bin:$PATH
```
Commit messages are plain, with no attribution trailers. Never `git add` PLAN.md, docs/plans/, graphify-out/. KDoc must not contain `/*`; never paste literal BOM characters. Existing-file line numbers are approximate.

---

### Task 1: After-upload action model and storage

**Files:** Create `core/domain/src/main/java/com/xerahs/android/core/domain/model/AfterUploadAction.kt`, test `core/domain/src/test/java/com/xerahs/android/core/domain/model/AfterUploadActionTest.kt`; Modify `SettingsDataStore.kt`, `SettingsRepository.kt`, `SettingsRepositoryImpl.kt`, `core/data/.../repository/UploadProfileRepositoryImpl.kt`.

- [ ] **Step 1: Failing test**

```kotlin
package com.xerahs.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AfterUploadActionTest {
    @Test fun encodesInDeclarationOrder() = assertEquals(
        "COPY_URL,OPEN_URL",
        AfterUploadAction.encode(setOf(AfterUploadAction.OPEN_URL, AfterUploadAction.COPY_URL))
    )

    @Test fun decodesAndIgnoresUnknownNames() = assertEquals(
        setOf(AfterUploadAction.SHORTEN_URL, AfterUploadAction.SHARE_SHEET),
        AfterUploadAction.decode("SHORTEN_URL, SHARE_SHEET,BOGUS")
    )

    @Test fun emptyAndNullDecodeToEmpty() {
        assertEquals(emptySet<AfterUploadAction>(), AfterUploadAction.decode(""))
        assertEquals(emptySet<AfterUploadAction>(), AfterUploadAction.decode(null))
    }
}
```

Run `./gradlew :core:domain:testDebugUnitTest --tests '*AfterUploadActionTest*'` → FAIL.

- [ ] **Step 2: Model**

```kotlin
package com.xerahs.android.core.domain.model

enum class AfterUploadAction(val label: String) {
    COPY_URL("Copy URL"),
    SHORTEN_URL("Shorten URL"),
    SHARE_SHEET("Share"),
    OPEN_URL("Open in browser");

    companion object {
        fun encode(actions: Set<AfterUploadAction>): String =
            entries.filter { it in actions }.joinToString(",") { it.name }

        fun decode(value: String?): Set<AfterUploadAction> =
            value.orEmpty().split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()
    }
}
```

Run → PASS (3).

- [ ] **Step 3: DataStore** — in `SettingsDataStore.Keys` add `val DEFAULT_AFTER_UPLOAD_ACTIONS = stringPreferencesKey("after_upload_actions")`, and add to the class:

```kotlin
    private fun profileActionsKey(profileId: String) = stringPreferencesKey("after_upload_actions_$profileId")

    // Missing key: fall back to the old auto-copy switch.
    fun getDefaultAfterUploadActions(): Flow<Set<AfterUploadAction>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_AFTER_UPLOAD_ACTIONS]?.let { AfterUploadAction.decode(it) }
            ?: if (prefs[Keys.AUTO_COPY_URL] == true) setOf(AfterUploadAction.COPY_URL) else emptySet()
    }

    suspend fun setDefaultAfterUploadActions(actions: Set<AfterUploadAction>) {
        context.dataStore.edit { it[Keys.DEFAULT_AFTER_UPLOAD_ACTIONS] = AfterUploadAction.encode(actions) }
    }

    // null = the profile uses the default set.
    fun getProfileAfterUploadActions(profileId: String): Flow<Set<AfterUploadAction>?> = context.dataStore.data.map { prefs ->
        prefs[profileActionsKey(profileId)]?.let { AfterUploadAction.decode(it) }
    }

    suspend fun setProfileAfterUploadActions(profileId: String, actions: Set<AfterUploadAction>?) {
        context.dataStore.edit { prefs ->
            if (actions == null) prefs.remove(profileActionsKey(profileId))
            else prefs[profileActionsKey(profileId)] = AfterUploadAction.encode(actions)
        }
    }
```

(import `com.xerahs.android.core.domain.model.AfterUploadAction`.)

- [ ] **Step 4: Repository** — `SettingsRepository.kt` add:

```kotlin
    fun getDefaultAfterUploadActions(): Flow<Set<AfterUploadAction>>
    suspend fun setDefaultAfterUploadActions(actions: Set<AfterUploadAction>)
    fun getProfileAfterUploadActions(profileId: String): Flow<Set<AfterUploadAction>?>
    suspend fun setProfileAfterUploadActions(profileId: String, actions: Set<AfterUploadAction>?)
    // Profile set if it has one, otherwise the default set.
    suspend fun resolveAfterUploadActions(profileId: String?): Set<AfterUploadAction>
```

`SettingsRepositoryImpl.kt`: delegate the first four to `settingsDataStore`, and:

```kotlin
    override suspend fun resolveAfterUploadActions(profileId: String?): Set<AfterUploadAction> =
        profileId?.let { settingsDataStore.getProfileAfterUploadActions(it).first() }
            ?: settingsDataStore.getDefaultAfterUploadActions().first()
```

- [ ] **Step 5: Clean up on profile delete** — inject `private val settingsDataStore: SettingsDataStore` into `UploadProfileRepositoryImpl` and in `deleteProfile` add `settingsDataStore.setProfileAfterUploadActions(id, null)`.

- [ ] **Step 6: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add core && git commit -m "feat: store after-upload actions per profile"`

---

### Task 2: Run actions after uploads

**Files:** Create `feature/upload/.../worker/ShortenStep.kt`, test `feature/upload/src/test/java/com/xerahs/android/feature/upload/worker/ShortenStepTest.kt`; Modify `UploadWorker.kt`, `UploadViewModel.kt`, `UploadScreen.kt`.

- [ ] **Step 1: Failing test**

```kotlin
package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.AfterUploadAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortenStepTest {
    private val shorten = setOf(AfterUploadAction.SHORTEN_URL)
    private val ok: suspend (String) -> Result<String> = { Result.success("https://s.test/1") }
    private val fail: suspend (String) -> Result<String> = { Result.failure(IllegalStateException("down")) }

    @Test fun shortensWhenSelected() = runBlocking { assertEquals("https://s.test/1", maybeShorten("https://long.test/a", shorten, ok)) }
    @Test fun keepsUrlWithoutAction() = runBlocking { assertEquals("https://long.test/a", maybeShorten("https://long.test/a", emptySet(), ok)) }
    @Test fun keepsUrlOnFailure() = runBlocking { assertEquals("https://long.test/a", maybeShorten("https://long.test/a", shorten, fail)) }
    @Test fun skipsNonHttp() = runBlocking { assertEquals("/data/x.png", maybeShorten("/data/x.png", shorten, ok)) }
}
```

Run `./gradlew :feature:upload:testDebugUnitTest --tests '*ShortenStepTest*'` → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.AfterUploadAction

// Returns the short link when SHORTEN_URL is selected and shortening works, else the original URL.
internal suspend fun maybeShorten(
    url: String?,
    actions: Set<AfterUploadAction>,
    shorten: suspend (String) -> Result<String>,
): String? {
    if (url == null || AfterUploadAction.SHORTEN_URL !in actions || !url.startsWith("http", ignoreCase = true)) return url
    return shorten(url).getOrNull()?.takeIf { it.isNotBlank() } ?: url
}
```

Run → PASS (4).

- [ ] **Step 3: Worker**
  - Inject `private val urlShortener: UrlShortenerRepository` (core:domain).
  - Add `const val KEY_ACTIONS = "after_upload_actions"` to the companion.
  - Before the per-file loop: `val actions = settingsRepository.resolveAfterUploadActions(profileId)`.
  - In the success branch, before building the `HistoryItem`: `val finalUrl = maybeShorten(result.url, actions) { urlShortener.shorten(it) }`, then use `url = finalUrl` in the `HistoryItem` and `finalUrl?.let { urls.add(it) }` instead of the old `result.url` add.
  - Success output: add `.putString(KEY_ACTIONS, AfterUploadAction.encode(actions))`.
  - Change the call to `postSuccessNotification(combinedUrl, urls.size, actions)` and replace the function with:

```kotlin
    private fun postSuccessNotification(url: String, count: Int, actions: Set<AfterUploadAction>) {
        val first = url.lines().firstOrNull().orEmpty()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val copyIntent = Intent(appContext, CopyUrlReceiver::class.java).apply { putExtra("url", url) }

        val title = if (count > 1) "$count uploads complete" else "Upload complete"
        val builder = NotificationCompat.Builder(appContext, CHANNEL_UPLOAD_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(first)
            .setAutoCancel(true)
            .addAction(0, "Copy URL", PendingIntent.getBroadcast(appContext, 0, copyIntent, flags))
        if (AfterUploadAction.SHARE_SHEET in actions && first.isNotEmpty()) {
            val share = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, first), null
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(0, "Share", PendingIntent.getActivity(appContext, 1, share, flags))
        }
        if (AfterUploadAction.OPEN_URL in actions && first.startsWith("http", ignoreCase = true)) {
            val open = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(first)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(0, "Open", PendingIntent.getActivity(appContext, 2, open, flags))
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_COMPLETE, builder.build())
    }
```

  Remove the now-unused `ClipData`/`ClipboardManager` imports.

- [ ] **Step 4: ViewModel** — in `UploadViewModel.kt`:
  - Add `data class AfterUploadEvent(val urls: List<String>, val actions: Set<AfterUploadAction>)`.
  - In `UploadUiState` replace `autoCopiableUrl` and `autoCopyUrl` with `val pendingAfterUpload: AfterUploadEvent? = null`.
  - Delete the `getAutoCopyUrl()` collector in `init`.
  - In the SUCCEEDED branch replace the `autoCopiableUrl = …` line with:
    ```kotlin
                            pendingAfterUpload = AfterUploadEvent(
                                urls,
                                AfterUploadAction.decode(workInfo.outputData.getString(UploadWorker.KEY_ACTIONS))
                            ).takeIf { it.urls.isNotEmpty() && it.actions.isNotEmpty() }
    ```
  - Add `fun consumeAfterUpload() { _uiState.value = _uiState.value.copy(pendingAfterUpload = null) }`.

- [ ] **Step 5: Screen** — in `UploadScreen.kt` replace the auto-copy `LaunchedEffect(uiState.autoCopiableUrl) { … }` with:

```kotlin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.pendingAfterUpload) {
        val event = uiState.pendingAfterUpload ?: return@LaunchedEffect
        viewModel.consumeAfterUpload()
        val first = event.urls.first()
        if (AfterUploadAction.COPY_URL in event.actions) {
            clipboardManager.setText(AnnotatedString(event.urls.joinToString("\n")))
            scope.launch { snackbarHostState.showSnackbar(if (event.urls.size > 1) "Links copied" else "Link copied") }
        }
        if (AfterUploadAction.SHARE_SHEET in event.actions) {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, first), null)
            )
        }
        if (AfterUploadAction.OPEN_URL in event.actions && first.startsWith("http", ignoreCase = true)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(first))) }
        }
    }
```

(imports: `android.content.Intent`, `android.net.Uri`, `AfterUploadAction`, `LocalContext`, `rememberCoroutineScope`, `kotlinx.coroutines.launch` — skip any already present.)

- [ ] **Step 6: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add feature/upload && git commit -m "feat(upload): run after-upload actions"`

---

### Task 3: After-upload settings UI

**Files:** Create `feature/settings/src/main/java/com/xerahs/android/feature/settings/AfterUploadActionChips.kt`; Modify `SettingsViewModel.kt`, `UploadSettingsScreen.kt`, `profiles/ProfileManagementViewModel.kt`, `profiles/ProfileEditorScreen.kt`.

- [ ] **Step 1: Shared chips**

```kotlin
package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.AfterUploadAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AfterUploadActionChips(
    selected: Set<AfterUploadAction>,
    onToggle: (AfterUploadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AfterUploadAction.entries.forEach { action ->
            FilterChip(selected = action in selected, onClick = { onToggle(action) }, label = { Text(action.label) })
        }
    }
}
```

- [ ] **Step 2: Default actions in Upload settings**
  - `SettingsViewModel`: replace `autoCopyUrl` state, its collector and `setAutoCopyUrl` with `val defaultAfterUploadActions: Set<AfterUploadAction> = emptySet()`, a collector on `settingsRepository.getDefaultAfterUploadActions()`, and:
    ```kotlin
    fun toggleDefaultAfterUploadAction(action: AfterUploadAction) {
        viewModelScope.launch {
            val current = _uiState.value.defaultAfterUploadActions
            settingsRepository.setDefaultAfterUploadActions(if (action in current) current - action else current + action)
        }
    }
    ```
  - `UploadSettingsScreen`: replace the "Auto-copy URL after upload" `ListItem` with:
    ```kotlin
                Column(Modifier.padding(16.dp)) {
                    Text("After upload", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Runs after each upload unless the profile has its own actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AfterUploadActionChips(
                        selected = uiState.defaultAfterUploadActions,
                        onToggle = viewModel::toggleDefaultAfterUploadAction,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
    ```

- [ ] **Step 3: Profile editor**
  - `ProfileManagementViewModel`: inject `private val settingsRepository: SettingsRepository`; state `val afterUploadActions: Set<AfterUploadAction>? = null` (null = use default).
  - Where an existing profile is loaded into the editor (the function that calls `.applyConfig(config)`), also set `afterUploadActions = settingsRepository.getProfileAfterUploadActions(profileId).first()`.
  - Add:
    ```kotlin
    fun setUseDefaultActions(useDefault: Boolean) {
        viewModelScope.launch {
            val actions = if (useDefault) null else settingsRepository.getDefaultAfterUploadActions().first()
            _editorState.value = _editorState.value.copy(afterUploadActions = actions)
        }
    }

    fun toggleAfterUploadAction(action: AfterUploadAction) {
        val current = _editorState.value.afterUploadActions ?: return
        _editorState.value = _editorState.value.copy(
            afterUploadActions = if (action in current) current - action else current + action
        )
    }
    ```
  - In `saveProfile`, after create/update: `settingsRepository.setProfileAfterUploadActions(id, state.afterUploadActions)`.
  - `ProfileEditorScreen`: after the destination-specific fields add
    ```kotlin
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use default after-upload actions", Modifier.weight(1f))
                Switch(checked = state.afterUploadActions == null, onCheckedChange = viewModel::setUseDefaultActions)
            }
            state.afterUploadActions?.let { AfterUploadActionChips(it, viewModel::toggleAfterUploadAction) }
    ```

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS; `git add feature/settings && git commit -m "feat(settings): choose after-upload actions"`

---

### Task 4: Record where each upload went (Room v5)

**Files:** Create `core/common/src/main/java/com/xerahs/android/core/common/S3Endpoint.kt`, test `core/common/src/test/java/com/xerahs/android/core/common/S3EndpointTest.kt`; Modify `UploadResult.kt`, `HistoryItem.kt`, `HistoryEntity.kt`, `Migrations.kt`, `XerahSDatabase.kt`, `DatabaseModule.kt`, `S3Uploader.kt`, `FtpUploader.kt`, `SftpUploader.kt`, `NextcloudUploader.kt`, `UploadWorker.kt`.

- [ ] **Step 1: Extract S3 URL building.** Move the host/url logic from `S3Uploader.upload` (the `configEndpoint`/`usePathStyle`/dotted-bucket branches) into:

```kotlin
package com.xerahs.android.core.common

object S3Endpoint {
    data class Target(val host: String, val url: String)

    // Same rules S3Uploader has always used. The object key is appended as-is.
    fun resolve(endpoint: String?, bucket: String, region: String, usePathStyle: Boolean, objectKey: String): Target {
        // body: the existing S3Uploader code, unchanged, returning Target(host, url)
    }
}
```

Copy the existing code verbatim into the body (including any endpoint normalisation it already does), then make `S3Uploader` call `S3Endpoint.resolve(config.endpoint, config.bucket, config.region, config.usePathStyle, objectKey)`. Test (adjust only for normalisation the original code performs):

```kotlin
package com.xerahs.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class S3EndpointTest {
    @Test fun awsVirtualHosted() = assertEquals(
        S3Endpoint.Target("b.s3.eu-west-1.amazonaws.com", "https://b.s3.eu-west-1.amazonaws.com/a/x.png"),
        S3Endpoint.resolve(null, "b", "eu-west-1", false, "a/x.png")
    )

    @Test fun awsDottedBucketUsesPathStyle() = assertEquals(
        S3Endpoint.Target("s3.eu-west-1.amazonaws.com", "https://s3.eu-west-1.amazonaws.com/my.b/a/x.png"),
        S3Endpoint.resolve(null, "my.b", "eu-west-1", false, "a/x.png")
    )

    @Test fun customEndpointPathStyle() = assertEquals(
        S3Endpoint.Target("minio.test", "https://minio.test:9000/b/a/x.png"),
        S3Endpoint.resolve("https://minio.test:9000", "b", "us-east-1", true, "a/x.png")
    )
}
```

Run `./gradlew :core:common:testDebugUnitTest --tests '*S3EndpointTest*'` → PASS; existing build stays green.

- [ ] **Step 2: Model + migration**
  - `UploadResult`: add `val remoteKey: String? = null` (after `deleteUrl`).
  - `HistoryItem`: add `val remoteKey: String? = null, val profileId: String? = null` after `mimeType`.
  - `HistoryEntity`: add `val remoteKey: String? = null, val profileId: String? = null` and map both in `toDomain`/`fromDomain`.
  - `Migrations.kt`:
    ```kotlin
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE history ADD COLUMN remoteKey TEXT")
            db.execSQL("ALTER TABLE history ADD COLUMN profileId TEXT")
        }
    }
    ```
  - `XerahSDatabase` `version = 5`; `DatabaseModule` import + `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.

- [ ] **Step 3: Uploaders return `remoteKey`**
  - S3: `remoteKey = objectKey` in the success `UploadResult`.
  - FTP and SFTP: `remoteKey = (if (remotePath.isEmpty() || remotePath == "/") "" else remotePath) + "/" + remoteFileName` (SFTP uses `uploadName`).
  - Nextcloud: `remoteKey = (folderParts + remoteFileName).joinToString("/")` (pass it into `success(...)`; give `success` a `remoteKey: String? = null` parameter).

- [ ] **Step 4: Worker** — in the `HistoryItem(...)` call add `remoteKey = result.remoteKey, profileId = profileId`.

- [ ] **Step 5: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS and `core/data/schemas/.../5.json` generated. `git add core feature app && git commit -m "feat: remember remote location and profile of each upload (Room v5)"`

---

### Task 5: Remote delete

**Files:** Create `core/domain/.../repository/RemoteDeleteRepository.kt`, `core/data/.../remote/delete/RemoteDeleter.kt`, `core/data/.../repository/RemoteDeleteRepositoryImpl.kt`, test `core/data/src/test/java/com/xerahs/android/core/data/remote/delete/RemoteDeleterTest.kt`; Modify `app/.../di/AppModule.kt`.

- [ ] **Step 1: Domain**

```kotlin
package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.HistoryItem

// The host wants the user to open this page to finish deleting.
class OpenInBrowserException(val url: String) : Exception("Open the deletion page to finish")

interface RemoteDeleteRepository {
    fun canDelete(item: HistoryItem): Boolean
    suspend fun delete(item: HistoryItem): Result<Unit>
}
```

- [ ] **Step 2: Failing tests** (MockWebServer; FTP/SFTP are covered by review only, no test server)

```kotlin
package com.xerahs.android.core.data.remote.delete

import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDeleterTest {
    private val server = MockWebServer()
    private val deleter = RemoteDeleter(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun item(dest: UploadDestination, remoteKey: String? = null, deleteUrl: String? = null) = HistoryItem(
        id = "1", filePath = "/x", uploadDestination = dest, timestamp = 0, fileName = "x.png",
        remoteKey = remoteKey, deleteUrl = deleteUrl
    )

    @Test fun canDeleteRules() {
        assertTrue(deleter.canDelete(item(UploadDestination.CUSTOM_HTTP, deleteUrl = "https://d")))
        assertFalse(deleter.canDelete(item(UploadDestination.CUSTOM_HTTP)))
        assertTrue(deleter.canDelete(item(UploadDestination.S3, remoteKey = "a/x.png")))
        assertFalse(deleter.canDelete(item(UploadDestination.S3)))
        assertFalse(deleter.canDelete(item(UploadDestination.IMGUR, remoteKey = "k", deleteUrl = "https://d")))
        assertFalse(deleter.canDelete(item(UploadDestination.LOCAL, remoteKey = "/x")))
    }

    @Test fun deletionUrlSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/delete/abc").toString()
        deleter.delete(item(UploadDestination.CUSTOM_HTTP, deleteUrl = url), UploadConfig.CustomUploaderConfig())
        assertEquals("GET", server.takeRequest().method)
    }

    @Test fun deletionUrlNeedsBrowser() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val url = server.url("/delete/abc").toString()
        val e = runCatching { deleter.delete(item(UploadDestination.CUSTOM_HTTP, deleteUrl = url), UploadConfig.CustomUploaderConfig()) }.exceptionOrNull()
        assertTrue(e is OpenInBrowserException)
        assertEquals(url, (e as OpenInBrowserException).url)
    }

    @Test fun nextcloudDeleteToleratesMissingFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val config = UploadConfig.NextcloudConfig(server.url("/").toString(), "alice", "pw")
        deleter.delete(item(UploadDestination.NEXTCLOUD, remoteKey = "XerahS/x.png"), config)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/remote.php/dav/files/alice/XerahS/x.png", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test fun s3DeleteIsSigned() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val config = UploadConfig.S3Config(
            accessKeyId = "AK", secretAccessKey = "SK", region = "us-east-1", bucket = "b",
            endpoint = server.url("/").toString().trimEnd('/'), usePathStyle = true
        )
        deleter.delete(item(UploadDestination.S3, remoteKey = "a/x.png"), config)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/b/a/x.png", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256"))
    }

    @Test fun s3ErrorIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("AccessDenied"))
        val config = UploadConfig.S3Config(accessKeyId = "AK", secretAccessKey = "SK", bucket = "b",
            endpoint = server.url("/").toString().trimEnd('/'), usePathStyle = true)
        val e = runCatching { deleter.delete(item(UploadDestination.S3, remoteKey = "a/x.png"), config) }.exceptionOrNull()
        assertTrue(e!!.message!!.startsWith("S3 HTTP 403"))
    }
}
```

Run `./gradlew :core:data:testDebugUnitTest --tests '*RemoteDeleterTest*'` → FAIL.

- [ ] **Step 3: Deleter**

```kotlin
package com.xerahs.android.core.data.remote.delete

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import com.xerahs.android.core.common.AwsV4Signer
import com.xerahs.android.core.common.S3Endpoint
import com.xerahs.android.core.data.remote.http.await
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Deletes an upload from its host. Throws on failure; 404 / missing file counts as success.
@Singleton
class RemoteDeleter @Inject constructor(private val okHttpClient: OkHttpClient) {

    fun canDelete(item: HistoryItem): Boolean = when (item.uploadDestination) {
        UploadDestination.CUSTOM_HTTP -> !item.deleteUrl.isNullOrBlank()
        UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP, UploadDestination.NEXTCLOUD ->
            !item.remoteKey.isNullOrBlank()
        else -> false
    }

    suspend fun delete(item: HistoryItem, config: UploadConfig) {
        require(canDelete(item)) { "Deleting from ${item.uploadDestination.displayName} isn't supported" }
        val key = item.remoteKey.orEmpty()
        when (config) {
            is UploadConfig.CustomUploaderConfig -> viaDeletionUrl(item.deleteUrl!!)
            is UploadConfig.S3Config -> s3(config, key)
            is UploadConfig.FtpConfig -> withContext(Dispatchers.IO) { ftp(config, key) }
            is UploadConfig.SftpConfig -> withContext(Dispatchers.IO) { sftp(config, key) }
            is UploadConfig.NextcloudConfig -> nextcloud(config, key)
            else -> throw IllegalArgumentException("No delete support for this config")
        }
    }

    private suspend fun viaDeletionUrl(url: String) {
        val code = okHttpClient.newCall(Request.Builder().url(url).get().build()).await().use { it.code }
        if (code !in 200..299 && code != 404) throw OpenInBrowserException(url)
    }

    private suspend fun s3(config: UploadConfig.S3Config, key: String) {
        val target = S3Endpoint.resolve(config.endpoint, config.bucket, config.region, config.usePathStyle, key)
        val signed = AwsV4Signer.sign(
            method = "DELETE", url = target.url, headers = emptyMap(), payload = ByteArray(0),
            accessKeyId = config.accessKeyId, secretAccessKey = config.secretAccessKey,
            region = config.region, host = target.host
        )
        val request = Request.Builder().url(target.url).delete().apply {
            signed.headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", signed.authorization)
        }.build()
        okHttpClient.newCall(request).await().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IOException("S3 HTTP ${resp.code}: ${resp.peekBody(200).string()}")
            }
        }
    }

    private fun ftp(config: UploadConfig.FtpConfig, path: String) {
        val client = if (config.useFtps) FTPSClient() else FTPClient()
        try {
            client.connect(config.host, config.port)
            if (!client.login(config.username, config.password)) throw IOException("FTP login failed")
            if (config.usePassiveMode) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            if (!client.deleteFile(path) && client.replyCode != FTPReply.FILE_UNAVAILABLE) {
                throw IOException("FTP delete failed: ${client.replyString?.trim()}")
            }
        } finally {
            runCatching { if (client.isConnected) { client.logout(); client.disconnect() } }
        }
    }

    private fun sftp(config: UploadConfig.SftpConfig, path: String) {
        val jsch = JSch()
        if (!config.keyPath.isNullOrEmpty()) {
            if (config.keyPassphrase.isNullOrEmpty()) jsch.addIdentity(config.keyPath)
            else jsch.addIdentity(config.keyPath, config.keyPassphrase)
        }
        val session = jsch.getSession(config.username, config.host, config.port)
        var channel: ChannelSftp? = null
        try {
            if (config.keyPath.isNullOrEmpty()) session.setPassword(config.password)
            session.setConfig(java.util.Properties().apply { this["StrictHostKeyChecking"] = "no" })
            session.connect(30000)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(30000)
            try {
                channel.rm(path)
            } catch (e: SftpException) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw IOException("SFTP delete failed: ${e.message}", e)
            }
        } finally {
            channel?.disconnect()
            session.disconnect()
        }
    }

    private suspend fun nextcloud(config: UploadConfig.NextcloudConfig, key: String) {
        val url = config.serverUrl.trim().toHttpUrl().newBuilder()
            .addPathSegments("remote.php/dav/files").addPathSegment(config.username)
            .apply { key.split('/').filter { it.isNotBlank() }.forEach { addPathSegment(it) } }
            .build()
        val request = Request.Builder().url(url)
            .header("Authorization", Credentials.basic(config.username, config.appPassword)).delete().build()
        okHttpClient.newCall(request).await().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw IOException("Nextcloud HTTP ${resp.code}")
        }
    }
}
```

`StrictHostKeyChecking=no` matches the existing `SftpUploader`. Run the test → PASS (6).

- [ ] **Step 4: Repository impl + binding**

```kotlin
package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.remote.delete.RemoteDeleter
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.RemoteDeleteRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDeleteRepositoryImpl @Inject constructor(
    private val deleter: RemoteDeleter,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
) : RemoteDeleteRepository {

    override fun canDelete(item: HistoryItem) = deleter.canDelete(item)

    override suspend fun delete(item: HistoryItem): Result<Unit> = try {
        deleter.delete(item, configFor(item))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // The upload's profile if it still exists, otherwise the global config.
    private suspend fun configFor(item: HistoryItem): UploadConfig = withContext(Dispatchers.IO) {
        val dest = item.uploadDestination
        item.profileId?.takeIf { profileRepository.getProfile(it) != null }
            ?.let { profileRepository.getProfileConfig(it, dest) }
            ?: when (dest) {
                UploadDestination.S3 -> settingsRepository.getS3Config()
                UploadDestination.FTP -> settingsRepository.getFtpConfig()
                UploadDestination.SFTP -> settingsRepository.getSftpConfig()
                UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig()
                UploadDestination.CUSTOM_HTTP -> settingsRepository.getCustomUploaderConfig()
                else -> throw IllegalArgumentException("Deleting from ${dest.displayName} isn't supported")
            }
    }
}
```

`AppModule.kt`: add `@Binds @Singleton abstract fun bindRemoteDeleteRepository(impl: RemoteDeleteRepositoryImpl): RemoteDeleteRepository`.

- [ ] **Step 5: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add core app && git commit -m "feat(data): delete uploads from S3, FTP, SFTP, Nextcloud and custom hosts"`

---

### Task 6: "Delete from host" in the timeline

**Files:** Modify `feature/history/.../home/HomeViewModel.kt`, `HomeScreen.kt`.

- [ ] **Step 1: ViewModel** — inject `private val remoteDelete: RemoteDeleteRepository` and add:

```kotlin
sealed interface HomeMessage {
    data class Toast(val text: String) : HomeMessage
    data class OpenUrl(val url: String) : HomeMessage
}

    private val _messages = MutableSharedFlow<HomeMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<HomeMessage> = _messages.asSharedFlow()

    fun canDeleteFromHost(item: HistoryItem): Boolean = remoteDelete.canDelete(item)

    fun deleteFromHost(item: HistoryItem) {
        viewModelScope.launch {
            remoteDelete.delete(item).fold(
                onSuccess = {
                    historyRepository.deleteHistoryItem(item.id)
                    _messages.emit(HomeMessage.Toast("Deleted from ${item.uploadDestination.displayName}"))
                },
                onFailure = { e ->
                    _messages.emit(
                        if (e is OpenInBrowserException) HomeMessage.OpenUrl(e.url)
                        else HomeMessage.Toast("Couldn't delete: ${e.message ?: "unknown error"}")
                    )
                }
            )
        }
    }
```

(`HomeMessage` at top level in the same file.)

- [ ] **Step 2: Screen**
  - In `HomeScreen`, collect messages:
    ```kotlin
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is HomeMessage.Toast -> Toast.makeText(context, msg.text, Toast.LENGTH_SHORT).show()
                is HomeMessage.OpenUrl -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.url))) }
            }
        }
    }
    ```
  - Change the `TimelineRow(item = item, onOpen = …)` call to also pass `canDeleteFromHost = viewModel.canDeleteFromHost(item), onDeleteFromHost = { viewModel.deleteFromHost(item) }`, and add those two parameters to `TimelineRow`.
  - In `TimelineRow`, add `var confirmDelete by remember { mutableStateOf(false) }`, a third menu item when `canDeleteFromHost`:
    ```kotlin
                if (canDeleteFromHost) {
                    DropdownMenuItem(
                        text = { Text("Delete from host") },
                        onClick = { menuOpen = false; confirmDelete = true },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) }
                    )
                }
    ```
    and the dialog:
    ```kotlin
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete from host?") },
                text = { Text("This removes the file from ${item.uploadDestination.displayName} and from your history.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteFromHost() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            )
        }
    ```

- [ ] **Step 3: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS; `git add feature/history && git commit -m "feat(history): delete uploads from their host"`

---

### Task 7: Shared passphrase envelope

**Files:** Create `core/common/src/main/java/com/xerahs/android/core/common/crypto/PassphraseEnvelope.kt`, test `core/common/src/test/java/com/xerahs/android/core/common/crypto/PassphraseEnvelopeTest.kt`; Modify `core/data/.../importer/XsdcDecoder.kt`.

- [ ] **Step 1: Failing test**

```kotlin
package com.xerahs.android.core.common.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PassphraseEnvelopeTest {
    private val pass = "correct horse".toCharArray()

    @Test fun roundTrips() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        assertEquals("Test.Format", PassphraseEnvelope.formatOf(sealed))
        assertArrayEquals("hello".toByteArray(), PassphraseEnvelope.open(sealed, "Test.Format", pass.copyOf()))
    }

    @Test fun wrongPassphrase() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        val e = runCatching { PassphraseEnvelope.open(sealed, "Test.Format", "nope".toCharArray()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.WRONG_PASSPHRASE, (e as EnvelopeException).reason)
    }

    @Test fun wrongFormat() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        val e = runCatching { PassphraseEnvelope.open(sealed, "Other", pass.copyOf()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.WRONG_FORMAT, (e as EnvelopeException).reason)
    }

    @Test fun notJson() {
        assertEquals(null, PassphraseEnvelope.formatOf("plain text"))
        val e = runCatching { PassphraseEnvelope.open("plain text", "Test.Format", pass.copyOf()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.NOT_JSON, (e as EnvelopeException).reason)
    }
}
```

Run → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package com.xerahs.android.core.common.crypto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EnvelopeException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_JSON, WRONG_FORMAT, UNSUPPORTED, BAD_METADATA, WRONG_PASSPHRASE }
}

// XerahS passphrase envelope (.xsdc / .xsbk): PBKDF2-HMAC-SHA256 key, AES-256-GCM, 16-byte tag stored separately.
object PassphraseEnvelope {
    const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val TAG_BYTES = 16

    fun formatOf(text: String): String? = runCatching {
        JsonParser.parseString(text.trim().removePrefix("﻿")).asJsonObject.get("Format")?.asString
    }.getOrNull()

    fun seal(format: String, plain: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations)
        val sealed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
                doFinal(plain)
            }
        } finally {
            key.fill(0)
        }
        val b64 = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("Format", format)
            addProperty("FormatVersion", 1)
            add("Encryption", JsonObject().apply {
                addProperty("Method", "Passphrase")
                addProperty("Kdf", "PBKDF2-HMAC-SHA256")
                addProperty("Iterations", iterations)
                addProperty("Salt", b64.encodeToString(salt))
                addProperty("Cipher", "AES-256-GCM")
                addProperty("Nonce", b64.encodeToString(nonce))
                addProperty("Tag", b64.encodeToString(sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size)))
            })
            addProperty("Payload", b64.encodeToString(sealed.copyOfRange(0, sealed.size - TAG_BYTES)))
        }.toString()
    }

    fun open(text: String, format: String, passphrase: CharArray): ByteArray {
        val envelope = try {
            JsonParser.parseString(text.trim().removePrefix("﻿")).asJsonObject
        } catch (e: Exception) {
            throw EnvelopeException(EnvelopeException.Reason.NOT_JSON, e)
        }
        if (envelope.str("Format") != format || envelope.int("FormatVersion") != 1) {
            throw EnvelopeException(EnvelopeException.Reason.WRONG_FORMAT)
        }
        val enc = envelope.get("Encryption")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw EnvelopeException(EnvelopeException.Reason.BAD_METADATA)
        if (enc.str("Method") != "Passphrase" || enc.str("Kdf") != "PBKDF2-HMAC-SHA256" || enc.str("Cipher") != "AES-256-GCM") {
            throw EnvelopeException(EnvelopeException.Reason.UNSUPPORTED)
        }
        val iterations = enc.int("Iterations")?.takeIf { it in 1..MAX_ITERATIONS }
            ?: throw EnvelopeException(EnvelopeException.Reason.BAD_METADATA)
        return try {
            val b64 = Base64.getDecoder()
            val key = deriveKey(passphrase, b64.decode(enc.str("Salt").orEmpty()), iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                    GCMParameterSpec(TAG_BYTES * 8, b64.decode(enc.str("Nonce").orEmpty())))
            } finally {
                key.fill(0)
            }
            cipher.doFinal(b64.decode(envelope.str("Payload").orEmpty()) + b64.decode(enc.str("Tag").orEmpty()))
        } catch (e: Exception) {
            throw EnvelopeException(EnvelopeException.Reason.WRONG_PASSPHRASE, e)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
}
```

Run → PASS (4).

- [ ] **Step 3: XsdcDecoder uses it** — replace the envelope/crypto part of `decode` (everything that produces `plain`) with:

```kotlin
        val plain = try {
            PassphraseEnvelope.open(String(bytes, Charsets.UTF_8), "XerahS.DestinationConfig", passphrase)
        } catch (e: EnvelopeException) {
            throw XsdcException(
                when (e.reason) {
                    EnvelopeException.Reason.NOT_JSON -> "The .xsdc file is not valid JSON."
                    EnvelopeException.Reason.WRONG_FORMAT -> "This is not a XerahS destination config."
                    EnvelopeException.Reason.UNSUPPORTED -> "This .xsdc encryption method is not supported."
                    EnvelopeException.Reason.BAD_METADATA -> "This .xsdc file has invalid encryption metadata."
                    EnvelopeException.Reason.WRONG_PASSPHRASE -> "Wrong passphrase or damaged file."
                },
                e
            )
        }
```

Remove the now-unused imports and `MAX_ITERATIONS`. Run `./gradlew :core:data:testDebugUnitTest` → existing `XsdcDecoderTest` and `UploaderImportParsingTest` still PASS.

- [ ] **Step 4: Commit** — `git add core && git commit -m "refactor: share passphrase envelope between .xsdc and backups"`

---

### Task 8: Backup payload with profiles

**Files:** Create `feature/settings/.../ConfigJson.kt`, test `feature/settings/src/test/java/com/xerahs/android/feature/settings/ConfigJsonTest.kt`; Modify `ExportImportManager.kt`.

- [ ] **Step 1: Failing test**

```kotlin
package com.xerahs.android.feature.settings

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigJsonTest {
    @Test fun roundTripsEveryConfigType() {
        listOf(
            UploadDestination.IMGUR to UploadConfig.ImgurConfig("id", "sec", "at", null, false),
            UploadDestination.S3 to UploadConfig.S3Config("AK", "SK", "eu-west-1", "b", "https://e", null, "p/", "public-read", true),
            UploadDestination.FTP to UploadConfig.FtpConfig("h", 2121, "u", "pw", "/r", true, false, "https://x"),
            UploadDestination.SFTP to UploadConfig.SftpConfig("h", 22, "u", "pw", "/k", "kp", "/r", ""),
            UploadDestination.CUSTOM_HTTP to UploadConfig.CustomUploaderConfig(CustomUploaderSpec(name = "C", requestURL = "https://c.test")),
            UploadDestination.NEXTCLOUD to UploadConfig.NextcloudConfig("https://nc", "a", "pw", "F", false),
            UploadDestination.IMMICH to UploadConfig.ImmichConfig("https://im", "k", false),
            UploadDestination.GITHUB_GIST to UploadConfig.GistConfig("t", true),
        ).forEach { (dest, config) ->
            assertEquals(dest.name, config, ConfigJson.fromJson(dest, ConfigJson.toJson(config)))
        }
    }

    @Test fun localHasNoConfig() = assertEquals(null, ConfigJson.fromJson(UploadDestination.LOCAL, com.google.gson.JsonObject()))
}
```

Run `./gradlew :feature:settings:testDebugUnitTest --tests '*ConfigJsonTest*'` → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package com.xerahs.android.feature.settings

import com.google.gson.JsonObject
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination

// JSON form of every UploadConfig, used for profiles in backups.
object ConfigJson {
    fun toJson(config: UploadConfig): JsonObject = JsonObject().apply {
        when (config) {
            is UploadConfig.ImgurConfig -> {
                addProperty("clientId", config.clientId); addProperty("clientSecret", config.clientSecret)
                addProperty("accessToken", config.accessToken); addProperty("refreshToken", config.refreshToken)
                addProperty("useAnonymous", config.useAnonymous)
            }
            is UploadConfig.S3Config -> {
                addProperty("accessKeyId", config.accessKeyId); addProperty("secretAccessKey", config.secretAccessKey)
                addProperty("region", config.region); addProperty("bucket", config.bucket)
                addProperty("endpoint", config.endpoint); addProperty("customUrl", config.customUrl)
                addProperty("prefix", config.prefix); addProperty("acl", config.acl)
                addProperty("usePathStyle", config.usePathStyle)
            }
            is UploadConfig.FtpConfig -> {
                addProperty("host", config.host); addProperty("port", config.port)
                addProperty("username", config.username); addProperty("password", config.password)
                addProperty("remotePath", config.remotePath); addProperty("useFtps", config.useFtps)
                addProperty("usePassiveMode", config.usePassiveMode); addProperty("httpUrl", config.httpUrl)
            }
            is UploadConfig.SftpConfig -> {
                addProperty("host", config.host); addProperty("port", config.port)
                addProperty("username", config.username); addProperty("password", config.password)
                addProperty("keyPath", config.keyPath); addProperty("keyPassphrase", config.keyPassphrase)
                addProperty("remotePath", config.remotePath); addProperty("httpUrl", config.httpUrl)
            }
            is UploadConfig.CustomUploaderConfig -> addProperty("sxcu", SxcuWriter.write(config.spec))
            is UploadConfig.NextcloudConfig -> {
                addProperty("serverUrl", config.serverUrl); addProperty("username", config.username)
                addProperty("appPassword", config.appPassword); addProperty("folder", config.folder)
                addProperty("publicShare", config.publicShare)
            }
            is UploadConfig.ImmichConfig -> {
                addProperty("serverUrl", config.serverUrl); addProperty("apiKey", config.apiKey)
                addProperty("createShareLink", config.createShareLink)
            }
            is UploadConfig.GistConfig -> { addProperty("token", config.token); addProperty("isPublic", config.isPublic) }
        }
    }

    fun fromJson(destination: UploadDestination, j: JsonObject): UploadConfig? {
        fun s(k: String) = j.get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        fun ns(k: String) = j.get(k)?.takeIf { it.isJsonPrimitive }?.asString
        fun b(k: String, d: Boolean) = j.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: d
        fun i(k: String, d: Int) = j.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: d
        return when (destination) {
            UploadDestination.IMGUR -> UploadConfig.ImgurConfig(s("clientId"), s("clientSecret"), ns("accessToken"), ns("refreshToken"), b("useAnonymous", true))
            UploadDestination.S3 -> UploadConfig.S3Config(s("accessKeyId"), s("secretAccessKey"), ns("region") ?: "us-east-1", s("bucket"), ns("endpoint"), ns("customUrl"), s("prefix"), s("acl"), b("usePathStyle", false))
            UploadDestination.FTP -> UploadConfig.FtpConfig(s("host"), i("port", 21), s("username"), s("password"), ns("remotePath") ?: "/", b("useFtps", false), b("usePassiveMode", true), s("httpUrl"))
            UploadDestination.SFTP -> UploadConfig.SftpConfig(s("host"), i("port", 22), s("username"), s("password"), ns("keyPath"), ns("keyPassphrase"), ns("remotePath") ?: "/", s("httpUrl"))
            UploadDestination.CUSTOM_HTTP -> ns("sxcu")?.let { SxcuParser.parse(it).getOrNull() }?.let { UploadConfig.CustomUploaderConfig(it) }
            UploadDestination.NEXTCLOUD -> UploadConfig.NextcloudConfig(s("serverUrl"), s("username"), s("appPassword"), ns("folder") ?: "XerahS", b("publicShare", true))
            UploadDestination.IMMICH -> UploadConfig.ImmichConfig(s("serverUrl"), s("apiKey"), b("createShareLink", true))
            UploadDestination.GITHUB_GIST -> UploadConfig.GistConfig(s("token"), b("isPublic", false))
            UploadDestination.LOCAL -> null
        }
    }
}
```

The positional constructor order follows the existing `UploadConfig` classes; if a constructor differs, use named arguments. Run → PASS (2).

- [ ] **Step 3: ExportImportManager** — inject `private val profileRepository: UploadProfileRepository`. Add (imports: `JsonArray`, `JsonParser`, `PassphraseEnvelope`, `AfterUploadAction`, `UploadProfile`, `UploadDestination`, `generateId`, `generateTimestamp`, `kotlinx.coroutines.flow.first`):

```kotlin
    companion object { const val BACKUP_FORMAT = "XerahS.Backup" }

    suspend fun exportBackup(passphrase: CharArray): String {
        val payload = JsonObject()
        payload.add("settings", JsonParser.parseString(exportSettings()))
        payload.addProperty("defaultAfterUploadActions", AfterUploadAction.encode(settingsRepository.getDefaultAfterUploadActions().first()))
        val profiles = JsonArray()
        profileRepository.getAllProfiles().first().forEach { p ->
            profiles.add(JsonObject().apply {
                addProperty("name", p.name)
                addProperty("destination", p.destination.name)
                addProperty("isDefault", p.isDefault)
                add("config", ConfigJson.toJson(profileRepository.getProfileConfig(p.id, p.destination)))
                settingsRepository.getProfileAfterUploadActions(p.id).first()
                    ?.let { addProperty("afterUploadActions", AfterUploadAction.encode(it)) }
            })
        }
        payload.add("profiles", profiles)
        return PassphraseEnvelope.seal(BACKUP_FORMAT, payload.toString().toByteArray(Charsets.UTF_8), passphrase)
    }

    fun isEncryptedBackup(text: String): Boolean = PassphraseEnvelope.formatOf(text) == BACKUP_FORMAT

    fun openBackup(text: String, passphrase: CharArray): JsonObject =
        JsonParser.parseString(String(PassphraseEnvelope.open(text, BACKUP_FORMAT, passphrase), Charsets.UTF_8)).asJsonObject

    suspend fun parseBackupExtrasPreview(payload: JsonObject): ImportSection? {
        val existing = profileRepository.getAllProfiles().first()
        val fields = mutableListOf<ImportField>()
        payload.get("defaultAfterUploadActions")?.takeIf { it.isJsonPrimitive }?.asString?.let { imported ->
            val current = AfterUploadAction.encode(settingsRepository.getDefaultAfterUploadActions().first())
            fields.add(ImportField("afterUploadActions.default", "Default after-upload actions", current, imported, current != imported))
        }
        payload.getAsJsonArray("profiles")?.forEachIndexed { index, el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEachIndexed
            val name = o.get("name")?.asString ?: return@forEachIndexed
            val dest = runCatching { UploadDestination.valueOf(o.get("destination").asString) }.getOrNull() ?: return@forEachIndexed
            val exists = existing.any { it.name == name && it.destination == dest }
            fields.add(ImportField("profile.$index", "$name (${dest.displayName})", if (exists) "Existing profile" else "None", "From backup", hasConflict = exists))
        }
        return if (fields.isEmpty()) null else ImportSection("Upload profiles", fields)
    }

    suspend fun applyBackupExtras(payload: JsonObject, preview: ImportPreview) {
        val accepted = preview.sections.flatMap { it.fields }
            .filter { it.resolution == FieldResolution.USE_IMPORTED }.map { it.key }.toSet()
        if ("afterUploadActions.default" in accepted) {
            payload.get("defaultAfterUploadActions")?.asString
                ?.let { settingsRepository.setDefaultAfterUploadActions(AfterUploadAction.decode(it)) }
        }
        val existing = profileRepository.getAllProfiles().first()
        payload.getAsJsonArray("profiles")?.forEachIndexed { index, el ->
            if ("profile.$index" !in accepted) return@forEachIndexed
            val o = el.asJsonObject
            val name = o.get("name").asString
            val dest = UploadDestination.valueOf(o.get("destination").asString)
            val config = o.getAsJsonObject("config")?.let { ConfigJson.fromJson(dest, it) } ?: return@forEachIndexed
            val match = existing.firstOrNull { it.name == name && it.destination == dest }
            val profile = UploadProfile(
                id = match?.id ?: generateId(),
                name = name,
                destination = dest,
                isDefault = o.get("isDefault")?.asBoolean ?: false,
                createdAt = match?.createdAt ?: generateTimestamp()
            )
            if (match != null) profileRepository.updateProfile(profile, config) else profileRepository.createProfile(profile, config)
            if (profile.isDefault) profileRepository.setDefault(profile.id, dest)
            settingsRepository.setProfileAfterUploadActions(
                profile.id, o.get("afterUploadActions")?.asString?.let { AfterUploadAction.decode(it) }
            )
        }
    }
```

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add feature/settings && git commit -m "feat(settings): encrypted backup payload with upload profiles"`

---

### Task 9: Backup screen

**Files:** Modify `SettingsViewModel.kt`, `BackupSettingsScreen.kt`.

- [ ] **Step 1: ViewModel**
  - State: `val backupPassphraseRequest: BackupPassphraseRequest? = null` with `enum class BackupPassphraseRequest { EXPORT, IMPORT }` (top level in the file), and `val pendingBackupPayload: String? = null`.
  - Private fields: `private var exportPassphrase: CharArray? = null`, `private var pendingEncryptedBackup: String? = null`.
  - Replace `exportSettings(outputStream)` with:
    ```kotlin
    fun requestExportPassphrase() { _uiState.value = _uiState.value.copy(backupPassphraseRequest = BackupPassphraseRequest.EXPORT) }

    fun setExportPassphrase(passphrase: CharArray) {
        exportPassphrase = passphrase
        _uiState.value = _uiState.value.copy(backupPassphraseRequest = null)
    }

    fun exportBackup(outputStream: OutputStream) {
        val passphrase = exportPassphrase ?: return
        exportPassphrase = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val text = exportImportManager.exportBackup(passphrase)
                    withContext(Dispatchers.IO) { outputStream.use { it.write(text.toByteArray()) } }
                }
                _uiState.value = _uiState.value.copy(exportImportMessage = "Backup saved")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Backup failed: ${e.message}")
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }
    ```
  - In `previewImport`, after reading `json`: if `exportImportManager.isEncryptedBackup(json)` → `pendingEncryptedBackup = json; _uiState.value = _uiState.value.copy(backupPassphraseRequest = BackupPassphraseRequest.IMPORT)` and return; otherwise keep the existing plain-JSON path.
  - Add:
    ```kotlin
    fun unlockBackup(passphrase: CharArray) {
        val text = pendingEncryptedBackup ?: return
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.Default) { exportImportManager.openBackup(text, passphrase) }
                val settingsJson = payload.get("settings").toString()
                val preview = exportImportManager.parseImportPreview(settingsJson)
                val extras = exportImportManager.parseBackupExtrasPreview(payload)
                pendingEncryptedBackup = null
                _uiState.value = _uiState.value.copy(
                    backupPassphraseRequest = null,
                    importPreview = ImportPreview(preview.sections + listOfNotNull(extras)),
                    pendingImportJson = settingsJson,
                    pendingBackupPayload = payload.toString()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Wrong passphrase or damaged backup")
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun cancelPassphrase() {
        exportPassphrase = null
        pendingEncryptedBackup = null
        _uiState.value = _uiState.value.copy(backupPassphraseRequest = null)
    }
    ```
  - In `applyResolvedImport`, inside the IO block after `exportImportManager.applyResolvedImport(json, preview)`:
    ```kotlin
                    _uiState.value.pendingBackupPayload?.let {
                        exportImportManager.applyBackupExtras(JsonParser.parseString(it).asJsonObject, preview)
                    }
    ```
    and clear `pendingBackupPayload = null` wherever `pendingImportJson` is cleared (apply success and `cancelImportPreview`).

- [ ] **Step 2: Screen**
  - Export launcher: `ActivityResultContracts.CreateDocument("application/octet-stream")`, callback calls `viewModel.exportBackup(outputStream)`.
  - Export button: `onClick = { viewModel.requestExportPassphrase() }`, label "Export encrypted backup".
  - Import launcher: `importLauncher.launch(arrayOf("*/*"))`.
  - Passphrase dialog, shown from `uiState.backupPassphraseRequest`:
    ```kotlin
    uiState.backupPassphraseRequest?.let { request ->
        var first by remember(request) { mutableStateOf("") }
        var second by remember(request) { mutableStateOf("") }
        val exporting = request == BackupPassphraseRequest.EXPORT
        val error = when {
            exporting && first.length in 1..7 -> "Use at least 8 characters"
            exporting && second.isNotEmpty() && first != second -> "Passphrases don't match"
            else -> null
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPassphrase() },
            title = { Text(if (exporting) "Protect your backup" else "Unlock backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exporting) Text("The backup includes passwords and keys. You need this passphrase to restore it.")
                    OutlinedTextField(first, { first = it }, label = { Text("Passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (exporting) OutlinedTextField(second, { second = it }, label = { Text("Repeat passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), isError = error != null,
                        supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = if (exporting) first.length >= 8 && first == second else first.isNotEmpty(),
                    onClick = {
                        if (exporting) {
                            viewModel.setExportPassphrase(first.toCharArray())
                            exportLauncher.launch("xerahs-backup-${java.time.LocalDate.now()}.xsbk")
                        } else {
                            viewModel.unlockBackup(first.toCharArray())
                        }
                    }
                ) { Text(if (exporting) "Continue" else "Unlock") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelPassphrase() }) { Text("Cancel") } }
        )
    }
    ```
    If the user cancels the SAF picker after entering an export passphrase, the stored passphrase stays until the next export; clear it by calling `viewModel.cancelPassphrase()` in the launcher callback when `uri == null`.

- [ ] **Step 3: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS; `git add feature/settings && git commit -m "feat(settings): passphrase-protected backups"`

---

### Task 10: Verification

- [ ] `./gradlew clean testDebugUnitTest lint assembleRelease` → PASS.
- [ ] Emulator: install the previous branch build, seed history, upgrade to this build (v4 → v5 keeps rows); set default actions Copy + Share, upload to Local from the Upload screen (link copied, share sheet opens); background the app during an upload (notification shows Copy and Share); export a backup with a profile, uninstall, reinstall, import with the passphrase (profile and settings return; wrong passphrase shows the error); release APK starts.
- [ ] Vault save (session log, `features/after-upload.md`, `data/room-migrations.md` v5, roadmap: C done, next D+E).
