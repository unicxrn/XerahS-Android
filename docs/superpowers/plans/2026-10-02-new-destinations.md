# New Destinations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native Nextcloud, Immich and GitHub Gist destinations, plus bundled .sxcu presets for XBackBone, Pastebin and Bitly.

**Architecture:** Three new `UploadDestination` values with `UploadConfig` subtypes, stored in `SecureCredentialStore`. Uploaders live in `feature/upload/uploader/` and share a small cancellable HTTP helper. Settings and the profile editor render the new configs from one field-descriptor table (`NativeDestinationForms`) instead of three hand-written forms. Presets are `.sxcu` resources in `core:common` that feed the existing import preview.

**Tech Stack:** Kotlin, OkHttp 4.12 + MockWebServer, Gson tree API, Compose M3, Hilt, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-new-destinations-design.md`

**Environment (all gradle commands):**
```bash
cd /home/damnox/Documents/Playground/XerahS/XerahS-Android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/sdk ANDROID_SDK_ROOT=$HOME/Android/sdk
export PATH=$JAVA_HOME/bin:$PATH
```
Commit messages: plain, no attribution trailers. Never `git add` PLAN.md, docs/plans/, graphify-out/. KDoc must not contain `/*` (e.g. `image/*`); never paste literal BOM characters.

---

## File structure

- Create `core/data/.../remote/http/CallAwait.kt` — `suspend fun Call.await()`
- Modify `core/data/.../remote/custom/CustomUploaderClient.kt` — use `await()`
- Modify `core/domain/.../model/UploadConfig.kt`, `HistoryItem.kt` (enum), `DestinationCapabilities.kt` (+ test)
- Modify `core/data/.../local/datastore/SecureCredentialStore.kt`, `SettingsRepository.kt`, `SettingsRepositoryImpl.kt`
- Create `feature/upload/.../uploader/HttpReply.kt`, `NextcloudUploader.kt`, `ImmichUploader.kt`, `GistUploader.kt` (+ tests)
- Modify `feature/upload/.../worker/UploadWorker.kt`, `UploadScreen.kt` (icon), `feature/upload/build.gradle.kts`
- Create `feature/settings/.../destinations/NativeDestinationForms.kt` (+ test), `NativeDestinationFields.kt`, `NativeDestinationConfigScreen.kt`, `NativeDestinationConfigViewModel.kt`
- Modify `feature/settings/.../SettingsViewModel.kt`, `UploadSettingsScreen.kt`, `profiles/ProfileManagementViewModel.kt`, `profiles/ProfileEditorScreen.kt`, `profiles/ProfileManagementScreen.kt`, `feature/settings/build.gradle.kts`
- Modify `feature/history/.../home/Destinations.kt`, `HistoryScreen.kt` (colours)
- Modify `app/.../ui/navigation/NavGraph.kt`
- Create `core/common/src/main/resources/sxcu-presets/{xbackbone,pastebin,bitly}.sxcu`, `core/common/.../sxcu/SxcuPresets.kt` (+ test)
- Modify `feature/settings/.../importer/UploaderImportViewModel.kt`, `UploaderImportScreen.kt`

---

### Task 1: Shared cancellable `Call.await()`

**Files:** Create `core/data/src/main/java/com/xerahs/android/core/data/remote/http/CallAwait.kt`; Modify `core/data/src/main/java/com/xerahs/android/core/data/remote/custom/CustomUploaderClient.kt`

- [ ] **Step 1: Create the helper**

```kotlin
package com.xerahs.android.core.data.remote.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Runs the call on OkHttp's dispatcher; cancelling the coroutine cancels the HTTP call.
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
```

- [ ] **Step 2: Use it in CustomUploaderClient** — delete the private `executeCancellable` function and its now-unused imports (`Callback`, `Response`, `resume`, `resumeWithException`, `suspendCancellableCoroutine`); replace `val resp = executeCancellable(call)` with `val resp = call.await()` and add `import com.xerahs.android.core.data.remote.http.await`.

- [ ] **Step 3: Verify** — `./gradlew :core:data:testDebugUnitTest` → PASS (all existing tests, incl. the cancellation test).

- [ ] **Step 4: Commit** — `git add core/data/src/main && git commit -m "refactor(data): share cancellable Call.await helper"`

---

### Task 2: Model, storage and wiring for the new destinations

Adding enum values breaks every exhaustive `when (destination)`. This task fixes all of them. The three worker branches return a temporary "not available yet" failure; Tasks 4–6 replace each one.

**Files:** see steps.

- [ ] **Step 1: Enum** — in `core/domain/.../model/HistoryItem.kt` append to `UploadDestination` (after `LOCAL("Local")`, keep existing order, names are persisted):

```kotlin
    LOCAL("Local"),
    NEXTCLOUD("Nextcloud"),
    IMMICH("Immich"),
    GITHUB_GIST("GitHub Gist")
```

- [ ] **Step 2: Configs** — append inside `sealed class UploadConfig` in `UploadConfig.kt`:

```kotlin
    data class NextcloudConfig(
        val serverUrl: String = "",
        val username: String = "",
        val appPassword: String = "",
        val folder: String = "XerahS",
        val publicShare: Boolean = true
    ) : UploadConfig()

    data class ImmichConfig(
        val serverUrl: String = "",
        val apiKey: String = "",
        val createShareLink: Boolean = true
    ) : UploadConfig()

    data class GistConfig(
        val token: String = "",
        val isPublic: Boolean = false
    ) : UploadConfig()
```

- [ ] **Step 3: Capabilities test first** — append to `DestinationCapabilitiesTest`:

```kotlin
    @Test fun newDestinations() {
        assertTrue(accepts(UploadDestination.NEXTCLOUD, "application/zip"))
        assertTrue(accepts(UploadDestination.IMMICH, "image/heic"))
        assertTrue(accepts(UploadDestination.IMMICH, "video/mp4"))
        assertFalse(accepts(UploadDestination.IMMICH, "application/pdf"))
        assertTrue(accepts(UploadDestination.GITHUB_GIST, "text/plain"))
        assertFalse(accepts(UploadDestination.GITHUB_GIST, "image/png"))
    }
```

Then in `DestinationCapabilities.accepts` replace the last branch with:

```kotlin
        UploadDestination.IMMICH -> MimeTypes.isRasterImage(mimeType) || mimeType.startsWith("video/")
        UploadDestination.GITHUB_GIST -> MimeTypes.isText(mimeType)
        UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP,
        UploadDestination.LOCAL, UploadDestination.NEXTCLOUD -> true
```

- [ ] **Step 4: SecureCredentialStore** — add before `// Profile-specific configs`:

```kotlin
    // Nextcloud / Immich / GitHub Gist. Prefix "" = global, "profile_<id>_" = profile.
    fun getNextcloudConfig() = readNextcloud("")
    fun saveNextcloudConfig(config: UploadConfig.NextcloudConfig) = prefs.edit().apply { writeNextcloud("", config) }.apply()
    fun getImmichConfig() = readImmich("")
    fun saveImmichConfig(config: UploadConfig.ImmichConfig) = prefs.edit().apply { writeImmich("", config) }.apply()
    fun getGistConfig() = readGist("")
    fun saveGistConfig(config: UploadConfig.GistConfig) = prefs.edit().apply { writeGist("", config) }.apply()

    private fun readNextcloud(p: String) = UploadConfig.NextcloudConfig(
        serverUrl = prefs.getString("${p}nextcloud_server_url", "") ?: "",
        username = prefs.getString("${p}nextcloud_username", "") ?: "",
        appPassword = prefs.getString("${p}nextcloud_app_password", "") ?: "",
        folder = prefs.getString("${p}nextcloud_folder", "XerahS") ?: "XerahS",
        publicShare = prefs.getBoolean("${p}nextcloud_public_share", true)
    )

    private fun SharedPreferences.Editor.writeNextcloud(p: String, c: UploadConfig.NextcloudConfig) {
        putString("${p}nextcloud_server_url", c.serverUrl)
        putString("${p}nextcloud_username", c.username)
        putString("${p}nextcloud_app_password", c.appPassword)
        putString("${p}nextcloud_folder", c.folder)
        putBoolean("${p}nextcloud_public_share", c.publicShare)
    }

    private fun readImmich(p: String) = UploadConfig.ImmichConfig(
        serverUrl = prefs.getString("${p}immich_server_url", "") ?: "",
        apiKey = prefs.getString("${p}immich_api_key", "") ?: "",
        createShareLink = prefs.getBoolean("${p}immich_share_link", true)
    )

    private fun SharedPreferences.Editor.writeImmich(p: String, c: UploadConfig.ImmichConfig) {
        putString("${p}immich_server_url", c.serverUrl)
        putString("${p}immich_api_key", c.apiKey)
        putBoolean("${p}immich_share_link", c.createShareLink)
    }

    private fun readGist(p: String) = UploadConfig.GistConfig(
        token = prefs.getString("${p}gist_token", "") ?: "",
        isPublic = prefs.getBoolean("${p}gist_public", false)
    )

    private fun SharedPreferences.Editor.writeGist(p: String, c: UploadConfig.GistConfig) {
        putString("${p}gist_token", c.token)
        putBoolean("${p}gist_public", c.isPublic)
    }
```

In `getProfileConfig` add branches `UploadDestination.NEXTCLOUD -> readNextcloud(p)`, `UploadDestination.IMMICH -> readImmich(p)`, `UploadDestination.GITHUB_GIST -> readGist(p)`. In `saveProfileConfig` add `is UploadConfig.NextcloudConfig -> writeNextcloud(p, config)`, `is UploadConfig.ImmichConfig -> writeImmich(p, config)`, `is UploadConfig.GistConfig -> writeGist(p, config)`.

- [ ] **Step 5: Repository** — `SettingsRepository.kt` add:

```kotlin
    suspend fun getNextcloudConfig(): UploadConfig.NextcloudConfig
    suspend fun saveNextcloudConfig(config: UploadConfig.NextcloudConfig)
    suspend fun getImmichConfig(): UploadConfig.ImmichConfig
    suspend fun saveImmichConfig(config: UploadConfig.ImmichConfig)
    suspend fun getGistConfig(): UploadConfig.GistConfig
    suspend fun saveGistConfig(config: UploadConfig.GistConfig)
```

`SettingsRepositoryImpl.kt` add the six overrides delegating to `secureCredentialStore` (same one-line style as `getS3Config`).

- [ ] **Step 6: Other exhaustive `when`s** — let the compiler list them (`./gradlew assembleDebug`) and add branches:
  - `SettingsViewModel.checkDestinationConfigured`:
    ```kotlin
            UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig().let { it.serverUrl.isNotBlank() && it.username.isNotBlank() && it.appPassword.isNotBlank() }
            UploadDestination.IMMICH -> settingsRepository.getImmichConfig().let { it.serverUrl.isNotBlank() && it.apiKey.isNotBlank() }
            UploadDestination.GITHUB_GIST -> settingsRepository.getGistConfig().token.isNotBlank()
    ```
  - `UploadScreen.destinationIcon`: `NEXTCLOUD -> Icons.Default.FolderShared`, `IMMICH -> Icons.Default.PhotoLibrary`, `GITHUB_GIST -> Icons.Default.Code` (imports from `androidx.compose.material.icons.filled`).
  - Colour `when`s in `feature/history/.../home/Destinations.kt`, `HistoryScreen.kt`, `profiles/ProfileEditorScreen.kt` (`destDotColor`), `profiles/ProfileManagementScreen.kt`: `NEXTCLOUD -> Color(0xFF0082C9)`, `IMMICH -> Color(0xFF4250AF)`, `GITHUB_GIST -> Color(0xFF6E7681)`.
  - `ProfileManagementViewModel.toConfig`: `NEXTCLOUD -> UploadConfig.NextcloudConfig()`, `IMMICH -> UploadConfig.ImmichConfig()`, `GITHUB_GIST -> UploadConfig.GistConfig()` (Task 3 replaces these).
  - `ProfileEditorScreen` field `when`: `UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST -> {}` (Task 3 replaces).
  - `UploadWorker.performUpload`:
    ```kotlin
            UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST ->
                UploadResult(success = false, errorMessage = "${destination.displayName} isn't available yet", destination = destination)
    ```

- [ ] **Step 7: Verify** — `./gradlew assembleDebug testDebugUnitTest` → PASS.

- [ ] **Step 8: Commit** — `git add core feature app && git commit -m "feat: add Nextcloud, Immich and GitHub Gist destination models"`

---

### Task 3: Settings forms for the new destinations

**Files:** Create `feature/settings/src/main/java/com/xerahs/android/feature/settings/destinations/{NativeDestinationForms,NativeDestinationFields,NativeDestinationConfigViewModel,NativeDestinationConfigScreen}.kt`, test `feature/settings/src/test/java/com/xerahs/android/feature/settings/destinations/NativeDestinationFormsTest.kt`; Modify `feature/settings/build.gradle.kts`, `UploadSettingsScreen.kt`, `ProfileManagementViewModel.kt`, `ProfileEditorScreen.kt`, `app/.../NavGraph.kt`.

- [ ] **Step 1: Test setup** — in `feature/settings/build.gradle.kts` add `testImplementation(libs.junit)` if missing.

- [ ] **Step 2: Failing test**

```kotlin
package com.xerahs.android.feature.settings.destinations

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeDestinationFormsTest {
    @Test fun roundTripsEveryConfig() {
        listOf(
            UploadConfig.NextcloudConfig("https://nc.test", "alice", "pw", "Shots", false),
            UploadConfig.ImmichConfig("https://im.test", "key", false),
            UploadConfig.GistConfig("tok", true),
        ).forEach { config ->
            val destination = NativeDestinationForms.destinationOf(config)!!
            assertEquals(config, NativeDestinationForms.toConfig(destination, NativeDestinationForms.toValues(config)))
        }
    }

    @Test fun missingValuesFallBackToDefaults() {
        assertEquals(UploadConfig.NextcloudConfig(), NativeDestinationForms.toConfig(UploadDestination.NEXTCLOUD, emptyMap()))
        assertEquals(UploadConfig.GistConfig(), NativeDestinationForms.toConfig(UploadDestination.GITHUB_GIST, emptyMap()))
    }

    @Test fun reportsMissingRequiredFields() {
        assertEquals(
            listOf("Username", "App password"),
            NativeDestinationForms.missingRequired(UploadDestination.NEXTCLOUD, mapOf("serverUrl" to "https://nc.test"))
        )
    }
}
```

Run `./gradlew :feature:settings:testDebugUnitTest --tests '*NativeDestinationFormsTest*'` → FAIL (unresolved).

- [ ] **Step 3: Forms**

```kotlin
package com.xerahs.android.feature.settings.destinations

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination

data class FormField(
    val key: String,
    val label: String,
    val kind: Kind,
    val required: Boolean = true,
    val default: String = "",
    val help: String? = null,
) {
    enum class Kind { TEXT, URL, SECRET, SWITCH }
}

// Field tables for destinations whose settings are plain key/value forms.
object NativeDestinationForms {
    fun supports(destination: UploadDestination) = destination in setOf(
        UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST
    )

    fun fields(destination: UploadDestination): List<FormField> = when (destination) {
        UploadDestination.NEXTCLOUD -> listOf(
            FormField("serverUrl", "Server URL", FormField.Kind.URL, help = "For example https://cloud.example.com"),
            FormField("username", "Username", FormField.Kind.TEXT),
            FormField("appPassword", "App password", FormField.Kind.SECRET, help = "Create one under Settings, Security, Devices & sessions"),
            FormField("folder", "Folder", FormField.Kind.TEXT, required = false, default = "XerahS"),
            FormField("publicShare", "Create a public share link", FormField.Kind.SWITCH, required = false, default = "true"),
        )
        UploadDestination.IMMICH -> listOf(
            FormField("serverUrl", "Server URL", FormField.Kind.URL, help = "For example https://photos.example.com"),
            FormField("apiKey", "API key", FormField.Kind.SECRET, help = "Create one under Account settings, API keys"),
            FormField("createShareLink", "Create a share link", FormField.Kind.SWITCH, required = false, default = "true"),
        )
        UploadDestination.GITHUB_GIST -> listOf(
            FormField("token", "Personal access token", FormField.Kind.SECRET, help = "Needs the gist scope"),
            FormField("isPublic", "Public gist", FormField.Kind.SWITCH, required = false, default = "false"),
        )
        else -> emptyList()
    }

    fun destinationOf(config: UploadConfig): UploadDestination? = when (config) {
        is UploadConfig.NextcloudConfig -> UploadDestination.NEXTCLOUD
        is UploadConfig.ImmichConfig -> UploadDestination.IMMICH
        is UploadConfig.GistConfig -> UploadDestination.GITHUB_GIST
        else -> null
    }

    fun toValues(config: UploadConfig): Map<String, String> = when (config) {
        is UploadConfig.NextcloudConfig -> mapOf(
            "serverUrl" to config.serverUrl, "username" to config.username, "appPassword" to config.appPassword,
            "folder" to config.folder, "publicShare" to config.publicShare.toString(),
        )
        is UploadConfig.ImmichConfig -> mapOf(
            "serverUrl" to config.serverUrl, "apiKey" to config.apiKey,
            "createShareLink" to config.createShareLink.toString(),
        )
        is UploadConfig.GistConfig -> mapOf("token" to config.token, "isPublic" to config.isPublic.toString())
        else -> emptyMap()
    }

    fun toConfig(destination: UploadDestination, values: Map<String, String>): UploadConfig {
        val v = fields(destination).associate { it.key to it.default } + values
        fun s(key: String) = v[key].orEmpty().trim()
        fun b(key: String) = v[key]?.toBooleanStrictOrNull() ?: false
        return when (destination) {
            UploadDestination.NEXTCLOUD -> UploadConfig.NextcloudConfig(s("serverUrl"), s("username"), s("appPassword"), s("folder"), b("publicShare"))
            UploadDestination.IMMICH -> UploadConfig.ImmichConfig(s("serverUrl"), s("apiKey"), b("createShareLink"))
            UploadDestination.GITHUB_GIST -> UploadConfig.GistConfig(s("token"), b("isPublic"))
            else -> throw IllegalArgumentException("$destination has no form")
        }
    }

    fun missingRequired(destination: UploadDestination, values: Map<String, String>): List<String> =
        fields(destination).filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
}
```

Note: app passwords and tokens are trimmed; that's intended (pasted values often carry whitespace).

Run the test → PASS (3).

- [ ] **Step 4: Shared field renderer** (`NativeDestinationFields.kt`)

```kotlin
package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.UploadDestination

@Composable
fun NativeDestinationFields(
    destination: UploadDestination,
    values: Map<String, String>,
    onChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeDestinationForms.fields(destination).forEach { field ->
            val value = values[field.key] ?: field.default
            if (field.kind == FormField.Kind.SWITCH) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = value.toBoolean(), onCheckedChange = { onChange(field.key, it.toString()) })
                }
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    label = { Text(field.label) },
                    supportingText = field.help?.let { { Text(it) } },
                    singleLine = true,
                    visualTransformation = if (field.kind == FormField.Kind.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.kind) {
                            FormField.Kind.URL -> KeyboardType.Uri
                            FormField.Kind.SECRET -> KeyboardType.Password
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

- [ ] **Step 5: Settings screen + ViewModel**

`NativeDestinationConfigViewModel.kt`:

```kotlin
package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NativeDestinationConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val destination: UploadDestination = UploadDestination.valueOf(checkNotNull(savedStateHandle["destination"]))

    suspend fun load(): Map<String, String> = withContext(Dispatchers.IO) {
        NativeDestinationForms.toValues(
            when (destination) {
                UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig()
                UploadDestination.IMMICH -> settingsRepository.getImmichConfig()
                UploadDestination.GITHUB_GIST -> settingsRepository.getGistConfig()
                else -> error("$destination has no form")
            }
        )
    }

    // Returns null when saved, otherwise a message for the user.
    suspend fun save(values: Map<String, String>): String? {
        val missing = NativeDestinationForms.missingRequired(destination, values)
        if (missing.isNotEmpty()) return "Fill in: ${missing.joinToString()}"
        withContext(Dispatchers.IO) {
            when (val config = NativeDestinationForms.toConfig(destination, values)) {
                is UploadConfig.NextcloudConfig -> settingsRepository.saveNextcloudConfig(config)
                is UploadConfig.ImmichConfig -> settingsRepository.saveImmichConfig(config)
                is UploadConfig.GistConfig -> settingsRepository.saveGistConfig(config)
                else -> Unit
            }
        }
        return null
    }
}
```

`NativeDestinationConfigScreen.kt`:

```kotlin
package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SettingsGroupCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDestinationConfigScreen(
    onBack: () -> Unit,
    viewModel: NativeDestinationConfigViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!loaded) { values = viewModel.load(); loaded = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.destination.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard(Modifier.padding(top = 8.dp)) {
                NativeDestinationFields(
                    destination = viewModel.destination,
                    values = values,
                    onChange = { key, value -> values = values + (key to value) },
                    modifier = Modifier.padding(16.dp)
                )
            }
            Button(
                onClick = {
                    scope.launch { snackbar.showSnackbar(viewModel.save(values) ?: "Saved") }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("Save") }
        }
    }
}
```

`values` uses `remember` (a Map isn't saveable) but `loaded` survives recreation, so on rotation the form reloads stored values. Acceptable for a short form.

- [ ] **Step 6: Navigation + Settings rows** — in `NavGraph.kt` add

```kotlin
    data object NativeDestinationConfig : Screen("settings/destination/{destination}") {
        fun createRoute(destination: UploadDestination) = "settings/destination/${destination.name}"
    }
```

and a destination:

```kotlin
        composable(
            route = Screen.NativeDestinationConfig.route,
            arguments = listOf(navArgument("destination") { type = NavType.StringType })
        ) {
            BiometricGate(navController) {
                NativeDestinationConfigScreen(onBack = { navController.popBackStack() })
            }
        }
```

`UploadSettingsScreen` gets a parameter `onNavigateToDestination: (UploadDestination) -> Unit = {}`; after the FTP / SFTP `DestinationItem` add, each preceded by the same `HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)`:

```kotlin
                        DestinationItem(icon = Icons.Default.FolderShared, title = "Nextcloud", subtitle = "Upload to your Nextcloud", onClick = { onNavigateToDestination(UploadDestination.NEXTCLOUD) })
                        DestinationItem(icon = Icons.Default.PhotoLibrary, title = "Immich", subtitle = "Add to your photo library", onClick = { onNavigateToDestination(UploadDestination.IMMICH) })
                        DestinationItem(icon = Icons.Default.Code, title = "GitHub Gist", subtitle = "Text and code snippets", onClick = { onNavigateToDestination(UploadDestination.GITHUB_GIST) })
```

Wire it in NavGraph's `UploadSettingsScreen(` call: `onNavigateToDestination = { navController.navigate(Screen.NativeDestinationConfig.createRoute(it)) },`. Update the "Configure Destinations" subtitle string to `"Imgur, S3, FTP, Nextcloud, Immich, Gist, Custom uploader"`.

- [ ] **Step 7: Profile editor** — in `ProfileManagementViewModel`:
  - state: `val nativeValues: Map<String, String> = emptyMap(), val nativeError: String? = null,`
  - `fun updateNativeValue(key: String, value: String) { _editorState.value = _editorState.value.copy(nativeValues = _editorState.value.nativeValues + (key to value), nativeError = null) }`
  - in `saveProfile`, next to the custom-uploader validation:
    ```kotlin
            if (NativeDestinationForms.supports(state.destination)) {
                val missing = NativeDestinationForms.missingRequired(state.destination, state.nativeValues)
                if (missing.isNotEmpty()) {
                    _editorState.value = state.copy(isSaving = false, nativeError = "Fill in: ${missing.joinToString()}")
                    return@launch
                }
            }
    ```
  - `applyConfig`: add `is UploadConfig.NextcloudConfig, is UploadConfig.ImmichConfig, is UploadConfig.GistConfig -> copy(nativeValues = NativeDestinationForms.toValues(config))`
  - `toConfig`: replace the Task 2 defaults with `UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST -> NativeDestinationForms.toConfig(destination, nativeValues)`
  - import `com.xerahs.android.feature.settings.destinations.NativeDestinationForms`

  In `ProfileEditorScreen` replace the Task 2 `-> {}` branch with:

  ```kotlin
                UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST -> {
                    NativeDestinationFields(state.destination, state.nativeValues, viewModel::updateNativeValue)
                    state.nativeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
  ```

- [ ] **Step 8: Verify** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS.

- [ ] **Step 9: Commit** — `git add feature/settings app && git commit -m "feat(settings): settings and profile forms for the new destinations"`

---

### Task 4: Nextcloud uploader

**Files:** Create `feature/upload/.../uploader/HttpReply.kt`, `NextcloudUploader.kt`, test `feature/upload/src/test/java/com/xerahs/android/feature/upload/uploader/NextcloudUploaderTest.kt`; Modify `feature/upload/build.gradle.kts`, `UploadWorker.kt`.

- [ ] **Step 1: Test deps** — in `feature/upload/build.gradle.kts` add `implementation(libs.gson)` and `testImplementation(libs.okhttp.mockwebserver)`.

- [ ] **Step 2: Shared reply helper** (`HttpReply.kt`)

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.data.remote.http.await
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class HttpReply(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

// Sends the request (cancellable) and reads at most 1 MB of the body.
internal suspend fun OkHttpClient.send(request: Request): HttpReply =
    newCall(request).await().use { HttpReply(it.code, it.peekBody(1_000_000L).string()) }

internal fun hostError(host: String, reply: HttpReply) = "$host HTTP ${reply.code}: ${reply.body.take(200)}"
```

- [ ] **Step 3: Failing tests**

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NextcloudUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val uploader = NextcloudUploader(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun config(share: Boolean = true) =
        UploadConfig.NextcloudConfig(server.url("/").toString(), "alice", "pw", "XerahS", share)
    private fun file() = tmp.newFile("shot.png").apply { writeText("PNG") }

    @Test fun uploadsAndShares() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("""{"ocs":{"data":{"url":"https://nc.test/s/abc"}}}"""))
        val result = uploader.upload(file(), config(), "shot.png")

        assertTrue(result.errorMessage, result.success)
        assertEquals("https://nc.test/s/abc", result.url)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/remote.php/dav/files/alice/XerahS/shot.png", put.path)
        assertTrue(put.getHeader("Authorization")!!.startsWith("Basic "))
        val share = server.takeRequest()
        assertEquals("POST", share.method)
        assertTrue(share.path!!.startsWith("/ocs/v2.php/apps/files_sharing/api/v1/shares"))
        assertEquals("true", share.getHeader("OCS-APIRequest"))
        val body = share.body.readUtf8()
        assertTrue(body, body.contains("path=%2FXerahS%2Fshot.png") && body.contains("shareType=3"))
    }

    @Test fun createsMissingFolderThenRetries() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("""{"ocs":{"data":{"url":"https://nc.test/s/x"}}}"""))
        assertTrue(uploader.upload(file(), config(), "shot.png").success)
        assertEquals(listOf("PUT", "MKCOL", "PUT", "POST"), List(4) { server.takeRequest().method })
    }

    @Test fun withoutShareReturnsWebDavUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val result = uploader.upload(file(), config(share = false), "shot.png")
        assertEquals(server.url("/remote.php/dav/files/alice/XerahS/shot.png").toString(), result.url)
        assertEquals(1, server.requestCount)
    }

    @Test fun reportsHttpErrorsWithoutSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val result = uploader.upload(file(), config(), "shot.png")
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.startsWith("Nextcloud HTTP 401"))
        assertFalse(result.errorMessage!!.contains("pw"))
    }
}
```

Run `./gradlew :feature:upload:testDebugUnitTest --tests '*NextcloudUploaderTest*'` → FAIL.

- [ ] **Step 4: Implement**

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.google.gson.JsonParser
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextcloudUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(file: File, config: UploadConfig.NextcloudConfig, remoteFileName: String): UploadResult = try {
        val base = config.serverUrl.trim().toHttpUrl()
        val auth = Credentials.basic(config.username, config.appPassword)
        val folderParts = config.folder.split('/').filter { it.isNotBlank() }
        val fileUrl = davUrl(base, config.username, folderParts + remoteFileName)
        val body = file.asRequestBody(MimeTypes.fromFileName(remoteFileName).toMediaType())

        var put = okHttpClient.send(Request.Builder().url(fileUrl).header("Authorization", auth).put(body).build())
        if ((put.code == 404 || put.code == 409) && folderParts.isNotEmpty()) {
            createFolders(base, config.username, folderParts, auth)
            put = okHttpClient.send(Request.Builder().url(fileUrl).header("Authorization", auth).put(body).build())
        }
        when {
            !put.ok -> failure(hostError("Nextcloud", put))
            !config.publicShare -> success(fileUrl.toString())
            else -> share(base, auth, "/" + (folderParts + remoteFileName).joinToString("/"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("Nextcloud network error: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalArgumentException) {
        failure("Nextcloud server URL is invalid")
    }

    private fun davUrl(base: HttpUrl, user: String, segments: List<String>): HttpUrl =
        base.newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(user)
            .apply { segments.forEach { addPathSegment(it) } }.build()

    // MKCOL each level; 405 means it already exists.
    private suspend fun createFolders(base: HttpUrl, user: String, parts: List<String>, auth: String) {
        for (i in parts.indices) {
            val url = davUrl(base, user, parts.take(i + 1))
            okHttpClient.send(Request.Builder().url(url).header("Authorization", auth).method("MKCOL", null).build())
        }
    }

    private suspend fun share(base: HttpUrl, auth: String, path: String): UploadResult {
        val url = base.newBuilder().addPathSegments("ocs/v2.php/apps/files_sharing/api/v1/shares")
            .addQueryParameter("format", "json").build()
        val reply = okHttpClient.send(
            Request.Builder().url(url)
                .header("Authorization", auth)
                .header("OCS-APIRequest", "true")
                .post(FormBody.Builder().add("path", path).add("shareType", "3").build())
                .build()
        )
        if (!reply.ok) return failure("Uploaded, but sharing failed: " + hostError("Nextcloud", reply))
        val link = runCatching {
            JsonParser.parseString(reply.body).asJsonObject
                .getAsJsonObject("ocs").getAsJsonObject("data").get("url").asString
        }.getOrNull()
        return if (link.isNullOrBlank()) failure("Uploaded, but Nextcloud returned no share link") else success(link)
    }

    private fun success(url: String) = UploadResult(success = true, url = url, destination = UploadDestination.NEXTCLOUD)
    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.NEXTCLOUD)
}
```

Note: `addPathSegments` splits on `/`, so the base server path (e.g. `https://host/nextcloud/`) is kept. If `serverUrl` ends with `/`, `toHttpUrl()` already has an empty last segment; `newBuilder().addPathSegments` replaces it — verified by the tests that use `server.url("/")`.

Run the test → PASS (4).

- [ ] **Step 5: Wire the worker** — inject `private val nextcloudUploader: NextcloudUploader` into `UploadWorker` and replace `NEXTCLOUD` in the Task 2 stub branch with its own branch:

```kotlin
            UploadDestination.NEXTCLOUD -> nextcloudUploader.upload(
                file,
                (profileConfig as? UploadConfig.NextcloudConfig) ?: settingsRepository.getNextcloudConfig(),
                resolvedName
            )
```

- [ ] **Step 6: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add feature/upload && git commit -m "feat(upload): Nextcloud uploader with public share links"`

---

### Task 5: Immich uploader

**Files:** Create `ImmichUploader.kt`, test `ImmichUploaderTest.kt` (same package/dirs as Task 4); Modify `UploadWorker.kt`.

- [ ] **Step 1: Failing tests**

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImmichUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val uploader = ImmichUploader(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun config(share: Boolean = true) = UploadConfig.ImmichConfig(base(), "k3y", share)
    private fun file() = tmp.newFile("shot.png").apply { writeText("PNG") }

    @Test fun uploadsAndCreatesShareLink() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"a1","status":"created"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"key":"k9"}"""))
        val result = uploader.upload(file(), config(), "shot.png")

        assertTrue(result.errorMessage, result.success)
        assertEquals("${base()}/share/k9", result.url)
        val upload = server.takeRequest()
        assertEquals("/api/assets", upload.path)
        assertEquals("k3y", upload.getHeader("x-api-key"))
        val body = upload.body.readUtf8()
        assertTrue(body.contains("name=\"assetData\"; filename=\"shot.png\"") && body.contains("xerahs-android"))
        val link = server.takeRequest()
        assertEquals("/api/shared-links", link.path)
        assertTrue(link.body.readUtf8().contains("\"assetIds\":[\"a1\"]"))
    }

    @Test fun withoutShareReturnsPhotoUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"a1","status":"duplicate"}"""))
        assertEquals("${base()}/photos/a1", uploader.upload(file(), config(share = false), "shot.png").url)
    }

    @Test fun reportsHttpErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))
        val result = uploader.upload(file(), config(), "shot.png")
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.startsWith("Immich HTTP 401"))
    }
}
```

Run → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmichUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(file: File, config: UploadConfig.ImmichConfig, remoteFileName: String): UploadResult = try {
        val base = config.serverUrl.trim().trimEnd('/')
        val modified = Instant.ofEpochMilli(file.lastModified()).toString()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("deviceAssetId", "$remoteFileName-${file.length()}-${file.lastModified()}")
            .addFormDataPart("deviceId", "xerahs-android")
            .addFormDataPart("fileCreatedAt", modified)
            .addFormDataPart("fileModifiedAt", modified)
            .addFormDataPart("assetData", remoteFileName, file.asRequestBody(MimeTypes.fromFileName(remoteFileName).toMediaType()))
            .build()
        val upload = okHttpClient.send(request("$base/api/assets", config.apiKey).post(body).build())
        val id = if (upload.ok) runCatching { JsonParser.parseString(upload.body).asJsonObject.get("id").asString }.getOrNull() else null
        when {
            !upload.ok -> failure(hostError("Immich", upload))
            id.isNullOrBlank() -> failure("Immich returned no asset id")
            !config.createShareLink -> success("$base/photos/$id")
            else -> shareLink(base, config.apiKey, id)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("Immich network error: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalArgumentException) {
        failure("Immich server URL is invalid")
    }

    private suspend fun shareLink(base: String, apiKey: String, assetId: String): UploadResult {
        val json = JsonObject().apply {
            addProperty("type", "INDIVIDUAL")
            add("assetIds", JsonArray().apply { add(assetId) })
        }
        val reply = okHttpClient.send(
            request("$base/api/shared-links", apiKey)
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        )
        if (!reply.ok) return failure("Uploaded, but sharing failed: " + hostError("Immich", reply))
        val key = runCatching { JsonParser.parseString(reply.body).asJsonObject.get("key").asString }.getOrNull()
        return if (key.isNullOrBlank()) failure("Uploaded, but Immich returned no share key") else success("$base/share/$key")
    }

    private fun request(url: String, apiKey: String) = Request.Builder().url(url.toHttpUrl())
        .header("x-api-key", apiKey).header("Accept", "application/json")

    private fun success(url: String) = UploadResult(success = true, url = url, destination = UploadDestination.IMMICH)
    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.IMMICH)
}
```

Run → PASS (3).

- [ ] **Step 3: Wire the worker** — inject `ImmichUploader`, add:

```kotlin
            UploadDestination.IMMICH -> immichUploader.upload(
                file,
                (profileConfig as? UploadConfig.ImmichConfig) ?: settingsRepository.getImmichConfig(),
                resolvedName
            )
```

and drop `IMMICH` from the stub branch.

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add feature/upload && git commit -m "feat(upload): Immich uploader with shared links"`

---

### Task 6: GitHub Gist uploader

**Files:** Create `GistUploader.kt`, test `GistUploaderTest.kt`; Modify `UploadWorker.kt`.

- [ ] **Step 1: Failing tests**

```kotlin
package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GistUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var uploader: GistUploader

    @Before fun setUp() {
        server.start()
        uploader = GistUploader(OkHttpClient()).apply { apiBase = server.url("/").toString().trimEnd('/') }
    }
    @After fun tearDown() = server.shutdown()

    @Test fun createsSecretGist() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url":"https://gist.github.com/u/1"}"""))
        val file = tmp.newFile("note.txt").apply { writeText("say \"hi\"") }
        val result = uploader.upload(file, UploadConfig.GistConfig("tok", isPublic = false), "note.txt")

        assertEquals("https://gist.github.com/u/1", result.url)
        val req = server.takeRequest()
        assertEquals("/gists", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals("""{"public":false,"files":{"note.txt":{"content":"say \"hi\""}}}""", req.body.readUtf8())
    }

    @Test fun rejectsFilesOverOneMegabyte() = runBlocking {
        val file = tmp.newFile("big.txt").apply { writeBytes(ByteArray(1_000_001) { 'a'.code.toByte() }) }
        val result = uploader.upload(file, UploadConfig.GistConfig("tok"), "big.txt")
        assertFalse(result.success)
        assertEquals(0, server.requestCount)
    }

    @Test fun reportsHttpErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Bad credentials"))
        val result = uploader.upload(tmp.newFile("a.txt").apply { writeText("x") }, UploadConfig.GistConfig("tok"), "a.txt")
        assertTrue(result.errorMessage!!.startsWith("GitHub HTTP 401"))
    }
}
```

Run → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package com.xerahs.android.feature.upload.uploader

import androidx.annotation.VisibleForTesting
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GistUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    @VisibleForTesting
    internal var apiBase = "https://api.github.com"

    suspend fun upload(file: File, config: UploadConfig.GistConfig, remoteFileName: String): UploadResult = try {
        if (file.length() > MAX_BYTES) {
            failure("Gist files must be under 1 MB")
        } else {
            val text = withContext(Dispatchers.IO) { file.readText() }
            val json = JsonObject().apply {
                addProperty("public", config.isPublic)
                add("files", JsonObject().apply {
                    add(remoteFileName, JsonObject().apply { addProperty("content", text) })
                })
            }
            val reply = okHttpClient.send(
                Request.Builder().url("$apiBase/gists")
                    .header("Authorization", "Bearer ${config.token}")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            )
            val url = if (reply.ok) runCatching { JsonParser.parseString(reply.body).asJsonObject.get("html_url").asString }.getOrNull() else null
            when {
                !reply.ok -> failure(hostError("GitHub", reply))
                url.isNullOrBlank() -> failure("GitHub returned no gist URL")
                else -> UploadResult(success = true, url = url, destination = UploadDestination.GITHUB_GIST)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("GitHub network error: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.GITHUB_GIST)

    private companion object {
        const val MAX_BYTES = 1_000_000L
    }
}
```

Note: Gson's `JsonObject.toString()` is not HTML-escaping, so the expected body in the test matches exactly.

Run → PASS (3).

- [ ] **Step 3: Wire the worker** — inject `GistUploader`, replace the remaining stub branch with:

```kotlin
            UploadDestination.GITHUB_GIST -> gistUploader.upload(
                file,
                (profileConfig as? UploadConfig.GistConfig) ?: settingsRepository.getGistConfig(),
                resolvedName
            )
```

- [ ] **Step 4: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest` → PASS; `git add feature/upload && git commit -m "feat(upload): GitHub Gist uploader"`

---

### Task 7: Bundled .sxcu presets

**Files:** Create `core/common/src/main/resources/sxcu-presets/{xbackbone,pastebin,bitly}.sxcu`, `core/common/.../sxcu/SxcuPresets.kt`, test `core/common/src/test/java/com/xerahs/android/core/common/sxcu/SxcuPresetsTest.kt`.

- [ ] **Step 1: Preset files**

`xbackbone.sxcu`:
```json
{
  "Version": "17.0.0",
  "Name": "XBackBone",
  "DestinationType": "ImageUploader, TextUploader, FileUploader",
  "RequestMethod": "POST",
  "RequestURL": "{{host}}/upload",
  "Body": "MultipartFormData",
  "Arguments": { "token": "{{token}}" },
  "FileFormName": "upload",
  "URL": "{json:url}"
}
```

`pastebin.sxcu`:
```json
{
  "Version": "17.0.0",
  "Name": "Pastebin",
  "DestinationType": "TextUploader",
  "RequestMethod": "POST",
  "RequestURL": "https://pastebin.com/api/api_post.php",
  "Body": "FormURLEncoded",
  "Arguments": {
    "api_dev_key": "{{api_key}}",
    "api_option": "paste",
    "api_paste_code": "{input}",
    "api_paste_private": "1"
  },
  "URL": "{regex:^https?://\\S+}"
}
```

`bitly.sxcu`:
```json
{
  "Version": "17.0.0",
  "Name": "Bitly",
  "DestinationType": "URLShortener",
  "RequestMethod": "POST",
  "RequestURL": "https://api-ssl.bitly.com/v4/shorten",
  "Headers": { "Authorization": "Bearer {{token}}" },
  "Body": "JSON",
  "Data": "{\"long_url\":\"{input}\"}",
  "URL": "{json:link}",
  "ErrorMessage": "{json:description}"
}
```

- [ ] **Step 2: Failing test**

```kotlin
package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SxcuPresetsTest {
    private fun values(id: String) = SxcuPresets.all.first { it.id == id }.fields.associate { it.key to "v-${it.key}" }

    @Test fun everyPresetRendersAndParses() {
        SxcuPresets.all.forEach { preset ->
            assertTrue(preset.id, SxcuPresets.render(preset.id, values(preset.id)).isSuccess)
        }
    }

    @Test fun xbackboneTrimsHostSlashAndKeepsToken() {
        val spec = SxcuPresets.render("xbackbone", mapOf("host" to "https://x.test/ ", "token" to "t\"1")).getOrThrow()
        assertEquals("https://x.test/upload", spec.requestURL)
        assertEquals("t\"1", spec.arguments["token"])
    }

    @Test fun bitlyEvaluates() {
        val spec = SxcuPresets.render("bitly", mapOf("token" to "tok")).getOrThrow()
        assertEquals("Bearer tok", spec.headers["Authorization"])
        assertTrue(CustomDestinationType.URL_SHORTENER in spec.destinationTypes)
        val ctx = SyntaxContext(input = "https://long.test", response = SyntaxResponse("""{"link":"https://bit.ly/1"}""", ""))
        assertEquals("""{"long_url":"https://long.test"}""", ShareXSyntax.evaluate(spec.data, ctx))
        assertEquals("https://bit.ly/1", ShareXSyntax.evaluate(spec.url, ctx))
    }

    @Test(expected = SyntaxEvaluationException::class)
    fun pastebinErrorBodyIsNotAUrl() {
        val spec = SxcuPresets.render("pastebin", mapOf("api_key" to "k")).getOrThrow()
        ShareXSyntax.evaluate(spec.url, SyntaxContext(response = SyntaxResponse("Bad API request, invalid api_dev_key", "")))
    }
}
```

Run `./gradlew :core:common:testDebugUnitTest --tests '*SxcuPresetsTest*'` → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonPrimitive

data class PresetField(val key: String, val label: String, val secret: Boolean = false)

data class SxcuPreset(val id: String, val name: String, val description: String, val fields: List<PresetField>)

// Bundled .sxcu templates. {{key}} placeholders are filled from user input before parsing.
object SxcuPresets {
    val all = listOf(
        SxcuPreset("xbackbone", "XBackBone", "Self-hosted file host",
            listOf(PresetField("host", "Server URL"), PresetField("token", "Upload token", secret = true))),
        SxcuPreset("pastebin", "Pastebin", "Text pastes",
            listOf(PresetField("api_key", "Developer API key", secret = true))),
        SxcuPreset("bitly", "Bitly", "URL shortener",
            listOf(PresetField("token", "Access token", secret = true))),
    )

    fun render(id: String, values: Map<String, String>): Result<CustomUploaderSpec> = runCatching {
        val preset = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown preset $id")
        var text = javaClass.getResourceAsStream("/sxcu-presets/$id.sxcu")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Missing preset file $id")
        preset.fields.forEach { field ->
            val raw = values[field.key].orEmpty().trim()
            val value = if (field.key == "host") raw.trimEnd('/') else raw
            text = text.replace("{{${field.key}}}", jsonEscape(value))
        }
        SxcuParser.parse(text).getOrThrow()
    }

    private fun jsonEscape(s: String) = JsonPrimitive(s).toString().let { it.substring(1, it.length - 1) }
}
```

Run → PASS (4).

- [ ] **Step 4: Commit** — `git add core/common && git commit -m "feat(common): bundled XBackBone, Pastebin and Bitly presets"`

---

### Task 8: "Add from preset" in the import screen

**Files:** Modify `feature/settings/.../importer/UploaderImportViewModel.kt`, `UploaderImportScreen.kt`.

- [ ] **Step 1: ViewModel** — add (imports: `SxcuPresets`, `UploadConfig`, `UploadDestination`):

```kotlin
    fun loadPreset(id: String, values: Map<String, String>) {
        _state.value = SxcuPresets.render(id, values).fold(
            onSuccess = { spec ->
                val warnings = if (spec.requestURL.trim().startsWith("http://", ignoreCase = true)) listOf(INSECURE_HTTP_WARNING) else emptyList()
                UploaderImportState.Preview(
                    listOf(SelectableDraft(ImportDraft(spec.name, UploadDestination.CUSTOM_HTTP, UploadConfig.CustomUploaderConfig(spec), warnings)))
                )
            },
            onFailure = { UploaderImportState.Error("Couldn't build the preset: ${it.message}") }
        )
    }
```

- [ ] **Step 2: Screen** — in the `Idle` branch add `OutlinedButton(onClick = { pickPreset = true }) { Text("Add from preset") }` next to the existing buttons, with these states and dialogs at the top of the composable:

```kotlin
    var pickPreset by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf<SxcuPreset?>(null) }

    if (pickPreset) {
        AlertDialog(
            onDismissRequest = { pickPreset = false },
            title = { Text("Add from preset") },
            text = {
                Column {
                    SxcuPresets.all.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(p.description) },
                            modifier = Modifier.clickable { pickPreset = false; preset = p }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickPreset = false }) { Text("Cancel") } }
        )
    }

    preset?.let { p ->
        val answers = remember(p) { mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { preset = null },
            title = { Text(p.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.fields.forEach { f ->
                        OutlinedTextField(
                            value = answers[f.key].orEmpty(),
                            onValueChange = { answers[f.key] = it },
                            label = { Text(f.label) },
                            singleLine = true,
                            visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.fields.all { !answers[it.key].isNullOrBlank() },
                    onClick = { viewModel.loadPreset(p.id, answers.toMap()); preset = null }
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { preset = null }) { Text("Cancel") } }
        )
    }
```

Add missing imports (`clickable`, `ListItem`, `mutableStateMapOf`, `VisualTransformation`, `SxcuPreset`, `SxcuPresets`).

- [ ] **Step 3: Verify and commit** — `./gradlew assembleDebug testDebugUnitTest lint` → PASS; `git add feature/settings && git commit -m "feat(settings): add uploaders from built-in presets"`

---

### Task 9: Verification

- [ ] `./gradlew clean testDebugUnitTest lint assembleRelease` → PASS.
- [ ] Emulator, release APK: app starts; Settings → Uploads → Configure Destinations shows Nextcloud, Immich, GitHub Gist; each screen saves and reloads values; a new profile for each shows the right fields; Import uploader → Add from preset → Bitly → preview → import works (confirms the presets ship in the release APK); a Bitly profile appears in the URL shortener picker; sharing a PDF hides Immich and Gist, sharing text shows Gist.
- [ ] Vault save (session log, update `pipeline/custom-uploaders.md` presets section, new `features/new-destinations.md`, roadmap: B done, next C).
