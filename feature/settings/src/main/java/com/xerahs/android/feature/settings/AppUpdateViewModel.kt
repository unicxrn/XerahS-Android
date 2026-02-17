package com.xerahs.android.feature.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.feature.settings.data.GitHubRelease
import com.xerahs.android.feature.settings.data.GitHubReleaseChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

data class AppUpdateUiState(
    val currentVersion: String = "",
    val latestRelease: GitHubRelease? = null,
    val allReleases: List<GitHubRelease> = emptyList(),
    val isChecking: Boolean = false,
    val isLoadingChangelog: Boolean = false,
    val error: String? = null,
    val updateAvailable: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    application: Application,
    private val releaseChecker: GitHubReleaseChecker,
    private val okHttpClient: OkHttpClient
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        val versionName = try {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
        _uiState.update { it.copy(currentVersion = versionName) }

        checkForUpdate()
        loadChangelog()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null) }

            releaseChecker.fetchLatestRelease()
                .onSuccess { release ->
                    val isNewer = releaseChecker.isNewerVersion(
                        release.tagName,
                        _uiState.value.currentVersion
                    )
                    _uiState.update {
                        it.copy(
                            latestRelease = release,
                            updateAvailable = isNewer,
                            isChecking = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to check for updates",
                            isChecking = false
                        )
                    }
                }
        }
    }

    fun downloadAndInstall() {
        val release = _uiState.value.latestRelease ?: return
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
        if (apkAsset == null) {
            _uiState.update { it.copy(downloadError = "No APK found in release assets") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, downloadError = null) }

            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(apkAsset.browserDownloadUrl, apkAsset.size)
                }

                // Trigger install
                val context = getApplication<Application>()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)

                _uiState.update { it.copy(isDownloading = false, downloadProgress = 1f) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadError = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun downloadApk(url: String, totalSize: Long): File {
        val context = getApplication<Application>()
        val apkFile = File(context.cacheDir, "update.apk")
        if (apkFile.exists()) apkFile.delete()

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response")
        val contentLength = if (totalSize > 0) totalSize else body.contentLength()

        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = bytesRead.toFloat() / contentLength.toFloat()
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                }
            }
        }

        return apkFile
    }

    private fun loadChangelog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChangelog = true) }

            releaseChecker.fetchAllReleases()
                .onSuccess { releases ->
                    _uiState.update {
                        it.copy(allReleases = releases, isLoadingChangelog = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingChangelog = false) }
                }
        }
    }
}
