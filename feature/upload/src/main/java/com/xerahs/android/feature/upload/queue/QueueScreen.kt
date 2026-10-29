package com.xerahs.android.feature.upload.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xerahs.android.core.ui.lumen.LumenTopBar

/** Upload queue. A placeholder for now; the list of uploads comes later. */
@Composable
fun QueueScreen(onBack: () -> Unit) {
    Scaffold(topBar = { LumenTopBar(title = "Queue", onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
