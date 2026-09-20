package com.cutm.TeamPulse.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: KeystoreRecoveryManager,
) : TokenManager {

    private val prefs: SharedPreferences by lazy {
        val masterKey = recoveryManager.getMasterKey()
        
        if (masterKey != null) {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } else {
            // CRITICAL FALLBACK: Keystore completely broken, use plain SharedPreferences
            Log.e(TAG, "!!! SECURITY WARNING: Using plain SharedPreferences due to Keystore failure !!!")
            Log.e(TAG, "!!! Access tokens will NOT be encrypted on this device !!!")
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

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
        const val TAG = "TokenManagerImpl"
        const val PREFS_NAME = "teampulse_auth_prefs"
        const val PREFS_NAME_FALLBACK = "teampulse_auth_prefs_plain"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry"
    }
}
