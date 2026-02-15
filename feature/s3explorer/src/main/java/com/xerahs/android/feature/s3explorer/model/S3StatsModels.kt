package com.xerahs.android.feature.s3explorer.model

data class FileTypeBreakdown(
    val category: String,
    val fileCount: Int,
    val totalSize: Long
)

data class AgeDistributionBucket(
    val label: String,
    val count: Int,
    val size: Long
)

data class MonthlyGrowthPoint(
    val yearMonth: String,
    val cumulativeSize: Long,
    val cumulativeCount: Int,
    val addedSize: Long,
    val addedCount: Int
)

data class CostEstimation(
    val storageCost: Double,
    val getCost: Double,
    val putCost: Double,
    val bandwidthCost: Double,
    val totalMonthlyCost: Double,
    val region: String,
    val storageGB: Double,
    val estimatedGets: Int,
    val estimatedPuts: Int,
    val viewsPerFile: Int,
    val bandwidthGB: Double
)

data class BucketAnalytics(
    val totalFiles: Int,
    val totalSize: Long,
    val totalFolders: Int,
    val averageFileSize: Long,
    val largestFile: S3Object?,
    val newestFile: S3Object?,
    val oldestFile: S3Object?,
    val fileTypeBreakdown: List<FileTypeBreakdown>,
    val ageDistribution: List<AgeDistributionBucket>,
    val monthlyGrowth: List<MonthlyGrowthPoint>
)
