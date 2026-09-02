#!/usr/bin/env python3
from pathlib import Path

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


def replace_or_already(rel, old, new, count=1):
    text = read(rel)
    if text.count(new) >= count:
        print(f'AIOtv polish: already applied in {rel}: {new!r}')
        return
    found = text.count(old)
    if found < count:
        raise SystemExit(
            f'AIOtv polish: expected either source or branded value in {rel}; '
            f'found {found} source occurrence(s): {old!r}'
        )
    write(rel, text.replace(old, new, count))
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

# Launcher / TV dashboard branding. Accept either pristine upstream resources or
# an already-branded manifest so the AIOtv UI branch can carry canonical assets
# directly without making the build-time product layer brittle.
replace_or_already(
    'app/src/main/AndroidManifest.xml',
    'android:banner="@mipmap/banner"',
    'android:banner="@drawable/aiotv_tv_banner"'
)
replace_or_already(
    'app/src/main/AndroidManifest.xml',
    'android:icon="@mipmap/ic_launcher"',
    'android:icon="@drawable/aiotv_launcher"'
)

# Hide the entire Integrations settings tab. Plugins are disabled by AppFeaturePolicy in the full flavour.
replace_required(
    'app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScreen.kt',
    'SettingsCategory.INTEGRATION -> true',
    'SettingsCategory.INTEGRATION -> false'
)

# AIOtv baseline defaults. These are the intended defaults for a new install and
# are also applied once to existing test installs by the migration below. After
# that migration flag is written, users are free to change these values normally.
player_file = 'app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt'
for old, new in [
    # Data-class defaults.
    ('val useForcedSubtitles: Boolean = false,', 'val useForcedSubtitles: Boolean = true,'),
    ('val showOnlyPreferredLanguages: Boolean = false,', 'val showOnlyPreferredLanguages: Boolean = true,'),
    ('val internalPlayerEngine: InternalPlayerEngine = InternalPlayerEngine.EXOPLAYER,', 'val internalPlayerEngine: InternalPlayerEngine = InternalPlayerEngine.AUTO,'),
    ('val parentalGuideEnabled: Boolean = true,', 'val parentalGuideEnabled: Boolean = false,'),
    ('val autoSkipSegmentTypes: Set<AutoSkipSegmentType> = emptySet(),', 'val autoSkipSegmentTypes: Set<AutoSkipSegmentType> = setOf(AutoSkipSegmentType.INTRO, AutoSkipSegmentType.OUTRO),'),
    ('val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL,', 'val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,'),
    ('val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,', 'val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.INSTALLED_ADDONS_ONLY,'),
    ('val streamAutoPlayNextEpisodeEnabled: Boolean = false,', 'val streamAutoPlayNextEpisodeEnabled: Boolean = true,'),

    # PlayerSettingsDataStore read-time fallbacks. These are what the UI actually
    # sees when no preference has yet been persisted.
    ('runCatching { InternalPlayerEngine.valueOf(it) }.getOrDefault(InternalPlayerEngine.EXOPLAYER)', 'runCatching { InternalPlayerEngine.valueOf(it) }.getOrDefault(InternalPlayerEngine.AUTO)'),
    ('} ?: InternalPlayerEngine.EXOPLAYER,', '} ?: InternalPlayerEngine.AUTO,'),
    ('parentalGuideEnabled = prefs[parentalGuideEnabledKey] ?: true,', 'parentalGuideEnabled = prefs[parentalGuideEnabledKey] ?: false,'),
    ('?: emptySet(),\n                dv5ToDv81Enabled', '?: setOf(AutoSkipSegmentType.INTRO, AutoSkipSegmentType.OUTRO),\n                dv5ToDv81Enabled'),
    ('runCatching { StreamAutoPlayMode.valueOf(it) }.getOrDefault(StreamAutoPlayMode.MANUAL)', 'runCatching { StreamAutoPlayMode.valueOf(it) }.getOrDefault(StreamAutoPlayMode.FIRST_STREAM)'),
    ('} ?: StreamAutoPlayMode.MANUAL,', '} ?: StreamAutoPlayMode.FIRST_STREAM,'),
    ('runCatching { StreamAutoPlaySource.valueOf(it) }.getOrDefault(StreamAutoPlaySource.ALL_SOURCES)', 'runCatching { StreamAutoPlaySource.valueOf(it) }.getOrDefault(StreamAutoPlaySource.INSTALLED_ADDONS_ONLY)'),
    ('} ?: StreamAutoPlaySource.ALL_SOURCES,', '} ?: StreamAutoPlaySource.INSTALLED_ADDONS_ONLY,'),
    ('streamAutoPlayNextEpisodeEnabled = prefs[streamAutoPlayNextEpisodeEnabledKey] ?: false,', 'streamAutoPlayNextEpisodeEnabled = prefs[streamAutoPlayNextEpisodeEnabledKey] ?: true,'),
    ('useForcedSubtitles = (prefs[subtitleUseForcedSubtitlesKey] ?: false) ||', 'useForcedSubtitles = (prefs[subtitleUseForcedSubtitlesKey] ?: true) ||'),
    ('showOnlyPreferredLanguages = prefs[subtitleShowOnlyPreferredLanguagesKey] ?: false,', 'showOnlyPreferredLanguages = prefs[subtitleShowOnlyPreferredLanguagesKey] ?: true,'),
]:
    replace_required(player_file, old, new)

# Add a versioned one-time migration so an installed pre-defaults test build gets
# the AIOtv baseline exactly once. This intentionally establishes the product
# baseline on upgrade; subsequent app starts never overwrite user changes.
replace_required(
    player_file,
    '    private val migrationTargetBufferSizeReducedDoneKey = booleanPreferencesKey("migration_target_buffer_size_reduced_done")\n',
    '    private val migrationTargetBufferSizeReducedDoneKey = booleanPreferencesKey("migration_target_buffer_size_reduced_done")\n'
    '    private val migrationAioTvDefaultsV1DoneKey = booleanPreferencesKey("migration_aiotv_defaults_v1_done")\n'
)

replace_required(
    player_file,
    '        factory.get(profileId, FEATURE).edit { prefs ->\n                val loadControlMigrated = prefs[migrationLoadControlDefaultsAlignedDoneKey] ?: false\n',
    '        factory.get(profileId, FEATURE).edit { prefs ->\n'
    '                val aioTvDefaultsMigrated = prefs[migrationAioTvDefaultsV1DoneKey] ?: false\n'
    '                if (!aioTvDefaultsMigrated) {\n'
    '                    prefs[playerPreferenceKey] = PlayerPreference.INTERNAL.name\n'
    '                    prefs[internalPlayerEngineKey] = InternalPlayerEngine.AUTO.name\n'
    '                    prefs[loadingOverlayEnabledKey] = true\n'
    '                    prefs[parentalGuideEnabledKey] = false\n'
    '                    prefs[skipIntroEnabledKey] = true\n'
    '                    prefs[autoSkipSegmentTypesKey] = setOf(\n'
    '                        AutoSkipSegmentType.INTRO.storedValue,\n'
    '                        AutoSkipSegmentType.OUTRO.storedValue\n'
    '                    )\n'
    '                    prefs[streamAutoPlayModeKey] = StreamAutoPlayMode.FIRST_STREAM.name\n'
    '                    prefs[streamAutoPlaySourceKey] = StreamAutoPlaySource.INSTALLED_ADDONS_ONLY.name\n'
    '                    prefs[streamAutoPlayNextEpisodeEnabledKey] = true\n'
    '                    prefs[subtitleUseForcedSubtitlesKey] = true\n'
    '                    prefs[subtitleShowOnlyPreferredLanguagesKey] = true\n'
    '                    prefs[migrationAioTvDefaultsV1DoneKey] = true\n'
    '                }\n\n'
    '                val loadControlMigrated = prefs[migrationLoadControlDefaultsAlignedDoneKey] ?: false\n'
)

# Remaining original script follows below.
