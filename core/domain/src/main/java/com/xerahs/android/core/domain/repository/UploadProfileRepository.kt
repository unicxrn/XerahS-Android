package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import kotlinx.coroutines.flow.Flow

interface UploadProfileRepository {
    fun getAllProfiles(): Flow<List<UploadProfile>>
    fun getProfilesForDestination(destination: UploadDestination): Flow<List<UploadProfile>>
    suspend fun getProfile(id: String): UploadProfile?
    suspend fun getDefaultProfile(destination: UploadDestination): UploadProfile?
    suspend fun createProfile(profile: UploadProfile, config: UploadConfig)
    suspend fun updateProfile(profile: UploadProfile, config: UploadConfig)
    suspend fun deleteProfile(id: String)
    suspend fun setDefault(id: String, destination: UploadDestination)
    fun getProfileConfig(profileId: String, destination: UploadDestination): UploadConfig
    fun saveProfileConfig(profileId: String, config: UploadConfig)
}
