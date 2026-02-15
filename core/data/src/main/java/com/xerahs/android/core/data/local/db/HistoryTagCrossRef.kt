package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "history_tag_cross_ref",
    primaryKeys = ["historyId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HistoryTagCrossRef(
    val historyId: String,
    val tagId: String
)
