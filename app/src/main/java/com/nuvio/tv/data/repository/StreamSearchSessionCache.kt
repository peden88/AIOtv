package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.AddonStreams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class StreamSearchRequestKey(
    val profileId: Int,
    val type: String,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val sourceConfiguration: String
)

internal data class StreamPrefetchCacheResult(
    val requestId: String,
    val cacheHit: Boolean,
    val addonCount: Int,
    val streamCount: Int,
    val durationMs: Long,
    val errorMessage: String?
)

internal data class StreamPrefetchConsumption(
    val requestId: String,
    val addonCount: Int,
    val streamCount: Int,
    val ageMs: Long,
    val ready: Boolean
)

/**
 * Keeps a small set of source searches alive independently of UI collectors.
 * Completed results are memory-only and short-lived because addon URLs may expire.
 */
internal class StreamSearchSessionCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val completedTtlMs: Long = DEFAULT_COMPLETED_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val prefetchCompletedTtlMs: Long = DEFAULT_PREFETCH_COMPLETED_TTL_MS,
    private val maxPrefetchEntries: Int = DEFAULT_MAX_PREFETCH_ENTRIES,
    private val onPrefetchStarted: (
        key: StreamSearchRequestKey,
        requestId: String,
        cacheHit: Boolean
    ) -> Unit = { _, _, _ -> },
    private val onPrefetchConsumed: (
        key: StreamSearchRequestKey,
        consumption: StreamPrefetchConsumption
    ) -> Unit = { _, _ -> },
) {
    private data class Snapshot(
        val result: NetworkResult<List<AddonStreams>>,
        val version: Long,
        val isComplete: Boolean
    )

    private class Session(
        val state: MutableStateFlow<Snapshot>,
        var prefetchedOnly: Boolean,
        var prefetchRequestId: String?,
        var prefetchStartedAtMs: Long?,
    ) {
        lateinit var job: Job
        @Volatile var invalidated: Boolean = false
        var completedAtMs: Long? = null
        var lastSuccessfulResult: NetworkResult.Success<List<AddonStreams>>? = null
    }

    private val mutex = Mutex()
    private val sessions = LinkedHashMap<StreamSearchRequestKey, Session>(16, 0.75f, true)

    private data class Acquisition(
        val session: Session,
        val created: Boolean,
        val consumedPrefetch: StreamPrefetchConsumption? = null
    )

    fun observe(
        key: StreamSearchRequestKey,
        forceRefresh: Boolean,
        producer: () -> Flow<NetworkResult<List<AddonStreams>>>
    ): Flow<NetworkResult<List<AddonStreams>>> = flow {
        val acquisition = acquireSession(
            key = key,
            forceRefresh = forceRefresh,
            prefetchedOnly = false,
            prefetchRequestId = null,
            producer = producer,
        )
        val session = acquisition.session
        acquisition.consumedPrefetch?.let { consumption ->
            onPrefetchConsumed(key, consumption)
        }
        var emittedVersion = -1L
        emitAll(
            session.state.transformWhile { snapshot ->
                if (snapshot.version != emittedVersion) {
                    emittedVersion = snapshot.version
                    emit(snapshot.result)
                }
                !snapshot.isComplete
            }
        )
    }

    /** Starts a repository-owned search so a later observer can attach to it. */
    suspend fun prefetch(
        key: StreamSearchRequestKey,
        requestId: String,
        producer: () -> Flow<NetworkResult<List<AddonStreams>>>
    ): StreamPrefetchCacheResult {
        val acquisition = acquireSession(
            key = key,
            forceRefresh = false,
            prefetchedOnly = true,
            prefetchRequestId = requestId,
            producer = producer,
        )
        val session = acquisition.session
        val effectiveRequestId = session.prefetchRequestId ?: requestId
        onPrefetchStarted(key, effectiveRequestId, !acquisition.created)
        session.job.join()
        val groups = session.lastSuccessfulResult?.data.orEmpty()
        val currentResult = session.state.value.result
        val errorMessage = if (groups.isEmpty() && currentResult is NetworkResult.Error) {
            currentResult.message
        } else {
            null
        }
        val startedAt = session.prefetchStartedAtMs ?: nowMs()
        return StreamPrefetchCacheResult(
            requestId = effectiveRequestId,
            cacheHit = !acquisition.created,
            addonCount = groups.size,
            streamCount = groups.sumOf { it.streams.size },
            durationMs = (nowMs() - startedAt).coerceAtLeast(0L),
            errorMessage = errorMessage
        )
    }

    private suspend fun acquireSession(
        key: StreamSearchRequestKey,
        forceRefresh: Boolean,
        prefetchedOnly: Boolean,
        prefetchRequestId: String?,
        producer: () -> Flow<NetworkResult<List<AddonStreams>>>
    ): Acquisition {
        var created: Session? = null
        var consumedPrefetch: StreamPrefetchConsumption? = null
        val selected = mutex.withLock {
            removeExpiredLocked()
            removeObsoleteSessionsLocked(key)

            if (forceRefresh) {
                sessions.remove(key)?.cancelAndComplete()
            } else {
                sessions[key]?.let { session ->
                    if (prefetchedOnly && session.prefetchRequestId == null) {
                        session.prefetchRequestId = prefetchRequestId
                        session.prefetchStartedAtMs = nowMs()
                    }
                    if (!prefetchedOnly && session.prefetchedOnly) {
                        val groups = session.lastSuccessfulResult?.data.orEmpty()
                        consumedPrefetch = session.prefetchRequestId?.let { existingRequestId ->
                            StreamPrefetchConsumption(
                                requestId = existingRequestId,
                                addonCount = groups.size,
                                streamCount = groups.sumOf { it.streams.size },
                                ageMs = (nowMs() - (session.prefetchStartedAtMs ?: nowMs()))
                                    .coerceAtLeast(0L),
                                ready = session.completedAtMs != null && groups.isNotEmpty()
                            )
                        }
                        session.prefetchedOnly = false
                    }
                    return@withLock session
                }
            }

            createSession(key, prefetchedOnly, prefetchRequestId, producer).also { session ->
                sessions[key] = session
                trimPrefetchEntriesLocked()
                trimToSizeLocked()
                created = session
            }
        }
        withContext(NonCancellable) {
            created?.job?.start()
        }
        return Acquisition(
            session = selected,
            created = created != null,
            consumedPrefetch = consumedPrefetch
        )
    }

    private fun createSession(
        key: StreamSearchRequestKey,
        prefetchedOnly: Boolean,
        prefetchRequestId: String?,
        producer: () -> Flow<NetworkResult<List<AddonStreams>>>
    ): Session {
        val session = Session(
            MutableStateFlow(
                Snapshot(
                    result = NetworkResult.Loading,
                    version = 0L,
                    isComplete = false
                )
            ),
            prefetchedOnly = prefetchedOnly,
            prefetchRequestId = prefetchRequestId,
            prefetchStartedAtMs = if (prefetchedOnly) nowMs() else null,
        )
        session.job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                producer().collect { result ->
                    if (result is NetworkResult.Success && result.data.isNotEmpty()) {
                        session.lastSuccessfulResult = result
                    }
                    session.state.update { current ->
                        Snapshot(
                            result = result,
                            version = current.version + 1,
                            isComplete = false
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                session.state.update { current ->
                    Snapshot(
                        result = NetworkResult.Error(error.message ?: "Failed to fetch streams"),
                        version = current.version + 1,
                        isComplete = false
                    )
                }
            } finally {
                completeSession(key, session)
            }
        }
        return session
    }

    private suspend fun completeSession(key: StreamSearchRequestKey, session: Session) {
        if (session.invalidated) return
        session.state.update { current ->
            val lastSuccess = session.lastSuccessfulResult
            if (lastSuccess != null && current.result !is NetworkResult.Success) {
                Snapshot(
                    result = lastSuccess,
                    version = current.version + 1,
                    isComplete = true
                )
            } else {
                current.copy(isComplete = true)
            }
        }
        mutex.withLock {
            if (sessions[key] !== session) return@withLock
            if (session.lastSuccessfulResult != null) {
                session.completedAtMs = nowMs()
            } else {
                sessions.remove(key)
            }
        }
    }

    private fun removeObsoleteSessionsLocked(requestedKey: StreamSearchRequestKey) {
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (existingKey, session) = iterator.next()
            val belongsToAnotherProfile = existingKey.profileId != requestedKey.profileId
            val sourceConfigurationChanged = existingKey.matchesMediaRequest(requestedKey) &&
                existingKey.sourceConfiguration != requestedKey.sourceConfiguration
            if (belongsToAnotherProfile || sourceConfigurationChanged) {
                iterator.remove()
                session.cancelAndComplete()
            }
        }
    }

    private fun removeExpiredLocked() {
        val now = nowMs()
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next().value
            val completedAt = session.completedAtMs ?: continue
            val ttlMs = if (session.prefetchedOnly) prefetchCompletedTtlMs else completedTtlMs
            if (now - completedAt >= ttlMs) {
                iterator.remove()
                session.cancelAndComplete()
            }
        }
    }

    private fun trimPrefetchEntriesLocked() {
        var prefetchCount = sessions.values.count { it.prefetchedOnly }
        if (prefetchCount <= maxPrefetchEntries) return

        val iterator = sessions.entries.iterator()
        while (iterator.hasNext() && prefetchCount > maxPrefetchEntries) {
            val session = iterator.next().value
            if (!session.prefetchedOnly) continue
            iterator.remove()
            session.cancelAndComplete()
            prefetchCount -= 1
        }
    }

    private fun trimToSizeLocked() {
        while (sessions.size > maxEntries) {
            val iterator = sessions.entries.iterator()
            if (!iterator.hasNext()) return
            val session = iterator.next().value
            iterator.remove()
            session.cancelAndComplete()
        }
    }

    private fun Session.cancelAndComplete() {
        invalidated = true
        state.update { current -> current.copy(isComplete = true) }
        job.cancel()
    }

    private fun StreamSearchRequestKey.matchesMediaRequest(other: StreamSearchRequestKey): Boolean =
        profileId == other.profileId &&
            type == other.type &&
            videoId == other.videoId &&
            season == other.season &&
            episode == other.episode

    private companion object {
        const val DEFAULT_COMPLETED_TTL_MS = 15 * 60 * 1_000L
        const val DEFAULT_MAX_ENTRIES = 12
        const val DEFAULT_PREFETCH_COMPLETED_TTL_MS = 10 * 60 * 1_000L
        const val DEFAULT_MAX_PREFETCH_ENTRIES = 2
    }
}
