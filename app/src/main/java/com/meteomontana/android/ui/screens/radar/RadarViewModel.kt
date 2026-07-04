package com.meteomontana.android.ui.screens.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorRadarApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.dto.RadarBoundsDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.School
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Día que enseña la timeline del radar. */
enum class RadarDay { HOY, AYER }

/**
 * Estado de la pestaña Radar: compuesto España del backend (PNG Cumbre),
 * timeline por día y escuelas para los pines.
 */
data class RadarUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val radar: String = "",
    val bounds: RadarBoundsDto? = null,
    val day: RadarDay = RadarDay.HOY,
    /** Frames en orden cronológico; el bitmap llega en cuanto se descarga. */
    val frames: List<RadarFrameUi> = emptyList(),
    val schools: List<School> = emptyList(),
    val scoresById: Map<String, Int> = emptyMap()
)

data class RadarFrameUi(
    val ts: String,
    val capturedAt: String,   // "2026-07-03T18:40"
    val bitmap: Bitmap? = null
) {
    /** "18:40" para la UI. */
    val timeLabel: String get() = capturedAt.substringAfter('T').take(5)
}

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarApi: KtorRadarApi,
    private val schoolApi: KtorSchoolApi,
    private val forecastApi: KtorForecastApi,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(RadarUiState())
    val state: StateFlow<RadarUiState> = _state

    private var loadedForKey: String? = null

    fun load(day: RadarDay = _state.value.day) {
        val dateStr = (if (day == RadarDay.HOY) LocalDate.now() else LocalDate.now().minusDays(1))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        if (loadedForKey == dateStr && _state.value.error == null) return
        loadedForKey = dateStr
        _state.value = _state.value.copy(loading = true, error = null, day = day)
        viewModelScope.launch {
            try {
                val dto = radarApi.getFrames(date = dateStr)
                // Un día entero son hasta 144 ciclos: para no fundir la memoria
                // con bitmaps, la película usa pasos de ~30 min salvo la última
                // hora, que va completa (es lo que más importa).
                val cut = (dto.frames.size - 7).coerceAtLeast(0)
                val thinned = dto.frames.filterIndexed { i, _ -> i >= cut || i % 3 == 0 }
                _state.value = _state.value.copy(
                    loading = false,
                    radar = dto.radar,
                    bounds = dto.bounds,
                    frames = thinned.map { RadarFrameUi(it.ts, it.capturedAt) }
                )
                // Descarga de PNGs: de MÁS RECIENTE a más antiguo (lo primero que
                // se ve es AHORA), 4 en paralelo, y con caché en disco — los
                // frames son inmutables, así que reabrir el radar solo baja los
                // nuevos y el play está listo en 1-2 segundos.
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val gate = Semaphore(4)
                thinned.reversed().map { ref ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            try {
                                val png = cachedFramePng(dto.radar, ref.ts)
                                val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, opts)
                                _state.value = _state.value.copy(
                                    frames = _state.value.frames.map {
                                        if (it.ts == ref.ts) it.copy(bitmap = bmp) else it
                                    })
                            } catch (_: Exception) { /* frame perdido: la animación lo salta */ }
                        }
                    }
                }.awaitAll()
                pruneFrameCache()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "No se pudo cargar el radar. Comprueba tu conexión."
                )
            }
        }
        if (_state.value.schools.isEmpty()) {
            loadSchools()
        }
    }

    /** PNG del frame, de la caché de disco si ya se descargó alguna vez. */
    private suspend fun cachedFramePng(radar: String, ts: String): ByteArray =
        withContext(Dispatchers.IO) {
            val dir = File(appContext.cacheDir, "radar").apply { mkdirs() }
            val f = File(dir, "${radar}_${ts.replace(Regex("[^A-Za-z0-9-]"), "_")}.png")
            if (f.exists() && f.length() > 0) return@withContext f.readBytes()
            val png = radarApi.getFramePng(radar, ts)
            runCatching { f.writeBytes(png) }
            png
        }

    /** Borra frames cacheados de hace más de 2 días (retención del backend). */
    private fun pruneFrameCache() {
        runCatching {
            val cutoff = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
            File(appContext.cacheDir, "radar").listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    private fun loadSchools() {
        viewModelScope.launch {
            try {
                val schools = schoolApi.getSchools().map { it.toDomain() }
                _state.value = _state.value.copy(schools = schools)
                // Scores del día para colorear los pines (cacheado en el back).
                val scores = forecastApi.getTodayScores(schools.map { it.id })
                    .associate { it.id to it.todayScore }
                _state.value = _state.value.copy(scoresById = scores)
            } catch (_: Exception) { /* sin pines; el radar sigue funcionando */ }
        }
    }
}
