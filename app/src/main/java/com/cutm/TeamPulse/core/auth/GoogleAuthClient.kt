package com.cutm.TeamPulse.core.auth

import android.app.Activity
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.model.UserSession

interface GoogleAuthClient {

    suspend fun signIn(activity: Activity): ApiResult<UserSession>

    suspend fun signOut()

    suspend fun refreshTokenIfNeeded(): ApiResult<Unit>
}
