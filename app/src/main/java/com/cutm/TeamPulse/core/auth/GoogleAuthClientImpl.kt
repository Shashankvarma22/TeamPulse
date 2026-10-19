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
 * Rebuilt Google Credential Manager integration for Sign-In with Google.
 *
 * Strictly responsible for:
 * - Requesting Google ID token credential via CredentialManager
 * - Parsing and validating the credential
 * - Returning GoogleIdentity on success
 *
 * Explicitly NOT responsible for:
 * - Role lookup or authorization
 * - Session persistence
 * - Google Sheets authorization
 * - Dashboard navigation
 */
@Singleton
class GoogleAuthClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
) : GoogleAuthClient {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    override suspend fun signIn(activity: Activity): ApiResult<GoogleIdentity> {
        Log.d(TAG, "SIGN_IN_STARTED")

        // Verify Google Play Services
        val gmsAvailability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        if (gmsAvailability != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services unavailable: code=$gmsAvailability")
            return ApiResult.Error(context.getString(R.string.sign_in_error_generic))
        }

        // Configure credential request with Web OAuth client
        val serverClientId = context.getString(R.string.default_web_client_id)
        Log.d(TAG, "Web OAuth client: $serverClientId")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(generateSecureNonce())
            .build()

        val credentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            Log.d(TAG, "CREDENTIAL_MANAGER_CALLED")
            val credentialResponse = credentialManager.getCredential(
                context = activity,
                request = credentialRequest
            )
            Log.d(TAG, "CREDENTIAL_RETURNED")
            parseGoogleIdToken(credentialResponse.credential)

        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled sign-in")
            ApiResult.Error(context.getString(R.string.sign_in_error_cancelled), e)

        } catch (e: NoCredentialException) {
            Log.e(TAG, "No Google account on device", e)
            ApiResult.Error(context.getString(R.string.sign_in_error_no_google_account), e)

        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: type=${e.javaClass.simpleName}, credType=${e.type}, message=${e.message}", e)
            ApiResult.Error(context.getString(R.string.sign_in_error_generic), e)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.javaClass.simpleName}", e)
            ApiResult.Error(context.getString(R.string.sign_in_error_generic), e)
        }
    }

    private fun parseGoogleIdToken(credential: androidx.credentials.Credential): ApiResult<GoogleIdentity> {
        Log.d(TAG, "GOOGLE_ID_PARSE_START")

        if (credential !is CustomCredential) {
            Log.e(TAG, "Not a CustomCredential: ${credential::class.simpleName}")
            return ApiResult.Error(context.getString(R.string.sign_in_error_generic))
        }

        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Log.e(TAG, "Credential type mismatch: ${credential.type}")
            return ApiResult.Error(context.getString(R.string.sign_in_error_generic))
        }

        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Log.d(TAG, "GOOGLE_ID_PARSE_SUCCESS")

            val identity = GoogleIdentity(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.id,
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                idToken = googleIdTokenCredential.idToken
            )

            Log.d(TAG, "User authenticated: ${identity.email}")
            ApiResult.Success(identity)

        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse Google ID token", e)
            ApiResult.Error(context.getString(R.string.sign_in_error_unexpected_credential), e)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing credential: ${e::class.simpleName}", e)
            ApiResult.Error(context.getString(R.string.sign_in_error_generic), e)
        }
    }

    override suspend fun signOut() {
        Log.d(TAG, "SIGN_OUT_STARTED")
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state: ${e.message}")
        }
        tokenManager.clearTokens()
        Log.d(TAG, "Sign-out complete")
    }

    override suspend fun refreshTokenIfNeeded(): ApiResult<Unit> {
        val hasToken = tokenManager.hasStoredToken()
        val expiry = tokenManager.getAccessTokenExpiry()
        val isExpired = expiry != null && System.currentTimeMillis() >= expiry

        return when {
            hasToken && !isExpired -> ApiResult.Success(Unit)
            !hasToken -> ApiResult.Error("No access token available")
            else -> ApiResult.Error("Access token expired")
        }
    }

    private fun generateSecureNonce(): String {
        val uuid = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(uuid.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
