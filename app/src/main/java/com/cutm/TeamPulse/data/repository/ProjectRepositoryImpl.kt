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
            ApiResult.Success(Unit)
        } catch (e: Exception) {
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
            // Verify session
            val session = sessionDao.getActive()
                ?: return@withContext ApiResult.Error("Session expired")

            // Cascade delete in ATOMIC TRANSACTION
            database.withTransaction {
                // 1. Get all teams in project
                val teams = teamDao.getByProjectSync(projectId)

                // 2. Delete all tasks for each team
                teams.forEach { team ->
                    taskDao.deleteByTeam(team.teamId)
                }

                // 3. Delete all teams
                teamDao.deleteByProject(projectId)

                // 4. Delete project
                projectDao.deleteById(projectId)
            }
            // All deletes succeed atomically or none do - no partial state possible

            // TODO (future): Queue sync operations to delete from Google Sheets
            // syncQueueDao.enqueue(SyncOperation.DELETE_PROJECT, projectId)

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to delete project", e)
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
}

