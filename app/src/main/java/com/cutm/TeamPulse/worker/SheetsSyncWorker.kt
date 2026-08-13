package com.cutm.TeamPulse.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cutm.TeamPulse.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Foundation skeleton for Sheets synchronization.
 * Does not call Google Sheets API yet.
 */
@HiltWorker
class SheetsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SheetsSyncWorker started (foundation skeleton)")
        return when (val result = syncRepository.processQueue()) {
            is com.cutm.TeamPulse.core.network.ApiResult.Success -> Result.success()
            is com.cutm.TeamPulse.core.network.ApiResult.Error -> {
                Log.w(TAG, "Sync queue processing not yet implemented: ${result.message}")
                Result.success()
            }
        }
    }

    private companion object {
        const val TAG = "SheetsSyncWorker"
    }
}
