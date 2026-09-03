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
        val deviceId: String,
        val bootstrapEtag: String?,
        val policyRevision: Int,
        val lastValidatedAtEpochMs: Long
    )

    companion object {
        private const val PREFS = "aio_tv_control_session_v1"
        private const val KEY_ALIAS = "aio_tv_control_device_token_v1"
        private const val TOKEN = "token_ciphertext"
        private const val DEVICE_ID = "device_id"
        private const val BOOTSTRAP_ETAG = "bootstrap_etag"
        private const val POLICY_REVISION = "policy_revision"
        private const val LAST_VALIDATED_AT = "last_validated_at"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val OFFLINE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): Session? {
        val encrypted = prefs.getString(TOKEN, null) ?: return null
        val deviceId = prefs.getString(DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null

        return try {
            Session(
                accessToken = decrypt(encrypted),
                deviceId = deviceId,
                bootstrapEtag = prefs.getString(BOOTSTRAP_ETAG, null),
                policyRevision = prefs.getInt(POLICY_REVISION, 0),
                lastValidatedAtEpochMs = prefs.getLong(LAST_VALIDATED_AT, 0L)
            )
        } catch (_: Exception) {
            // A restored backup, lock-screen reset, or Keystore invalidation can
            // make the ciphertext undecryptable. Treat that as a logged-out TV.
            clear()
            null
        }
    }

    @Synchronized
    fun saveSession(accessToken: String, deviceId: String) {
        prefs.edit()
            .putString(TOKEN, encrypt(accessToken))
            .putString(DEVICE_ID, deviceId)
            .remove(BOOTSTRAP_ETAG)
            .putInt(POLICY_REVISION, 0)
            .putLong(LAST_VALIDATED_AT, 0L)
            .apply()
    }

    @Synchronized
    fun saveBootstrapMetadata(
        etag: String?,
        policyRevision: Int,
        validatedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val editor = prefs.edit()
        if (etag.isNullOrBlank()) {
            editor.remove(BOOTSTRAP_ETAG)
        } else {
            editor.putString(BOOTSTRAP_ETAG, etag)
        }
        editor
            .putInt(POLICY_REVISION, policyRevision)
            .putLong(LAST_VALIDATED_AT, validatedAtEpochMs)
            .apply()
    }

    fun canUseOfflinePolicy(session: Session, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return session.policyRevision > 0 &&
            session.lastValidatedAtEpochMs > 0L &&
            nowEpochMs - session.lastValidatedAtEpochMs <= OFFLINE_GRACE_MS
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
