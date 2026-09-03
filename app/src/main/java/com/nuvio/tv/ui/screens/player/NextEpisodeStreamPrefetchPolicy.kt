package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval

internal const val NEXT_EPISODE_STREAM_PREFETCH_LEAD_MS = 60_000L

/**
 * Uses the same outro-aware timing rule as the post-play overlay, projected by
 * [prefetchLeadMs]. This gives the remote addon search time to finish before an
 * outro auto-skip can immediately hand playback to the next episode.
 */
internal fun shouldPrefetchNextEpisodeStreams(
    positionMs: Long,
    durationMs: Long,
    skipIntervals: List<SkipInterval>,
    thresholdMode: NextEpisodeThresholdMode,
    thresholdPercent: Float,
    thresholdMinutesBeforeEnd: Float,
    isLive: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasPlaybackError: Boolean,
    autoPlayNextEpisodeEnabled: Boolean,
    nextEpisodeHasAired: Boolean,
    hasNextEpisode: Boolean,
    isCloudPlayback: Boolean,
    alreadyPrefetched: Boolean,
    prefetchLeadMs: Long = NEXT_EPISODE_STREAM_PREFETCH_LEAD_MS,
): Boolean {
    if (isLive || !hasRenderedFirstFrame || hasPlaybackError || !autoPlayNextEpisodeEnabled) return false
    if (!nextEpisodeHasAired || !hasNextEpisode || isCloudPlayback || alreadyPrefetched) return false
    if (durationMs <= 0L || positionMs < 0L || positionMs >= durationMs) return false
    if (isShortPlaceholderDuration(durationMs)) return false

    val leadMs = prefetchLeadMs.coerceAtLeast(0L)
    val projectedPositionMs = if (positionMs >= durationMs - leadMs) {
        durationMs
    } else {
        positionMs + leadMs
    }
    return PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
        positionMs = projectedPositionMs,
        durationMs = durationMs,
        skipIntervals = skipIntervals,
        thresholdMode = thresholdMode,
        thresholdPercent = thresholdPercent,
        thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
    )
}
