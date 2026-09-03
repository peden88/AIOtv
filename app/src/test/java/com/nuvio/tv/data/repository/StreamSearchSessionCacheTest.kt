package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.AddonStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSearchSessionCacheTest {

    @Test
    fun `prefetched search is reused by the next observer`() = runTest {
        var producerCalls = 0
        val starts = mutableListOf<String>()
        val consumptions = mutableListOf<StreamPrefetchConsumption>()
        val cache = StreamSearchSessionCache(
            scope = this,
            onPrefetchStarted = { _, requestId, _ -> starts += requestId },
            onPrefetchConsumed = { _, consumption -> consumptions += consumption }
        )
        val key = key(episode = 2)
        val producer: () -> Flow<NetworkResult<List<AddonStreams>>> = {
            flow {
                producerCalls += 1
                emit(NetworkResult.Success(resultFor("episode-2")))
            }
        }

        val prefetched = cache.prefetch(key, "prefetch-1", producer)
        advanceUntilIdle()

        val observed = cache.observe(key, forceRefresh = false, producer = producer).toList()

        assertEquals(1, producerCalls)
        assertTrue(observed.last() is NetworkResult.Success)
        assertEquals(listOf("prefetch-1"), starts)
        assertEquals("prefetch-1", prefetched.requestId)
        assertEquals(1, prefetched.addonCount)
        assertEquals(1, consumptions.size)
        assertTrue(consumptions.single().ready)
    }

    @Test
    fun `completed prefetch expires after ten minutes`() = runTest {
        var nowMs = 0L
        var producerCalls = 0
        val cache = StreamSearchSessionCache(scope = this, nowMs = { nowMs })
        val key = key(episode = 2)
        val producer: () -> Flow<NetworkResult<List<AddonStreams>>> = {
            flow {
                producerCalls += 1
                emit(NetworkResult.Success(resultFor("episode-2")))
            }
        }

        cache.prefetch(key, "prefetch-2", producer)
        advanceUntilIdle()
        nowMs = 10 * 60 * 1_000L
        cache.observe(key, forceRefresh = false, producer = producer).toList()

        assertEquals(2, producerCalls)
    }

    @Test
    fun `failed prefetch falls back to a fresh observed search`() = runTest {
        var producerCalls = 0
        val cache = StreamSearchSessionCache(scope = this)
        val key = key(episode = 2)

        cache.prefetch(key, "prefetch-3") {
            flow {
                producerCalls += 1
                emit(NetworkResult.Error("prefetch failed"))
            }
        }
        advanceUntilIdle()

        val observed = cache.observe(key, forceRefresh = false) {
            flow {
                producerCalls += 1
                emit(NetworkResult.Success(resultFor("normal-search")))
            }
        }.toList()

        assertEquals(2, producerCalls)
        assertTrue(observed.last() is NetworkResult.Success)
    }

    @Test
    fun `cache retains at most two stream searches`() = runTest {
        val producerCalls = mutableMapOf<Int, Int>()
        val cache = StreamSearchSessionCache(scope = this)

        suspend fun prefetch(episode: Int) {
            cache.prefetch(key(episode), "prefetch-$episode") {
                flow {
                    producerCalls[episode] = producerCalls.getOrDefault(episode, 0) + 1
                    emit(NetworkResult.Success(resultFor("episode-$episode")))
                }
            }
            advanceUntilIdle()
        }

        prefetch(1)
        prefetch(2)
        prefetch(3)
        cache.observe(key(1), forceRefresh = false) {
            flow {
                producerCalls[1] = producerCalls.getOrDefault(1, 0) + 1
                emit(NetworkResult.Success(resultFor("episode-1")))
            }
        }.toList()

        assertEquals(2, producerCalls[1])
        assertEquals(1, producerCalls[2])
        assertEquals(1, producerCalls[3])
    }

    @Test
    fun `prefetch limit does not evict ordinary stream searches`() = runTest {
        val producerCalls = mutableMapOf<Int, Int>()
        val cache = StreamSearchSessionCache(scope = this)

        fun producer(episode: Int): () -> Flow<NetworkResult<List<AddonStreams>>> = {
            flow {
                producerCalls[episode] = producerCalls.getOrDefault(episode, 0) + 1
                emit(NetworkResult.Success(resultFor("episode-$episode")))
            }
        }

        cache.observe(key(1), forceRefresh = false, producer = producer(1)).toList()
        cache.observe(key(2), forceRefresh = false, producer = producer(2)).toList()
        cache.observe(key(3), forceRefresh = false, producer = producer(3)).toList()
        cache.observe(key(1), forceRefresh = false, producer = producer(1)).toList()

        assertEquals(1, producerCalls[1])
        assertEquals(1, producerCalls[2])
        assertEquals(1, producerCalls[3])
    }

    private fun key(episode: Int) = StreamSearchRequestKey(
        profileId = 1,
        type = "series",
        videoId = "tt123:1:$episode",
        season = 1,
        episode = episode,
        sourceConfiguration = "managed-addons",
    )

    private fun resultFor(name: String) = listOf(
        AddonStreams(addonName = name, addonLogo = null, streams = emptyList()),
    )
}
