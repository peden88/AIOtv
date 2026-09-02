package com.nuvio.tv.ui.screens.addon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.repository.AioTvManagedAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AioTvPolicyRefreshState(
    val isRefreshing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class AioTvPolicyRefreshViewModel @Inject constructor(
    private val repository: AioTvManagedAccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AioTvPolicyRefreshState())
    val state: StateFlow<AioTvPolicyRefreshState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = AioTvPolicyRefreshState(isRefreshing = true)
            _state.value = when (val result = repository.restoreAndBootstrap()) {
                is AioTvManagedAccountRepository.BootstrapResult.Ready -> {
                    AioTvPolicyRefreshState(
                        message = "Assigned addons refreshed (policy revision ${result.data.policy.revision})."
                    )
                }
                is AioTvManagedAccountRepository.BootstrapResult.OfflineReady -> {
                    AioTvPolicyRefreshState(
                        message = "AIOtv Control is offline. Using the last verified assignments " +
                            "(policy revision ${result.policyRevision})."
                    )
                }
                AioTvManagedAccountRepository.BootstrapResult.NoSession,
                AioTvManagedAccountRepository.BootstrapResult.Revoked -> {
                    AioTvPolicyRefreshState(
                        message = "This TV is no longer paired. Restart AIOtv to request a new code.",
                        isError = true
                    )
                }
                is AioTvManagedAccountRepository.BootstrapResult.Failed -> {
                    AioTvPolicyRefreshState(
                        message = result.message,
                        isError = true
                    )
                }
            }
        }
    }
}
