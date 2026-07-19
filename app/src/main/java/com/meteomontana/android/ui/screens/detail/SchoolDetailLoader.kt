package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.data.api.KtorMountainApi
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.util.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Carga del detalle de escuela: llamadas paralelas al backend + fallback al
 * snapshot offline + caché del forecast. Extraído de SchoolDetailViewModel
 * (SRP): construir el Success/Error es una responsabilidad completa en sí
 * misma; el VM solo publica el estado y encadena las cargas posteriores
 * (stats mensuales, refresco del snapshot).
 */
class SchoolDetailLoader @Inject constructor(
    private val getSchoolById: GetSchoolByIdUseCase,
    private val getForecast: GetForecastUseCase,
    private val getNotes: GetNotesUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val savedSchoolRepo: SavedSchoolRepository,
    private val mountainApi: KtorMountainApi
) {
    /** [fromNetwork] distingue el Success online del snapshot offline: solo el
     *  online encadena stats mensuales y refresco del guardado. */
    data class LoadResult(val state: SchoolDetailUiState, val fromNetwork: Boolean)

    suspend fun load(schoolId: String): LoadResult {
        // Si el backend falla pero la escuela está guardada offline → modo offline.
        val schoolFromNet = runCatching { getSchoolById(schoolId) }
        if (schoolFromNet.isFailure) {
            val snapshot = runCatching { savedSchoolRepo.loadOffline(schoolId) }.getOrNull()
            if (snapshot != null) {
                return LoadResult(
                    SchoolDetailUiState.Success(
                        school = School(
                            id = snapshot.school.id, name = snapshot.school.name,
                            location = null, region = snapshot.school.region,
                            style = null, rockType = snapshot.school.rockType,
                            lat = snapshot.school.lat, lon = snapshot.school.lon,
                            source = null
                        ),
                        forecast = snapshot.forecast,
                        forecastError = if (snapshot.forecast == null)
                            "Sin conexión y sin snapshot — solo mapa offline" else null,
                        notes = emptyList(),
                        isFavorite = false,
                        blocks = snapshot.blocks.map { savedSchoolRepo.toBlock(it, snapshot.lines) },
                        isCurrentUserAdmin = false,
                        isSavedOffline = true,
                        monthlyLoading = false,
                        offlineSnapshotAt = snapshot.forecastFetchedAt
                    ),
                    fromNetwork = false
                )
            }
        }
        val state = try {
            val school = schoolFromNet.getOrThrow()
            // Llamadas independientes en paralelo: en serie eran ~5 round-trips
            // al backend (~350 ms cada uno en remoto). runCatching dentro de
            // cada async evita que un fallo individual cancele al resto.
            coroutineScope {
                val forecastD = async { runCatching { getForecast(schoolId) } }
                val notesD = async { runCatching { getNotes(schoolId) }.getOrDefault(emptyList()) }
                val isFavD = async { runCatching { getMyFavorites().any { it.id == schoolId } }.getOrDefault(false) }
                val blocksD = async { runCatching { getBlocks(schoolId) }.getOrDefault(emptyList()) }
                val isAdminD = async { runCatching { getMyProfile().isAdmin }.getOrDefault(false) }
                val isSavedD = async { runCatching { savedSchoolRepo.loadOffline(schoolId) != null }.getOrDefault(false) }
                // Boletín EN PARALELO con el resto — si se insertara tarde,
                // recoloca la LazyColumn y Compose destruye el diálogo del
                // deep-link del diario (bug del 2026-07-03).
                val bulletinD = async { runCatching { mountainApi.getBulletin(school.lat, school.lon) }.getOrNull() }
                val forecastResult = forecastD.await()
                // Forecast fresco → a la caché. Si la red falló → último
                // forecast cacheado, marcando su antigüedad para que la UI
                // avise de que son datos viejos.
                var forecast = forecastResult.getOrNull()
                var forecastCachedAt: Long? = null
                if (forecast != null) {
                    runCatching { savedSchoolRepo.cacheForecast(schoolId, forecast) }
                } else {
                    runCatching { savedSchoolRepo.loadCachedForecast(schoolId) }.getOrNull()?.let { (cached, at) ->
                        forecast = cached
                        forecastCachedAt = at
                    }
                }
                SchoolDetailUiState.Success(
                    school = school,
                    forecast = forecast,
                    forecastError = if (forecast == null)
                        forecastResult.exceptionOrNull()?.toUserMessage() else null,
                    notes = notesD.await(), isFavorite = isFavD.await(), blocks = blocksD.await(),
                    isCurrentUserAdmin = isAdminD.await(),
                    isSavedOffline = isSavedD.await(),
                    monthlyLoading = true,
                    forecastCachedAt = forecastCachedAt,
                    mountainBulletin = bulletinD.await()
                )
            }
        } catch (t: Throwable) {
            SchoolDetailUiState.Error(t.toUserMessage())
        }
        return LoadResult(state, fromNetwork = true)
    }
}
