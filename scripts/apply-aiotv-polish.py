#!/usr/bin/env python3
from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def replace_required(rel, old, new, count=1):
    text = read(rel)
    found = text.count(old)
    if found < count:
        raise SystemExit(f'AIOtv polish: expected {count} occurrence(s) in {rel}, found {found}: {old!r}')
    text = text.replace(old, new, count)
    write(rel, text)
    print(f'AIOtv polish: updated {rel}')


def remove_between(rel, start, end):
    text = read(rel)
    i = text.find(start)
    if i < 0:
        raise SystemExit(f'AIOtv polish: start marker not found in {rel}')
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f'AIOtv polish: end marker not found in {rel}')
    text = text[:i] + '        // AIOtv managed build: P2P settings are intentionally hidden.\n\n' + text[j:]
    write(rel, text)
    print(f'AIOtv polish: removed P2P settings UI from {rel}')


def decode_asset(source_rel, dest_rels):
    encoded = ''.join(read(source_rel).split())
    data = base64.b64decode(encoded)
    for rel in dest_rels:
        p = ROOT / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        print(f'AIOtv polish: wrote brand asset {rel}')

# Launcher / TV dashboard branding.
replace_required(
    'app/src/main/AndroidManifest.xml',
    'android:banner="@mipmap/banner"',
    'android:banner="@drawable/aiotv_tv_banner"'
)
replace_required(
    'app/src/main/AndroidManifest.xml',
    'android:icon="@mipmap/ic_launcher"',
    'android:icon="@drawable/aiotv_app_icon"'
)

# Hide the entire Integrations settings tab. Plugins are disabled by AppFeaturePolicy in the full flavour.
replace_required(
    'app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScreen.kt',
    'SettingsCategory.INTEGRATION -> true',
    'SettingsCategory.INTEGRATION -> false'
)

# Fresh-install defaults. These remain user-editable after the first value is saved.
player_file = 'app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt'
for old, new in [
    ('val useForcedSubtitles: Boolean = false,', 'val useForcedSubtitles: Boolean = true,'),
    ('val showOnlyPreferredLanguages: Boolean = false,', 'val showOnlyPreferredLanguages: Boolean = true,'),
    ('val internalPlayerEngine: InternalPlayerEngine = InternalPlayerEngine.EXOPLAYER,', 'val internalPlayerEngine: InternalPlayerEngine = InternalPlayerEngine.AUTO,'),
    ('val parentalGuideEnabled: Boolean = true,', 'val parentalGuideEnabled: Boolean = false,'),
    ('val autoSkipSegmentTypes: Set<AutoSkipSegmentType> = emptySet(),', 'val autoSkipSegmentTypes: Set<AutoSkipSegmentType> = setOf(AutoSkipSegmentType.INTRO, AutoSkipSegmentType.OUTRO),'),
    ('val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL,', 'val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,'),
    ('val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,', 'val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.INSTALLED_ADDONS_ONLY,'),
    ('val streamAutoPlayNextEpisodeEnabled: Boolean = false,', 'val streamAutoPlayNextEpisodeEnabled: Boolean = true,'),
]:
    replace_required(player_file, old, new)

# AIOtv-owned/local progress is the default fallback rather than an external tracking provider.
tracking_file = 'app/src/main/java/com/nuvio/tv/data/local/TraktSettingsDataStore.kt'
replace_required(tracking_file, '?: TRAKT\n', '?: NUVIO_SYNC\n')
replace_required(tracking_file, 'val DEFAULT_WATCH_PROGRESS_SOURCE = WatchProgressSource.TRAKT', 'val DEFAULT_WATCH_PROGRESS_SOURCE = WatchProgressSource.NUVIO_SYNC')
replace_required(tracking_file, 'val DEFAULT_LIBRARY_SOURCE_MODE = LibrarySourceMode.TRAKT', 'val DEFAULT_LIBRARY_SOURCE_MODE = LibrarySourceMode.LOCAL')

# Remove the P2P category itself, not merely its toggle.
remove_between(
    'app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsSections.kt',
    '        playbackCollapsibleSection(\n            keyPrefix = "p2p",',
    '        if (playerSettings.internalPlayerEngine == InternalPlayerEngine.EXOPLAYER ||'
)

# Rebrand all user-facing Android string resources without renaming internal identifiers.
for strings in (ROOT / 'app/src').glob('*/res/values*/strings.xml'):
    text = strings.read_text(encoding='utf-8')
    updated = text.replace('Nuvio Sync', 'AIOtv Account').replace('Nuvio Library', 'AIOtv Library').replace('Nuvio', 'AIOtv')
    if updated != text:
        strings.write_text(updated, encoding='utf-8')
        print(f'AIOtv polish: rebranded strings in {strings.relative_to(ROOT)}')

# Catch hard-coded user-facing literals while leaving class/package names untouched.
for kt in (ROOT / 'app/src/main/java').rglob('*.kt'):
    text = kt.read_text(encoding='utf-8')
    updated = (text
        .replace('"Nuvio Sync"', '"AIOtv Account"')
        .replace('"Nuvio Library"', '"AIOtv Library"')
        .replace('"Nuvio"', '"AIOtv"'))
    if updated != text:
        kt.write_text(updated, encoding='utf-8')
        print(f'AIOtv polish: rebranded literals in {kt.relative_to(ROOT)}')

# Decode the approved AIOtv icon/wordmark into full-flavour resource overrides.
decode_asset('branding/aiotv_app_icon.webp.b64', [
    'app/src/full/res/drawable-nodpi/aiotv_app_icon.webp',
    'app/src/full/res/drawable-nodpi/app_logo_mark.webp',
    'app/src/full/res/drawable-nodpi/ic_launcher.webp',
])
decode_asset('branding/aiotv_wordmark.webp.b64', [
    'app/src/full/res/drawable-nodpi/app_logo_wordmark.webp',
    'app/src/full/res/drawable-nodpi/app_logo_wordmark_arctic_blue.webp',
    'app/src/full/res/drawable-nodpi/app_logo_wordmark_gold.webp',
    'app/src/full/res/drawable-nodpi/app_logo_wordmark_graphite.webp',
    'app/src/full/res/drawable-nodpi/app_logo_wordmark_jade.webp',
    'app/src/full/res/drawable-nodpi/app_logo_wordmark_rose_gold.webp',
    'app/src/full/res/drawable-nodpi/nuvio_text.webp',
])
decode_asset('branding/aiotv_tv_banner.webp.b64', [
    'app/src/full/res/drawable-nodpi/aiotv_tv_banner.webp',
    'app/src/full/res/drawable-nodpi/tv_banner.webp',
])

print('AIOtv polish: complete')
