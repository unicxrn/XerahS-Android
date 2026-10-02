package com.xerahs.android.feature.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.common.image.ColorFormat
import com.xerahs.android.core.common.image.FitMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerToolScreen(onBack: () -> Unit, viewModel: ColorPickerToolViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { ImageLoading.load(context, uri) }
            if (bmp == null) {
                error = "Couldn't open the image"
            } else {
                bitmap = bmp
                picked = null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Color picker") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose image") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            bitmap?.let { bmp ->
                Box(
                    Modifier.fillMaxWidth().height(360.dp)
                        .onSizeChanged { viewSize = it }
                        .pointerInput(bmp) {
                            detectTapGestures { offset ->
                                FitMapping.toBitmap(offset.x, offset.y, viewSize.width, viewSize.height, bmp.width, bmp.height)
                                    ?.let { (x, y) -> picked = bmp.getPixel(x, y) or 0xFF000000.toInt(); viewModel.remember(picked!!) }
                            }
                        }
                ) {
                    Image(bmp.asImageBitmap(), contentDescription = "Image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Text("Tap the image to pick a colour", style = MaterialTheme.typography.bodySmall)
            }
            picked?.let { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).background(Color(c), MaterialTheme.shapes.small))
                    Column(Modifier.weight(1f)) {
                        Text(ColorFormat.hex(c), fontFamily = FontFamily.Monospace)
                        Text(ColorFormat.rgb(c), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(ColorFormat.hex(c))) }) { Text("Copy HEX") }
                }
            }
            val recent by viewModel.recent.collectAsState()
            if (recent.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { c ->
                        Box(Modifier.size(32.dp).background(Color(c), MaterialTheme.shapes.small).clickable { picked = c })
                    }
                }
            }
        }
    }
}
