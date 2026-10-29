package com.xerahs.android.feature.upload.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.HostChip
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.hostColor

/** Everything uploading, waiting, failed or finished in the last 24 hours. */
@Composable
fun QueueScreen(onBack: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LumenTopBar(title = "Queue", onBack = onBack, actions = {
                if (sections?.hasFinished == true) {
                    OutlinedButton(onClick = viewModel::clearFinished, shape = CircleShape) { Text("Clear finished") }
                }
            })
        }
    ) { padding ->
        val s = sections
        when {
            s == null -> Box(Modifier.fillMaxSize().padding(padding))
            s.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing uploading", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                section("Running", s.running, viewModel)
                section("Waiting", s.waiting, viewModel)
                section("Failed", s.failed, viewModel)
                section("Done in the last 24 hours", s.done, viewModel)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, rows: List<QueueItem>, vm: QueueViewModel) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { Eyebrow(title, Modifier.padding(top = 10.dp, bottom = 2.dp)) }
    items(rows, key = { it.id }) { QueueRow(it, vm) }
}

@Composable
private fun QueueRow(item: QueueItem, vm: QueueViewModel) {
    val active = item.isActive
    LumenCard(Modifier.fillMaxWidth(), radius = 22.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val label = if (item.fileCount > 1) "${item.fileName} and ${item.fileCount - 1} more" else item.fileName
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.destination?.let { HostChip(it.displayName, it.hostColor()) }
            }
            if (item.fraction != null) {
                LinearProgressIndicator(progress = { item.fraction }, Modifier.fillMaxWidth())
            } else if (item.isRunning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (active || item.retrySpec != null) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active) {
                        OutlinedButton(
                            onClick = { vm.cancel(item) }, shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Cancel") }
                    }
                    if (item.retrySpec != null) {
                        OutlinedButton(onClick = { vm.retry(item) }, shape = CircleShape) { Text("Retry") }
                    }
                }
            }
        }
    }
}
