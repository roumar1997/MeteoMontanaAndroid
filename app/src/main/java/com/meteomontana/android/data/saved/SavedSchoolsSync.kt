package com.meteomontana.android.data.saved

import co.touchlab.kermit.Logger
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mantiene al día las escuelas guardadas offline: cada vez que hay conexión
 * (al arrancar o al recuperarla) re-descarga sus bloques + forecast y reemplaza
 * el snapshot, para que offline nunca muestre datos viejos (p.ej. piedras que ya
 * se borraron). El usuario no necesita volver a pulsar "descargar".
 * Se inicia desde MeteoMontanaApp.onCreate().
 */
@Singleton
class SavedSchoolsSync @Inject constructor(
    private val saved: SavedSchoolRepository,
    private val networkMonitor: NetworkMonitor,
    private val getBlocks: GetBlocksUseCase,
    private val getForecast: GetForecastUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log = Logger.withTag("SavedSync")

    fun start() {
        scope.launch {
            networkMonitor.isOnline.filter { it }.collect { sync() }
        }
        scope.launch {
            if (networkMonitor.isOnline.value) sync()
        }
    }

    private suspend fun sync() {
        val ids = saved.savedIds()
        if (ids.isEmpty()) return
        log.i("Refrescando ${ids.size} escuela(s) guardada(s)")
        runCatching {
            saved.syncAllSaved(
                fetchBlocks = { getBlocks(it) },
                fetchForecast = { runCatching { getForecast(it) }.getOrNull() }
            )
        }.onFailure { log.w("Fallo refrescando guardadas: ${it.message}") }
    }
}
