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

    override suspend fun readProjects(): ApiResult<List<ProjectRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.SHARED_DATA_SPREADSHEET_ID,
                range = "Projects!A:I", // 9 columns: project_id through status
            )

            android.util.Log.d("SyncDebug", "readProjects: API returned ${response.values?.size ?: 0} total rows")
            if (response.values != null) {
                response.values.forEachIndexed { index, row ->
                    android.util.Log.d("SyncDebug", "RAW_PROJECT_ROW[$index]=$row (size=${(row as? List<*>)?.size ?: 0})")
                }
            }

            val rows = response.values?.drop(1) // Skip header row
                ?.mapNotNull { row -> parseProjectRow(row, SheetsConfig.SHARED_DATA_SPREADSHEET_ID) }
                ?: emptyList()

            android.util.Log.d("SyncDebug", "readProjects: Parsed ${rows.size} successful ProjectRow objects")
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

    override suspend fun readTeams(): ApiResult<List<TeamRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.SHARED_DATA_SPREADSHEET_ID,
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

    override suspend fun readStudents(): ApiResult<List<StudentRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.SHARED_DATA_SPREADSHEET_ID,
                range = "Students!A:G", // 7 columns: student_email through removed_at
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

    override suspend fun readTasks(): ApiResult<List<TaskRow>> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.SHARED_DATA_SPREADSHEET_ID,
                range = "TaskAssignments!A:J", // 10 columns: task_id through status
            )

            val rows = response.values?.drop(1) // Skip header row
                ?.mapNotNull { row -> parseTaskRow(row) }
                ?: emptyList()

            ApiResult.Success(rows)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read TaskAssignments tab (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read TaskAssignments tab.",
                cause = e,
            )
        }
    }

    // --- Private parsing functions ---

    /**
     * Parse Projects row: project_id, name, teacher_email, spreadsheet_id, start_date, due_date,
     * drive_folder_id, github_repo, status
     * 
     * Columns 0-8 (9 total) - CORRECTED mapping per actual sheet structure
     */
    private fun parseProjectRow(row: List<String>, spreadsheetId: String): ProjectRow? {
        if (row.size < 9) {
            android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - row too short (${row.size} < 9). Row=$row")
            return null
        }

        return try {
            val projectId = row.getOrNull(0)?.trim()
            if (projectId == null || projectId.isEmpty()) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - projectId null/empty at [0]")
                return null
            }

            val name = row.getOrNull(1)?.trim()
            if (name == null || name.isEmpty()) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - name null/empty at [1]")
                return null
            }

            val teacherEmail = row.getOrNull(2)?.trim()
            if (teacherEmail == null || teacherEmail.isEmpty()) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - teacherEmail null/empty at [2]")
                return null
            }

            // FIXED: spreadsheet_id now at [3] (was being skipped entirely before)
            val sheetId = row.getOrNull(3)?.trim() ?: spreadsheetId
            
            val startDateStr = row.getOrNull(4)?.trim()  // FIXED: moved from [3]
            val startDate = startDateStr?.toLongOrNull()
            if (startDate == null) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - startDate failed toLongOrNull. Value at [4]='$startDateStr'")
                return null
            }

            val dueDateStr = row.getOrNull(5)?.trim()  // FIXED: moved from [4]
            val dueDate = dueDateStr?.toLongOrNull()
            if (dueDate == null) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - dueDate failed toLongOrNull. Value at [5]='$dueDateStr'")
                return null
            }

            val driveFolderId = row.getOrNull(6)?.trim()  // FIXED: moved from [5]
            if (driveFolderId == null || driveFolderId.isEmpty()) {
                android.util.Log.d("SyncDebug", "parseProjectRow: SKIPPED - driveFolderId null/empty at [6]")
                return null
            }

            val githubRepo = row.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() }  // FIXED: moved from [6]
            val status = row.getOrNull(8)?.trim() ?: "ACTIVE"  // FIXED: moved from [7]

            android.util.Log.d("SyncDebug", "parseProjectRow: SUCCESS - projectId=$projectId, name=$name, teacherEmail=$teacherEmail, sheetId=$sheetId, startDate=$startDate, dueDate=$dueDate")

            ProjectRow(
                projectId = projectId,
                name = name,
                teacherEmail = teacherEmail,
                spreadsheetId = sheetId,  // NOW uses spreadsheet_id from sheet instead of hardcoded
                driveFolderId = driveFolderId,
                startDate = startDate,
                dueDate = dueDate,
                status = status,
                githubRepo = githubRepo,
            )
        } catch (e: Exception) {
            android.util.Log.d("SyncDebug", "parseProjectRow: EXCEPTION - ${e.message}", e)
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
     * Parse Students row: student_email, display_name, team_id, project_id, joined_at, role, removed_at
     * 
     * Columns 0-6 (7 total):
     * A: student_email, B: display_name, C: team_id, D: project_id, E: joined_at, F: role, G: removed_at
     * 
     * removed_at (column G): Soft-delete marker. Blank/null if active, epoch millis if removed.
     * Parse as nullable Long — blank or missing → null, valid number → Long.
     */
    private fun parseStudentRow(row: List<String>): StudentRow? {
        if (row.size < 6) return null  // Require at least A-F; G is optional (may be missing/blank)

        return try {
            val removedAtStr = row.getOrNull(6)?.trim()?.takeIf { it.isNotEmpty() }
            val removedAt = removedAtStr?.toLongOrNull()

            StudentRow(
                studentEmail = row.getOrNull(0)?.trim() ?: return null,
                displayName = row.getOrNull(1)?.trim() ?: return null,
                teamId = row.getOrNull(2)?.trim() ?: return null,
                projectId = row.getOrNull(3)?.trim() ?: return null,
                joinedAt = row.getOrNull(4)?.toLongOrNull() ?: return null,
                role = row.getOrNull(5)?.trim() ?: "MEMBER",
                removedAt = removedAt,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse TaskAssignments row: task_id, team_id, project_id, assignee_email, title,
     * description, weight, due_date, status (9 columns read, 0-8)
     * 
     * hasEverBeenCompleted is managed by applyTaskUpdate() and must not be trusted from Sheets input.
     * It will be populated from Room when syncing existing tasks.
     */
    private fun parseTaskRow(row: List<String>): TaskRow? {
        if (row.size < 9) return null

        return try {
            TaskRow(
                taskId = row.getOrNull(0)?.trim() ?: return null,
                teamId = row.getOrNull(1)?.trim() ?: return null,
                projectId = row.getOrNull(2)?.trim() ?: return null,
                assigneeEmail = row.getOrNull(3)?.trim() ?: return null,
                title = row.getOrNull(4)?.trim() ?: return null,
                description = row.getOrNull(5)?.trim() ?: "",
                weight = row.getOrNull(6)?.toFloatOrNull() ?: 1.0f,
                dueDate = row.getOrNull(7)?.toLongOrNull() ?: return null,
                status = row.getOrNull(8)?.trim() ?: "TODO",
            )
        } catch (e: Exception) {
            null
        }
    }
}
