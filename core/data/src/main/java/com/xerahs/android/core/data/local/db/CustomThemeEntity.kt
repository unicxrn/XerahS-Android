package com.xerahs.android.core.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_themes")
data class CustomThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val seedColor: Int,
    val createdAt: Long
)
