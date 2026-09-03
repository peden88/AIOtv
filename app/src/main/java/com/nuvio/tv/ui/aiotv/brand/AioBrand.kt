package com.nuvio.tv.ui.aiotv.brand

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nuvio.tv.R

/** Canonical AIOtv brand lockup. Use on first-run, About and deliberate brand moments. */
@Composable
fun AioBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "AIOtv",
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    Image(
        painter = painterResource(R.drawable.aiotv_wordmark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}

/** Compact inverted event-horizon tile for navigation and subtle system states. */
@Composable
fun AioBrandGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    Image(
        painter = painterResource(R.drawable.aiotv_horizon_mark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}
