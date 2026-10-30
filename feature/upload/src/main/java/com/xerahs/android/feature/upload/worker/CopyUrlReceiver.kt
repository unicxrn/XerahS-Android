package com.xerahs.android.feature.upload.worker

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.xerahs.android.feature.upload.R

class CopyUrlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        copy(context, url)
    }

    companion object {
        /** Copies an upload link the same way everywhere: notification, widget and shortcut. */
        fun copy(context: Context, url: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Upload URL", url))
            // Android 13+ shows its own copy confirmation.
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
