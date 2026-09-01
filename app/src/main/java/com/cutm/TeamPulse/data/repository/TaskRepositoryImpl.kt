package com.cutm.TeamPulse.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.StudentProgressDao
import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.local.entity.StudentProgressEntity
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
    private val studentDao: com.cutm.TeamPulse.data.local.dao.StudentDao,
    private val studentProgressDao: com.cutm.TeamPulse.data.local.dao.StudentProgressDao,
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
        android.util.Log.d("TaskRepository", "=== CREATE TASK CALLED ===")
        android.util.Log.d("TaskRepository", "Task ID: ${task.taskId}")
        android.util.Log.d("TaskRepository", "Title: ${task.title}")
        android.util.Log.d("TaskRepository", "Assignee: ${task.assigneeEmail}")
        android.util.Log.d("TaskRepository", "Team: ${task.teamId}")
        android.util.Log.d("TaskRepository", "Project: ${task.projectId}")
        android.util.Log.d("TaskRepository", "Due Date: ${task.dueDate} (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(task.dueDate))})")

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
        android.util.Log.d("TaskRepository", "=== CREATE TASK SUCCEEDED ===")
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
            // CRITICAL: Must run AFTER updateFields() to override any hasEverBeenCompleted
            // value the caller's lambda may have set (prevents caller from bypassing guard)
            val finalEntity = if (justCompletedForFirstTime) {
                updatedEntity.copy(hasEverBeenCompleted = true)
            } else {
                // Preserve existing flag (don't reset to false)
                updatedEntity.copy(hasEverBeenCompleted = currentEntity.hasEverBeenCompleted)
            }
            
            // 5. Persist (atomic with guard check)
            taskAssignmentDao.upsert(finalEntity)
            
            // 6. Award XP if first-time completion
            if (justCompletedForFirstTime && finalEntity.assigneeEmail.isNotEmpty()) {
                awardXpForTaskCompletion(
                    studentEmail = finalEntity.assigneeEmail,
                    taskWeight = finalEntity.weight
                )
            }
        }
    }

    /**
     * Award XP to student for task completion.
     * 
     * CRITICAL: This method executes inside the same database.withTransaction {}
     * block as the task status update (called from applyTaskUpdate).
     * 
     * Atomicity guarantee: Task update + XP award succeed together or fail together.
     * 
     * @param studentEmail Student to award XP
     * @param taskWeight Task weight for XP calculation
     */
    private suspend fun awardXpForTaskCompletion(
        studentEmail: String,
        taskWeight: Float
    ) {
        // Verify student still exists before awarding XP
        // (Prevents FK constraint violation if student was deleted)
        // CRITICAL: This check MUST remain inside the same withTransaction {} block
        // as the subsequent studentProgressDao.upsert() call to prevent race condition
        // where student is deleted between check and insert.
        val studentExists = studentDao.getByEmailSync(studentEmail) != null
        if (!studentExists) {
            Log.w("TaskRepository", "Skipping XP award: student $studentEmail no longer exists")
            return  // Exit early, task completion proceeds without XP award
        }
        
        // Calculate XP
        val xpAmount = if (taskWeight > 0) {
            (taskWeight * 10).toInt()
        } else {
            10  // Flat default for zero/negative weight
        }
        
        // Get or create progress record
        val currentProgress = studentProgressDao.getByEmail(studentEmail)
        val updatedProgress = if (currentProgress != null) {
            currentProgress.copy(
                totalXp = currentProgress.totalXp + xpAmount,
                tasksCompleted = currentProgress.tasksCompleted + 1,
                hasTaskMasterBadge = currentProgress.tasksCompleted + 1 >= 10 || currentProgress.hasTaskMasterBadge,
                lastModifiedLocal = System.currentTimeMillis(),
                localDirty = true
            )
        } else {
            // First task completed by this student
            StudentProgressEntity(
                studentEmail = studentEmail,
                totalXp = xpAmount,
                tasksCompleted = 1,
                hasTaskMasterBadge = false,  // Won't reach threshold on first task
                lastModifiedLocal = System.currentTimeMillis(),
                localDirty = true
            )
        }
        
        studentProgressDao.upsert(updatedProgress)
        
        Log.d("TaskRepository", "Awarded $xpAmount XP to $studentEmail (total: ${updatedProgress.totalXp}, tasks: ${updatedProgress.tasksCompleted})")
    }

    override suspend fun deleteTask(taskId: String) {
        taskAssignmentDao.deleteById(taskId)
    }
}
