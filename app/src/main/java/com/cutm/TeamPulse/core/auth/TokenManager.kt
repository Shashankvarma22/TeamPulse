package com.cutm.TeamPulse.core.auth

interface TokenManager {

    fun getAccessToken(): String?

    fun saveAccessToken(token: String, expiresAtMillis: Long? = null)

    fun getAccessTokenExpiry(): Long?

    fun clearTokens()

    fun hasStoredToken(): Boolean
}
