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
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @return List of parsed project rows (skipping header row)
     */
    suspend fun readProjects(spreadsheetId: String): ApiResult<List<ProjectRow>>

    /**
     * Phase 1: Read-only sync from Teams tab.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @return List of parsed team rows (skipping header row)
     */
    suspend fun readTeams(spreadsheetId: String): ApiResult<List<TeamRow>>

    /**
     * Phase 1: Read-only sync from Students tab.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @return List of parsed student rows (skipping header row)
     */
    suspend fun readStudents(spreadsheetId: String): ApiResult<List<StudentRow>>
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
 */
data class StudentRow(
    val studentEmail: String,
    val displayName: String,
    val teamId: String,
    val projectId: String,
    val joinedAt: Long, // Epoch millis
    val role: String,   // "MEMBER" typically, future-proofing for LEAD etc.
)

