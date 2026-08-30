package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.Team
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>

    fun observeProject(projectId: String): Flow<Project?>

    suspend fun createProject(
        projectId: String,
        name: String,
        teacherEmail: String,
        dueDate: Long,
        spreadsheetId: String,
        driveFolderId: String
    ): ApiResult<Unit>

    /**
     * Observe all teams for a specific project
     */
    fun observeTeamsForProject(projectId: String): Flow<List<Team>>

    /**
     * Create a new team within a project
     *
     * @param teamId Unique team identifier (UUID)
     * @param projectId Parent project ID
     * @param teamName Team name (1-100 chars)
     * @return ApiResult.Success on success, ApiResult.Error on failure
     */
    suspend fun createTeam(
        teamId: String,
        projectId: String,
        teamName: String
    ): ApiResult<Unit>
}
