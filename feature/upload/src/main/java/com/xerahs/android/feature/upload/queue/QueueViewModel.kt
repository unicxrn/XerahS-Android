package com.xerahs.android.feature.upload.queue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
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
    fun retry(item: QueueItem) = requeue(item, item.retrySpec)

    /** Duplicate row: upload the file even though it is already in history. */
    fun uploadAnyway(item: QueueItem) = requeue(item, item.uploadAnywaySpec())

    /** Duplicate row: drop that file and upload the rest, if any. */
    fun skipFile(item: QueueItem) {
        val spec = item.skipFileSpec()
        if (spec == null) hidden.update { it + item.id } else requeue(item, spec)
    }

    private fun requeue(item: QueueItem, spec: UploadSpec?) {
        if (spec == null || item.id in hidden.value) return
        // A fixed unique name makes a second tap a no-op even before the hidden set updates.
        UploadEnqueuer.enqueue(context, spec, uniqueName = "retry-${item.id}")
        hidden.update { it + item.id }
    }

    /** Drops finished rows. pruneWork() removes finished work whatever its age (it ignores keepResultsForAtLeast), and rows are also hidden here so they go at once. */
    fun clearFinished() {
        val current = sections.value ?: return
        hidden.update { it + current.failed.map { i -> i.id } + current.done.map { i -> i.id } }
        workManager.pruneWork()
    }
}
