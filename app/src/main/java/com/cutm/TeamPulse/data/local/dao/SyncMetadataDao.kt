package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    fun observeByKey(key: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun getByKey(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)
}
