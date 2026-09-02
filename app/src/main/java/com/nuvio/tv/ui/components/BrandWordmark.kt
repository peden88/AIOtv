package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nuvio.tv.R

/**
 * Canonical AIOtv in-app wordmark.
 *
 * This intentionally ignores Nuvio's selectable theme-brand resources: AIOtv
 * has one product identity regardless of the user's colour/theme settings.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    Image(
        painter = painterResource(id = R.drawable.aiotv_wordmark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}
