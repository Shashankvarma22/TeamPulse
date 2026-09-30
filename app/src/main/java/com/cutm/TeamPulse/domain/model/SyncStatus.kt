package com.cutm.TeamPulse.domain.model

enum class SyncOperationType {
    APPEND,
    UPDATE,
    DELETE,
}

enum class SyncQueueStatus {
    PENDING,              // Awaiting sync, or retrying (retryCount < 10)
    FAILED_PERMANENTLY,   // Retry limit exceeded (retryCount >= 10), manual intervention needed
}

data class SyncStatus(
    val pendingCount: Int,
    val lastSyncAt: Long?,
    val isSyncing: Boolean,
)
