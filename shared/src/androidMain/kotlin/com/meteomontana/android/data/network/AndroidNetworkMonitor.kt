package com.meteomontana.android.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.meteomontana.android.domain.port.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Impl Android del NetworkMonitor. Suscribe un NetworkCallback al
 * ConnectivityManager y publica en un StateFlow si hay internet usable.
 *
 * Se llevan las redes validadas que siguen vivas (wifi y datos pueden estar a la vez): hay conexion mientras quede
 * alguna. Un simple "onLost = offline" dejaba el banner SIN CONEXION puesto al perder una red habiendo otra buena,
 * o si el aviso de la red vieja llegaba despues del de la nueva.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val validated = HashSet<Network>()

    init {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Estado inicial síncrono (con la misma exigencia que el callback: internet Y validada).
        _isOnline.value = cm.activeNetwork?.let { net ->
            cm.getNetworkCapabilities(net)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        } == true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update { it.add(network) }
            override fun onLost(network: Network) = update { it.remove(network) }
            override fun onUnavailable() = update { it.clear() }
        })
    }

    private fun update(change: (MutableSet<Network>) -> Unit) {
        synchronized(validated) {
            change(validated)
            _isOnline.value = validated.isNotEmpty()
        }
    }
}
