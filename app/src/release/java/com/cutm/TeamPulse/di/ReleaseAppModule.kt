package com.cutm.TeamPulse.di

import com.cutm.TeamPulse.ui.debug.DebugMenuProvider
import com.cutm.TeamPulse.ui.debug.DebugMenuProviderNoOp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReleaseAppModule {
    
    @Provides
    @Singleton
    fun provideDebugMenuProvider(): DebugMenuProvider = DebugMenuProviderNoOp()
}
