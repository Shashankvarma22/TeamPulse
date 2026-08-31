package com.cutm.TeamPulse.data.repository

import androidx.room.withTransaction
import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
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
    private val database: TeamPulseDatabase,
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

    override fun observeTasksForProject(projectId: String): Flow<List<TaskAssignment>> {
        return taskAssignmentDao.observeByProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        applyTaskUpdate(
            taskId = taskId,
            updateFields = { currentEntity ->
                currentEntity.copy(
                    status = status,
                    localDirty = true,
                    lastModifiedLocal = System.currentTimeMillis()
                )
            }
        )
    }

    override suspend fun createTask(task: TaskAssignment) {
        val entity = TaskAssignmentEntity(
            taskId = task.taskId,
            teamId = task.teamId,
            projectId = task.projectId,
            assigneeEmail = task.assigneeEmail,
            title = task.title,
            description = task.description,
            weight = task.weight,
            dueDate = task.dueDate,
            status = task.status,
            hasEverBeenCompleted = task.hasEverBeenCompleted,
            localDirty = true,
            lastModifiedLocal = System.currentTimeMillis(),
            remoteRowIndex = null
        )

        taskAssignmentDao.upsert(entity)
    }

    override suspend fun updateTask(task: TaskAssignment) {
        applyTaskUpdate(
            taskId = task.taskId,
            updateFields = { currentEntity ->
                // Merge caller's fields with current entity
                // CRITICAL: Never trust caller's hasEverBeenCompleted value
                currentEntity.copy(
                    teamId = task.teamId,
                    projectId = task.projectId,
                    assigneeEmail = task.assigneeEmail,
                    title = task.title,
                    description = task.description,
                    weight = task.weight,
                    dueDate = task.dueDate,
                    status = task.status,
                    // hasEverBeenCompleted managed by applyTaskUpdate, not caller
                    localDirty = true,
                    lastModifiedLocal = System.currentTimeMillis(),
                    remoteRowIndex = task.remoteRowIndex
                )
            }
        )
    }

    /**
     * Unified task update helper - ensures atomic status change + completion guard.
     * 
     * CRITICAL INVARIANTS:
     * 1. Always reads current entity from DB (never trusts caller's guard state)
     * 2. Determines "first completion" by comparing current.status → new.status
     * 3. Sets hasEverBeenCompleted flag inside same transaction as task update
     * 4. Phase 6.2 XP award will hook into this transaction block
     * 
     * @param taskId Task to update
     * @param updateFields Lambda that takes current entity and returns updated entity
     *                     (with caller's field changes, but NOT hasEverBeenCompleted)
     */
    private suspend fun applyTaskUpdate(
        taskId: String,
        updateFields: (TaskAssignmentEntity) -> TaskAssignmentEntity
    ) {
        database.withTransaction {
            // 1. Read current entity (source of truth for guard state)
            val currentEntity = taskAssignmentDao.getById(taskId) ?: return@withTransaction
            
            // 2. Apply caller's field updates
            val updatedEntity = updateFields(currentEntity)
            
            // 3. Check if this is first-time completion (guard logic)
            val wasCompleted = currentEntity.status == TaskStatus.DONE
            val isNowCompleted = updatedEntity.status == TaskStatus.DONE
            val justCompletedForFirstTime = !wasCompleted && isNowCompleted && !currentEntity.hasEverBeenCompleted
            
            // 4. Set completion guard if first-time DONE
            val finalEntity = if (justCompletedForFirstTime) {
                updatedEntity.copy(hasEverBeenCompleted = true)
            } else {
                // Preserve existing flag (don't reset to false)
                updatedEntity.copy(hasEverBeenCompleted = currentEntity.hasEverBeenCompleted)
            }
            
            // 5. Persist (atomic with guard check)
            taskAssignmentDao.upsert(finalEntity)
            
            // TODO Phase 6.2: Award XP here if justCompletedForFirstTime && finalEntity.assigneeEmail.isNotEmpty()
            // awardXpForTaskCompletion(finalEntity.assigneeEmail, finalEntity.weight)
        }
    }

    override suspend fun deleteTask(taskId: String) {
        taskAssignmentDao.deleteById(taskId)
    }
}
