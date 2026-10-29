package com.xerahs.android.feature.upload.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Handles the Retry action on a failed upload notification: re-enqueues the files that didn't finish. */
class RetryUploadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val values = extras.keySet().associateWith { extras.getString(it) }
        val spec = UploadSpec.fromStrings(values) ?: return
        UploadEnqueuer.enqueue(context, spec)
        val notificationId = intent.getIntExtra(UploadNotifier.EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
