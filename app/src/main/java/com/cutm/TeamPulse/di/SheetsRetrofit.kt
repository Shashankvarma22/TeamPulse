package com.cutm.TeamPulse.di

import javax.inject.Qualifier

/** Qualifies the Retrofit/service instances that talk to sheets.googleapis.com. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SheetsRetrofit
