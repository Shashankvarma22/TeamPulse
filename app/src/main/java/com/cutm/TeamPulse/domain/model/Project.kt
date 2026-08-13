package com.cutm.TeamPulse.domain.model

data class Project(
    val projectId: String,
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
