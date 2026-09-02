package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.nuvio.tv.ui.aiotv.brand.AioBrandWordmark

/**
 * Compatibility wrapper for upstream screens that still call BrandWordmark.
 * The visible product identity is always the canonical AIOtv event-horizon lockup.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    AioBrandWordmark(
        modifier = modifier,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alpha = alpha
    )
}
