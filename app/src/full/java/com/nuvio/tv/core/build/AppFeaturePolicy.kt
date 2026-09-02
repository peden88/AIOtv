package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val managedBuild: Boolean = false
    val addonManagementEnabled: Boolean = true
    val integrationsEnabled: Boolean = true
    val legacyAccountEnabled: Boolean = true
    val profilesEnabled: Boolean = true
    val p2pSettingsEnabled: Boolean = true
    val appIconPickerEnabled: Boolean = true
    val debugSettingsEnabled: Boolean = true
    val pluginsEnabled: Boolean = true
    val inAppUpdatesEnabled: Boolean = true
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
}
