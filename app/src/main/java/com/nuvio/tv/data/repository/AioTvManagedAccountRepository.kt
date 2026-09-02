package com.nuvio.tv.data.repository

import android.net.Uri
import android.os.Build
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.aio.AioTvServerConfig
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.AioTvAuthStore
import com.nuvio.tv.data.remote.api.AioTvApi
import com.nuvio.tv.data.remote.dto.AioTvBootstrapData
import com.nuvio.tv.data.remote.dto.AioTvDeviceStartData
import com.nuvio.tv.data.remote.dto.AioTvDeviceTokenRequest
import com.nuvio.tv.data.remote.dto.AioTvManagedAddon
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AioTvManagedAccountRepository @Inject constructor(
    private val api: AioTvApi,
    private val authStore: AioTvAuthStore,
    private val addonRepository: AddonRepository
) {
    sealed interface TokenPollResult {
        data class Pending(val intervalSeconds: Int) : TokenPollResult
        data class Approved(val session: AioTvAuthStore.Session) : TokenPollResult
        data object Expired : TokenPollResult
        data class Failed(val message: String) : TokenPollResult
    }

    sealed interface BootstrapResult {
        data class Ready(val data: AioTvBootstrapData) : BootstrapResult
        data class Current(val policyRevision: Int) : BootstrapResult
        data class OfflineReady(val policyRevision: Int) : BootstrapResult
        data object NoSession : BootstrapResult
        data object Revoked : BootstrapResult
        data class Failed(val message: String) : BootstrapResult
    }

    private val baseUrl: String
        get() = AioTvServerConfig.BASE_URL.trim().trimEnd('/')

    val isServerConfigured: Boolean
        get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    suspend fun startPairing(): Result<AioTvDeviceStartData> = withContext(Dispatchers.IO) {
        if (!isServerConfigured) {
            return@withContext Result.failure(
                IllegalStateException("AIOtv Control URL is not configured in this build")
            )
        }
        runCatching {
            val response = api.startPairing(
                "$baseUrl/api/v1/pairings",
                mapOf(
                    "deviceName" to listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android TV" },
                    "platform" to "android_tv",
                    "appVersion" to BuildConfig.VERSION_NAME
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body?.success != true || body.data == null) {
                throw IllegalStateException(
                    body?.error?.message ?: "Pairing request failed (HTTP ${response.code()})"
                )
            }

            body.data
        }
    }

    suspend fun pollToken(deviceCode: String): TokenPollResult = withContext(Dispatchers.IO) {
        if (!isServerConfigured) return@withContext TokenPollResult.Failed("AIOtv Control is not configured")
        try {
            val response = api.pollPairing(
                "$baseUrl/api/v1/pairings/token",
                AioTvDeviceTokenRequest(deviceCode)
            )
            if (response.code() == 410) return@withContext TokenPollResult.Expired
            val body = response.body()
            if (!response.isSuccessful || body?.data == null) {
                return@withContext TokenPollResult.Failed(
                    body?.error?.message ?: "Pairing check failed (HTTP ${response.code()})"
                )
            }
            when (body.data.status.lowercase()) {
                "pending" -> TokenPollResult.Pending(body.data.interval ?: 3)
                "approved" -> {
                    val token = body.data.accessToken
                    val deviceId = body.data.deviceId
                    if (token.isNullOrBlank() || deviceId.isNullOrBlank()) {
                        TokenPollResult.Failed("AIOtv Control returned an incomplete device session")
                    } else {
                        authStore.saveSession(token, deviceId)
                        TokenPollResult.Approved(authStore.load()!!)
                    }
                }
                "expired", "consumed" -> TokenPollResult.Expired
                else -> TokenPollResult.Failed("Unexpected pairing state: ${body.data.status}")
            }
        } catch (error: Exception) {
            TokenPollResult.Failed(error.message ?: "Unable to contact AIOtv Control")
        }
    }

    suspend fun restoreAndBootstrap(): BootstrapResult = withContext(Dispatchers.IO) {
        val session = authStore.load() ?: return@withContext BootstrapResult.NoSession
        val result = bootstrapAndReconcile(session)
        if (result is BootstrapResult.Failed && authStore.canUseOfflinePolicy(session)) {
            BootstrapResult.OfflineReady(session.policyRevision)
        } else {
            result
        }
    }

    suspend fun refreshIfStale(minimumIntervalMs: Long = 60_000L): BootstrapResult? =
        withContext(Dispatchers.IO) {
            val session = authStore.load() ?: return@withContext BootstrapResult.NoSession
            val lastValidatedAt = session.lastValidatedAtEpochMs
            if (lastValidatedAt > 0L &&
                System.currentTimeMillis() - lastValidatedAt < minimumIntervalMs
            ) {
                return@withContext null
            }
            val result = bootstrapAndReconcile(session)
            if (result is BootstrapResult.Failed && authStore.canUseOfflinePolicy(session)) {
                BootstrapResult.OfflineReady(session.policyRevision)
            } else {
                result
            }
        }

    suspend fun bootstrapAndReconcile(
        session: AioTvAuthStore.Session
    ): BootstrapResult = withContext(Dispatchers.IO) {
        if (!isServerConfigured) return@withContext BootstrapResult.Failed("AIOtv Control is not configured")
        try {
            val response = api.bootstrap(
                "$baseUrl/api/v1/device/bootstrap",
                "Bearer ${session.accessToken}",
                session.bootstrapEtag
            )
            if (response.code() == 401 || response.code() == 403) {
                authStore.clear()
                return@withContext BootstrapResult.Revoked
            }
            if (response.code() == 304) {
                authStore.saveBootstrapMetadata(session.bootstrapEtag, session.policyRevision)
                return@withContext BootstrapResult.Current(session.policyRevision)
            }
            val envelope = response.body()
            val data = envelope?.data
            if (!response.isSuccessful || envelope?.success != true || data == null) {
                return@withContext BootstrapResult.Failed(
                    envelope?.error?.message ?: "AIOtv bootstrap failed (HTTP ${response.code()})"
                )
            }
            if (data.device.id != session.deviceId) {
                authStore.clear()
                return@withContext BootstrapResult.Revoked
            }

            reconcileManagedAddons(data.policy.addons)
            authStore.saveBootstrapMetadata(response.headers()["ETag"], data.policy.revision)
            BootstrapResult.Ready(data)
        } catch (error: Exception) {
            BootstrapResult.Failed(error.message ?: "Unable to initialise managed AIOtv account")
        }
    }

    fun clearSession() = authStore.clear()

    private suspend fun reconcileManagedAddons(assignments: List<AioTvManagedAddon>) {
        val desired = LinkedHashMap<String, AioTvManagedAddon>()
        assignments.forEach { assignment ->
            desired.putIfAbsent(canonicalizeAddonUrl(assignment.manifestUrl), assignment)
        }

        val installed = addonRepository.getInstalledAddons().first()
        val installedByCanonical = installed.associateBy { canonicalizeAddonUrl(it.baseUrl) }

        // Additions are validated before changing membership so a temporary
        // manifest outage never causes us to first delete a working addon set.
        for ((canonical, assignment) in desired) {
            if (installedByCanonical.containsKey(canonical)) {
                addonRepository.setAddonEnabled(installedByCanonical.getValue(canonical).baseUrl, true)
                continue
            }
            when (val fetched = addonRepository.fetchAddon(assignment.manifestUrl)) {
                is NetworkResult.Success -> {
                    addonRepository.addAddon(assignment.manifestUrl)
                    addonRepository.setAddonEnabled(assignment.manifestUrl, true)
                }
                is NetworkResult.Error -> throw IllegalStateException(
                    "Could not load managed addon ${assignment.name.ifBlank { assignment.manifestUrl }}: ${fetched.message}"
                )
                NetworkResult.Loading -> throw IllegalStateException(
                    "Managed addon ${assignment.name.ifBlank { assignment.manifestUrl }} is still loading"
                )
            }
        }

        // Remove every local addon not present in the administrator policy.
        // We intentionally do not call setAddonOrder(); existing assigned
        // addons retain their local order and catalog-order preferences.
        installed.forEach { addon ->
            if (!desired.containsKey(canonicalizeAddonUrl(addon.baseUrl))) {
                addonRepository.removeAddon(addon.baseUrl)
            }
        }
    }

    private fun canonicalizeAddonUrl(url: String): String {
        val parsed = Uri.parse(url.trim())
        val path = parsed.encodedPath.orEmpty().trimEnd('/')
        val cleanPath = if (path.endsWith("/manifest.json", ignoreCase = true)) {
            path.dropLast("/manifest.json".length).trimEnd('/')
        } else {
            path.trimEnd('/')
        }
        val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
        val scheme = parsed.scheme.orEmpty().lowercase()
        val authority = parsed.encodedAuthority.orEmpty().lowercase()
        return "$scheme://$authority$cleanPath$query"
    }
}
