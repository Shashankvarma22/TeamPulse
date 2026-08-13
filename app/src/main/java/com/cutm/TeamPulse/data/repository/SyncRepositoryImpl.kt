package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import com.cutm.TeamPulse.domain.model.SyncStatus
import com.cutm.TeamPulse.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foundation sync repository. Queue processing is a no-op until Sheets API integration.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
) : SyncRepository {

    override fun observeSyncStatus(): Flow<SyncStatus> {
        return syncQueueDao.observeByStatus(SyncQueueStatus.PENDING).map { pending ->
            SyncStatus(
                pendingCount = pending.size,
                lastSyncAt = null,
                isSyncing = false,
            )
        }
    }

    override suspend fun processQueue(): ApiResult<Unit> {
        // Foundation skeleton: no Sheets API calls yet.
        return ApiResult.Success(Unit)
    }
}
