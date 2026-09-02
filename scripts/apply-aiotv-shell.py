#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_required(rel: str, old: str, new: str, count: int = 1) -> None:
    text = read(rel)
    found = text.count(old)
    if found < count:
        raise SystemExit(
            f"AIOtv shell: expected {count} occurrence(s) in {rel}, found {found}: {old!r}"
        )
    write(rel, text.replace(old, new, count))
    print(f"AIOtv shell: updated {rel}")


# Replace both Nuvio sidebar variants with the AIOtv-owned compact floating rail.
main_file = "app/src/main/java/com/nuvio/tv/MainActivity.kt"
replace_required(
    main_file,
    '''                            if (modernSidebarEnabled) {
                                ModernSidebarScaffold(
                                    longPressBackHeld = longPressBackHeld,
                                    navController = navController,
                                    startDestination = startDestination,
                                    currentRoute = currentRoute,
                                    rootRoutes = rootRoutes,
                                    drawerItems = drawerItems,
                                    selectedDrawerRoute = selectedDrawerRoute,
                                    selectedDrawerItem = selectedDrawerItem,
                                    sidebarCollapsed = sidebarCollapsed,
                                    modernSidebarBlurEnabled = modernSidebarBlurEnabled,
                                    hideBuiltInHeaders = hideBuiltInHeadersForFloatingPill,
                                    activeProfileName = activeProfile?.name ?: "",
                                    activeProfileColorHex = activeProfile?.avatarColorHex ?: "#1E88E5",
                                    activeProfileAvatarImageUrl = activeProfileAvatarImageUrl,
                                    showProfileSelector = profiles.size > 1,
                                    onSwitchProfile = { hasSelectedProfileThisSession = false },
                                    onNavigate = { optimisticRoute = it },
                                    onExitApp = handleExitApp
                                )
                            } else {
                                LegacySidebarScaffold(
                                    longPressBackHeld = longPressBackHeld,
                                    navController = navController,
                                    startDestination = startDestination,
                                    currentRoute = currentRoute,
                                    rootRoutes = rootRoutes,
                                    drawerItems = drawerItems,
                                    selectedDrawerRoute = selectedDrawerRoute,
                                    sidebarCollapsed = sidebarCollapsed,
                                    hideBuiltInHeaders = false,
                                    activeProfileName = activeProfile?.name ?: "",
                                    activeProfileColorHex = activeProfile?.avatarColorHex ?: "#1E88E5",
                                    activeProfileAvatarImageUrl = activeProfileAvatarImageUrl,
                                    showProfileSelector = profiles.size > 1,
                                    onSwitchProfile = { hasSelectedProfileThisSession = false },
                                    onNavigate = { optimisticRoute = it },
                                    onExitApp = handleExitApp
                                )
                            }
''',
    '''                            AioNavigationScaffold(
                                longPressBackHeld = longPressBackHeld,
                                navController = navController,
                                startDestination = startDestination,
                                currentRoute = currentRoute,
                                rootRoutes = rootRoutes,
                                drawerItems = drawerItems,
                                selectedDrawerRoute = selectedDrawerRoute,
                                hideBuiltInHeaders = true,
                                onNavigate = { optimisticRoute = it },
                                onExitApp = handleExitApp
                            )
''',
)

# Establish a consistent AIOtv card focus language across Home, Discover,
# collections and See All without rewriting their data/focus machinery.
card_file = "app/src/main/java/com/nuvio/tv/ui/components/ContentCard.kt"
replace_required(
    card_file,
    'import com.nuvio.tv.ui.theme.NuvioTheme\n',
    'import com.nuvio.tv.ui.theme.NuvioTheme\n'
    'import com.nuvio.tv.ui.aiotv.design.AioColors\n'
    'import com.nuvio.tv.ui.aiotv.design.AioMotion\n'
    'import com.nuvio.tv.ui.aiotv.design.AioRadii\n',
)
replace_required(
    card_file,
    '    val cardShape = remember(posterCardStyle.cornerRadius) { RoundedCornerShape(posterCardStyle.cornerRadius) }\n',
    '    val cardShape = remember { RoundedCornerShape(AioRadii.Card) }\n',
)
replace_required(
    card_file,
    '                    border = NuvioTheme.focusRing.border(posterCardStyle.focusedBorderWidth),\n',
    '                    border = BorderStroke(2.dp, AioColors.FocusBorder),\n',
)
replace_required(
    card_file,
    '            scale = CardDefaults.scale(focusedScale = posterCardStyle.focusedScale)\n',
    '            scale = CardDefaults.scale(focusedScale = AioMotion.FocusScale, pressedScale = 0.995f)\n',
)

# Tone down Nuvio's oversized modern-card scaling. AIOtv gets cleaner rows with
# more negative space and less motion between neighboring cards.
home_file = "app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeContent.kt"
replace_required(home_file, '    val portraitModernPosterScale = 1.08f\n', '    val portraitModernPosterScale = 1.0f\n')
replace_required(home_file, '    val landscapeModernPosterScale = 1.34f\n', '    val landscapeModernPosterScale = 1.08f\n')
replace_required(home_file, '    val continueWatchingScale = 1.34f\n', '    val continueWatchingScale = 1.08f\n')
replace_required(home_file, '            val rowHorizontalPadding = 52.dp\n', '            val rowHorizontalPadding = 44.dp\n')

print("AIOtv shell: complete")
