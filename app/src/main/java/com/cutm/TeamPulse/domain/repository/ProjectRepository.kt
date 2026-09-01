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

    /**
     * Add a member to a team.
     * Creates StudentEntity and updates TeamEntity.memberEmails atomically.
     *
     * @param teamId Team to add member to
     * @param projectId Project the team belongs to
     * @param studentEmail Student's email address
     * @param displayName Student's display name
     * @return ApiResult.Success on success, ApiResult.Error if duplicate or team not found
     */
    suspend fun addMemberToTeam(
        teamId: String,
        projectId: String,
        studentEmail: String,
        displayName: String
    ): ApiResult<Unit>

    /**
     * Remove a member from a team.
     * Deletes StudentEntity and updates TeamEntity.memberEmails atomically.
     * Tasks assigned to the member become stale assignees.
     *
     * @param teamId Team to remove member from
     * @param studentEmail Student's email address
     * @return ApiResult.Success on success, ApiResult.Error if team not found
     */
    suspend fun removeMemberFromTeam(
        teamId: String,
        studentEmail: String
    ): ApiResult<Unit>

    /**
     * Check if an email is already a member of a team.
     *
     * @param teamId Team to check
     * @param email Email to check
     * @return true if email is in team.memberEmails, false otherwise
     */
    suspend fun isEmailInTeam(
        teamId: String,
        email: String
    ): Boolean

    /**
     * ONE-TIME DATA REPAIR: Remove orphaned team/task from deleted "Blaa" project.
     * Call once, verify with logcat, then remove this function.
     */
    suspend fun cleanupOrphanedBlaaData(): ApiResult<Unit>
}
