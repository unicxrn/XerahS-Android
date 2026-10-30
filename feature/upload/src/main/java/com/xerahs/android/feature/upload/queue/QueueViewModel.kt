package com.xerahs.android.feature.upload.queue

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xerahs.android.feature.upload.worker.StoppedUploadStore
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadNotifier
import com.xerahs.android.feature.upload.worker.UploadSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val stoppedStore = StoppedUploadStore.forCache(context.cacheDir)
    private val hidden = MutableStateFlow<Set<UUID>>(emptySet())

    private val works = workManager.getWorkInfosByTagFlow(UPLOAD_WORK_TAG).map { infos ->
        // Payloads of works Android stopped; only cancelled works can have one.
        val stopped = infos.filter { it.state == WorkInfo.State.CANCELLED }
            .mapNotNull { info -> stoppedStore.read(info.id)?.let { info.id to it } }
            .toMap<UUID, Data>()
        infos to stopped
    }.flowOn(Dispatchers.IO)

    val sections: StateFlow<QueueSections?> =
        combine(works, hidden) { (infos, stopped), gone ->
            QueueSections.build(infos, System.currentTimeMillis(), gone, stopped)
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
        if (spec == null) {
            hidden.update { it + item.id }
            forget(listOf(item.id))
        } else {
            requeue(item, spec)
        }
    }

    private fun requeue(item: QueueItem, spec: UploadSpec?) {
        if (spec == null || item.id in hidden.value) return
        // Same unique name as the notification's Retry, so whichever comes second is a no-op.
        UploadEnqueuer.enqueueRetry(context, item.id, spec)
        hidden.update { it + item.id }
        forget(listOf(item.id))
    }

    /** Drops finished rows. pruneWork() removes finished work whatever its age (it ignores keepResultsForAtLeast), and rows are also hidden here so they go at once. */
    fun clearFinished() {
        val current = sections.value ?: return
        val ids = current.failed.map { it.id } + current.done.map { it.id }
        hidden.update { it + ids }
        forget(current.failed.map { it.id }, dismiss = false)
        workManager.pruneWork()
    }

    /** Removes the saved stop payloads of [ids] and, with [dismiss], their failure notifications. */
    private fun forget(ids: List<UUID>, dismiss: Boolean = true) {
        if (dismiss) {
            val notifications = NotificationManagerCompat.from(context)
            ids.forEach { notifications.cancel(UploadNotifier.notificationIdFor(it)) }
        }
        viewModelScope.launch(Dispatchers.IO) { ids.forEach { stoppedStore.clear(it) } }
    }
}
