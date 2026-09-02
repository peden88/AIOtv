package com.nuvio.tv.ui.screens.account

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface AiotvPairingState {
    data object Starting : AiotvPairingState
    data class Waiting(val code: String, val expiresInSeconds: Int) : AiotvPairingState
    data class Applying(val userName: String) : AiotvPairingState
    data class Paired(val userName: String) : AiotvPairingState
    data class Error(val message: String) : AiotvPairingState
}

@HiltViewModel
class AiotvManagedPairingViewModel @Inject constructor(
    application: Application,
    private val addonRepository: AddonRepository
) : AndroidViewModel(application) {

    companion object {
        private const val BASE_URL = "https://aiocontrol.peden88.stream/api/v1"
        private const val PREFS = "aiotv_managed_pairing"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_USER = "managed_user"
        private const val POLL_DELAY_MS = 2_000L
    }

    private val client = OkHttpClient()
    private val preferences = application.getSharedPreferences(PREFS, Application.MODE_PRIVATE)
    private val _state = MutableStateFlow<AiotvPairingState>(AiotvPairingState.Starting)
    val state: StateFlow<AiotvPairingState> = _state.asStateFlow()

    private val deviceId: String by lazy {
        preferences.getString(KEY_DEVICE_ID, null)
            ?: buildDeviceId().also { preferences.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    init {
        start()
    }

    fun retry() {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            _state.value = AiotvPairingState.Starting
            runCatching {
                requestPairing()
            }.onFailure {
                _state.value = AiotvPairingState.Error(it.message ?: "Unable to contact AIOtv Control")
            }
        }
    }

    private suspend fun requestPairing() = withContext(Dispatchers.IO) {
        val body = """{"device_id":"${escapeJson(deviceId)}","device_name":"AIOtv"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/pairing/request")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Pairing request failed (${response.code})")
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val code = json.get("pairing_code")?.asString ?: error("Control server returned no pairing code")
            val token = json.get("device_token")?.asString ?: error("Control server returned no device token")
            val expiresIn = json.get("expires_in")?.asInt ?: 900
            preferences.edit().putString(KEY_TOKEN, token).apply()
            _state.value = AiotvPairingState.Waiting(code, expiresIn)
            pollPairing(code, token)
        }
    }

    private suspend fun pollPairing(code: String, token: String) {
        while (true) {
            delay(POLL_DELAY_MS)
            val status = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$BASE_URL/pairing/status/$code?device_id=${java.net.URLEncoder.encode(deviceId, "UTF-8")}")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Pairing status failed (${response.code})")
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }
            }

            when (status.get("status")?.asString) {
                "pending" -> Unit
                "expired" -> {
                    _state.value = AiotvPairingState.Error("Pairing code expired. Select Retry for a new code.")
                    return
                }
                "paired" -> {
                    val userName = status.get("user")?.asString ?: "Managed user"
                    _state.value = AiotvPairingState.Applying(userName)
                    applyManagedConfiguration(token, userName)
                    return
                }
                else -> error("Unexpected pairing response")
            }
        }
    }

    private suspend fun applyManagedConfiguration(token: String, userName: String) {
        val config = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/device/config?device_id=${java.net.URLEncoder.encode(deviceId, "UTF-8")}")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Managed configuration failed (${response.code})")
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            }
        }

        val managedUrls = config.getAsJsonArray("addons")
            ?.mapNotNull { element ->
                element.asJsonObject.get("manifest_url")?.asString?.takeIf { it.isNotBlank() }
            }
            .orEmpty()

        val existing = addonRepository.getInstalledAddons().first()
        existing.forEach { addon -> addonRepository.removeAddon(addon.baseUrl) }
        managedUrls.forEach { url -> addonRepository.addAddon(url) }
        addonRepository.setAddonOrder(managedUrls)

        preferences.edit()
            .putString(KEY_USER, userName)
            .putString(KEY_TOKEN, token)
            .apply()
        _state.value = AiotvPairingState.Paired(userName)
    }

    private fun buildDeviceId(): String {
        val androidId = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId?.takeIf { it.isNotBlank() }?.let { "android-$it" }
            ?: "android-${UUID.randomUUID()}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
