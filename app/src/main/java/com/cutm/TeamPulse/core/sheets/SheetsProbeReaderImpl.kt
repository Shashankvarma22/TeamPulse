package com.cutm.TeamPulse.core.sheets

import com.cutm.TeamPulse.core.config.SheetsConfig
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.remote.SheetsApiService
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class SheetsProbeReaderImpl @Inject constructor(
    private val sheetsApiService: SheetsApiService,
) : SheetsProbeReader {

    override suspend fun readUsersRegistryRowCount(): ApiResult<Int> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.USERS_REGISTRY_SPREADSHEET_ID,
                range = SheetsConfig.USERS_REGISTRY_RANGE,
            )
            ApiResult.Success(response.values?.size ?: 0)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read Users Registry (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read Users Registry.",
                cause = e,
            )
        }
    }

    override suspend fun readProjects(spreadsheetId: String): ApiResult<List<ProjectRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = spreadsheetId,
                range = "Projects!A:I", // 9 columns: project_id through status
            )

            val rows = response.values?.drop(1) // Skip header row
                ?.mapNotNull { row -> parseProjectRow(row, spreadsheetId) }
                ?: emptyList()

            ApiResult.Success(rows)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read Projects tab (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read Projects tab.",
                cause = e,
            )
        }
    }

    override suspend fun readTeams(spreadsheetId: String): ApiResult<List<TeamRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = spreadsheetId,
                range = "Teams!A:E", // 5 columns: team_id through created_at
            )

            val rows = response.values?.drop(1) // Skip header row
                ?.mapNotNull { row -> parseTeamRow(row) }
                ?: emptyList()

            ApiResult.Success(rows)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read Teams tab (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read Teams tab.",
                cause = e,
            )
        }
    }

    override suspend fun readStudents(spreadsheetId: String): ApiResult<List<StudentRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = spreadsheetId,
                range = "Students!A:F", // 6 columns: student_email through role
            )

            val rows = response.values?.drop(1) // Skip header row
                ?.mapNotNull { row -> parseStudentRow(row) }
                ?: emptyList()

            ApiResult.Success(rows)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read Students tab (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read Students tab.",
                cause = e,
            )
        }
    }

    // --- Private parsing functions ---

    /**
     * Parse Projects row: project_id, name, teacher_email, start_date, due_date,
     * drive_folder_id, github_repo, status
     * 
     * Columns 0-7 (8 total), plus spreadsheetId passed from caller
     */
    private fun parseProjectRow(row: List<String>, spreadsheetId: String): ProjectRow? {
        if (row.size < 7) return null // Minimum required columns (without optional github_repo)

        return try {
            ProjectRow(
                projectId = row.getOrNull(0)?.trim() ?: return null,
                name = row.getOrNull(1)?.trim() ?: return null,
                teacherEmail = row.getOrNull(2)?.trim() ?: return null,
                spreadsheetId = spreadsheetId,
                driveFolderId = row.getOrNull(5)?.trim() ?: return null,
                startDate = row.getOrNull(3)?.toLongOrNull() ?: return null,
                dueDate = row.getOrNull(4)?.toLongOrNull() ?: return null,
                status = row.getOrNull(7)?.trim() ?: "ACTIVE",
                githubRepo = row.getOrNull(6)?.trim()?.takeIf { it.isNotEmpty() },
            )
        } catch (e: Exception) {
            null // Skip malformed rows
        }
    }

    /**
     * Parse Teams row: team_id, project_id, team_name, member_emails[], created_at
     * 
     * member_emails stored as comma-separated string in sheet
     */
    private fun parseTeamRow(row: List<String>): TeamRow? {
        if (row.size < 5) return null

        return try {
            TeamRow(
                teamId = row.getOrNull(0)?.trim() ?: return null,
                projectId = row.getOrNull(1)?.trim() ?: return null,
                teamName = row.getOrNull(2)?.trim() ?: return null,
                memberEmails = row.getOrNull(3)?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                createdAt = row.getOrNull(4)?.toLongOrNull() ?: return null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse Students row: student_email, display_name, team_id, project_id, joined_at, role
     */
    private fun parseStudentRow(row: List<String>): StudentRow? {
        if (row.size < 6) return null

        return try {
            StudentRow(
                studentEmail = row.getOrNull(0)?.trim() ?: return null,
                displayName = row.getOrNull(1)?.trim() ?: return null,
                teamId = row.getOrNull(2)?.trim() ?: return null,
                projectId = row.getOrNull(3)?.trim() ?: return null,
                joinedAt = row.getOrNull(4)?.toLongOrNull() ?: return null,
                role = row.getOrNull(5)?.trim() ?: "MEMBER",
            )
        } catch (e: Exception) {
            null
        }
    }
}
