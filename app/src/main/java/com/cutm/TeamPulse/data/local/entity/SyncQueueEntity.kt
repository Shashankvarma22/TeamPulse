package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cutm.TeamPulse.domain.model.SyncOperationType
import com.cutm.TeamPulse.domain.model.SyncQueueStatus

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val operationType: SyncOperationType,
    val targetTab: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val retryCount: Int,
    val createdAt: Long,
    val status: SyncQueueStatus,
)
