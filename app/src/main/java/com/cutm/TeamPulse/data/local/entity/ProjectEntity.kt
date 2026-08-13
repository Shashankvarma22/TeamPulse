package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cutm.TeamPulse.domain.model.ProjectStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val name: String,
    val teacherEmail: String,
    val spreadsheetId: String,
    val driveFolderId: String,
    val startDate: Long,
    val dueDate: Long,
    val status: ProjectStatus,
    val githubRepo: String?,
    val localDirty: Boolean,
    val lastModifiedLocal: Long,
    val lastSyncedAt: Long?,
)
