package com.cutm.TeamPulse.core.auth

import android.app.Activity
import com.cutm.TeamPulse.core.network.ApiResult

interface GoogleAuthClient {

    suspend fun signIn(activity: Activity): ApiResult<GoogleIdentity>

    suspend fun signOut()

    suspend fun refreshTokenIfNeeded(): ApiResult<Unit>
}
