package com.nuvio.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.repository.AioTvManagedAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AioTvGateState {
    data object Loading : AioTvGateState

    data class Pairing(
        val deviceCode: String,
        val userCode: String,
        val expiresAtEpochMs: Long,
        val pollIntervalSeconds: Int
    ) : AioTvGateState

    data class Error(
        val message: String
    ) : AioTvGateState

    data object Ready : AioTvGateState
}

@HiltViewModel
class AioTvGateViewModel @Inject constructor(
    private val repository: AioTvManagedAccountRepository,
    private val appOnboardingDataStore: AppOnboardingDataStore
) : ViewModel() {

    private val _state = MutableStateFlow<AioTvGateState>(AioTvGateState.Loading)
    val state: StateFlow<AioTvGateState> = _state.asStateFlow()

    private var authJob: Job? = null

    init {
        initialise()
    }

    fun retry() {
        initialise()
    }

    fun requestNewCode() {
        authJob?.cancel()
        authJob = viewModelScope.launch { startPairing() }
    }

    private fun initialise() {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _state.value = AioTvGateState.Loading
            when (val result = repository.restoreAndBootstrap()) {
                is AioTvManagedAccountRepository.BootstrapResult.Ready -> markReady()
                is AioTvManagedAccountRepository.BootstrapResult.OfflineReady -> markReady()
                AioTvManagedAccountRepository.BootstrapResult.NoSession,
                AioTvManagedAccountRepository.BootstrapResult.Revoked -> startPairing()
                is AioTvManagedAccountRepository.BootstrapResult.Failed -> {
                    _state.value = AioTvGateState.Error(result.message)
                }
            }
        }
    }

    private suspend fun startPairing() {
        _state.value = AioTvGateState.Loading
        val start = repository.startPairing().getOrElse { error ->
            _state.value = AioTvGateState.Error(
                error.message ?: "Unable to start AIOtv pairing"
            )
            return
        }

        val expiresAt = System.currentTimeMillis() + start.expiresIn * 1000L
        var intervalSeconds = start.interval.coerceAtLeast(1)
        _state.value = AioTvGateState.Pairing(
            deviceCode = start.deviceCode,
            userCode = start.userCode,
            expiresAtEpochMs = expiresAt,
            pollIntervalSeconds = intervalSeconds
        )

        while (System.currentTimeMillis() < expiresAt) {
            delay(intervalSeconds * 1000L)
            when (val poll = repository.pollToken(start.deviceCode)) {
                is AioTvManagedAccountRepository.TokenPollResult.Pending -> {
                    intervalSeconds = poll.intervalSeconds.coerceAtLeast(1)
                    val current = _state.value
                    if (current is AioTvGateState.Pairing) {
                        _state.value = current.copy(pollIntervalSeconds = intervalSeconds)
                    }
                }
                is AioTvManagedAccountRepository.TokenPollResult.Approved -> {
                    _state.value = AioTvGateState.Loading
                    when (val bootstrap = repository.bootstrapAndReconcile(poll.session)) {
                        is AioTvManagedAccountRepository.BootstrapResult.Ready -> markReady()
                        is AioTvManagedAccountRepository.BootstrapResult.OfflineReady -> markReady()
                        AioTvManagedAccountRepository.BootstrapResult.Revoked,
                        AioTvManagedAccountRepository.BootstrapResult.NoSession -> {
                            startPairing()
                        }
                        is AioTvManagedAccountRepository.BootstrapResult.Failed -> {
                            _state.value = AioTvGateState.Error(bootstrap.message)
                        }
                    }
                    return
                }
                AioTvManagedAccountRepository.TokenPollResult.Expired -> {
                    _state.value = AioTvGateState.Error(
                        "This pairing code expired. Request a new code to continue."
                    )
                    return
                }
                is AioTvManagedAccountRepository.TokenPollResult.Failed -> {
                    _state.value = AioTvGateState.Error(poll.message)
                    return
                }
            }
        }

        _state.value = AioTvGateState.Error(
            "This pairing code expired. Request a new code to continue."
        )
    }

    /**
     * AIOtv Control owns pairing in this distribution. Mark Nuvio's legacy
     * first-launch account QR as completed before entering MainActivity so the
     * user is never asked to sign into a second account system.
     */
    private suspend fun markReady() {
        appOnboardingDataStore.setHasSeenAuthQrOnFirstLaunch(true)
        _state.value = AioTvGateState.Ready
    }
}
