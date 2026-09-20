package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    fun observeSyncStatus(): Flow<SyncStatus>

    suspend fun processQueue(): ApiResult<Unit>

    /**
     * Phase 1: Pull projects/teams/students from Sheets → Room (read-only sync).
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @return Success if all three tabs synced successfully, Error otherwise
     */
    suspend fun pullFromSheets(spreadsheetId: String): ApiResult<Unit>
}
