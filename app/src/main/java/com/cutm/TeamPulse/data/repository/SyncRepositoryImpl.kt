package com.cutm.TeamPulse.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.cutm.TeamPulse.core.dispatchers.DispatcherProvider
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.core.sheets.SheetsProbeReader
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.SyncMetadataDao
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.data.remote.SheetsWriter
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.domain.model.SyncOperationType
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import com.cutm.TeamPulse.domain.model.SyncStatus
import com.cutm.TeamPulse.domain.repository.SyncRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foundation sync repository. Queue processing is a no-op until Sheets API integration.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val sheetsProbeReader: SheetsProbeReader,
    private val sheetsWriter: SheetsWriter,
    private val projectDao: ProjectDao,
    private val teamDao: TeamDao,
    private val studentDao: StudentDao,
    private val taskAssignmentDao: com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao,
    private val syncMetadataDao: com.cutm.TeamPulse.data.local.dao.SyncMetadataDao,
    private val taskRepository: Lazy<com.cutm.TeamPulse.domain.repository.TaskRepository>,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider,
) : SyncRepository {

    override fun observeSyncStatus(): Flow<SyncStatus> {
        return syncQueueDao.observeByStatus(SyncQueueStatus.PENDING).map { pending ->
            SyncStatus(
                pendingCount = pending.size,
                lastSyncAt = null,
                isSyncing = false,
            )
        }
    }

    override suspend fun processQueue(): ApiResult<Unit> = withContext(dispatchers.io) {
        return@withContext try {
            Log.d(TAG, "processQueue: Starting")

            // 1. Fetch up to 10 PENDING items (batch size)
            val pendingItems = syncQueueDao.getByStatus(SyncQueueStatus.PENDING)
                .take(10)  // Limit to 10 per batch
            
            if (pendingItems.isEmpty()) {
                Log.d(TAG, "processQueue: No pending items in sync_queue")
                return@withContext ApiResult.Success(Unit)
            }

            Log.d(TAG, "processQueue: Processing ${pendingItems.size} items from sync_queue")

            // 2. Process each item independently (failures don't abort batch)
            var successCount = 0
            var failureCount = 0

            for (item in pendingItems) {
                try {
                    Log.d(TAG, "processQueue: Processing item ${item.queueId} (${item.entityType} ${item.entityId})")

                    // Call appropriate SheetsWriter method based on entityType + operationType
                    val writeResult = when {
                        item.entityType == "TASK" && (item.operationType == SyncOperationType.UPDATE || item.operationType == SyncOperationType.APPEND) -> {
                            /**
                             * TASK ROUTING RULE (from Phase 3 guard trace + approved fix):
                             * 
                             * When processQueue() processes a queued task write:
                             * - Source: TaskRepositoryImpl.applyTaskUpdate() (lines 113-154)
                             *   ↓ (sets hasEverBeenCompleted guard)
                             *   ↓ (calls taskAssignmentDao.upsert at line 145)
                             *   ↓ (calls awardXpForTaskCompletion, enqueues to sync_queue)
                             * - Next: processQueue() (this method) gets the queued item
                             * - Finally: SheetsWriter.writeTask() below (guard already ran, no re-validation)
                             * 
                             * CRITICAL: The queued task entity was created by applyTaskUpdate(),
                             * which already validated hasEverBeenCompleted. Sheets gets that final state.
                             * 
                             * FUTURE NOTE (Phase 5, pullFromSheets enhancement):
                             * When Sheets-sourced tasks are pulled in Phase 5 and conflict with Room:
                             * - Existing task with status change → route through taskRepository.get().updateTask()
                             *   to re-validate hasEverBeenCompleted guard (prevents XP farming via Sheets edit)
                             * - New task (not in Room) → direct DAO upsert (no guard needed, status=TODO)
                             * This routing rule applies to pullFromSheets(), not processQueue().
                             * processQueue() only writes pre-validated entities from the app or sync operations.
                             */
                            
                            val taskEntity = moshi.adapter(com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity::class.java)
                                .fromJson(item.payloadJson)
                            
                            if (taskEntity != null) {
                                // Resolve spreadsheetId from projectId using suspend DAO call (safe on IO dispatcher)
                                val spreadsheetId = projectDao.getById(taskEntity.projectId)?.spreadsheetId
                                if (spreadsheetId != null) {
                                    sheetsWriter.writeTask(spreadsheetId, taskEntity)
                                } else {
                                    val reason = "Could not resolve spreadsheetId for project ${taskEntity.projectId}"
                                    Log.e(TAG, "processQueue: $reason")
                                    ApiResult.Error(reason)
                                }
                            } else {
                                ApiResult.Error("Failed to deserialize task entity from payloadJson: ${item.payloadJson}")
                            }
                        }
                        
                        item.entityType == "STUDENT_PROGRESS" && item.operationType == SyncOperationType.UPDATE -> {
                            // Parse payload as map of {studentEmail, totalXp, badgeId, badgeName, spreadsheetId}
                            @Suppress("UNCHECKED_CAST")
                            val data = moshi.adapter(Map::class.java).fromJson(item.payloadJson) as? Map<String, Any>
                            
                            if (data != null) {
                                val studentEmail = data["studentEmail"]?.toString() ?: ""
                                val totalXp = (data["totalXp"] as? Number)?.toInt() ?: 0
                                val badgeId = data["badgeId"]?.toString()
                                val badgeName = data["badgeName"]?.toString()
                                val spreadsheetId = data["spreadsheetId"]?.toString() ?: ""
                                
                                sheetsWriter.writeStudentProgress(
                                    spreadsheetId = spreadsheetId,
                                    studentEmail = studentEmail,
                                    totalXp = totalXp,
                                    badgeId = badgeId,
                                    badgeName = badgeName
                                )
                            } else {
                                ApiResult.Error("Failed to deserialize student progress from payloadJson: ${item.payloadJson}")
                            }
                        }
                        
                        item.entityType == "PROJECT" && (item.operationType == SyncOperationType.UPDATE || item.operationType == SyncOperationType.APPEND) -> {
                            val projectEntity = moshi.adapter(ProjectEntity::class.java)
                                .fromJson(item.payloadJson)
                            
                            if (projectEntity != null) {
                                sheetsWriter.writeProject(projectEntity.spreadsheetId, projectEntity)
                            } else {
                                ApiResult.Error("Failed to deserialize project entity from payloadJson: ${item.payloadJson}")
                            }
                        }
                        
                        item.entityType == "TEAM" && (item.operationType == SyncOperationType.UPDATE || item.operationType == SyncOperationType.APPEND) -> {
                            val teamEntity = moshi.adapter(com.cutm.TeamPulse.data.local.entity.TeamEntity::class.java)
                                .fromJson(item.payloadJson)
                            
                            if (teamEntity != null) {
                                // Resolve spreadsheetId from projectId using suspend DAO call (safe on IO dispatcher)
                                val spreadsheetId = projectDao.getById(teamEntity.projectId)?.spreadsheetId
                                if (spreadsheetId != null) {
                                    sheetsWriter.writeTeam(spreadsheetId, teamEntity)
                                } else {
                                    ApiResult.Error("Could not resolve spreadsheetId for project ${teamEntity.projectId}")
                                }
                            } else {
                                ApiResult.Error("Failed to deserialize team entity from payloadJson: ${item.payloadJson}")
                            }
                        }
                        
                        item.entityType == "STUDENT" && (item.operationType == SyncOperationType.APPEND || item.operationType == SyncOperationType.DELETE) -> {
                            /**
                             * STUDENT SYNC ROUTING (Phase 3 critical fix):
                             * - Source: addMemberToTeam() or removeMemberFromTeam()
                             * - Enqueues SyncQueueEntity with entityType="STUDENT", operationType=APPEND/DELETE
                             * - processQueue() routes here via this case
                             * 
                             * OPERATIONTYPE HANDLING:
                             * - APPEND: payloadJson contains full StudentEntity (with studentEmail, displayName, teamId, projectId, joinedAt)
                             *   Deserialize and call sheetsWriter.writeStudent() to append to Students tab
                             * - DELETE: payloadJson is empty string (doesn't need payload, entityId already holds studentEmail)
                             *   Don't deserialize; extract projectId from entityId context, call sheetsWriter.writeStudent()
                             *   or a simpler skip-based approach
                             * 
                             * CRITICAL: Previously APPEND worked but DELETE fell through to "else" case (moshi.fromJson("") returns null).
                             * Now we handle both operations correctly without silent failures.
                             */
                            
                            when (item.operationType) {
                                SyncOperationType.APPEND -> {
                                    // APPEND: Deserialize full StudentEntity from payloadJson
                                    val studentEntity = moshi.adapter(StudentEntity::class.java)
                                        .fromJson(item.payloadJson)
                                    
                                    if (studentEntity != null) {
                                        // Resolve spreadsheetId from projectId using suspend DAO call (safe on IO dispatcher)
                                        val spreadsheetId = projectDao.getById(studentEntity.projectId)?.spreadsheetId
                                        if (spreadsheetId != null) {
                                            sheetsWriter.writeStudent(spreadsheetId, studentEntity, item.operationType)
                                        } else {
                                            val reason = "Could not resolve spreadsheetId for project ${studentEntity.projectId}"
                                            Log.e(TAG, "processQueue: $reason")
                                            ApiResult.Error(reason)
                                        }
                                    } else {
                                        ApiResult.Error("Failed to deserialize student entity from payloadJson: ${item.payloadJson}")
                                    }
                                }
                                
                                SyncOperationType.DELETE -> {
                                    // DELETE: entityId holds studentEmail, payloadJson is empty (no deserialization needed)
                                    // Look up student to get projectId, then resolve spreadsheetId
                                    val studentEmail = item.entityId
                                    val student = studentDao.getByEmailSync(studentEmail)
                                    
                                    if (student != null) {
                                        val spreadsheetId = projectDao.getById(student.projectId)?.spreadsheetId
                                        if (spreadsheetId != null) {
                                            // Call writeStudent with the DELETE operation
                                            // Note: writeStudent's DELETE branch just logs and returns Success (non-destructive)
                                            sheetsWriter.writeStudent(spreadsheetId, student, item.operationType)
                                        } else {
                                            val reason = "Could not resolve spreadsheetId for project ${student.projectId}"
                                            Log.e(TAG, "processQueue: $reason")
                                            ApiResult.Error(reason)
                                        }
                                    } else {
                                        // Student already deleted from Room (expected for DELETE operations)
                                        // Log as info, not error — this is normal cleanup
                                        Log.d(TAG, "processQueue: Student $studentEmail not found in Room (expected for DELETE), skipping Sheets write")
                                        ApiResult.Success(Unit)
                                    }
                                }
                                
                                else -> {
                                    ApiResult.Error("Unexpected operation type for STUDENT: ${item.operationType}")
                                }
                            }
                        }
                        
                        else -> {
                            ApiResult.Error("Unknown entity type or operation: ${item.entityType} / ${item.operationType}")
                        }
                    }

                    // 3a. On success: delete item from queue
                    if (writeResult is ApiResult.Success) {
                        syncQueueDao.delete(item.queueId)
                        Log.d(TAG, "processQueue: Item ${item.queueId} synced successfully")
                        successCount++
                    } else if (writeResult is ApiResult.Error) {
                        // 3b. On failure: increment retryCount
                        val newRetryCount = item.retryCount + 1
                        val failureMessage = writeResult.message

                        if (newRetryCount >= 10) {
                            // Mark as FAILED_PERMANENTLY
                            syncQueueDao.updateStatusAndReason(
                                queueId = item.queueId,
                                status = SyncQueueStatus.FAILED_PERMANENTLY,
                                failureReason = failureMessage
                            )
                            Log.e(TAG, "processQueue: Item ${item.queueId} failed permanently after $newRetryCount retries: $failureMessage")
                            failureCount++
                        } else {
                            // Keep as PENDING for next retry
                            syncQueueDao.updateRetryCount(item.queueId, newRetryCount)
                            Log.w(TAG, "processQueue: Item ${item.queueId} failed (attempt $newRetryCount/10): $failureMessage")
                            failureCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "processQueue: Unexpected error processing item ${item.queueId}", e)
                    failureCount++
                    // Continue to next item (don't abort batch)
                }
            }

            Log.d(TAG, "processQueue: Batch complete - $successCount succeeded, $failureCount failed")
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "processQueue: Fatal error", e)
            ApiResult.Error("Queue processing failed: ${e.message}", e)
        }
    }

    override suspend fun pullFromSheets(teacherEmail: String): ApiResult<Unit> {
        return try {
            Log.d(TAG, "pullFromSheets: Starting for teacher $teacherEmail")

            // 1. Pull projects (will filter by teacher_email below)
            val projectsResult = sheetsProbeReader.readProjects()
            if (projectsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Projects tab: ${projectsResult.message}")
                return projectsResult
            }
            val allProjectRows = (projectsResult as ApiResult.Success).data
            Log.d(TAG, "pullFromSheets: Read ${allProjectRows.size} total projects from sheet")

            // 2. Pull teams
            val teamsResult = sheetsProbeReader.readTeams()
            if (teamsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Teams tab: ${teamsResult.message}")
                return teamsResult
            }
            val allTeamRows = (teamsResult as ApiResult.Success).data
            Log.d(TAG, "pullFromSheets: Read ${allTeamRows.size} total teams from sheet")

            // 3. Pull students
            val studentsResult = sheetsProbeReader.readStudents()
            if (studentsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Students tab: ${studentsResult.message}")
                return studentsResult
            }
            val allStudentRows = (studentsResult as ApiResult.Success).data
            Log.d(TAG, "pullFromSheets: Read ${allStudentRows.size} total students from sheet")

            // DEBUG: Log all unique teacher emails in sheet
            val uniqueEmails = allProjectRows.map { it.teacherEmail }.distinct()
            Log.d(TAG, "pullFromSheets: Unique teacher emails in sheet: $uniqueEmails")

            // FILTER BY TEACHER_EMAIL: Only sync data belonging to this teacher
            Log.d(TAG, "pullFromSheets: Filtering projects - SESSION_EMAIL=[${teacherEmail}] (length=${teacherEmail.length})")
            
            val projectRows = allProjectRows.filter { row ->
                val sheetEmail = row.teacherEmail
                val matches = sheetEmail.equals(teacherEmail, ignoreCase = true)
                Log.d(TAG, "pullFromSheets: Comparing SHEET_EMAIL=[${sheetEmail}] (len=${sheetEmail.length}) == SESSION_EMAIL=[${teacherEmail}] (len=${teacherEmail.length}) -> $matches")
                matches
            }
            
            Log.d(TAG, "pullFromSheets: After filter - ${projectRows.size} projects match teacher")
            
            val projectIds = projectRows.map { it.projectId }.toSet()
            val teamRows = allTeamRows.filter { it.projectId in projectIds }
            val studentRows = allStudentRows.filter { it.projectId in projectIds }

            // 4. Map DTOs → Entities
            val now = System.currentTimeMillis()
            val projectEntities = projectRows.map { row ->
                ProjectEntity(
                    projectId = row.projectId,
                    name = row.name,
                    teacherEmail = row.teacherEmail,
                    spreadsheetId = row.spreadsheetId,
                    driveFolderId = row.driveFolderId,
                    startDate = row.startDate,
                    dueDate = row.dueDate,
                    status = parseProjectStatus(row.status),
                    githubRepo = row.githubRepo,
                    localDirty = false, // Just pulled from Sheets, clean
                    lastModifiedLocal = now,
                    lastSyncedAt = now,
                )
            }

            val teamEntities = teamRows.map { row ->
                TeamEntity(
                    teamId = row.teamId,
                    projectId = row.projectId,
                    teamName = row.teamName,
                    memberEmails = row.memberEmails,
                    createdAt = row.createdAt,
                    localDirty = false,
                    lastModifiedLocal = now,
                )
            }

            val studentEntities = studentRows
                .filter { row ->
                    // Skip soft-deleted students (removedAt is not null)
                    if (row.removedAt != null) {
                        Log.d(TAG, "pullFromSheets: Skipping soft-deleted student ${row.studentEmail} (removedAt=${row.removedAt})")
                        false
                    } else {
                        true
                    }
                }
                .map { row ->
                    StudentEntity(
                        studentEmail = row.studentEmail,
                        displayName = row.displayName,
                        teamId = row.teamId,
                        projectId = row.projectId,
                        joinedAt = row.joinedAt,
                        localDirty = false,
                    )
                }

            // 5. Upsert to Room (replace strategy), with protection for locally-modified items
            projectEntities.forEach { projectDao.upsert(it) }
            
            // For teams: only upsert if not locally dirty (has pending sync)
            teamEntities.forEach { sheetTeam ->
                val existingTeam = teamDao.getById(sheetTeam.teamId)
                if (existingTeam == null || !existingTeam.localDirty) {
                    // Safe to overwrite: either new or no pending local changes
                    teamDao.upsert(sheetTeam)
                } else {
                    // Skip: local changes pending sync, don't revert them
                    Log.d(TAG, "pullFromSheets: Skipping team ${sheetTeam.teamId} - has pending local changes (localDirty=true)")
                }
            }
            
            // For students: only upsert if not locally dirty (has pending sync)
            studentEntities.forEach { sheetStudent ->
                val existingStudent = studentDao.getByEmailSync(sheetStudent.studentEmail)
                if (existingStudent == null || !existingStudent.localDirty) {
                    // Safe to overwrite: either new or no pending local changes
                    studentDao.upsert(sheetStudent)
                } else {
                    // Skip: local changes pending sync, don't revert them
                    Log.d(TAG, "pullFromSheets: Skipping student ${sheetStudent.studentEmail} - has pending local changes (localDirty=true)")
                }
            }

            // 6. Phase 5: Read and sync TaskAssignments tab
            val tasksResult = sheetsProbeReader.readTasks()
            if (tasksResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read TaskAssignments tab: ${tasksResult.message}")
                return tasksResult
            }
            val allTaskRows = (tasksResult as ApiResult.Success).data
            Log.d(TAG, "pullFromSheets: Read ${allTaskRows.size} total tasks from sheet")

            // Filter tasks by project scope (only process tasks for teacher's projects)
            val taskRows = allTaskRows.filter { it.projectId in projectIds }
            Log.d(TAG, "pullFromSheets: After filter - ${taskRows.size} tasks match teacher's projects")

            // Process each task row with approved routing rule
            val taskRowIndexMapBySpreadsheet = mutableMapOf<String, MutableMap<String, Int>>() // spreadsheetId → (taskId → rowIndex)
            
            taskRows.forEachIndexed { index, row ->
                try {
                    val rowIndex = index + 2  // +1 for header, +1 for 1-indexed Sheets
                    
                    // Parse status to TaskStatus enum
                    val taskStatus = when (row.status.uppercase()) {
                        "TODO" -> com.cutm.TeamPulse.domain.model.TaskStatus.TODO
                        "IN_PROGRESS" -> com.cutm.TeamPulse.domain.model.TaskStatus.IN_PROGRESS
                        "DONE" -> com.cutm.TeamPulse.domain.model.TaskStatus.DONE
                        else -> com.cutm.TeamPulse.domain.model.TaskStatus.TODO
                    }

                    // TASK ROUTING RULE (approved fix):
                    // Check if task already exists in Room
                    val existingTask = taskAssignmentDao.getById(row.taskId)

                    // BUG 1 FIX: Preserve hasEverBeenCompleted from Room for existing tasks
                    val hasEverBeenCompleted = existingTask?.hasEverBeenCompleted ?: false

                    // Create entity from row
                    val taskEntity = TaskAssignmentEntity(
                        taskId = row.taskId,
                        teamId = row.teamId,
                        projectId = row.projectId,
                        assigneeEmail = row.assigneeEmail,
                        title = row.title,
                        description = row.description,
                        weight = row.weight,
                        dueDate = row.dueDate,
                        status = taskStatus,
                        hasEverBeenCompleted = hasEverBeenCompleted,  // BUG 1 FIX: Preserved from Room
                        localDirty = false,
                        lastModifiedLocal = now,
                        remoteRowIndex = rowIndex
                    )

                    if (existingTask == null) {
                        // NEW TASK: Direct DAO upsert is safe
                        // Status will be whatever Sheets has, no XP risk since hasEverBeenCompleted=false
                        Log.d(TAG, "pullFromSheets: Task ${row.taskId} is NEW - direct DAO upsert")
                        taskAssignmentDao.upsert(taskEntity)
                    } else if (existingTask.status != taskStatus) {
                        // EXISTING TASK WITH STATUS CHANGE: Route through taskRepository.get().updateTask()
                        // This ensures applyTaskUpdate() runs and the hasEverBeenCompleted guard validates
                        Log.d(TAG, "pullFromSheets: Task ${row.taskId} status changed (${existingTask.status} → $taskStatus) - routing through updateTask()")
                        
                        // Build domain model for updateTask()
                        val taskDomain = taskEntity.toDomain()
                        taskRepository.get().updateTask(taskDomain)
                    } else {
                        // EXISTING TASK, STATUS UNCHANGED: Direct DAO upsert is safe
                        Log.d(TAG, "pullFromSheets: Task ${row.taskId} status unchanged - direct DAO upsert")
                        taskAssignmentDao.upsert(taskEntity)
                    }

                    // BUG 2 FIX: Group row index cache by spreadsheetId for each task's project
                    val spreadsheetId = projectEntities.find { it.projectId == row.projectId }?.spreadsheetId
                    if (spreadsheetId != null) {
                        taskRowIndexMapBySpreadsheet.getOrPut(spreadsheetId) { mutableMapOf() }[row.taskId] = rowIndex
                    } else {
                        Log.w(TAG, "pullFromSheets: Could not find spreadsheetId for task ${row.taskId} (projectId=${row.projectId})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "pullFromSheets: Error processing task row ${row.taskId}", e)
                    // Continue to next task (don't abort batch)
                }
            }

            // 7. Update row index cache in sync_metadata for TaskAssignments tab (per spreadsheet)
            taskRowIndexMapBySpreadsheet.forEach { (spreadsheetId, taskIndexMap) ->
                try {
                    val cacheKey = "tasks:$spreadsheetId"
                    val cacheJson = moshi.adapter(Map::class.java).toJson(taskIndexMap)
                    
                    val cacheEntity = com.cutm.TeamPulse.data.local.entity.SyncMetadataEntity(
                        key = cacheKey,
                        lastPullAt = now,
                        lastPushAt = 0,
                        etagOrRevision = null,
                        rowIndexCache = cacheJson
                    )
                    syncMetadataDao.upsert(cacheEntity)
                    Log.d(TAG, "pullFromSheets: Updated row index cache for TaskAssignments - $cacheKey with ${taskIndexMap.size} entries")
                } catch (e: Exception) {
                    Log.e(TAG, "pullFromSheets: Error updating task row index cache for $spreadsheetId", e)
                    // Non-fatal, continue
                }
            }

            Log.d(TAG, "pullFromSheets: Success for $teacherEmail - synced ${projectEntities.size} projects, ${teamEntities.size} teams, ${studentEntities.size} students, ${taskRows.size} tasks")
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "pullFromSheets: Unexpected error", e)
            ApiResult.Error("Pull from Sheets failed: ${e.message}", e)
        }
    }

    private fun parseProjectStatus(status: String): ProjectStatus {
        return when (status.uppercase()) {
            "ACTIVE" -> ProjectStatus.ACTIVE
            "ARCHIVED" -> ProjectStatus.ARCHIVED
            "COMPLETED" -> ProjectStatus.ARCHIVED // Map COMPLETED → ARCHIVED
            else -> ProjectStatus.ACTIVE // Default
        }
    }

    companion object {
        private const val TAG = "SyncRepository"
    }
}
