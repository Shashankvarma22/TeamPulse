package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.core.auth.GoogleAuthClient
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.local.dao.UserSessionDao
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.data.mapper.toEntity
import com.cutm.TeamPulse.domain.model.UserSession
import com.cutm.TeamPulse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userSessionDao: UserSessionDao,
    private val googleAuthClient: GoogleAuthClient,
) : AuthRepository {

    override fun observeSession(): Flow<UserSession?> {
        return userSessionDao.observeCurrentSession().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun saveSession(session: UserSession) {
        userSessionDao.upsert(session.toEntity())
    }

    override suspend fun signOut() {
        googleAuthClient.signOut()
        userSessionDao.clear()
    }

    override suspend fun refreshTokenIfNeeded(): ApiResult<Unit> {
        return googleAuthClient.refreshTokenIfNeeded()
    }
}
