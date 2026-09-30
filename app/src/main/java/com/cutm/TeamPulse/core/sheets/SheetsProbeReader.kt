package com.cutm.TeamPulse.core.sheets

import com.cutm.TeamPulse.core.network.ApiResult

/**
 * Reads only a row COUNT from the Users Registry sheet, as a proof-of-access
 * step after Sheets read authorization. Intentionally separate from
 * UserRegistryRepository (which stays a stub this checkpoint) and never
 * exposes row contents to callers — keeping raw sheet data out of the
 * ViewModel/UI entirely.
 */
interface SheetsProbeReader {

    suspend fun readUsersRegistryRowCount(): ApiResult<Int>

    /**
     * Phase 1: Read-only sync from Projects tab.
     * Uses the shared data spreadsheet (SheetsConfig.SHARED_DATA_SPREADSHEET_ID).
     * 
     * @return List of parsed project rows (skipping header row)
     */
    suspend fun readProjects(): ApiResult<List<ProjectRow>>

    /**
     * Phase 1: Read-only sync from Teams tab.
     * Uses the shared data spreadsheet (SheetsConfig.SHARED_DATA_SPREADSHEET_ID).
     * 
     * @return List of parsed team rows (skipping header row)
     */
    suspend fun readTeams(): ApiResult<List<TeamRow>>

    /**
     * Phase 1: Read-only sync from Students tab.
     * Uses the shared data spreadsheet (SheetsConfig.SHARED_DATA_SPREADSHEET_ID).
     * 
     * @return List of parsed student rows (skipping header row)
     */
    suspend fun readStudents(): ApiResult<List<StudentRow>>

    /**
     * Phase 5: Read-only sync from TaskAssignments tab.
     * Uses the shared data spreadsheet (SheetsConfig.SHARED_DATA_SPREADSHEET_ID).
     * 
     * @return List of parsed task rows (skipping header row)
     */
    suspend fun readTasks(): ApiResult<List<TaskRow>>
}

/**
 * Parsed row from Projects tab (Sheets → DTO).
 * Maps to ProjectEntity via mapper in sync repository.
 */
data class ProjectRow(
    val projectId: String,
    val name: String,
    val teacherEmail: String,
    val spreadsheetId: String, // Parent sheet ID (not in Sheets row, passed from caller)
    val driveFolderId: String,
    val startDate: Long, // Epoch millis
    val dueDate: Long,   // Epoch millis
    val status: String,  // "ACTIVE" | "COMPLETED" | "ARCHIVED"
    val githubRepo: String?,
)

/**
 * Parsed row from Teams tab (Sheets → DTO).
 */
data class TeamRow(
    val teamId: String,
    val projectId: String,
    val teamName: String,
    val memberEmails: List<String>, // Comma-separated in sheet, parsed to list
    val createdAt: Long, // Epoch millis
)

/**
 * Parsed row from Students tab (Sheets → DTO).
 * 
 * Students tab schema (A–G):
 * A: student_email, B: display_name, C: team_id, D: project_id, E: joined_at, F: role, G: removed_at
 * 
 * removed_at (column G): Soft-delete marker. Null/blank if student is active, epoch millis if removed.
 * When a student is deleted, removedAt gets a timestamp instead of the row being physically deleted.
 * This preserves the audit trail while allowing pullFromSheets to skip re-adding removed students.
 */
data class StudentRow(
    val studentEmail: String,
    val displayName: String,
    val teamId: String,
    val projectId: String,
    val joinedAt: Long, // Epoch millis
    val role: String,   // "MEMBER" typically, future-proofing for LEAD etc.
    val removedAt: Long?, // Epoch millis if soft-deleted, null if active
)

/**
 * Parsed row from TaskAssignments tab (Sheets → DTO).
 */
data class TaskRow(
    val taskId: String,
    val teamId: String,
    val projectId: String,
    val assigneeEmail: String,
    val title: String,
    val description: String,
    val weight: Float,
    val dueDate: Long,      // Epoch millis
    val status: String,     // "TODO" | "IN_PROGRESS" | "DONE"
)

