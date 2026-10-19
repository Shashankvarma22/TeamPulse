package com.cutm.TeamPulse.ui.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.core.auth.GoogleAuthClient
import com.cutm.TeamPulse.core.auth.GoogleIdentity
import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.core.auth.TokenManager
import com.cutm.TeamPulse.core.dispatchers.DispatcherProvider
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.UserSession
import com.cutm.TeamPulse.domain.repository.AuthRepository
import com.cutm.TeamPulse.domain.repository.UserRegistryRepository
import com.cutm.TeamPulse.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val googleAuthClient: GoogleAuthClient,
    private val authRepository: AuthRepository,
    private val userRegistryRepository: UserRegistryRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<UiState<GoogleIdentity>>(UiState.Idle)

    val uiState: StateFlow<UiState<GoogleIdentity>> =
        _uiState.asStateFlow()

    private val _requestSheetsAuthorization =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val requestSheetsAuthorization: SharedFlow<Unit> =
        _requestSheetsAuthorization.asSharedFlow()

    private val _navigateToHome =
        MutableSharedFlow<SessionRole>(extraBufferCapacity = 1)

    val navigateToHome: SharedFlow<SessionRole> =
        _navigateToHome.asSharedFlow()

    private var googleIdentity: GoogleIdentity? = null
    private var isSigningIn = false

    val existingSession: StateFlow<UserSession?> = authRepository.observeSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun onGoogleSignInClicked(activity: Activity) {
        if (isSigningIn) {
            Log.d("TEAM_PULSE_AUTH", "Sign-in already in progress, ignoring click")
            return
        }

        if (_uiState.value is UiState.Loading) {
            Log.d("TEAM_PULSE_AUTH", "Loading state, ignoring click")
            return
        }

        viewModelScope.launch {
            try {
                isSigningIn = true
                _uiState.value = UiState.Loading

                val result = googleAuthClient.signIn(activity)
                Log.d("TEAM_PULSE_AUTH", "onGoogleSignInClicked: googleAuthClient.signIn() result: $result")

                when (result) {
                    is ApiResult.Success -> {
                        Log.d("TEAM_PULSE_AUTH", "GOOGLE_CREDENTIAL_SUCCESS: email=${result.data.email}")
                        googleIdentity = result.data
                        Log.d("TEAM_PULSE_AUTH", "SHEETS_AUTHORIZATION_REQUESTED")
                        _requestSheetsAuthorization.tryEmit(Unit)
                    }

                    is ApiResult.Error -> {
                        Log.e("TEAM_PULSE_AUTH", "onGoogleSignInClicked: GOOGLE_CREDENTIAL_ERROR: ${result.message}", result.cause)
                        _uiState.value = UiState.Error(result.message)
                    }
                }
            } finally {
                isSigningIn = false
            }
        }
    }

    fun onAuthorizationToken(
        token: String,
        expiresAtMillis: Long?,
    ) {
        viewModelScope.launch {
            try {
                Log.d("TEAM_PULSE_AUTH", "onAuthorizationToken called")
                _uiState.value = UiState.Loading

                tokenManager.saveAccessToken(
                    token = token,
                    expiresAtMillis = expiresAtMillis,
                )

                val identity = googleIdentity
                if (identity == null) {
                    Log.e("TEAM_PULSE_AUTH", "GOOGLE_IDENTITY_LOST: authentication state lost")
                    _uiState.value = UiState.Error("Authentication state lost. Please sign in again.")
                    return@launch
                }

                Log.d("TEAM_PULSE_AUTH", "USER_LOOKUP_STARTED: email=${identity.email}")

                when (val roleResult = userRegistryRepository.lookupUser(identity.email, identity.idToken)) {
                    is ApiResult.Success -> {
                        val role = roleResult.data
                        Log.d("TEAM_PULSE_AUTH", "USER_LOOKUP_SUCCESS: role=$role")

                        val session = UserSession(
                            email = identity.email,
                            displayName = identity.displayName,
                            role = role,
                            photoUrl = identity.photoUrl,
                            lastSignInAt = System.currentTimeMillis(),
                        )

                        Log.d("TEAM_PULSE_AUTH", "SESSION_SAVE_STARTED: email=${session.email}, role=$role")

                        authRepository.saveSession(session)
                        Log.d("TEAM_PULSE_AUTH", "SESSION_SAVE_SUCCESS")

                        _uiState.value = UiState.Success(identity)
                        Log.d("TEAM_PULSE_AUTH", "SIGN_IN_SUCCESS: navigating to role=$role")

                        _navigateToHome.tryEmit(role)
                    }

                    is ApiResult.Error -> {
                        Log.e("TEAM_PULSE_AUTH", "USER_LOOKUP_FAILED: ${roleResult.message}", roleResult.cause)
                        _uiState.value = UiState.Error(roleResult.message)
                    }
                }
            } catch (e: Exception) {
                Log.e("TEAM_PULSE_AUTH", "Unexpected error in onAuthorizationToken", e)
                _uiState.value = UiState.Error("Sign-in failed. Please try again.")
            }
        }
    }

    fun onAuthorizationError(message: String) {
        Log.e("TEAM_PULSE_AUTH", "Authorization error: $message")
        _uiState.value = UiState.Error(message)
    }
}
