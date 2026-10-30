package com.xerahs.android.shortcuts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.feature.upload.worker.CopyUrlReceiver
import com.xerahs.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Copy last link" shortcut. Invisible: copies the newest upload's link and closes. A local
 * upload has no link, so it opens that item's share screen instead.
 */
@AndroidEntryPoint
class CopyLastLinkActivity : ComponentActivity() {

    @Inject
    lateinit var historyRepository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val item = runCatching { historyRepository.latest().firstOrNull() }.getOrNull()
            val url = item?.url?.takeIf { it.isNotBlank() }
            when {
                item == null -> toast("No uploads yet")
                item.uploadDestination == UploadDestination.LOCAL || url == null -> startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("xerahs://share/${item.id}"), this@CopyLastLinkActivity, MainActivity::class.java)
                        // Reuse a running app: onNewIntent opens the share screen.
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                else -> CopyUrlReceiver.copy(applicationContext, url, item.fileName, item.isImage)
            }
            finish()
        }
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
}
