package com.xerahs.android.feature.upload.screenshots

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the Upload action on a screenshot notification: copies the image and queues an upload. */
class ScreenshotActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsRepository(): SettingsRepository
        fun profileRepository(): UploadProfileRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScreenshotNotifier.ACTION_UPLOAD) return
        val uri = intent.getStringExtra(ScreenshotNotifier.EXTRA_URI)?.let(Uri::parse) ?: return
        val notificationId = intent.getIntExtra(ScreenshotNotifier.EXTRA_NOTIFICATION_ID, 0)
        val app = context.applicationContext
        val deps = EntryPointAccessors.fromApplication(app, Deps::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ScreenshotUploads.copyAndEnqueue(app, uri, deps.settingsRepository(), deps.profileRepository())
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(app, "Couldn't read that screenshot", Toast.LENGTH_SHORT).show()
                }
            } finally {
                ScreenshotNotifier.cancel(app, notificationId)
                pending.finish()
            }
        }
    }
}
