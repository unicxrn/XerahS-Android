package com.xerahs.android.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.feature.settings.data.GitHubRelease
import com.xerahs.android.feature.settings.data.GitHubReleaseChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateUiState(
    val currentVersion: String = "",
    val latestRelease: GitHubRelease? = null,
    val allReleases: List<GitHubRelease> = emptyList(),
    val isChecking: Boolean = false,
    val isLoadingChangelog: Boolean = false,
    val error: String? = null,
    val updateAvailable: Boolean = false
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    application: Application,
    private val releaseChecker: GitHubReleaseChecker
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
