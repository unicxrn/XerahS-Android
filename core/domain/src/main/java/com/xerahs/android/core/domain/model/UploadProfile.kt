package com.xerahs.android.core.domain.model

data class UploadProfile(
    val id: String,
    val name: String,
    val destination: UploadDestination,
    val isDefault: Boolean = false,
    val createdAt: Long
)
