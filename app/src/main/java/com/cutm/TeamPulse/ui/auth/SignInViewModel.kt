package com.cutm.TeamPulse.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.core.auth.GoogleAuthClient
import com.cutm.TeamPulse.core.auth.TokenManager
import com.cutm.TeamPulse.core.dispatchers.DispatcherProvider
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.core.sheets.SheetsProbeReader
import com.cutm.TeamPulse.domain.model.UserSession
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
    private val dispatcherProvider: DispatcherProvider,
    private val tokenManager: TokenManager,
    private val sheetsProbeReader: SheetsProbeReader,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<UiState<UserSession>>(UiState.Idle)

    val uiState: StateFlow<UiState<UserSession>> =
        _uiState.asStateFlow()

    private val _requestSheetsAuthorization =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val requestSheetsAuthorization: SharedFlow<Unit> =
        _requestSheetsAuthorization.asSharedFlow()

    private val _sheetsProof =
        MutableSharedFlow<String>(extraBufferCapacity = 1)

    val sheetsProof: SharedFlow<String> =
        _sheetsProof.asSharedFlow()

    fun onGoogleSignInClicked(activity: Activity) {
        if (_uiState.value is UiState.Loading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val result = googleAuthClient.signIn(activity)

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = UiState.Success(result.data)
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

            tokenManager.saveAccessToken(
                token = token,
                expiresAtMillis = expiresAtMillis,
            )

            when (val result = sheetsProbeReader.readUsersRegistryRowCount()) {
                is ApiResult.Success -> {
                    _sheetsProof.tryEmit(
                        "Users Registry read successfully: ${result.data} row(s)."
                    )
                }

                is ApiResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun onAuthorizationError(message: String) {
        _uiState.value = UiState.Error(message)
    }
}
