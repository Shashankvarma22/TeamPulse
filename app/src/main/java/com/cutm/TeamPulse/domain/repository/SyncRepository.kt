package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    fun observeSyncStatus(): Flow<SyncStatus>

    suspend fun processQueue(): ApiResult<Unit>

    /**
     * Phase 1: Pull projects/teams/students from shared Sheets → Room (read-only sync).
     * Data is filtered by teacher_email since the sheet contains all teachers' data.
     * 
     * @param teacherEmail The teacher's email to filter projects, teams, and students
     * @return Success if all three tabs synced successfully, Error otherwise
     */
    suspend fun pullFromSheets(teacherEmail: String): ApiResult<Unit>
}
