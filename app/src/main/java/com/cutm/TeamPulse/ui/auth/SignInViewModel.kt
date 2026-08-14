package com.cutm.TeamPulse.ui.auth

import android.app.Activity
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun onGoogleSignInClicked(activity: Activity) {
        if (_uiState.value is UiState.Loading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val result = googleAuthClient.signIn(activity)

            when (result) {
                is ApiResult.Success -> {
                    // Store identity temporarily until role is resolved
                    googleIdentity = result.data
                    _uiState.value = UiState.Success(result.data)
                    // Request Sheets authorization before role lookup
                    _requestSheetsAuthorization.tryEmit(Unit)
                }

                is ApiResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun onAuthorizationToken(
        token: String,
        expiresAtMillis: Long?,
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Save access token first
            tokenManager.saveAccessToken(
                token = token,
                expiresAtMillis = expiresAtMillis,
            )

            // Retrieve the stored identity
            val identity = googleIdentity
            if (identity == null) {
                _uiState.value = UiState.Error("Authentication state lost. Please sign in again.")
                return@launch
            }

            // Now perform role lookup with authenticated token
            when (val roleResult = userRegistryRepository.lookupUser(identity.email)) {
                is ApiResult.Success -> {
                    val role = roleResult.data

                    // Create UserSession with resolved role
                    val session = UserSession(
                        email = identity.email,
                        displayName = identity.displayName,
                        role = role,
                        photoUrl = identity.photoUrl,
                        lastSignInAt = System.currentTimeMillis(),
                    )

                    // Persist session with real role
                    authRepository.saveSession(session)

                    // Navigate to appropriate home
                    _navigateToHome.tryEmit(role)
                }

                is ApiResult.Error -> {
                    _uiState.value = UiState.Error(roleResult.message)
                }
            }
        }
    }

    fun onAuthorizationError(message: String) {
        _uiState.value = UiState.Error(message)
    }
}
