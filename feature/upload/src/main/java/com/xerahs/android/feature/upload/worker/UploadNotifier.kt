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
    const val EXTRA_WORK_ID = "old_work_id"

    private const val ID_BASE = 2000
    private const val ID_SPAN = 100000
    private const val PROGRESS_BASE = ID_BASE + ID_SPAN

    fun notificationIdFor(workId: UUID): Int = ID_BASE + (workId.hashCode() and 0x0FFFFFFF) % ID_SPAN

    /** Separate from [notificationIdFor] so stopping the foreground service can't remove the result. */
    fun progressIdFor(workId: UUID): Int = PROGRESS_BASE + (workId.hashCode() and 0x0FFFFFFF) % ID_SPAN

    fun shareUri(historyId: String): Uri = Uri.parse("xerahs://share/${Uri.encode(historyId)}")
    fun queueUri(): Uri = Uri.parse("xerahs://queue")

    /** Makes each action's PendingIntent unique per work, so two works whose IDs collide never share extras. */
    private fun actionUri(workId: UUID, action: String): Uri = Uri.parse("xerahs-internal://work/$workId/$action")

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
        fileName: String? = null,
        isImage: Boolean? = null,
    ) {
        val id = notificationIdFor(workId)
        val first = url.lines().firstOrNull().orEmpty()
        val target = if (count == 1 && historyId != null) shareUri(historyId) else queueUri()
        val copy = CopyUrlReceiver.intent(context, url, fileName, isImage).setData(actionUri(workId, "copy"))

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle(if (count > 1) "$count uploads complete" else "Upload complete")
            .setContentText(first)
            .setContentIntent(open(context, target, id))
            .setAutoCancel(true)
            .addAction(0, "Copy link", PendingIntent.getBroadcast(context, id, copy, FLAGS))
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

    /**
     * Posts a final failure. With [canRetry] and a [retry] spec, adds a Retry action that re-enqueues it.
     * When part of a batch of [total] files made it, [uploadedUrls] (one per line) are named in the text
     * and offered through a Copy action.
     */
    fun failure(
        context: Context,
        workId: UUID,
        message: String,
        canRetry: Boolean,
        retry: UploadSpec? = null,
        uploadedUrls: List<String> = emptyList(),
        total: Int = 0,
    ) {
        val id = notificationIdFor(workId)
        val text = failureText(message, uploadedUrls.size, total)
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle("Upload failed")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, queueUri(), id))
            .setAutoCancel(true)
        if (canRetry && retry != null) {
            builder.addAction(0, "Retry", PendingIntent.getBroadcast(context, id, enqueueIntent(context, workId, "retry", id, retry), FLAGS))
        }
        if (uploadedUrls.isNotEmpty()) {
            val copy = CopyUrlReceiver.intent(context, uploadedUrls.joinToString("\n")).setData(actionUri(workId, "copy"))
            builder.addAction(0, "Copy links", PendingIntent.getBroadcast(context, id + 1, copy, FLAGS))
        }
        post(context, id, builder.build())
    }

    /** "2 of 5 uploaded. <message>" when part of a batch made it, otherwise just [message]. */
    fun failureText(message: String, uploaded: Int, total: Int): String =
        if (uploaded > 0 && total > 1) "$uploaded of $total uploaded. $message" else message

    /**
     * A background upload (a direct share) stopped at a file that is already in history. Offers the
     * existing link and "Upload anyway", which re-enqueues the rest without the duplicate check.
     */
    fun duplicate(context: Context, workId: UUID, fileName: String?, url: String?, remaining: UploadSpec?) {
        val id = notificationIdFor(workId)
        val first = url?.lines()?.firstOrNull().orEmpty()
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(com.xerahs.android.core.ui.R.drawable.ic_stat_xerahs)
            .setContentTitle(if (fileName.isNullOrBlank()) "Already uploaded" else "Already uploaded: $fileName")
            .setContentText(first)
            .setContentIntent(open(context, queueUri(), id))
            .setAutoCancel(true)
        if (first.isNotEmpty()) {
            val copy = CopyUrlReceiver.intent(context, url!!, fileName).setData(actionUri(workId, "copy"))
            builder.addAction(0, "Copy link", PendingIntent.getBroadcast(context, id, copy, FLAGS))
        }
        if (remaining != null) {
            val again = enqueueIntent(context, workId, "anyway", id, remaining.copy(skipDuplicateCheck = true))
            builder.addAction(0, "Upload anyway", PendingIntent.getBroadcast(context, id + 1, again, FLAGS))
        }
        post(context, id, builder.build())
    }

    /** A [RetryUploadReceiver] intent that enqueues [spec] and clears notification [notificationId]. */
    private fun enqueueIntent(context: Context, workId: UUID, action: String, notificationId: Int, spec: UploadSpec): Intent {
        val intent = Intent(context, RetryUploadReceiver::class.java)
            .setData(actionUri(workId, action))
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(EXTRA_WORK_ID, workId.toString())
        UploadEnqueuer.inputData(spec).keyValueMap.forEach { (key, value) ->
            if (value != null) intent.putExtra(key, value.toString())
        }
        return intent
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
