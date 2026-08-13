package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.core.network.ApiResult

interface UserRegistryRepository {

    suspend fun lookupUser(email: String): ApiResult<SessionRole>
}
