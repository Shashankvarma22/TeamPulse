package com.cutm.TeamPulse.data.repository

import android.util.Log
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.core.sheets.SheetsProbeReader
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import com.cutm.TeamPulse.domain.model.SyncStatus
import com.cutm.TeamPulse.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foundation sync repository. Queue processing is a no-op until Sheets API integration.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val sheetsProbeReader: SheetsProbeReader,
    private val projectDao: ProjectDao,
    private val teamDao: TeamDao,
    private val studentDao: StudentDao,
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

    override suspend fun processQueue(): ApiResult<Unit> {
        // Foundation skeleton: no Sheets API calls yet.
        return ApiResult.Success(Unit)
    }

    override suspend fun pullFromSheets(spreadsheetId: String): ApiResult<Unit> {
        return try {
            Log.d(TAG, "pullFromSheets: Starting for spreadsheet $spreadsheetId")

            // 1. Pull projects
            val projectsResult = sheetsProbeReader.readProjects(spreadsheetId)
            if (projectsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Projects tab: ${projectsResult.message}")
                return projectsResult
            }
            val projectRows = (projectsResult as ApiResult.Success).data

            // 2. Pull teams
            val teamsResult = sheetsProbeReader.readTeams(spreadsheetId)
            if (teamsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Teams tab: ${teamsResult.message}")
                return teamsResult
            }
            val teamRows = (teamsResult as ApiResult.Success).data

            // 3. Pull students
            val studentsResult = sheetsProbeReader.readStudents(spreadsheetId)
            if (studentsResult is ApiResult.Error) {
                Log.e(TAG, "pullFromSheets: Failed to read Students tab: ${studentsResult.message}")
                return studentsResult
            }
            val studentRows = (studentsResult as ApiResult.Success).data

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

            val studentEntities = studentRows.map { row ->
                StudentEntity(
                    studentEmail = row.studentEmail,
                    displayName = row.displayName,
                    teamId = row.teamId,
                    projectId = row.projectId,
                    joinedAt = row.joinedAt,
                    localDirty = false,
                )
            }

            // 5. Upsert to Room (replace strategy)
            projectEntities.forEach { projectDao.upsert(it) }
            teamEntities.forEach { teamDao.upsert(it) }
            studentEntities.forEach { studentDao.upsert(it) }

            Log.d(TAG, "pullFromSheets: Success - synced ${projectEntities.size} projects, ${teamEntities.size} teams, ${studentEntities.size} students")
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
