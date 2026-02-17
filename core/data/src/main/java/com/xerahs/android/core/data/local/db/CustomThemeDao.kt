package com.xerahs.android.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomThemeDao {

    @Query("SELECT * FROM custom_themes ORDER BY createdAt DESC")
    fun getAllThemes(): Flow<List<CustomThemeEntity>>

    @Query("SELECT * FROM custom_themes WHERE id = :id")
    suspend fun getTheme(id: String): CustomThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: CustomThemeEntity)

    @Query("DELETE FROM custom_themes WHERE id = :id")
    suspend fun deleteTheme(id: String)
}
