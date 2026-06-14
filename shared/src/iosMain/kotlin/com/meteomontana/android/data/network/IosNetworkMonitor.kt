package com.meteomontana.android.data.network

import com.meteomontana.android.domain.port.NetworkMonitor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Impl iOS de [NetworkMonitor] con NWPathMonitor (Network framework). Espejo
 * de AndroidNetworkMonitor (ConnectivityManager).
 *
 * ⚠️ Escrito sin Mac (Fase B del plan pre-Mac): no se ha podido compilar en
 * Windows. Revisar al primer build en Xcode (Fase E) la firma del handler de
 * actualización y la mutación del StateFlow desde la cola de fondo.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _isOnline.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("meteomontana.network", null))
        nw_path_monitor_start(monitor)
    }
}
