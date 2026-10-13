package com.audiocast.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed storage for the Twitch stream key.
 *
 * The key is encrypted with AES-256/GCM using a key that never leaves the
 * hardware-backed keystore (on supported devices). Only the ciphertext is
 * persisted in a private SharedPreferences file.
 *
 * The plaintext key is never written to logs, files, or analytics.
 */
class SecureKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun saveStreamKey(key: String) {
        require(key.isNotBlank()) { "Stream key must not be empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        // IV || ciphertext, base64 encoded
        val packed = cipher.iv + ciphertext
        prefs.edit()
            .putString(PREF_STREAM_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun getStreamKey(): String? {
        val packed = prefs.getString(PREF_STREAM_KEY, null) ?: return null
        val bytes = try {
            Base64.decode(packed, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (bytes.size <= GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, bytes, 0, GCM_IV_LENGTH)
            )
            String(cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasStreamKey(): Boolean = prefs.contains(PREF_STREAM_KEY)

    fun clearStreamKey() {
        prefs.edit().remove(PREF_STREAM_KEY).apply()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "audiocast/stream_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
        const val PREFS_NAME = "audiocast_secure_prefs"
        const val PREF_STREAM_KEY = "stream_key"
    }
}
