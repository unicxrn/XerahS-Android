package com.xerahs.android.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadProfileDao {

    @Query("SELECT * FROM upload_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<UploadProfileEntity>>

    @Query("SELECT * FROM upload_profiles WHERE destination = :destination ORDER BY createdAt DESC")
    fun getProfilesForDestination(destination: String): Flow<List<UploadProfileEntity>>

    @Query("SELECT * FROM upload_profiles WHERE id = :id")
    suspend fun getProfile(id: String): UploadProfileEntity?

    @Query("SELECT * FROM upload_profiles WHERE destination = :destination AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(destination: String): UploadProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UploadProfileEntity)

    @Query("DELETE FROM upload_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("UPDATE upload_profiles SET isDefault = 0 WHERE destination = :destination")
    suspend fun clearDefaultForDestination(destination: String)

    @Query("UPDATE upload_profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)
}
