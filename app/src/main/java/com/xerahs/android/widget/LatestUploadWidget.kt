package com.xerahs.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.xerahs.android.R
import com.xerahs.android.core.common.image.calculateInSampleSize
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.feature.upload.worker.CopyUrlReceiver
import com.xerahs.android.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LatestUploadWidgetEntryPoint {
    fun historyRepository(): HistoryRepository
}

/** Home-screen widget showing the newest upload: thumbnail, link, host, copy and upload. */
class LatestUploadWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_WIDE, SIZE_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, LatestUploadWidgetEntryPoint::class.java)
            .historyRepository()
        val first = try {
            repo.latest().firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val firstThumb = first?.let { withContext(Dispatchers.IO) { loadThumbnail(it) } }
        provideContent {
            // Keep collecting so a refresh during the same Glance session shows the newest item.
            val item by repo.latest().collectAsState(initial = first)
            val thumb by produceState(initialValue = firstThumb.takeIf { item?.id == first?.id }, item?.id) {
                value = item?.let { withContext(Dispatchers.IO) { loadThumbnail(it) } }
            }
            WidgetContent(context, item, thumb)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, item: HistoryItem?, thumb: Bitmap?) {
        val isLocal = item?.uploadDestination == UploadDestination.LOCAL
        val tall = LocalSize.current.height >= SIZE_TALL.height
        val captureIntent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val url = item?.url?.takeIf { it.isNotBlank() }
        val firstUrl = url?.lines()?.firstOrNull().orEmpty()
        val copyDescription = context.getString(R.string.widget_copy_link)
        val tapAction = when {
            item == null -> null
            isLocal -> actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("xerahs://share/${item.id}"), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            firstUrl.isNotEmpty() -> actionSendBroadcast(
                Intent(context, CopyUrlReceiver::class.java).putExtra("url", url)
            )
            else -> null
        }
        val thumbSize = if (tall) 72.dp else 48.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(12.dp)
        ) {
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (item != null) {
                    Thumb(item, thumb, thumbSize)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(
                        modifier = GlanceModifier.defaultWeight().then(
                            if (tapAction != null) {
                                GlanceModifier
                                    .clickable(tapAction)
                                    .semantics { contentDescription = if (isLocal) item.fileName else copyDescription }
                            } else GlanceModifier
                        )
                    ) {
                        Text(
                            text = if (item.uploadDestination == UploadDestination.LOCAL) item.fileName else firstUrl.ifEmpty { item.fileName },
                            maxLines = if (tall) 2 else 1,
                            style = TextStyle(color = TEXT, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = when {
                                !tall -> hostOf(item)
                                isLocal -> context.getString(R.string.widget_tap_to_open)
                                else -> context.getString(R.string.widget_tap_to_copy)
                            },
                            maxLines = 1,
                            style = TextStyle(color = MUTED, fontSize = 11.sp)
                        )
                        if (tall) {
                            Spacer(GlanceModifier.size(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = hostOf(item),
                                    maxLines = 1,
                                    modifier = GlanceModifier
                                        .background(ImageProvider(R.drawable.widget_chip))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = TextStyle(color = LIME, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(
                                        item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                                    ).toString(),
                                    maxLines = 1,
                                    style = TextStyle(color = MUTED, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = context.getString(R.string.widget_empty),
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .height(48.dp)
                        .clickable(actionStartActivity(captureIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_upload),
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_button))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    @Composable
    private fun Thumb(item: HistoryItem, bitmap: Bitmap?, size: androidx.compose.ui.unit.Dp) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(size).cornerRadius(12.dp)
            )
        } else {
            // File missing or not an image: a lime dot stands in for the host.
            Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
                Box(
                    modifier = GlanceModifier
                        .size(14.dp)
                        .background(ImageProvider(R.drawable.widget_dot)),
                    content = {}
                )
            }
        }
    }

    companion object {
        private val SIZE_WIDE = DpSize(250.dp, 56.dp)
        private val SIZE_TALL = DpSize(250.dp, 100.dp)

        private val INK = ColorProvider(Color(0xFF0E0F12))
        private val TEXT = ColorProvider(Color(0xFFF2F3F5))
        private val MUTED = ColorProvider(Color(0xFF9AA0A8))
        private val LIME = ColorProvider(Color(0xFFB8F23A))

        private const val MAX_THUMB_EDGE = 256

        internal fun hostOf(item: HistoryItem): String {
            if (item.uploadDestination == UploadDestination.LOCAL) return UploadDestination.LOCAL.displayName
            val host = runCatching { Uri.parse(item.url?.lines()?.firstOrNull().orEmpty()).host }.getOrNull()
            return host?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: item.uploadDestination.displayName
        }

        internal fun loadThumbnail(item: HistoryItem): Bitmap? {
            if (!item.isImage) return null
            val candidates = listOfNotNull(item.thumbnailPath, item.filePath)
            for (path in candidates) {
                val file = File(path)
                if (!file.isFile) continue
                val bitmap = runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(minOf(bounds.outWidth, bounds.outHeight), minOf(bounds.outWidth, bounds.outHeight), MAX_THUMB_EDGE)
                    }
                    BitmapFactory.decodeFile(path, opts)
                }.getOrNull()
                if (bitmap != null) return bitmap
            }
            return null
        }
    }
}
