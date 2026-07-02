package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.GripMeasureSession
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import com.meteomontana.android.domain.usecase.grips.GetMyGripMeasureSessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GripProgressViewModel @Inject constructor(
    private val getGripTypes: GetGripTypesUseCase,
    private val getMyGripMeasureSessions: GetMyGripMeasureSessionsUseCase
) : ViewModel() {

    private val _gripTypes = MutableStateFlow<List<GripType>>(emptyList())
    val gripTypes: StateFlow<List<GripType>> = _gripTypes.asStateFlow()

    private val _selectedGripType = MutableStateFlow<GripType?>(null)
    val selectedGripType: StateFlow<GripType?> = _selectedGripType.asStateFlow()

    private val _hand = MutableStateFlow("LEFT")
    val hand: StateFlow<String> = _hand.asStateFlow()

    private val _sessions = MutableStateFlow<List<GripMeasureSession>>(emptyList())
    val sessions: StateFlow<List<GripMeasureSession>> = _sessions.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _gripTypes.value = runCatching { getGripTypes() }.getOrDefault(emptyList())
            _selectedGripType.value = _gripTypes.value.firstOrNull()
            loadSessions()
        }
    }

    fun selectGripType(t: GripType) { _selectedGripType.value = t; loadSessions() }
    fun selectHand(h: String) { _hand.value = h; loadSessions() }

    private fun loadSessions() {
        val gripType = _selectedGripType.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _sessions.value = runCatching { getMyGripMeasureSessions(gripType.id, _hand.value) }
                .getOrDefault(emptyList()).reversed() // más antiguo primero, para la gráfica
            _loading.value = false
        }
    }
}
