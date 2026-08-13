package com.cutm.TeamPulse.domain.model

import com.cutm.TeamPulse.core.auth.SessionRole

data class UserSession(
    val email: String,
    val displayName: String,
    val role: SessionRole,
    val photoUrl: String?,
    val lastSignInAt: Long,
)
