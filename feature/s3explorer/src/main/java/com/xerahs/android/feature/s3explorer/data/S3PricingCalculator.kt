package com.xerahs.android.feature.s3explorer.data

import com.xerahs.android.feature.s3explorer.model.CostEstimation

object S3PricingCalculator {

    private data class RegionPricing(
        val storage: Double,
        val get: Double,
        val put: Double
    )

    private val REGION_PRICING = mapOf(
        // US regions
        "us-east-1" to RegionPricing(0.023, 0.0004, 0.005),
        "us-east-2" to RegionPricing(0.023, 0.0004, 0.005),
        "us-west-1" to RegionPricing(0.026, 0.0004, 0.005),
        "us-west-2" to RegionPricing(0.023, 0.0004, 0.005),
        // EU regions
        "eu-west-1" to RegionPricing(0.023, 0.0004, 0.005),
        "eu-west-2" to RegionPricing(0.024, 0.0004, 0.005),
        "eu-west-3" to RegionPricing(0.024, 0.0004, 0.005),
        "eu-central-1" to RegionPricing(0.0245, 0.00043, 0.0054),
        "eu-north-1" to RegionPricing(0.023, 0.0004, 0.005),
        "eu-south-1" to RegionPricing(0.024, 0.00044, 0.0055),
        // Asia Pacific
        "ap-southeast-1" to RegionPricing(0.025, 0.0004, 0.005),
        "ap-southeast-2" to RegionPricing(0.025, 0.00044, 0.0055),
        "ap-northeast-1" to RegionPricing(0.025, 0.00037, 0.0047),
        "ap-northeast-2" to RegionPricing(0.025, 0.0004, 0.005),
        "ap-south-1" to RegionPricing(0.025, 0.0004, 0.005),
        "sa-east-1" to RegionPricing(0.0405, 0.0004, 0.005),
    )

    private val DEFAULT_PRICING = RegionPricing(0.023, 0.0004, 0.005)

    private data class BandwidthTier(val limitGB: Double, val pricePerGB: Double)

    private val BANDWIDTH_TIERS = listOf(
        BandwidthTier(10.0 * 1024, 0.09),
        BandwidthTier(50.0 * 1024, 0.085),
        BandwidthTier(150.0 * 1024, 0.07),
        BandwidthTier(Double.MAX_VALUE, 0.05),
    )

    fun calculateCost(
        totalSizeBytes: Long,
        fileCount: Int,
        region: String,
        estimatedGets: Int,
        estimatedPuts: Int,
        viewsPerFile: Int
    ): CostEstimation {
        val pricing = REGION_PRICING[region] ?: DEFAULT_PRICING
        val storageGB = totalSizeBytes / (1024.0 * 1024.0 * 1024.0)

        val storageCost = storageGB * pricing.storage
        val getCost = (estimatedGets / 1000.0) * pricing.get
        val putCost = (estimatedPuts / 1000.0) * pricing.put

        // Bandwidth: avg file size * total views
        val bandwidthGB = if (fileCount > 0) {
            val avgFileSizeBytes = totalSizeBytes.toDouble() / fileCount
            val totalViews = fileCount.toLong() * viewsPerFile
            (avgFileSizeBytes * totalViews) / (1024.0 * 1024.0 * 1024.0)
        } else 0.0

        val bandwidthCost = calculateBandwidthCost(bandwidthGB)

        return CostEstimation(
            storageCost = storageCost,
            getCost = getCost,
            putCost = putCost,
            bandwidthCost = bandwidthCost,
            totalMonthlyCost = storageCost + getCost + putCost + bandwidthCost,
            region = region,
            storageGB = storageGB,
            estimatedGets = estimatedGets,
            estimatedPuts = estimatedPuts,
            viewsPerFile = viewsPerFile,
            bandwidthGB = bandwidthGB
        )
    }

    private fun calculateBandwidthCost(bandwidthGB: Double): Double {
        var remainingGB = bandwidthGB
        var cost = 0.0
        var prevLimit = 0.0

        for (tier in BANDWIDTH_TIERS) {
            val tierSize = tier.limitGB - prevLimit
            val gbInTier = minOf(remainingGB, tierSize)
            cost += gbInTier * tier.pricePerGB
            remainingGB -= gbInTier
            prevLimit = tier.limitGB
            if (remainingGB <= 0) break
        }

        return cost
    }
}
