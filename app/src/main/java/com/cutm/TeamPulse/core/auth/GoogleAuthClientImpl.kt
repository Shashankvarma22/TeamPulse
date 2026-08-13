package com.cutm.TeamPulse.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.dao.UserSessionDao
import com.cutm.TeamPulse.data.mapper.toEntity
import com.cutm.TeamPulse.domain.model.UserSession
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Google Sign-In implementation backed by Credential Manager.
 *
 * NOTE ON SCOPE: This only establishes *identity* (email, display name,
 * photo, ID token) via the "Sign in with Google" button flow. It does
 * NOT request Drive/Sheets OAuth scopes or an offline-capable access
 * token — that requires the separate Authorization API (incremental
 * auth) described in the PRD §5 and is a later milestone.
 *
 * NOTE ON ROLE: UserSession.role is non-nullable, but the Users Registry
 * lookup (UserRegistryRepositoryImpl) is still a stub. Per approved
 * decision, every freshly signed-in user is provisionally assigned
 * SessionRole.STUDENT. TODO: replace with a real UserRegistryRepository
 * lookup once that task lands, and re-route/re-persist the corrected
 * role after lookup.
 */
@Singleton
class GoogleAuthClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val userSessionDao: UserSessionDao,
) : GoogleAuthClient {

    private val credentialManager by lazy { CredentialManager.create(context) }

    override suspend fun signIn(activity: Activity): ApiResult<UserSession> {
        val serverClientId = context.getString(R.string.default_web_client_id)

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

            // INTENTIONALLY NOT PERSISTED THIS MILESTONE:
            // googleIdTokenCredential.idToken is a Google *ID token* (proves identity,
            // aud = our Web client ID) — it is NOT an OAuth *access token* and must not
            // be stored via TokenManager.saveAccessToken()/read by AuthInterceptor as a
            // Bearer credential for Google APIs; doing so would be silently wrong.
            // TokenManager currently only models a single generic "access token" and
            // isn't in scope for this change. A real OAuth access/refresh token for
            // Drive/Sheets scopes will come from a future AuthorizationClient
            // (incremental authorization) milestone, at which point TokenManager should
            // be extended (e.g. distinct saveIdToken()/saveAccessToken() + refresh
            // token storage) to hold both kinds of token correctly.
            // For now, identity alone is persisted below via UserSessionDao; there is
            // no access token to refresh, so refreshTokenIfNeeded() will correctly
            // report "no stored token" until that milestone lands — this is expected,
            // not a bug.

            val session = UserSession(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName
                    ?: googleIdTokenCredential.id,
                // TODO: replace with UserRegistryRepository.lookupUser(email) result
                // once the Users Registry lookup task is implemented.
                role = SessionRole.STUDENT,
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                lastSignInAt = System.currentTimeMillis(),
            )

            userSessionDao.upsert(session.toEntity())

            ApiResult.Success(session)
        } catch (e: GetCredentialCancellationException) {
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_cancelled),
                cause = e,
            )
        } catch (e: NoCredentialException) {
            ApiResult.Error(
                message = context.getString(R.string.sign_in_error_no_google_account),
                cause = e,
            )
        } catch (e: GetCredentialException) {
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
        return if (tokenManager.hasStoredToken()) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error(message = "No stored token available for refresh.")
        }
    }

    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
