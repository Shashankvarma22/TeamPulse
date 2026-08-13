package com.cutm.TeamPulse.domain.model

data class TaskAssignment(
    val taskId: String,
    val teamId: String,
    val projectId: String,
    val assigneeEmail: String,
    val title: String,
    val description: String,
    val weight: Float,
    val dueDate: Long,
    val status: TaskStatus,
    val localDirty: Boolean,
    val lastModifiedLocal: Long,
    val remoteRowIndex: Int?,
)
