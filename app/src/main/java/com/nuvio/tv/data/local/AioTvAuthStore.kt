package com.nuvio.tv.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AioTvAuthStore @Inject constructor(
    @ApplicationContext context: Context
) {
    data class Session(
        val accessToken: String,
        val uuid: String,
        val expiresAtEpochMs: Long,
        val bootstrapEtag: String?,
        val policyRevision: Int
    ) {
        val isExpired: Boolean
            get() = expiresAtEpochMs <= System.currentTimeMillis()
    }

    companion object {
        private const val PREFS = "aio_tv_managed_session"
        private const val KEY_ALIAS = "aio_tv_device_token_v1"
        private const val TOKEN = "token_ciphertext"
        private const val UUID = "uuid"
        private const val EXPIRES_AT = "expires_at"
        private const val BOOTSTRAP_ETAG = "bootstrap_etag"
        private const val POLICY_REVISION = "policy_revision"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): Session? {
        val encrypted = prefs.getString(TOKEN, null) ?: return null
        val uuid = prefs.getString(UUID, null)?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = prefs.getLong(EXPIRES_AT, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            clear()
            return null
        }

        return try {
            Session(
                accessToken = decrypt(encrypted),
                uuid = uuid,
                expiresAtEpochMs = expiresAt,
                bootstrapEtag = prefs.getString(BOOTSTRAP_ETAG, null),
                policyRevision = prefs.getInt(POLICY_REVISION, 0)
            )
        } catch (_: Exception) {
            // A restored backup, lock-screen reset, or Keystore invalidation can
            // make the ciphertext undecryptable. Treat that as a logged-out TV.
            clear()
            null
        }
    }

    @Synchronized
    fun saveSession(accessToken: String, uuid: String, expiresInSeconds: Int) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(1) * 1000L
        prefs.edit()
            .putString(TOKEN, encrypt(accessToken))
            .putString(UUID, uuid)
            .putLong(EXPIRES_AT, expiresAt)
            .remove(BOOTSTRAP_ETAG)
            .putInt(POLICY_REVISION, 0)
            .apply()
    }

    @Synchronized
    fun saveBootstrapMetadata(etag: String?, policyRevision: Int) {
        val editor = prefs.edit()
        if (etag.isNullOrBlank()) {
            editor.remove(BOOTSTRAP_ETAG)
        } else {
            editor.putString(BOOTSTRAP_ETAG, etag)
        }
        editor.putInt(POLICY_REVISION, policyRevision).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted token" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }
}
