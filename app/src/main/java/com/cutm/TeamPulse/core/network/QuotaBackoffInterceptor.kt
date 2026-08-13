package com.cutm.TeamPulse.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Foundation interceptor for Google Sheets API quota backoff.
 * Retry logic will be implemented when Sheets API integration is added.
 */
class QuotaBackoffInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
