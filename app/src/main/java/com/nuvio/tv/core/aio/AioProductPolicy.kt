package com.nuvio.tv.core.aio

/**
 * Fork-level product rules for AIOtv.
 *
 * These are deliberately separate from Nuvio's normal user/profile capability
 * model. AIOtv treats the backend account configuration as authoritative for
 * addons, while device-local playback preferences and catalog presentation
 * remain user controlled.
 */
object AioProductPolicy {
    const val FIXED_APPEARANCE = true

    const val ADMIN_MANAGED_ADDONS = true
    const val USER_CAN_INSTALL_ADDONS = false
    const val USER_CAN_REMOVE_ADDONS = false
    const val USER_CAN_ENABLE_DISABLE_ADDONS = false
    const val USER_CAN_REORDER_ADDONS = false

    const val USER_CAN_REORDER_CATALOGS = true
    const val USER_CAN_ENABLE_DISABLE_CATALOGS = true

    const val PRESERVE_PLAYBACK_SETTINGS = true
}
