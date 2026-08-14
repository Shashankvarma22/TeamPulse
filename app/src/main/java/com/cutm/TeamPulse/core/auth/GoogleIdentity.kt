package com.cutm.TeamPulse.core.auth

/**
 * Represents the identity information obtained from Google Credential Manager.
 * This is intentionally separate from UserSession — it contains only what
 * Google authentication provides, without a resolved role or persisted session.
 */
data class GoogleIdentity(
    val email: String,
    val displayName: String,
    val photoUrl: String?,
)
