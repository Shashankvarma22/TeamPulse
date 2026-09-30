package com.cutm.TeamPulse.data.remote

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.StudentProgressEntity
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity

/**
 * Interface for writing data to Google Sheets tabs.
 * 
 * CRITICAL: All writes flow through processQueue() in SyncRepository.
 * This ensures writes are persisted to Room first, queued, then synced to Sheets.
 * 
 * Implements read-cache-write pattern via sync_metadata.rowIndexCache to optimize
 * repeated writes to the same entities (avoids full-sheet scans).
 */
interface SheetsWriter {
    
    /**
     * Write a task to the TaskAssignments tab.
     * Uses cached row index from sync_metadata if available, else scans and caches.
     * 
     * CRITICAL CALL CHAIN (from design doc §8.3):
     * applyTaskUpdate() → enqueue to sync_queue → processQueue() → SheetsWriter.writeTask()
     * 
     * The hasEverBeenCompleted guard has already run before this method is called.
     * This method ONLY persists the entity state to Sheets, no re-validation needed.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @param task Task entity to write
     * @return ApiResult.Success if write confirmed, ApiResult.Error otherwise
     */
    suspend fun writeTask(
        spreadsheetId: String,
        task: TaskAssignmentEntity
    ): ApiResult<Unit>

    /**
     * Write student progress (XP/badges) to Achievements tab.
     * Upserts by student_email + badge_id (or student_email only for XP-only updates).
     * 
     * Per Decision 4 (design doc §12), uses badgeId="XP_TOTAL" as pseudo-badge for XP-only updates.
     * One row per student_email + badge_id maximum — upsert, never append.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @param studentEmail Student identifier
     * @param totalXp Current total XP (running aggregate)
     * @param badgeId Badge type (e.g., "TASK_MASTER"), null for XP-only updates
     * @param badgeName Human-readable badge name, null if badgeId is null
     * @return ApiResult.Success if write confirmed, ApiResult.Error otherwise
     */
    suspend fun writeStudentProgress(
        spreadsheetId: String,
        studentEmail: String,
        totalXp: Int,
        badgeId: String?,
        badgeName: String?
    ): ApiResult<Unit>

    /**
     * Write a project to the Projects tab.
     * 
     * @param spreadsheetId The target spreadsheet ID (contains Projects tab)
     * @param project Project entity to write
     * @return ApiResult.Success if write confirmed, ApiResult.Error otherwise
     */
    suspend fun writeProject(
        spreadsheetId: String,
        project: ProjectEntity
    ): ApiResult<Unit>

    /**
     * Write a team to the Teams tab.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @param team Team entity to write
     * @return ApiResult.Success if write confirmed, ApiResult.Error otherwise
     */
    suspend fun writeTeam(
        spreadsheetId: String,
        team: TeamEntity
    ): ApiResult<Unit>

    /**
     * Write a student to the Students tab.
     * 
     * CRITICAL: This is called from processQueue() when a STUDENT sync item is dequeued.
     * STUDENT items are enqueued by addMemberToTeam() with SyncOperationType.APPEND
     * and by removeMemberFromTeam() with SyncOperationType.DELETE.
     * 
     * For APPEND operations (new student): append a new row to Students tab.
     * For DELETE operations (removed student): mark row as soft-deleted with removed_at timestamp.
     * 
     * Students tab schema (A–G):
     * A: student_email, B: display_name, C: team_id, D: project_id, E: joined_at, F: role, G: removed_at
     * 
     * removed_at (column G): Soft-delete marker. Null/blank if student is active, epoch millis if removed.
     * When a student is deleted, removed_at gets a timestamp instead of the row being physically deleted.
     * This preserves the audit trail while allowing pullFromSheets to skip re-adding removed students.
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @param student Student entity to write
     * @param operationType APPEND (new student) or DELETE (removed student)
     * @return ApiResult.Success if write confirmed, ApiResult.Error otherwise
     */
    suspend fun writeStudent(
        spreadsheetId: String,
        student: StudentEntity,
        operationType: com.cutm.TeamPulse.domain.model.SyncOperationType
    ): ApiResult<Unit>
}
