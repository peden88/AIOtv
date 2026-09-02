package com.nuvio.tv.core.build

/** Product policy for administrator-managed AIOtv devices. */
object AppFeaturePolicy {
    val managedBuild: Boolean = true
    val addonManagementEnabled: Boolean = false
    val integrationsEnabled: Boolean = false
    val legacyAccountEnabled: Boolean = false
    val profilesEnabled: Boolean = false
    val p2pSettingsEnabled: Boolean = false
    val appIconPickerEnabled: Boolean = false
    val debugSettingsEnabled: Boolean = false
    val pluginsEnabled: Boolean = false
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
}
