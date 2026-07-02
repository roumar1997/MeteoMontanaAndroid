package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.GripScaleDevice
import com.meteomontana.android.domain.port.GripScaleProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Guarda qué báscula está "bloqueada" para la sesión actual (Medir/Entrenar
 *  la leen de aquí; se resetea al cerrar la app). */
object GripScaleSession {
    var connectedDeviceId: String? = null
    var connectedAlias: String? = null
}

@HiltViewModel
class GripConnectViewModel @Inject constructor(
    private val scaleProvider: GripScaleProvider
) : ViewModel() {

    private val _devices = MutableStateFlow<List<GripScaleDevice>>(emptyList())
    val devices: StateFlow<List<GripScaleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _connectedId = MutableStateFlow(GripScaleSession.connectedDeviceId)
    val connectedId: StateFlow<String?> = _connectedId.asStateFlow()

    val hasPermission: Boolean get() = scaleProvider.hasPermission()

    private var scanJob: Job? = null

    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                scaleProvider.scanDevices().collect { _devices.value = it }
            } catch (_: Throwable) {
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scaleProvider.stopScan()
        _scanning.value = false
    }

    fun connect(deviceId: String) {
        GripScaleSession.connectedDeviceId = deviceId
        GripScaleSession.connectedAlias = scaleProvider.getAlias(deviceId)
        _connectedId.value = deviceId
        stopScan()
    }

    fun renameDevice(deviceId: String, alias: String) {
        scaleProvider.setAlias(deviceId, alias)
        if (GripScaleSession.connectedDeviceId == deviceId) GripScaleSession.connectedAlias = alias
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(alias = alias) else it }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
