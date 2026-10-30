package com.xerahs.android.feature.upload.worker

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.xerahs.android.core.domain.LinkFormatter
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.upload.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CopyUrlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        val isImage = if (intent.hasExtra(EXTRA_IS_IMAGE)) intent.getBooleanExtra(EXTRA_IS_IMAGE, false) else null
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                copy(context.applicationContext, url, fileName, isImage)
            } finally {
                pending.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CopyEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_IS_IMAGE = "is_image"

        /** Broadcast for a copy button. [fileName] and [isImage] feed the "Copy link as" format. */
        fun intent(context: Context, url: String, fileName: String? = null, isImage: Boolean? = null): Intent =
            Intent(context, CopyUrlReceiver::class.java).apply {
                putExtra(EXTRA_URL, url)
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
                isImage?.let { putExtra(EXTRA_IS_IMAGE, it) }
            }

        /**
         * Copies upload links the same way everywhere: upload screen, notification, widget and
         * shortcut. Each link (one per line) is written in the "Copy link as" format; without a
         * [fileName] or [isImage], both come from the URL.
         */
        suspend fun copy(context: Context, url: String, fileName: String? = null, isImage: Boolean? = null, toast: Boolean = true) {
            val format = EntryPointAccessors.fromApplication(context.applicationContext, CopyEntryPoint::class.java)
                .settingsRepository().getLinkFormat().first()
            copyText(context, LinkFormatter.formatLinks(url, format, fileName, isImage), toast)
        }

        /** Puts [text] on the clipboard as is. */
        fun copyText(context: Context, text: String, toast: Boolean = true) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Upload URL", text))
            // Android 13+ shows its own copy confirmation.
            if (toast && Build.VERSION.SDK_INT < 33) {
                Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
