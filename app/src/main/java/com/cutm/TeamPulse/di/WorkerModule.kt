package com.cutm.TeamPulse.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager workers are provided via @HiltWorker and HiltWorkerFactory
 * configured in [com.cutm.TeamPulse.TeamPulseApplication].
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule
