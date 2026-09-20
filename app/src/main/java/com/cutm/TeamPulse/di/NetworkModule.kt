package com.cutm.TeamPulse.di

import com.cutm.TeamPulse.core.config.SheetsConfig
import com.cutm.TeamPulse.core.network.AuthInterceptor
import com.cutm.TeamPulse.core.network.QuotaBackoffInterceptor
import com.cutm.TeamPulse.data.remote.SheetsApiService
import com.cutm.TeamPulse.data.remote.UserRoleApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        quotaBackoffInterceptor: QuotaBackoffInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(quotaBackoffInterceptor)
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @SheetsRetrofit
    fun provideSheetsRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SheetsConfig.SHEETS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideSheetsApiService(@SheetsRetrofit retrofit: Retrofit): SheetsApiService {
        return retrofit.create(SheetsApiService::class.java)
    }

    @Provides
    @Singleton
    @WorkerRetrofit
    fun provideWorkerRetrofit(moshi: Moshi): Retrofit {
        // Cloudflare Worker for getUserRole does NOT use AuthInterceptor (service account token).
        // Authentication is via Google ID token passed in Authorization header per-request.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(SheetsConfig.CLOUD_FUNCTIONS_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideUserRoleApiService(@WorkerRetrofit retrofit: Retrofit): UserRoleApiService {
        return retrofit.create(UserRoleApiService::class.java)
    }

    // Placeholder base URL until Google API service interfaces are added.
    private const val BASE_URL = "https://placeholder.invalid/"
    private const val TIMEOUT_SECONDS = 30L
}
