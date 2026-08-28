package com.nuvio.tv.ui.screens.addon

import com.nuvio.tv.core.aio.AioProductPolicy
import com.nuvio.tv.core.server.AddonWebConfigMode
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.UserProfile

internal object AddonManagementAccess {

    /**
     * AIOtv addons are account policy, not a device preference. The central AIO
     * account-management service is the only writer; every TV profile sees the
     * resulting addon set as read-only.
     */
    fun isReadOnly(profile: UserProfile?): Boolean {
        if (AioProductPolicy.ADMIN_MANAGED_ADDONS) return true
        return profile?.let { !it.isPrimary && it.usesPrimaryAddons } == true
    }

    fun webConfigMode(
        profile: UserProfile?,
        experienceMode: ExperienceMode = ExperienceMode.ADVANCED
    ): AddonWebConfigMode {
        return when {
            AioProductPolicy.ADMIN_MANAGED_ADDONS -> AddonWebConfigMode.COLLECTIONS_ONLY
            isReadOnly(profile) -> AddonWebConfigMode.COLLECTIONS_ONLY
            experienceMode == ExperienceMode.ESSENTIAL -> AddonWebConfigMode.ADDONS_ONLY
            else -> AddonWebConfigMode.FULL
        }
    }
}
