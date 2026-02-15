package com.xerahs.android.feature.s3explorer.data

import com.xerahs.android.feature.s3explorer.model.AgeDistributionBucket
import com.xerahs.android.feature.s3explorer.model.BucketAnalytics
import com.xerahs.android.feature.s3explorer.model.FileTypeBreakdown
import com.xerahs.android.feature.s3explorer.model.MonthlyGrowthPoint
import com.xerahs.android.feature.s3explorer.model.S3Object
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object BucketAnalyticsComputer {

    private val FILE_TYPE_CATEGORIES = mapOf(
        "Images" to setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif", "heic", "heif", "avif"),
        "Videos" to setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp"),
        "Audio" to setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus"),
        "Documents" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods"),
        "Archives" to setOf("zip", "tar", "gz", "rar", "7z", "bz2", "xz", "tgz"),
        "Code" to setOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "yaml", "yml", "sh", "rb", "go", "rs", "c", "cpp", "h", "swift"),
    )

    fun compute(objects: List<S3Object>, folderCount: Int = 0): BucketAnalytics {
        if (objects.isEmpty()) {
            return BucketAnalytics(
                totalFiles = 0,
                totalSize = 0,
                totalFolders = folderCount,
                averageFileSize = 0,
                largestFile = null,
                newestFile = null,
                oldestFile = null,
                fileTypeBreakdown = emptyList(),
                ageDistribution = emptyList(),
                monthlyGrowth = emptyList()
            )
        }

        val totalSize = objects.sumOf { it.size }
        val averageFileSize = totalSize / objects.size

        return BucketAnalytics(
            totalFiles = objects.size,
            totalSize = totalSize,
            totalFolders = folderCount,
            averageFileSize = averageFileSize,
            largestFile = objects.maxByOrNull { it.size },
            newestFile = objects.maxByOrNull { it.lastModified },
            oldestFile = objects.filter { it.lastModified > 0 }.minByOrNull { it.lastModified },
            fileTypeBreakdown = computeFileTypeBreakdown(objects),
            ageDistribution = computeAgeDistribution(objects),
            monthlyGrowth = computeMonthlyGrowth(objects)
        )
    }

    private fun computeFileTypeBreakdown(objects: List<S3Object>): List<FileTypeBreakdown> {
        val categoryMap = mutableMapOf<String, MutableList<S3Object>>()

        for (obj in objects) {
            val ext = obj.extension
            val category = FILE_TYPE_CATEGORIES.entries.find { ext in it.value }?.key ?: "Other"
            categoryMap.getOrPut(category) { mutableListOf() }.add(obj)
        }

        return categoryMap.map { (category, files) ->
            FileTypeBreakdown(
                category = category,
                fileCount = files.size,
                totalSize = files.sumOf { it.size }
            )
        }.sortedByDescending { it.totalSize }
    }

    private fun computeAgeDistribution(objects: List<S3Object>): List<AgeDistributionBucket> {
        val now = System.currentTimeMillis()
        val buckets = listOf(
            "Today" to TimeUnit.DAYS.toMillis(1),
            "1-7 days" to TimeUnit.DAYS.toMillis(7),
            "1-4 weeks" to TimeUnit.DAYS.toMillis(28),
            "1-3 months" to TimeUnit.DAYS.toMillis(90),
            "3-6 months" to TimeUnit.DAYS.toMillis(180),
            "6-12 months" to TimeUnit.DAYS.toMillis(365),
            "1+ year" to Long.MAX_VALUE,
        )

        val result = buckets.map { (label, _) ->
            AgeDistributionBucket(label = label, count = 0, size = 0)
        }.toMutableList()

        for (obj in objects) {
            if (obj.lastModified <= 0) continue
            val ageMs = now - obj.lastModified
            val bucketIndex = when {
                ageMs < TimeUnit.DAYS.toMillis(1) -> 0
                ageMs < TimeUnit.DAYS.toMillis(7) -> 1
                ageMs < TimeUnit.DAYS.toMillis(28) -> 2
                ageMs < TimeUnit.DAYS.toMillis(90) -> 3
                ageMs < TimeUnit.DAYS.toMillis(180) -> 4
                ageMs < TimeUnit.DAYS.toMillis(365) -> 5
                else -> 6
            }
            val bucket = result[bucketIndex]
            result[bucketIndex] = bucket.copy(count = bucket.count + 1, size = bucket.size + obj.size)
        }

        return result
    }

    private fun computeMonthlyGrowth(objects: List<S3Object>): List<MonthlyGrowthPoint> {
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthMap = mutableMapOf<String, MutableList<S3Object>>()

        for (obj in objects) {
            if (obj.lastModified <= 0) continue
            val yearMonth = dateFormat.format(Date(obj.lastModified))
            monthMap.getOrPut(yearMonth) { mutableListOf() }.add(obj)
        }

        val sortedMonths = monthMap.keys.sorted()
        val points = mutableListOf<MonthlyGrowthPoint>()
        var cumulativeSize = 0L
        var cumulativeCount = 0

        for (month in sortedMonths) {
            val files = monthMap[month]!!
            val addedSize = files.sumOf { it.size }
            val addedCount = files.size
            cumulativeSize += addedSize
            cumulativeCount += addedCount
            points.add(
                MonthlyGrowthPoint(
                    yearMonth = month,
                    cumulativeSize = cumulativeSize,
                    cumulativeCount = cumulativeCount,
                    addedSize = addedSize,
                    addedCount = addedCount
                )
            )
        }

        // Return last 12 months if more
        return if (points.size > 12) points.takeLast(12) else points
    }
}
