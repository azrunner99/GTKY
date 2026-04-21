package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.AppConfig

@Dao
interface AppConfigDao {
    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(config: AppConfig)

    @Query("DELETE FROM app_config WHERE `key` = :key")
    suspend fun deleteKey(key: String)
}
