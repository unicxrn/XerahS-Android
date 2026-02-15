package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xerahs.android.core.domain.model.Album

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
) {
    fun toDomain(): Album = Album(
        id = id,
        name = name,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(album: Album): AlbumEntity = AlbumEntity(
            id = album.id,
            name = album.name,
            createdAt = album.createdAt
        )
    }
}
