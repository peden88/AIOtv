package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.aiotv.design.AioSpacing

/** AIOtv full-page host for the existing advanced settings content. */
@Composable
fun AioAdvancedSettingsScreen(
    onBackPress: () -> Unit
) {
    BackHandler { onBackPress() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = AioSpacing.ScreenHorizontal,
                end = AioSpacing.ScreenHorizontal,
                top = 24.dp,
                bottom = 24.dp
            )
    ) {
        AdvancedSettingsContent()
    }
}
