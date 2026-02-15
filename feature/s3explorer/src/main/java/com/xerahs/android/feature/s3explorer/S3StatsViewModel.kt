package com.xerahs.android.feature.s3explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.Result
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.s3explorer.data.BucketAnalyticsComputer
import com.xerahs.android.feature.s3explorer.data.S3ApiClient
import com.xerahs.android.feature.s3explorer.data.S3PricingCalculator
import com.xerahs.android.feature.s3explorer.model.BucketAnalytics
import com.xerahs.android.feature.s3explorer.model.CostEstimation
import com.xerahs.android.feature.s3explorer.model.S3Object
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatsTab { OVERVIEW, FILE_TYPES, AGE, GROWTH, COST }

data class S3StatsUiState(
    val isScanning: Boolean = true,
    val scanCount: Int = 0,
    val error: String? = null,
    val analytics: BucketAnalytics? = null,
    val costEstimation: CostEstimation? = null,
    val selectedTab: StatsTab = StatsTab.OVERVIEW,
    val viewsPerFile: Int = 10,
    val estimatedGets: Int = 0,
    val estimatedPuts: Int = 0
)

@HiltViewModel
class S3StatsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val s3ApiClient: S3ApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(S3StatsUiState())
    val uiState: StateFlow<S3StatsUiState> = _uiState.asStateFlow()

    private var s3Config: UploadConfig.S3Config? = null
    private var allObjects: List<S3Object> = emptyList()
    private var folderCount: Int = 0

    init {
        loadAndScan()
    }

    private fun loadAndScan() {
        viewModelScope.launch {
            val config = settingsRepository.getS3Config()
            s3Config = config

            val configured = config.accessKeyId.isNotEmpty() &&
                    config.secretAccessKey.isNotEmpty() &&
                    config.bucket.isNotEmpty()

            if (!configured) {
                _uiState.update { it.copy(isScanning = false, error = "S3 not configured") }
                return@launch
            }

            scanBucket(config)
        }
    }

    private suspend fun scanBucket(config: UploadConfig.S3Config) {
        _uiState.update { it.copy(isScanning = true, scanCount = 0, error = null) }

        val collectedObjects = mutableListOf<S3Object>()
        val folderPrefixes = mutableSetOf<String>()
        var continuationToken: String? = null

        do {
            when (val result = s3ApiClient.listObjects(
                config,
                prefix = "",
                delimiter = "",
                continuationToken = continuationToken
            )) {
                is Result.Success -> {
                    val data = result.data
                    collectedObjects.addAll(data.objects)

                    // Count unique folder prefixes from object keys
                    for (obj in data.objects) {
                        val parts = obj.key.split("/")
                        for (i in 1 until parts.size) {
                            folderPrefixes.add(parts.take(i).joinToString("/") + "/")
                        }
                    }

                    _uiState.update { it.copy(scanCount = collectedObjects.size) }

                    continuationToken = if (data.isTruncated) data.nextContinuationToken else null
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            error = result.message ?: result.exception.message ?: "Scan failed"
                        )
                    }
                    return
                }
                is Result.Loading -> {}
            }
        } while (continuationToken != null)

        allObjects = collectedObjects
        folderCount = folderPrefixes.size

        val analytics = BucketAnalyticsComputer.compute(allObjects, folderCount)
        val defaultGets = (allObjects.size * 10).coerceAtLeast(100)
        val defaultPuts = (allObjects.size.toDouble() * 0.1).toInt().coerceAtLeast(10)

        _uiState.update {
            it.copy(
                estimatedGets = defaultGets,
                estimatedPuts = defaultPuts
            )
        }

        val cost = S3PricingCalculator.calculateCost(
            totalSizeBytes = analytics.totalSize,
            fileCount = analytics.totalFiles,
            region = config.region,
            estimatedGets = defaultGets,
            estimatedPuts = defaultPuts,
            viewsPerFile = _uiState.value.viewsPerFile
        )

        _uiState.update {
            it.copy(
                isScanning = false,
                analytics = analytics,
                costEstimation = cost
            )
        }
    }

    fun retry() {
        loadAndScan()
    }

    fun setSelectedTab(tab: StatsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setViewsPerFile(views: Int) {
        _uiState.update { it.copy(viewsPerFile = views) }
        recalculateCost()
    }

    fun setEstimatedGets(gets: Int) {
        _uiState.update { it.copy(estimatedGets = gets) }
        recalculateCost()
    }

    fun setEstimatedPuts(puts: Int) {
        _uiState.update { it.copy(estimatedPuts = puts) }
        recalculateCost()
    }

    private fun recalculateCost() {
        val config = s3Config ?: return
        val analytics = _uiState.value.analytics ?: return
        val state = _uiState.value

        val cost = S3PricingCalculator.calculateCost(
            totalSizeBytes = analytics.totalSize,
            fileCount = analytics.totalFiles,
            region = config.region,
            estimatedGets = state.estimatedGets,
            estimatedPuts = state.estimatedPuts,
            viewsPerFile = state.viewsPerFile
        )

        _uiState.update { it.copy(costEstimation = cost) }
    }
}
