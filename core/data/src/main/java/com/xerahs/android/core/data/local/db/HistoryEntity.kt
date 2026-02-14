package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val thumbnailPath: String?,
    val url: String?,
    val deleteUrl: String?,
    val uploadDestination: String,
    val timestamp: Long,
    val fileName: String,
    val fileSize: Long
) {
    fun toDomain(): HistoryItem = HistoryItem(
        id = id,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        url = url,
        deleteUrl = deleteUrl,
        uploadDestination = UploadDestination.valueOf(uploadDestination),
        timestamp = timestamp,
        fileName = fileName,
        fileSize = fileSize
    )

    companion object {
        fun fromDomain(item: HistoryItem): HistoryEntity = HistoryEntity(
            id = item.id,
            filePath = item.filePath,
            thumbnailPath = item.thumbnailPath,
            url = item.url,
            deleteUrl = item.deleteUrl,
            uploadDestination = item.uploadDestination.name,
            timestamp = item.timestamp,
            fileName = item.fileName,
            fileSize = item.fileSize
        )
    }
}
