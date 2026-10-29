package com.xerahs.android.feature.upload.queue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val hidden = MutableStateFlow<Set<UUID>>(emptySet())

    val sections: StateFlow<QueueSections?> =
        combine(workManager.getWorkInfosByTagFlow(UPLOAD_WORK_TAG), hidden) { infos, gone ->
            QueueSections.build(infos, System.currentTimeMillis(), gone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel(item: QueueItem) {
        workManager.cancelWorkById(item.id)
    }

    /** Re-enqueues what was left of a failed upload, then hides the old row. */
    fun retry(item: QueueItem) {
        val spec = item.retrySpec ?: return
        UploadEnqueuer.enqueue(context, spec)
        hidden.update { it + item.id }
    }

    /** Drops finished rows. pruneWork() only removes work past its retention, so rows are also hidden here. */
    fun clearFinished() {
        val current = sections.value ?: return
        hidden.update { it + current.failed.map { i -> i.id } + current.done.map { i -> i.id } }
        workManager.pruneWork()
    }
}
