@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.aiotv.brand.AioBrandGlyph
import com.nuvio.tv.ui.aiotv.design.AioColors
import com.nuvio.tv.ui.aiotv.design.AioRadii
import com.nuvio.tv.ui.aiotv.design.AioSpacing

private data class AioSettingsDestination(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** AIOtv Settings is navigation, not an accordion/dashboard. */
@Composable
fun AioSettingsHubScreen(
    onBackPress: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLayout: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    BackHandler { onBackPress() }

    val destinations = remember(
        onNavigateToAppearance,
        onNavigateToLayout,
        onNavigateToAddons,
        onNavigateToPlayback,
        onNavigateToTracking,
        onNavigateToAdvanced,
        onNavigateToAbout
    ) {
        listOf(
            AioSettingsDestination("Language", "Interface language and regional display", Icons.Default.Language, onNavigateToAppearance),
            AioSettingsDestination("Home & layout", "Catalog presentation, posters and home behaviour", Icons.Default.GridView, onNavigateToLayout),
            AioSettingsDestination("Content", "View the addons and catalogs assigned to this account", Icons.Default.Tune, onNavigateToAddons),
            AioSettingsDestination("Playback", "Player, autoplay, audio, subtitles and skipping", Icons.Default.PlayArrow, onNavigateToPlayback),
            AioSettingsDestination("Tracking", "Watch progress and external tracking providers", Icons.Default.Sync, onNavigateToTracking),
            AioSettingsDestination("Advanced", "Network, diagnostics, cache and device behaviour", Icons.Default.Settings, onNavigateToAdvanced),
            AioSettingsDestination("About AIOtv", "Version, licences and project information", Icons.Default.Info, onNavigateToAbout)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AioSpacing.ScreenHorizontal, vertical = AioSpacing.ScreenVertical)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displaySmall,
                        color = AioColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AIOtv preferences and device behaviour",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AioColors.TextSecondary
                    )
                }
                AioBrandGlyph(
                    modifier = Modifier.size(width = 92.dp, height = 58.dp),
                    contentDescription = null,
                    alpha = 0.66f
                )
            }
            Spacer(modifier = Modifier.height(AioSpacing.Section))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AioSpacing.Row),
                modifier = Modifier.fillMaxSize()
            ) {
                items(destinations, key = { it.title }) { destination ->
                    AioSettingsRow(destination)
                }
            }
        }
    }
}

@Composable
private fun AioSettingsRow(destination: AioSettingsDestination) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AioRadii.Card)

    Card(
        onClick = destination.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = AioColors.Surface,
            focusedContainerColor = AioColors.SurfaceFocused,
            pressedContainerColor = AioColors.SurfacePressed
        ),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 0.995f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, AioColors.Divider), shape),
            focusedBorder = Border(BorderStroke(2.dp, AioColors.FocusBorder), shape)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = if (focused) AioColors.FocusBorder else AioColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AioColors.TextPrimary,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = destination.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AioColors.TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (focused) AioColors.TextPrimary else AioColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
