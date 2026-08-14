package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    fun observeSession(): Flow<UserSession?>

    suspend fun saveSession(session: UserSession)

    suspend fun signOut()

    suspend fun refreshTokenIfNeeded(): ApiResult<Unit>
}
