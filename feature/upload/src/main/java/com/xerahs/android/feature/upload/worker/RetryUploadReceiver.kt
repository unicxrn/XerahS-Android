package com.xerahs.android.feature.upload.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import java.util.UUID

/** Handles the Retry action on a failed upload notification: re-enqueues the files that didn't finish. */
class RetryUploadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val values = extras.keySet().associateWith { extras.getString(it) }
        val spec = UploadSpec.fromStrings(values) ?: return
        val oldId = intent.getStringExtra(UploadNotifier.EXTRA_WORK_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val notifications = NotificationManagerCompat.from(context)
        if (oldId != null) {
            // Same unique name as the queue's Retry, which then hides the old row.
            UploadEnqueuer.enqueueRetry(context, oldId, spec)
            notifications.cancel(UploadNotifier.notificationIdFor(oldId))
            StoppedUploadStore.forCache(context.cacheDir).clear(oldId)
        } else {
            // A notification posted by an older build: no work id to key on.
            UploadEnqueuer.enqueue(context, spec.copy(background = true))
        }
        val notificationId = intent.getIntExtra(UploadNotifier.EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) notifications.cancel(notificationId)
    }
}
