package com.cutm.TeamPulse.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManagerImpl @Inject constructor(
    @ApplicationContext context: Context,
) : TokenManager {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun saveAccessToken(token: String, expiresAtMillis: Long?) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            if (expiresAtMillis != null) {
                putLong(KEY_ACCESS_TOKEN_EXPIRY, expiresAtMillis)
            } else {
                remove(KEY_ACCESS_TOKEN_EXPIRY)
            }
        }.apply()
    }

    override fun getAccessTokenExpiry(): Long? {
        return if (prefs.contains(KEY_ACCESS_TOKEN_EXPIRY)) {
            prefs.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L)
        } else {
            null
        }
    }

    override fun clearTokens() {
        prefs.edit().clear().apply()
    }

    override fun hasStoredToken(): Boolean {
        return getAccessToken() != null
    }

    private companion object {
        const val PREFS_NAME = "teampulse_auth_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry"
    }
}
