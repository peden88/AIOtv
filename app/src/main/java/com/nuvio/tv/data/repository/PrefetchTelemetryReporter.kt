package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.aio.AioTvServerConfig
import com.nuvio.tv.data.local.AioTvAuthStore
import com.nuvio.tv.data.remote.api.AioTvApi
import com.nuvio.tv.data.remote.dto.AioTvPrefetchEventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefetchTelemetryReporter @Inject constructor(
    private val api: AioTvApi,
    private val authStore: AioTvAuthStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingEvents = Channel<PendingEvent>(capacity = 64)

    init {
        scope.launch {
            for (pending in pendingEvents) {
                deliver(pending)
            }
        }
    }

    fun report(event: AioTvPrefetchEventRequest) {
        val session = authStore.load() ?: return
        if (pendingEvents.trySend(PendingEvent(session.accessToken, event)).isFailure) {
            Log.d(TAG, "Prefetch telemetry queue is full; dropping ${event.stage}")
        }
    }

    private suspend fun deliver(pending: PendingEvent) {
        val baseUrl = AioTvServerConfig.BASE_URL.trim().trimEnd('/')
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) return
        runCatching {
            val response = api.reportPrefetchEvent(
                url = "$baseUrl/api/v1/device/prefetch-events",
                authorization = "Bearer ${pending.accessToken}",
                body = pending.event
            )
            if (!response.isSuccessful) {
                Log.d(TAG, "Prefetch telemetry rejected with HTTP ${response.code()}")
            }
        }.onFailure { error ->
            Log.d(TAG, "Prefetch telemetry unavailable: ${error.message}")
        }
    }

    private data class PendingEvent(
        val accessToken: String,
        val event: AioTvPrefetchEventRequest
    )

    private companion object {
        const val TAG = "PrefetchTelemetry"
    }
}
