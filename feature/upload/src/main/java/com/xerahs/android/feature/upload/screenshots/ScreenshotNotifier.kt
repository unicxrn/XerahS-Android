package com.xerahs.android.feature.upload.screenshots

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xerahs.android.core.common.image.calculateInSampleSize

/** Posts the "New screenshot" notification with Upload and Edit actions. */
object ScreenshotNotifier {
    const val CHANNEL = "screenshots_channel"
    const val ACTION_UPLOAD = "com.xerahs.android.action.UPLOAD_SCREENSHOT"
    const val EXTRA_URI = "screenshot_uri"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    private const val ID_BASE = 400000
    private const val ID_SPAN = 100000
    private const val MAX_PREVIEW_EDGE = 512
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** One notification per image, derived from its MediaStore id. */
    fun notificationIdFor(mediaId: Long): Int = ID_BASE + (mediaId % ID_SPAN).toInt()

    fun show(context: Context, mediaId: Long, uri: Uri) {
        val id = notificationIdFor(mediaId)
        val upload = Intent(context, ScreenshotActionReceiver::class.java)
            .setAction(ACTION_UPLOAD)
            .setData(Uri.parse("xerahs-internal://screenshot/$mediaId/upload"))
            .putExtra(EXTRA_URI, uri.toString())
            .putExtra(EXTRA_NOTIFICATION_ID, id)
        // The editor path is the normal share intake: the activity copies the content:// URI on arrival.
        val edit = Intent(Intent.ACTION_SEND)
            .setClassName(context.packageName, "com.xerahs.android.ui.MainActivity")
            .setData(Uri.parse("xerahs-internal://screenshot/$mediaId/edit"))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val editPending = PendingIntent.getActivity(context, id, edit, FLAGS)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle("New screenshot")
            .setContentText("Upload it or edit it first")
            .setContentIntent(editPending)
            .setAutoCancel(true)
            .addAction(0, "Upload", PendingIntent.getBroadcast(context, id, upload, FLAGS))
            .addAction(0, "Edit", editPending)
        preview(context, uri)?.let { bmp ->
            builder.setLargeIcon(bmp)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?))
        }
        post(context, id, builder.build())
    }

    /** A bitmap no larger than 512 px on its long edge, or null if the image can't be read. */
    private fun preview(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_PREVIEW_EDGE)
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        if (id != 0) NotificationManagerCompat.from(context).cancel(id)
    }
}
