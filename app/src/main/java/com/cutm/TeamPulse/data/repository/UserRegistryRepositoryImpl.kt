package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.domain.repository.UserRegistryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foundation stub. Users Registry Sheet lookup will be implemented in a later task.
 */
@Singleton
class UserRegistryRepositoryImpl @Inject constructor() : UserRegistryRepository {

    override suspend fun lookupUser(email: String): ApiResult<SessionRole> {
        return ApiResult.Error(
            message = "Users Registry lookup is not implemented yet.",
        )
    }
}
