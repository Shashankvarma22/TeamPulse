package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    fun observeSyncStatus(): Flow<SyncStatus>

    suspend fun processQueue(): ApiResult<Unit>
}
