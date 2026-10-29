package com.xerahs.android.feature.upload.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xerahs.android.core.domain.model.AfterUploadAction
import java.util.UUID

/**
 * Upload notifications. Each work gets its own IDs so concurrent uploads don't replace each other,
 * and taps deep-link into the app: a single upload opens its share screen, failures and batches
 * open the queue.
 */
object UploadNotifier {
    const val CHANNEL_PROGRESS = "upload_channel"
    const val CHANNEL_RESULT = "upload_complete_channel"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    private const val ID_BASE = 2000
    private const val ID_SPAN = 100000
    private const val PROGRESS_BASE = ID_BASE + ID_SPAN

    fun notificationIdFor(workId: UUID): Int = ID_BASE + (workId.hashCode() and 0x0FFFFFFF) % ID_SPAN

    /** Separate from [notificationIdFor] so stopping the foreground service can't remove the result. */
    fun progressIdFor(workId: UUID): Int = PROGRESS_BASE + (workId.hashCode() and 0x0FFFFFFF) % ID_SPAN

    fun shareUri(historyId: String): Uri = Uri.parse("xerahs://share/${Uri.encode(historyId)}")
    fun queueUri(): Uri = Uri.parse("xerahs://queue")

    fun progress(context: Context, workId: UUID, text: String, current: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle("XerahS Upload")
            .setContentText(text)
            .setProgress(total, current, current == 0)
            .setContentIntent(open(context, queueUri(), progressIdFor(workId)))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun success(
        context: Context,
        workId: UUID,
        historyId: String?,
        url: String,
        count: Int = 1,
        actions: Set<AfterUploadAction> = emptySet(),
    ) {
        val id = notificationIdFor(workId)
        val first = url.lines().firstOrNull().orEmpty()
        val target = if (count == 1 && historyId != null) shareUri(historyId) else queueUri()
        val copy = Intent(context, CopyUrlReceiver::class.java).putExtra("url", url)

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle(if (count > 1) "$count uploads complete" else "Upload complete")
            .setContentText(first)
            .setContentIntent(open(context, target, id))
            .setAutoCancel(true)
            .addAction(0, "Copy URL", PendingIntent.getBroadcast(context, id, copy, FLAGS))
        if (AfterUploadAction.SHARE_SHEET in actions && first.isNotEmpty()) {
            val share = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, first), null
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(0, "Share", PendingIntent.getActivity(context, id + 1, share, FLAGS))
        }
        if (AfterUploadAction.OPEN_URL in actions && first.startsWith("http", ignoreCase = true)) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(first)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(0, "Open", PendingIntent.getActivity(context, id + 2, view, FLAGS))
        }
        post(context, id, builder.build())
    }

    /** Posts a final failure. With [canRetry] and a [retry] spec, adds a Retry action that re-enqueues it. */
    fun failure(context: Context, workId: UUID, message: String, canRetry: Boolean, retry: UploadSpec? = null) {
        val id = notificationIdFor(workId)
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle("Upload failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open(context, queueUri(), id))
            .setAutoCancel(true)
        if (canRetry && retry != null) {
            val intent = Intent(context, RetryUploadReceiver::class.java).putExtra(EXTRA_NOTIFICATION_ID, id)
            UploadEnqueuer.inputData(retry).keyValueMap.forEach { (key, value) ->
                if (value != null) intent.putExtra(key, value.toString())
            }
            builder.addAction(0, "Retry", PendingIntent.getBroadcast(context, id, intent, FLAGS))
        }
        post(context, id, builder.build())
    }

    /** An explicit intent to our launcher activity carrying the deep link, so no other app can catch it. */
    private fun open(context: Context, uri: Uri, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let { intent.component = it }
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    // POST_NOTIFICATIONS is requested by the app; without it notify() is a no-op, which is fine here.
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
