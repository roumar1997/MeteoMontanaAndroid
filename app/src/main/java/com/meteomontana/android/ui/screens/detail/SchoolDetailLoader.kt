package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.approach.GetApproachesUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.util.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Un reintento corto ante un fallo puntual de red (móvil cambiando de celda,
 * wifi que acaba de conectar). Nada más.
 *
 * HISTORIA — importante para no volver a subir esto: llegó a haber DOS
 * reintentos con esperas de 400ms y 1200ms porque "los bloques salían vacíos
 * al entrar en frío y bien al reentrar" (2026-08-13/14). Se atribuyó a una
 * conexión TLS inicial lenta y se descartó el token. **Era el token**: el
 * cliente HTTP esperaba SIN LÍMITE a que Firebase lo diera, incluso en rutas
 * públicas que no lo necesitan (ver `TOKEN_WAIT_MS` en `ApiHttpClient`, y la
 * medida: el backend responde en ~0,5 s siempre). Arreglada la causa, esperar
 * 1,6 s extra solo servía para disimular errores de verdad.
 */
private suspend fun <T> retryOnce(block: suspend () -> T): Result<T> {
    val first = runCatching { block() }
    if (first.isSuccess) return first
    delay(400)
    return runCatching { block() }
}

/**
 * Como [retryOnce] pero con un intento más (400 ms y 1200 ms).
 *
 * Solo para los BLOQUES. Son el contenido de la pantalla: si fallan, la
 * escuela sale sin una sola piedra, sin sectores y sin parkings — y encima
 * sin ningún aviso, porque el fallo se convertía en lista vacía. Álvaro lo
 * pilló otra vez el 2026-08-24 ("sale Cabo Negro y ni una piedra ni sector");
 * es preferible tardar 1,6 s más en el caso malo que enseñar una escuela
 * vacía que parece rota.
 */
private suspend fun <T> retryTwice(block: suspend () -> T): Result<T> {
    val first = runCatching { block() }
    if (first.isSuccess) return first
    delay(400)
    val second = runCatching { block() }
    if (second.isSuccess) return second
    delay(1_200)
    return runCatching { block() }
}

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
    private val getMountainBulletin: com.meteomontana.android.domain.usecase.weather.GetMountainBulletinUseCase,
    private val db: com.meteomontana.db.MeteoMontanaDb,
    private val blockRepo: com.meteomontana.android.domain.repository.BlockRepository,
    private val getApproaches: GetApproachesUseCase
) {

    /**
     * PREVIEW instantánea desde disco (stale-while-revalidate): escuela del
     * catálogo cacheado + bloques cacheados + último forecast. Se pinta al
     * abrir SIN esperar a la red (el flujo diario→piedra abre la ficha ya);
     * la carga real de [load] la sustituye al llegar. null = nunca visitada.
     */
    suspend fun loadCachedPreview(schoolId: String): SchoolDetailUiState.Success? {
        val row = runCatching {
            db.schemaQueries.cachedSchoolById(schoolId).executeAsOneOrNull()
        }.getOrNull() ?: return null
        val blocks = runCatching { blockRepo.getCachedBlocks(schoolId) }.getOrNull()
        val cachedForecast = runCatching { savedSchoolRepo.loadCachedForecast(schoolId) }.getOrNull()
        return SchoolDetailUiState.Success(
            school = School(
                id = row.id, name = row.name, location = row.location,
                region = row.region, style = row.style, rockType = row.rockType,
                lat = row.lat, lon = row.lon, source = row.source
            ),
            forecast = cachedForecast?.first,
            forecastError = null,
            notes = emptyList(),
            isFavorite = false,
            blocks = blocks ?: emptyList(),
            isCurrentUserAdmin = false,
            // Sin spinner de stats en la preview: la carga real lo gestiona.
            monthlyLoading = false,
            forecastCachedAt = cachedForecast?.second
        )
    }
    /** [fromNetwork] distingue el Success online del snapshot offline: solo el
     *  online encadena stats mensuales y refresco del guardado. */
    data class LoadResult(val state: SchoolDetailUiState, val fromNetwork: Boolean)

    suspend fun load(schoolId: String): LoadResult {
        // Si hay copia offline, no tiene sentido esperar el timeout COMPLETO de
        // red (hasta 30s, ver ApiHttpClient) antes de enseñarla: con cobertura
        // mala-pero-no-nula (3G débil, por ejemplo) eso deja el spinner fijo en
        // pantalla el tiempo suficiente para parecer colgado. Con copia guardada,
        // 8s de margen es de sobra para una red que sí responde; sin copia no hay
        // a qué volver, así que ahí se deja el timeout normal — Álvaro, 2026-08-29
        // ("se queda cargando" con la escuela ya descargada y 3G débil).
        val cachedSnapshot = runCatching { savedSchoolRepo.loadOffline(schoolId) }.getOrNull()
        val schoolFromNet = runCatching {
            if (cachedSnapshot != null) {
                withTimeoutOrNull(8_000) { getSchoolById(schoolId) }
                    ?: error("Sin respuesta del servidor a tiempo")
            } else {
                getSchoolById(schoolId)
            }
        }
        if (schoolFromNet.isFailure) {
            val snapshot = cachedSnapshot ?: runCatching { savedSchoolRepo.loadOffline(schoolId) }.getOrNull()
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
                // Si los bloques FALLAN, no se pasa por "lista vacía" sin más:
                // eso pinta la escuela como si no tuviera nada. Se reintenta y,
                // en última instancia, se tira del snapshot guardado en disco
                // (aunque sea viejo, es infinitamente mejor que una escuela en
                // blanco) — Álvaro, 2026-08-24.
                val blocksD = async {
                    val res = retryTwice { getBlocks(schoolId) }
                    res.onSuccess {
                        android.util.Log.i("Cumbre", "getBlocks($schoolId) OK: ${it.size} bloques")
                    }
                    res.getOrElse { fallo ->
                        // Se REGISTRA el motivo: al convertir el fallo en lista
                        // vacía no quedaba ni rastro de por qué una escuela
                        // salía sin nada, y hubo que diagnosticarlo dos veces a
                        // ciegas (2026-08-13 y 2026-08-24).
                        android.util.Log.w(
                            "Cumbre",
                            "getBlocks($schoolId) falló tras 3 intentos: " +
                                "${fallo::class.simpleName}: ${fallo.message}", fallo
                        )
                        runCatching {
                            savedSchoolRepo.loadOffline(schoolId)?.let { s ->
                                s.blocks.map { b -> savedSchoolRepo.toBlock(b, s.lines) }
                            }
                        }.getOrNull().orEmpty()
                    }
                }
                val isAdminD = async { retryOnce { getMyProfile().isAdmin }.getOrDefault(false) }
                val isSavedD = async { runCatching { savedSchoolRepo.loadOffline(schoolId) != null }.getOrDefault(false) }
                // Aproximaciones (parking → sector): lectura, admin-gated en
                // la UI (APPROACH_DESIGN.md §2.6/§10 — pendiente revisión legal).
                val approachesD = async { runCatching { getApproaches(schoolId) }.getOrDefault(emptyList()) }
                // Boletín EN PARALELO con el resto — si se insertara tarde,
                // recoloca la LazyColumn y Compose destruye el diálogo del
                // deep-link del diario (bug del 2026-07-03).
                val bulletinD = async { runCatching { getMountainBulletin(school.lat, school.lon) }.getOrNull() }
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
                    mountainBulletin = bulletinD.await(),
                    approaches = approachesD.await()
                )
            }
        } catch (t: Throwable) {
            SchoolDetailUiState.Error(t.toUserMessage())
        }
        return LoadResult(state, fromNetwork = true)
    }
}
