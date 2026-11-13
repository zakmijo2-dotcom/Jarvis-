package com.example.core.wakeword

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WakeWordState {
    DORMANT_PHASE_2,
    STANDBY_CONFIGURED_PENDING_ENGINE,
    TRIGGERED
}

interface WakeWordEngine {
    val state: StateFlow<WakeWordState>
    val keyword: String
    val isEngineAvailableInPhase1: Boolean
    fun syncStandbyPreference(enabled: Boolean)
}

class Phase1WakeWordEngine : WakeWordEngine {
    private val _state = MutableStateFlow(WakeWordState.DORMANT_PHASE_2)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()
    override val keyword: String = "Hey Jarvis"
    override val isEngineAvailableInPhase1: Boolean = false

    override fun syncStandbyPreference(enabled: Boolean) {
        _state.value = if (enabled) {
            WakeWordState.STANDBY_CONFIGURED_PENDING_ENGINE
        } else {
            WakeWordState.DORMANT_PHASE_2
        }
    }
}
