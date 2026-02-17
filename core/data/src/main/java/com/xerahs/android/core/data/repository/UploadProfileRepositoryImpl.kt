package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.datastore.SecureCredentialStore
import com.xerahs.android.core.data.local.db.UploadProfileDao
import com.xerahs.android.core.data.local.db.UploadProfileEntity
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadProfileRepositoryImpl @Inject constructor(
    private val profileDao: UploadProfileDao,
    private val credentialStore: SecureCredentialStore
) : UploadProfileRepository {

    override fun getAllProfiles(): Flow<List<UploadProfile>> =
        profileDao.getAllProfiles().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getProfilesForDestination(destination: UploadDestination): Flow<List<UploadProfile>> =
        profileDao.getProfilesForDestination(destination.name).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getProfile(id: String): UploadProfile? =
        profileDao.getProfile(id)?.toDomain()

    override suspend fun getDefaultProfile(destination: UploadDestination): UploadProfile? =
        profileDao.getDefaultProfile(destination.name)?.toDomain()

    override suspend fun createProfile(profile: UploadProfile, config: UploadConfig) {
        profileDao.insertProfile(profile.toEntity())
        saveProfileConfig(profile.id, config)
    }

    override suspend fun updateProfile(profile: UploadProfile, config: UploadConfig) {
        profileDao.insertProfile(profile.toEntity())
        saveProfileConfig(profile.id, config)
    }

    override suspend fun deleteProfile(id: String) {
        profileDao.deleteProfile(id)
        credentialStore.deleteProfileConfig(id)
    }

    override suspend fun setDefault(id: String, destination: UploadDestination) {
        profileDao.clearDefaultForDestination(destination.name)
        profileDao.setDefault(id)
    }

    override fun getProfileConfig(profileId: String, destination: UploadDestination): UploadConfig {
        return credentialStore.getProfileConfig(profileId, destination)
    }

    override fun saveProfileConfig(profileId: String, config: UploadConfig) {
        credentialStore.saveProfileConfig(profileId, config)
    }

    private fun UploadProfileEntity.toDomain() = UploadProfile(
        id = id,
        name = name,
        destination = UploadDestination.valueOf(destination),
        isDefault = isDefault,
        createdAt = createdAt
    )

    private fun UploadProfile.toEntity() = UploadProfileEntity(
        id = id,
        name = name,
        destination = destination.name,
        isDefault = isDefault,
        createdAt = createdAt
    )
}
