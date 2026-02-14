package com.xerahs.android.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xerahs.android.XerahSApplication
import com.xerahs.android.ui.MainActivity

class QuickCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showNotification()
            ACTION_CAPTURE -> {
                // Launch main activity to initiate capture
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_AUTO_CAPTURE, true)
                }
                startActivity(launchIntent)
            }
            ACTION_HIDE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun showNotification() {
        val captureIntent = Intent(this, QuickCaptureService::class.java).apply {
            action = ACTION_CAPTURE
        }
        val capturePendingIntent = PendingIntent.getService(
            this, 0, captureIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val hideIntent = Intent(this, QuickCaptureService::class.java).apply {
            action = ACTION_HIDE
        }
        val hidePendingIntent = PendingIntent.getService(
            this, 1, hideIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, XerahSApplication.CHANNEL_QUICK_CAPTURE)
            .setContentTitle("XerahS Quick Capture")
            .setContentText("Tap to capture screenshot")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_camera, "Capture", capturePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", hidePendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_SHOW = "com.xerahs.android.SHOW_QUICK_CAPTURE"
        const val ACTION_CAPTURE = "com.xerahs.android.QUICK_CAPTURE"
        const val ACTION_HIDE = "com.xerahs.android.HIDE_QUICK_CAPTURE"
        const val EXTRA_AUTO_CAPTURE = "auto_capture"
        private const val NOTIFICATION_ID = 1002

        fun showIntent(context: Context): Intent =
            Intent(context, QuickCaptureService::class.java).apply {
                action = ACTION_SHOW
            }
    }
}
