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
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userSessionDao: UserSessionDao,
    private val googleAuthClient: GoogleAuthClient,
) : AuthRepository {

    init {
        android.util.Log.d("AuthRepository", "AuthRepositoryImpl created (new process? check timestamp)")
    }

    override fun observeSession(): Flow<UserSession?> {
        return userSessionDao.observeCurrentSession()
            .onEach { entity ->
                android.util.Log.d("AuthRepository", "observeCurrentSession emitted: ${if (entity == null) "null" else "session for ${entity.email}, lastSignIn=${entity.lastSignInAt}"}")
            }
            .map { entity ->
                entity?.toDomain()
            }
    }

    override suspend fun saveSession(session: UserSession) {
        android.util.Log.d("AuthRepository", "Saving session: email=${session.email}, role=${session.role}, timestamp=${session.lastSignInAt}")
        userSessionDao.upsert(session.toEntity())
        android.util.Log.d("AuthRepository", "Session saved to database")
    }

    override suspend fun signOut() {
        android.util.Log.d("AuthRepository", "signOut() called - clearing session and credentials")
        googleAuthClient.signOut()
        userSessionDao.clear()
        android.util.Log.d("AuthRepository", "signOut() complete")
    }

    override suspend fun refreshTokenIfNeeded(): ApiResult<Unit> {
        return googleAuthClient.refreshTokenIfNeeded()
    }
}
