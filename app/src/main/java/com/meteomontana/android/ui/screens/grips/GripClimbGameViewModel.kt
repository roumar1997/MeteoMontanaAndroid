package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.grips.GripClimbGameEngine
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.port.GripScaleProvider
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import com.meteomontana.android.domain.usecase.grips.GetMyGripMaxesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ClimbGameUiPhase {
    data object Setup : ClimbGameUiPhase
    data object NoMaxRecorded : ClimbGameUiPhase
    data object Playing : ClimbGameUiPhase
}

@HiltViewModel
class GripClimbGameViewModel @Inject constructor(
    private val scaleProvider: GripScaleProvider,
    private val getGripTypes: GetGripTypesUseCase,
    private val getMyGripMaxes: GetMyGripMaxesUseCase
) : ViewModel() {

    private val _gripTypes = MutableStateFlow<List<GripType>>(emptyList())
    val gripTypes: StateFlow<List<GripType>> = _gripTypes.asStateFlow()

    private val _selectedGripType = MutableStateFlow<GripType?>(null)
    val selectedGripType: StateFlow<GripType?> = _selectedGripType.asStateFlow()

    private val _hand = MutableStateFlow("LEFT")
    val hand: StateFlow<String> = _hand.asStateFlow()

    private val _difficulty = MutableStateFlow(GripClimbGameEngine.Difficulty.MEDIO)
    val difficulty: StateFlow<GripClimbGameEngine.Difficulty> = _difficulty.asStateFlow()

    private val _uiPhase = MutableStateFlow<ClimbGameUiPhase>(ClimbGameUiPhase.Setup)
    val uiPhase: StateFlow<ClimbGameUiPhase> = _uiPhase.asStateFlow()

    private val _engineState = MutableStateFlow(GripClimbGameEngine.GameState())
    val engineState: StateFlow<GripClimbGameEngine.GameState> = _engineState.asStateFlow()

    private val _currentPct = MutableStateFlow(0.0)
    val currentPct: StateFlow<Double> = _currentPct.asStateFlow()

    val connectedDeviceId: String? get() = GripScaleSession.connectedDeviceId

    private var maxesByGripHand: Map<Pair<Int, String>, Double> = emptyMap()
    private var engine: GripClimbGameEngine? = null
    private var tickJob: Job? = null
    private var weightJob: Job? = null
    private var currentKg = 0.0

    init {
        viewModelScope.launch {
            _gripTypes.value = runCatching { getGripTypes() }.getOrDefault(emptyList())
            _selectedGripType.value = _gripTypes.value.firstOrNull()
            maxesByGripHand = runCatching { getMyGripMaxes() }.getOrDefault(emptyList())
                .associate { (it.gripTypeId to it.hand) to it.maxKg }
        }
    }

    fun selectGripType(t: GripType) { _selectedGripType.value = t }
    fun selectHand(h: String) { _hand.value = h }
    fun selectDifficulty(d: GripClimbGameEngine.Difficulty) { _difficulty.value = d }

    /** Ángulo de desplome de la pared a una altura dada — para que el Canvas
     *  pueda dibujar el perfil completo, no solo el punto actual. */
    fun overhangDegAt(heightM: Double): Double = engine?.overhangAt(heightM) ?: 0.0

    fun start() {
        val gripType = _selectedGripType.value ?: return
        val deviceId = connectedDeviceId ?: return
        val max = maxesByGripHand[gripType.id to _hand.value]
        if (max == null || max <= 0.0) {
            _uiPhase.value = ClimbGameUiPhase.NoMaxRecorded
            return
        }
        val e = GripClimbGameEngine(_difficulty.value)
        engine = e
        _engineState.value = e.state.value
        _uiPhase.value = ClimbGameUiPhase.Playing
        startWeightListener(deviceId)
        startTickLoop(max)
    }

    private fun startWeightListener(deviceId: String) {
        weightJob = viewModelScope.launch {
            try {
                scaleProvider.observeWeight(deviceId).collect { reading ->
                    currentKg = reading.kg
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun startTickLoop(maxKg: Double) {
        tickJob = viewModelScope.launch {
            var lastMs = System.currentTimeMillis()
            while (true) {
                delay(16)
                val e = engine ?: break
                val now = System.currentTimeMillis()
                val delta = now - lastMs
                lastMs = now
                val pct = (currentKg / maxKg * 100.0).coerceIn(0.0, 200.0)
                _currentPct.value = pct
                val s = e.tick(pct, delta)
                _engineState.value = s
                if (s.phase == GripClimbGameEngine.Phase.GAME_OVER) break
            }
        }
    }

    /** Reinicia la partida con los mismos ajustes (agarre/mano/dificultad). */
    fun retry() {
        stopLoops()
        start()
    }

    fun backToSetup() {
        stopLoops()
        _uiPhase.value = ClimbGameUiPhase.Setup
    }

    private fun stopLoops() {
        tickJob?.cancel(); tickJob = null
        weightJob?.cancel(); weightJob = null
        scaleProvider.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopLoops()
    }
}
