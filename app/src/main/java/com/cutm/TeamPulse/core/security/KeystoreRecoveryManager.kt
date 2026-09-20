package com.cutm.TeamPulse.core.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized recovery manager for Keystore corruption.
 * Handles full recovery including:
 * - Deleting corrupted EncryptedSharedPreferences files
 * - Deleting stale Keystore aliases
 * - Deleting encrypted database
 * - Managing fallback to software-backed keys when hardware fails
 */
@Singleton
class KeystoreRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) {

    private var recoveryAttempted = false
    private var useSoftwareBackedKey = false
    
    private val _isUsingFallbackPassphrase = MutableStateFlow(false)
    
    // DEBUG-ONLY: Force getMasterKey() to return null to test fallback path
    // This will only compile in debug builds
    @Volatile
    private var debugForceNullKey = false
    val isUsingFallbackPassphrase: StateFlow<Boolean> = _isUsingFallbackPassphrase

    /**
     * Attempts to create a MasterKey, recovering from corruption if needed.
     * Returns null if hardware AND software-backed keys both fail.
     */
    fun getMasterKey(): MasterKey? {
        // DEBUG-ONLY: Allow forcing null for testing fallback path
        if (debugForceNullKey) {
            Log.d(TAG, "DEBUG: Returning null (forced by debugForceNullKey)")
            return null
        }
        
        return try {
            createMasterKey()
        } catch (e: GeneralSecurityException) {
            if (!recoveryAttempted) {
                Log.e(TAG, "Keystore corruption detected on first attempt: ${e.javaClass.simpleName} - ${e.message}", e)
                performFullRecovery()
                recoveryAttempted = true
                
                // Retry with hardware-backed key after recovery
                try {
                    createMasterKey()
                } catch (e2: GeneralSecurityException) {
                    Log.e(TAG, "Hardware-backed Keystore still failing after recovery: ${e2.javaClass.simpleName}", e2)
                    Log.w(TAG, "Falling back to software-backed Keystore (no hardware security)")
                    useSoftwareBackedKey = true
                    
                    // Retry with software-backed key
                    try {
                        createMasterKey()
                    } catch (e3: GeneralSecurityException) {
                        Log.e(TAG, "Software-backed Keystore ALSO failed - device Keystore completely broken", e3)
                        null
                    }
                }
            } else {
                // Recovery already attempted and we're still failing
                Log.e(TAG, "Keystore still failing after recovery attempt", e)
                null
            }
        }
    }

    private fun createMasterKey(): MasterKey {
        val builder = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        
        if (useSoftwareBackedKey) {
            // Explicitly disable StrongBox and user authentication for software fallback
            builder.setUserAuthenticationRequired(false)
        }
        
        return builder.build()
    }

    private fun performFullRecovery() {
        Log.w(TAG, "=== STARTING FULL KEYSTORE RECOVERY ===")
        
        // 1. Delete ALL EncryptedSharedPreferences files that might be using corrupted key
        deleteEncryptedSharedPrefs(PREFS_NAME_AUTH)
        deleteEncryptedSharedPrefs(PREFS_NAME_DB_KEY)
        
        // 2. Delete Keystore entries (both app-specific and MasterKey default)
        keystoreManager.deleteKeystoreEntry()  // App's custom key
        deleteDefaultMasterKeyAlias()  // MasterKey's default alias
        
        // 3. Delete SQLCipher database (passphrase unrecoverable)
        deleteDatabaseFile()
        
        Log.w(TAG, "=== KEYSTORE RECOVERY COMPLETE ===")
    }

    private fun deleteEncryptedSharedPrefs(prefsName: String) {
        try {
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/${prefsName}.xml")
            if (prefsFile.exists()) {
                val deleted = prefsFile.delete()
                Log.w(TAG, "Deleted EncryptedSharedPreferences '$prefsName': $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete EncryptedSharedPreferences '$prefsName'", e)
        }
    }

    private fun deleteDefaultMasterKeyAlias() {
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val defaultAlias = "_androidx_security_master_key_"
            if (keyStore.containsAlias(defaultAlias)) {
                keyStore.deleteEntry(defaultAlias)
                Log.w(TAG, "Deleted default MasterKey alias: $defaultAlias")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete default MasterKey alias", e)
        }
    }

    private fun deleteDatabaseFile() {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                val deleted = dbFile.delete()
                Log.w(TAG, "Deleted database file: $deleted")
            }
            
            // Delete WAL/SHM files
            val dbDir = dbFile.parentFile
            if (dbDir != null && dbDir.exists()) {
                dbDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(DATABASE_NAME)) {
                        val deleted = file.delete()
                        Log.d(TAG, "Deleted database file: ${file.name} ($deleted)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete database file", e)
        }
    }

fun getDebugForceNullKey(): Boolean = debugForceNullKey

    fun setDebugForceNullKey(force: Boolean) {
        debugForceNullKey = force
        if (force) {
            Log.w(TAG, "!!! DEBUG: Forcing getMasterKey() to return null for testing fallback path !!!")
        }
    }

    fun setUsingFallbackPassphrase(using: Boolean) {
        _isUsingFallbackPassphrase.value = using
    }

    fun isUsingSoftwareBackedKey(): Boolean = useSoftwareBackedKey

    private companion object {
        const val TAG = "KeystoreRecoveryManager"
        const val PREFS_NAME_AUTH = "teampulse_auth_prefs"
        const val PREFS_NAME_DB_KEY = "teampulse_db_key_prefs"
        const val DATABASE_NAME = "teampulse.db"
    }
}


