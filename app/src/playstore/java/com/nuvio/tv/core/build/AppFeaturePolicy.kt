package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val managedBuild: Boolean = false
    val addonManagementEnabled: Boolean = true
    val integrationsEnabled: Boolean = true
    val legacyAccountEnabled: Boolean = true
    val profilesEnabled: Boolean = true
    val p2pSettingsEnabled: Boolean = true
    val appIconPickerEnabled: Boolean = true
    val debugSettingsEnabled: Boolean = false
    val pluginsEnabled: Boolean = false
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = false
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    val imdbRatingLogoEnabled: Boolean = false
}
