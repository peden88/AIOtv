package com.nuvio.tv.core.aio

/**
 * Fork-level product rules for AIOtv.
 *
 * These are deliberately separate from Nuvio's normal user/profile capability
 * model. AIOtv treats the backend account configuration as authoritative for
 * identity and addons, while device-local playback preferences and catalog
 * presentation remain user controlled.
 */
object AioProductPolicy {
    const val FIXED_APPEARANCE = true

    // AIOtv Control's administrator-approved device binding is the only account
    // model exposed by AIOtv. Upstream Nuvio account/profile controls remain
    // internal implementation details and are not user-manageable.
    const val USER_CAN_ACCESS_NUVIO_ACCOUNT = false
    const val USER_CAN_MANAGE_NUVIO_PROFILES = false

    const val ADMIN_MANAGED_ADDONS = true
    const val USER_CAN_INSTALL_ADDONS = false
    const val USER_CAN_REMOVE_ADDONS = false
    const val USER_CAN_ENABLE_DISABLE_ADDONS = false
    const val USER_CAN_REORDER_ADDONS = false

    const val USER_CAN_REORDER_CATALOGS = true
    const val USER_CAN_ENABLE_DISABLE_CATALOGS = true

    const val PRESERVE_PLAYBACK_SETTINGS = true
}
