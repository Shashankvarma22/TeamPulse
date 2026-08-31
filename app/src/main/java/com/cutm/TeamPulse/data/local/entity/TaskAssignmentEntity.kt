package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cutm.TeamPulse.domain.model.TaskStatus

@Entity(tableName = "task_assignments")
data class TaskAssignmentEntity(
    @PrimaryKey val taskId: String,
    val teamId: String,
    val projectId: String,
    val assigneeEmail: String,
    val title: String,
    val description: String,
    val weight: Float,
    val dueDate: Long,
    val status: TaskStatus,
    val hasEverBeenCompleted: Boolean = false,  // One-time XP guard: set on first COMPLETED transition
    val localDirty: Boolean,
    val lastModifiedLocal: Long,
    val remoteRowIndex: Int?,
)
