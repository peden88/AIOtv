package com.nuvio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.ui.aiotv.design.AioColors
import com.nuvio.tv.ui.aiotv.design.AioMotion
import com.nuvio.tv.ui.aiotv.design.AioRadii
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen

/**
 * AIOtv's root navigation shell.
 *
 * Nuvio's navigation graph remains intact, but the large expanding drawer is
 * replaced by a compact floating rail. The rail is icon-first, reveals labels
 * only while it owns focus, and never covers a large portion of the media UI.
 */
@Composable
fun AioNavigationScaffold(
    longPressBackHeld: MutableState<Boolean>,
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    hideBuiltInHeaders: Boolean,
    onNavigate: (String) -> Unit,
    onExitApp: () -> Unit
) {
    val showRail = currentRoute in rootRoutes
    val currentIndex = drawerItems.indexOfFirst { it.route == selectedDrawerRoute }.coerceAtLeast(0)
    val itemRequesters = remember(drawerItems) {
        drawerItems.associate { it.route to FocusRequester() }
    }
    val contentRequester = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    var pendingContentFocus by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (railHasFocus) 190.dp else 66.dp,
        animationSpec = tween(AioMotion.NavigationMs),
        label = "aioRailWidth"
    )
    val contentInset by animateDpAsState(
        targetValue = if (showRail) 76.dp else 0.dp,
        animationSpec = tween(AioMotion.NavigationMs),
        label = "aioContentInset"
    )

    LaunchedEffect(pendingContentFocus, railHasFocus) {
        if (!pendingContentFocus || railHasFocus) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { contentRequester.requestFocus() }
        pendingContentFocus = false
    }

    BackHandler(enabled = currentRoute in rootRoutes && !railHasFocus) {
        itemRequesters[drawerItems.getOrNull(currentIndex)?.route]?.let { requester ->
            runCatching { requester.requestFocus() }
        }
    }
    BackHandler(enabled = currentRoute in rootRoutes && railHasFocus) {
        if (!longPressBackHeld.value) onExitApp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentInset)
                .focusRequester(contentRequester)
                .onKeyEvent { event ->
                    if (!showRail || event.type != KeyEventType.KeyDown || event.key != Key.DirectionLeft) {
                        return@onKeyEvent false
                    }
                    val moved = androidx.compose.ui.platform.LocalFocusManager.current
                        .moveFocus(FocusDirection.Left)
                    if (moved) {
                        true
                    } else {
                        itemRequesters[drawerItems.getOrNull(currentIndex)?.route]
                            ?.let { runCatching { it.requestFocus() } }
                        true
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalSidebarExpanded provides railHasFocus,
                LocalContentFocusRequester provides contentRequester
            ) {
                NuvioNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    hideBuiltInHeaders = hideBuiltInHeaders
                )
            }
        }

        if (showRail) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 18.dp, bottom = 18.dp)
                    .width(railWidth)
                    .fillMaxHeight()
                    .background(
                        color = AioColors.Surface.copy(alpha = if (railHasFocus) 0.97f else 0.88f),
                        shape = RoundedCornerShape(AioRadii.Large)
                    )
                    .padding(horizontal = 9.dp, vertical = 16.dp)
                    .onFocusChanged { railHasFocus = it.hasFocus },
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                drawerItems.forEachIndexed { index, item ->
                    AioRailItem(
                        item = item,
                        selected = index == currentIndex,
                        expanded = railHasFocus,
                        modifier = Modifier.focusRequester(itemRequesters.getValue(item.route)),
                        onClick = {
                            onNavigate(item.route)
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(startDestination) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            railHasFocus = false
                            pendingContentFocus = true
                        }
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AioRailItem(
    item: DrawerItem,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val icon = aioIconFor(item)
    val selectedAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(AioMotion.FocusMs),
        label = "aioRailSelected"
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(AioMotion.FocusMs),
        label = "aioRailScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusProperties { canFocus = true }
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (selected) AioColors.AccentSoft.copy(alpha = 0.24f + selectedAlpha * 0.12f) else Color.Transparent,
            focusedContainerColor = AioColors.SurfaceFocused,
            pressedContainerColor = AioColors.SurfacePressed
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border.None
        ),
        shape = CardDefaults.shape(RoundedCornerShape(AioRadii.Small)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 0.98f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.label,
                tint = when {
                    focused -> AioColors.TextPrimary
                    selected -> AioColors.FocusBorder
                    else -> AioColors.TextSecondary
                },
                modifier = Modifier.size(22.dp)
            )
            if (expanded) {
                Spacer(modifier = Modifier.width(13.dp))
                Text(
                    text = item.label,
                    color = if (focused || selected) AioColors.TextPrimary else AioColors.TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun aioIconFor(item: DrawerItem): ImageVector = when (item.route) {
    Screen.Home.route -> Icons.Default.Home
    Screen.Discover.route -> Icons.Default.Explore
    Screen.Search.route -> Icons.Default.Search
    Screen.Library.route -> Icons.Default.VideoLibrary
    Screen.Settings.route -> Icons.Default.Settings
    else -> item.icon ?: Icons.Default.Home
}
