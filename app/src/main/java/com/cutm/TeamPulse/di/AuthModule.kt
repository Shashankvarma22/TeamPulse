package com.cutm.TeamPulse.di

import com.cutm.TeamPulse.core.auth.AuthorizationManager
import com.cutm.TeamPulse.core.auth.AuthorizationManagerImpl
import com.cutm.TeamPulse.core.auth.GoogleAuthClient
import com.cutm.TeamPulse.core.auth.GoogleAuthClientImpl
import com.cutm.TeamPulse.core.auth.TokenManager
import com.cutm.TeamPulse.core.auth.TokenManagerImpl
import com.cutm.TeamPulse.core.sheets.SheetsProbeReader
import com.cutm.TeamPulse.core.sheets.SheetsProbeReaderImpl
import com.cutm.TeamPulse.data.repository.AuthRepositoryImpl
import com.cutm.TeamPulse.data.repository.ProjectRepositoryImpl
import com.cutm.TeamPulse.data.repository.SyncRepositoryImpl
import com.cutm.TeamPulse.data.repository.TaskRepositoryImpl
import com.cutm.TeamPulse.data.repository.TeamRepositoryImpl
import com.cutm.TeamPulse.data.repository.UserRegistryRepositoryImpl
import com.cutm.TeamPulse.domain.repository.AuthRepository
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import com.cutm.TeamPulse.domain.repository.SyncRepository
import com.cutm.TeamPulse.domain.repository.TaskRepository
import com.cutm.TeamPulse.domain.repository.TeamRepository
import com.cutm.TeamPulse.domain.repository.UserRegistryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: TokenManagerImpl): TokenManager

    @Binds
    @Singleton
    abstract fun bindGoogleAuthClient(impl: GoogleAuthClientImpl): GoogleAuthClient

    @Binds
    @Singleton
    abstract fun bindAuthorizationManager(impl: AuthorizationManagerImpl): AuthorizationManager

    @Binds
    @Singleton
    abstract fun bindSheetsProbeReader(impl: SheetsProbeReaderImpl): SheetsProbeReader

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRegistryRepository(impl: UserRegistryRepositoryImpl): UserRegistryRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: TeamRepositoryImpl): TeamRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}
