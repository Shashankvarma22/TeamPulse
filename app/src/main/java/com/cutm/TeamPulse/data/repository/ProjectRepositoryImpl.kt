package com.cutm.TeamPulse.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.cutm.TeamPulse.core.dispatchers.DispatcherProvider
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.dao.UserSessionDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.SyncQueueEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.domain.model.SyncOperationType
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val teamDao: TeamDao,
    private val taskDao: TaskAssignmentDao,
    private val syncQueueDao: SyncQueueDao,
    private val sessionDao: UserSessionDao,
    private val studentDao: com.cutm.TeamPulse.data.local.dao.StudentDao,
    private val database: TeamPulseDatabase,
    private val dispatchers: DispatcherProvider,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> {
        return projectDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeProject(projectId: String): Flow<Project?> {
        return projectDao.observeById(projectId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun createProject(
        projectId: String,
        name: String,
        teacherEmail: String,
        dueDate: Long,
        spreadsheetId: String,
        driveFolderId: String
    ): ApiResult<Unit> {
        return try {
            android.util.Log.d("ProjectRepository", "=== CREATE PROJECT CALLED ===")
            android.util.Log.d("ProjectRepository", "Project ID: $projectId")
            android.util.Log.d("ProjectRepository", "Project Name: $name")
            android.util.Log.d("ProjectRepository", "Teacher: $teacherEmail")
            android.util.Log.d("ProjectRepository", "Due Date: $dueDate (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(dueDate))})")

            val currentTime = System.currentTimeMillis()
            val entity = ProjectEntity(
                projectId = projectId,
                name = name,
                teacherEmail = teacherEmail,
                spreadsheetId = spreadsheetId,
                driveFolderId = driveFolderId,
                startDate = currentTime,
                dueDate = dueDate,
                status = ProjectStatus.ACTIVE,
                githubRepo = null,
                localDirty = true, // New project not yet synced
                lastModifiedLocal = currentTime,
                lastSyncedAt = null
            )
            projectDao.upsert(entity)
            
            android.util.Log.d("ProjectRepository", "=== CREATE PROJECT SUCCEEDED ===")
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "=== CREATE PROJECT FAILED ===", e)
            ApiResult.Error(e.message ?: "Failed to create project")
        }
    }

    override fun observeTeamsForProject(projectId: String): Flow<List<Team>> {
        return teamDao.observeByProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createTeam(
        teamId: String,
        projectId: String,
        teamName: String
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        try {
            val now = System.currentTimeMillis()

            val teamEntity = TeamEntity(
                teamId = teamId,
                projectId = projectId,
                teamName = teamName,
                memberEmails = emptyList(), // Empty on creation
                createdAt = now,
                localDirty = true,
                lastModifiedLocal = now
            )

            teamDao.upsert(teamEntity)

            // Enqueue sync (stubbed - no actual Sheets call yet)
            syncQueueDao.insert(
                SyncQueueEntity(
                    queueId = 0, // Auto-generated
                    operationType = SyncOperationType.APPEND,
                    targetTab = "Teams",
                    entityType = "team",
                    entityId = teamId,
                    payloadJson = "", // Stubbed - will be populated by sync processor
                    retryCount = 0,
                    createdAt = now,
                    status = SyncQueueStatus.PENDING
                )
            )

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to create team", e)
            ApiResult.Error("Failed to create team: ${e.message}")
        }
    }

    override suspend fun deleteTeam(teamId: String): ApiResult<Unit> = withContext(dispatchers.io) {
        try {
            // Verify session
            val session = sessionDao.getActive()
                ?: return@withContext ApiResult.Error("Session expired")

            // Delete team locally (single operation, no transaction needed)
            teamDao.deleteById(teamId)

            // TODO (future): Queue sync operation to delete from Google Sheets
            // syncQueueDao.enqueue(SyncOperation.DELETE_TEAM, teamId)

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to delete team", e)
            ApiResult.Error("Failed to delete team")
        }
    }

    override suspend fun deleteProject(projectId: String): ApiResult<Unit> = withContext(dispatchers.io) {
        try {
            android.util.Log.d("ProjectRepository", "=== DELETE PROJECT CALLED ===")
            android.util.Log.d("ProjectRepository", "Project ID: $projectId")

            // Verify session
            val session = sessionDao.getActive()
            if (session == null) {
                android.util.Log.e("ProjectRepository", "Delete failed: Session expired")
                return@withContext ApiResult.Error("Session expired")
            }
            
            android.util.Log.d("ProjectRepository", "Session verified: ${session.email}")

            // Cascade delete in ATOMIC TRANSACTION
            database.withTransaction {
                android.util.Log.d("ProjectRepository", "Starting transaction...")

                // 1. Get all teams in project
                val teams = teamDao.getByProjectSync(projectId)
                android.util.Log.d("ProjectRepository", "Found ${teams.size} teams to delete")

                // 2. Delete all tasks for each team
                teams.forEach { team ->
                    android.util.Log.d("ProjectRepository", "  Deleting tasks for team: ${team.teamId}")
                    taskDao.deleteByTeam(team.teamId)
                }

                // 3. Delete all teams
                android.util.Log.d("ProjectRepository", "Deleting ${teams.size} teams...")
                teamDao.deleteByProject(projectId)

                // 4. Delete project
                android.util.Log.d("ProjectRepository", "Deleting project $projectId...")
                projectDao.deleteById(projectId)
                
                android.util.Log.d("ProjectRepository", "Transaction completed successfully")
            }
            // All deletes succeed atomically or none do - no partial state possible

            android.util.Log.d("ProjectRepository", "=== DELETE PROJECT SUCCEEDED ===")

            // TODO (future): Queue sync operations to delete from Google Sheets
            // syncQueueDao.enqueue(SyncOperation.DELETE_PROJECT, projectId)

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "=== DELETE PROJECT FAILED ===", e)
            ApiResult.Error("Failed to delete project")
        }
    }

    override suspend fun getTeamCount(projectId: String): Int = withContext(dispatchers.io) {
        return@withContext try {
            teamDao.getByProjectSync(projectId).size
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to get team count", e)
            0
        }
    }

    override suspend fun getTaskCount(projectId: String): Int = withContext(dispatchers.io) {
        return@withContext try {
            val teams = teamDao.getByProjectSync(projectId)
            var totalTasks = 0
            teams.forEach { team ->
                // Need to count tasks per team
                // Using a query would be more efficient, but this works with existing DAO
                val tasks = taskDao.getByTeamSync(team.teamId)
                totalTasks += tasks.size
            }
            totalTasks
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to get task count", e)
            0
        }
    }

    override suspend fun addMemberToTeam(
        teamId: String,
        projectId: String,
        studentEmail: String,
        displayName: String
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        try {
            val team = teamDao.getById(teamId)
                ?: return@withContext ApiResult.Error("Team not found")

            // Check duplicate in target team
            if (studentEmail in team.memberEmails) {
                return@withContext ApiResult.Error("Student already in this team")
            }

            // Check if email exists in ANY team in this project
            val existingStudent = studentDao.getByEmailSync(studentEmail)
            if (existingStudent != null && existingStudent.projectId == projectId) {
                return@withContext ApiResult.Error("Student already in another team")
            }

            val now = System.currentTimeMillis()

            // Create student entity
            val student = com.cutm.TeamPulse.data.local.entity.StudentEntity(
                studentEmail = studentEmail,
                displayName = displayName,
                teamId = teamId,
                projectId = projectId,
                joinedAt = now,
                localDirty = true
            )

            // Update team entity
            val updatedTeam = team.copy(
                memberEmails = team.memberEmails + studentEmail,
                localDirty = true,
                lastModifiedLocal = now
            )

            // ATOMIC TRANSACTION: Both operations must succeed or both fail
            database.withTransaction {
                studentDao.upsert(student)
                teamDao.upsert(updatedTeam)
            }

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to add member", e)
            ApiResult.Error("Failed to add member")
        }
    }

    override suspend fun removeMemberFromTeam(
        teamId: String,
        studentEmail: String
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        try {
            val team = teamDao.getById(teamId)
                ?: return@withContext ApiResult.Error("Team not found")

            val now = System.currentTimeMillis()

            // Update team entity (remove email from list)
            val updatedTeam = team.copy(
                memberEmails = team.memberEmails - studentEmail,
                localDirty = true,
                lastModifiedLocal = now
            )

            // ATOMIC TRANSACTION: Both operations must succeed or both fail
            database.withTransaction {
                studentDao.deleteByEmail(studentEmail)
                teamDao.upsert(updatedTeam)
            }

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to remove member", e)
            ApiResult.Error("Failed to remove member")
        }
    }

    override suspend fun isEmailInTeam(teamId: String, email: String): Boolean = withContext(dispatchers.io) {
        val team = teamDao.getById(teamId) ?: return@withContext false
        return@withContext email in team.memberEmails
    }
}
