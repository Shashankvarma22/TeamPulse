package com.cutm.TeamPulse.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
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
            try {
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: AEADBadTagException) {
                // Keystore key exists but can't decrypt previously-encrypted prefs
                Log.e(TAG, "!!! AEADBadTagException during EncryptedSharedPreferences.create() !!!")
                Log.e(TAG, "!!! Keystore key exists but can't decrypt previously-encrypted data !!!")
                Log.e(TAG, "!!! Exception: ${"$"}{e.javaClass.simpleName} - ${"$"}{e.message}", e)
                
                // Delete corrupted prefs file
                recoveryManager.deleteEncryptedSharedPrefs(PREFS_NAME)
                
                // Fallback to plain SharedPreferences
                Log.e(TAG, "!!! SECURITY WARNING: Using plain SharedPreferences due to Keystore failure !!!")
                Log.e(TAG, "!!! Access tokens will NOT be encrypted on this device !!!")
                context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
            } catch (e: GeneralSecurityException) {
                // Other Keystore-related failures
                Log.e(TAG, "!!! GeneralSecurityException during EncryptedSharedPreferences.create() !!!")
                Log.e(TAG, "!!! Exception: ${"$"}{e.javaClass.simpleName} - ${"$"}{e.message}", e)
                
                // Delete corrupted prefs file
                recoveryManager.deleteEncryptedSharedPrefs(PREFS_NAME)
                
                // Fallback to plain SharedPreferences
                Log.e(TAG, "!!! SECURITY WARNING: Using plain SharedPreferences due to Keystore failure !!!")
                Log.e(TAG, "!!! Access tokens will NOT be encrypted on this device !!!")
                context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
            }
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
