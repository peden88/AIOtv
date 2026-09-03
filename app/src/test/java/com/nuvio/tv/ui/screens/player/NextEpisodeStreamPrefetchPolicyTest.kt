package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeStreamPrefetchPolicyTest {

    private fun shouldPrefetch(
        positionMs: Long = 38 * 60_000L + 36_000L,
        durationMs: Long = 40 * 60_000L,
        skipIntervals: List<SkipInterval> = emptyList(),
        thresholdMode: NextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
        thresholdPercent: Float = 99f,
        thresholdMinutesBeforeEnd: Float = 2f,
        isLive: Boolean = false,
        hasRenderedFirstFrame: Boolean = true,
        hasPlaybackError: Boolean = false,
        autoPlayNextEpisodeEnabled: Boolean = true,
        nextEpisodeHasAired: Boolean = true,
        hasNextEpisode: Boolean = true,
        isCloudPlayback: Boolean = false,
        alreadyPrefetched: Boolean = false,
        prefetchLeadMs: Long = NEXT_EPISODE_STREAM_PREFETCH_LEAD_MS,
    ) = shouldPrefetchNextEpisodeStreams(
        positionMs = positionMs,
        durationMs = durationMs,
        skipIntervals = skipIntervals,
        thresholdMode = thresholdMode,
        thresholdPercent = thresholdPercent,
        thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
        isLive = isLive,
        hasRenderedFirstFrame = hasRenderedFirstFrame,
        hasPlaybackError = hasPlaybackError,
        autoPlayNextEpisodeEnabled = autoPlayNextEpisodeEnabled,
        nextEpisodeHasAired = nextEpisodeHasAired,
        hasNextEpisode = hasNextEpisode,
        isCloudPlayback = isCloudPlayback,
        alreadyPrefetched = alreadyPrefetched,
        prefetchLeadMs = prefetchLeadMs,
    )

    @Test
    fun `prefetch starts one minute before percentage based autoplay`() {
        assertTrue(shouldPrefetch())
        assertFalse(shouldPrefetch(positionMs = 38 * 60_000L + 35_999L))
    }

    @Test
    fun `prefetch follows minutes before end autoplay setting`() {
        assertTrue(
            shouldPrefetch(
                positionMs = 39 * 60_000L,
                durationMs = 42 * 60_000L,
                thresholdMode = NextEpisodeThresholdMode.MINUTES_BEFORE_END,
                thresholdMinutesBeforeEnd = 2f,
            )
        )
        assertFalse(
            shouldPrefetch(
                positionMs = 39 * 60_000L - 1L,
                durationMs = 42 * 60_000L,
                thresholdMode = NextEpisodeThresholdMode.MINUTES_BEFORE_END,
                thresholdMinutesBeforeEnd = 2f,
            )
        )
    }

    @Test
    fun `prefetch anticipates detected outro and auto skip`() {
        val outro = SkipInterval(
            startTime = 39.0 * 60.0,
            endTime = 41.0 * 60.0 + 50.0,
            type = "outro",
            provider = "introdb",
        )

        assertTrue(
            shouldPrefetch(
                positionMs = 38 * 60_000L,
                durationMs = 42 * 60_000L,
                skipIntervals = listOf(outro),
            )
        )
        assertFalse(
            shouldPrefetch(
                positionMs = 38 * 60_000L - 1L,
                durationMs = 42 * 60_000L,
                skipIntervals = listOf(outro),
            )
        )
    }

    @Test
    fun `prefetch can align exactly with post play trigger when lead is zero`() {
        assertTrue(shouldPrefetch(positionMs = 39 * 60_000L + 36_000L, prefetchLeadMs = 0L))
        assertFalse(shouldPrefetch(positionMs = 39 * 60_000L + 35_999L, prefetchLeadMs = 0L))
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
