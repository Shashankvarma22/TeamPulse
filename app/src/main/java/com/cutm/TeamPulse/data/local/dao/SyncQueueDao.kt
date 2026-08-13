package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.SyncQueueEntity
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: SyncQueueStatus): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: SyncQueueStatus): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = :status WHERE queueId = :queueId")
    suspend fun updateStatus(queueId: Long, status: SyncQueueStatus)
}
