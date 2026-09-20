package com.cutm.TeamPulse.di

import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing TeamPulseDatabase from Application.onCreate()
 * without triggering eager field injection.
 * 
 * This allows database warmup to happen at the right time (in onCreate())
 * rather than during Hilt's pre-onCreate() field injection phase.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): TeamPulseDatabase
}
