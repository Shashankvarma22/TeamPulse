package com.cutm.TeamPulse.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real incremental-authorization implementation backed by the Play Services
 * Identity Authorization API (`Identity.getAuthorizationClient`). Requests
 * ONLY the Sheets read-only scope — this is deliberately scoped down from
 * general Drive/account access, per the PRD's least-privilege guidance.
 *
 * This class never stores tokens itself (that's TokenManager, driven by the
 * ViewModel) and never launches an IntentSender (that's the Fragment, via
 * its ActivityResultLauncher) — it only requests/parses outcomes.
 */
@Singleton
class AuthorizationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthorizationManager {

    override suspend fun requestSheetsReadAuthorization(activity: Activity): AuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SHEETS_READONLY_SCOPE)))
            .build()

        return suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener { result ->
                    continuation.resume(result.toOutcome())
                }
                .addOnFailureListener { exception ->
                    continuation.resume(
                        AuthorizationOutcome.Error(
                            message = exception.message
                                ?: "Failed to request Sheets read authorization.",
                        ),
                    )
                }
        }
    }

    override suspend fun handleAuthorizationResult(intent: Intent): AuthorizationOutcome {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(intent)
            result.toOutcome()
        } catch (e: ApiException) {
            AuthorizationOutcome.Error(
                message = e.message ?: "Failed to complete Sheets read authorization.",
            )
        }
    }

    private fun AuthorizationResult.toOutcome(): AuthorizationOutcome {
        return when {
            hasResolution() -> {
                val resolution = pendingIntent
                if (resolution != null) {
                    AuthorizationOutcome.ResolutionRequired(resolution)
                } else {
                    AuthorizationOutcome.Error(
                        message = "Authorization requires resolution but no resolution was provided.",
                    )
                }
            }
            accessToken != null -> AuthorizationOutcome.Authorized(
                accessToken = accessToken!!,
                // The Authorization API does not surface an explicit expiry
                // timestamp on AuthorizationResult; token lifetime is
                // managed by Play services. TokenManager treats a null
                // expiry as "unknown" rather than "never expires".
                expiresAtMillis = null,
            )
            else -> AuthorizationOutcome.Error(
                message = "Authorization did not return an access token.",
            )
        }
    }

    private companion object {
        const val SHEETS_READONLY_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly"
    }
}
