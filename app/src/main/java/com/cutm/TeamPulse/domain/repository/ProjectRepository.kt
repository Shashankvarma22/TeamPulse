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

    /**
     * Delete a team. Tasks assigned to team members will become stale assignees.
     */
    suspend fun deleteTeam(teamId: String): ApiResult<Unit>

    /**
     * Delete a project and cascade to all teams and tasks.
     * @return ApiResult.Success if deleted, ApiResult.Error if session expired or operation failed
     */
    suspend fun deleteProject(projectId: String): ApiResult<Unit>

    /**
     * Get the count of teams in a project (for deletion confirmation)
     */
    suspend fun getTeamCount(projectId: String): Int

    /**
     * Get the count of tasks in a project (for deletion confirmation)
     */
    suspend fun getTaskCount(projectId: String): Int
}
