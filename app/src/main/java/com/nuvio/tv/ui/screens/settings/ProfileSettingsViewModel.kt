package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.aio.AioProductPolicy
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val profileSyncService: ProfileSyncService
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    /**
     * SettingsScreen shows both upstream Account and Profiles only when this is
     * true. AIOtv has a single administrator-managed Pocket ID identity model,
     * so keep those Nuvio management sections hidden without modifying the much
     * larger upstream SettingsScreen.
     */
    val isPrimaryProfileActive: StateFlow<Boolean> = MutableStateFlow(
        AioProductPolicy.USER_CAN_ACCESS_NUVIO_ACCOUNT ||
            AioProductPolicy.USER_CAN_MANAGE_NUVIO_PROFILES
    )

    val canAddProfile: Boolean
        get() = AioProductPolicy.USER_CAN_MANAGE_NUVIO_PROFILES && profileManager.canCreateProfile

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean,
        usesPrimaryPlugins: Boolean,
        avatarId: String? = null
    ) {
        if (!AioProductPolicy.USER_CAN_MANAGE_NUVIO_PROFILES || _isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val existingIds = profileManager.profiles.value.map { it.id }.toSet()
            val success = profileManager.createProfile(
                name = name,
                avatarColorHex = avatarColorHex,
                avatarId = avatarId
            )
            if (success) {
                val profiles = profileManager.profiles.value
                val newProfile = profiles.firstOrNull { it.id !in existingIds }
                if (newProfile != null && (usesPrimaryAddons || usesPrimaryPlugins)) {
                    profileManager.updateProfile(
                        newProfile.copy(
                            usesPrimaryAddons = usesPrimaryAddons,
                            usesPrimaryPlugins = usesPrimaryPlugins
                        )
                    )
                }
                profileSyncService.pushToRemote()
            }
            _isCreating.value = false
        }
    }

    fun updateProfile(profile: UserProfile) {
        if (!AioProductPolicy.USER_CAN_MANAGE_NUVIO_PROFILES) return
        viewModelScope.launch {
            profileManager.updateProfile(profile)
            profileSyncService.pushToRemote()
        }
    }

    fun deleteProfile(id: Int) {
        if (!AioProductPolicy.USER_CAN_MANAGE_NUVIO_PROFILES) return
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            profileSyncService.deleteProfileData(id)
            profileSyncService.pushToRemote()
        }
    }
}
