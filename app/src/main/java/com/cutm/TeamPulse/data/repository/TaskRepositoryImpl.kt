package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskAssignmentDao: TaskAssignmentDao,
) : TaskRepository {

    override fun observeTasksForTeam(teamId: String): Flow<List<TaskAssignment>> {
        return taskAssignmentDao.observeByTeam(teamId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTasksForStudent(email: String): Flow<List<TaskAssignment>> {
        return taskAssignmentDao.observeByAssignee(email).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val entity = taskAssignmentDao.getById(taskId) ?: return

        val updated = entity.copy(
            status = status,
            localDirty = true,
            lastModifiedLocal = System.currentTimeMillis()
        )

        taskAssignmentDao.upsert(updated)
    }
}
