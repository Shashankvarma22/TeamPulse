package com.cutm.TeamPulse.core.auth

import android.app.PendingIntent

/**
 * Result of a Google Sheets read-scope incremental authorization request
 * (see AuthorizationManager). This is deliberately separate from
 * [com.cutm.TeamPulse.core.network.ApiResult]/[UiState] — it models an
 * Activity-Result-shaped outcome (token vs. a resolution the caller must
 * launch vs. an error), not a generic success/error payload.
 */
sealed class AuthorizationOutcome {

    /** Authorization was already granted; [accessToken] can be used immediately. */
    data class Authorized(
        val accessToken: String,
        val expiresAtMillis: Long?,
    ) : AuthorizationOutcome()

    /**
     * The user must resolve a consent UI before authorization can complete.
     * Callers should launch [pendingIntent] via an
     * ActivityResultLauncher<IntentSenderRequest> and pass the result Intent
     * to AuthorizationManager.handleAuthorizationResult.
     */
    data class ResolutionRequired(
        val pendingIntent: PendingIntent,
    ) : AuthorizationOutcome()

    data class Error(
        val message: String,
    ) : AuthorizationOutcome()
}
