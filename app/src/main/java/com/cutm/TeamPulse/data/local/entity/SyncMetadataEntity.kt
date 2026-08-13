package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val lastPullAt: Long,
    val lastPushAt: Long,
    val etagOrRevision: String?,
)
