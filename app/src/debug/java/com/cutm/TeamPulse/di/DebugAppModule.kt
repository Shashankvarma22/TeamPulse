package com.cutm.TeamPulse.di

import com.cutm.TeamPulse.ui.debug.DebugMenuProvider
import com.cutm.TeamPulse.ui.debug.DebugMenuProviderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugAppModule {
    
    @Provides
    @Singleton
    fun provideDebugMenuProvider(): DebugMenuProvider = DebugMenuProviderImpl()
}
