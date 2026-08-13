package com.cutm.TeamPulse.core.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher passphrase derived from a randomly generated key
 * stored in EncryptedSharedPreferences. Android Keystore backs the
 * MasterKey used to encrypt the preferences.
 */
@Singleton
class SqlCipherKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) {

    fun getPassphrase(): ByteArray {
        keystoreManager.getOrCreateSecretKey()

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

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

    private companion object {
        const val PREFS_NAME = "teampulse_db_key_prefs"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val PASSPHRASE_LENGTH = 32
    }
}
