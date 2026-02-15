package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xerahs.android.core.domain.model.Tag

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String
) {
    fun toDomain(): Tag = Tag(
        id = id,
        name = name
    )

    companion object {
        fun fromDomain(tag: Tag): TagEntity = TagEntity(
            id = tag.id,
            name = tag.name
        )
    }
}
