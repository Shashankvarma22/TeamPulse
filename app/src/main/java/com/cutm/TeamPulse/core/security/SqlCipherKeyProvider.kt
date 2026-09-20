package com.cutm.TeamPulse.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher passphrase derived from a randomly generated key
 * stored in EncryptedSharedPreferences (if Keystore is available) or
 * in plain SharedPreferences (if Keystore has failed permanently).
 *
 * Once the fallback path is taken, it persists permanently for the install —
 * even if hardware Keystore recovers later, we continue using the fallback
 * passphrase to avoid database key mismatch.
 */
@Singleton
class SqlCipherKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: KeystoreRecoveryManager,
) {

    fun getPassphrase(): ByteArray {
        // Check if we've already fallen back to unencrypted passphrase
        val plainPrefs = context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        val wasAlreadyFallback = plainPrefs.getBoolean(KEY_IS_USING_FALLBACK, false)
        
        if (wasAlreadyFallback) {
            // Once we've fallen back, stay in fallback forever
            Log.w(TAG, "SQLCipher passphrase: Using persisted fallback (unencrypted)")
            recoveryManager.setUsingFallbackPassphrase(true)
            return getOrCreateFallbackPassphrase()
        }
        
        // Try to use hardware-backed (or software-backed) Keystore
        val masterKey = recoveryManager.getMasterKey()
        
        if (masterKey != null) {
            // PRIMARY PATH: Keystore is working (hardware or software-backed)
            Log.d(TAG, "SQLCipher passphrase: Using encrypted passphrase via Keystore")
            try {
                return getOrCreateEncryptedPassphrase(masterKey)
            } catch (e: AEADBadTagException) {
                // CRITICAL: EncryptedSharedPreferences couldn't decrypt existing prefs with this key
                // (key changed, corrupted, or restored from backup with wrong key)
                Log.e(TAG, "!!! AEADBadTagException during EncryptedSharedPreferences.create() !!!")
                Log.e(TAG, "!!! Keystore key exists but can't decrypt previously-encrypted data !!!")
                Log.e(TAG, "!!! This indicates Keystore corruption or key mismatch !!!")
                Log.e(TAG, "!!! Exception: ${"$"}{e.javaClass.simpleName} - ${"$"}{e.message}", e)
                
                // Delete the corrupted prefs file so it doesn't become a permanent orphan
                recoveryManager.deleteEncryptedSharedPrefs(PREFS_NAME)
                recoveryManager.deleteDatabaseFile()
                
                // Fall back to unencrypted storage
                Log.e(TAG, "!!! Switching to permanent fallback passphrase !!!")
                plainPrefs.edit().putBoolean(KEY_IS_USING_FALLBACK, true).apply()
                recoveryManager.setUsingFallbackPassphrase(true)
                
                return getOrCreateFallbackPassphrase()
            } catch (e: GeneralSecurityException) {
                // Broader catch for other Keystore-related failures
                Log.e(TAG, "!!! GeneralSecurityException during EncryptedSharedPreferences.create() !!!")
                Log.e(TAG, "!!! Exception: ${"$"}{e.javaClass.simpleName} - ${"$"}{e.message}", e)
                
                // Delete the corrupted prefs file
                recoveryManager.deleteEncryptedSharedPrefs(PREFS_NAME)
                recoveryManager.deleteDatabaseFile()
                
                // Fall back to unencrypted storage
                Log.e(TAG, "!!! Switching to permanent fallback passphrase !!!")
                plainPrefs.edit().putBoolean(KEY_IS_USING_FALLBACK, true).apply()
                recoveryManager.setUsingFallbackPassphrase(true)
                
                return getOrCreateFallbackPassphrase()
            }
        } else {
            // FALLBACK PATH: Keystore completely failed
            Log.e(TAG, "!!! SQLCipher FALLBACK TRIGGERED !!!")
            Log.e(TAG, "!!! Keystore initialization failed after full recovery attempt !!!")
            Log.e(TAG, "!!! Switching to unencrypted passphrase fallback !!!")
            Log.e(TAG, "!!! This device's secure storage failed and can't be repaired automatically !!!")
            Log.e(TAG, "!!! Database passphrase will be stored unencrypted !!!")
            
            // Mark as permanently in fallback mode
            plainPrefs.edit().putBoolean(KEY_IS_USING_FALLBACK, true).apply()
            recoveryManager.setUsingFallbackPassphrase(true)
            
            return getOrCreateFallbackPassphrase()
        }
    }

    private fun getOrCreateEncryptedPassphrase(masterKey: androidx.security.crypto.MasterKey): ByteArray {
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val passphrase = ByteArray(PASSPHRASE_LENGTH).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun getOrCreateFallbackPassphrase(): ByteArray {
        val plainPrefs = context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)

        val existing = plainPrefs.getString(KEY_DB_PASSPHRASE_FALLBACK, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val passphrase = ByteArray(PASSPHRASE_LENGTH).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }
        plainPrefs.edit()
            .putString(KEY_DB_PASSPHRASE_FALLBACK, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private companion object {
        const val TAG = "SqlCipherKeyProvider"
        const val PREFS_NAME = "teampulse_db_key_prefs"
        const val PREFS_NAME_FALLBACK = "teampulse_db_key_prefs_fallback"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_DB_PASSPHRASE_FALLBACK = "db_passphrase_fallback"
        const val KEY_IS_USING_FALLBACK = "is_using_fallback"
        const val PASSPHRASE_LENGTH = 32
    }
}

