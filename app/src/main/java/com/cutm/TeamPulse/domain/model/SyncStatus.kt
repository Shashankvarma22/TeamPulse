package com.cutm.TeamPulse.domain.model

enum class SyncOperationType {
    APPEND,
    UPDATE,
    DELETE,
}

enum class SyncQueueStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
    DONE,
}

data class SyncStatus(
    val pendingCount: Int,
    val lastSyncAt: Long?,
    val isSyncing: Boolean,
)
