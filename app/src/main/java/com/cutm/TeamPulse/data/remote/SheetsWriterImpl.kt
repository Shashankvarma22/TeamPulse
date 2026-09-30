package com.cutm.TeamPulse.data.remote

import android.util.Log
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.dao.SyncMetadataDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.StudentProgressEntity
import com.cutm.TeamPulse.data.local.entity.SyncMetadataEntity
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SheetsWriter using Google Sheets API v4 via Retrofit.
 * 
 * AUTH PATH (TRACED from SheetsProbeReaderImpl + NetworkModule):
 * - Uses @SheetsRetrofit Retrofit client (provideSheetsRetrofit in NetworkModule)
 * - AuthInterceptor adds "Authorization: Bearer <token>" header
 * - Token comes from TokenManager.getAccessToken() (user's OAuth token)
 * - Same auth path as SheetsProbeReaderImpl — no separate service account flow
 * 
 * ROW-INDEX OPTIMIZATION (TRACED from design doc §3, Decision 3):
 * - Read-cache-write pattern using sync_metadata.rowIndexCache
 * - Cache format: JSON string mapping entityId → rowIndex
 * - Example key: "tasks:project_abc" → rowIndexCache: '{"task_123": 5, "task_456": 12}'
 * - Row index = actual row number in Sheets (1-indexed for API ranges, +2 from API array index)
 * - Fallback: If no cache entry, linear scan to find row, then cache result
 * - Never full-sheet scan on every write
 */
@Singleton
class SheetsWriterImpl @Inject constructor(
    private val sheetsApiService: SheetsApiService,
    private val syncMetadataDao: SyncMetadataDao,
    private val teamDao: TeamDao,
    private val studentDao: StudentDao,
    private val projectDao: ProjectDao,
    private val moshi: Moshi,
) : SheetsWriter {

    private companion object {
        const val TAG = "SheetsWriter"
    }

    override suspend fun writeTask(
        spreadsheetId: String,
        task: TaskAssignmentEntity
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "writeTask: Writing task ${task.taskId} to Sheets")

            /**
             * CRITICAL CALL CHAIN (TRACED from design doc §8.3):
             * TaskRepositoryImpl.applyTaskUpdate() [lines 113-154]
             *   ↓ (if justCompletedForFirstTime)
             * awardXpForTaskCompletion() [lines 166-214]
             *   ↓ (inside withTransaction block)
             * studentProgressDao.upsert() [line 210]
             *   ↓ (after this session's Phase 4 implementation)
             * syncQueueDao.enqueue(targetTab="TaskAssignments", ...)
             *   ↓ (WorkManager triggers)
             * SyncRepositoryImpl.processQueue() [new implementation, Phase 3]
             *   ↓ (calls SheetsWriter for each pending item)
             * THIS METHOD: SheetsWriterImpl.writeTask()
             * 
             * The hasEverBeenCompleted guard (lines 126-142 of TaskRepositoryImpl.applyTaskUpdate())
             * has already executed and set the final flag state before this write happens.
             * No re-validation needed here — only persist the entity state to Sheets.
             */

            // Step 1: Lookup cached row index
            val cacheKey = "tasks:$spreadsheetId"
            val rowIndex = lookupOrScanRowIndex(
                spreadsheetId = spreadsheetId,
                cacheKey = cacheKey,
                entityId = task.taskId,
                tabName = "TaskAssignments",
                rowCount = 10  // Assumed TaskAssignments tab has columns A-J
            )

            if (rowIndex == null) {
                Log.w(TAG, "writeTask: Failed to find row for task ${task.taskId} in Sheets, skipping write")
                return@withContext ApiResult.Error("Task row not found in Sheets")
            }

            // Step 2: Convert entity to Sheets row values
            val rowValues = listOf(
                task.taskId,                              // A: task_id
                task.teamId,                              // B: team_id
                task.projectId,                           // C: project_id
                task.assigneeEmail,                       // D: assignee_email
                task.title,                               // E: title
                task.description,                         // F: description
                task.weight.toString(),                   // G: weight
                task.dueDate.toString(),                  // H: due_date
                task.status.name,                         // I: status (ENUM NAME: TODO, IN_PROGRESS, DONE)
                task.hasEverBeenCompleted.toString()      // J: has_ever_been_completed
                    .uppercase()  // Convert "true"/"false" → "TRUE"/"FALSE"
            )

            // Step 3: Call Sheets API to update row
            val range = "TaskAssignments!A${rowIndex}:J${rowIndex}"
            try {
                sheetsApiService.updateValues(
                    spreadsheetId = spreadsheetId,
                    range = range,
                    body = SheetsUpdateRequest(
                        values = listOf(rowValues)
                    )
                )
                Log.d(TAG, "writeTask: Successfully wrote task ${task.taskId} to row $rowIndex")
                ApiResult.Success(Unit)
            } catch (e: HttpException) {
                Log.e(TAG, "writeTask: HTTP error ${e.code()} writing task ${task.taskId}", e)
                ApiResult.Error("Failed to write task to Sheets (HTTP ${e.code()})", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeTask: Error writing task ${task.taskId}", e)
            ApiResult.Error("Failed to write task to Sheets: ${e.message}", e)
        }
    }

    override suspend fun writeStudentProgress(
        spreadsheetId: String,
        studentEmail: String,
        totalXp: Int,
        badgeId: String?,
        badgeName: String?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "writeStudentProgress: Writing XP=$totalXp, badge=$badgeId for $studentEmail")

            /**
             * Decision 4 (design doc §12): Achievements tab schema
             * - Columns: student_email, badge_id, badge_name, awarded_at, xp_total
             * - One row per student_email + badge_id maximum (upsert by key)
             * - XP-only updates use badgeId="XP_TOTAL" as pseudo-badge
             * - awarded_at timestamp when badge first earned (set on first write, updated on upsert)
             */

            // Determine row identifier and values
            val upsertKey = if (badgeId != null) {
                "$studentEmail:$badgeId"  // Badge row key
            } else {
                "$studentEmail:XP_TOTAL"  // XP-only pseudo-badge row key
            }

            val actualBadgeId = badgeId ?: "XP_TOTAL"
            val actualBadgeName = badgeName ?: "XP Total"
            val awardedAtTimestamp = System.currentTimeMillis()

            // Step 1: Lookup or scan for existing row
            val cacheKey = "achievements:$spreadsheetId"
            val rowIndex = lookupOrScanRowIndex(
                spreadsheetId = spreadsheetId,
                cacheKey = cacheKey,
                entityId = upsertKey,
                tabName = "Achievements",
                rowCount = 5  // Assumed Achievements tab has columns A-E
            )

            val range: String
            val operation: String
            if (rowIndex == null) {
                // New row: append to next available row in Achievements tab
                // For now, simplified: write to a fixed row (in Phase 4, handle append logic)
                Log.w(TAG, "writeStudentProgress: No cached row for $upsertKey, treating as new row")
                operation = "append"
                range = "Achievements!A:E"  // Append range (API will find first empty row)
            } else {
                // Existing row: update in place
                operation = "update"
                range = "Achievements!A${rowIndex}:E${rowIndex}"
            }

            // Step 2: Build row values
            val rowValues = listOf(
                studentEmail,                    // A: student_email
                actualBadgeId,                   // B: badge_id
                actualBadgeName,                 // C: badge_name
                awardedAtTimestamp.toString(),   // D: awarded_at (timestamp)
                totalXp.toString()               // E: xp_total (running aggregate)
            )

            // Step 3: Call Sheets API
            try {
                if (operation == "update" && rowIndex != null) {
                    sheetsApiService.updateValues(
                        spreadsheetId = spreadsheetId,
                        range = range,
                        body = SheetsUpdateRequest(
                            values = listOf(rowValues)
                        )
                    )
                } else {
                    sheetsApiService.appendValues(
                        spreadsheetId = spreadsheetId,
                        range = range,
                        body = SheetsUpdateRequest(
                            values = listOf(rowValues)
                        )
                    )
                }
                Log.d(TAG, "writeStudentProgress: Successfully wrote $operation for $upsertKey with XP=$totalXp")
                ApiResult.Success(Unit)
            } catch (e: HttpException) {
                Log.e(TAG, "writeStudentProgress: HTTP error ${e.code()} writing $upsertKey", e)
                ApiResult.Error("Failed to write student progress to Sheets (HTTP ${e.code()})", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeStudentProgress: Error writing student progress", e)
            ApiResult.Error("Failed to write student progress: ${e.message}", e)
        }
    }

    override suspend fun writeProject(
        spreadsheetId: String,
        project: ProjectEntity
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "writeProject: Writing project ${project.projectId}")

            val cacheKey = "projects"
            val rowIndex = lookupOrScanRowIndex(
                spreadsheetId = spreadsheetId,
                cacheKey = cacheKey,
                entityId = project.projectId,
                tabName = "Projects",
                rowCount = 9  // Projects tab: A-I (9 columns)
            )

            if (rowIndex == null) {
                Log.w(TAG, "writeProject: Project row not found, skipping")
                return@withContext ApiResult.Error("Project row not found in Sheets")
            }

            val rowValues = listOf(
                project.projectId,          // A: project_id
                project.name,               // B: name
                project.teacherEmail,       // C: teacher_email
                project.spreadsheetId,      // D: spreadsheet_id
                project.startDate.toString(),    // E: start_date
                project.dueDate.toString(),      // F: due_date
                project.driveFolderId,      // G: drive_folder_id
                project.githubRepo ?: "",   // H: github_repo (optional, default empty)
                project.status.name         // I: status
            )

            val range = "Projects!A${rowIndex}:I${rowIndex}"
            try {
                sheetsApiService.updateValues(
                    spreadsheetId = spreadsheetId,
                    range = range,
                    body = SheetsUpdateRequest(
                        values = listOf(rowValues)
                    )
                )
                Log.d(TAG, "writeProject: Successfully wrote project ${project.projectId}")
                ApiResult.Success(Unit)
            } catch (e: HttpException) {
                Log.e(TAG, "writeProject: HTTP error ${e.code()}", e)
                ApiResult.Error("Failed to write project to Sheets (HTTP ${e.code()})", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeProject: Error writing project", e)
            ApiResult.Error("Failed to write project: ${e.message}", e)
        }
    }

    override suspend fun writeTeam(
        spreadsheetId: String,
        team: TeamEntity
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "writeTeam: Writing team ${team.teamId}")

            val cacheKey = "teams:$spreadsheetId"
            val rowIndex = lookupOrScanRowIndex(
                spreadsheetId = spreadsheetId,
                cacheKey = cacheKey,
                entityId = team.teamId,
                tabName = "Teams",
                rowCount = 5  // Teams tab: A-E (5 columns)
            )

            if (rowIndex == null) {
                Log.w(TAG, "writeTeam: Team row not found, skipping")
                return@withContext ApiResult.Error("Team row not found in Sheets")
            }

            // memberEmails is a list; convert to comma-separated string for Sheets
            val memberEmailsString = team.memberEmails.joinToString(",")

            val rowValues = listOf(
                team.teamId,                // A: team_id
                team.projectId,             // B: project_id
                team.teamName,              // C: team_name
                memberEmailsString,         // D: member_emails (comma-separated)
                team.createdAt.toString()   // E: created_at
            )

            val range = "Teams!A${rowIndex}:E${rowIndex}"
            try {
                sheetsApiService.updateValues(
                    spreadsheetId = spreadsheetId,
                    range = range,
                    body = SheetsUpdateRequest(
                        values = listOf(rowValues)
                    )
                )
                Log.d(TAG, "writeTeam: Successfully wrote team ${team.teamId}")
                ApiResult.Success(Unit)
            } catch (e: HttpException) {
                Log.e(TAG, "writeTeam: HTTP error ${e.code()}", e)
                ApiResult.Error("Failed to write team to Sheets (HTTP ${e.code()})", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeTeam: Error writing team", e)
            ApiResult.Error("Failed to write team: ${e.message}", e)
        }
    }

    override suspend fun writeStudent(
        spreadsheetId: String,
        student: StudentEntity,
        operationType: com.cutm.TeamPulse.domain.model.SyncOperationType
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "writeStudent: Writing student ${student.studentEmail} (operation=$operationType)")

            /**
             * STUDENT SYNC ROUTING (Option 2: soft-delete with removed_at marker):
             * - addMemberToTeam() enqueues SyncOperationType.APPEND with new StudentEntity
             * - removeMemberFromTeam() enqueues SyncOperationType.DELETE with StudentEntity
             * - processQueue() deserializes and routes to this method
             * - APPEND: Add new row to Students tab (A–G with removed_at blank/null)
             * - DELETE: Find row, read existing data (especially role in column F), write all columns with removed_at timestamp (column G)
             * 
             * Students tab schema (A–G):
             * A: student_email, B: display_name, C: team_id, D: project_id, E: joined_at, F: role, G: removed_at
             */

            when (operationType) {
                com.cutm.TeamPulse.domain.model.SyncOperationType.APPEND -> {
                    // New student: append to Students tab
                    Log.d(TAG, "writeStudent: APPEND operation for ${student.studentEmail}")

                    val rowValues = listOf(
                        student.studentEmail,           // A: student_email
                        student.displayName,            // B: display_name
                        student.teamId,                 // C: team_id
                        student.projectId,              // D: project_id
                        student.joinedAt.toString(),    // E: joined_at
                        "MEMBER",                       // F: role (default)
                        ""                              // G: removed_at (blank for new student)
                    )

                    val range = "Students!A:G"  // Append to next available row
                    try {
                        sheetsApiService.appendValues(
                            spreadsheetId = spreadsheetId,
                            range = range,
                            body = SheetsUpdateRequest(
                                values = listOf(rowValues)
                            )
                        )
                        Log.d(TAG, "writeStudent: Successfully appended student ${student.studentEmail}")
                        ApiResult.Success(Unit)
                    } catch (e: HttpException) {
                        Log.e(TAG, "writeStudent: HTTP error ${e.code()} appending student", e)
                        ApiResult.Error("Failed to append student to Sheets (HTTP ${e.code()})", e)
                    }
                }

                com.cutm.TeamPulse.domain.model.SyncOperationType.DELETE -> {
                    // Student removed from team: soft-delete by writing removed_at timestamp
                    // Look up row index, read existing data, write all columns with removed_at = now
                    Log.d(TAG, "writeStudent: DELETE operation (soft-delete) for ${student.studentEmail}")

                    val cacheKey = "students:$spreadsheetId"
                    val rowIndex = lookupOrScanRowIndex(
                        spreadsheetId = spreadsheetId,
                        cacheKey = cacheKey,
                        entityId = student.studentEmail,
                        tabName = "Students",
                        rowCount = 7  // Students tab: A-G (7 columns)
                    )

                    if (rowIndex == null) {
                        // Student row not found in Sheets (already removed or never synced?)
                        Log.w(TAG, "writeStudent: DELETE - Student ${student.studentEmail} not found in Sheets, skipping soft-delete")
                        return@withContext ApiResult.Success(Unit)
                    }

                    try {
                        // Read the current row to preserve existing values (especially role in column F)
                        val getResponse = sheetsApiService.getValues(
                            spreadsheetId = spreadsheetId,
                            range = "Students!A${rowIndex}:G${rowIndex}"
                        )

                        val existingRow = getResponse.values?.firstOrNull() as? List<*>
                        if (existingRow == null) {
                            Log.w(TAG, "writeStudent: DELETE - Could not read existing row at $rowIndex, using defaults")
                            // Fallback: construct row without reading existing role
                            val rowValues = listOf(
                                student.studentEmail,          // A: student_email
                                student.displayName,           // B: display_name
                                student.teamId,                // C: team_id
                                student.projectId,             // D: project_id
                                student.joinedAt.toString(),   // E: joined_at
                                "MEMBER",                      // F: role (default fallback)
                                System.currentTimeMillis().toString()  // G: removed_at (now)
                            )

                            sheetsApiService.updateValues(
                                spreadsheetId = spreadsheetId,
                                range = "Students!A${rowIndex}:G${rowIndex}",
                                body = SheetsUpdateRequest(
                                    values = listOf(rowValues)
                                )
                            )
                            Log.d(TAG, "writeStudent: Successfully soft-deleted student ${student.studentEmail} at row $rowIndex (fallback, no role preserved)")
                            return@withContext ApiResult.Success(Unit)
                        }

                        // Read-modify-write: preserve existing values and add removed_at timestamp
                        val role = existingRow.getOrNull(5)?.toString() ?: "MEMBER"  // Column F: role
                        val rowValues = listOf(
                            student.studentEmail,          // A: student_email
                            student.displayName,           // B: display_name
                            student.teamId,                // C: team_id
                            student.projectId,             // D: project_id
                            student.joinedAt.toString(),   // E: joined_at
                            role,                          // F: role (preserved from existing row)
                            System.currentTimeMillis().toString()  // G: removed_at (now)
                        )

                        sheetsApiService.updateValues(
                            spreadsheetId = spreadsheetId,
                            range = "Students!A${rowIndex}:G${rowIndex}",
                            body = SheetsUpdateRequest(
                                values = listOf(rowValues)
                            )
                        )
                        Log.d(TAG, "writeStudent: Successfully soft-deleted student ${student.studentEmail} at row $rowIndex with removed_at timestamp")
                        ApiResult.Success(Unit)
                    } catch (e: HttpException) {
                        Log.e(TAG, "writeStudent: HTTP error ${e.code()} soft-deleting student", e)
                        ApiResult.Error("Failed to soft-delete student in Sheets (HTTP ${e.code()})", e)
                    }
                }

                else -> {
                    val reason = "Unexpected operation type for STUDENT: $operationType"
                    Log.e(TAG, "writeStudent: $reason")
                    ApiResult.Error(reason)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeStudent: Error writing student", e)
            ApiResult.Error("Failed to write student: ${e.message}", e)
        }
    }

    /**
     * Helper: Look up cached row index from sync_metadata, or scan the Sheets tab.
     * 
     * TRACED from design doc §3 (Decision 3 read-cache-write pattern):
     * 1. Check sync_metadata.rowIndexCache for cacheKey
     * 2. If cache hit and entityId in map: use cached index
     * 3. If cache miss or entityId not in map: linear scan Sheets tab
     * 4. Update cache with result for future writes
     */
    private suspend fun lookupOrScanRowIndex(
        spreadsheetId: String,
        cacheKey: String,
        entityId: String,
        tabName: String,
        rowCount: Int
    ): Int? {
        // Step 1: Check cache
        val cacheRow = syncMetadataDao.getByKey(cacheKey)
        if (cacheRow != null && cacheRow.rowIndexCache != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val rowMap = moshi.adapter(Map::class.java)
                    .fromJson(cacheRow.rowIndexCache) as? Map<String, Int>
                if (rowMap != null && rowMap.containsKey(entityId)) {
                    val cachedIndex = rowMap[entityId]!!
                    Log.d(TAG, "lookupOrScanRowIndex: Cache hit for $entityId → row $cachedIndex")
                    return cachedIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "lookupOrScanRowIndex: Failed to parse cache JSON for $cacheKey", e)
            }
        }

        // Step 2: Cache miss or entityId not in map — linear scan
        Log.d(TAG, "lookupOrScanRowIndex: Cache miss for $entityId, scanning $tabName tab")
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = spreadsheetId,
                range = "$tabName!A:${('A' + rowCount).toChar()}"  // Read all columns
            )

            var foundRowIndex: Int? = null
            var duplicateCount = 0
            response.values?.forEachIndexed { arrayIndex, row ->
                val rowList = row as? List<*>
                if (rowList != null && rowList.isNotEmpty()) {
                    val firstColumn = rowList[0].toString()
                    if (firstColumn == entityId) {
                        val currentRowIndex = arrayIndex + 2  // 1 for header, 1 for 1-indexed Sheets
                        if (foundRowIndex == null) {
                            // First match found
                            foundRowIndex = currentRowIndex
                            Log.d(TAG, "lookupOrScanRowIndex: Scan found $entityId at row $currentRowIndex")
                        } else {
                            // Duplicate found! Log warning but use first match
                            duplicateCount++
                            Log.w(TAG, "lookupOrScanRowIndex: DUPLICATE DETECTED - $entityId found at row $currentRowIndex (already found at row $foundRowIndex). Using first match. Duplicate count: $duplicateCount")
                        }
                    }
                }
            }

            if (duplicateCount > 0) {
                Log.w(TAG, "lookupOrScanRowIndex: WARNING - Found $duplicateCount duplicate rows for $entityId in $tabName tab. This indicates test data or previous sync errors. Consider manual cleanup of $tabName sheet.")
            }

            if (foundRowIndex != null) {
                // Update cache with new mapping
                updateRowIndexCache(cacheKey, entityId, foundRowIndex!!)
            }

            foundRowIndex
        } catch (e: Exception) {
            Log.e(TAG, "lookupOrScanRowIndex: Failed to scan $tabName tab", e)
            null
        }
    }

    /**
     * Helper: Update sync_metadata.rowIndexCache with new entityId → rowIndex mapping.
     */
    private suspend fun updateRowIndexCache(
        cacheKey: String,
        entityId: String,
        rowIndex: Int
    ) {
        return withContext(Dispatchers.IO) {
            try {
                val existing = syncMetadataDao.getByKey(cacheKey)
                val currentMap = if (existing != null && existing.rowIndexCache != null) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        (moshi.adapter(Map::class.java)
                            .fromJson(existing.rowIndexCache) as? Map<String, Int>)?.toMutableMap()
                            ?: mutableMapOf()
                    } catch (e: Exception) {
                        Log.w(TAG, "updateRowIndexCache: Failed to parse existing cache", e)
                        mutableMapOf()
                    }
                } else {
                    mutableMapOf()
                }

                // Add or update the mapping
                currentMap[entityId] = rowIndex
                val newCacheJson = moshi.adapter(Map::class.java).toJson(currentMap)

                val updated = if (existing != null) {
                    existing.copy(rowIndexCache = newCacheJson)
                } else {
                    SyncMetadataEntity(
                        key = cacheKey,
                        lastPullAt = System.currentTimeMillis(),
                        lastPushAt = System.currentTimeMillis(),
                        etagOrRevision = null,
                        rowIndexCache = newCacheJson
                    )
                }

                syncMetadataDao.upsert(updated)
                Log.d(TAG, "updateRowIndexCache: Cached $entityId → $rowIndex for key $cacheKey")
            } catch (e: Exception) {
                Log.e(TAG, "updateRowIndexCache: Failed to update cache", e)
                // Non-fatal: cache miss on next write, but functionality continues
            }
        }
    }
}
