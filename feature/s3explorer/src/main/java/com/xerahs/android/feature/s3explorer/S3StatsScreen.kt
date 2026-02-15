package com.xerahs.android.feature.s3explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.common.formatSize
import com.xerahs.android.core.common.toShortDate
import com.xerahs.android.core.ui.StatCard
import com.xerahs.android.feature.s3explorer.model.AgeDistributionBucket
import com.xerahs.android.feature.s3explorer.model.BucketAnalytics
import com.xerahs.android.feature.s3explorer.model.CostEstimation
import com.xerahs.android.feature.s3explorer.model.FileTypeBreakdown
import com.xerahs.android.feature.s3explorer.model.MonthlyGrowthPoint
import com.xerahs.android.feature.s3explorer.model.S3Object

private data class TabInfo(val tab: StatsTab, val label: String, val icon: ImageVector)

private val TAB_ITEMS = listOf(
    TabInfo(StatsTab.OVERVIEW, "Overview", Icons.Default.Dashboard),
    TabInfo(StatsTab.FILE_TYPES, "File Types", Icons.Default.Category),
    TabInfo(StatsTab.AGE, "Age", Icons.Default.CalendarMonth),
    TabInfo(StatsTab.GROWTH, "Growth", Icons.Default.TrendingUp),
    TabInfo(StatsTab.COST, "Cost", Icons.Default.AttachMoney),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3StatsScreen(
    onBack: () -> Unit = {},
    viewModel: S3StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bucket Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isScanning) {
                ScanningIndicator(scanCount = uiState.scanCount)
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (uiState.analytics != null) {
                ScrollableTabRow(
                    selectedTabIndex = TAB_ITEMS.indexOfFirst { it.tab == uiState.selectedTab },
                    edgePadding = 16.dp
                ) {
                    TAB_ITEMS.forEach { info ->
                        Tab(
                            selected = uiState.selectedTab == info.tab,
                            onClick = { viewModel.setSelectedTab(info.tab) },
                            text = { Text(info.label) },
                            icon = { Icon(info.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (uiState.selectedTab) {
                        StatsTab.OVERVIEW -> OverviewTab(uiState.analytics!!)
                        StatsTab.FILE_TYPES -> FileTypesTab(uiState.analytics!!)
                        StatsTab.AGE -> AgeDistributionTab(uiState.analytics!!)
                        StatsTab.GROWTH -> GrowthTab(uiState.analytics!!)
                        StatsTab.COST -> CostTab(
                            costEstimation = uiState.costEstimation,
                            viewsPerFile = uiState.viewsPerFile,
                            estimatedGets = uiState.estimatedGets,
                            estimatedPuts = uiState.estimatedPuts,
                            onViewsPerFileChange = viewModel::setViewsPerFile,
                            onEstimatedGetsChange = viewModel::setEstimatedGets,
                            onEstimatedPutsChange = viewModel::setEstimatedPuts
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningIndicator(scanCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Scanning bucket... $scanCount objects found",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun OverviewTab(analytics: BucketAnalytics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = "Files",
            value = "%,d".format(analytics.totalFiles),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Total Size",
            value = analytics.totalSize.formatSize(),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = "Avg File Size",
            value = analytics.averageFileSize.formatSize(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Folders",
            value = "%,d".format(analytics.totalFolders),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "Notable Files",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    analytics.largestFile?.let { NotableFileCard("Largest", it) }
    analytics.newestFile?.let { NotableFileCard("Newest", it) }
    analytics.oldestFile?.let { NotableFileCard("Oldest", it) }
}

@Composable
private fun NotableFileCard(label: String, obj: S3Object) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = obj.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = obj.size.formatSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (obj.lastModified > 0) {
                    Text(
                        text = obj.lastModified.toShortDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypesTab(analytics: BucketAnalytics) {
    if (analytics.fileTypeBreakdown.isEmpty()) {
        Text("No file data available", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val maxSize = analytics.totalSize.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )

    analytics.fileTypeBreakdown.forEachIndexed { index, breakdown ->
        val isEmpty = breakdown.fileCount == 0
        FileTypeCard(
            breakdown = breakdown,
            fraction = breakdown.totalSize.toFloat() / maxSize,
            color = colors[index % colors.size],
            dimmed = isEmpty
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FileTypeCard(
    breakdown: FileTypeBreakdown,
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    dimmed: Boolean
) {
    val alpha = if (dimmed) 0.4f else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = breakdown.category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = "${breakdown.fileCount} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = breakdown.totalSize.formatSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                Text(
                    text = "%.1f%%".format(fraction * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (!dimmed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeDistributionTab(analytics: BucketAnalytics) {
    if (analytics.ageDistribution.isEmpty()) {
        Text("No file data available", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val maxCount = analytics.ageDistribution.maxOf { it.count }.coerceAtLeast(1)

    Text(
        text = "File Age Distribution",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    analytics.ageDistribution.forEach { bucket ->
        AgeBarRow(bucket = bucket, maxCount = maxCount)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AgeBarRow(bucket: AgeDistributionBucket, maxCount: Int) {
    val isEmpty = bucket.count == 0
    val contentAlpha = if (isEmpty) 0.4f else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = bucket.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = if (isEmpty) "—" else "${bucket.count} files (${bucket.size.formatSize()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!isEmpty) {
                val fraction = bucket.count.toFloat() / maxCount
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun GrowthTab(analytics: BucketAnalytics) {
    if (analytics.monthlyGrowth.isEmpty()) {
        Text("No growth data available", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Text(
        text = "Storage Growth (Last ${analytics.monthlyGrowth.size} months)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    val maxSize = analytics.monthlyGrowth.maxOf { it.cumulativeSize }.coerceAtLeast(1)

    // Scrollable bar chart for many months
    val chartScrollState = rememberScrollState()
    val barWidth = if (analytics.monthlyGrowth.size <= 6) 0.dp else 48.dp
    val useFixedWidth = analytics.monthlyGrowth.size > 6

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .then(if (useFixedWidth) Modifier.horizontalScroll(chartScrollState) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        analytics.monthlyGrowth.forEach { point ->
            val fraction = point.cumulativeSize.toFloat() / maxSize
            Column(
                modifier = if (useFixedWidth) Modifier.width(barWidth) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }

    // Labels row — scrollable in sync when many months
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (useFixedWidth) Modifier.horizontalScroll(chartScrollState) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        analytics.monthlyGrowth.forEach { point ->
            // Show short month label: "Jan", "Feb", etc. parsed from yyyy-MM
            val label = formatMonthLabel(point.yearMonth)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = if (useFixedWidth) Modifier.width(barWidth) else Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Summary table
    Text(
        text = "Monthly Breakdown",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    analytics.monthlyGrowth.reversed().forEach { point ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = point.yearMonth, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "+${point.addedCount} files (+${point.addedSize.formatSize()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = point.cumulativeSize.formatSize(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CostTab(
    costEstimation: CostEstimation?,
    viewsPerFile: Int,
    estimatedGets: Int,
    estimatedPuts: Int,
    onViewsPerFileChange: (Int) -> Unit,
    onEstimatedGetsChange: (Int) -> Unit,
    onEstimatedPutsChange: (Int) -> Unit
) {
    if (costEstimation == null) {
        Text("No cost data available", style = MaterialTheme.typography.bodyMedium)
        return
    }

    // Total cost card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estimated Monthly Cost",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCost(costEstimation.totalMonthlyCost),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Region: ${costEstimation.region}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Cost breakdown
    Text(
        text = "Cost Breakdown",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    CostLineItem("Storage (${formatGB(costEstimation.storageGB)})", costEstimation.storageCost)
    CostLineItem("GET requests (${"%,d".format(costEstimation.estimatedGets)})", costEstimation.getCost)
    CostLineItem("PUT requests (${"%,d".format(costEstimation.estimatedPuts)})", costEstimation.putCost)
    CostLineItem("Bandwidth (${formatGB(costEstimation.bandwidthGB)})", costEstimation.bandwidthCost)

    Spacer(modifier = Modifier.height(24.dp))

    // Sliders
    Text(
        text = "Adjust Estimates",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    SliderWithLabel(
        label = "Views per file",
        value = viewsPerFile,
        range = 1f..100f,
        onValueChange = { onViewsPerFileChange(it) }
    )

    SliderWithLabel(
        label = "GET requests/mo",
        value = estimatedGets,
        range = 0f..100000f,
        onValueChange = { onEstimatedGetsChange(it) }
    )

    SliderWithLabel(
        label = "PUT requests/mo",
        value = estimatedPuts,
        range = 0f..10000f,
        onValueChange = { onEstimatedPutsChange(it) }
    )
}

@Composable
private fun CostLineItem(label: String, cost: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatCost(cost),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "%,d".format(value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatCost(cost: Double): String {
    return when {
        cost >= 100.0 -> "$%.0f".format(cost)
        cost >= 1.0 -> "$%.2f".format(cost)
        cost >= 0.01 -> "$%.3f".format(cost)
        else -> "$%.4f".format(cost)
    }
}

private fun formatGB(gb: Double): String {
    return if (gb < 1.0) "%.2f MB".format(gb * 1024) else "%.2f GB".format(gb)
}

private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatMonthLabel(yearMonth: String): String {
    // yearMonth is "yyyy-MM"
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val monthIndex = (parts[1].toIntOrNull() ?: return yearMonth) - 1
    if (monthIndex !in MONTH_NAMES.indices) return yearMonth
    return MONTH_NAMES[monthIndex]
}
