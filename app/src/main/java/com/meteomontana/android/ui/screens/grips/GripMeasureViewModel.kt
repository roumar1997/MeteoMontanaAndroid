package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateGripMeasureSessionRequest
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.port.GripScaleProvider
import com.meteomontana.android.domain.usecase.grips.CreateGripMeasureSessionUseCase
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MeasurePhase {
    data object Idle : MeasurePhase
    data object Measuring : MeasurePhase
    data class Done(val peakKg: Double, val avgKg: Double, val durationS: Int) : MeasurePhase
}

@HiltViewModel
class GripMeasureViewModel @Inject constructor(
    private val scaleProvider: GripScaleProvider,
    private val getGripTypes: GetGripTypesUseCase,
    private val createMeasureSession: CreateGripMeasureSessionUseCase
) : ViewModel() {

    private val _gripTypes = MutableStateFlow<List<GripType>>(emptyList())
    val gripTypes: StateFlow<List<GripType>> = _gripTypes.asStateFlow()

    private val _selectedGripType = MutableStateFlow<GripType?>(null)
    val selectedGripType: StateFlow<GripType?> = _selectedGripType.asStateFlow()

    private val _hand = MutableStateFlow("LEFT")
    val hand: StateFlow<String> = _hand.asStateFlow()

    private val _points = MutableStateFlow<List<ChartPoint>>(emptyList())
    val points: StateFlow<List<ChartPoint>> = _points.asStateFlow()

    private val _phase = MutableStateFlow<MeasurePhase>(MeasurePhase.Idle)
    val phase: StateFlow<MeasurePhase> = _phase.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val connectedDeviceId: String? get() = GripScaleSession.connectedDeviceId

    private var measureJob: Job? = null

    init {
        viewModelScope.launch {
            _gripTypes.value = runCatching { getGripTypes() }.getOrDefault(emptyList())
            _selectedGripType.value = _gripTypes.value.firstOrNull()
        }
    }

    fun selectGripType(t: GripType) { _selectedGripType.value = t }
    fun selectHand(h: String) { _hand.value = h }

    fun startMeasuring() {
        val deviceId = connectedDeviceId ?: return
        _points.value = emptyList()
        _phase.value = MeasurePhase.Measuring
        _saved.value = false
        val startedAt = System.currentTimeMillis()
        measureJob = viewModelScope.launch {
            try {
                scaleProvider.observeWeight(deviceId).collect { reading ->
                    _points.value = _points.value + ChartPoint(reading.kg.toFloat())
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun stopMeasuring() {
        measureJob?.cancel()
        measureJob = null
        scaleProvider.disconnect()
        val kgs = _points.value.map { it.kg }
        if (kgs.isEmpty()) { _phase.value = MeasurePhase.Idle; return }
        val peak = kgs.max().toDouble()
        val avg = kgs.average()
        // ~8Hz de muestreo (protocolo WH-C06) -> duración aproximada.
        val durationS = (kgs.size / 8.0).toInt().coerceAtLeast(1)
        _phase.value = MeasurePhase.Done(peak, avg, durationS)
    }

    fun save() {
        val gripType = _selectedGripType.value ?: return
        val done = _phase.value as? MeasurePhase.Done ?: return
        viewModelScope.launch {
            runCatching {
                createMeasureSession(
                    CreateGripMeasureSessionRequest(
                        gripTypeId = gripType.id, hand = _hand.value,
                        peakKg = done.peakKg, avgKg = done.avgKg, durationS = done.durationS
                    )
                )
            }
            _saved.value = true
        }
    }

    fun reset() {
        _phase.value = MeasurePhase.Idle
        _points.value = emptyList()
        _saved.value = false
    }

    override fun onCleared() {
        super.onCleared()
        measureJob?.cancel()
        scaleProvider.disconnect()
    }
}
