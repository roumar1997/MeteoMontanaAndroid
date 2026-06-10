package com.meteomontana.android.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Puerto que expone el estado de conectividad en tiempo real.
 * Android: implementado con ConnectivityManager.NetworkCallback.
 * iOS: implementado con NWPathMonitor.
 *
 * UI lo consume vía StateFlow → banner global "SIN CONEXIÓN" + decisiones
 * de carga online/offline.
 */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}
