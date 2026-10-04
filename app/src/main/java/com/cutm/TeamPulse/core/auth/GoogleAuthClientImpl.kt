package com.cutm.TeamPulse.core.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.network.ApiResult
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TEAM_PULSE_AUTH"

/**
 * Google Sign-In via Credential Manager API.
 *
 * This class is responsible ONLY for Google identity authentication.
 * It does NOT perform role lookup or session persistence — those happen
 * later in the sign-in flow after OAuth authorization is complete.
 *
 * Flow:
 * 1. Generate nonce for ID token validation
 * 2. Request Google ID token credential
 * 3. Validate and extract user info
 * 4. Return GoogleIdentity
 */
@Singleton
class GoogleAuthClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
) : GoogleAuthClient {

    private val credentialManager by lazy { CredentialManager.create(context) }

    override suspend fun signIn(activity: Activity): ApiResult<GoogleIdentity> {
        val serverClientId = context.getString(R.string.default_web_client_id)

        // Check Google Play Services availability
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (availability != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services not available: code=$availability")
            return ApiResult.Error("Google Play Services not available (code $availability). Please update Play Services.")
        }

        Log.d(TAG, "signIn: serverClientId=$serverClientId")
        Log.d(TAG, "signIn: calling credentialManager.getCredential")

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = serverClientId,
        )
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activity,
                request = request,
            )

            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return ApiResult.Error(
                    message = context.getString(R.string.sign_in_error_unexpected_credential),
                )
            }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val identity = GoogleIdentity(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName
                    ?: googleIdTokenCredential.id,
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                idToken = googleIdTokenCredential.idToken, // Capture ID token for Cloud Function auth
            )

            ApiResult.Success(identity)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Sign-in cancelled: ${e.message}")
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_cancelled),
                cause = e,
            )
        } catch (e: NoCredentialException) {
            Log.e(TAG, "NoCredentialException: ${e.message}", e)
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_no_google_account),
                cause = e,
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: type=${e.javaClass.simpleName}, message=${e.message}", e)
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_generic),
                cause = e,
            )
        } catch (e: GoogleIdTokenParsingException) {
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_unexpected_credential),
                cause = e,
            )
        }
    }

    override suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Best-effort: clearing credential state must never block local sign-out.
        }
        tokenManager.clearTokens()
    }

    override suspend fun refreshTokenIfNeeded(): ApiResult<Unit> {
        val expiry = tokenManager.getAccessTokenExpiry()
        val isExpired = expiry != null && System.currentTimeMillis() >= expiry
        val hasToken = tokenManager.hasStoredToken()

        return if (hasToken && !isExpired) {
            // Token exists and is not expired — no refresh needed
            ApiResult.Success(Unit)
        } else if (!hasToken) {
            // No token available at all — re-authorization required
            ApiResult.Error(
                message = "No stored token available. Re-authorization required via sign-in flow."
            )
        } else {
            // Token exists but is expired — re-authorization required
            ApiResult.Error(
                message = "Access token expired. Re-authorization required via sign-in flow."
            )
        }
    }

    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
