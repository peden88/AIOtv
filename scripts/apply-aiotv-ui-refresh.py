#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.write_text(text, encoding="utf-8")


def replace_required(rel: str, old: str, new: str, count: int = 1) -> None:
    text = read(rel)
    found = text.count(old)
    if found < count:
        raise SystemExit(
            f"AIOtv UI refresh: expected {count} occurrence(s) in {rel}, "
            f"found {found}: {old!r}"
        )
    write(rel, text.replace(old, new, count))
    print(f"AIOtv UI refresh: updated {rel}")


# ---------------------------------------------------------------------------
# Full-page settings navigation
# ---------------------------------------------------------------------------
screen_file = "app/src/main/java/com/nuvio/tv/ui/navigation/Screen.kt"
replace_required(
    screen_file,
    '    data object PlaybackSettings : Screen("playback_settings")\n',
    '    data object PlaybackSettings : Screen("playback_settings")\n'
    '    data object AdvancedSettings : Screen("advanced_settings")\n',
)

nav_file = "app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt"
replace_required(
    nav_file,
    'import com.nuvio.tv.ui.screens.settings.SettingsScreen\n',
    'import com.nuvio.tv.ui.screens.settings.SettingsScreen\n'
    'import com.nuvio.tv.ui.screens.settings.AioSettingsHubScreen\n'
    'import com.nuvio.tv.ui.screens.settings.AioAdvancedSettingsScreen\n',
)

replace_required(
    nav_file,
    '''        composable(Screen.Settings.route) {
            SettingsScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToTracking = { navController.navigate(Screen.Tracking.route) },
                onNavigateToAddons = { navController.navigate(Screen.AddonManager.route) },
                onNavigateToPlugins = { navController.navigate(Screen.Plugins.route) },
                onNavigateToAuthQrSignIn = { navController.navigate(Screen.AuthQrSignIn.route) },
                onNavigateToManageProfiles = { navController.navigate(Screen.ManageProfiles.route) },
                onNavigateToSupportersContributors = {
                    navController.navigate(Screen.SupportersContributors.route)
                },
                onNavigateToLicensesAttributions = {
                    navController.navigate(Screen.LicensesAttributions.route)
                }
            )
        }
''',
    '''        composable(Screen.Settings.route) {
            AioSettingsHubScreen(
                onBackPress = { navController.popBackStack() },
                onNavigateToAppearance = { navController.navigate(Screen.ThemeSettings.route) },
                onNavigateToLayout = { navController.navigate(Screen.LayoutSettings.route) },
                onNavigateToPlayback = { navController.navigate(Screen.PlaybackSettings.route) },
                onNavigateToTracking = { navController.navigate(Screen.Tracking.route) },
                onNavigateToAdvanced = { navController.navigate(Screen.AdvancedSettings.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
''',
)

replace_required(
    nav_file,
    '''        composable(Screen.PlaybackSettings.route) {
            PlaybackSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
''',
    '''        composable(Screen.PlaybackSettings.route) {
            PlaybackSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.AdvancedSettings.route) {
            AioAdvancedSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
''',
)


# ---------------------------------------------------------------------------
# Restore the exact catalog/discover location after Details -> Back.
# Nuvio preserved a focused index, but Discover requested focus before the
# off-screen target card had been composed. Scroll the target into composition
# first, then restore focus.
# ---------------------------------------------------------------------------
discover_file = "app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt"
replace_required(
    discover_file,
    '''        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
''',
    '''        val targetIndex = effectiveFocusedItemIndex.coerceIn(0, totalCells - 1)
        val targetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!targetVisible) {
            gridState.scrollToItem(targetIndex)
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
''',
)

# See All already remembered the focused card, but not the exact viewport. Save
# first-visible index/offset at navigation time and restore that viewport first.
see_all_file = "app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt"
replace_required(
    see_all_file,
    '''    val gridState = rememberLazyGridState()
    val restoreFocusRequester = remember { FocusRequester() }
''',
    '''    var savedGridIndex by rememberSaveable("${catalogKey}_grid_index") { mutableStateOf(0) }
    var savedGridOffset by rememberSaveable("${catalogKey}_grid_offset") { mutableStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedGridIndex,
        initialFirstVisibleItemScrollOffset = savedGridOffset
    )
    val restoreFocusRequester = remember { FocusRequester() }
''',
)

replace_required(
    see_all_file,
    '''        val targetIndex = focusedItemIndex.coerceIn(0, items.lastIndex)
        val isTargetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!isTargetVisible) {
            gridState.animateScrollToItem(targetIndex)
        }
''',
    '''        val targetIndex = focusedItemIndex.coerceIn(0, items.lastIndex)
        val savedIndex = savedGridIndex.coerceIn(0, items.lastIndex)
        if (gridState.firstVisibleItemIndex != savedIndex ||
            gridState.firstVisibleItemScrollOffset != savedGridOffset
        ) {
            gridState.scrollToItem(savedIndex, savedGridOffset)
        }
        val isTargetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!isTargetVisible) {
            gridState.scrollToItem(targetIndex)
        }
''',
)

replace_required(
    see_all_file,
    '''                            onClick = {
                                focusedItemKey = itemFocusKey
                                onNavigateToDetail(
''',
    '''                            onClick = {
                                focusedItemKey = itemFocusKey
                                savedGridIndex = gridState.firstVisibleItemIndex
                                savedGridOffset = gridState.firstVisibleItemScrollOffset
                                onNavigateToDetail(
''',
)

replace_required(
    see_all_file,
    '''            onNavigateToDetail = { id, type2, addonBaseUrl ->
                onNavigateToDetail(id, type2, addonBaseUrl)
            }
''',
    '''            onNavigateToDetail = { id, type2, addonBaseUrl ->
                savedGridIndex = gridState.firstVisibleItemIndex
                savedGridOffset = gridState.firstVisibleItemScrollOffset
                onNavigateToDetail(id, type2, addonBaseUrl)
            }
''',
)

# Collection-folder tabbed grids persisted their scroll state, but focus restore
# could return early if the requested card had not composed yet. Bring its saved
# index into view before resolving the requester.
folder_file = "app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt"
replace_required(
    folder_file,
    '''            LaunchedEffect(items, tabFocusState.hasSavedFocus, tabFocusState.focusedItemKey) {
                val targetKey = tabFocusState.focusedItemKey ?: return@LaunchedEffect
                if (!tabFocusState.hasSavedFocus) return@LaunchedEffect
                val requester = itemFocusRequesters[targetKey] ?: return@LaunchedEffect
                repeat(2) { withFrameNanos { } }
                if (runCatching { requester.requestFocus() }.isSuccess) {
                    lastFocusedItemKey = targetKey
                }
            }
''',
    '''            LaunchedEffect(items, tabFocusState.hasSavedFocus, tabFocusState.focusedItemKey) {
                val targetKey = tabFocusState.focusedItemKey ?: return@LaunchedEffect
                if (!tabFocusState.hasSavedFocus) return@LaunchedEffect
                val targetIndex = targetKey.substringAfterLast('_').toIntOrNull()
                if (targetIndex != null && targetIndex in items.indices &&
                    gridState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }
                ) {
                    gridState.scrollToItem(targetIndex)
                }
                repeat(3) { withFrameNanos { } }
                val requester = itemFocusRequesters[targetKey] ?: return@LaunchedEffect
                if (runCatching { requester.requestFocus() }.isSuccess) {
                    lastFocusedItemKey = targetKey
                }
            }
''',
)

print("AIOtv UI refresh: complete")
