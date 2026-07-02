package com.meteomontana.android.ui.screens.grips

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.grips.GripWorkoutEngine
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.port.GripScaleProvider
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import com.meteomontana.android.domain.usecase.grips.GetGripWorkoutUseCase
import com.meteomontana.android.domain.usecase.grips.GetMyGripMaxesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GripWorkoutRunViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scaleProvider: GripScaleProvider,
    private val getGripWorkout: GetGripWorkoutUseCase,
    private val getGripTypes: GetGripTypesUseCase,
    private val getMyGripMaxes: GetMyGripMaxesUseCase
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle.get<String>("workoutId"))

    private val _workout = MutableStateFlow<GripWorkout?>(null)
    val workout: StateFlow<GripWorkout?> = _workout.asStateFlow()

    private var engine: GripWorkoutEngine? = null
    private val _engineState = MutableStateFlow<GripWorkoutEngine.EngineState?>(null)
    val engineState: StateFlow<GripWorkoutEngine.EngineState?> = _engineState.asStateFlow()

    private val _points = MutableStateFlow<List<ChartPoint>>(emptyList())
    val points: StateFlow<List<ChartPoint>> = _points.asStateFlow()

    private val _currentKg = MutableStateFlow(0.0)
    val currentKg: StateFlow<Double> = _currentKg.asStateFlow()

    private val _gripTypes = MutableStateFlow<List<GripType>>(emptyList())
    private val _maxesByGripHand = MutableStateFlow<Map<Pair<Int, String>, Double>>(emptyMap())

    val connectedDeviceId: String? get() = GripScaleSession.connectedDeviceId

    private var tickJob: Job? = null
    private var weightJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var lastBeepMs = 0L

    init {
        viewModelScope.launch {
            val w = runCatching { getGripWorkout(workoutId) }.getOrNull() ?: return@launch
            _workout.value = w
            _gripTypes.value = runCatching { getGripTypes() }.getOrDefault(emptyList())
            _maxesByGripHand.value = runCatching { getMyGripMaxes() }.getOrDefault(emptyList())
                .associate { (it.gripTypeId to it.hand) to it.maxKg }
            engine = GripWorkoutEngine(w)
            _engineState.value = engine?.state?.value
            runCatching { toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
            startEngineLoop()
            startWeightListener()
        }
    }

    /** Rango objetivo en kg del set/mano actuales, o null si no hay máximo guardado. */
    fun currentTargetRangeKg(): Pair<Double, Double>? {
        val w = _workout.value ?: return null
        val es = _engineState.value ?: return null
        val set = w.sets.getOrNull(es.setIndex) ?: return null
        val hand = if (es.activeHand == GripWorkoutEngine.Hand.LEFT) "LEFT"
        else if (es.activeHand == GripWorkoutEngine.Hand.RIGHT) "RIGHT" else return null
        val max = _maxesByGripHand.value[set.gripTypeId to hand] ?: return null
        return (max * set.targetMinPct / 100.0) to (max * set.targetMaxPct / 100.0)
    }

    fun currentGripLabel(): String? {
        val w = _workout.value ?: return null
        val es = _engineState.value ?: return null
        val set = w.sets.getOrNull(es.setIndex) ?: return null
        return _gripTypes.value.firstOrNull { it.id == set.gripTypeId }?.label()
    }

    private fun startEngineLoop() {
        tickJob = viewModelScope.launch {
            var lastMs = System.currentTimeMillis()
            while (true) {
                delay(100)
                val now = System.currentTimeMillis()
                val delta = now - lastMs
                lastMs = now
                val e = engine ?: break
                e.tick(delta)
                _engineState.value = e.state.value
                if (e.state.value.finished) break
            }
        }
    }

    private fun startWeightListener() {
        val deviceId = connectedDeviceId ?: return
        weightJob = viewModelScope.launch {
            try {
                scaleProvider.observeWeight(deviceId).collect { reading ->
                    _currentKg.value = reading.kg
                    val es = _engineState.value
                    val hand = when (es?.activeHand) {
                        GripWorkoutEngine.Hand.LEFT -> "LEFT"
                        GripWorkoutEngine.Hand.RIGHT -> "RIGHT"
                        else -> null
                    }
                    if (hand != null) {
                        _points.value = (_points.value + ChartPoint(reading.kg.toFloat(), hand)).takeLast(200)
                        checkTargetBeep(reading.kg)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    /** Pita en bucle mientras la fuerza está fuera del rango objetivo (sección 3.2). */
    private fun checkTargetBeep(kg: Double) {
        val range = currentTargetRangeKg() ?: return
        val outOfRange = kg < range.first || kg > range.second
        val now = System.currentTimeMillis()
        if (outOfRange && now - lastBeepMs > 500) {
            lastBeepMs = now
            runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
    }

    fun stop() {
        tickJob?.cancel()
        weightJob?.cancel()
        scaleProvider.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        toneGenerator?.release()
    }
}
