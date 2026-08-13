package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cutm.TeamPulse.core.auth.SessionRole

@Entity(tableName = "user_sessions")
data class UserSessionEntity(
    @PrimaryKey val email: String,
    val displayName: String,
    val role: SessionRole,
    val photoUrl: String?,
    val lastSignInAt: Long,
)
