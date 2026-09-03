package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeStreamPrefetchPolicyTest {

    private fun shouldPrefetch(
        positionMs: Long = 40 * 60_000L,
        durationMs: Long = 41 * 60_000L,
        isLive: Boolean = false,
        hasRenderedFirstFrame: Boolean = true,
        hasPlaybackError: Boolean = false,
        autoPlayNextEpisodeEnabled: Boolean = true,
        nextEpisodeHasAired: Boolean = true,
        hasNextEpisode: Boolean = true,
        isCloudPlayback: Boolean = false,
        alreadyPrefetched: Boolean = false,
    ) = shouldPrefetchNextEpisodeStreams(
        positionMs = positionMs,
        durationMs = durationMs,
        isLive = isLive,
        hasRenderedFirstFrame = hasRenderedFirstFrame,
        hasPlaybackError = hasPlaybackError,
        autoPlayNextEpisodeEnabled = autoPlayNextEpisodeEnabled,
        nextEpisodeHasAired = nextEpisodeHasAired,
        hasNextEpisode = hasNextEpisode,
        isCloudPlayback = isCloudPlayback,
        alreadyPrefetched = alreadyPrefetched,
    )

    @Test
    fun `prefetch starts at sixty seconds remaining`() {
        assertTrue(shouldPrefetch())
    }

    @Test
    fun `prefetch does not start before the final minute`() {
        assertFalse(shouldPrefetch(positionMs = 39 * 60_000L + 59_999L))
    }

    @Test
    fun `prefetch skips unaired and missing next episodes`() {
        assertFalse(shouldPrefetch(nextEpisodeHasAired = false))
        assertFalse(shouldPrefetch(hasNextEpisode = false))
    }

    @Test
    fun `prefetch skips disabled autoplay live cloud and failed playback`() {
        assertFalse(shouldPrefetch(autoPlayNextEpisodeEnabled = false))
        assertFalse(shouldPrefetch(isLive = true))
        assertFalse(shouldPrefetch(isCloudPlayback = true))
        assertFalse(shouldPrefetch(hasPlaybackError = true))
        assertFalse(shouldPrefetch(hasRenderedFirstFrame = false))
    }

    @Test
    fun `prefetch starts only once for the same episode`() {
        assertFalse(shouldPrefetch(alreadyPrefetched = true))
    }

    @Test
    fun `prefetch skips short error placeholder streams`() {
        assertFalse(shouldPrefetch(positionMs = 60_000L, durationMs = 120_000L))
    }
}
