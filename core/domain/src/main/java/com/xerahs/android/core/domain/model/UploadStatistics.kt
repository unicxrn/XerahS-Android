package com.xerahs.android.core.domain.model

data class UploadStatistics(
    val totalUploads: Int = 0,
    val totalSize: Long = 0,
    val averageSize: Long = 0,
    val uploadsThisWeek: Int = 0,
    val countByDestination: List<Pair<String, Int>> = emptyList(),
    val sizeByDestination: List<Pair<String, Long>> = emptyList(),
    val uploadsPerDay: List<Pair<Long, Int>> = emptyList(),
    val topAlbums: List<Pair<String, Int>> = emptyList(),
    val topTags: List<Pair<String, Int>> = emptyList()
)
