package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_profiles")
data class UploadProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val destination: String,
    val isDefault: Boolean = false,
    val createdAt: Long
)
