package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    fun observeTasksForTeam(teamId: String): Flow<List<TaskAssignment>>

    fun observeTasksForStudent(email: String): Flow<List<TaskAssignment>>

    suspend fun updateTaskStatus(taskId: String, status: TaskStatus)

    suspend fun createTask(task: TaskAssignment)
}
