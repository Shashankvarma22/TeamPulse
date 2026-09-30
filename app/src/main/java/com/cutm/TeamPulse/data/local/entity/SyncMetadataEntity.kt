package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val lastPullAt: Long,
    val lastPushAt: Long,
    val etagOrRevision: String?,
    
    // NEW (Phase 3): JSON-encoded map of entityId → rowIndex for write-back optimization
    // Example: {"task_123": 5, "task_456": 12} means task_123 is at row 5 in Sheets
    // Populated during pullFromSheets(), consumed by SheetsWriter
    // Key format examples: "tasks:project_abc", "achievements:project_abc", "projects"
    val rowIndexCache: String? = null,  // JSON string, null if not cached yet
)
